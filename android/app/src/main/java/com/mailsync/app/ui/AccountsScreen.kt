package com.mailsync.app.ui

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.clip
import com.mailsync.app.ui.components.CustomToggle
import com.mailsync.app.ui.theme.*
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.api.services.gmail.GmailScopes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountsScreen(viewModel: SettingsViewModel, highlight: String? = null, onNavigateBack: () -> Unit) {
    val accounts by viewModel.accounts.collectAsState()
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val scrollState = rememberScrollState()
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    var accountToDelete by remember { mutableStateOf<String?>(null) }
    var showGmailPermissionDialog by remember { mutableStateOf(false) }
    var showTrustMessage by remember { mutableStateOf(false) }

    // Helper to build a sign-in intent that ALWAYS requests Gmail read permission.
    // This is what forces Google to show the permission checkbox screen every time.
    fun buildSignInIntent() {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(com.google.android.gms.common.api.Scope(GmailScopes.GMAIL_READONLY))
            .requestServerAuthCode(context.getString(com.mailsync.app.R.string.web_client_id), true)
            .build()
        val googleSignInClient = GoogleSignIn.getClient(context, gso)
        // Always sign out first so Google never silently reuses a cached session
        googleSignInClient.signOut().addOnCompleteListener {
            // We return this client reference so the launcher can use the intent
        }
    }

    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(com.google.android.gms.common.api.ApiException::class.java)
            if (account?.email != null) {
                // CRITICAL CHECK: Did the user actually tick the Gmail permission checkbox?
                val hasGmailScope = account.grantedScopes.any {
                    it.scopeUri == GmailScopes.GMAIL_READONLY
                }

                if (!hasGmailScope) {
                    // User skipped the Gmail permission — show our friendly dialog
                    showGmailPermissionDialog = true
                } else {
                    // Trigger the async account addition (token exchange + save).
                    // The success Toast will be shown from inside SettingsViewModel once the token exchange confirms success.
                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                    viewModel.addAccountEmail(account.email!!, account.displayName, account.serverAuthCode, context)
                }
            }
        } catch (e: com.google.android.gms.common.api.ApiException) {
            com.mailsync.app.utils.ErrorReporter.reportApiException(context, e.statusCode, "AccountsScreen")
        } catch (e: Exception) {
            com.mailsync.app.utils.ErrorReporter.reportError(context, e, "AccountsScreen")
        }
    }

    if (showTrustMessage) {
        AlertDialog(
            onDismissRequest = { showTrustMessage = false },
            containerColor = DarkSurface,
            title = { Text("Optimal Sync Experience", color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("To ensure the fastest real-time syncing, we recommend connecting only your 2 or 3 primary accounts. While you are welcome to connect as many accounts as you need, syncing many accounts requires heavier processing to communicate with Google's servers. This can result in Google temporarily slowing down your OTP delivery.", color = TextSecondary, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("💡 Pro Tip for Instant Sync:", color = SuccessGreen, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("For instant, millisecond syncing across unlimited accounts, make sure Gmail Notifications are enabled on your phone, and grant Notification Access to MailSync. When enabled, MailSync reads OTPs instantly from your Gmail notifications, entirely bypassing server delays!", color = TextSecondary, fontSize = 14.sp)
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showTrustMessage = false
                        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                            .requestEmail()
                            .requestScopes(com.google.android.gms.common.api.Scope(GmailScopes.GMAIL_READONLY))
                            .requestServerAuthCode(context.getString(com.mailsync.app.R.string.web_client_id), true)
                            .build()
                        val googleSignInClient = GoogleSignIn.getClient(context, gso)
                        googleSignInClient.signOut().addOnCompleteListener {
                            googleSignInLauncher.launch(googleSignInClient.signInIntent)
                        }
                    }
                ) {
                    Text("Continue Adding Account", color = SuccessGreen)
                }
            },
            dismissButton = {
                TextButton(onClick = { showTrustMessage = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Accounts", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkBackground,
                    titleContentColor = TextPrimary,
                    navigationIconContentColor = TextPrimary
                )
            )
        },
        containerColor = DarkBackground
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp)
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val disabledSyncAccounts by viewModel.disabledSyncAccounts.collectAsState()
            val allAccountsDisabled = accounts.isNotEmpty() && accounts.all { it in disabledSyncAccounts }
            
            if (highlight == "accounts" && allAccountsDisabled) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFE53935).copy(alpha = 0.2f))
                        .padding(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFE53935), modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Please enable at least one account to activate background syncing.",
                            color = Color(0xFFE53935),
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            } else {
                Spacer(modifier = Modifier.height(16.dp))
            }
            
            Spacer(modifier = Modifier.height(16.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Google Accounts", fontWeight = FontWeight.Bold, color = TextPrimary, style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    if (accounts.isEmpty()) {
                        Button(
                            onClick = {
                                val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                                    .requestEmail()
                                    .requestScopes(com.google.android.gms.common.api.Scope(GmailScopes.GMAIL_READONLY))
                                    .requestServerAuthCode(context.getString(com.mailsync.app.R.string.web_client_id), true)
                                    .build()
                                // Await signOut to ensure the account chooser is shown
                                val client = GoogleSignIn.getClient(context, gso)
                                client.signOut().addOnCompleteListener {
                                    googleSignInLauncher.launch(client.signInIntent)
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(50.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = TextPrimary, contentColor = DarkBackground),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Email, contentDescription = null, tint = DarkBackground)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Sign in with Google", fontWeight = FontWeight.Bold)
                        }
                    } else {
                        val revokedAccounts by viewModel.revokedAccounts.collectAsState()

                        accounts.forEach { email ->
                            val isSyncingEnabled = !disabledSyncAccounts.contains(email)
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    val accountName = viewModel.getAccountName(email) ?: email.substringBefore("@")
                                    Text(
                                        text = accountName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = email,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextSecondary,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        val isRevoked = revokedAccounts.contains(email)
                                        Box(modifier = Modifier.size(8.dp).clip(androidx.compose.foundation.shape.CircleShape).background(if (isRevoked) ErrorRed else if (isSyncingEnabled) SuccessGreen else androidx.compose.ui.graphics.Color.Gray))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            if (isRevoked) "Access Revoked (Tap to re-add)" else if (isSyncingEnabled) "Syncing OTPs" else "Sync Paused",
                                            color = if (isRevoked) ErrorRed else if (isSyncingEnabled) SuccessGreen else TextSecondary,
                                            fontSize = 12.sp,
                                            modifier = if (isRevoked) {
                                                Modifier.clickable {
                                                    val gso = com.google.android.gms.auth.api.signin.GoogleSignInOptions.Builder(com.google.android.gms.auth.api.signin.GoogleSignInOptions.DEFAULT_SIGN_IN)
                                                        .requestEmail()
                                                        .requestScopes(com.google.android.gms.common.api.Scope(GmailScopes.GMAIL_READONLY))
                                                        .requestServerAuthCode(context.getString(com.mailsync.app.R.string.web_client_id), true)
                                                        .build()
                                                    val client = com.google.android.gms.auth.api.signin.GoogleSignIn.getClient(context, gso)
                                                    client.signOut().addOnCompleteListener {
                                                        googleSignInLauncher.launch(client.signInIntent)
                                                    }
                                                }
                                            } else {
                                                Modifier
                                            }
                                        )
                                    }
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CustomToggle(
                                        checked = isSyncingEnabled,
                                        onCheckedChange = {
                                            viewModel.setAccountSyncEnabled(email, it)
                                        }
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    IconButton(onClick = {
                                        accountToDelete = email
                                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                    }, modifier = Modifier.size(24.dp)) {
                                        Icon(Icons.Filled.Delete, contentDescription = "Remove Account", tint = ErrorRed)
                                    }
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        OutlinedButton(
                            onClick = {
                                if (accounts.size >= 3) {
                                    showTrustMessage = true
                                } else {
                                    val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                                        .requestEmail()
                                        .requestScopes(com.google.android.gms.common.api.Scope(GmailScopes.GMAIL_READONLY))
                                        .requestServerAuthCode(context.getString(com.mailsync.app.R.string.web_client_id), true)
                                        .build()
                                    val client = GoogleSignIn.getClient(context, gso)
                                    client.signOut().addOnCompleteListener {
                                        googleSignInLauncher.launch(client.signInIntent)
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                            border = androidx.compose.foundation.BorderStroke(1.dp, DarkSurfaceVariant)
                        ) {
                            Icon(Icons.Default.Email, contentDescription = null, tint = TextPrimary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Add another Account")
                        }
                    }
                }
            }
            

            Spacer(modifier = Modifier.height(32.dp))
            
            // Subtle Privacy Footer
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Lock, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Privacy First", color = SuccessGreen, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Everything runs strictly on your device. We never store, read, or sell your emails.",
                color = TextSecondary,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            
            Spacer(modifier = Modifier.height(32.dp))
        }
        

        // ── Remove Account Dialog ─────────────────────────────────────────────
        if (accountToDelete != null) {
            AlertDialog(
                onDismissRequest = { accountToDelete = null },
                title = { Text("Remove Account", fontWeight = FontWeight.Bold, color = TextPrimary) },
                text = { Text("Are you sure you want to remove $accountToDelete? The app will stop syncing OTPs for this account.", color = TextSecondary) },
                confirmButton = {
                    TextButton(onClick = {
                        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                            .requestEmail()
                            .build()
                        val googleSignInClient = GoogleSignIn.getClient(context, gso)
                        googleSignInClient.revokeAccess().addOnCompleteListener {
                            googleSignInClient.signOut()
                        }
                        viewModel.removeAccountEmail(accountToDelete!!)
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                        accountToDelete = null
                    }) {
                        Text("Remove", color = ErrorRed, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { accountToDelete = null }) {
                        Text("Cancel", color = TextSecondary)
                    }
                },
                containerColor = DarkSurfaceVariant,
                shape = RoundedCornerShape(16.dp)
            )
        }

        // ── Gmail Permission Missing Dialog ───────────────────────────────────
        // Shown when user goes through Google sign-in but skips the Gmail checkbox
        if (showGmailPermissionDialog) {
            AlertDialog(
                onDismissRequest = { showGmailPermissionDialog = false },
                title = {
                    Text(
                        "🙈  Whoa, You Forgot Something!",
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        fontSize = 18.sp
                    )
                },
                text = {
                    Column {
                        Text(
                            "Okay, real talk — if you don't let MailSync read your Gmail, how on earth are we supposed to find your OTPs for you? We're not magicians! 🪄",
                            color = TextPrimary,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            "We know what you're thinking: \"But wait... can they see ALL my emails?\" — Absolutely not. Zero. Nada.",
                            color = TextSecondary,
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "MailSync is 100% open source. Every single line of code is public on GitHub for you to audit. We only scan for OTP emails, everything runs on YOUR device, and your data never leaves your phone.",
                            color = TextSecondary,
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF1E1A2E))
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = SuccessGreen,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "No servers. No tracking. No bull.",
                                color = SuccessGreen,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            "Still not convinced? Check our source code on GitHub or read our Privacy Policy — both links are in the app settings. We're open source bhaiya, we won't hurt our own people. 🤝",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showGmailPermissionDialog = false
                            // Re-launch sign-in so user gets another chance to allow Gmail
                            val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                                .requestEmail()
                                .requestScopes(com.google.android.gms.common.api.Scope(GmailScopes.GMAIL_READONLY))
                                .requestServerAuthCode(context.getString(com.mailsync.app.R.string.web_client_id), true)
                                .build()
                            val client = GoogleSignIn.getClient(context, gso)
                            client.signOut().addOnCompleteListener {
                                googleSignInLauncher.launch(client.signInIntent)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PurpleAccent)
                    ) {
                        Text("↩️  Try Again", color = TextPrimary, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showGmailPermissionDialog = false }) {
                        Text("I'll Do It Later", color = TextSecondary)
                    }
                },
                containerColor = DarkSurface,
                shape = RoundedCornerShape(20.dp)
            )
        }
    }
}
