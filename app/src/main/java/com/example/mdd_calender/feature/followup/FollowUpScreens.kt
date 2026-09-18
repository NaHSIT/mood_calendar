package com.example.mdd_calender.feature.followup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EventNote
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Student component. The caller must supply an already-authorized, current-student-only state. */
@Composable
fun StudentFollowUpScreen(
    state: StudentFollowUpUiState,
    onTaskAction: (taskId: String) -> Unit,
    onRequestExit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("我的随访计划", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(state.statusLabel, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    val completed = state.tasks.count { it.status == FollowUpTaskUiStatus.COMPLETED }
                    Text("已完成 $completed / ${state.tasks.size} 项安排", style = MaterialTheme.typography.bodyMedium)
                    if (state.tasks.isNotEmpty()) LinearProgressIndicator(progress = { completed.toFloat() / state.tasks.size }, modifier = Modifier.fillMaxWidth())
                }
            }
            PolicyDisclosure(state.policyDisclosure)
        }
        if (state.tasks.isEmpty()) {
            item { CareEmptyState("当前没有随访任务", "新的打卡和复测安排会显示在这里。", Icons.Outlined.EventNote) }
        } else {
            items(state.tasks, key = { it.taskId }) { task ->
                StudentTaskCard(task = task, onAction = { onTaskAction(task.taskId) })
            }
        }
        if (state.isTracking) {
            item {
                OutlinedButton(
                    onClick = onRequestExit,
                    enabled = !state.exitRequestPending,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (state.exitRequestPending) "退出申请审核中" else "申请退出 AA 随访")
                }
            }
        }
    }
}

@Composable
private fun StudentTaskCard(task: StudentFollowUpTaskUi, onAction: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(task.title, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                StatusPill(task.status)
            }
            Text(task.dueLabel, style = MaterialTheme.typography.bodySmall)
            task.actionLabel?.let { label ->
                Button(onClick = onAction, modifier = Modifier.fillMaxWidth()) { Text(label) }
            }
        }
    }
}

/** Teacher component. Its state intentionally has no raw health, diary, score, or item-answer field. */
@Composable
fun TeacherFollowUpScreen(
    state: TeacherFollowUpUiState,
    onOpenEnrollment: (enrollmentId: String) -> Unit,
    onReviewExit: (enrollmentId: String, approve: Boolean, note: String) -> Unit = { _, _, _ -> },
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("责任范围内随访", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            PolicyDisclosure(state.policyDisclosure)
        }
        if (state.enrollments.isEmpty()) {
            item { Text("当前没有负责的 AA 随访", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else {
            items(state.enrollments, key = { it.enrollmentId }) { item ->
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(item.studentCode, fontWeight = FontWeight.Bold)
                        Text("AA 状态：${item.aaStatusLabel}")
                        Text("任务完成度：${item.completionLabel}")
                        Text("待联系：${item.pendingContactCount}")
                        OutlinedButton(
                            onClick = { onOpenEnrollment(item.enrollmentId) },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("查看随访详情") }
                        if (item.exitReviewPending) {
                            ExitReviewActions(item = item, onReviewExit = onReviewExit)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExitReviewActions(
    item: TeacherEnrollmentSummaryUi,
    onReviewExit: (enrollmentId: String, approve: Boolean, note: String) -> Unit,
) {
    var note by rememberSaveable(item.enrollmentId) { mutableStateOf("") }
    Text("学生退出申请", fontWeight = FontWeight.SemiBold)
    Text("申请理由：${item.exitReason?.takeIf(String::isNotBlank) ?: "未填写"}")
    OutlinedTextField(
        value = note,
        onValueChange = { note = it },
        label = { Text("审核依据（必填）") },
        modifier = Modifier.fillMaxWidth(),
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(
            onClick = { onReviewExit(item.enrollmentId, false, note.trim()) },
            enabled = note.isNotBlank(),
            modifier = Modifier.weight(1f),
        ) { Text("拒绝退出") }
        Button(
            onClick = { onReviewExit(item.enrollmentId, true, note.trim()) },
            enabled = note.isNotBlank(),
            modifier = Modifier.weight(1f),
        ) { Text("批准退出") }
    }
    Text(
        "批准前仍需校验稳定观察条件及未处理安全关注；批准后由仓储取消未来任务。",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun PolicyDisclosure(text: String) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = RoundedCornerShape(12.dp),
    ) {
        Text(text, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun StatusPill(status: FollowUpTaskUiStatus) {
    val (label, color) = when (status) {
        FollowUpTaskUiStatus.UPCOMING -> "待处理" to MaterialTheme.colorScheme.primary
        FollowUpTaskUiStatus.DUE -> "今日到期" to MaterialTheme.colorScheme.tertiary
        FollowUpTaskUiStatus.OVERDUE -> "已逾期" to MaterialTheme.colorScheme.error
        FollowUpTaskUiStatus.PAUSED -> "已暂停" to MaterialTheme.colorScheme.outline
        FollowUpTaskUiStatus.COMPLETED -> "已完成" to Color(0xFF2E7D32)
        FollowUpTaskUiStatus.CANCELLED -> "已取消" to MaterialTheme.colorScheme.outline
    }
    Text(label, color = color, style = MaterialTheme.typography.labelMedium)
}
