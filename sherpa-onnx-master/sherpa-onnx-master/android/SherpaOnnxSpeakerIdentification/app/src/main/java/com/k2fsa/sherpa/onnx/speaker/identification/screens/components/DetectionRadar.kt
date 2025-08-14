package com.k2fsa.sherpa.onnx.speaker.identification.screens.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlin.random.Random
import kotlin.math.sin
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.PathEffect

@Composable
fun DetectionRadar(
    active: Boolean,
    score: Float?,
    volume: Float? = null,
    modifier: Modifier = Modifier,
    onToggle: (() -> Unit)? = null,
) {
    val infinite = rememberInfiniteTransition(label = "radar")
    val sweep by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (active) 1400 else 2800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ), label = "sweep"
    )
    val pulse by infinite.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "pulse"
    )

    val ringColor = if (active) Color(0xFF1D6FEA) else Color(0xFFB0BEC5)
    val glow = if (active) Color(0x334F92FF) else Color(0x22B0BEC5)
    // 公安风主色：通话中为深公安蓝，待机为灰蓝
    val accent = if (active) Color(0xFF1565C0) else Color(0xFF90A4AE)

    data class Star(val angle: Float, val rFactor: Float, val baseAlpha: Float, val size: Float)
    val stars = remember {
        List(28) {
            val angle = Random.nextFloat() * (2f * Math.PI).toFloat()
            val rFactor = 0.2f + 0.75f * Random.nextFloat()
            val baseAlpha = 0.15f + 0.25f * Random.nextFloat()
            val size = 1.2f + 1.8f * Random.nextFloat()
            Star(angle, rFactor, baseAlpha, size)
        }
    }

    val clickableMod = if (onToggle != null) modifier
        .clip(CircleShape)
        .clickable { onToggle() }
        .background(Color.Transparent) else modifier

    Box(modifier = clickableMod, contentAlignment = Alignment.Center) {
        // 外层呼吸光圈
        Box(
            modifier = Modifier
                .size((200 * pulse).dp)
                .clip(CircleShape)
                .background(glow)
        )
        // 音量环（按音量放大，绘制描边）
        Canvas(modifier = Modifier.size(210.dp)) {
            val radius = size.minDimension / 2f
            val vol = (volume ?: (((score ?: -5f) + 10f) / 10f)).coerceIn(0f, 1f)
            val stroke = 6f + 10f * vol
            drawCircle(
                color = ringColor.copy(alpha = 0.35f),
                radius = radius * (0.85f + 0.1f * vol),
                style = Stroke(width = stroke)
            )
        }
        // 主圆（加入羽化发光）
        Canvas(modifier = Modifier.size(180.dp)) {
            val radius = size.minDimension / 2f
            // 发光外环
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(ringColor.copy(alpha = 0.18f), Color.Transparent),
                    center = androidx.compose.ui.geometry.Offset(size.width/2f, size.height/2f),
                    radius = radius
                )
            )
            // 内部白色主体
            drawCircle(color = Color.White, radius = radius * 0.92f)
        }
        // 文案
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            androidx.compose.foundation.layout.Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = if (active) Icons.Filled.Mic else Icons.Filled.Shield,
                    contentDescription = null,
                    tint = if (active) Color(0xFF1D6FEA) else Color(0xFF90A4AE)
                )
                Text(text = if (active) "实时检测中" else "待机", style = MaterialTheme.typography.titleMedium)
                if (score != null) {
                    val (label, color) = when {
                        score <= -8f -> "伪造" to Color(0xFFD32F2F) // 高风险
                        score <= -5f -> "可疑" to Color(0xFFF9A825) // 中风险
                        else -> "真人语音" to Color(0xFF2E7D32) // 低风险
                    }
                    androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
                        if (label == "伪造") {
                            Icon(imageVector = Icons.Default.Warning, contentDescription = null, tint = color)
                            androidx.compose.foundation.layout.Spacer(modifier = androidx.compose.ui.Modifier.size(4.dp))
                        }
                        Text(text = "$label  分数: ${"%.3f".format(score)}", color = color)
                    }
                }
            }
        }
        // 星空背景（轻量级）：少量闪烁星点 - 放在顶层
        Canvas(modifier = Modifier.size(210.dp)) {
            val center = androidx.compose.ui.geometry.Offset(size.width/2f, size.height/2f)
            val radius = size.minDimension / 2f
            // 让星点随时间微微闪烁（用 sweep 作为节拍）
            for (star in stars) {
                val x = center.x + (star.rFactor * radius) * kotlin.math.cos(star.angle)
                val y = center.y + (star.rFactor * radius) * kotlin.math.sin(star.angle)
                val twinkle = 0.55f + 0.45f * kotlin.math.abs(kotlin.math.sin((sweep + x + y) / 28f))
                drawCircle(color = Color.White.copy(alpha = star.baseAlpha * twinkle), radius = star.size)
            }
        }
        // 刻度环（公安风）+ 内部虚线同心圆 + 十字准线 - 顶层装饰
        Canvas(modifier = Modifier.size(200.dp)) {
            val radius = size.minDimension / 2f
            val tickColor = accent.copy(alpha = 0.35f) // 公安蓝/待机灰蓝随状态切换
            val center = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height / 2f)
            // 刻度
            for (i in 0 until 60) {
                val angle = Math.toRadians((i * 6).toDouble()).toFloat()
                val start = androidx.compose.ui.geometry.Offset(
                    x = center.x + (radius - 10f) * kotlin.math.cos(angle),
                    y = center.y + (radius - 10f) * kotlin.math.sin(angle)
                )
                val end = androidx.compose.ui.geometry.Offset(
                    x = center.x + radius * kotlin.math.cos(angle),
                    y = center.y + radius * kotlin.math.sin(angle)
                )
                drawLine(color = tickColor, start = start, end = end, strokeWidth = if (i % 5 == 0) 2.8f else 1.4f)
            }
            // 动态虚线同心圆
            val pe = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), phase = sweep * 0.5f)
            for (rf in listOf(0.3f, 0.55f, 0.8f)) {
                drawCircle(
                    color = accent.copy(alpha = 0.25f),
                    radius = radius * rf,
                    style = Stroke(width = 1.5f, pathEffect = pe)
                )
            }
            // 十字准线（公安风格）
            drawLine(color = accent.copy(alpha = 0.2f), start = androidx.compose.ui.geometry.Offset(center.x - radius * 0.9f, center.y), end = androidx.compose.ui.geometry.Offset(center.x + radius * 0.9f, center.y), strokeWidth = 1.2f)
            drawLine(color = accent.copy(alpha = 0.2f), start = androidx.compose.ui.geometry.Offset(center.x, center.y - radius * 0.9f), end = androidx.compose.ui.geometry.Offset(center.x, center.y + radius * 0.9f), strokeWidth = 1.2f)
        }
        // 扫描扇形 + 渐隐拖尾 - 最顶层
        Canvas(modifier = Modifier.size(200.dp)) {
            val radius = size.minDimension / 2f
            val topLeft = androidx.compose.ui.geometry.Offset((size.width - 2 * radius) / 2f, (size.height - 2 * radius) / 2f)
            for (i in 0..4) {
                val alpha = 0.28f - i * 0.05f
                drawArc(
                    color = accent.copy(alpha = alpha.coerceAtLeast(0f)),
                    startAngle = sweep - i * 12f,
                    sweepAngle = 45f,
                    useCenter = true,
                    topLeft = topLeft,
                    size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2)
                )
            }
        }
    }
}

