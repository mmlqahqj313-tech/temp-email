package com.tempinbox.privateinbox

data class MailAccount(
    val id: String,
    val address: String,
    val token: String,
    val password: String,
    val createdAtMillis: Long,
    val expiresAtMillis: Long
)

data class MailSummary(
    val id: String,
    val senderName: String,
    val senderAddress: String,
    val subject: String,
    val intro: String,
    val seen: Boolean,
    val hasAttachments: Boolean,
    val createdAt: String
)

data class MailDetails(
    val id: String,
    val senderName: String,
    val senderAddress: String,
    val subject: String,
    val text: String,
    val html: List<String>,
    val seen: Boolean,
    val hasAttachments: Boolean,
    val createdAt: String
)
