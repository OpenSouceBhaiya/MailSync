package com.mailsync.app.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

data class LinkedDevice(
    val id: String,
    val name: String,
    val browser: String,
    val dateLinked: String,
    val status: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevicesScreen(
    onNavigateBack: () -> Unit,
    onNavigateToScanner: () -> Unit,
    onNavigateToAccounts: () -> Unit,
    viewModel: SettingsViewModel
) {
    val devices by viewModel.linkedDevices.collectAsState()
    var selectedDevice by remember { mutableStateOf<LinkedDevice?>(null) }
    var showBottomSheet by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf<LinkedDevice?>(null) }
    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()
    var deletingDeviceIds by remember { mutableStateOf(setOf<String>()) }
    val context = androidx.compose.ui.platform.LocalContext.current

    if (showRenameDialog != null) {
        var newName by remember { mutableStateOf(showRenameDialog!!.name) }
        AlertDialog(
            onDismissRequest = { showRenameDialog = null },
            title = { Text("Rename PC", color = Color.White) },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF00FFA3)
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = { 
                    viewModel.renameLinkedDevice(showRenameDialog!!.id, newName)
                    showRenameDialog = null
                }) {
                    Text("Save", color = Color(0xFF00FFA3))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = null }) {
                    Text("Cancel", color = Color.Gray)
                }
            },
            containerColor = Color(0xFF1E1926)
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Linked PCs", color = Color.White, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF120F17))
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNavigateToScanner,
                containerColor = Color(0xFF00FFA3),
                contentColor = Color.Black
            ) {
                Icon(Icons.Default.Add, contentDescription = "Link New PC")
            }
        },
        containerColor = Color(0xFF120F17)
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text("Active Sessions", color = Color(0xFF00FFA3), fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Spacer(modifier = Modifier.height(8.dp))
            }

            if (devices.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 80.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(80.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF00FFA3).copy(alpha = 0.1f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Devices, contentDescription = null, tint = Color(0xFF00FFA3), modifier = Modifier.size(40.dp))
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            text = "No PCs Linked",
                            color = Color.White,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "To start syncing OTPs seamlessly, install the MailSync Companion extension on your PC.",
                            color = Color.Gray,
                            fontSize = 15.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 24.dp)
                        )
                        Spacer(modifier = Modifier.height(48.dp))
                        
                        val localUriHandler = androidx.compose.ui.platform.LocalUriHandler.current
                        Button(
                            onClick = { localUriHandler.openUri("https://opensourcebhaiya.online/apps/mailsync") },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00FFA3), contentColor = Color.Black),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).height(56.dp)
                        ) {
                            Icon(Icons.Default.Download, contentDescription = "Download")
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Download PC Extension", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                    }
                }
            } else {
                items(devices, key = { it.id }) { device ->
                    val isDeleting = deletingDeviceIds.contains(device.id)

                    AnimatedVisibility(
                        visible = !isDeleting,
                        exit = fadeOut(animationSpec = tween(400)) + 
                               scaleOut(targetScale = 0.8f, animationSpec = tween(400)) + 
                               shrinkVertically(animationSpec = tween(400, delayMillis = 400))
                    ) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = device.status != "terminated") {
                                    selectedDevice = device
                                    showBottomSheet = true
                                },
                            shape = RoundedCornerShape(20.dp),
                            color = Color(0xFF181520),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00FFA3).copy(alpha = 0.3f))
                        ) {
                            Row(
                                modifier = Modifier.padding(20.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(52.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(if (device.status == "terminated") Color(0xFFFF4A4A).copy(alpha = 0.1f) else Color(0xFF00FFA3).copy(alpha = 0.1f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Monitor, contentDescription = null, tint = if (device.status == "terminated") Color(0xFFFF4A4A) else Color(0xFF00FFA3))
                                }
                                Spacer(modifier = Modifier.width(20.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(device.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                        if (device.status != "terminated") {
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Icon(
                                                Icons.Default.Edit, 
                                                contentDescription = "Rename", 
                                                tint = Color.Gray, 
                                                modifier = Modifier.size(16.dp).clickable { showRenameDialog = device }
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(device.browser, color = Color(0xFFB0A8B9), fontSize = 14.sp)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    if (device.status == "terminated") {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color(0xFFFF4A4A)))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Terminated", color = Color(0xFFFF4A4A), fontSize = 12.sp)
                                        }
                                    } else {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF00FFA3)))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Active • ${device.dateLinked}", color = Color.Gray, fontSize = 12.sp)
                                        }
                                    }
                                }
                                if (device.status == "terminated") {
                                    Button(
                                        onClick = { onNavigateToScanner() },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00FFA3)),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                        modifier = Modifier.height(32.dp)
                                    ) {
                                        Text("Link", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                } else {
                                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.Gray)
                                }
                            }
                        }
                    }

                    if (isDeleting) {
                        LaunchedEffect(device.id) {
                            delay(800) // Wait for animation to finish
                            viewModel.unlinkLinkedDevice(device.id)
                            deletingDeviceIds = deletingDeviceIds - device.id
                            android.widget.Toast.makeText(context, "Session terminated.", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }}

    if (showBottomSheet && selectedDevice != null) {
        ModalBottomSheet(
            onDismissRequest = { showBottomSheet = false },
            sheetState = sheetState,
            containerColor = Color(0xFF1E1926)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF03DAC5)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Computer, contentDescription = null, tint = Color.White, modifier = Modifier.size(32.dp))
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(selectedDevice!!.name, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Text("Linked on ${selectedDevice!!.dateLinked}", color = Color.Gray, fontSize = 14.sp)
                
                Spacer(modifier = Modifier.height(32.dp))

                Button(
                    onClick = {
                        val deviceToUnlink = selectedDevice!!
                        showBottomSheet = false
                        deletingDeviceIds = deletingDeviceIds + deviceToUnlink.id
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF4A4A)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("Terminate Session", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}


