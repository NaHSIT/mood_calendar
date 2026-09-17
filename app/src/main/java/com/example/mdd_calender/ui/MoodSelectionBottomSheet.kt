package com.example.mdd_calender.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mdd_calender.ui.theme.*

data class MoodItem(val emoji: String, val color: Color, val label: String)

val MOODS = listOf(
    MoodItem("🤩", MoodAmazing, "惊喜"),
    MoodItem("😊", MoodGood, "开心"),
    MoodItem("😐", MoodMeh, "平淡"),
    MoodItem("😔", MoodBad, "难过"),
    MoodItem("😭", MoodAwful, "糟糕")
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoodSelectionBottomSheet(
    onDismissRequest: () -> Unit,
    onMoodSelected: (String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedMood by remember { mutableStateOf<MoodItem?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "今天感觉如何？",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                ),
                modifier = Modifier.padding(bottom = 24.dp)
            )

            LazyVerticalGrid(
                columns = GridCells.Fixed(5),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(MOODS) { mood ->
                    val isSelected = selectedMood == mood
                    val scale by animateFloatAsState(
                        targetValue = if (isSelected) 1.2f else 1.0f,
                        animationSpec = tween(200),
                        label = "scale"
                    )
                    val alpha by animateFloatAsState(
                        targetValue = if (selectedMood == null || isSelected) 1f else 0.4f,
                        animationSpec = tween(200),
                        label = "alpha"
                    )

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .alpha(alpha)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                selectedMood = mood
                            }
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(56.dp)
                                .scale(scale)
                                .shadow(
                                    elevation = if (isSelected) 8.dp else 0.dp,
                                    shape = CircleShape,
                                    spotColor = mood.color,
                                    ambientColor = mood.color
                                )
                                .background(
                                    color = if (isSelected) mood.color.copy(alpha = 0.2f) else Color.Transparent,
                                    shape = CircleShape
                                )
                                .border(
                                    width = if (isSelected) 2.dp else 0.dp,
                                    color = if (isSelected) mood.color else Color.Transparent,
                                    shape = CircleShape
                                )
                        ) {
                            Text(
                                text = mood.emoji,
                                fontSize = 32.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Text(
                            text = mood.label,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) mood.color else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = { selectedMood?.let { onMoodSelected(it.emoji) } },
                enabled = selectedMood != null,
                colors = ButtonDefaults.buttonColors(
                    containerColor = selectedMood?.color ?: MaterialTheme.colorScheme.primary,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(percent = 50), // Pill shape 100dp equivalent
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Text(
                    text = "保存心情",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
