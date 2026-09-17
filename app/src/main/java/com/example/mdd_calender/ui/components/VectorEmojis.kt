package com.example.mdd_calender.ui.components

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.mdd_calender.data.IconPack
import com.example.mdd_calender.data.MoodIconConfig

enum class MoodType(val label: String, val color: Color) {
    AMAZING("惊喜", Color(0xFFFFD166)),
    GOOD("开心", Color(0xFF06D6A0)),
    MEH("平淡", Color(0xFF118AB2)),
    BAD("难过", Color(0xFF073B4C)),
    AWFUL("糟糕", Color(0xFFEF476F));
    
    companion object {
        fun fromLabel(label: String): MoodType {
            return entries.find { it.label == label } ?: GOOD
        }
    }
}

object MoodIconProvider {
    fun getIconVector(mood: MoodType, pack: IconPack): ImageVector {
        return when (pack) {
            IconPack.CLASSIC -> when (mood) {
                MoodType.AMAZING -> Icons.Default.SentimentVerySatisfied
                MoodType.GOOD -> Icons.Default.SentimentSatisfied
                MoodType.MEH -> Icons.Default.SentimentNeutral
                MoodType.BAD -> Icons.Default.SentimentDissatisfied
                MoodType.AWFUL -> Icons.Default.SentimentVeryDissatisfied
            }
            IconPack.MINIMALIST -> when (mood) {
                // Using generic geometric/rounded icons for minimalist feel
                MoodType.AMAZING -> Icons.Rounded.Star
                MoodType.GOOD -> Icons.Rounded.CheckCircle
                MoodType.MEH -> Icons.Rounded.RemoveCircle
                MoodType.BAD -> Icons.Rounded.Warning
                MoodType.AWFUL -> Icons.Rounded.Cancel
            }
        }
    }
}

@Composable
fun MoodVectorIcon(
    mood: MoodType,
    modifier: Modifier = Modifier,
    tint: Color = Color.White,
    config: MoodIconConfig = MoodIconConfig() // default to empty
) {
    val customUriString = config.customUris[mood.label]
    
    Box(
        modifier = modifier
            .background(if (customUriString == null) mood.color else Color.Transparent, CircleShape)
            .clip(CircleShape),
        contentAlignment = Alignment.Center
    ) {
        if (customUriString != null) {
            AsyncImage(
                model = Uri.parse(customUriString),
                contentDescription = mood.label,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize()
            )
        } else {
            val vector = MoodIconProvider.getIconVector(mood, config.pack)
            Icon(
                imageVector = vector,
                contentDescription = mood.label,
                tint = tint,
                modifier = Modifier.size(48.dp) // Base size, can be overridden by modifier
            )
        }
    }
}
