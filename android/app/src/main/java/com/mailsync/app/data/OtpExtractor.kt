package com.mailsync.app.data

import android.util.Log

data class ExtractionResult(
    val code: String,
    val expiresAt: Long?
)

object OtpExtractor {

    // Tier 1 Trigger phrases (Broadened)
    private val triggerKeywords = listOf(
        "verification code", "one-time code", "one time password", "otp is", "otp", 
        "your code", "security code", "access code", "login code", "sign-in code", 
        "sign in code", "enter this code", "enter the code", "use this code", 
        "code to sign in", "confirmation code", "auth code", "authentication code", 
        "code is", "your pin", "passcode", "pin", "the code"
    )
    
    // Footer boundaries for deprioritization
    private val footerBoundaries = listOf(
        "terms of", "privacy policy", "unsubscribe", "this message was sent to", 
        "the team", "all rights reserved", "©"
    )
    
    // Preceding metadata labels for exclusion
    // IMPORTANT: Only include words that NEVER appear near a real OTP code.
    // Do NOT include "sent", "received", "date", "amount", "rs", "inr" — these appear in legitimate
    // OTP emails like "OTP sent to your device" or "Received: OTP is 483920".
    private val metadataPrefixes = listOf(
        "src", "reference", "ref", "ticket", "case", "order", "invoice", "tracking",
        "transaction", "txn", "upi", "rs", "inr", "₹", "balance", "bal", "a/c", "ac", 
        "account", "amount", "statement", "credit", "debit"
    )

    private fun safeLog(tag: String, msg: String) {
        try {
            android.util.Log.d(tag, msg)
        } catch (e: Throwable) {
            println("$tag: $msg")
        }
    }

    fun extractOtp(subject: String?, bodyText: String?, bodyHtml: String?, receivedTimeMs: Long): ExtractionResult? {
        val rawText = if (!bodyText.isNullOrBlank()) bodyText.trim() else stripHtml(bodyHtml ?: "")
        val truncatedBody = rawText.take(50000)

        // Try extracting from subject first
        var result: ExtractionResult? = null
        if (subject != null) {
            result = extractFromText(subject, receivedTimeMs)
        }
        
        // If not found in subject, try body
        if (result == null) {
            result = extractFromText(truncatedBody, receivedTimeMs)
        }
        
        return result
    }

    private fun stripHtml(html: String): String {
        // Remove style and script blocks entirely first
        var text = html.replace(Regex("(?is)<style.*?>.*?</style>"), " ")
        text = text.replace(Regex("(?is)<script.*?>.*?</script>"), " ")
        
        // Strip invisible characters (Zero-width spaces, etc.)
        text = text.replace(Regex("[\\u200B\\u200C\\u200D\\uFEFF]"), "")
        
        // Replace block elements with newlines to preserve visual structure
        text = text.replace(Regex("(?i)</?(p|div|br|tr|ul|li|h[1-6])[^>]*>"), "\n")
        
        // Remove all other HTML tags
        text = text.replace(Regex("<[^>]*>"), " ")
        
        // Replace multiple spaces with a single space, but preserve newlines
        text = text.replace(Regex("[ \\t]+"), " ")
        text = text.replace(Regex("\\n\\s*\\n"), "\n\n")
        return text.trim()
    }
    
    private fun normalizeText(text: String): String {
        // Find digit sequences separated by spaces or dashes and squash them.
        var normalized = text
        var prev = ""
        while (normalized != prev) {
            prev = normalized
            // Only collapse space or dash if it's flanked by digits
            normalized = normalized.replace(Regex("(?<=\\d)[ -]+(?=\\d)"), "")
        }
        
        // Final pass: Strip invisible unicode characters just in case
        normalized = normalized.replace(Regex("[\\u200B\\u200C\\u200D\\uFEFF]"), "")
        return normalized
    }
    
    private fun isAfterFooter(text: String, position: Int): Boolean {
        val beforeText = text.substring(0, position).lowercase()
        // If we see a horizontal rule like ----- or _____, consider it footer
        if (beforeText.contains("----") || beforeText.contains("____")) return true
        
        for (boundary in footerBoundaries) {
            if (beforeText.contains(boundary)) return true
        }
        return false
    }
    
