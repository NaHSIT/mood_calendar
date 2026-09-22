package com.example.mdd_calender.feature.teacher

import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material3.Surface
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import com.example.mdd_calender.ui.components.CareEmptyState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.mdd_calender.domain.model.AlertDisposition
import com.example.mdd_calender.domain.model.InterventionStatus
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun TeacherWorkbenchRoute(viewModel: TeacherWorkbenchViewModel) {
    val state by viewModel.state.collectAsState()
    if (state.selected == null) {
        TeacherInboxScreen(state, viewModel::setFilter, viewModel::select, viewModel::reviewExit, viewModel::refresh)
    } else {
        TeacherAlertDetailScreen(
            details = state.selected!!,
            message = state.message,
            onBack = viewModel::clearSelection,
            onAcknowledge = { viewModel.acknowledge(it) },
            onStart = { viewModel.start(it) },
            onClose = viewModel::close,
        )
    }
}

@Composable
fun TeacherInboxScreen(
    state: TeacherWorkbenchUiState,
    onFilter: (TeacherInboxFilter) -> Unit,
    onOpen: (String) -> Unit,
    onReviewExit: (enrollmentId: String, approve: Boolean, note: String) -> Unit,
    onRefresh: () -> Unit,
) {
    val visible = state.items.filter { item ->
        when (state.filter) {
            TeacherInboxFilter.NEW -> item.summary.disposition == AlertDisposition.NEW
            TeacherInboxFilter.IN_PROGRESS -> item.summary.disposition == AlertDisposition.ACKNOWLEDGED || item.summary.disposition == AlertDisposition.IN_PROGRESS
            TeacherInboxFilter.CLOSED -> item.summary.disposition == AlertDisposition.CLOSED
        }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("预警工作台", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        OutlinedButton(onClick = onRefresh) { Text("刷新") }
                    }
                    Text("${state.items.size} 条关注事件 · ${state.exitReviews.size} 项退出待审核", style = MaterialTheme.typography.bodyMedium)
                    Text("首页优先展示待确认事项，按状态切换处理队列。", style = MaterialTheme.typography.bodySmall)
                    Text("仅展示你负责学生的必要摘要，原始健康数据始终保持私密。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TeacherInboxFilter.values().forEach { filter ->
                FilterChip(selected = state.filter == filter, onClick = { onFilter(filter) }, label = { Text(filterLabel(filter)) })
            }
        }
        }
        state.message?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.primary) } }
        if (state.exitReviews.isNotEmpty()) {
            item {
            Text("AA 退出待审核", style = MaterialTheme.typography.titleMedium)
            Text(
                "请结合近期复测和联系记录审核，批准后将结束本次随访。",
                style = MaterialTheme.typography.bodySmall,
            )
            }
            items(state.exitReviews, key = { "exit:${it.enrollmentId}" }) { review ->
                ExitReviewCard(review = review, onReview = onReviewExit)
            }
        }
        if (state.loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        else if (visible.isEmpty()) item {
            CareEmptyState("暂无${filterLabel(state.filter)}的预警", "新的关注事件会在这里出现。可切换上方分类，查看其他处置阶段的记录。", Icons.Outlined.NotificationsNone)
        }
        else {
            items(visible, key = { it.summary.eventId }) { item ->
                Card(Modifier.fillMaxWidth().clickable { onOpen(item.summary.eventId) }, shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("脱敏代号：${item.summary.studentCode}", style = MaterialTheme.typography.titleMedium)
                        Text("关注等级：${item.summary.concernLevel.label()} · ${item.summary.disposition.label()}", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                        Text("触发原因：${item.summary.minimalReasonTags.joinToString("、", transform = ::reasonLabel)}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("更新时间：${formatTime(item.summary.occurredAtEpochMillis)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("查看详情与跟进 →", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

@Composable
private fun ExitReviewCard(
    review: TeacherExitReviewItem,
    onReview: (enrollmentId: String, approve: Boolean, note: String) -> Unit,
) {
    var note by remember(review.enrollmentId) { mutableStateOf("") }
    Card(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("随访档案：${review.enrollmentId.takeLast(8)}")
            Text("申请理由：${review.reason}")
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("审核备注（必填）") },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onReview(review.enrollmentId, true, note) }, enabled = note.isNotBlank()) {
                    Text("批准退出")
                }
                OutlinedButton(onClick = { onReview(review.enrollmentId, false, note) }, enabled = note.isNotBlank()) {
                    Text("驳回")
                }
            }
        }
    }
}

@Composable
fun TeacherAlertDetailScreen(
    details: TeacherAlertDetails,
    message: String?,
    onBack: () -> Unit,
    onAcknowledge: (String) -> Unit,
    onStart: (String) -> Unit,
    onClose: (String, String) -> Unit = { _, _ -> },
) {
    val item = details.item
    var note by remember(item.summary.eventId) { mutableStateOf("") }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Text("预警详情", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("① 预警基本信息", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.primaryContainer) {
            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("学生代号：${item.summary.studentCode}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text("${item.summary.concernLevel.label()} · ${item.summary.disposition.label()}", color = MaterialTheme.colorScheme.primary)
                Text("触发时间：${formatTime(item.summary.occurredAtEpochMillis)}", style = MaterialTheme.typography.bodyMedium)
            }
        }
        Text("仅显示教师工作摘要；不包含逐题答案、日记或原始健康数据。", style = MaterialTheme.typography.bodySmall)
        message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        Text("② 工作摘要", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        val assessmentReminders = item.summary.minimalReasonTags.filter { it.endsWith("MISSING_OR_EXPIRED") }
        val safetyWarnings = item.summary.minimalReasonTags.filter { it == "SAFETY_REVIEW_REQUIRED" }
        val auxiliarySignals = item.summary.minimalReasonTags - assessmentReminders.toSet() - safetyWarnings.toSet()
        if (assessmentReminders.isNotEmpty()) {
            SummaryGroup("评估提醒", assessmentReminders.joinToString("、", transform = ::reasonLabel), MaterialTheme.colorScheme.tertiaryContainer)
        }
        if (safetyWarnings.isNotEmpty()) {
            SummaryGroup("安全预警", safetyWarnings.joinToString("、", transform = ::reasonLabel), MaterialTheme.colorScheme.errorContainer)
        }
        if (auxiliarySignals.isNotEmpty()) {
            SummaryGroup("辅助标签", auxiliarySignals.joinToString("、", transform = ::reasonLabel), MaterialTheme.colorScheme.secondaryContainer)
        }
        if (item.summary.minimalReasonTags.isEmpty()) Text("当前没有附加摘要标签。", color = MaterialTheme.colorScheme.onSurfaceVariant)

        Text("③ 当前处理操作", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        item.intervention?.let { intervention ->
            when (intervention.status) {
                InterventionStatus.PENDING_CONFIRMATION -> Button(onClick = { onAcknowledge(intervention.caseId) }) { Text("确认预警") }
                InterventionStatus.CONFIRMED -> Button(onClick = { onStart(intervention.caseId) }) { Text("启动干预") }
                InterventionStatus.ACTIVE, InterventionStatus.PENDING_CLOSURE -> {
                    OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("联系及处理记录（必填）") }, modifier = Modifier.fillMaxWidth())
                    Button(onClick = { onClose(intervention.caseId, note) }, enabled = note.isNotBlank()) { Text("记录处理并结束本次干预") }
                    Text("结束本次干预后仍保留 AA 随访，退出须另行审核。", style = MaterialTheme.typography.bodySmall)
                }
                else -> Text("本次干预已结束")
            }
        } ?: Text("当前暂无可操作的干预记录")

        Text("④ 状态与处理历史", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text("${formatTime(item.summary.occurredAtEpochMillis)}  系统生成脱敏预警摘要")
        item.intervention?.startedAtEpochMillis?.let { Text("${formatTime(it)}  教师启动干预并进入随访衔接") }
        item.intervention?.minimalActionNote?.takeIf { it.isNotBlank() }?.let { Text("处理结果：$it") }
        if (item.intervention?.status == InterventionStatus.CLOSED) Text("当前状态：本次教师工单已结束；学生随访任务按独立状态继续。")
        OutlinedButton(onClick = onBack) { Text("返回消息箱") }
    }
}

@Composable
private fun SummaryGroup(title: String, text: String, color: androidx.compose.ui.graphics.Color) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), color = color) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, fontWeight = FontWeight.Bold)
            Text(text)
        }
    }
}

