package com.example.mdd_calender.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mdd_calender.data.WeatherCondition
import com.example.mdd_calender.ui.MoodViewModel
import com.example.mdd_calender.ui.components.glassmorphicCard
import com.example.mdd_calender.ui.theme.getWeatherColors
import androidx.compose.material.icons.filled.Settings
import java.time.format.DateTimeFormatter
import java.time.LocalDate
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.isSystemInDarkTheme

@Composable
fun HomeScreen(
    viewModel: MoodViewModel,
    onNavigateToCalendar: () -> Unit,
    onNavigateToDayDetail: (String) -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToAnalysis: () -> Unit,
    onNavigateToAnniversary: () -> Unit
) {
    val context = LocalContext.current
    var hasLocationPermission by remember { mutableStateOf(false) }
    
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            hasLocationPermission = isGranted
            if (isGranted) {
                viewModel.fetchLocationAndWeather(context)
            }
        }
    )

    LaunchedEffect(Unit) {
        if (androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            hasLocationPermission = true
            viewModel.fetchLocationAndWeather(context)
        } else {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
    }

    val locationName by viewModel.locationName.collectAsState()
    val selectedDate by viewModel.selectedDate.collectAsState()
    val weatherData by viewModel.weatherData.collectAsState()
    val records by viewModel.monthlyRecords.collectAsState()
    val todayRecords by viewModel.todayRecords.collectAsState()
    val anniversaries by viewModel.anniversaries.collectAsState()
    val iconConfig by viewModel.iconConfig.collectAsState()
    
    val isNightMode = isSystemInDarkTheme()
    
    // Smooth transition between weather themes
    val weatherColors = getWeatherColors(weatherData?.condition ?: WeatherCondition.CLEAR, isNightMode)
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        weatherColors.backgroundStart,
                        weatherColors.backgroundEnd
                    )
                )
            )
    ) {
        if (!hasLocationPermission) {
            PermissionGuideCard(
                onGrantClick = { locationPermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION) }
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp)
            ) {
                Spacer(modifier = Modifier.height(56.dp))
                
                // Top Header: Status Pill
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .glassmorphicCard(cornerRadius = 100.dp, surfaceAlpha = 0.2f, shadowElevation = 8.dp)
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Icon(Icons.Default.LocationOn, contentDescription = "当前位置", tint = weatherColors.textPrimary, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = locationName ?: "定位中...",
                            style = MaterialTheme.typography.labelLarge.copy(
                                color = weatherColors.textPrimary,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Box(modifier = Modifier.height(12.dp).width(1.dp).background(weatherColors.textPrimary.copy(alpha = 0.3f)))
                        Spacer(modifier = Modifier.width(12.dp))
                        
                        val weatherText = if (weatherData != null) {
                            val temp = weatherData!!.temperature.toInt()
                            val cond = when(weatherData!!.condition) {
                                WeatherCondition.CLEAR -> "晴朗"
                                WeatherCondition.CLOUDY -> "多云"
                                WeatherCondition.RAIN -> "雨"
                                WeatherCondition.SNOW -> "雪"
                                WeatherCondition.FOG -> "雾"
                                WeatherCondition.THUNDERSTORM -> "雷暴"
                            }
                            "$cond ${temp}°C"
                        } else {
                            "-- °C"
                        }
                        
                        Text(
                            text = weatherText,
                            style = MaterialTheme.typography.labelLarge.copy(
                                color = weatherColors.textPrimary,
                                fontWeight = FontWeight.Normal
                            )
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .glassmorphicCard(cornerRadius = 20.dp, surfaceAlpha = 0.2f, shadowElevation = 4.dp)
                            .clickable { onNavigateToSettings() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = "设置", tint = weatherColors.textPrimary, modifier = Modifier.size(20.dp))
                    }
                }
                
                // Wrap the rest in a scrollable column
                val scrollState = androidx.compose.foundation.rememberScrollState()
                Column(modifier = Modifier.fillMaxWidth().weight(1f).verticalScroll(scrollState)) {
                    Spacer(modifier = Modifier.height(48.dp))
                    
                    // Greeting
                    Text(
                            text = "你好，",
                        style = MaterialTheme.typography.displayMedium.copy(
                            color = weatherColors.textPrimary.copy(alpha = 0.7f),
                            fontWeight = FontWeight.Light,
                            letterSpacing = (-1.5).sp
                        )
                    )
                    Text(
                        text = "今天感觉如何？",
                        style = MaterialTheme.typography.displaySmall.copy(
                            color = weatherColors.textPrimary,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-1).sp
                        )
                    )
                    
                    Spacer(modifier = Modifier.height(32.dp))
                    
                    // Anniversary Showcase Header
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "重要日子",
                            style = MaterialTheme.typography.titleMedium.copy(
                                color = weatherColors.textPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Text(
                            text = "全部 >",
                            style = MaterialTheme.typography.labelLarge.copy(
                                color = weatherColors.textPrimary.copy(alpha = 0.6f),
                                fontWeight = FontWeight.Bold
                            ),
                            modifier = Modifier.clickable { onNavigateToAnniversary() }
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Anniversary Showcase
                    if (anniversaries.isNotEmpty()) {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(anniversaries) { ann ->
                                val targetDate = LocalDate.parse(ann.targetDate)
                                val daysDiff = java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), targetDate)
                                val isPast = daysDiff < 0
                                val displayDays = Math.abs(daysDiff)
                                val color = Color(android.graphics.Color.parseColor(ann.colorHex))
                                
                                Box(
                                    modifier = Modifier
                                        .width(160.dp)
                                        .height(100.dp)
                                        .glassmorphicCard(cornerRadius = 20.dp, surfaceAlpha = 0.2f, shadowElevation = 0.dp)
                                        .background(color.copy(alpha = 0.15f))
                                        .padding(16.dp)
                                ) {
                                    Column(verticalArrangement = Arrangement.Center, modifier = Modifier.fillMaxSize()) {
                                        Text(
                                            text = ann.title,
                                            style = MaterialTheme.typography.labelMedium.copy(
                                                color = weatherColors.textPrimary,
                                                fontWeight = FontWeight.Bold
                                            ),
                                            maxLines = 1,
                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Row(verticalAlignment = Alignment.Bottom) {
                                            Text(
                                                text = if (ann.isCountdown && !isPast) "还剩" else "已过",
                                                style = MaterialTheme.typography.labelSmall.copy(color = weatherColors.textPrimary.copy(alpha = 0.7f))
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "$displayDays",
                                                style = MaterialTheme.typography.titleLarge.copy(
                                                    color = color,
                                                    fontWeight = FontWeight.Black
                                                )
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "天",
                                                style = MaterialTheme.typography.labelSmall.copy(color = weatherColors.textPrimary.copy(alpha = 0.7f))
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(100.dp)
                                .glassmorphicCard(cornerRadius = 20.dp, surfaceAlpha = 0.2f, shadowElevation = 0.dp)
                                .clickable { onNavigateToAnniversary() }
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Add, contentDescription = "添加纪念日", tint = weatherColors.textPrimary.copy(alpha = 0.6f))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "添加纪念日或倒数",
                                    style = MaterialTheme.typography.labelLarge.copy(color = weatherColors.textPrimary.copy(alpha = 0.6f))
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    // Bento Box Layout
                    // 1. Hero Card
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 220.dp)
                        .glassmorphicCard(cornerRadius = 40.dp, surfaceAlpha = weatherColors.surfaceAlpha)
                        .clickable { onNavigateToDayDetail(LocalDate.now().toString()) }
                        .padding(32.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "记录此刻",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = weatherColors.textPrimary
                            )
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (todayRecords.isEmpty()) "捕获今天的每一个小情绪，让它们成为珍贵的回忆。" else "今天已有 ${todayRecords.size} 条记录，左右滑动快速回看。",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = weatherColors.textPrimary.copy(alpha = 0.7f),
                                lineHeight = 22.sp
                            )
                        )
                        Spacer(modifier = Modifier.height(18.dp))
                        if (todayRecords.isNotEmpty()) {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                                items(todayRecords, key = { it.id }) { record ->
                                    Surface(
                                        modifier = Modifier
                                            .width(180.dp)
                                            .clickable { onNavigateToDayDetail(record.date) },
                                        shape = RoundedCornerShape(18.dp),
                                        color = MaterialTheme.colorScheme.surface.copy(alpha = .65f),
                                    ) {
                                        Column(Modifier.padding(14.dp)) {
                                            Text("${record.time} · ${record.moodType}", fontWeight = FontWeight.Bold, color = weatherColors.textPrimary)
                                            Text(
                                                listOfNotNull(record.note, record.content).joinToString(" ").ifBlank { "没有留下文字" },
                                                maxLines = 2,
                                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                                color = weatherColors.textPrimary.copy(alpha = .72f),
                                            )
                                        }
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(18.dp))
                        }
                        
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .background(weatherColors.textPrimary)
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "新增心情记录", tint = MaterialTheme.colorScheme.surface, modifier = Modifier.size(32.dp))
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .glassmorphicCard(cornerRadius = 28.dp, surfaceAlpha = weatherColors.surfaceAlpha)
                        .clickable { onNavigateToDayDetail(LocalDate.now().toString()) }
                        .padding(22.dp)
                ) {
                    Column {
                        Text("今日收获 · 时光回流", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = weatherColors.textPrimary)
                        Spacer(Modifier.height(8.dp))
                        val latestText = todayRecords.asReversed().firstNotNullOfOrNull { record ->
                            listOfNotNull(record.note, record.content).joinToString(" ").takeIf { it.isNotBlank() }
                        }
                        Text(
                            latestText ?: "点这里写下今天值得记住的一件小事。",
                            maxLines = 3,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            color = weatherColors.textPrimary.copy(alpha = .75f),
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(100.dp))
            }
        }
    }
}
}

@Composable
fun PermissionGuideCard(onGrantClick: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Card(
            modifier = Modifier
                .padding(32.dp)
                .fillMaxWidth(),
            shape = RoundedCornerShape(32.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(56.dp), tint = Color(0xFF0066FF))
                Spacer(modifier = Modifier.height(24.dp))
                Text("需要您的定位权限", fontWeight = FontWeight.Bold, fontSize = 22.sp, color = MaterialTheme.colorScheme.onSurface)
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "为了提供智能天气感知和动态背景，我们需要获取您的位置以匹配实时天气数据。", 
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    lineHeight = 24.sp
                )
                Spacer(modifier = Modifier.height(32.dp))
                Button(
                    onClick = onGrantClick, 
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("授权位置信息", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
