package com.oem.cluster.ui.cluster

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oem.cluster.data.TirePressuresPsi
import com.oem.cluster.data.TurnSignal
import com.oem.cluster.ui.theme.ClusterPalette
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun CircularTelemetryGauge(
    value: Float,
    maxValue: Float,
    displayValueText: String,
    unitLabel: String,
    palette: ClusterPalette,
    redlineStartRatio: Float? = null,
    modifier: Modifier = Modifier
) {
    val animatedProgress by animateFloatAsState(
        targetValue = (value / maxValue).coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 80), // Fast 60 FPS tracking
        label = "gaugeProgress"
    )

    val isRedline = redlineStartRatio != null && animatedProgress >= redlineStartRatio
    val activeColor = if (isRedline) palette.criticalRed else palette.primaryAccent

    Box(modifier = modifier.aspectRatio(1f), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize().padding(12.dp)) {
            val strokeWidth = size.minDimension * 0.065f
            val radius = (size.minDimension - strokeWidth) / 2f
            val center = Offset(size.width / 2f, size.height / 2f)
            val arcTopLeft = Offset(center.x - radius, center.y - radius)
            val arcSize = Size(radius * 2f, radius * 2f)

            val startAngle = 135f
            val totalSweep = 270f

            // 1. Background Track Arc
            drawArc(
                color = Color.White.copy(alpha = 0.08f),
                startAngle = startAngle,
                sweepAngle = totalSweep,
                useCenter = false,
                topLeft = arcTopLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            // 2. Redline Zone Arc (for Tachometer)
            if (redlineStartRatio != null) {
                val redStart = startAngle + (totalSweep * redlineStartRatio)
                val redSweep = totalSweep * (1f - redlineStartRatio)
                drawArc(
                    color = palette.criticalRed.copy(alpha = 0.35f),
                    startAngle = redStart,
                    sweepAngle = redSweep,
                    useCenter = false,
                    topLeft = arcTopLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth * 0.85f, cap = StrokeCap.Butt)
                )
            }

            // 3. Active Value Glow Sweep
            drawArc(
                brush = Brush.sweepGradient(
                    colors = listOf(palette.secondaryGlow, activeColor, activeColor),
                    center = center
                ),
                startAngle = startAngle,
                sweepAngle = totalSweep * animatedProgress,
                useCenter = false,
                topLeft = arcTopLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            // 4. Major & Minor Tick Marks
            val tickCount = 40
            for (i in 0..tickCount) {
                val ratio = i.toFloat() / tickCount
                val angleDeg = startAngle + totalSweep * ratio
                val angleRad = Math.toRadians(angleDeg.toDouble())
                val isMajor = i % 5 == 0
                val outerR = radius - strokeWidth * 0.85f
                val innerR = outerR - (if (isMajor) 18f else 9f)

                val tickColor = when {
                    redlineStartRatio != null && ratio >= redlineStartRatio -> palette.criticalRed
                    ratio <= animatedProgress -> activeColor
                    else -> Color.White.copy(alpha = 0.35f)
                }

                drawLine(
                    color = tickColor,
                    start = Offset(
                        center.x + (innerR * cos(angleRad)).toFloat(),
                        center.y + (innerR * sin(angleRad)).toFloat()
                    ),
                    end = Offset(
                        center.x + (outerR * cos(angleRad)).toFloat(),
                        center.y + (outerR * sin(angleRad)).toFloat()
                    ),
                    strokeWidth = if (isMajor) 3.5f else 1.8f,
                    cap = StrokeCap.Round
                )
            }

            // 5. Needle Pointer
            val needleAngleRad = Math.toRadians((startAngle + totalSweep * animatedProgress).toDouble())
            val needleTip = Offset(
                center.x + ((radius - strokeWidth * 0.4f) * cos(needleAngleRad)).toFloat(),
                center.y + ((radius - strokeWidth * 0.4f) * sin(needleAngleRad)).toFloat()
            )
            drawLine(
                color = activeColor,
                start = center,
                end = needleTip,
                strokeWidth = 5f,
                cap = StrokeCap.Round
            )
            drawCircle(color = palette.background, radius = radius * 0.36f, center = center)
        }

        // Center Digital Value Readout
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = displayValueText,
                color = if (isRedline) palette.criticalRed else palette.textPrimary,
                fontSize = 54.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-1).sp
            )
            Text(
                text = unitLabel,
                color = palette.textMuted,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
fun TopTelltaleAndGearBar(
    gearLabel: String,
    turnSignal: TurnSignal,
    palette: ClusterPalette,
    modifier: Modifier = Modifier
) {
    val blinkTransition = rememberInfiniteTransition(label = "blinker")
    val blinkAlpha by blinkTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 420),
            repeatMode = RepeatMode.Reverse
        ),
        label = "blinkAlpha"
    )

    val leftActive = turnSignal == TurnSignal.LEFT || turnSignal == TurnSignal.HAZARD
    val rightActive = turnSignal == TurnSignal.RIGHT || turnSignal == TurnSignal.HAZARD
    val signalGreen = Color(0xFF22C55E)

    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 36.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "Left Turn Signal",
            tint = if (leftActive) signalGreen.copy(alpha = blinkAlpha) else Color.White.copy(alpha = 0.12f),
            modifier = Modifier.size(34.dp)
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(22.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            listOf("P", "R", "N", "D", "S").forEach { gear ->
                val isSelected = gear == gearLabel
                val badgeColor = when {
                    !isSelected -> Color.Transparent
                    gear == "R" -> palette.criticalRed
                    else -> palette.primaryAccent
                }
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(badgeColor.copy(alpha = if (isSelected) 0.25f else 0f), RoundedCornerShape(8.dp))
                        .border(
                            width = if (isSelected) 2.dp else 0.dp,
                            color = badgeColor,
                            shape = RoundedCornerShape(8.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = gear,
                        color = if (isSelected) palette.textPrimary else palette.textMuted.copy(alpha = 0.45f),
                        fontSize = 20.sp,
                        fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium
                    )
                }
            }
        }

        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = "Right Turn Signal",
            tint = if (rightActive) signalGreen.copy(alpha = blinkAlpha) else Color.White.copy(alpha = 0.12f),
            modifier = Modifier.size(34.dp)
        )
    }
}

