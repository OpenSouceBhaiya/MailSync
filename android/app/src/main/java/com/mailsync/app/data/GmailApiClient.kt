package com.mailsync.app.data

import android.content.Context
import com.google.api.client.http.HttpRequestInitializer
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.gmail.Gmail
import com.google.api.services.gmail.GmailScopes
import com.google.api.services.gmail.model.MessagePart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.async
import com.google.api.services.gmail.model.MessagePartHeader
import android.util.Log

class GmailApiClient(private val context: Context) {

    private val jsonFactory = GsonFactory.getDefaultInstance()
    private val httpTransport = NetHttpTransport()

    fun getGmailService(accessToken: String): Gmail {
        val requestInitializer = HttpRequestInitializer { request ->
            request.headers.authorization = "Bearer $accessToken"
        }

        return Gmail.Builder(httpTransport, jsonFactory, requestInitializer)
            .setApplicationName("MailSync")
            .build()
    }

    /**
     * Recursively traverses the MIME tree to find text/plain and text/html parts at any depth.
     * Many bank and transactional emails use multipart/alternative -> text/plain/text/html nesting.
     */
    private fun extractPartsRecursive(part: MessagePart, texts: MutableList<String>, htmls: MutableList<String>) {
        val mimeType = part.mimeType ?: return
        when {
            mimeType == "text/plain" -> {
                val data = part.body?.data
                if (data != null) {
                    try { texts.add(String(java.util.Base64.getUrlDecoder().decode(data))) } catch (_: Exception) {}
                }
            }
            mimeType == "text/html" -> {
                val data = part.body?.data
                if (data != null) {
                    try { htmls.add(String(java.util.Base64.getUrlDecoder().decode(data))) } catch (_: Exception) {}
                }
            }
            mimeType.startsWith("multipart/") -> {
                // Recurse into sub-parts
                part.parts?.forEach { subPart -> extractPartsRecursive(subPart, texts, htmls) }
            }
        }
    }

    suspend fun fetchLatestOtps(email: String, accessToken: String, sinceTimestampMs: Long): List<OtpEntity> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val service = getGmailService(accessToken)
        
        // OPTIMIZATION 1: Drop the "q" parameter. 
        // Gmail's search index takes 5-10 seconds to index a new email. 
        // By just asking for the latest INBOX messages, we bypass the search index and get emails instantly (0s latency).
        val response = service.users().messages().list("me")
            .setLabelIds(listOf("INBOX"))
            .setMaxResults(5)
            .execute()

        val messages = response.messages ?: return@withContext emptyList()
        
        // OPTIMIZATION 2: Fetch all 5 messages concurrently instead of sequentially.
        val deferredMessages = messages.map { messageRef ->
            async {
                try {
                    service.users().messages().get("me", messageRef.id)
                        .setFormat("full")
                        .execute()
                } catch (e: Exception) {
                    Log.e("GmailApiClient", "Error fetching msg ${messageRef.id}", e)
                    null
                }
            }
        }
        
        val otps = mutableListOf<OtpEntity>()
        
        for (deferredMsg in deferredMessages) {
            try {
                val msg = deferredMsg.await() ?: continue

                val internalDate = msg.internalDate ?: continue
                if (internalDate.toLong() <= sinceTimestampMs) continue

                val headers: List<MessagePartHeader>? = msg.payload?.headers
                val subject = headers?.find { it.name.equals("Subject", ignoreCase = true) }?.value ?: ""
                var sender = headers?.find { it.name.equals("From", ignoreCase = true) }?.value ?: "Unknown"

                if (sender.contains("<")) {
                    sender = sender.substringBefore("<").trim().removeSurrounding("\"")
                }

                val textParts = mutableListOf<String>()
                val htmlParts = mutableListOf<String>()

                val payload = msg.payload
                if (payload != null) {
                    val directData = payload.body?.data
                    if (directData != null) {
                        try {
                            val decoded = String(java.util.Base64.getUrlDecoder().decode(directData as String))
                            if (payload.mimeType == "text/html") htmlParts.add(decoded)
                            else textParts.add(decoded)
                        } catch (_: Exception) {}
                    }
                    val parts: List<MessagePart>? = payload.parts
                    parts?.forEach { part: MessagePart -> extractPartsRecursive(part, textParts, htmlParts) }
                }

                val bodyText = textParts.joinToString("\n")
                val bodyHtml = htmlParts.joinToString("\n")

                val extractionResult = OtpExtractor.extractOtp(subject, bodyText, bodyHtml, internalDate)
                if (extractionResult != null) {
                    otps.add(
                        OtpEntity(
                            id = msg.id,
                            code = extractionResult.code,
                            sender = sender,
                            subject = subject,
                            account = "$email [Backend]",
                            receivedAt = internalDate,
                            expiresAt = extractionResult.expiresAt,
                            isUsed = false
                        )
                    )
                }
            } catch (e: Exception) {
                Log.e("GmailApiClient", "Error processing deferred message", e)
            }
        }
        return@withContext otps
    }
}
