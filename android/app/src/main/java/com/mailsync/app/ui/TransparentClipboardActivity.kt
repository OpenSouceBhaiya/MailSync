package com.mailsync.app.ui

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import android.util.Log

class TransparentClipboardActivity : Activity() {

    private var otpCode: String? = null

    private var hasCopied = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
        otpCode = intent.getStringExtra("EXTRA_OTP_CODE")
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && !hasCopied) {
            hasCopied = true
            val code = otpCode
            if (code != null) {
                try {
                    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText("OTP", code)
                    
                    clipboard.setPrimaryClip(clip)
                    Log.d("TransparentClipboard", "Successfully copied OTP to clipboard in onWindowFocusChanged: $code")
                    
                    if (android.os.Build.VERSION.SDK_INT <= 32) {
                        Toast.makeText(this, "MailSync: OTP Copied ✔️", Toast.LENGTH_LONG).show()
                    }
                    
                    val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        vibrator.vibrate(android.os.VibrationEffect.createOneShot(50, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
                    } else {
                        @Suppress("DEPRECATION")
                        vibrator.vibrate(50)
                    }
                } catch (e: Exception) {
                    Log.e("TransparentClipboard", "Failed to copy to clipboard", e)
                }
            }
            
            Handler(Looper.getMainLooper()).postDelayed({
                finish()
                @Suppress("DEPRECATION")
                overridePendingTransition(0, 0)
            }, 250)
        }
    }
}