    private fun hasMetadataPrefix(text: String, matchStart: Int): Boolean {
        // Look at the ~20 characters preceding the match
        val start = maxOf(0, matchStart - 20)
        val prefixText = text.substring(start, matchStart).lowercase()
        
        // Check if prefixText ends with one of the metadata prefixes, ignoring punctuation
        val cleanPrefix = prefixText.replace(Regex("[^a-z0-9 ]"), " ").trim()
        val words = cleanPrefix.split("\\s+".toRegex())
        if (words.isEmpty()) return false
        
        val lastWord = words.last()
        val secondToLastWord = if (words.size > 1) words[words.size - 2] else ""
        
        for (prefix in metadataPrefixes) {
            if (lastWord == prefix || secondToLastWord == prefix) return true
        }
        return false
    }

    private fun extractFromText(text: String, receivedTimeMs: Long): ExtractionResult? {
        val normalized = normalizeText(text)
        
        // STRICT FILTER: Use word boundaries to prevent "pin" matching inside "shopping"
        val hasOtpKeyword = triggerKeywords.any { 
            Regex("\\b${Regex.escape(it)}\\b", RegexOption.IGNORE_CASE).containsMatchIn(normalized) 
        }
        if (!hasOtpKeyword) {
            safeLog("OtpExtractor", "Rejected: No OTP keywords found in text.")
            return null
        }
        
        val numberRegex = Regex("\\b([0-9]{4,8}|[a-zA-Z0-9]{4,8})\\b")
        val matches = numberRegex.findAll(normalized).toList()
        
        var bestMatch: String? = null
        var highestScore = Int.MIN_VALUE
        
        for (match in matches) {
            val candidate = match.groupValues[1]
            val startPos = match.range.first
            
            val digitCount = candidate.count { it.isDigit() }
            val letterCount = candidate.count { it.isLetter() }

            // 1. Reject pure letters or non-digits, and alphanumerics with too few digits (e.g., tracking IDs)
            if (digitCount == 0) continue
            if (letterCount > 0 && digitCount < 3) continue // Require at least 3 digits if it contains letters
            
            var score = 0
            
            // 2. Length scoring (Standard OTPs are usually 4, 6 or 8 digits)
            if (candidate.length == 4 || candidate.length == 6 || candidate.length == 8) {
                score += 50
            }
            
            // 2b. Penalize years (e.g., 2023, 2024, 2026)
            if (candidate.length == 4 && (candidate.startsWith("201") || candidate.startsWith("202"))) {
                score -= 150
            }
            
            // 3. Position scoring (earlier in email is better)
            score += (normalized.length - startPos) / 100
            
            // 4. Positive context (Proximity to OTP keywords)
            val windowStart = maxOf(0, startPos - 150)
            val windowEnd = minOf(normalized.length, startPos + candidate.length + 150)
            val window = normalized.substring(windowStart, windowEnd).lowercase()
            
            if (window.contains("otp") || window.contains("code") || window.contains("verification") || window.contains("pin") || window.contains("password")) {
                score += 100
            } else {
                // If it's not near an OTP keyword, heavily penalize it! (E.g. random amounts in a bank statement)
                score -= 300
            }
            
            // 5. Negative context (Metadata prefixes immediately before)
            // Use -300 (not -1000) so a strong OTP keyword nearby (+100) can still save a valid candidate
            if (hasMetadataPrefix(normalized, startPos)) {
                score -= 300 // Penalize IDs, references, orders
            }
            
            // 6. Negative context (Footers)
            if (isAfterFooter(normalized, startPos)) {
                score -= 150
            }
            
            // 7. Negative context (Phone numbers or long strings of digits)
            val wideWindow = normalized.substring(maxOf(0, startPos - 5), minOf(normalized.length, startPos + candidate.length + 5))
            if (wideWindow.count { it.isDigit() } > 8) {
                score -= 100
            }
            
            if (score > highestScore) {
                highestScore = score
                bestMatch = candidate
            }
        }
        
        if (bestMatch != null && highestScore >= 0) {
            safeLog("OtpExtractor", "Smart Score Engine Match: $bestMatch, Score: $highestScore")
            return ExtractionResult(bestMatch, extractExpiry(normalized, receivedTimeMs))
        }

        return null
    }
    
    private fun extractExpiry(text: String, receivedTimeMs: Long): Long? {
        val expiryRegex = Regex("(?i)expires?\\s+in\\s+(\\d+)\\s+(minute|hour)s?")
        val match = expiryRegex.find(text)
        if (match != null) {
            val amount = match.groupValues[1].toLongOrNull() ?: return null
            val unit = match.groupValues[2].lowercase()
            val multiplier = if (unit == "hour") 60 * 60 * 1000L else 60 * 1000L
            return receivedTimeMs + (amount * multiplier)
        }
        return null
    }
}
