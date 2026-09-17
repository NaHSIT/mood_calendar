package com.example.mdd_calender.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.with
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mdd_calender.data.MoodRecord
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun CalendarScreen(
    viewModel: MoodViewModel,
    onDateClick: (LocalDate) -> Unit
) {
    val currentMonth by viewModel.currentMonth.collectAsState()
    val selectedDate by viewModel.selectedDate.collectAsState()
    val monthlyRecords by viewModel.monthlyRecords.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 24.dp)
    ) {
        Spacer(modifier = Modifier.height(48.dp))
        
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val formatter = DateTimeFormatter.ofPattern("yyyy年 M月")
            Text(
                text = currentMonth.format(formatter),
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            )
            Row {
                IconButton(onClick = { viewModel.changeMonth(currentMonth.minusMonths(1)) }) {
                    Icon(
                        Icons.Default.ChevronLeft, 
                        contentDescription = "上个月",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
                IconButton(onClick = { viewModel.changeMonth(currentMonth.plusMonths(1)) }) {
                    Icon(
                        Icons.Default.ChevronRight, 
                        contentDescription = "下个月",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Days of week
        Row(modifier = Modifier.fillMaxWidth()) {
            val daysOfWeek = listOf("一", "二", "三", "四", "五", "六", "日")
            daysOfWeek.forEach { day ->
                Text(
                    text = day,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelLarge.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        AnimatedContent(
            targetState = currentMonth,
            transitionSpec = {
                fadeIn(animationSpec = tween(300)) with fadeOut(animationSpec = tween(300))
            },
            label = "month_transition"
        ) { targetMonth ->
            
            // Calculate grid bounds INSIDE AnimatedContent using targetMonth to prevent DateTimeException
            val daysInMonth = targetMonth.lengthOfMonth()
            val firstDayOfMonth = targetMonth.atDay(1).dayOfWeek.value
            val emptyDaysBefore = firstDayOfMonth - 1
            val totalCells = emptyDaysBefore + daysInMonth

            LazyVerticalGrid(
                columns = GridCells.Fixed(7),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(totalCells) { index ->
                    if (index < emptyDaysBefore) {
                        Box(modifier = Modifier.aspectRatio(1f))
                    } else {
                        val dayOfMonth = index - emptyDaysBefore + 1
                        val date = targetMonth.atDay(dayOfMonth)
                        val record = monthlyRecords.find { it.date == date.format(DateTimeFormatter.ISO_LOCAL_DATE) }
                        
                        CalendarDayItem(
                            date = date,
                            isSelected = date == selectedDate,
                            isToday = date == LocalDate.now(),
                            record = record,
                            onClick = { onDateClick(date) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CalendarDayItem(
    date: LocalDate,
    isSelected: Boolean,
    isToday: Boolean,
    record: MoodRecord?,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.85f else 1f,
        animationSpec = tween(150),
        label = "scale"
    )

    val moodItem = MOODS.find { it.emoji == record?.moodType }
    
    // Background colors
    val backgroundColor = when {
        moodItem != null -> moodItem.color
        isSelected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
        else -> MaterialTheme.colorScheme.surface
    }

    val shadowElevation = if (moodItem != null || isSelected) 8.dp else 2.dp

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .aspectRatio(1f)
            .padding(6.dp)
            .scale(scale)
            .shadow(
                elevation = shadowElevation,
                shape = RoundedCornerShape(28.dp), // Apple-like rounded rectangle as per PRD
                spotColor = backgroundColor,
                ambientColor = backgroundColor
            )
            .clip(RoundedCornerShape(28.dp))
            .background(backgroundColor)
            .then(
                if (isToday && moodItem == null) {
                    Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(28.dp))
                } else Modifier
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
    ) {
        if (record != null) {
            Text(
                text = record.moodType,
                fontSize = 24.sp
            )
        } else {
            Text(
                text = date.dayOfMonth.toString(),
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                    color = if (isSelected || isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
            )
        }
    }
}
