package com.mailsync.app.utils

import android.content.Context
import android.widget.Toast

object ToastManager {
    private var currentToast: Toast? = null

    fun show(context: Context, message: String, duration: Int = Toast.LENGTH_SHORT) {
        // Cancel the current toast if it exists to prevent queueing
        currentToast?.cancel()
        
        // Create and show the new toast immediately
        val newToast = Toast.makeText(context.applicationContext, message, duration)
        newToast.show()
        currentToast = newToast
    }
}
