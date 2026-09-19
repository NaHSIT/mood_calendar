package com.example.mdd_calender.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mdd_calender.data.MoodRecord
import com.example.mdd_calender.data.WeatherCondition
import com.example.mdd_calender.ui.MoodViewModel
import com.example.mdd_calender.ui.components.MoodType
import com.example.mdd_calender.ui.components.MoodVectorIcon
import com.example.mdd_calender.ui.components.glassmorphicCard
import com.example.mdd_calender.ui.theme.getWeatherColors
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalAnimationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreenV3(
    viewModel: MoodViewModel,
    onBack: () -> Unit,
    onDateClick: (LocalDate) -> Unit
) {
    val currentMonth by viewModel.currentMonth.collectAsState()
    val monthlyRecords by viewModel.monthlyRecords.collectAsState()
    val weatherData by viewModel.weatherData.collectAsState()
    val iconConfig by viewModel.iconConfig.collectAsState()
    
    val weatherColors = getWeatherColors(weatherData?.condition ?: WeatherCondition.CLEAR, isSystemInDarkTheme())

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(weatherColors.backgroundStart, weatherColors.backgroundEnd)
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 760.dp)
                .align(Alignment.TopCenter)
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(56.dp))
            
            // Custom App Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .glassmorphicCard(cornerRadius = 24.dp, surfaceAlpha = 0.5f, shadowElevation = 4.dp)
                        .clickable { onBack() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "返回", tint = weatherColors.textPrimary)
                }
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    "心情日历", 
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = weatherColors.textPrimary,
                        letterSpacing = (-0.5).sp
                    )
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Glassmorphic Calendar Container
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .glassmorphicCard(cornerRadius = 40.dp, surfaceAlpha = weatherColors.surfaceAlpha)
                    .padding(horizontal = 20.dp, vertical = 24.dp)
            ) {
                Column {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val formatter = DateTimeFormatter.ofPattern("yyyy年 M月")
                        Text(
                            text = currentMonth.format(formatter),
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontWeight = FontWeight.Black,
                                color = weatherColors.textPrimary,
                                letterSpacing = (-1).sp
                            ),
                            modifier = Modifier.padding(start = 8.dp)
                        )
                        Row {
                            IconButton(onClick = { viewModel.changeMonth(currentMonth.minusMonths(1)) }) {
                                Icon(Icons.Default.ChevronLeft, contentDescription = "上个月", tint = weatherColors.textPrimary)
                            }
                            IconButton(onClick = { viewModel.changeMonth(currentMonth.plusMonths(1)) }) {
                                Icon(Icons.Default.ChevronRight, contentDescription = "下个月", tint = weatherColors.textPrimary)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Days of week
                    Row(modifier = Modifier.fillMaxWidth()) {
                        val daysOfWeek = listOf("一", "二", "三", "四", "五", "六", "日")
                        daysOfWeek.forEach { day ->
                            Text(
                                text = day,
                                modifier = Modifier.weight(1f),
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    color = weatherColors.textPrimary.copy(alpha = 0.5f),
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    AnimatedContent(
                        targetState = currentMonth,
                        transitionSpec = {
                            fadeIn(animationSpec = tween(300)) togetherWith fadeOut(animationSpec = tween(300))
                        },
                        label = "month_transition"
                    ) { targetMonth ->
                        
                        val daysInMonth = targetMonth.lengthOfMonth()
                        val firstDayOfMonth = targetMonth.atDay(1).dayOfWeek.value
                        val emptyDaysBefore = firstDayOfMonth - 1
                        val totalCells = emptyDaysBefore + daysInMonth

                        Column {
                            (0 until totalCells).chunked(7).forEach { week ->
                                Row(Modifier.fillMaxWidth()) {
                                    week.forEach { index ->
                                        Box(Modifier.weight(1f)) {
                                            if (index < emptyDaysBefore) {
                                                Box(modifier = Modifier.aspectRatio(1f))
                                            } else {
                                                val dayOfMonth = index - emptyDaysBefore + 1
                                                val date = targetMonth.atDay(dayOfMonth)
                                                val record = monthlyRecords.find { it.date == date.format(DateTimeFormatter.ISO_LOCAL_DATE) }
                                                CalendarDayItemV3(
                                                    date = date,
                                                    isToday = date == LocalDate.now(),
                                                    record = record,
                                                    textColor = weatherColors.textPrimary,
                                                    onClick = { onDateClick(date) },
                                                    config = iconConfig,
                                                )
                                            }
                                        }
                                    }
                                    repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
                                }
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun CalendarDayItemV3(
    date: LocalDate,
    isToday: Boolean,
    record: MoodRecord?,
    textColor: Color,
    onClick: () -> Unit,
    config: com.example.mdd_calender.data.MoodIconConfig
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.85f else 1f,
        animationSpec = tween(150),
        label = "scale"
    )

    val moodType = record?.moodType?.let { MoodType.fromLabel(it) }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .aspectRatio(1f)
            .padding(4.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(if (isToday && moodType == null) textColor.copy(alpha = 0.1f) else Color.Transparent)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
    ) {
        if (moodType != null) {
            MoodVectorIcon(mood = moodType, modifier = Modifier.size(32.dp), config = config)
        } else {
            Text(
                text = date.dayOfMonth.toString(),
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = if (isToday) FontWeight.Black else FontWeight.SemiBold,
                    color = if (isToday) textColor else textColor.copy(alpha = 0.7f)
                )
            )
        }
    }
}
