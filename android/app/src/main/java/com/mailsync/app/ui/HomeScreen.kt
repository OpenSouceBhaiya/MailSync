package com.mailsync.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import android.os.Build
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.animateContentSize
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.SpanStyle
import android.content.Intent
import com.mailsync.app.ui.components.CustomToggle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.rotate
import com.mailsync.app.data.OtpEntity
import java.text.SimpleDateFormat
import java.util.*
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.api.services.gmail.GmailScopes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: OtpViewModel,
    historyViewModel: OtpHistoryViewModel,
    insightsViewModel: InsightsViewModel,
    settingsViewModel: SettingsViewModel,
    onNavigateToSettings: (String?) -> Unit,
    onNavigateToAccounts: (String?) -> Unit,
    onNavigateToScanner: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val lastScanTime by viewModel.lastScanTime.collectAsState()
    val totalOtps by insightsViewModel.totalOtps.collectAsState()
    val successRate by insightsViewModel.successRate.collectAsState()
    val accounts by settingsViewModel.accounts.collectAsState()
    val disabledAccounts by settingsViewModel.disabledSyncAccounts.collectAsState()
    val isSyncEnabled by settingsViewModel.isSyncEnabled.collectAsState()
    val allAccountsDisabled = accounts.isNotEmpty() && accounts.all { it in disabledAccounts }
    val context = LocalContext.current
    val isNotificationAccessGranted = androidx.core.app.NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
    val canDrawOverlays = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) android.provider.Settings.canDrawOverlays(context) else true
    val isTrulyActive = isSyncEnabled && accounts.isNotEmpty() && !allAccountsDisabled && isNotificationAccessGranted && canDrawOverlays
    val historyState by historyViewModel.uiState.collectAsState()
    val allOtps = if (historyState is HistoryUiState.Success) {
        (historyState as HistoryUiState.Success).otps
    } else {
        emptyList()
    }
    val isSyncing by viewModel.isSyncing.collectAsState()
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    var lifecycleTrigger by remember { mutableStateOf(0) }
    
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                lifecycleTrigger++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    
    val _trigger = lifecycleTrigger // ensure recomposition on resume
    
    var showBurst by remember { mutableStateOf(false) }
    
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                if (settingsViewModel.isSyncEnabled.value && settingsViewModel.accounts.value.isNotEmpty()) {
                    val startIntent = Intent(context, com.mailsync.app.service.OtpForegroundService::class.java)
                    try {
                        context.startService(startIntent)
                    } catch (e: Exception) { }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    
    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(com.google.android.gms.common.api.ApiException::class.java)
            if (account?.email != null) {
                val hasScope = account.grantedScopes.any { it.scopeUri == GmailScopes.GMAIL_READONLY }
                if (!hasScope) {
                    android.widget.Toast.makeText(context, "You must check the permission box to allow syncing!", android.widget.Toast.LENGTH_LONG).show()
                    val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN).build()
                    GoogleSignIn.getClient(context, gso).signOut()
                } else {
                    settingsViewModel.addAccountEmail(account.email!!, account.displayName)
                    showBurst = true
                    val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        vibrator.vibrate(android.os.VibrationEffect.createWaveform(longArrayOf(0, 50, 100, 50), -1))
                    } else {
                        @Suppress("DEPRECATION")
                        vibrator.vibrate(longArrayOf(0, 50, 100, 50), -1)
                    }
                }
            }
        } catch (e: com.google.android.gms.common.api.ApiException) {
            com.mailsync.app.utils.ErrorReporter.reportApiException(context, e.statusCode, "HomeScreen")
        } catch (e: Exception) {
            com.mailsync.app.utils.ErrorReporter.reportError(context, e, "HomeScreen")
        }
    }

    if (showBurst) {
        CelebratoryBurst(onAnimationFinished = { showBurst = false })
    }
    
    val isInstantSyncEnabled by settingsViewModel.isInstantSyncEnabled.collectAsState()
    val isClipboardCopyEnabled by settingsViewModel.isClipboardCopyEnabled.collectAsState()
    
    val needsClipboardPermission = !isClipboardCopyEnabled || !canDrawOverlays
    val needsNotificationPermission = !isInstantSyncEnabled || !isNotificationAccessGranted
    
    var showPermissionPrompt by remember { mutableStateOf(false) }
    var hasPromptedBatteryOpt by remember { mutableStateOf(false) }

    var showAddMenu by remember { mutableStateOf(false) }
    @OptIn(ExperimentalMaterial3Api::class)
    val sheetState = rememberModalBottomSheetState()
    
    LaunchedEffect(Unit) {
        val count = settingsViewModel.appOpenCount
        if ((needsClipboardPermission || needsNotificationPermission) && count > 0 && (count - 1) % 3 == 0) {
            showPermissionPrompt = true
        }
    }
    
    if (showPermissionPrompt) {
        AlertDialog(
            onDismissRequest = { showPermissionPrompt = false },
            title = { Text("Enhance Your Workflow", color = Color.White) },
            text = { Text("For a flawless and magical experience, please enable both Instant Sync (Notifications) and Background Clipboard in Settings!", color = Color.LightGray) },
            confirmButton = {
                TextButton(onClick = { showPermissionPrompt = false }) {
                    Text("OK", color = Color(0xFFE2C4FF))
                }
            },
            containerColor = Color(0xFF1E1926)
        )
    }

    Scaffold { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF120F17))
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item { Spacer(modifier = Modifier.height(16.dp)) }

            // Greeting
            item {
                val firstAccount = accounts.firstOrNull()
                val fullName = if (firstAccount != null) {
                    settingsViewModel.getAccountName(firstAccount) ?: firstAccount.substringBefore("@")
                } else null
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    WavingHandGreeting(fullName = fullName)
                    
                    IconButton(
                        onClick = { showAddMenu = true },
                        modifier = Modifier
                            .size(48.dp)
                            .background(Color(0xFFE2C4FF).copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Link PC",
                            tint = Color(0xFFE2C4FF)
                        )
                    }
                }
            }

            // Removed state collections from here

            if (needsClipboardPermission && needsNotificationPermission) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFFE53935).copy(alpha = 0.15f))
                            .padding(16.dp)
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.Top) {
                                Icon(Icons.Default.Warning, contentDescription = "Permissions Required", tint = Color(0xFFE53935))
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Permissions Required", color = Color(0xFFE53935), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                    Text("Background clipboard and Instant Sync must be turned on so the app can function flawlessly.", color = Color.White.copy(alpha = 0.8f), fontSize = 14.sp)
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = { onNavigateToSettings("both") },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Fix It Now", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            } else if (needsClipboardPermission) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFFE53935).copy(alpha = 0.15f))
                            .padding(16.dp)
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.Top) {
                                Icon(Icons.Default.Warning, contentDescription = "Permissions Required", tint = Color(0xFFE53935))
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Permissions Required", color = Color(0xFFE53935), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                    Text("Background clipboard copy must be turned on so the app can function flawlessly.", color = Color.White.copy(alpha = 0.8f), fontSize = 14.sp)
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = { onNavigateToSettings("clipboard") },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Fix It Now", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            } else if (needsNotificationPermission) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFF4CAF50).copy(alpha = 0.15f))
                            .padding(16.dp)
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.Top) {
                                Icon(Icons.Default.Info, contentDescription = "Info", tint = Color(0xFF4CAF50))
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Notification Disabled", color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                    Text("Please enable it as that helps us extract faster, but if you can't, no issues, the backend is working properly.", color = Color.White.copy(alpha = 0.8f), fontSize = 14.sp)
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = { onNavigateToSettings("instant_sync") },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Fix It Now", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            if (accounts.isNotEmpty()) {
                if (!isSyncEnabled || allAccountsDisabled) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color(0xFFFFB020).copy(alpha = 0.15f))
                                .padding(16.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Warning, contentDescription = "Action Required", tint = Color(0xFFFFB020))
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Action Required", color = Color(0xFFFFB020), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                    Text(if (allAccountsDisabled) "All accounts are paused." else "Sync is disabled.", color = Color.White.copy(alpha = 0.8f), fontSize = 14.sp)
                                }
                                Button(
                                    onClick = { 
                                        if (allAccountsDisabled) onNavigateToAccounts("accounts")
                                        else settingsViewModel.setSyncEnabled(true)
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFB020))
                                ) {
                                    Text("Fix", color = Color.Black, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            if (accounts.isEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(32.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(24.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("No Gmail account connected yet", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Connect an account to start syncing OTPs securely.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                            Spacer(modifier = Modifier.height(24.dp))
                            Button(
                                onClick = { 
                                    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
                                    val activeNetwork = cm.activeNetwork
                                    val capabilities = cm.getNetworkCapabilities(activeNetwork)
                                    if (capabilities != null && (capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) || capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR) || capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET))) {
                                        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                                            .requestEmail()
                                            .requestScopes(com.google.android.gms.common.api.Scope(GmailScopes.GMAIL_READONLY))
                                            .build()
                                        val googleSignInClient = GoogleSignIn.getClient(context, gso)
                                        googleSignInClient.signOut().addOnCompleteListener {
                                            googleSignInLauncher.launch(googleSignInClient.signInIntent)
                                        }
                                    } else {
                                        android.widget.Toast.makeText(context, "No internet connection available. Please connect to the internet to sign in.", android.widget.Toast.LENGTH_LONG).show()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Text("Connect Gmail Account", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
            item {
                val infiniteBgTransition = androidx.compose.animation.core.rememberInfiniteTransition()
                val bgOffset by infiniteBgTransition.animateFloat(
                    initialValue = 0f,
                    targetValue = 1000f,
                    animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                        animation = androidx.compose.animation.core.tween(5000, easing = androidx.compose.animation.core.LinearEasing),
                        repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
                    )
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(
                            brush = Brush.linearGradient(
                                colors = if (isTrulyActive) listOf(Color(0xFF2C1C5E), Color(0xFF1E153A), Color(0xFF2C1C5E)) else listOf(Color(0xFF2A2A2A), Color(0xFF1A1A1A), Color(0xFF2A2A2A)),
                                start = androidx.compose.ui.geometry.Offset(if (isTrulyActive) bgOffset else 0f, 0f),
                                end = androidx.compose.ui.geometry.Offset(if (isTrulyActive) bgOffset + 500f else 500f, 500f)
                            )
                        )
                        .padding(24.dp)
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(if (isTrulyActive) Color(0xFF10B981) else Color.Gray))
                                Spacer(modifier = Modifier.width(6.dp))
                                val statusText = if (!isNotificationAccessGranted || !canDrawOverlays) "AUTO-FETCH INACTIVE" else if (isTrulyActive) "AUTO-FETCH ACTIVE" else "AUTO-FETCH PAUSED"
                                val statusColor = if (!isNotificationAccessGranted || !canDrawOverlays) Color(0xFFE53935) else if (isTrulyActive) Color(0xFF10B981) else Color.Gray
                                Text(statusText, color = statusColor, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (isSyncing) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color(0xFF10B981), strokeWidth = 2.dp)
                                    Spacer(modifier = Modifier.width(8.dp))
                                } else {
                                    IconButton(
                                        onClick = { 
                                            if (isSyncEnabled) {
                                                val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
                                                val activeNetwork = cm.activeNetwork
                                                val capabilities = cm.getNetworkCapabilities(activeNetwork)
                                                if (capabilities != null && (capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) || capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR) || capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET))) {
                                                    viewModel.fetchOtps()
                                                } else {
                                                    android.widget.Toast.makeText(context, "No internet connection available.", android.widget.Toast.LENGTH_LONG).show()
                                                }
                                            }
                                        },
                                        modifier = Modifier.size(32.dp),
                                        enabled = isSyncEnabled
                                    ) {
                                        Icon(Icons.Default.Refresh, contentDescription = "Manual Refresh", tint = if (isSyncEnabled) Color.White else Color.Gray, modifier = Modifier.size(20.dp))
                                    }
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                                CustomToggle(
                                    checked = isTrulyActive,
                                    onCheckedChange = { isChecked ->
                                        if (accounts.isEmpty() || allAccountsDisabled) {
                                            android.widget.Toast.makeText(context, "Please connect or enable an account first", android.widget.Toast.LENGTH_LONG).show()
                                        } else if (isChecked && (needsNotificationPermission || needsClipboardPermission)) {
                                            showPermissionPrompt = true
                                        } else {
                                            settingsViewModel.setSyncEnabled(isChecked)
                                            // No battery opt prompt here
                                        }
                                    }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        val lastOtpText = if (allOtps.isNotEmpty()) {
                            val diff = System.currentTimeMillis() - allOtps.first().receivedAt
                            val seconds = diff / 1000
                            val minutes = seconds / 60
                            if (minutes > 60) "Last OTP: ${minutes/60} hr ago"
                            else if (minutes > 0) "Last OTP: $minutes min ago"
                            else "Last OTP: $seconds sec ago"
                        } else "Last OTP: Never"
                        
                        Text(lastOtpText, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Gmail connected: ${accounts.size} Accounts", color = Color(0xFFA1A1AA), fontSize = 14.sp)
                    }
                }
            }

            // Stats Row 1
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatCard(
                        modifier = Modifier.weight(1f),
                        title = "Total OTPs",
                        value = totalOtps.toString(),
                        subtext = "Extracted",
                        icon = Icons.Default.Lock
                    )
                    StatCard(
                        modifier = Modifier.weight(1f),
                        title = "Success Rate",
                        value = "${successRate}%",
                        subtext = "Extraction",
                        icon = Icons.Default.CheckCircle
                    )
                }
            }

            // Live OTP Feed
            item {
                Text("Live OTP Feed", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
                
                if (uiState is OtpUiState.Loading && allOtps.isEmpty()) {
                    LoadingWithTips(modifier = Modifier.fillMaxWidth().height(300.dp))
                } else if (uiState is OtpUiState.Error && allOtps.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Warning, contentDescription = "Error", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(48.dp))
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Oops! Something went wrong.", color = Color.White, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text((uiState as OtpUiState.Error).message, color = MaterialTheme.colorScheme.error, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(onClick = { viewModel.fetchOtps() }) {
                                Text("Retry")
                            }
                        }
                    }
                } else if (allOtps.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text("No OTPs found yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            
            // Render up to 4 recent OTPs
            items(allOtps.take(4)) { otp ->
                OtpListItemFeed(otp = otp, context = context)
            }
            
            item { Spacer(modifier = Modifier.height(80.dp)) } // Bottom padding for navbar
        }
    }

    if (showAddMenu) {
        ModalBottomSheet(
            onDismissRequest = { showAddMenu = false },
            sheetState = sheetState,
            containerColor = Color(0xFF1E1926),
            dragHandle = { BottomSheetDefaults.DragHandle(color = Color.Gray) }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
                    .padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text("Select Action", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Spacer(modifier = Modifier.height(8.dp))
                
                Surface(
                    modifier = Modifier.fillMaxWidth().clickable {
                        showAddMenu = false
                        onNavigateToAccounts(null)
                    },
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF2D2938)
                ) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AccountCircle, contentDescription = "Manage Accounts", tint = Color(0xFF00FFA3), modifier = Modifier.size(32.dp))
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text("Manage Accounts", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text("Add or remove synced email addresses", color = Color.Gray, fontSize = 14.sp)
                        }
                    }
                }
                
                Surface(
                    modifier = Modifier.fillMaxWidth().clickable {
                        showAddMenu = false
                        onNavigateToScanner()
                    },
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF2D2938)
                ) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Computer, contentDescription = "Link PC", tint = Color(0xFF00FFA3), modifier = Modifier.size(32.dp))
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text("Link PC", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text("Scan QR code from extension", color = Color.Gray, fontSize = 14.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StatCard(modifier: Modifier = Modifier, title: String, value: String, subtext: String, icon: ImageVector) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(6.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(title, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(4.dp))
            Text(value, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Spacer(modifier = Modifier.height(4.dp))
            Text(subtext, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun OtpListItemFeed(otp: OtpEntity, context: Context) {
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Generic Letter Icon
            val firstLetter = otp.sender.take(1).uppercase()
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF3B2A6E)),
                contentAlignment = Alignment.Center
            ) {
                Text(firstLetter, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                val tagRegex = Regex("(?i)(\\[|\\()\\s*(backend|instant)\\s*(\\]|\\))")
                val cleanSender = otp.sender.replace(tagRegex, "").trim()
                Text(cleanSender, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(otp.code, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary, letterSpacing = 2.sp)
                
                val cleanAccount = otp.account.replace(tagRegex, "").trim()
                Text(cleanAccount, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                
                // 12-hour format time string
                val timeString = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(otp.receivedAt))
                Text("$timeString", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
            }
            
            Spacer(modifier = Modifier.width(8.dp))
            
            // Copy Button
            TextButton(
                onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText("OTP", otp.code)
                    clipboard.setPrimaryClip(clip)
                    if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) { android.widget.Toast.makeText(context, "OTP Copied", android.widget.Toast.LENGTH_SHORT).show() }
                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                },
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                Text("COPY", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun WavingHandGreeting(fullName: String?) {
    var isWaving by remember { mutableStateOf(false) }
    var hasPlayedStartup by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    val rotation = remember { androidx.compose.animation.core.Animatable(0f) }
    
    // Animation for gradient text
    val infiniteTransition = androidx.compose.animation.core.rememberInfiniteTransition()
    val gradientOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(3000, easing = androidx.compose.animation.core.LinearEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Restart
        ),
        label = "gradientOffset"
    )

    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current

    LaunchedEffect(isWaving) {
        if (isWaving) {
            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
            rotation.animateTo(20f, animationSpec = androidx.compose.animation.core.tween(150))
            rotation.animateTo(-20f, animationSpec = androidx.compose.animation.core.tween(150))
            rotation.animateTo(20f, animationSpec = androidx.compose.animation.core.tween(150))
            rotation.animateTo(-20f, animationSpec = androidx.compose.animation.core.tween(150))
            rotation.animateTo(0f, animationSpec = androidx.compose.animation.core.tween(150))
            isWaving = false
        }
    }

    // Fade + slide entrance
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        visible = true
    }

    androidx.compose.animation.AnimatedVisibility(
        visible = visible,
        enter = androidx.compose.animation.fadeIn(animationSpec = androidx.compose.animation.core.tween(400)) + 
                androidx.compose.animation.slideInVertically(initialOffsetY = { it / 2 }, animationSpec = androidx.compose.animation.core.tween(400))
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "👋",
                fontSize = 28.sp,
                modifier = Modifier
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null
                    ) {
                        if (!isWaving) isWaving = true
                    }
                    .graphicsLayer { 
                        rotationZ = rotation.value
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.7f, 0.7f)
                    }
            )
            Spacer(modifier = Modifier.width(8.dp))
            
            // Build the text with annotated string or just two texts
            val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
            val greetingTime = when {
                hour < 12 -> "Good Morning"
                hour < 17 -> "Good Afternoon"
                else -> "Good Evening"
            }
            val displayFullName = fullName?.split(" ")?.joinToString(" ") { it.replaceFirstChar { char -> if (char.isLowerCase()) char.titlecase(java.util.Locale.getDefault()) else char.toString() } }
            
            val nameTransition = androidx.compose.animation.core.rememberInfiniteTransition(label = "name")
            val nameOffset by nameTransition.animateFloat(
                initialValue = 0f,
                targetValue = 2000f,
                animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                    animation = androidx.compose.animation.core.tween(3000, easing = androidx.compose.animation.core.LinearEasing),
                    repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
                ),
                label = "nameOffset"
            )

            Text(
                modifier = Modifier.weight(1f, fill = false).animateContentSize(),
                text = buildAnnotatedString {
                    append(if (displayFullName.isNullOrBlank()) greetingTime else "$greetingTime,\n")
                    if (!displayFullName.isNullOrBlank()) {
                        withStyle(
                            style = SpanStyle(
                                brush = Brush.linearGradient(
                                    colors = listOf(
                                        Color(0xFF40FFAA), 
                                        Color(0xFF4079FF), 
                                        Color(0xFF40FFAA), 
                                        Color(0xFF4079FF), 
                                        Color(0xFF40FFAA)
                                    ),
                                    start = androidx.compose.ui.geometry.Offset(nameOffset, 0f),
                                    end = androidx.compose.ui.geometry.Offset(nameOffset + 500f, 500f)
                                )
                            )
                        ) {
                            append(displayFullName)
                        }
                    }
                },
                fontWeight = FontWeight.Bold,
                fontSize = 24.sp,
                color = Color.White,
                lineHeight = 32.sp,
                maxLines = 3,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
        }
    }
}
