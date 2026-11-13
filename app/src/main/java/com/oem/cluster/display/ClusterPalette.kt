package com.oem.cluster.display

import androidx.compose.ui.graphics.Color
import com.oem.cluster.data.DriveMode

data class ClusterPalette(
    val background: Color,
    val surfaceCard: Color,
    val primaryAccent: Color,
    val secondaryGlow: Color,
    val warningAmber: Color,
    val criticalRed: Color,
    val textPrimary: Color,
    val textMuted: Color
)

fun paletteForDriveMode(mode: DriveMode): ClusterPalette = when (mode) {
    DriveMode.COMFORT -> ClusterPalette(
        background = Color(0xFF070D14),
        surfaceCard = Color(0xFF0E1824),
        primaryAccent = Color(0xFF38BDF8), // Ice Cyan
        secondaryGlow = Color(0xFF0284C7),
        warningAmber = Color(0xFFF59E0B),
        criticalRed = Color(0xFFEF4444),
        textPrimary = Color(0xFFF8FAFC),
        textMuted = Color(0xFF64748B)
    )

    DriveMode.ECO -> ClusterPalette(
        background = Color(0xFF06110D),
        surfaceCard = Color(0xFF0C1F18),
        primaryAccent = Color(0xFF10B981), // Emerald Green
        secondaryGlow = Color(0xFF059669),
        warningAmber = Color(0xFFF59E0B),
        criticalRed = Color(0xFFEF4444),
        textPrimary = Color(0xFFF0FDF4),
        textMuted = Color(0xFF6EE7B7)
    )

    DriveMode.SPORT -> ClusterPalette(
        background = Color(0xFF120608),
        surfaceCard = Color(0xFF1F0B0E),
        primaryAccent = Color(0xFFEF4444), // Racing Crimson
        secondaryGlow = Color(0xFFDC2626),
        warningAmber = Color(0xFFF59E0B),
        criticalRed = Color(0xFFFF1E27),
        textPrimary = Color(0xFFFFF1F2),
        textMuted = Color(0xFF94A3B8)
    )
}