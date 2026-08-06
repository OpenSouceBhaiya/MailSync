package com.mailsync.app.ui

import android.app.Activity
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import com.mailsync.app.ui.theme.*

import androidx.compose.material.icons.filled.Computer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel, highlight: String? = null, onNavigateToAccounts: () -> Unit, onNavigateToDevices: () -> Unit) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    var isNotificationAccessGranted = NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
    var canDrawOverlays = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) android.provider.Settings.canDrawOverlays(context) else true

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    var lifecycleTrigger by remember { mutableStateOf(0) }
    
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                lifecycleTrigger++
                val notifGranted = NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
                val overlayGranted = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) android.provider.Settings.canDrawOverlays(context) else true
                
                // Force sync the app state with OS state since SettingsScreen is where OS permissions are managed
                viewModel.setInstantSyncEnabled(notifGranted)
                viewModel.setClipboardCopyEnabled(overlayGranted)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    
    val _trigger = lifecycleTrigger
    isNotificationAccessGranted = NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
    canDrawOverlays = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) android.provider.Settings.canDrawOverlays(context) else true

    val notificationPermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
        onResult = { _ ->
            // Launch the notification listener settings unconditionally after the prompt
            try {
                context.startActivity(Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            } catch (e: Exception) {
                android.util.Log.e("Settings", "Failed to open notification settings", e)
            }
        }
    )

    val batteryOptLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult(),
        onResult = { _ ->
            try {
                val intent = Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION, android.net.Uri.parse("package:${context.packageName}"))
                context.startActivity(intent)
            } catch (e: Exception) {
                android.util.Log.e("Settings", "Failed to open overlay settings", e)
            }
        }
    )

    Box(modifier = Modifier.fillMaxSize().background(DarkBackground), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 600.dp)
                .padding(24.dp)
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.Start
        ) {
            Spacer(modifier = Modifier.height(24.dp))
            
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "OTP Syncer",
                    style = MaterialTheme.typography.headlineMedium,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Settings",
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
            }
            
            Spacer(modifier = Modifier.height(48.dp))

            // General Settings
            Text("General", style = MaterialTheme.typography.titleMedium, color = TextPrimary, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(16.dp))

            SettingsItem(
                title = "Connected Accounts",
                subtitle = "Manage your Gmail accounts",
                icon = Icons.Default.Email,
                onClick = onNavigateToAccounts
            )
            Spacer(modifier = Modifier.height(12.dp))
            SettingsItem(
                title = "Linked PCs",
                subtitle = "Manage active sessions & browser extensions",
                icon = Icons.Default.Computer,
                onClick = onNavigateToDevices
            )
            

            Spacer(modifier = Modifier.height(32.dp))

            // Sync Settings
            Text("Sync & Permissions", style = MaterialTheme.typography.titleMedium, color = TextPrimary, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(16.dp))
            
            val autoStopDelayMs by viewModel.autoStopDelayMs.collectAsState()
            val currentMinutes = (autoStopDelayMs / (60 * 1000)).toInt()
            
            var activeHighlight by remember { mutableStateOf(highlight) }
            LaunchedEffect(highlight) {
                activeHighlight = highlight
                if (highlight != null) {
                    kotlinx.coroutines.delay(2000)
                    activeHighlight = null
                }
            }
            
            val infiniteTransition = androidx.compose.animation.core.rememberInfiniteTransition()
            val highlightAlpha by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 0.4f,
                animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                    animation = androidx.compose.animation.core.tween(800, easing = androidx.compose.animation.core.FastOutLinearInEasing),
                    repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
                )
            )
            
            val isInstantSyncEnabled by viewModel.isInstantSyncEnabled.collectAsState()
            val isClipboardCopyEnabled by viewModel.isClipboardCopyEnabled.collectAsState()
            
            val instantSyncHighlight = if ((activeHighlight == "instant_sync" || activeHighlight == "both") && (!isNotificationAccessGranted || !isInstantSyncEnabled)) highlightAlpha else 0f
            val clipboardHighlight = if ((activeHighlight == "clipboard" || activeHighlight == "both") && (!canDrawOverlays || !isClipboardCopyEnabled)) highlightAlpha else 0f

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFFE53935).copy(alpha = instantSyncHighlight))
                    .padding(vertical = 12.dp, horizontal = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Notifications, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Instant Sync Engine", fontWeight = FontWeight.Bold, color = TextPrimary)
                        Text("Required. Silently checks incoming emails and copies OTPs instantly.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    }
                    Switch(
                        checked = isInstantSyncEnabled && isNotificationAccessGranted,
                        onCheckedChange = { isChecked ->
                            if (isChecked) {
                                if (!isNotificationAccessGranted) {
                                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                                        notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                                    } else {
                                        val intent = Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
                                        context.startActivity(intent)
                                    }
                                } else {
                                    viewModel.setInstantSyncEnabled(true)
                                }
                            } else {
                                val intent = Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
                                context.startActivity(intent)
                            }
                        }
                    )
                }
            }
            
            // Battery Note Moved Outside Row for Better Formatting
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 40.dp, end = 16.dp, bottom = 12.dp)
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                    .padding(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "To preserve battery, the sync engine will automatically shut down after $currentMinutes minutes of inactivity (adjustable).",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        fontSize = 11.sp,
                        lineHeight = 14.sp
                    )
                }
            }
            
            val isAlwaysOnSyncEnabled by viewModel.isAlwaysOnSyncEnabled.collectAsState()

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .padding(vertical = 12.dp, horizontal = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Computer, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                        Text("Always-On Background Sync", fontWeight = FontWeight.Bold, color = TextPrimary)
                        Text("Wakes phone when PC needs OTP, even if Gmail notifications are disabled. Uses slightly more battery.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    }
                    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
                    Switch(
                        checked = isAlwaysOnSyncEnabled,
                        onCheckedChange = { isChecked ->
                            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                            viewModel.setAlwaysOnSyncEnabled(isChecked)
                        }
                    )
                }
            }
            
            var canDrawOverlays = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) Settings.canDrawOverlays(context) else true

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFFE53935).copy(alpha = clipboardHighlight))
                    .padding(vertical = 12.dp, horizontal = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Notifications, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                        Text("Background Clipboard Copy", fontWeight = FontWeight.Bold, color = TextPrimary)
                        Text("Required to magically copy OTPs while you are using other apps (Appear on Top).", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    }
                    Switch(
                        checked = isClipboardCopyEnabled && canDrawOverlays,
                        onCheckedChange = { isChecked ->
                            if (isChecked) {
                                if (!canDrawOverlays) {
                                    val pm = context.getSystemService(android.content.Context.POWER_SERVICE) as android.os.PowerManager
                                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M && !pm.isIgnoringBatteryOptimizations(context.packageName)) {
                                        try {
                                            val intent = Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                                data = android.net.Uri.parse("package:${context.packageName}")
                                            }
                                            batteryOptLauncher.launch(intent)
                                        } catch (e: Exception) {
                                            // Fallback if battery intent fails
                                            val intent = Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION, android.net.Uri.parse("package:${context.packageName}"))
                                            context.startActivity(intent)
                                        }
                                    } else {
                                        val intent = Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION, android.net.Uri.parse("package:${context.packageName}"))
                                        context.startActivity(intent)
                                    }
                                } else {
                                    viewModel.setClipboardCopyEnabled(true)
                                }
                            } else {
                                val intent = Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION, android.net.Uri.parse("package:${context.packageName}"))
                                context.startActivity(intent)
                            }
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Auto-Stop Timer Settings
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp, horizontal = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Notifications, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Auto-pause syncing", fontWeight = FontWeight.Bold, color = TextPrimary)
                        Text("Background engine will stop after ${currentMinutes.toInt()} minutes of inactivity.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    val options = listOf(5, 10, 20, 30)
                    options.forEach { minutes ->
                        val isSelected = currentMinutes == minutes
                        val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primary else com.mailsync.app.ui.theme.DarkSurfaceVariant,
                            modifier = Modifier
                                .clickable {
                                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                    viewModel.setAutoStopDelayMs((minutes * 60 * 1000).toLong())
                                }
                                .padding(4.dp)
                        ) {
                            Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                if (isSelected) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                }
                                Text(
                                    text = "${minutes}m",
                                    color = if (isSelected) Color.White else TextPrimary,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))


            // Security Settings
            Text("Security & Privacy", style = MaterialTheme.typography.titleMedium, color = TextPrimary, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(16.dp))
            
            val isBiometricEnabled by viewModel.isBiometricEnabled.collectAsState()
            
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Biometric App Lock", fontWeight = FontWeight.Bold, color = TextPrimary)
                    Text("Require fingerprint or face scan to open the app", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                }
                Switch(
                    checked = isBiometricEnabled,
                    onCheckedChange = { enable ->
                        val activity = context as? androidx.fragment.app.FragmentActivity
                        if (enable) {
                            if (activity != null) {
                                val helper = BiometricHelper(activity)
                                helper.authenticate(
                                    onSuccess = { viewModel.setBiometricEnabled(true) },
                                    onError = { 
                                        com.mailsync.app.utils.ToastManager.show(context, "Verification failed: $it", android.widget.Toast.LENGTH_SHORT)
                                    }
                                )
                            } else {
                                viewModel.setBiometricEnabled(true)
                            }
                        } else {
                            if (activity != null) {
                                val helper = BiometricHelper(activity)
                                helper.authenticate(
                                    onSuccess = { viewModel.setBiometricEnabled(false) },
                                    onError = { 
                                        com.mailsync.app.utils.ToastManager.show(context, "Verification failed to disable lock: $it", android.widget.Toast.LENGTH_SHORT)
                                    }
                                )
                            } else {
                                viewModel.setBiometricEnabled(false)
                            }
                        }
                    }
                )
            }
            
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Help & Support Section
            Text("Help & Support", style = MaterialTheme.typography.titleMedium, color = TextPrimary, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            
            SettingsItem(
                title = "Contact Us",
                subtitle = "Get in touch with support",
                icon = Icons.Default.Email,
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://opensourcebhaiya.online/contact"))
                    context.startActivity(intent)
                }
            )
            
            val highlightBugReport by viewModel.highlightBugReport.collectAsState()
            val highlightColor: Color by animateColorAsState(
                targetValue = if (highlightBugReport) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                animationSpec = tween(durationMillis = 1000)
            )

            var highlightTriggered by remember { mutableStateOf(false) }
            LaunchedEffect(highlightBugReport) {
                if (highlightBugReport) {
                    scrollState.animateScrollTo(10000)
                    highlightTriggered = true
                    kotlinx.coroutines.delay(400)
                    highlightTriggered = false
                    kotlinx.coroutines.delay(1600)
                    viewModel.clearHighlightBugReport()
                }
            }
            
            val pulseScale by androidx.compose.animation.core.animateFloatAsState(
                targetValue = if (highlightTriggered) 1.05f else 1f,
                animationSpec = tween(400, easing = androidx.compose.animation.core.FastOutSlowInEasing),
                label = "pulse_scale"
            )
            
            SettingsItem(
                title = "Report a Bug",
                subtitle = "Help us improve MailSync",
                icon = Icons.Default.BugReport,
                modifier = Modifier
                    .graphicsLayer(scaleX = pulseScale, scaleY = pulseScale)
                    .background(highlightColor, shape = RoundedCornerShape(8.dp)),
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://opensourcebhaiya.online/bug-report"))
                    context.startActivity(intent)
                }
            )
            
            Spacer(modifier = Modifier.height(16.dp))
                        // App Info
              Column(
                  modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                  horizontalAlignment = Alignment.CenterHorizontally
              ) {
                  Row(
                      verticalAlignment = Alignment.CenterVertically,
                      modifier = Modifier.clickable {
                          val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://opensourcebhaiya.online"))
                          context.startActivity(intent)
                      }.padding(8.dp)
                  ) {
                      Icon(
                          Icons.Default.Language,
                          contentDescription = "Website",
                          tint = MaterialTheme.colorScheme.primary,
                          modifier = Modifier.size(16.dp)
                      )
                      Spacer(modifier = Modifier.width(6.dp))
                      Text(
                          text = "opensourcebhaiya.online",
                          style = MaterialTheme.typography.labelLarge,
                          color = MaterialTheme.colorScheme.primary,
                          fontWeight = FontWeight.Bold
                      )
                  }
                  Spacer(modifier = Modifier.height(4.dp))
                  Text(
                      text = "MailSync v${com.mailsync.app.BuildConfig.VERSION_NAME}",
                      style = MaterialTheme.typography.bodySmall,
                      color = TextSecondary
                  )
              }
            
            Spacer(modifier = Modifier.height(48.dp))
            
            Spacer(modifier = Modifier.height(100.dp))
        }
    }
}

@Composable
fun SettingsItem(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold, color = TextPrimary)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        }
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(24.dp))
    }
}

