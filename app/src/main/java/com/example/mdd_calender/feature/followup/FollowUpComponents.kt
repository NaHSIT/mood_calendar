package com.example.mdd_calender.feature.followup

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun StudentFollowUpPanel(view: StudentFollowUpView, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text("随访状态：${view.status.displayName()}")
        Text(view.policyNotice)
        view.tasks.forEach { task ->
            Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text(task.type.displayName())
                    Text("任务状态：${task.status.displayName()}")
                }
            }
        }
    }
}

@Composable
fun TeacherFollowUpCard(summary: TeacherFollowUpSummary, modifier: Modifier = Modifier) {
    Card(modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text("学生代号：${summary.studentId}")
            Text("AA 状态：${summary.status.displayName()}")
            Text("完成进度：${summary.completedTasks}/${summary.totalTasks}")
            if (summary.needsContact) Text("需要人工联系")
        }
    }
}

private fun AaStatus.displayName() = when (this) {
    AaStatus.ACTIVE -> "跟踪中"
    AaStatus.PAUSED -> "已暂停"
    AaStatus.EXIT_REVIEW -> "待退出审核"
    AaStatus.EXITED -> "已退出"
}

private fun FollowUpTaskStatus.displayName() = when (this) {
    FollowUpTaskStatus.UPCOMING -> "未到期"
    FollowUpTaskStatus.DUE -> "已到期"
    FollowUpTaskStatus.OVERDUE -> "已逾期"
    FollowUpTaskStatus.PAUSED -> "已暂停"
    FollowUpTaskStatus.COMPLETED -> "已完成"
    FollowUpTaskStatus.CANCELLED -> "已取消"
}

private fun FollowUpTaskType.displayName() = when (this) {
    FollowUpTaskType.MOOD_CHECK_IN -> "简短心情打卡"
    FollowUpTaskType.FULL_ASSESSMENT -> "完整量表复测"
    FollowUpTaskType.TEACHER_CONTACT -> "教师联系"
}
