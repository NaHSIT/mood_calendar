package com.example.mdd_calender.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun Modifier.glassmorphicCard(
    cornerRadius: Dp = 32.dp,
    surfaceAlpha: Float = 0.45f,
    shadowElevation: Dp = 24.dp
): Modifier {
    val surface = MaterialTheme.colorScheme.surface.copy(alpha = surfaceAlpha)
    val outline = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    return this.shadow(
        elevation = shadowElevation,
        shape = RoundedCornerShape(cornerRadius),
        spotColor = Color(0x33000000), // Very soft black/blue shadow
        ambientColor = Color(0x11000000)
    )
    .clip(RoundedCornerShape(cornerRadius))
    .background(surface)
    .border(
        width = 1.dp,
        color = outline,
        shape = RoundedCornerShape(cornerRadius)
    )
}
