package com.mailsync.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.foundation.layout.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.compose.ui.graphics.graphicsLayer
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.alpha

import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Notifications

import android.content.Intent

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Home : Screen("home", "Home", Icons.Filled.Home)
    object Inbox : Screen("inbox", "Inbox", Icons.Filled.Email)
    object Settings : Screen("settings", "Settings", Icons.Filled.Settings)
    object Accounts : Screen("accounts", "Accounts", Icons.Filled.Email)
}

@Composable
fun AppNavigation(
    otpViewModel: OtpViewModel,
    historyViewModel: OtpHistoryViewModel,
    settingsViewModel: SettingsViewModel,

    currentIntent: Intent? = null
) {
    val navController = rememberNavController()
    val context = androidx.compose.ui.platform.LocalContext.current
    val isConfigured by settingsViewModel.isConfigured.collectAsState(initial = true)
    
    LaunchedEffect(currentIntent) {
        currentIntent?.data?.let { uri ->
            if (uri.scheme == "gmailotpsyncer") {
                when (uri.host) {
                    "scan" -> {
                        navController.navigate("home") {
                            popUpTo(0)
                        }
                        otpViewModel.fetchOtps()
                    }
                    "settings" -> {
                        navController.navigate("settings") {
                            popUpTo(0)
                        }
                    }
                    "bug_report" -> {
                        if (isConfigured) {
                            navController.navigate("settings") {
                                popUpTo(0)
                            }
                            settingsViewModel.triggerHighlightBugReport()
                        } else {
                            com.mailsync.app.utils.ToastManager.show(
                                context, "Whoa buddy! \uD83D\uDE05 Please test the app first before reporting bugs!",
                                android.widget.Toast.LENGTH_LONG
                            )
                            navController.navigate("onboarding") {
                                popUpTo(0)
                            }
                        }
                    }
                }
                // Clear the data to prevent re-triggering navigation on recomposition
                currentIntent.data = null
            }
        }
    }
    val items = listOf(Screen.Home, Screen.Inbox, Screen.Settings)

    Scaffold(
        bottomBar = {
            val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
            val navBackStackEntry by navController.currentBackStackEntryAsState()
            val currentDestination = navBackStackEntry?.destination
            val route = currentDestination?.route
            val showBottomBar = route != null && (
                route.startsWith(Screen.Home.route) ||
                route.startsWith(Screen.Inbox.route) ||
                route.startsWith(Screen.Settings.route) ||
                route.startsWith(Screen.Accounts.route)
            )

            if (showBottomBar) {
                NavigationBar(
                    containerColor = com.mailsync.app.ui.theme.DarkSurface,
                    contentColor = com.mailsync.app.ui.theme.TextPrimary
                ) {

                    items.forEach { screen ->
                    val selected = currentDestination?.hierarchy?.any { it.route?.startsWith(screen.route) == true } == true

                    // Animation 1: Smooth color transition
                    val iconColor by animateColorAsState(
                        targetValue = if (selected) com.mailsync.app.ui.theme.PurpleAccent else com.mailsync.app.ui.theme.TextSecondary
                    )
                    
                    // Animation 2: Spring scale-up bounce on select
                    val iconScale by animateFloatAsState(
                        targetValue = if (selected) 1.2f else 1.0f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessLow
                        )
                    )

                    // Animation 3: Rotation for Settings
                    val iconRotation by animateFloatAsState(
                        targetValue = if (selected && screen == Screen.Settings) 90f else 0f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessLow
                        )
                    )

                    NavigationBarItem(
                        icon = {
                            Icon(
                                imageVector = screen.icon,
                                contentDescription = screen.title,
                                modifier = Modifier
                                    .scale(iconScale)
                                    .rotate(iconRotation),
                                tint = iconColor
                            )
                        },
                        label = { Text(screen.title) },
                        selected = selected,
                        onClick = {
                            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = com.mailsync.app.ui.theme.PurpleAccent,
                            unselectedIconColor = com.mailsync.app.ui.theme.TextSecondary,
                            selectedTextColor = com.mailsync.app.ui.theme.PurpleAccent,
                            unselectedTextColor = com.mailsync.app.ui.theme.TextSecondary,
                            indicatorColor = com.mailsync.app.ui.theme.DarkSurfaceVariant
                        )
                    )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "splash",
            modifier = Modifier.padding(innerPadding),
            enterTransition = { androidx.compose.animation.EnterTransition.None },
            exitTransition = { androidx.compose.animation.ExitTransition.None },
            popEnterTransition = { androidx.compose.animation.EnterTransition.None },
            popExitTransition = { androidx.compose.animation.ExitTransition.None }
        ) {
            composable("splash") {
                val isConfigured by settingsViewModel.isConfigured.collectAsState()
                val hasSeenOnboarding by settingsViewModel.hasSeenOnboarding.collectAsState()
                if (!hasSeenOnboarding) {
                    LaunchedEffect(Unit) {
                        navController.navigate("onboarding") {
                            popUpTo("splash") { inclusive = true }
                        }
                    }
                } else {
                    SplashScreen(onTimeout = {
                        navController.navigate(Screen.Home.route) {
                            popUpTo("splash") { inclusive = true }
                        }
                    })
                }
            }
            composable("onboarding") {
                OnboardingScreen(
                    onFinishOnboarding = {
                        settingsViewModel.setHasSeenOnboarding(true)
                        navController.navigate(Screen.Home.route) {
                            popUpTo("onboarding") { inclusive = true }
                        }
                    },
                    settingsViewModel = settingsViewModel
                )
            }
            composable("setup") {
                SetupScreen(
                    viewModel = settingsViewModel,
                    onSetupComplete = {
                        navController.navigate(Screen.Home.route) {
                            popUpTo("setup") { inclusive = true }
                        }
                    }
                )
            }
            composable(Screen.Home.route) {
                val hasSeenOnboarding by settingsViewModel.hasSeenOnboarding.collectAsState()
                if (!hasSeenOnboarding) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(32.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(64.dp))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Welcome to MailSync!",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Kindly onboard first for the best experience and understanding of the app.",
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center,
                            color = com.mailsync.app.ui.theme.TextSecondary
                        )
                        Spacer(modifier = Modifier.height(32.dp))
                        Button(
                            onClick = {
                                navController.navigate("onboarding") {
                                    popUpTo(Screen.Home.route) { inclusive = true }
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(50.dp)
                        ) {
                            Text("Start Onboarding", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                } else {
                    HomeScreen(viewModel = otpViewModel, historyViewModel = historyViewModel, settingsViewModel = settingsViewModel, onNavigateToSettings = { highlight ->
                        val route = if (highlight != null) "${Screen.Settings.route}?highlight=$highlight" else Screen.Settings.route
                        navController.navigate(route)
                    }, onNavigateToAccounts = { highlight ->
                        val route = if (highlight != null) "${Screen.Accounts.route}?highlight=$highlight" else Screen.Accounts.route
                        navController.navigate(route)
                    }, onNavigateToScanner = {
                        navController.navigate("qr_scanner")
                    })
                }
            }
            composable("qr_scanner") {
                QRScannerScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onQrCodeScanned = { qrContent ->
                        val isMailSync = qrContent.contains("mailsync/connect?uuid=") || qrContent.startsWith("mailsync://connect")
                        if (isMailSync) {
                            try {
                                val uri = android.net.Uri.parse(qrContent)
                                val uuid = uri.getQueryParameter("uuid") ?: ""
                                val pcName = uri.getQueryParameter("name") ?: "PC"
                                val browser = uri.getQueryParameter("browser") ?: "Browser"
                                val keyBase64 = uri.getQueryParameter("key") ?: ""

                                if (uuid.isNotEmpty() && keyBase64.isNotEmpty()) {
                                    val dateLinked = java.text.SimpleDateFormat("MMM dd 'at' h:mm a", java.util.Locale.getDefault()).format(java.util.Date())
                                    settingsViewModel.linkDevice(uuid, keyBase64, pcName, browser, dateLinked)
                                    android.widget.Toast.makeText(context, "✅ PC Linked Successfully!", android.widget.Toast.LENGTH_SHORT)
                                } else {
                                    com.mailsync.app.utils.ToastManager.show(context, "Invalid MailSync QR Code", android.widget.Toast.LENGTH_SHORT)
                                }
                            } catch (e: Exception) {
                                com.mailsync.app.utils.ToastManager.show(context, "Error parsing QR Code", android.widget.Toast.LENGTH_SHORT)
                            }
                        } else if (android.util.Patterns.WEB_URL.matcher(qrContent).matches()) {
                            // Already handled by QRScannerScreen's LaunchedEffect, but kept here just in case
                        } else {
                            // Otherwise, copy to clipboard
                            val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            val clip = android.content.ClipData.newPlainText("Scanned QR", qrContent)
                            clipboard.setPrimaryClip(clip)
                            com.mailsync.app.utils.ToastManager.show(context, "Text copied to clipboard", android.widget.Toast.LENGTH_SHORT)
                        }
                        navController.popBackStack()
                    }
                )
            }
            composable(Screen.Inbox.route) {
                OtpInboxScreen(viewModel = historyViewModel)
            }
            composable(
                route = Screen.Settings.route + "?highlight={highlight}",
                arguments = listOf(androidx.navigation.navArgument("highlight") { 
                    type = androidx.navigation.NavType.StringType
                    nullable = true 
                    defaultValue = null
                })
            ) { backStackEntry ->
                val highlight = backStackEntry.arguments?.getString("highlight")
                SettingsScreen(viewModel = settingsViewModel, highlight = highlight, onNavigateToAccounts = {
                    navController.navigate(Screen.Accounts.route)
                }, onNavigateToDevices = {
                    navController.navigate("devices")
                })
            }
            composable("devices") {
                DevicesScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToScanner = { navController.navigate("qr_scanner") },
                    onNavigateToAccounts = { navController.navigate(Screen.Accounts.route) },
                    viewModel = settingsViewModel
                )
            }
            composable(
                route = Screen.Accounts.route + "?highlight={highlight}",
                arguments = listOf(androidx.navigation.navArgument("highlight") { 
                    type = androidx.navigation.NavType.StringType
                    nullable = true 
                    defaultValue = null
                })
            ) { backStackEntry ->
                val highlight = backStackEntry.arguments?.getString("highlight")
                AccountsScreen(viewModel = settingsViewModel, highlight = highlight, onNavigateBack = {
                    navController.popBackStack()
                })
            }
        }
    }
}

@Composable
fun SplashScreen(onTimeout: () -> Unit) {
    var startAnimation by remember { mutableStateOf(false) }
    
    val quotes = listOf(
        "Over 1.8 billion people use Gmail worldwide.",
        "Gmail blocks 99.9% of spam, phishing, and malware.",
        "Your OTPs are sensitive and should never be shared.",
        "Gmail processes over 100 billion emails every day.",
        "100% Private, everything runs locally on your device.",
        "Enable 2FA on your Google Account for maximum security.",
        "OTP Syncer never sends your emails to external servers.",
        "Automated systems send millions of OTPs every single minute.",
        "Security is a journey, not a destination.",
        "Keep your device updated to ensure the best security.",
        "OTP Syncer uses the official Google APIs for safety.",
        "Google's advanced AI protects your inbox from threats.",
        "Your data stays on your device, always.",
        "We respect your privacy by doing all processing locally.",
        "Fast, secure, and reliable OTP synchronization.",
        "Never miss a login code again.",
        "Your workflow, uninterrupted.",
        "Seamless connection between your phone and PC.",
        "AES-256 encryption protects your data in transit.",
        "Syncing securely via Firebase Realtime Database.",
        "No more typing 6-digit codes manually.",
        "Efficiency meets security.",
        "Designed for professionals who value their time.",
        "Copying your OTPs so you don't have to.",
        "Automatically detecting codes in milliseconds.",
        "Built with privacy-first architecture.",
        "Open source technologies power the modern web.",
        "Simplifying your 2FA experience.",
        "The most secure way to handle your verification codes.",
        "Zero-knowledge architecture keeps your data safe.",
        "Protecting your digital identity, one OTP at a time.",
        "Seamlessly bridging your mobile and desktop devices.",
        "Empowering you with fast and secure logins.",
        "Security is not just a feature, it's our foundation.",
        "Experience the magic of instant OTP sync.",
        "Say goodbye to manually retyping codes.",
        "Your digital life, secured and simplified.",
        "We believe in technology that works for you.",
        "Trust is built through transparency and security.",
        "Empowering productivity with secure automation.",
        "The modern way to handle two-factor authentication.",
        "Your security is our top priority.",
        "Fast, frictionless, and secure by design.",
        "Protecting your accounts with advanced encryption.",
        "Seamless OTP delivery, right to your clipboard.",
        "Focus on your work, we'll handle the OTPs.",
        "Security that doesn't slow you down.",
        "The smartest way to sync your verification codes.",
        "Built for speed, engineered for security.",
        "Your seamless login experience starts here.",
        "Empowering secure connections across your devices.",
        "The ultimate tool for managing your OTPs.",
        "Security and convenience, perfectly balanced.",
        "Your digital security, simplified.",
        "Experience the future of two-factor authentication.",
        "Fast, reliable, and always secure.",
        "Your trusted companion for secure logins.",
        "Seamlessly syncing your OTPs in real-time.",
        "The intelligent way to manage your verification codes.",
        "Empowering your digital life with secure automation.",
        "Security you can trust, convenience you will love.",
        "Your fast track to secure logins.",
        "The elegant solution for two-factor authentication.",
        "Seamlessly integrating security into your workflow.",
        "Empowering you with fast, secure, and reliable OTP sync.",
        "Your digital identity, protected and simplified.",
        "The modern standard for secure OTP management.",
        "Fast, frictionless, and incredibly secure.",
        "Your seamless, secure login experience.",
        "Empowering your digital journey with secure automation.",
        "The intelligent, secure way to handle OTPs.",
        "Security that works with you, not against you.",
        "Your fast, reliable, and secure OTP companion.",
        "Seamlessly bridging the gap between security and convenience.",
        "Empowering you with the ultimate OTP sync experience.",
        "Your digital life, secured with advanced encryption.",
        "The smartest, most secure way to manage your codes.",
        "Fast, reliable, and designed with your privacy in mind.",
        "Your trusted solution for seamless secure logins.",
        "Seamlessly syncing your OTPs with zero-knowledge architecture.",
        "Empowering your productivity with secure, instant OTP delivery.",
        "The modern, secure approach to two-factor authentication.",
        "Security that empowers your digital lifestyle.",
        "Your fast, secure, and reliable login companion.",
        "Seamlessly integrating advanced security into your daily routine.",
        "Empowering you with the tools for a secure digital life.",
        "Your digital identity, safeguarded by cutting-edge encryption.",
        "The intelligent, privacy-first way to manage your OTPs.",
        "Fast, reliable, and uncompromisingly secure.",
        "Your trusted partner for seamless, secure verification.",
        "Seamlessly syncing your codes with unparalleled security.",
        "Empowering your digital experience with secure, instant automation.",
        "The modern, elegant solution for secure logins.",
        "Security that enhances your workflow.",
        "Your fast, secure, and intuitive OTP sync tool.",
        "Seamlessly bridging your devices with robust security.",
        "Empowering you to take control of your digital security.",
        "Your digital life, protected by our privacy-first approach.",
        "The smartest, most reliable way to handle your verification needs.",
        "Fast, secure, and designed to simplify your life."
    )
    val randomQuote = remember { quotes.random() }
    
    val infiniteTransition = androidx.compose.animation.core.rememberInfiniteTransition()
    val blinkScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.3f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(500, easing = androidx.compose.animation.core.FastOutSlowInEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        )
    )
    val blinkAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(500, easing = androidx.compose.animation.core.FastOutSlowInEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        )
    )
    
    val alphaAnim by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0f,
        animationSpec = androidx.compose.animation.core.tween(
            durationMillis = 800,
            easing = androidx.compose.animation.core.FastOutSlowInEasing
        )
    )

    LaunchedEffect(key1 = true) {
        startAnimation = true
        kotlinx.coroutines.delay(2500) // Give them time to read the quote
        onTimeout()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(com.mailsync.app.ui.theme.DarkBackground),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.foundation.layout.Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            val rotation by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                    animation = androidx.compose.animation.core.tween(2000, easing = androidx.compose.animation.core.LinearEasing),
                    repeatMode = androidx.compose.animation.core.RepeatMode.Restart
                )
            )

            androidx.compose.foundation.Canvas(
                modifier = Modifier
                    .size(80.dp)
                    .graphicsLayer { rotationZ = rotation }
            ) {
                // Draw a very thin, elegant arc (like an eclipse/crescent)
                drawArc(
                    color = androidx.compose.ui.graphics.Color.White,
                    startAngle = -90f,
                    sweepAngle = 180f,
                    useCenter = false,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                        width = 4f,
                        cap = androidx.compose.ui.graphics.StrokeCap.Round
                    ),
                    alpha = blinkAlpha
                )
            }
            
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(64.dp))
            
            Text(
                text = "\"$randomQuote\"",
                style = MaterialTheme.typography.bodyLarge,
                color = com.mailsync.app.ui.theme.TextPrimary,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Serif,
                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                modifier = Modifier.alpha(alphaAnim),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

