package com.example.mdd_calender.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.mdd_calender.data.WeatherCondition
import com.example.mdd_calender.domain.model.AssessmentRecord
import com.example.mdd_calender.domain.model.CareResult
import com.example.mdd_calender.integration.app.AppCareServices
import com.example.mdd_calender.ui.MoodViewModel
import com.example.mdd_calender.ui.analysis.DailyMoodPoint
import com.example.mdd_calender.ui.analysis.buildMoodInsight
import com.example.mdd_calender.ui.components.MoodType
import com.example.mdd_calender.ui.components.MoodVectorIcon
import com.example.mdd_calender.ui.components.glassmorphicCard
import com.example.mdd_calender.ui.theme.getWeatherColors
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

@Composable
fun AnalysisScreen(
    viewModel: MoodViewModel,
    careServices: AppCareServices? = null,
    onAssessment: () -> Unit = {},
    onBack: () -> Unit,
) {
    val month by viewModel.currentMonth.collectAsState()
    val records by viewModel.monthlyRecords.collectAsState()
    val weather by viewModel.weatherData.collectAsState()
    val icons by viewModel.iconConfig.collectAsState()
    val colors = getWeatherColors(weather?.condition ?: WeatherCondition.CLEAR, isSystemInDarkTheme())
    val insight = remember(records) { buildMoodInsight(records) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var assessments by remember { mutableStateOf<List<AssessmentRecord>>(emptyList()) }
    var assessmentError by remember { mutableStateOf<String?>(null) }

    suspend fun reloadAssessments() {
        if (careServices == null) return
        when (val result = careServices.assessments.listForCurrentStudent()) {
            is CareResult.Success -> {
                assessments = result.value.sortedByDescending { it.completedAtEpochMillis ?: 0 }
                assessmentError = null
            }
            is CareResult.Failure -> assessmentError = "量表记录加载失败，请稍后重试。"
        }
    }
    LaunchedEffect(careServices) { reloadAssessments() }
    val lifecycleOwner = LocalLifecycleOwner.current
    val resumeScope = rememberCoroutineScope()
    DisposableEffect(lifecycleOwner, careServices) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) resumeScope.launch { reloadAssessments() }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(colors.backgroundStart, colors.backgroundEnd)))) {
        LazyColumn(
            Modifier.fillMaxSize().widthIn(max = 840.dp).align(Alignment.TopCenter),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = colors.textPrimary) }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { viewModel.changeMonth(month.minusMonths(1)) }) { Icon(Icons.Default.ChevronLeft, "上个月", tint = colors.textPrimary) }
                        Text(month.format(DateTimeFormatter.ofPattern("yyyy年 M月")), fontWeight = FontWeight.Bold, color = colors.textPrimary)
                        IconButton(onClick = { viewModel.changeMonth(month.plusMonths(1)) }) { Icon(Icons.Default.ChevronRight, "下个月", tint = colors.textPrimary) }
                    }
                }
                TabRow(selectedTabIndex = tab, containerColor = Color.Transparent) {
                    Tab(tab == 0, { tab = 0 }, text = { Text("情绪趋势") })
                    Tab(tab == 1, { tab = 1 }, text = { Text("量表评估") })
                }
            }

            if (tab == 0) {
                if (records.isEmpty()) item { EmptyAnalysis(colors.textPrimary) }
                else {
                    item {
                        GlassCard {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Column {
                                    Text("记录天数", color = colors.textPrimary.copy(.65f))
                                    Text("${insight.recordDays} 天", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Black, color = colors.textPrimary)
                                    Text("共 ${records.size} 条", color = colors.textPrimary.copy(.6f))
                                }
                                insight.dominantMood?.let { mood ->
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("主导情绪", color = colors.textPrimary.copy(.65f))
                                        MoodVectorIcon(mood, Modifier.size(48.dp), config = icons)
                                        Text(mood.label, color = colors.textPrimary)
                                    }
                                }
                            }
                        }
                    }
                    item { SectionTitle("情绪趋势", colors.textPrimary); TrendChart(insight.points, colors.textPrimary) }
                    item { InsightCard("周趋势", insight.weeklySummary, colors.textPrimary) }
                    item { InsightCard("月趋势", insight.monthlySummary, colors.textPrimary) }
                    item { InsightCard("变化提醒", insight.changeNotice, colors.textPrimary) }
                    item { InsightCard("自我反思", insight.reflectionPrompt, colors.textPrimary, "基于记录规律生成，仅用于自我回顾，不作诊断。") }
                    item {
                        SectionTitle("频次详情", colors.textPrimary)
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            records.groupingBy { it.moodType }.eachCount().entries.sortedByDescending { it.value }.forEach { (label, count) ->
                                val mood = MoodType.fromLabel(label)
                                Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), color = mood.color.copy(.14f)) {
                                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                        MoodVectorIcon(mood, Modifier.size(34.dp), config = icons)
                                        Text(label, Modifier.padding(start = 12.dp).weight(1f), color = colors.textPrimary, fontWeight = FontWeight.Bold)
                                        Text("$count 次 · ${count * 100 / records.size}%", color = colors.textPrimary)
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                item {
                    GlassCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.AutoMirrored.Filled.Assignment, null)
                            Spacer(Modifier.width(10.dp))
                            Text(if (assessments.isEmpty()) "尚未完成量表" else "已完成 ${assessments.size} 次量表", fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(12.dp))
                        Text("PHQ-9 与 GAD-7 是情绪风险评估的核心数据源。提交后结果会在此处回显。", color = colors.textPrimary.copy(.78f))
                        assessmentError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = onAssessment, Modifier.fillMaxWidth()) { Text(if (assessments.isEmpty()) "开始填写量表" else "再次评估") }
                    }
                }
                items(assessments, key = { it.assessmentId }) { assessment -> AssessmentResultCard(assessment) }
                item { Text("量表用于筛查和自我了解，不是临床诊断。紧急安全顾虑请立即联系当地紧急服务或可信任的专业人员。", style = MaterialTheme.typography.bodySmall, color = colors.textPrimary.copy(.62f)) }
            }
            item { Spacer(Modifier.height(64.dp)) }
        }
    }
}

