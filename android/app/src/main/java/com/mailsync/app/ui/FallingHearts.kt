package com.mailsync.app.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.random.Random

data class HeartParticle(
    val id: Int,
    val startX: Float,
    val startY: Float,
    val endY: Float,
    val duration: Int,
    val delay: Int,
    val scale: Float
)

@Composable
fun FallingHeartsOverlay(clickCount: Int) {
    if (clickCount == 0) return

    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    
    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }
    
    // Generate particles when clickCount changes
    var particles by remember { mutableStateOf<List<HeartParticle>>(emptyList()) }
    
    LaunchedEffect(clickCount) {
        val particleCount = if (clickCount >= 3) 50 else 15 // Celebration mode if >= 3 clicks
        val newParticles = List(particleCount) { i ->
            HeartParticle(
                id = clickCount * 100 + i, // Unique ID per click
                startX = Random.nextFloat() * screenWidthPx,
                startY = if (clickCount >= 3) -100f else (screenHeightPx * 0.7f + Random.nextFloat() * 200f - 100f),
                endY = screenHeightPx + 100f,
                duration = Random.nextInt(2000, 4000),
                delay = Random.nextInt(0, 500),
                scale = Random.nextFloat() * 1.5f + 0.5f
            )
        }
        particles = newParticles
    }

    Box(modifier = Modifier.fillMaxSize()) {
        particles.forEach { particle ->
            HeartParticleView(particle)
        }
    }
}

@Composable
fun HeartParticleView(particle: HeartParticle) {
    var isVisible by remember { mutableStateOf(false) }
    var isFinished by remember { mutableStateOf(false) }
    
    LaunchedEffect(Unit) {
        delay(particle.delay.toLong())
        isVisible = true
        delay(particle.duration.toLong())
        isFinished = true
    }

    val yOffset by animateFloatAsState(
        targetValue = if (isVisible) particle.endY else particle.startY,
        animationSpec = tween(durationMillis = particle.duration, easing = LinearEasing),
        label = "yOffset"
    )

    val alpha by animateFloatAsState(
        targetValue = if (isFinished) 0f else if (isVisible) 1f else 0f,
        animationSpec = tween(durationMillis = 500),
        label = "alpha"
    )

    if (!isFinished || alpha > 0f) {
        Text(
            text = "❤️",
            fontSize = (24 * particle.scale).sp,
            modifier = Modifier
                .offset { IntOffset(particle.startX.toInt(), yOffset.toInt()) }
                .alpha(alpha)
        )
    }
}
