package com.tempinbox.privateinbox

import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.security.SecureRandom
import kotlinx.coroutines.delay

class MailTmApi {
    companion object {
        private const val BASE_URL = "https://api.mail.tm"
        private const val CONNECT_TIMEOUT_MS = 15_000
        private const val READ_TIMEOUT_MS = 20_000
        private const val MAX_MESSAGE_PAGES = 5
        private const val MAX_DOMAIN_PAGES = 3
        private const val PAGE_DELAY_MS = 150L
        private const val MESSAGE_PAGE_SIZE = 30
    }

    private val secureRandom = SecureRandom()

    suspend fun createAccount(): MailAccount {
        var lastError: Exception? = null

        repeat(4) {
            try {
                val domain = getFirstActiveDomain()
                val username = randomUsername()
                val address = "$username@$domain"
                val password = randomPassword()

                val accountResponse = requestJson(
                    method = "POST",
                    path = "/accounts",
                    body = JSONObject().apply {
                        put("address", address)
                        put("password", password)
                    }.toString()
                )

                val id = accountResponse.optString("id").trim()
                if (id.isBlank()) {
                    throw MailTmException(null, "لم تُرجع خدمة البريد معرّف الحساب.")
                }

                val token = authenticate(address, password)
                val now = System.currentTimeMillis()

                return MailAccount(
                    id = id,
                    address = address,
                    token = token,
                    password = password,
                    createdAtMillis = now,
                    expiresAtMillis = now + 60L * 60L * 1000L
                )
            } catch (e: Exception) {
                lastError = e
            }
        }

        throw lastError ?: MailTmException(null, "تعذر إنشاء عنوان بريد الآن.")
    }

    fun authenticate(address: String, password: String): String {
        val json = requestJson(
            method = "POST",
            path = "/token",
            body = JSONObject().apply {
                put("address", address)
                put("password", password)
            }.toString()
        )

        return json.optString("token").trim().ifBlank {
            throw MailTmException(null, "تعذر إنشاء جلسة البريد.")
        }
    }

    suspend fun listMessages(token: String): List<MailSummary> {
        val allMessages = mutableListOf<MailSummary>()
        var page = 1

        while (page <= MAX_MESSAGE_PAGES) {
            val json = getJson("/messages?page=$page", token)
            val members = json.optJSONArray("hydra:member") ?: JSONArray()
            val totalItems = json.optInt("hydra:totalItems", -1)

            for (i in 0 until members.length()) {
                val item = members.optJSONObject(i) ?: continue
                val from = item.optJSONObject("from")
                val id = item.optString("id").trim()
                if (id.isBlank()) continue

                allMessages += MailSummary(
                    id = id,
                    senderName = from?.optString("name").orEmpty(),
                    senderAddress = from?.optString("address").orEmpty(),
                    subject = item.optString("subject").ifBlank { "بدون موضوع" },
                    intro = item.optString("intro"),
                    seen = item.optBoolean("seen", false),
                    hasAttachments = item.optBoolean("hasAttachments", false),
                    createdAt = item.optString("createdAt")
                )
            }

            val fetched = allMessages.size
            val pageCount = members.length()
            val reachedTotal = totalItems >= 0 && fetched >= totalItems
            val reachedEnd = pageCount == 0 || pageCount < MESSAGE_PAGE_SIZE

            if (reachedTotal || reachedEnd) break

            page++
            delay(PAGE_DELAY_MS)
        }

        return allMessages
    }

    fun getMessage(token: String, messageId: String): MailDetails {
        val item = getJson("/messages/${safePathSegment(messageId)}", token)
        val from = item.optJSONObject("from")
        val htmlArray = item.optJSONArray("html")
        val html = buildList {
            if (htmlArray != null) {
                for (i in 0 until htmlArray.length()) {
                    add(htmlArray.optString(i))
                }
            }
        }

        return MailDetails(
            id = item.optString("id"),
            senderName = from?.optString("name").orEmpty(),
            senderAddress = from?.optString("address").orEmpty(),
            subject = item.optString("subject").ifBlank { "بدون موضوع" },
            text = item.optString("text"),
            html = html,
            seen = item.optBoolean("seen", false),
            hasAttachments = item.optBoolean("hasAttachments", false),
            createdAt = item.optString("createdAt")
        )
    }