@Composable
private fun GlassCard(content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxWidth().glassmorphicCard(28.dp, .72f).padding(22.dp)) { Column(content = content) }
}

@Composable private fun SectionTitle(text: String, color: Color) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = color, modifier = Modifier.padding(bottom = 10.dp))
}

@Composable private fun InsightCard(title: String, body: String, color: Color, footnote: String? = null) {
    GlassCard {
        Text(title, fontWeight = FontWeight.Bold, color = color)
        Spacer(Modifier.height(8.dp))
        Text(body, color = color.copy(.82f))
        footnote?.let { Spacer(Modifier.height(8.dp)); Text(it, style = MaterialTheme.typography.bodySmall, color = color.copy(.58f)) }
    }
}

@Composable private fun EmptyAnalysis(color: Color) {
    Box(Modifier.fillMaxWidth().height(260.dp), contentAlignment = Alignment.Center) { Text("这个月还没有记录\n先写下一次真实感受吧", color = color.copy(.7f)) }
}

@Composable private fun TrendChart(points: List<DailyMoodPoint>, color: Color) {
    GlassCard {
        if (points.size < 2) Text("至少记录 2 天后显示折线趋势。", color = color.copy(.7f))
        else Canvas(Modifier.fillMaxWidth().height(160.dp)) {
            val step = size.width / (points.size - 1)
            val offsets = points.mapIndexed { index, point -> Offset(index * step, size.height - ((point.score - 1f) / 4f * size.height)) }
            repeat(5) { level ->
                val y = size.height * level / 4f
                drawLine(color.copy(.12f), Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
            }
            offsets.zipWithNext().forEach { (a, b) -> drawLine(color, a, b, 3.dp.toPx(), StrokeCap.Round) }
            offsets.forEach { drawCircle(color, 5.dp.toPx(), it) }
        }
        if (points.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text("${points.first().date.monthValue}/${points.first().date.dayOfMonth} — ${points.last().date.monthValue}/${points.last().date.dayOfMonth}（1 低 — 5 高）", style = MaterialTheme.typography.bodySmall, color = color.copy(.62f))
        }
    }
}

@Composable private fun AssessmentResultCard(record: AssessmentRecord) {
    val name = if (record.type.name == "PHQ_9") "PHQ-9" else "GAD-7"
    val band = when (record.symptomBand.name) {
        "MINIMAL" -> "极轻微"; "MILD" -> "轻度"; "MODERATE" -> "中度"
        "MODERATELY_SEVERE" -> "中重度"; "SEVERE" -> "重度"; else -> record.symptomBand.name
    }
    val time = record.completedAtEpochMillis?.let {
        Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
    } ?: "时间未知"
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(name, fontWeight = FontWeight.Bold)
                Text("${record.totalScore} 分", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
            Text("结果范围：$band")
            Text(time, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("已完成全部 ${record.answers.size} 题 · 结果仅用于筛查", style = MaterialTheme.typography.bodySmall)
        }
    }
}
