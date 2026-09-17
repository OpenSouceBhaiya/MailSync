package com.mailsync.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OtpExtractorTest {

    @Test
    fun testSameLineLabeledCode() {
        val email = "Your verification code is 482913. Do not share it."
        val result = OtpExtractor.extractOtp(null, email, null, 0L)
        assertEquals("482913", result?.code)
    }

    @Test
    fun testNextLineIsolatedCode() {
        val email = """
            Hello,
            
            Use this code to sign in:
            
            511570
            
            Thanks!
        """.trimIndent()
        val result = OtpExtractor.extractOtp(null, email, null, 0L)
        assertEquals("511570", result?.code)
    }

    @Test
    fun testSpacedDigits() {
        val email = "Your OTP is 511 570"
        val result = OtpExtractor.extractOtp(null, email, null, 0L)
        assertEquals("511570", result?.code)
    }

    @Test
    fun testDashedDigits() {
        val email = "Your OTP is 482-913"
        val result = OtpExtractor.extractOtp(null, email, null, 0L)
        assertEquals("482913", result?.code)
    }

    @Test
    fun testAlphanumericCode() {
        val email = "Your access code is A3F92K"
        val result = OtpExtractor.extractOtp(null, email, null, 0L)
        assertEquals("A3F92K", result?.code)
    }
    
    @Test
    fun testNetflixStyleUUIDFooter() {
        // Netflix bug: extracted from footer UUID instead of body
        val email = """
            Enter this code to sign in
            
            4130
            
            ----
            SRC: 653956AC_22a3059a-5986-4458-a371-ef0c09e327ed_en-GB_IN_EVO
            Questions? Call 000-800-919-1743
        """.trimIndent()
        val result = OtpExtractor.extractOtp(null, email, null, 0L)
        assertEquals("4130", result?.code)
    }

    @Test
    fun testPhoneNumberDistraction() {
        val email = """
            Call us at 1-800-555-0199 if you need help.
            
            Your login code is 8841.
        """.trimIndent()
        val result = OtpExtractor.extractOtp(null, email, null, 0L)
        assertEquals("8841", result?.code)
    }

    @Test
    fun testNoLabelFallback() {
        val email = """
            Welcome to the app.
            
            654321
            
            Please use the code above.
        """.trimIndent()
        val result = OtpExtractor.extractOtp(null, email, null, 0L)
        assertEquals("654321", result?.code)
    }

    @Test
    fun testNoValidCodeReturnsNull() {
        val email = """
            Hello,
            Just checking in on your order.
            Thanks!
        """.trimIndent()
        val result = OtpExtractor.extractOtp(null, email, null, 0L)
        assertNull(result)
    }

    @Test
    fun testIgnoreMetadataInFooter() {
        val email = """
            Welcome to our service.
            
            ----
            Order ID: 1234567
            Ref: 98765432
        """.trimIndent()
        val result = OtpExtractor.extractOtp(null, email, null, 0L)
        assertNull(result)
    }
}