@Composable
fun CenterTpmsChassisCard(
    pressures: TirePressuresPsi,
    palette: ClusterPalette,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(palette.surfaceCard.copy(alpha = 0.7f), RoundedCornerShape(20.dp))
            .border(1.dp, palette.primaryAccent.copy(alpha = 0.25f), RoundedCornerShape(20.dp))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "TPMS TIRE TELEMETRY",
            color = palette.textMuted,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.5.sp
        )

        Row(
            modifier = Modifier.fillMaxWidth().weight(1f),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left Wheels
            Column(verticalArrangement = Arrangement.SpaceAround, modifier = Modifier.fillMaxHeight()) {
                WheelPressureReadout("FL", pressures.frontLeft, palette)
                WheelPressureReadout("RL", pressures.rearLeft, palette)
            }

            // Minimalist Chassis Wireframe
            Canvas(modifier = Modifier.width(68.dp).fillMaxHeight(0.82f)) {
                drawRoundRect(
                    color = palette.primaryAccent.copy(alpha = 0.45f),
                    size = size,
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(24f, 24f),
                    style = Stroke(width = 3f)
                )
                drawLine(
                    color = palette.primaryAccent.copy(alpha = 0.25f),
                    start = Offset(12f, size.height * 0.3f),
                    end = Offset(size.width - 12f, size.height * 0.3f),
                    strokeWidth = 2f
                )
                drawLine(
                    color = palette.primaryAccent.copy(alpha = 0.25f),
                    start = Offset(12f, size.height * 0.72f),
                    end = Offset(size.width - 12f, size.height * 0.72f),
                    strokeWidth = 2f
                )
            }

            // Right Wheels
            Column(verticalArrangement = Arrangement.SpaceAround, modifier = Modifier.fillMaxHeight()) {
                WheelPressureReadout("FR", pressures.frontRight, palette)
                WheelPressureReadout("RR", pressures.rearRight, palette)
            }
        }

        if (pressures.hasLowPressureWarning) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Low Tire Pressure",
                    tint = palette.warningAmber,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "LOW TIRE PRESSURE (< 28 PSI)",
                    color = palette.warningAmber,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun WheelPressureReadout(label: String, psi: Float, palette: ClusterPalette) {
    val isLow = psi < TirePressuresPsi.LOW_PRESSURE_THRESHOLD
    val color = if (isLow) palette.warningAmber else palette.textPrimary

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, color = palette.textMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Text(
            text = String.format("%.1f", psi),
            color = color,
            fontSize = 22.sp,
            fontWeight = FontWeight.ExtraBold
        )
        Text(text = "PSI", color = palette.textMuted, fontSize = 10.sp)
    }
}