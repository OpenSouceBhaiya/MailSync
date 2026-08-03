package com.mailsync.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.mailsync.app.data.OtpRepository
import com.mailsync.app.data.SettingsManager
import com.mailsync.app.ui.AppNavigation
import com.mailsync.app.ui.OtpViewModel
import com.mailsync.app.ui.OtpHistoryViewModel
import com.mailsync.app.ui.SettingsViewModel
import com.mailsync.app.ui.theme.GmailOtpSyncerTheme
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat


import android.view.WindowManager
import androidx.fragment.app.FragmentActivity
import androidx.compose.runtime.*
import androidx.compose.material3.*
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.background

import com.mailsync.app.ui.BiometricHelper
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.lazy.LazyColumn
import android.content.Context
import android.content.ClipboardManager
import android.content.ClipData
import android.widget.Toast

class MainActivity : FragmentActivity() {

    // Lazy initialization of our dependencies
    private val settingsManager by lazy { SettingsManager(applicationContext) }
    private val firebaseManager by lazy { com.mailsync.app.data.FirebaseManager() }
    private val otpRepository by lazy { OtpRepository(applicationContext, settingsManager, firebaseManager) }
    private val biometricHelper by lazy { BiometricHelper(this) }

    // Initialize ViewModels using our custom Factories so we can pass in the repository
    private val otpViewModel: OtpViewModel by viewModels {
        OtpViewModel.Factory(applicationContext, otpRepository, settingsManager)
    }
    
    private val settingsViewModel: SettingsViewModel by viewModels {
        SettingsViewModel.Factory(applicationContext, otpRepository, settingsManager, firebaseManager)
    }

    private val historyViewModel: OtpHistoryViewModel by viewModels {
        OtpHistoryViewModel.Factory(otpRepository, settingsManager)
    }


    // Polling service removed since backend handles it

    override fun onResume() {
        super.onResume()
        // Reset adaptive polling to fast interval when app is opened
        com.mailsync.app.AppState.lastActiveTimeMs = System.currentTimeMillis()
    }

    private val currentIntent = kotlinx.coroutines.flow.MutableStateFlow<Intent?>(null)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        currentIntent.value = intent
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Install the splash screen before super.onCreate()
        installSplashScreen()
        
        super.onCreate(savedInstanceState)
        
        // --- GLOBAL CRASH REPORTER ---
        val crashPrefs = getSharedPreferences("crash_logs", android.content.Context.MODE_PRIVATE)
        Thread.setDefaultUncaughtExceptionHandler { thread, exception ->
            val stackTrace = android.util.Log.getStackTraceString(exception)
            crashPrefs.edit().putString("last_crash", stackTrace).commit()
            
            // Kill the process so Android doesn't show the generic ANR/Crash dialog
            android.os.Process.killProcess(android.os.Process.myPid())
            System.exit(1)
        }
        
        settingsManager.incrementAppOpenCount()
        
        if (settingsManager.isConfigured() && settingsManager.isSyncEnabled()) {
            com.mailsync.app.service.ServiceHelper.startForegroundService(this)
        }
        currentIntent.value = intent
        setContent {
            var isUnlocked by remember { mutableStateOf(!settingsManager.isBiometricLockEnabled()) }
            var authError by remember { mutableStateOf<String?>(null) }
            val intentState by currentIntent.collectAsState()
            
            val lastCrash = crashPrefs.getString("last_crash", null)
            var showCrashDialog by remember { mutableStateOf(lastCrash != null) }

            GmailOtpSyncerTheme {
                if (showCrashDialog && lastCrash != null) {
                    val context = LocalContext.current
                    AlertDialog(
                        onDismissRequest = { },
                        title = { 
                            Text("Oops! MailSync hit a bump \uD83D\uDE1E", fontWeight = FontWeight.Bold, color = Color.White) 
                        },
                        text = { 
                            Column {
                                Text("\"Every bug is a feature waiting to be born.\" \uD83D\uDE80", fontStyle = androidx.compose.ui.text.font.FontStyle.Italic, color = Color(0xFFA1A1AA))
                                Spacer(modifier = Modifier.height(16.dp))
                                Text("The app unexpectedly crashed last time. Please help us fix it by reporting this bug!", color = Color.White)
                                Spacer(modifier = Modifier.height(16.dp))
                                Text("Steps to report:", fontWeight = FontWeight.Bold, color = Color.White)
                                Text("1. Click 'Copy & Report' below", color = Color(0xFFA1A1AA))
                                Text("2. Paste the log in our bug tracker", color = Color(0xFFA1A1AA))
                                Text("3. You'll be redirected to: opensourcebhaiya.online/bug-report", color = Color(0xFFA1A1AA))
                                Spacer(modifier = Modifier.height(16.dp))
                                Box(modifier = Modifier.background(Color(0xFF2D2938), RoundedCornerShape(8.dp)).padding(8.dp).fillMaxHeight(0.3f)) {
                                    LazyColumn {
                                        item { Text(lastCrash, style = MaterialTheme.typography.bodySmall, color = Color.LightGray) }
                                    }
                                }
                            }
                        },
                        confirmButton = {
                            Button(onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("Crash Log", lastCrash))
                                Toast.makeText(context, "Crash log copied! Opening browser...", Toast.LENGTH_SHORT).show()
                                
                                val browserIntent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://opensourcebhaiya.online/bug-report"))
                                context.startActivity(browserIntent)
                                
                                crashPrefs.edit().remove("last_crash").apply()
                                showCrashDialog = false
                            }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)) {
                                Text("Copy & Report")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = {
                                crashPrefs.edit().remove("last_crash").apply()
                                showCrashDialog = false
                            }) {
                                Text("Dismiss", color = Color.Gray)
                            }
                        },
                        containerColor = Color(0xFF1E1926)
                    )
                } else if (isUnlocked) {
                    AppNavigation(
                        otpViewModel = otpViewModel,
                        historyViewModel = historyViewModel,
                        settingsViewModel = settingsViewModel,

                        currentIntent = intentState
                    )
                } else {
                    // Lock Screen
                    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text("App Locked", style = MaterialTheme.typography.headlineMedium)
                            Spacer(modifier = Modifier.height(16.dp))
                            if (authError != null) {
                                Text(authError!!, color = MaterialTheme.colorScheme.error)
                                Spacer(modifier = Modifier.height(16.dp))
                            }
                            Button(onClick = {
                                biometricHelper.authenticate(
                                    onSuccess = { isUnlocked = true },
                                    onError = { authError = it }
                                )
                            }) {
                                Text("Unlock")
                            }
                        }
                    }
                    
                    // Trigger auth on launch
                    LaunchedEffect(Unit) {
                        biometricHelper.authenticate(
                            onSuccess = { isUnlocked = true },
                            onError = { authError = it }
                        )
                    }
                }
            }
        }
    }
}
