package com.example.mdd_calender.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import com.example.mdd_calender.data.WeatherCondition
import com.example.mdd_calender.ui.MoodViewModel
import com.example.mdd_calender.ui.components.MoodType
import com.example.mdd_calender.ui.components.MoodVectorIcon
import com.example.mdd_calender.ui.components.glassmorphicCard
import com.example.mdd_calender.ui.theme.getWeatherColors
import com.example.mdd_calender.utils.CognitivePsychoAnalyzer
import com.example.mdd_calender.utils.RiskLevel
import java.time.format.DateTimeFormatter
import java.time.LocalDate
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items as lazyItems
import android.net.Uri

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalysisScreen(
    viewModel: MoodViewModel,
    onBack: () -> Unit
) {
    val currentMonth by viewModel.currentMonth.collectAsState()
    val monthlyRecords by viewModel.monthlyRecords.collectAsState()
    val weatherData by viewModel.weatherData.collectAsState()
    val iconConfig by viewModel.iconConfig.collectAsState()
    
    val weatherColors = getWeatherColors(weatherData?.condition ?: WeatherCondition.CLEAR, false)
    var selectedFilterMood by remember { mutableStateOf<MoodType?>(null) }
    var showBottomSheet by remember { mutableStateOf(false) }

    // Aggregate Data
    val totalRecords = monthlyRecords.size
    val moodCounts = monthlyRecords.groupingBy { it.moodType }.eachCount()
    val sortedMoods = moodCounts.entries.sortedByDescending { it.value }
    val dominantMood = sortedMoods.firstOrNull()?.key?.let { MoodType.fromLabel(it) }

    // Psychological Analysis
    val psychoReport = remember(monthlyRecords) { CognitivePsychoAnalyzer.analyze(monthlyRecords) }

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
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(56.dp))
            
            // Top Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
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
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { viewModel.changeMonth(currentMonth.minusMonths(1)) }) {
                        Icon(Icons.Default.ChevronLeft, contentDescription = "上个月", tint = weatherColors.textPrimary)
                    }
                    val formatter = DateTimeFormatter.ofPattern("yyyy年 M月")
                    Text(
                        text = currentMonth.format(formatter), 
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = weatherColors.textPrimary,
                            letterSpacing = (-0.5).sp
                        )
                    )
                    IconButton(onClick = { viewModel.changeMonth(currentMonth.plusMonths(1)) }) {
                        Icon(Icons.Default.ChevronRight, contentDescription = "下个月", tint = weatherColors.textPrimary)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            var selectedTabIndex by remember { mutableIntStateOf(0) }
            
            TabRow(
                selectedTabIndex = selectedTabIndex,
                containerColor = Color.Transparent,
                contentColor = weatherColors.textPrimary,
                indicator = { tabPositions ->
                    if (selectedTabIndex < tabPositions.size) {
                        TabRowDefaults.Indicator(
                            Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                            color = weatherColors.textPrimary
                        )
                    }
                },
                divider = { }
            ) {
                Tab(
                    selected = selectedTabIndex == 0,
                    onClick = { selectedTabIndex = 0 },
                    text = { Text("📊 基础数据", fontWeight = FontWeight.Bold) }
                )
                Tab(
                    selected = selectedTabIndex == 1,
                    onClick = { selectedTabIndex = 1 },
                    text = { Text("🏥 深度洞察", fontWeight = FontWeight.Bold) }
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))

            if (totalRecords == 0) {
                // Empty State
                Box(modifier = Modifier.fillMaxWidth().height(300.dp), contentAlignment = Alignment.Center) {
                    Text(
                        "这个月还没有记录情绪，\n去首页记录一次吧！",
                        color = weatherColors.textPrimary.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            } else {
                when (selectedTabIndex) {
                    0 -> {
                        // Overview Header Card
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .glassmorphicCard(cornerRadius = 32.dp, surfaceAlpha = weatherColors.surfaceAlpha)
                                .padding(32.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(
                                        "总计记录",
                                        style = MaterialTheme.typography.labelLarge.copy(
                                            color = weatherColors.textPrimary.copy(alpha = 0.7f),
                                            fontWeight = FontWeight.Medium
                                        )
                                    )
                                    Text(
                                        "$totalRecords 天",
                                        style = MaterialTheme.typography.displayMedium.copy(
                                            color = weatherColors.textPrimary,
                                            fontWeight = FontWeight.Black,
                                            letterSpacing = (-1).sp
                                        )
                                    )
                                }
                                
                                if (dominantMood != null) {
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            "主导情绪",
                                            style = MaterialTheme.typography.labelLarge.copy(
                                                color = weatherColors.textPrimary.copy(alpha = 0.7f),
                                                fontWeight = FontWeight.Medium
                                            )
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Box(
                                            modifier = Modifier
                                                .size(56.dp)
                                                .background(dominantMood.color.copy(alpha = 0.2f), CircleShape)
                                                .padding(8.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            MoodVectorIcon(mood = dominantMood, config = iconConfig, modifier = Modifier.size(40.dp))
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // Distribution Bar Chart
                        Text(
                            "情绪色彩流",
                            style = MaterialTheme.typography.titleMedium.copy(
                                color = weatherColors.textPrimary,
                                fontWeight = FontWeight.Bold
                            ),
                            modifier = Modifier.padding(start = 8.dp, bottom = 12.dp)
                        )
                        
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(24.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .glassmorphicCard(cornerRadius = 12.dp, surfaceAlpha = 0.3f, shadowElevation = 0.dp)
                        ) {
                            sortedMoods.forEach { entry ->
                                val mood = MoodType.fromLabel(entry.key)
                                val weight = entry.value.toFloat() / totalRecords
                                Box(
                                    modifier = Modifier
                                        .weight(weight)
                                        .fillMaxHeight()
                                        .background(mood?.color ?: Color.Gray)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(32.dp))

                        // Bento Grid for details
                        Text(
                            "频次详情",
                            style = MaterialTheme.typography.titleMedium.copy(
                                color = weatherColors.textPrimary,
                                fontWeight = FontWeight.Bold
                            ),
                            modifier = Modifier.padding(start = 8.dp, bottom = 16.dp)
                        )

                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.fillMaxWidth().heightIn(max = 1000.dp) // Nested scrolling constraint
                        ) {
                            items(sortedMoods) { entry ->
                                val mood = MoodType.fromLabel(entry.key)
                                val percentage = ((entry.value.toFloat() / totalRecords) * 100).toInt()
                                
                                Box(
                                    modifier = Modifier
                                        .aspectRatio(1.5f)
                                        .glassmorphicCard(cornerRadius = 24.dp, surfaceAlpha = weatherColors.surfaceAlpha)
                                        .clickable {
                                            selectedFilterMood = mood
                                            showBottomSheet = true
                                        }
                                        .padding(16.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.fillMaxSize(),
                                        verticalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.Top
                                        ) {
                                            if (mood != null) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(36.dp)
                                                        .background(mood.color.copy(alpha = 0.15f), CircleShape)
                                                        .padding(6.dp),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    MoodVectorIcon(mood = mood, config = iconConfig, modifier = Modifier.size(24.dp))
                                                }
                                            }
                                            Text(
                                                "$percentage%",
                                                style = MaterialTheme.typography.labelLarge.copy(
                                                    color = weatherColors.textPrimary.copy(alpha = 0.6f),
                                                    fontWeight = FontWeight.Bold
                                                )
                                            )
                                        }
                                        
                                        Column {
                                            Text(
                                                text = "${entry.value} 次",
                                                style = MaterialTheme.typography.titleLarge.copy(
                                                    color = weatherColors.textPrimary,
                                                    fontWeight = FontWeight.Black
                                                )
                                            )
                                            Text(
                                                text = entry.key,
                                                style = MaterialTheme.typography.bodySmall.copy(
                                                    color = mood?.color ?: weatherColors.textPrimary.copy(alpha = 0.7f),
                                                    fontWeight = FontWeight.Bold
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    1 -> {
                        // Psychological Insight Panel
                        Text(
                            "深度情绪洞察 (CBT医学模型)",
                            style = MaterialTheme.typography.titleMedium.copy(
                                color = weatherColors.textPrimary,
                                fontWeight = FontWeight.Bold
                            ),
                            modifier = Modifier.padding(start = 8.dp, bottom = 12.dp)
                        )

                        val riskColor = when (psychoReport.riskLevel) {
                            RiskLevel.LOW -> Color(0xFF4CAF50)
                            RiskLevel.MEDIUM -> Color(0xFFFF9800)
                            RiskLevel.HIGH -> Color(0xFFE53935)
                            RiskLevel.CRISIS -> Color(0xFFD32F2F)
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .glassmorphicCard(cornerRadius = 24.dp, surfaceAlpha = weatherColors.surfaceAlpha)
                                .padding(24.dp)
                        ) {
                            Column {
                                // Title and Status
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = if (psychoReport.riskLevel == RiskLevel.HIGH || psychoReport.riskLevel == RiskLevel.CRISIS) Icons.Default.Warning else Icons.Default.Favorite,
                                        contentDescription = null,
                                        tint = riskColor,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = psychoReport.summary,
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = riskColor
                                        )
                                    )
                                }

                                // Empathy & Comfort Section
                                if (psychoReport.comfortMessage != null) {
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(riskColor.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                                            .padding(16.dp)
                                    ) {
                                        Text(
                                            text = "“${psychoReport.comfortMessage}”",
                                            style = MaterialTheme.typography.bodyLarge.copy(
                                                color = weatherColors.textPrimary,
                                                lineHeight = 24.sp,
                                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                                            )
                                        )
                                    }
                                }
                                
                                // Cognitive Distortions
                                if (psychoReport.identifiedDistortions.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                        "💡 识别到的认知扭曲：",
                                        style = MaterialTheme.typography.labelLarge.copy(
                                            color = weatherColors.textPrimary,
                                            fontWeight = FontWeight.Bold
                                        )
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        psychoReport.identifiedDistortions.joinToString("、"),
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            color = weatherColors.textPrimary.copy(alpha = 0.8f)
                                        )
                                    )
                                }

                                // Clinical Syndrome Suspicions (NEW)
                                if (psychoReport.suspectedDisorders.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(Color(0xFFFFCC80).copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                                            .padding(16.dp)
                                    ) {
                                        Column {
                                            Text(
                                                "⚠️ 临床初筛倾向 (基于 NLP 模型)：",
                                                style = MaterialTheme.typography.labelLarge.copy(
                                                    color = Color(0xFFE65100),
                                                    fontWeight = FontWeight.Bold
                                                )
                                            )
                                            Spacer(modifier = Modifier.height(8.dp))
                                            psychoReport.suspectedDisorders.forEach { disorder ->
                                                Text(
                                                    "- $disorder",
                                                    style = MaterialTheme.typography.bodyMedium.copy(
                                                        color = Color(0xFFE65100).copy(alpha = 0.9f),
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                )
                                            }
                                        }
                                    }
                                }

                                // Advice
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    "💬 干预建议：",
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        color = weatherColors.textPrimary,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    psychoReport.advice,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        color = weatherColors.textPrimary.copy(alpha = 0.8f),
                                        lineHeight = 22.sp
                                    )
                                )

                                // Medical Referral (CRISIS / HIGH)
                                if (psychoReport.medicalReferral != null) {
                                    Spacer(modifier = Modifier.height(24.dp))
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .glassmorphicCard(cornerRadius = 16.dp, surfaceAlpha = 0.2f, shadowElevation = 0.dp)
                                            .background(Color(0xFFFFEBEE).copy(alpha = 0.3f))
                                            .padding(16.dp)
                                    ) {
                                        Column {
                                            Text(
                                                "🆘 医疗干预指引",
                                                style = MaterialTheme.typography.titleSmall.copy(
                                                    color = Color(0xFFD32F2F),
                                                    fontWeight = FontWeight.Bold
                                                )
                                            )
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text(
                                                psychoReport.medicalReferral,
                                                style = MaterialTheme.typography.bodySmall.copy(
                                                    color = weatherColors.textPrimary,
                                                    lineHeight = 20.sp
                                                )
                                            )
                                        }
                                    }
                                }

                                // Legal Disclaimer
                                Spacer(modifier = Modifier.height(24.dp))
                                Text(
                                    "* 本分析基于认知行为模型，由本地算法自动生成，绝对保护隐私。仅供心理探索参考，不替代专业医疗诊断。",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = weatherColors.textPrimary.copy(alpha = 0.5f)
                                    )
                                )
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(100.dp))
            }
        }
    }

    if (showBottomSheet && selectedFilterMood != null) {
        val filteredRecords = monthlyRecords.filter { it.moodType == selectedFilterMood?.label }.sortedByDescending { it.date }
        ModalBottomSheet(
            onDismissRequest = { showBottomSheet = false },
            containerColor = weatherColors.backgroundStart.copy(alpha = 0.95f),
            scrimColor = Color.Black.copy(alpha = 0.5f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(selectedFilterMood!!.color.copy(alpha = 0.2f), CircleShape)
                            .padding(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        MoodVectorIcon(mood = selectedFilterMood!!, config = iconConfig, modifier = Modifier.size(24.dp))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        "${selectedFilterMood!!.label} 的记忆",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = weatherColors.textPrimary
                        )
                    )
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxWidth().heightIn(max = 500.dp)
                ) {
                    lazyItems(filteredRecords) { record ->
                        val dateText = LocalDate.parse(record.date).format(DateTimeFormatter.ofPattern("M月d日"))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .glassmorphicCard(cornerRadius = 16.dp, surfaceAlpha = 0.15f)
                                .padding(16.dp)
                        ) {
                            Column {
                                Text(
                                    dateText,
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        color = weatherColors.textPrimary.copy(alpha = 0.6f),
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                val textContent = "${record.note ?: ""} ${record.content ?: ""}".trim()
                                if (textContent.isNotEmpty()) {
                                    Text(
                                        textContent,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            color = weatherColors.textPrimary,
                                            lineHeight = 20.sp
                                        )
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
                                            .height(120.dp)
                                            .clip(RoundedCornerShape(8.dp)),
                                        contentScale = ContentScale.Crop
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}
