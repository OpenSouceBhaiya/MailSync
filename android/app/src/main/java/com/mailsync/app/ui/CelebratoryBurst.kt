package com.mailsync.app.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.delay
import kotlin.random.Random

@Composable
fun CelebratoryBurst(onAnimationFinished: () -> Unit) {
    var start by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        start = true
        delay(2000)
        onAnimationFinished()
    }

    val progress by animateFloatAsState(
        targetValue = if (start) 1f else 0f,
        animationSpec = tween(durationMillis = 1500, easing = LinearOutSlowInEasing)
    )

    val particles = remember {
        List(50) {
            Particle(
                x = Random.nextFloat() * 1000 - 500,
                y = Random.nextFloat() * 1000 - 500,
                color = listOf(Color.Red, Color.Green, Color.Blue, Color.Yellow, Color.Magenta).random(),
                size = Random.nextFloat() * 10 + 10
            )
        }
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        val center = Offset(size.width / 2, size.height / 2)
        
        particles.forEach { particle ->
            val currentX = center.x + particle.x * progress
            val currentY = center.y + particle.y * progress + (progress * progress * 500) // gravity effect
            val alpha = 1f - progress

            drawCircle(
                color = particle.color.copy(alpha = alpha.coerceIn(0f, 1f)),
                radius = particle.size,
                center = Offset(currentX, currentY)
            )
        }
    }
}

data class Particle(val x: Float, val y: Float, val color: Color, val size: Float)
