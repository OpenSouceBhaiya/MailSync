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
        "code is", "your pin", "passcode", "pin", "the code", "temporary password"
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
        "account", "amount", "statement", "credit", "debit", "aadhaar", "pan", "card", 
        "number", "no"
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

            // Calculate proximity context FIRST — needed to evaluate pure-alpha OTPs
            val windowStart = maxOf(0, startPos - 150)
            val windowEnd = minOf(normalized.length, startPos + candidate.length + 150)
            val window = normalized.substring(windowStart, windowEnd).lowercase()
            
            val nearOtpKeyword = window.contains("otp") || window.contains("code") || 
                window.contains("verification") || window.contains("pin") || 
                window.contains("password") || window.contains("token") ||
                window.contains("authenticate") || window.contains("access")
            
            // Reject pure letters ONLY if they are NOT near any OTP keyword
            // Many services (Microsoft, GitHub, Notion) send pure-alpha codes like "ABCDEF"
            if (digitCount == 0 && !nearOtpKeyword) continue
            
            // If it has letters and digits, require at least 2 digits to avoid tracking IDs
            if (letterCount > 0 && digitCount < 2) continue
            
            // Ultra-good Alpha OTP Filtering:
            // Pure alphabetical candidates MUST be fully uppercase (e.g., "ASDFGH").
            // Normal lowercase/mixed-case words in sentences will be rejected to prevent false positives.
            if (digitCount == 0) {
                if (!candidate.all { it.isUpperCase() } || candidate.length < 5) continue
            }
            
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
            if (nearOtpKeyword) {
                score += 100
            } else {
                // If it's not near an OTP keyword, heavily penalize it! (E.g. random amounts in a bank statement)
                score -= 300
            }
            
            // 4b. Extra bonus for pure-digit codes (most common OTP format)
            if (letterCount == 0) score += 30
            
            // 5. Negative context (Metadata prefixes immediately before)
            // Use -1000 so if it's an Aadhaar or PAN number it's strictly rejected
            if (hasMetadataPrefix(normalized, startPos)) {
                score -= 1000 // Penalize IDs, references, orders, aadhaar, pan
            }
            
            // 6. Negative context (Footers)
            if (isAfterFooter(normalized, startPos)) {
                score -= 150
            }
            
            // 7. Negative context (Phone numbers, Aadhaar, or long strings of digits)
            val wideWindow = normalized.substring(maxOf(0, startPos - 5), minOf(normalized.length, startPos + candidate.length + 5))
            if (wideWindow.count { it.isDigit() } >= 10) {
                // If there are 10 or more digits around/including it (like a phone number or 12-digit Aadhaar), completely reject it
                score -= 1000
            } else if (wideWindow.count { it.isDigit() } > 8) {
                score -= 200
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
        val patterns = listOf(
            // "expires in X minutes/hours/seconds"
            Regex("(?i)expir(?:es?|ed|ing)\\s+in\\s+(\\d+)\\s*(second|sec|minute|min|hour|hr)s?"),
            // "valid for X minutes/hours/seconds"
            Regex("(?i)valid\\s+for\\s+(\\d+)\\s*(second|sec|minute|min|hour|hr)s?"),
            // "use/enter/verify within X minutes"
            Regex("(?i)(?:use|enter|verify)\\s+(?:it\\s+)?within\\s+(\\d+)\\s*(second|sec|minute|min|hour|hr)s?"),
            // "OTP valid X min" (no 'for')
            Regex("(?i)(?:otp|code|pin)\\s+(?:is\\s+)?valid\\s+(\\d+)\\s*(second|sec|minute|min|hour|hr)s?"),
            // "X-minute OTP" / "X minute code"
            Regex("(?i)(\\d+)[- ]*(second|sec|minute|min|hour|hr)s?\\s+(?:otp|code|pin|password)")
        )
        for (pattern in patterns) {
            val match = pattern.find(text) ?: continue
            val amount = match.groupValues[1].toLongOrNull() ?: continue
            val unit = match.groupValues[2].lowercase()
            
            // Validation: Cap unreasonable expiry times
            if (unit.startsWith("min") && amount > 60) continue
            if ((unit.startsWith("hour") || unit.startsWith("hr")) && amount > 24) continue

            val multiplier = when {
                unit.startsWith("sec") -> 1000L
                unit.startsWith("hour") || unit.startsWith("hr") -> 60 * 60 * 1000L
                else -> 60 * 1000L
            }
            return receivedTimeMs + (amount * multiplier)
        }
        return null
    }
}
