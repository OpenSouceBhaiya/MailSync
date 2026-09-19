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
                    "sync_mode" -> {
                        if (isConfigured) {
                            navController.navigate("settings") {
                                popUpTo(0)
                            }
                            settingsViewModel.triggerHighlightSyncMode()
                        } else {
                            navController.navigate("onboarding") {
                                popUpTo(0)
                            }
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
                    "contact" -> {
                        navController.navigate("settings") {
                            popUpTo(0)
                        }
                        settingsViewModel.triggerHighlightContactUs()
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
                route.startsWith(Screen.Settings.route)
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
                    }, onNavigateToScanner = {
                        navController.navigate("qr_scanner")
                    })
                }
            }
            composable("qr_scanner") {
                QRScannerScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onQrCodeScanned = { qrContent ->
                        val isMailSync = qrContent.contains("connect?uuid=") && qrContent.contains("&key=")
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
                                    android.widget.Toast.makeText(context, "✅ PC Linked Successfully!", android.widget.Toast.LENGTH_SHORT).show()
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
                SettingsScreen(viewModel = settingsViewModel, highlight = highlight, onNavigateToDevices = {
                    navController.navigate("devices")
                })
            }
            composable("devices") {
                DevicesScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToScanner = { navController.navigate("qr_scanner") },
                    viewModel = settingsViewModel
                )
            }

        }
    }
}

