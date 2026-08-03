package com.mailsync.app.ui.components

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.onSizeChanged

val LASER_AGSL = """
    uniform float2 iResolution;
    uniform float iTime;
    uniform float3 uColor;

    float hash(float2 p) {
        float3 p3  = fract(float3(p.xyx) * .1031);
        p3 += dot(p3, p3.yzx + 33.33);
        return fract((p3.x + p3.y) * p3.z);
    }

    float noise(float2 p) {
        float2 i = floor(p);
        float2 f = fract(p);
        float2 u = f*f*(3.0-2.0*f);
        return mix(mix(hash(i + float2(0.0,0.0)), hash(i + float2(1.0,0.0)), u.x),
                   mix(hash(i + float2(0.0,1.0)), hash(i + float2(1.0,1.0)), u.x), u.y);
    }

    half4 main(float2 fragCoord) {
        float2 uv = (fragCoord - 0.5 * iResolution) / iResolution.y;
        
        // Base glowing line
        float y = uv.y;
        float x = uv.x;
        
        // Wavy modulation
        float wave = sin(x * 2.0 + iTime * 1.5) * 0.05 + sin(x * 5.0 - iTime * 0.8) * 0.02;
        y += wave;
        
        float intensity = 0.01 / max(abs(y), 0.001);
        
        // Add some noise / "fog"
        float fog = noise(uv * 5.0 + float2(iTime * 0.2, 0.0)) * 0.3;
        
        // Beam flare at center
        float flare = 0.05 / (dot(uv, uv) + 0.01);
        
        float finalLuma = intensity + fog * (intensity * 0.5) + flare * 0.5;
        
        return half4(uColor.r * finalLuma, uColor.g * finalLuma, uColor.b * finalLuma, 1.0);
    }
"""

@Composable
fun LaserFlowAnimation(
    modifier: Modifier = Modifier,
    color: Color = Color(0xFFFF79C6)
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        LaserFlowShader(modifier, color)
    } else {
        LaserFlowFallback(modifier, color)
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
fun LaserFlowShader(
    modifier: Modifier = Modifier,
    color: Color
) {
    val shader = remember { android.graphics.RuntimeShader(LASER_AGSL) }
    var width by remember { mutableStateOf(0f) }
    var height by remember { mutableStateOf(0f) }
    
    val infiniteTransition = rememberInfiniteTransition()
    val time by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 10f,
        animationSpec = infiniteRepeatable(
            animation = tween(10000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        )
    )
    
    Canvas(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged {
                width = it.width.toFloat()
                height = it.height.toFloat()
            }
    ) {
        if (width > 0 && height > 0) {
            shader.setFloatUniform("iResolution", width, height)
            shader.setFloatUniform("iTime", time)
            shader.setFloatUniform("uColor", color.red, color.green, color.blue)
            val brush = ShaderBrush(shader)
            drawRect(brush = brush)
        }
    }
}

@Composable
fun LaserFlowFallback(modifier: Modifier = Modifier, color: Color) {
    val infiniteTransition = rememberInfiniteTransition()
    val offset by infiniteTransition.animateFloat(
        initialValue = -20f,
        targetValue = 20f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        )
    )
    
    Canvas(modifier = modifier.fillMaxSize()) {
        val y = size.height / 2 + offset
        drawLine(
            color = color.copy(alpha = 0.3f),
            start = Offset(0f, y),
            end = Offset(size.width, y),
            strokeWidth = 30f
        )
        drawLine(
            color = color,
            start = Offset(0f, y),
            end = Offset(size.width, y),
            strokeWidth = 6f
        )
    }
}
