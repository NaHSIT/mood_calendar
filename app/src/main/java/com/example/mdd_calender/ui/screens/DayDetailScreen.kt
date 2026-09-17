package com.example.mdd_calender.ui.screens

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.mdd_calender.data.WeatherCondition
import com.example.mdd_calender.ui.MoodViewModel
import com.example.mdd_calender.ui.components.MoodType
import com.example.mdd_calender.ui.components.MoodVectorIcon
import com.example.mdd_calender.ui.components.glassmorphicCard
import com.example.mdd_calender.ui.theme.getWeatherColors
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun DayDetailScreen(
    viewModel: MoodViewModel,
    date: String,
    onBack: () -> Unit,
    onNavigateToEditor: (Int) -> Unit
) {
    val records by viewModel.selectedDateRecords.collectAsState()
    val weatherData by viewModel.weatherData.collectAsState()
    val config by viewModel.iconConfig.collectAsState()

    LaunchedEffect(date) {
        viewModel.selectDate(LocalDate.parse(date))
    }

    val weatherColors = getWeatherColors(weatherData?.condition ?: WeatherCondition.CLEAR, false)

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
                .padding(horizontal = 24.dp)
        ) {
            Spacer(modifier = Modifier.height(56.dp))
            
            // App Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .glassmorphicCard(cornerRadius = 24.dp, surfaceAlpha = 0.5f, shadowElevation = 4.dp)
                        .clickable { onBack() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = weatherColors.textPrimary)
                }
                
                Text(
                    text = date, 
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = weatherColors.textPrimary
                    )
                )
                
                Box(modifier = Modifier.size(48.dp)) // Placeholder for balance
            }

            Spacer(modifier = Modifier.height(32.dp))

            if (records.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(
                        "这一天还没有记录情绪",
                        style = MaterialTheme.typography.titleMedium.copy(
                            color = weatherColors.textPrimary.copy(alpha = 0.6f)
                        )
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(bottom = 80.dp) // Space for FAB
                ) {
                    items(records) { record ->
                        val mood = MoodType.fromLabel(record.moodType)
                        
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)
                        ) {
                            // Timeline track & icon
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .background(mood?.color?.copy(alpha = 0.2f) ?: Color.Gray.copy(alpha = 0.2f), CircleShape)
                                        .padding(8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (mood != null) {
                                        MoodVectorIcon(mood = mood, modifier = Modifier.size(28.dp), config = config)
                                    }
                                }
                                // Line connecting to next item
                                Box(
                                    modifier = Modifier
                                        .width(2.dp)
                                        .height(120.dp)
                                        .background(weatherColors.textPrimary.copy(alpha = 0.1f))
                                )
                            }
                            
                            Spacer(modifier = Modifier.width(16.dp))
                            
                            // Card
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .glassmorphicCard(cornerRadius = 24.dp, surfaceAlpha = weatherColors.surfaceAlpha)
                                    .clickable { onNavigateToEditor(record.id) }
                                    .padding(20.dp)
                            ) {
                                Column {
                                    Text(
                                        text = record.time,
                                        style = MaterialTheme.typography.labelLarge.copy(
                                            color = weatherColors.textPrimary.copy(alpha = 0.7f),
                                            fontWeight = FontWeight.Bold
                                        )
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    
                                    val textContent = "${record.note ?: ""} ${record.content ?: ""}".trim()
                                    if (textContent.isNotEmpty()) {
                                        Text(
                                            text = textContent,
                                            style = MaterialTheme.typography.bodyLarge.copy(
                                                color = weatherColors.textPrimary,
                                                lineHeight = 24.sp
                                            ),
                                            maxLines = 3,
                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                        )
                                    } else {
                                        Text(
                                            "没有留下文字...",
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                color = weatherColors.textPrimary.copy(alpha = 0.4f),
                                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                                            )
                                        )
                                    }
                                    
                                    if (!record.imageUris.isNullOrEmpty()) {
                                        Spacer(modifier = Modifier.height(12.dp))
                                        val uri = record.imageUris.split(",").first()
                                        AsyncImage(
                                            model = Uri.parse(uri),
                                            contentDescription = null,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(100.dp)
                                                .clip(RoundedCornerShape(12.dp)),
                                            contentScale = ContentScale.Crop
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        
        // FAB
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 40.dp)
                .size(64.dp)
                .glassmorphicCard(cornerRadius = 32.dp, surfaceAlpha = 0.8f, shadowElevation = 12.dp)
                .clickable { onNavigateToEditor(0) },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Add, contentDescription = "Add", tint = weatherColors.textPrimary, modifier = Modifier.size(32.dp))
        }
    }
}
