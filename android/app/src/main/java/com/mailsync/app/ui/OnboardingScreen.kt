package com.mailsync.app.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import com.mailsync.app.ui.components.CustomToggle
import com.mailsync.app.ui.theme.DarkBackground
import com.mailsync.app.ui.theme.TextPrimary
import com.mailsync.app.ui.theme.TextSecondary

data class OnboardingPage(
    val title: String,
    val description: String,
    val icon: ImageVector,
    val isSetupPage: Boolean = false
)

val onboardingPages = listOf(
    OnboardingPage(
        title = "Auto-fetch OTPs securely",
        description = "We securely extract OTPs directly from your Gmail notifications and emails without ever sending data to a server.",
        icon = Icons.Default.Sync
    ),
    OnboardingPage(
        title = "100% Private & Local",
        description = "Your data never leaves your device. Everything runs completely locally for maximum privacy and security.",
        icon = Icons.Default.Lock
    ),
    OnboardingPage(
        title = "MailSync Companion",
        description = "Link your PC via our Chrome Extension. Receive OTPs directly on your computer. Fully End-to-End Encrypted.",
        icon = Icons.Default.Computer
    ),
    OnboardingPage(
        title = "Setup Background Engines",
        description = "Enable these features so the app can fetch your OTPs instantly and magically copy them while you use other apps.",
        icon = Icons.Default.Sync, 
        isSetupPage = true
    )
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(
    onFinishOnboarding: () -> Unit,
    settingsViewModel: com.mailsync.app.ui.SettingsViewModel
) {
    val pagerState = androidx.compose.foundation.pager.rememberPagerState(pageCount = { onboardingPages.size })
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var showError by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    
    val notificationAccessLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult(),
        onResult = { _ ->
            val isGranted = androidx.core.app.NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
            if (!isGranted) {
                settingsViewModel.setSyncEnabled(false)
                showError = true
            } else {
                showError = false
            }
        }
    )

    val notificationPermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
        onResult = { _ ->
            try {
                notificationAccessLauncher.launch(android.content.Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            } catch (e: Exception) {
                android.util.Log.e("Onboarding", "Failed to open notification settings", e)
            }
        }
    )
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top spacer
            Spacer(modifier = Modifier.height(48.dp))

            val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
            androidx.compose.runtime.LaunchedEffect(pagerState.currentPage) {
                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f)
            ) { position ->
                val page = onboardingPages[position]
                
                // Remove outer calculation to avoid recomposition lag
                
                var isUnlocked by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
                var rotationClicks by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(0) }
                val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
                
                // Animation states
                val rotationAngle by androidx.compose.animation.core.animateFloatAsState(
                    targetValue = rotationClicks * 360f,
                    animationSpec = androidx.compose.animation.core.tween(700, easing = androidx.compose.animation.core.FastOutSlowInEasing)
                )
                
                val bellRotation = androidx.compose.runtime.remember { androidx.compose.animation.core.Animatable(0f) }
                var isRinging by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
                androidx.compose.runtime.LaunchedEffect(isRinging) {
                    if (isRinging) {
                        bellRotation.animateTo(20f, animationSpec = androidx.compose.animation.core.tween(100))
                        bellRotation.animateTo(-20f, animationSpec = androidx.compose.animation.core.tween(100))
                        bellRotation.animateTo(20f, animationSpec = androidx.compose.animation.core.tween(100))
                        bellRotation.animateTo(-20f, animationSpec = androidx.compose.animation.core.tween(100))
                        bellRotation.animateTo(0f, animationSpec = androidx.compose.animation.core.tween(100))
                        isRinging = false
                    }
                }
                
                var hasAnimated by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
                androidx.compose.runtime.LaunchedEffect(pagerState.currentPage) {
                    if (pagerState.currentPage == position && !hasAnimated) {
                        hasAnimated = true
                        if (page.icon == Icons.Default.Sync) {
                            rotationClicks++
                        } else if (page.icon == Icons.Default.Notifications) {
                            if (!isRinging) isRinging = true
                        } else if (page.icon == Icons.Default.Lock) {
                            isUnlocked = true
                            kotlinx.coroutines.delay(500)
                            isUnlocked = false
                        }
                    }
                }

                val displayIcon = if (page.icon == Icons.Default.Lock && isUnlocked) {
                    androidx.compose.material.icons.Icons.Default.LockOpen
                } else {
                    page.icon
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .animateContentSize()
                        .padding(32.dp)
                        .graphicsLayer {
                            val currentOffset = (pagerState.currentPage - position) + pagerState.currentPageOffsetFraction
                            val currentScale = 1f - 0.2f * kotlin.math.abs(currentOffset)
                            val currentAlpha = 1f - 0.5f * kotlin.math.abs(currentOffset)
                            
                            scaleX = currentScale
                            scaleY = currentScale
                            this.alpha = currentAlpha
                            translationX = currentOffset * 100f
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(120.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                            .clickable(
                                interactionSource = androidx.compose.runtime.remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                indication = null
                            ) {
                                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                if (page.icon == Icons.Default.Lock) {
                                    isUnlocked = !isUnlocked
                                } else if (page.icon == Icons.Default.Sync) {
                                    rotationClicks++
                                } else if (page.icon == Icons.Default.Notifications) {
                                    if (!isRinging) isRinging = true
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = displayIcon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .size(64.dp)
                                .graphicsLayer { 
                                    rotationZ = when (page.icon) {
                                        Icons.Default.Sync -> rotationAngle
                                        Icons.Default.Notifications -> bellRotation.value
                                        else -> 0f
                                    }
                                }
                        )
                    }
                    Spacer(modifier = Modifier.height(48.dp))
                    Text(
                        text = page.title,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = page.description,
                        fontSize = 16.sp,
                        color = TextSecondary,
                        textAlign = TextAlign.Center,
                        lineHeight = 24.sp
                    )

                    if (page.isSetupPage) {
                        Spacer(modifier = Modifier.height(32.dp))
                        
                        // Background Clipboard State
                        var canDrawOverlays by androidx.compose.runtime.remember { 
                            androidx.compose.runtime.mutableStateOf(android.provider.Settings.canDrawOverlays(context))
                        }
                        var isNotificationAccessGranted by androidx.compose.runtime.remember {
                            androidx.compose.runtime.mutableStateOf(androidx.core.app.NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName))
                        }
                        var triedOverlay by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
                        
                        val overlayLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                            contract = androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult(),
                            onResult = { _ ->
                                canDrawOverlays = android.provider.Settings.canDrawOverlays(context)
                                if (!canDrawOverlays) {
                                    showError = true
                                } else {
                                    showError = false
                                }
                            }
                        )

                        val batteryOptLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                            contract = androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult(),
                            onResult = { _ ->
                                try {
                                    val intent = Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION, android.net.Uri.parse("package:${context.packageName}"))
                                    overlayLauncher.launch(intent)
                                } catch (e: Exception) {
                                    android.util.Log.e("Onboarding", "Failed to open overlay settings", e)
                                }
                            }
                        )
                        
                        // Recheck permissions when returning to app
                        val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
                        androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
                            val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                                if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                                    canDrawOverlays = android.provider.Settings.canDrawOverlays(context)
                                    isNotificationAccessGranted = androidx.core.app.NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
                                    // Just update the states to reflect OS reality
                                    if (!isNotificationAccessGranted && settingsViewModel.isSyncEnabled.value) {
                                        settingsViewModel.setSyncEnabled(false)
                                    } else if (isNotificationAccessGranted && !settingsViewModel.isSyncEnabled.value) {
                                        settingsViewModel.setSyncEnabled(true)
                                    }
                                    if (!canDrawOverlays && triedOverlay) {
                                        // they tried overlay but didn't grant it, we can ignore this here since launcher handles error
                                    }
                                }
                            }
                            lifecycleOwner.lifecycle.addObserver(observer)
                            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                        }
                        
                        // Instant Sync Toggle
                        val isSyncEnabled by settingsViewModel.isSyncEnabled.collectAsState()
                        
                        Row(
                            modifier = Modifier
                                .fillMaxWidth(0.9f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f, fill = false)) {
                                Text("Instant Sync Engine", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 16.sp)
                                Text("Automatically fetch OTPs in the background", color = TextSecondary, fontSize = 12.sp)
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            CustomToggle(
                                checked = isSyncEnabled && isNotificationAccessGranted,
                                onCheckedChange = { isChecked ->
                                    if (isChecked) {
                                        if (!isNotificationAccessGranted) {
                                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                                                notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                                            } else {
                                                try {
                                                    val intent = Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
                                                    context.startActivity(intent)
                                                } catch (e: Exception) {
                                                    android.util.Log.e("Onboarding", "Failed to open notification settings", e)
                                                }
                                            }
                                        } else {
                                            settingsViewModel.setSyncEnabled(true)
                                        }
                                    } else {
                                        try {
                                            val intent = Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
                                            context.startActivity(intent)
                                        } catch (e: Exception) {
                                            android.util.Log.e("Onboarding", "Failed to open notification settings", e)
                                        }
                                    }
                                }
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Row(
                            modifier = Modifier
                                .fillMaxWidth(0.9f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f, fill = false)) {
                                Text("Background Clipboard", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 16.sp)
                                Text("Copy OTPs while using other apps", color = TextSecondary, fontSize = 12.sp)
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            CustomToggle(
                                checked = canDrawOverlays,
                                onCheckedChange = { isChecked ->
                                    if (isChecked) {
                                        triedOverlay = true
                                        val pm = context.getSystemService(android.content.Context.POWER_SERVICE) as android.os.PowerManager
                                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M && !pm.isIgnoringBatteryOptimizations(context.packageName)) {
                                            try {
                                                val intent = Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                                    data = android.net.Uri.parse("package:${context.packageName}")
                                                }
                                                batteryOptLauncher.launch(intent)
                                            } catch (e: Exception) {
                                                val intent = Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION, android.net.Uri.parse("package:${context.packageName}"))
                                                overlayLauncher.launch(intent)
                                            }
                                        } else {
                                            val intent = Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION, android.net.Uri.parse("package:${context.packageName}"))
                                            overlayLauncher.launch(intent)
                                        }
                                    } else {
                                        // The user cannot programmatically revoke DrawOverlays.
                                        // They have to go to settings.
                                        val intent = Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION, android.net.Uri.parse("package:${context.packageName}"))
                                        context.startActivity(intent)
                                    }
                                }
                            )
                        }

                        val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
                        androidx.compose.runtime.LaunchedEffect(isSyncEnabled, canDrawOverlays) {
                            if (isSyncEnabled && canDrawOverlays) {
                                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                            }
                        }

                        androidx.compose.animation.AnimatedVisibility(visible = isSyncEnabled && canDrawOverlays) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
                                Icon(Icons.Default.CheckCircle, contentDescription = "Ready", tint = Color(0xFF10B981), modifier = Modifier.size(48.dp))
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("All set and ready to go!", color = Color(0xFF10B981), fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Bottom section
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    repeat(onboardingPages.size) { index ->
                        val isSelected = pagerState.currentPage == index
                        val width by animateDpAsState(targetValue = if (isSelected) 24.dp else 8.dp)
                        Box(
                            modifier = Modifier
                                .height(8.dp)
                                .width(width)
                                .clip(CircleShape)
                                .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.DarkGray)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(32.dp))

                if (pagerState.currentPage == onboardingPages.size - 1) {
                    Button(
                        onClick = { onFinishOnboarding() },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 32.dp, vertical = 16.dp),
                        modifier = Modifier.fillMaxWidth(0.8f)
                    ) {
                        Text("Get Started", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    }
                } else {
                    Button(
                        onClick = {
                            scope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage + 1)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
                    ) {
                        Icon(Icons.Default.ArrowForward, contentDescription = "Next", tint = Color.White)
                    }
                }
            }
        }
        
        androidx.compose.animation.AnimatedVisibility(
            visible = showError,
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFE53935))
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Please allow both required permissions for features to work flawlessly.",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    fontSize = 14.sp
                )
            }
        }
    }
}
