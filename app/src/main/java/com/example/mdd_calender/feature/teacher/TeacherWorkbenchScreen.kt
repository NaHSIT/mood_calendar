package com.example.mdd_calender.feature.teacher

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
            onSimulateDelivery = { viewModel.simulateDelivery(it) },
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
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TeacherInboxFilter.values().forEach { filter ->
                FilterChip(selected = state.filter == filter, onClick = { onFilter(filter) }, label = { Text(filterLabel(filter)) })
            }
        }
        Spacer(Modifier.height(12.dp))
        state.message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        if (state.exitReviews.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text("AA 退出待审核", style = MaterialTheme.typography.titleMedium)
            Text(
                "批准前仍须由业务层复核稳定观察、安全关注及未来任务取消条件。",
                style = MaterialTheme.typography.bodySmall,
            )
            state.exitReviews.forEach { review ->
                ExitReviewCard(review = review, onReview = onReviewExit)
            }
            Spacer(Modifier.height(8.dp))
        }
        if (state.loading) Text("加载中…")
        else if (visible.isEmpty()) Text("暂无此状态的预警")
        else LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(visible, key = { it.summary.eventId }) { item ->
                Card(Modifier.fillMaxWidth().clickable { onOpen(item.summary.eventId) }) {
                    Column(Modifier.padding(16.dp)) {
                        Text("学生代号：${item.summary.studentCode}", style = MaterialTheme.typography.titleMedium)
                        Text("关注等级：${item.summary.concernLevel}")
                        Text("状态：${item.summary.disposition}")
                        Text("原因：${item.summary.minimalReasonTags.joinToString("、")}")
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onRefresh) { Text("刷新") }
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
    onSimulateDelivery: (String) -> Unit,
) {
    val item = details.item
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("预警详情", style = MaterialTheme.typography.headlineSmall)
        Text("学生代号：${item.summary.studentCode}")
        Text("关注等级：${item.summary.concernLevel}")
        Text("最少必要原因：${item.summary.minimalReasonTags.joinToString("、")}")
        Text("处置状态：${item.summary.disposition}")
        Text("仅显示教师工作摘要；不包含逐题答案、日记或原始健康数据。", style = MaterialTheme.typography.bodySmall)
        message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        item.intervention?.let { intervention ->
            when (intervention.status) {
                InterventionStatus.PENDING_CONFIRMATION -> Button(onClick = { onAcknowledge(intervention.caseId) }) { Text("确认预警") }
                InterventionStatus.CONFIRMED -> Button(onClick = { onStart(intervention.caseId) }) { Text("启动干预") }
                else -> Text("干预状态：${intervention.status}")
            }
        } ?: Text("当前暂无可操作的干预记录")
        OutlinedButton(onClick = { onSimulateDelivery(item.summary.eventId) }) { Text("模拟平台投递") }
        OutlinedButton(onClick = onBack) { Text("返回消息箱") }
    }
}

private fun filterLabel(filter: TeacherInboxFilter): String = when (filter) {
    TeacherInboxFilter.NEW -> "待确认"
    TeacherInboxFilter.IN_PROGRESS -> "处理中"
    TeacherInboxFilter.CLOSED -> "已结束"
}
