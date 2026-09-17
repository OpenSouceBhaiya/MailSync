package com.mailsync.app.data

import android.content.Context
import android.util.Log
import com.mailsync.app.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

data class TokenResponse(
    val accessToken: String,
    val refreshToken: String?, // Only returned on first exchange if prompt=consent
    val expiresInSeconds: Long
)

class GoogleAuthHelper(private val context: Context) {
    private val client = OkHttpClient()

    private val clientId: String
        get() = context.getString(R.string.web_client_id)
        
    private val clientSecret: String
        get() = context.getString(R.string.web_client_secret)

    /**
     * Exchanges the short-lived Server Auth Code for an Access Token and a persistent Refresh Token.
     */
    suspend fun exchangeAuthCodeForTokens(authCode: String): TokenResponse? = withContext(Dispatchers.IO) {
        try {
            val body = FormBody.Builder()
                .add("client_id", clientId)
                .add("client_secret", clientSecret)
                .add("code", authCode)
                .add("grant_type", "authorization_code")
                // Google Sign-In on Android uses empty string or 'urn:ietf:wg:oauth:2.0:oob' for redirect_uri
                .add("redirect_uri", "") 
                .build()

            val request = Request.Builder()
                .url("https://oauth2.googleapis.com/token")
                .post(body)
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()

            if (response.isSuccessful && responseBody != null) {
                val json = JSONObject(responseBody)
                return@withContext TokenResponse(
                    accessToken = json.getString("access_token"),
                    refreshToken = json.optString("refresh_token", null),
                    expiresInSeconds = json.optLong("expires_in", 3600L)
                )
            } else {
                Log.e("GoogleAuthHelper", "Token exchange failed: ${response.code} $responseBody")
            }
        } catch (e: Exception) {
            Log.e("GoogleAuthHelper", "Network error during token exchange", e)
        }
        return@withContext null
    }

    /**
     * Uses the saved Refresh Token to securely get a brand new Access Token from Google's servers.
     */
    suspend fun refreshAccessToken(refreshToken: String): TokenResponse? = withContext(Dispatchers.IO) {
        try {
            val body = FormBody.Builder()
                .add("client_id", clientId)
                .add("client_secret", clientSecret)
                .add("refresh_token", refreshToken)
                .add("grant_type", "refresh_token")
                .build()

            val request = Request.Builder()
                .url("https://oauth2.googleapis.com/token")
                .post(body)
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()

            if (response.isSuccessful && responseBody != null) {
                val json = JSONObject(responseBody)
                return@withContext TokenResponse(
                    accessToken = json.getString("access_token"),
                    // Google rarely returns a new refresh token here, but if they do, we capture it
                    refreshToken = json.optString("refresh_token", refreshToken),
                    expiresInSeconds = json.optLong("expires_in", 3600L)
                )
            } else if (response.code == 400 || response.code == 401 || response.code == 403) {
                // Google explicitly rejected our token — throw so OtpRepository can
                // detect this as a confirmed auth failure (vs transient null) and mark revoked.
                Log.e("GoogleAuthHelper", "Token refresh rejected by Google: ${response.code} $responseBody")
                throw Exception("${response.code} Token rejected: $responseBody")
            } else {
                // Transient server error (5xx etc.) — return null so caller skips this cycle
                Log.e("GoogleAuthHelper", "Token refresh failed (transient): ${response.code} $responseBody")
            }
        } catch (e: Exception) {
            Log.e("GoogleAuthHelper", "Token refresh error", e)
            // Re-throw auth errors so OtpRepository catch block can inspect them
            if (e.message?.contains("401") == true || e.message?.contains("403") == true) throw e
        }
        return@withContext null

    }
}
