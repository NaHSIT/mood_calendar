package com.example.mdd_calender.feature.followup

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
        modifier = modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("我的随访", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(state.statusLabel, style = MaterialTheme.typography.bodyLarge)
            PolicyDisclosure(state.policyDisclosure)
        }
        if (state.tasks.isEmpty()) {
            item { Text("当前没有随访任务", color = MaterialTheme.colorScheme.onSurfaceVariant) }
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
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(task.title, fontWeight = FontWeight.SemiBold)
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
                Card(
                    onClick = { onOpenEnrollment(item.enrollmentId) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(item.studentCode, fontWeight = FontWeight.Bold)
                        Text("AA 状态：${item.aaStatusLabel}")
                        Text("任务完成度：${item.completionLabel}")
                        Text("待联系：${item.pendingContactCount}")
                    }
                }
            }
        }
    }
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

