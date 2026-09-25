package com.tempinbox.privateinbox

import android.content.Context
import android.util.Base64
import java.nio.charset.StandardCharsets
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties

class SecureSessionStore(context: Context) {
    private companion object {
        const val PREFS = "temp_email_secure_state"
        const val ACCOUNT = "encrypted_account"
        const val AUTO_REFRESH = "auto_refresh"
        const val KEY_ALIAS = "temp_email_account_key"
        const val VERSION_PREFIX = "v1:"
    }

    private val preferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun save(account: MailAccount) {
        val plain = listOf(
            account.id,
            account.address,
            account.token,
            account.password,
            account.createdAtMillis.toString(),
            account.expiresAtMillis.toString()
        ).joinToString("\u001F")

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.iv + cipher.doFinal(plain.toByteArray(StandardCharsets.UTF_8))
        val encoded = Base64.encodeToString(encrypted, Base64.NO_WRAP)

        preferences.edit().putString(ACCOUNT, VERSION_PREFIX + encoded).apply()
    }

    fun load(): MailAccount? {
        val stored = preferences.getString(ACCOUNT, null) ?: return null
        if (!stored.startsWith(VERSION_PREFIX)) return null

        return runCatching {
            val raw = Base64.decode(stored.removePrefix(VERSION_PREFIX), Base64.NO_WRAP)
            require(raw.size > 12)

            val iv = raw.copyOfRange(0, 12)
            val cipherText = raw.copyOfRange(12, raw.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                getOrCreateKey(),
                GCMParameterSpec(128, iv)
            )

            val fields = cipher.doFinal(cipherText)
                .toString(StandardCharsets.UTF_8)
                .split("\u001F")

            require(fields.size == 6)
            require(fields[0].isNotBlank())
            require(fields[1].contains('@'))
            require(fields[2].isNotBlank())
            require(fields[3].isNotBlank())

            MailAccount(
                id = fields[0],
                address = fields[1],
                token = fields[2],
                password = fields[3],
                createdAtMillis = fields[4].toLong(),
                expiresAtMillis = fields[5].toLong()
            )
        }.getOrElse {
            clearAccount()
            null
        }
    }

    fun clearAccount() {
        preferences.edit().remove(ACCOUNT).apply()
    }

    fun clear() = clearAccount()

    fun getAutoRefresh(): Boolean = preferences.getBoolean(AUTO_REFRESH, true)

    fun setAutoRefresh(enabled: Boolean) {
        preferences.edit().putBoolean(AUTO_REFRESH, enabled).apply()
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = java.security.KeyStore.getInstance("AndroidKeyStore")
        keyStore.load(null)

        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            "AndroidKeyStore"
        )

        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )

        return generator.generateKey()
    }
}
