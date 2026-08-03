package com.mailsync.app.ui

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mailsync.app.ui.theme.*
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.api.services.gmail.GmailScopes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupScreen(
    viewModel: SettingsViewModel,
    onSetupComplete: () -> Unit
) {
    val context = LocalContext.current
    var isChecking by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        isChecking = false
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(com.google.android.gms.common.api.ApiException::class.java)
                val hasScope = account.grantedScopes.any { it.scopeUri == GmailScopes.GMAIL_READONLY }
                if (!hasScope) {
                    android.widget.Toast.makeText(context, "You must check the permission box to allow syncing!", android.widget.Toast.LENGTH_LONG).show()
                    val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN).build()
                    GoogleSignIn.getClient(context, gso).signOut()
                } else {
                    viewModel.addAccountEmail(account.email!!, account.displayName, account.serverAuthCode, context)
                    android.widget.Toast.makeText(context, "Account added successfully!", android.widget.Toast.LENGTH_SHORT).show()
                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                    onSetupComplete()
                }
        } catch (e: com.google.android.gms.common.api.ApiException) {
            if (e.statusCode != 12501) {
                com.mailsync.app.utils.ErrorReporter.reportApiException(context, e.statusCode, "SetupScreen")
                errorMsg = "Google Sign-In failed: Code ${e.statusCode}"
            }
        } catch (e: Exception) {
            com.mailsync.app.utils.ErrorReporter.reportError(context, e, "SetupScreen")
            errorMsg = "Google Sign-In failed: ${e.message}"
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(DarkBackground), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.padding(24.dp).widthIn(max = 600.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Welcome to MailSync",
                style = MaterialTheme.typography.headlineMedium,
                color = TextPrimary,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = "Connect your Google Account to securely sync OTPs directly on your device. No backend required.",
                color = TextSecondary,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(48.dp))

            if (errorMsg != null) {
                Text(errorMsg!!, color = ErrorRed, style = MaterialTheme.typography.bodySmall)
                Spacer(modifier = Modifier.height(16.dp))
            }

            Button(
                onClick = {
                    isChecking = true
                    errorMsg = null
                    
                    // Create Google Sign In Intent
                    val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                        .requestEmail()
                        .requestScopes(com.google.android.gms.common.api.Scope(GmailScopes.GMAIL_READONLY))
                        .requestServerAuthCode(context.getString(com.mailsync.app.R.string.web_client_id), true)
                        .build()
                    val googleSignInClient = GoogleSignIn.getClient(context, gso)
                    googleSignInClient.signOut().addOnCompleteListener {
                        googleSignInLauncher.launch(googleSignInClient.signInIntent)
                    }
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PurpleAccent),
                enabled = !isChecking
            ) {
                if (isChecking) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = TextPrimary)
                } else {
                    Icon(Icons.Default.Email, contentDescription = null, tint = TextPrimary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Sign in with Google", fontWeight = FontWeight.Bold, color = TextPrimary)
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("💡 Autofill Setup", fontWeight = FontWeight.Bold, color = TextPrimary)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "MailSync can automatically fill OTPs into apps and websites for you.",
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            val intent = android.content.Intent(android.provider.Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE)
                            intent.data = android.net.Uri.parse("package:${context.packageName}")
                            try {
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                android.widget.Toast.makeText(context, "Could not open settings", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = DarkSurface)
                    ) {
                        Text("Enable Autofill Keyboard")
                    }
                }
            }
        }
    }
}
