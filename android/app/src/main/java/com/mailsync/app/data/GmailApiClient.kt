package com.mailsync.app.data

import android.content.Context
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.gmail.Gmail
import com.google.api.services.gmail.GmailScopes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.accounts.Account
import android.util.Log

class GmailApiClient(private val context: Context) {

    private val jsonFactory = GsonFactory.getDefaultInstance()
    private val httpTransport = NetHttpTransport()

    fun getGmailService(email: String): Gmail {
        val credential = GoogleAccountCredential.usingOAuth2(
            context,
            listOf(GmailScopes.GMAIL_READONLY)
        ).apply {
            selectedAccount = Account(email, "com.google")
        }

        return Gmail.Builder(httpTransport, jsonFactory, credential)
            .setApplicationName("Gmail OTP Syncer")
            .build()
    }

    suspend fun fetchLatestOtps(email: String, sinceTimestampMs: Long): List<OtpEntity> = withContext(Dispatchers.IO) {
        val service = getGmailService(email)
        
        // Convert milliseconds to epoch seconds for Gmail's after: filter
        // This is the KEY optimization: Gmail does server-side filtering so we don't fetch old messages at all
        val sinceEpochSeconds = sinceTimestampMs / 1000
        val query = "newer_than:1d after:$sinceEpochSeconds label:inbox (otp OR code OR verification OR pin OR password)"

        val response = service.users().messages().list("me")
            .setQ(query)
            .setMaxResults(5) // Only need a few recent messages
            .execute()

        val messages = response.messages ?: return@withContext emptyList()
        val otps = mutableListOf<OtpEntity>()

        for (messageRef in messages) {
            try {
                val msg = service.users().messages().get("me", messageRef.id)
                    .setFormat("full")
                    .execute()

                val internalDate = msg.internalDate ?: continue
                if (internalDate <= sinceTimestampMs) continue

                val headers = msg.payload?.headers
                val subject = headers?.find { it.name.equals("Subject", ignoreCase = true) }?.value ?: ""
                var sender = headers?.find { it.name.equals("From", ignoreCase = true) }?.value ?: "Unknown"

                if (sender.contains("<")) {
                    sender = sender.substringBefore("<").trim().removeSurrounding("\"")
                }

                var bodyText = ""
                var bodyHtml = ""
                
                val parts = msg.payload?.parts
                if (parts != null) {
                    for (part in parts) {
                        if (part.mimeType == "text/plain") {
                            bodyText = part.body?.data?.let { String(java.util.Base64.getUrlDecoder().decode(it)) } ?: ""
                        } else if (part.mimeType == "text/html") {
                            bodyHtml = part.body?.data?.let { String(java.util.Base64.getUrlDecoder().decode(it)) } ?: ""
                        }
                    }
                } else if (msg.payload?.body?.data != null) {
                    val data = msg.payload.body.data
                    val decoded = String(java.util.Base64.getUrlDecoder().decode(data))
                    if (msg.payload.mimeType == "text/html") {
                        bodyHtml = decoded
                    } else {
                        bodyText = decoded
                    }
                }

                val extractionResult = OtpExtractor.extractOtp(subject, bodyText, bodyHtml, internalDate)
                if (extractionResult != null) {
                    otps.add(
                        OtpEntity(
                            id = messageRef.id,
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
                Log.e("GmailApiClient", "Error processing message ${messageRef.id}", e)
            }
        }
        return@withContext otps
    }
}
