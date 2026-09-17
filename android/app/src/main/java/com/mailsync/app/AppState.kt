package com.mailsync.app

object AppState {
    @Volatile var lastActiveTimeMs: Long = System.currentTimeMillis()
}