private fun formatTime(epochMillis: Long): String =
    Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))

private fun filterLabel(filter: TeacherInboxFilter): String = when (filter) {
    TeacherInboxFilter.NEW -> "待确认"
    TeacherInboxFilter.IN_PROGRESS -> "处理中"
    TeacherInboxFilter.CLOSED -> "已结束"
}

private fun com.example.mdd_calender.domain.model.ConcernLevel.label() = when (this) {
    com.example.mdd_calender.domain.model.ConcernLevel.LOW -> "低关注"
    com.example.mdd_calender.domain.model.ConcernLevel.MILD -> "轻度关注"
    com.example.mdd_calender.domain.model.ConcernLevel.MODERATE -> "中度关注"
    com.example.mdd_calender.domain.model.ConcernLevel.HIGH -> "高度关注"
    com.example.mdd_calender.domain.model.ConcernLevel.INSUFFICIENT_DATA -> "数据不足"
}

private fun AlertDisposition.label() = when (this) {
    AlertDisposition.NEW -> "待确认"
    AlertDisposition.ACKNOWLEDGED -> "已确认"
    AlertDisposition.IN_PROGRESS -> "处理中"
    AlertDisposition.CLOSED -> "已结束"
}

private fun reasonLabel(tag: String) = when (tag) {
    "PHQ_9_MISSING_OR_EXPIRED" -> "PHQ-9 缺失或过期"
    "GAD_7_MISSING_OR_EXPIRED" -> "GAD-7 缺失或过期"
    "SAFETY_REVIEW_REQUIRED" -> "需及时人工安全复核"
    "ELEVATED_HEART_RATE" -> "心率辅助异常信号"
    "LOW_SLEEP" -> "睡眠辅助异常信号"
    else -> "量表或辅助监测需复核"
}