@Composable
fun SplashScreen(onTimeout: () -> Unit) {
    var startAnimation by remember { mutableStateOf(false) }
    
    val quotes = listOf(
        "Automating the mundane to focus on the meaningful.",
        "Your workflow, uninterrupted.",
        "Security is a process, not a product.",
        "Efficiency is doing things right; effectiveness is doing the right things.",
        "The best code is no code at all.",
        "Simplicity is the soul of efficiency.",
        "Don't repeat yourself. Let us do it for you.",
        "Work smarter, not harder.",
        "Time is your most valuable asset. Save it.",
        "Focus on what matters. We'll handle the rest.",
        "Productivity is being able to do things that you were never able to do before.",
        "Every minute saved is a minute earned.",
        "A tool is only as good as the time it saves.",
        "Frictionless security for a seamless day.",
        "Less typing, more doing.",
        "Innovation is taking two things that exist and putting them together in a new way.",
        "Streamlining your digital life.",
        "Because you have better things to do than copy-paste.",
        "Your attention is precious. Guard it.",
        "Zero latency, infinite productivity.",
        "The fewer moving parts, the better.",
        "Stop context switching. Start achieving.",
        "Flow state achieved.",
        "Technology should work for you, not the other way around.",
        "Empowering your digital journey.",
        "Where security meets convenience.",
        "The magic of automation at your fingertips.",
        "Simplify, then automate.",
        "Reclaim your focus.",
        "Bridging the gap between devices.",
        "Privacy first, productivity always.",
        "Eliminating the bottlenecks in your day.",
        "The future of workflow is automated.",
        "Do more with less effort.",
        "Your time is finite. Optimize it.",
        "Security that doesn't slow you down.",
        "Seamless integration, effortless operation.",
        "The fastest way to get back to work.",
        "Unlock your true potential.",
        "Because manual data entry is so last decade.",
        "Your digital assistant, always ready.",
        "Speed is a feature.",
        "Design is not just what it looks like, it's how it works.",
        "The power of less.",
        "Focus is saying no to a thousand good ideas.",
        "Small optimizations lead to massive gains.",
        "Work in the flow, stay in the zone.",
        "The ultimate productivity hack.",
        "Secure by design, fast by default.",
        "Every keystroke saved is a victory.",
        "Your workspace, synchronized.",
        "Automate the routine, humanize the exception.",
        "The invisible bridge between your devices.",
        "Making the complex simple.",
        "Productivity is a mindset, automation is the tool.",
        "Stay focused, stay secure.",
        "The smartest way to work.",
        "Your digital ecosystem, harmonized.",
        "Less friction, more action.",
        "The elegance of a streamlined workflow.",
        "Reinventing the way you connect.",
        "Because your time is worth more.",
        "The silent engine of your productivity.",
        "Security without the hassle.",
        "Automate to innovate.",
        "The shortest path between thought and action.",
        "Your workflow, optimized.",
        "The joy of a frictionless experience.",
        "Technology that gets out of your way.",
        "Maximizing your output, minimizing your input.",
        "The art of digital efficiency.",
        "Seamlessly connecting your digital world.",
        "Productivity unleashed.",
        "The smart way to handle security.",
        "Your daily dose of digital efficiency.",
        "Automating the little things so you can focus on the big things.",
        "The future is automated.",
        "Your digital life, simplified.",
        "The perfect balance of speed and security.",
        "Empowering you to do your best work.",
        "The seamless connection you've been waiting for.",
        "Productivity is about working smarter.",
        "The ultimate tool for the modern professional.",
        "Security that empowers, rather than hinders.",
        "Your workflow, supercharged.",
        "The quiet power of automation.",
        "Making your digital life easier, one OTP at a time.",
        "The smart solution for a seamless day.",
        "Your digital identity, protected and accessible.",
        "The elegance of true efficiency.",
        "Automate the boring stuff.",
        "The fastest way to authenticate.",
        "Your workspace, unified.",
        "The invisible hand of productivity.",
        "Security made simple.",
        "The future of digital interaction.",
        "Your daily workflow, perfected.",
        "The seamless link between your devices.",
        "Productivity, redefined.",
        "The smart approach to security.",
        "Your digital world, synchronized.",
        "The art of working smarter.",
        "Automating for a better tomorrow.",
        "The silent partner in your success.",
        "Your workflow, elevated.",
        "The seamless experience you deserve.",
        "Productivity is the engine of progress.",
        "The smart way to stay secure.",
        "Your digital life, optimized.",
        "The elegant approach to authentication.",
        "Automate, optimize, succeed.",
        "The fastest path to productivity.",
        "Your workspace, connected.",
        "The invisible thread of efficiency.",
        "Security that works for you.",
        "The future of secure workflows.",
        "Your daily tasks, simplified.",
        "The seamless connection between your tools.",
        "Productivity at the speed of thought.",
        "The smart solution for digital security.",
        "Your digital ecosystem, united.",
        "The art of seamless integration.",
        "Automating your path to success.",
        "The silent force behind your productivity.",
        "Your workflow, perfected.",
        "The seamless transition between devices.",
        "Productivity is the key to unlocking potential.",
        "The smart way to handle authentication.",
        "Your digital world, harmonized.",
        "The elegant solution for a complex digital life.",
        "Automate the trivial, focus on the vital.",
        "The fastest way to securely connect.",
        "Your workspace, seamlessly integrated.",
        "The invisible bridge to higher productivity.",
        "Security that enhances your day.",
        "The future of seamless digital experiences.",
        "Your daily workflow, optimized for success.",
        "The seamless link that powers your day.",
        "Productivity through intelligent automation.",
        "The smart approach to digital identity.",
        "Your digital life, effortlessly synchronized.",
        "The art of frictionless security.",
        "Automating for peak performance.",
        "The silent enabler of your best work.",
        "Your workflow, intelligently automated.",
        "The seamless connection for the modern worker.",
        "Productivity is doing more with less friction.",
        "The smart way to bridge your devices.",
        "Your digital world, securely united.",
        "The elegant path to digital efficiency.",
        "Automate your way to a clearer mind.",
        "The fastest route to secure authentication.",
        "Focus on the signal, ignore the noise.",
        "Your workspace, effortlessly connected.",
        "Security designed for the speed of business.",
        "The invisible catalyst for your productivity.",
        "Small automations yield massive time savings.",
        "Eliminate friction. Maximize flow.",
        "The art of working without interruption.",
        "Your digital ecosystem, working in harmony."
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
        kotlinx.coroutines.delay(3000) // Give them time to read the quote
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