    fun markAsRead(token: String, messageId: String) {
        request("PATCH", "/messages/${safePathSegment(messageId)}", token, null)
    }

    fun deleteMessage(token: String, messageId: String) {
        request("DELETE", "/messages/${safePathSegment(messageId)}", token, null)
    }

    fun deleteAccount(token: String, accountId: String) {
        request("DELETE", "/accounts/${safePathSegment(accountId)}", token, null)
    }

    private suspend fun getFirstActiveDomain(): String {
        for (page in 1..MAX_DOMAIN_PAGES) {
            val json = getJson("/domains?page=$page", null)
            val domains = json.optJSONArray("hydra:member") ?: JSONArray()

            for (i in 0 until domains.length()) {
                val domain = domains.optJSONObject(i) ?: continue
                if (domain.optBoolean("isActive", false)) {
                    val value = domain.optString("domain").trim()
                    if (value.isNotBlank()) return value
                }
            }

            if (domains.length() == 0) break
            delay(PAGE_DELAY_MS)
        }

        throw MailTmException(null, "لا يوجد نطاق بريد متاح حاليًا.")
    }

    private fun getJson(path: String, token: String?): JSONObject {
        val result = request("GET", path, token, null)
        return if (result.isBlank()) JSONObject() else JSONObject(result)
    }

    private fun requestJson(method: String, path: String, body: String): JSONObject {
        val response = request(method, path, null, body)
        if (response.isBlank()) {
            throw MailTmException(null, "لم تُرجع الخدمة بيانات متوقعة.")
        }
        return JSONObject(response)
    }

    private fun request(
        method: String,
        path: String,
        token: String?,
        body: String?
    ): String {
        val connection = (URL(BASE_URL + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            useCaches = false
            doInput = true
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "TempEmail/1.0 Android")
            if (!token.isNullOrBlank()) {
                setRequestProperty("Authorization", "Bearer $token")
            }
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
            }
        }

        return try {
            if (body != null) {
                connection.outputStream.use { output ->
                    output.write(body.toByteArray(Charsets.UTF_8))
                }
            }

            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.use {
                BufferedReader(InputStreamReader(it, Charsets.UTF_8)).readText()
            }.orEmpty()

            if (status !in 200..299) {
                throw MailTmException(status, readableError(status, response))
            }

            response
        } catch (e: MailTmException) {
            throw e
        } catch (e: IOException) {
            throw MailTmException(null, "تعذر الوصول إلى الإنترنت الآن.")
        } finally {
            connection.disconnect()
        }
    }

    private fun readableError(status: Int, response: String): String {
        val apiMessage = runCatching {
            JSONObject(response).optString("detail").ifBlank {
                JSONObject(response).optString("message")
            }
        }.getOrDefault("")

        return when {
            status == 401 -> "انتهت جلسة البريد."
            status == 404 -> "العنصر المطلوب غير موجود."
            status == 422 -> "بيانات البريد غير مقبولة."
            status == 429 -> "الطلبات كثيرة حاليًا، جرّب بعد قليل."
            apiMessage.isNotBlank() -> apiMessage
            else -> "تعذر إكمال الطلب حاليًا. رمز الخطأ: $status."
        }
    }

    private fun safePathSegment(value: String): String = Uri.encode(value)

    private fun randomUsername(): String {
        val alphabet = "abcdefghijklmnopqrstuvwxyz0123456789"
        return buildString {
            repeat(11) { append(alphabet[secureRandom.nextInt(alphabet.length)]) }
        }
    }

    private fun randomPassword(): String {
        val alphabet = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789!@#%_-"
        return buildString {
            repeat(28) { append(alphabet[secureRandom.nextInt(alphabet.length)]) }
        }
    }
}

class MailTmException(
    val status: Int?,
    override val message: String
) : Exception(message)
