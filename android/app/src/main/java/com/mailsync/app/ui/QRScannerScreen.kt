package com.mailsync.app.ui

import android.Manifest
import android.content.Context
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.util.concurrent.Executors

@android.annotation.SuppressLint("UnsafeOptInUsageError")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QRScannerScreen(
    onNavigateBack: () -> Unit,
    onQrCodeScanned: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    if (!hasCameraPermission) {
        Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0D0B12)), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Camera permission is required to scan QR codes.", color = Color.White)
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                    Text("Grant Permission")
                }
            }
        }
        return
    }

    var scannedValue by remember { mutableStateOf<String?>(null) }
    
    // Telegram-style floating target animation
    val infiniteTransition = rememberInfiniteTransition()
    val scanLineOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // Camera Preview
        AndroidView(
            factory = { ctx ->
                val previewView = PreviewView(ctx)
                val cameraExecutor = Executors.newSingleThreadExecutor()
                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)

                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }

                    val imageAnalysis = ImageAnalysis.Builder()
                        .setTargetResolution(Size(1280, 720))
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                        
                    val scannerOptions = BarcodeScannerOptions.Builder()
                        .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                        .build()
                    val scanner = BarcodeScanning.getClient(scannerOptions)

                    imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                        val mediaImage = imageProxy.image
                        if (mediaImage != null && scannedValue == null) {
                            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                            scanner.process(image)
                                .addOnSuccessListener { barcodes ->
                                    for (barcode in barcodes) {
                                        barcode.rawValue?.let { value ->
                                            if (scannedValue == null) {
                                                scannedValue = value
                                            }
                                        }
                                    }
                                }
                                .addOnCompleteListener {
                                    imageProxy.close()
                                }
                        } else {
                            imageProxy.close()
                        }
                    }

                    try {
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            imageAnalysis
                        )
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }, ContextCompat.getMainExecutor(ctx))

                previewView
            },
            modifier = Modifier.fillMaxSize()
        )

        // Overlay with cutout and Telegram-style animated border
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasWidth = size.width
            val canvasHeight = size.height
            val boxSize = canvasWidth * 0.7f
            val boxRect = Rect(
                left = (canvasWidth - boxSize) / 2,
                top = (canvasHeight - boxSize) / 2,
                right = (canvasWidth + boxSize) / 2,
                bottom = (canvasHeight + boxSize) / 2
            )

            // Dimmed background
            val path = Path().apply {
                addRect(Rect(0f, 0f, canvasWidth, canvasHeight))
                addRoundRect(
                    androidx.compose.ui.geometry.RoundRect(
                        rect = boxRect,
                        cornerRadius = CornerRadius(32f, 32f)
                    )
                )
                fillType = PathFillType.EvenOdd
            }
            drawPath(path, color = Color.Black.copy(alpha = 0.7f))

            // Animated scanning line
            val lineY = boxRect.top + (boxRect.height * scanLineOffset)
            drawLine(
                color = Color(0xFF10B981).copy(alpha = 0.6f),
                start = Offset(boxRect.left + 20f, lineY),
                end = Offset(boxRect.right - 20f, lineY),
                strokeWidth = 4f
            )
            
            // Telegram-style corner brackets
            val bracketLength = 60f
            val bracketStroke = 12f
            val bracketColor = Color(0xFF10B981)
            
            // Top Left
            drawLine(bracketColor, Offset(boxRect.left, boxRect.top), Offset(boxRect.left + bracketLength, boxRect.top), bracketStroke)
            drawLine(bracketColor, Offset(boxRect.left, boxRect.top), Offset(boxRect.left, boxRect.top + bracketLength), bracketStroke)
            // Top Right
            drawLine(bracketColor, Offset(boxRect.right, boxRect.top), Offset(boxRect.right - bracketLength, boxRect.top), bracketStroke)
            drawLine(bracketColor, Offset(boxRect.right, boxRect.top), Offset(boxRect.right, boxRect.top + bracketLength), bracketStroke)
            // Bottom Left
            drawLine(bracketColor, Offset(boxRect.left, boxRect.bottom), Offset(boxRect.left + bracketLength, boxRect.bottom), bracketStroke)
            drawLine(bracketColor, Offset(boxRect.left, boxRect.bottom), Offset(boxRect.left, boxRect.bottom - bracketLength), bracketStroke)
            // Bottom Right
            drawLine(bracketColor, Offset(boxRect.right, boxRect.bottom), Offset(boxRect.right - bracketLength, boxRect.bottom), bracketStroke)
            drawLine(bracketColor, Offset(boxRect.right, boxRect.bottom), Offset(boxRect.right, boxRect.bottom - bracketLength), bracketStroke)
        }

        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onNavigateBack,
                modifier = Modifier
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(Color.Black.copy(alpha = 0.5f))
            ) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                "Link PC",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
        
        // Footer Text
        Text(
            "Point your phone at the PC screen to confirm login",
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 64.dp)
                .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                .padding(horizontal = 24.dp, vertical = 12.dp)
        )

        // Success Animation Overlay
        if (scannedValue != null) {
            val isMailSync = scannedValue!!.contains("mailsync/connect?uuid=") || scannedValue!!.startsWith("mailsync://connect")
            val isWebUrl = android.util.Patterns.WEB_URL.matcher(scannedValue!!).matches()
            
            LaunchedEffect(scannedValue) {
                delay(1200) // Show animation for 1.2s
                if (isMailSync) {
                    onQrCodeScanned(scannedValue!!)
                } else if (isWebUrl) {
                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(scannedValue!!))
                    context.startActivity(intent)
                    onNavigateBack()
                } else {
                    val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    clipboard.setPrimaryClip(android.content.ClipData.newPlainText("QR Code", scannedValue!!))
                    android.widget.Toast.makeText(context, "Text copied to clipboard", android.widget.Toast.LENGTH_SHORT).show()
                    onNavigateBack()
                }
            }
            
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.85f)),
                contentAlignment = Alignment.Center
            ) {
                var showAnimation by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) {
                    showAnimation = true
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val scale by animateFloatAsState(
                        targetValue = if (showAnimation) 1.2f else 0f,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                        label = "lock_scale"
                    )
                    
                    val icon = if (isMailSync) Icons.Default.CheckCircle else if (isWebUrl) Icons.Default.Public else Icons.Default.Description
                    val textStr = if (isMailSync) "Connection Successful" else if (isWebUrl) "Scanned Web Link" else "Text Copied"
                    
                    Box(
                        modifier = Modifier
                            .scale(scale)
                            .size(100.dp)
                            .background(Color(0xFF00FFA3), CircleShape)
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            icon,
                            contentDescription = "Success",
                            tint = Color.White,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        textStr,
                        color = Color(0xFF00FFA3),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.alpha(if (showAnimation) 1f else 0f)
                    )
                }
            }
        }
    }
}
