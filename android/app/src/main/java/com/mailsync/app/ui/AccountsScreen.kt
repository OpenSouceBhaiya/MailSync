package com.mailsync.app.ui

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.clip
import com.mailsync.app.ui.components.CustomToggle
import com.mailsync.app.ui.theme.*
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountsScreen(viewModel: SettingsViewModel, highlight: String? = null, onNavigateBack: () -> Unit) {
    val accounts by viewModel.accounts.collectAsState()
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    var accountToDelete by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<String?>(null) }

    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(com.google.android.gms.common.api.ApiException::class.java)
            if (account?.email != null) {
                if (accounts.contains(account.email)) {
                    android.widget.Toast.makeText(context, "Account ${account.email} is already added!", android.widget.Toast.LENGTH_SHORT).show()
                    val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN).requestEmail().requestProfile().build()
                    GoogleSignIn.getClient(context, gso).signOut()
                } else {
                    viewModel.addAccountEmail(account.email!!, account.displayName)
                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                    android.widget.Toast.makeText(context, "Account added successfully!", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: com.google.android.gms.common.api.ApiException) {
            com.mailsync.app.utils.ErrorReporter.reportApiException(context, e.statusCode, "AccountsScreen")
        } catch (e: Exception) {
            com.mailsync.app.utils.ErrorReporter.reportError(context, e, "AccountsScreen")
        }
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
                                    .build()
                                val googleSignInClient = GoogleSignIn.getClient(context, gso)
                                googleSignInClient.signOut().addOnCompleteListener {
                                    googleSignInLauncher.launch(googleSignInClient.signInIntent)
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
    val disabledSyncAccounts by viewModel.disabledSyncAccounts.collectAsState()
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
                                            fontSize = 12.sp
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
                                val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                                    .requestEmail()
                                    .build()
                                val googleSignInClient = GoogleSignIn.getClient(context, gso)
                                googleSignInClient.signOut().addOnCompleteListener {
                                    googleSignInLauncher.launch(googleSignInClient.signInIntent)
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
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            
            Spacer(modifier = Modifier.height(32.dp))
        }
        
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
    }
}
