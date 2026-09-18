@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.mdd_calender.integration.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.HealthAndSafety
import androidx.compose.material.icons.outlined.EventNote
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.FactCheck
import androidx.compose.material3.Surface
import androidx.compose.material3.Icon
import androidx.compose.material3.RadioButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import com.example.mdd_calender.ui.components.CareEntry
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.mdd_calender.domain.model.CareResult
import com.example.mdd_calender.domain.model.AaStatus
import com.example.mdd_calender.domain.model.ExitReviewDecision
import com.example.mdd_calender.feature.assessment.AssessmentScorer
import com.example.mdd_calender.feature.assessment.AssessmentType
import com.example.mdd_calender.feature.assessment.QuestionnaireCatalog
import com.example.mdd_calender.feature.assessment.ScoreResult
import com.example.mdd_calender.feature.assessment.itemCount
import com.example.mdd_calender.feature.assessment.toAssessmentRecord
import com.example.mdd_calender.feature.followup.FollowUpRepositoryUiAdapter
import com.example.mdd_calender.feature.followup.StudentFollowUpScreen
import com.example.mdd_calender.feature.health.HealthDataDomain
import com.example.mdd_calender.feature.health.HealthManagementScreen
import com.example.mdd_calender.feature.health.HealthMetric
import com.example.mdd_calender.feature.health.HealthProviderAvailability
import com.example.mdd_calender.feature.health.HealthProviderCapability
import com.example.mdd_calender.feature.health.HealthConsentSnapshot
import com.example.mdd_calender.feature.health.HealthManagementUiState
import com.example.mdd_calender.feature.health.RawHealthDataScreen
import com.example.mdd_calender.feature.health.RawHealthDataView
import com.example.mdd_calender.feature.teacher.TeacherWorkbenchRoute
import com.example.mdd_calender.feature.teacher.TeacherWorkbenchViewModel
import kotlinx.coroutines.launch
import java.util.UUID

@Composable
fun CareHubScreen(
    onAssessment: () -> Unit,
    onHealth: () -> Unit,
    onFollowUp: () -> Unit,
    onTeacher: () -> Unit,
) {
    LazyColumn(
        Modifier.fillMaxSize(), contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Text("校园照护", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            Text("让每一份感受，都被认真对待。", Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Icon(Icons.Outlined.FavoriteBorder, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                    Text("给自己一点时间", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                    Text("通过 PHQ-9 与 GAD-7，回顾最近两周的状态。没有标准答案，按真实感受选择就好。", style = MaterialTheme.typography.bodyMedium)
                    Button(onClick = onAssessment, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), shape = RoundedCornerShape(16.dp)) { Text("填写量表") }
                }
            }
        }
        item { Text("我的照护", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
        item { CareEntry("健康数据授权", "心率与睡眠，仅在你授权后同步", Icons.Outlined.HealthAndSafety, onHealth) }
        item { CareEntry("我的 AA 随访", "查看打卡、复测与持续关怀安排", Icons.Outlined.EventNote, onFollowUp) }
        item { CareEntry("教师预警工作台（演示）", "查看关注事件，跟进与记录处理", Icons.Outlined.FactCheck, onTeacher) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Outlined.Shield, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("当前为演示模式。原始健康数据仅本人可见，授权由你决定。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun AssessmentRoute(services: AppCareServices, followUpTaskId: String? = null, onBack: () -> Unit) {
    var type by rememberSaveable { mutableStateOf(AssessmentType.PHQ_9) }
    var phqAnswers by rememberSaveable { mutableStateOf(List<Int?>(9) { null }) }
    var gadAnswers by rememberSaveable { mutableStateOf(List<Int?>(7) { null }) }
    val answers = if (type == AssessmentType.PHQ_9) phqAnswers else gadAnswers
    var submissionId by rememberSaveable { mutableStateOf(UUID.randomUUID().toString()) }
    var completedAt by rememberSaveable { mutableStateOf<Long?>(null) }
    var submitted by rememberSaveable { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by rememberSaveable { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val descriptor = QuestionnaireCatalog.descriptor(type)
    val score = AssessmentScorer.score(type, answers.toList())

    Scaffold(topBar = { TopAppBar(title = { Text("量表评估") }, navigationIcon = { TextButton(onClick = onBack) { Text("返回") } }) }) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).testTag("assessment-list"),
            contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(enabled = !busy && completedAt == null, selected = type == AssessmentType.PHQ_9, onClick = { type = AssessmentType.PHQ_9 }, label = { Text("PHQ-9") })
                    FilterChip(enabled = !busy && completedAt == null, selected = type == AssessmentType.GAD_7, onClick = { type = AssessmentType.GAD_7 }, label = { Text("GAD-7") })
                }
                Text(descriptor.recallPeriod, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(16.dp))
                Text("已完成 ${answers.count { it != null }} / ${answers.size} 题", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                LinearProgressIndicator(progress = { answers.count { it != null }.toFloat() / answers.size }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                Text("按真实感受选择，筛查结果不构成诊断。", Modifier.padding(top = 12.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                var showNotice by rememberSaveable { mutableStateOf(false) }
                TextButton(onClick = { showNotice = !showNotice }) { Text(if (showNotice) "收起量表说明" else "量表说明与使用范围") }
                if (showNotice) Text(descriptor.contentNotice, style = MaterialTheme.typography.bodySmall)
            }
            itemsIndexed(descriptor.items) { index, question ->
                Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text("${(index + 1).toString().padStart(2, '0')}  /  ${answers.size}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        Text(question, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            descriptor.responseOptions.forEach { option ->
                                val selected = answers[index] == option.score
                                Surface(shape = RoundedCornerShape(14.dp), color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                                    border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)) {
                                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("answer-$index-${option.score}")
                                        .selectable(selected = selected, enabled = !busy && completedAt == null, role = Role.RadioButton, onClick = {
                                        val updated = answers.toMutableList().also { it[index] = option.score }.toList()
                                        if (type == AssessmentType.PHQ_9) phqAnswers = updated else gadAnswers = updated
                                    }).padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                        RadioButton(selected, onClick = null, enabled = !busy && completedAt == null)
                                        Text(option.label, Modifier.padding(start = 12.dp), style = MaterialTheme.typography.bodyMedium)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            item {
                if (score is ScoreResult.Incomplete) Text("还有 ${score.missingItemIndices.size} 题未填写")
                if (type == AssessmentType.PHQ_9 && (answers.getOrNull(8) ?: 0) > 0) {
                    Text(
                        "你在安全相关题目中选择了非零答案。若你正处于危险中，请立即联系可信任的人、当地急救或危机干预服务。提交后系统也会标记为需要尽快人工复核。",
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
                if (submitted && score is ScoreResult.Complete) Text("本次量表总分：${score.total}（筛查结果不构成诊断）")
                Button(
                    enabled = score is ScoreResult.Complete && !busy && !submitted,
                    onClick = {
                        val complete = score as? ScoreResult.Complete ?: return@Button
                        if (busy || submitted) return@Button
                        busy = true
                        if (completedAt == null) completedAt = System.currentTimeMillis()
                        scope.launch {
                            try {
                            val record = complete.toAssessmentRecord(
                                assessmentId = submissionId,
                                studentId = "demo-student",
                                instrumentVersion = descriptor.version,
                                answers = answers.map { requireNotNull(it) },
                                completedAtEpochMillis = requireNotNull(completedAt),
                            )
                            message = when (services.submitAssessmentAndTriggerCare(record)) {
                                is CareResult.Success -> {
                                    submitted = true
                                    if (followUpTaskId != null) {
                                        val adapter = FollowUpRepositoryUiAdapter(services.followUp, services.assessments)
                                        when (adapter.completeAssessmentTask(followUpTaskId, record.assessmentId, System.currentTimeMillis())) {
                                            is CareResult.Success -> "复测已保存，随访任务已完成。"
                                            is CareResult.Failure -> "复测已保存，但随访任务更新失败。"
                                        }
                                    } else "已保存并完成评估链；有关注事件时已进入模拟投递。"
                                }
                                is CareResult.Failure -> "流程未完成，点击提交可重试；已保存的量表不会重复新增。"
                            }
                            } finally { busy = false }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (busy) "提交中…" else if (submitted) "已提交" else "提交量表") }
                Spacer(Modifier.height(20.dp))
                OutlinedButton(onClick = onBack, Modifier.fillMaxWidth()) { Text("返回") }
            }
        }
    }
}

@Composable
fun HealthRoute(services: AppCareServices, onBack: () -> Unit) {
    var consent by remember { mutableStateOf(HealthConsentSnapshot("demo-student", emptySet(), 0, System.currentTimeMillis(), HealthDataDomain.DEMO)) }
    var rawData by remember { mutableStateOf(false) }
    var rawView by remember { mutableStateOf<RawHealthDataView>(RawHealthDataView.NotConnected) }
    var busy by remember { mutableStateOf(true) }
    var status by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        when (val result = services.healthConsentSnapshot()) {
            is CareResult.Success -> consent = result.value
            is CareResult.Failure -> status = "授权状态加载失败。"
        }
        busy = false
    }
    if (rawData) {
        RawHealthDataScreen(rawView, onBack = { rawData = false })
    } else {
        val capabilities = services.demoHealthProvider.capabilities()
        HealthManagementScreen(
            state = HealthManagementUiState(consent, capabilities, busy, status),
            onBack = onBack,
            onConsentChange = { metric, enabled ->
                scope.launch {
                    busy = true
                    status = null
                    when (val result = services.updateHealthConsent(metric, enabled)) {
                        is CareResult.Success -> {
                            consent = result.value
                            status = if (enabled) "授权已保存，演示数据已同步。" else "授权已撤回，后续同步已停止。"
                        }
                        is CareResult.Failure -> status = "授权更新失败，请重试。"
                    }
                    busy = false
                }
            },
            onDeleteRawData = { metrics ->
                scope.launch {
                    busy = true
                    status = when (val result = services.deleteRawHealthData(metrics)) {
                        is CareResult.Success -> "已删除 ${result.value} 条原始健康数据；既有最小化历史摘要不会随之撤回。"
                        is CareResult.Failure -> "删除失败，请重试。"
                    }
                    busy = false
                }
            },
            onOpenRawData = {
                scope.launch {
                    rawView = when (val result = services.rawHealthDataView()) {
                        is CareResult.Success -> result.value
                        is CareResult.Failure -> RawHealthDataView.AccessDenied
                    }
                    rawData = true
                }
            },
        )
    }
}

@Composable
fun FollowUpRoute(services: AppCareServices, onAssessmentTask: (String) -> Unit, onBack: () -> Unit) {
    var state by remember { mutableStateOf<com.example.mdd_calender.domain.model.CareResult<com.example.mdd_calender.feature.followup.StudentFollowUpUiState>?>(null) }
    val adapter = remember { FollowUpRepositoryUiAdapter(services.followUp, services.assessments) }
    val scope = rememberCoroutineScope()
    var operationMessage by remember { mutableStateOf<String?>(null) }
    var taskTypes by remember { mutableStateOf<Map<String, com.example.mdd_calender.domain.model.FollowUpTaskType>>(emptyMap()) }
    suspend fun reload() {
        services.ensureFollowUpTasks()
        taskTypes = when (val tasks = services.followUp.currentStudentTasks()) {
            is CareResult.Success -> tasks.value.associate { it.taskId to it.type }
            is CareResult.Failure -> emptyMap()
        }
        state = adapter.studentState()
    }
    LaunchedEffect(Unit) { reload() }
    Scaffold(topBar = {
        TopAppBar(
            title = { Text("我的 AA 随访") },
            navigationIcon = { TextButton(onClick = onBack) { Text("返回") } },
        )
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            when (val result = state) {
                null -> Text("加载中…", Modifier.padding(20.dp))
                is CareResult.Success -> StudentFollowUpScreen(
                    result.value,
                    onTaskAction = { taskId ->
                        if (taskTypes[taskId] == com.example.mdd_calender.domain.model.FollowUpTaskType.ASSESSMENT_RETAKE) {
                            onAssessmentTask(taskId)
                        } else scope.launch { adapter.completeTask(taskId, System.currentTimeMillis()); reload() }
                    },
                    onRequestExit = {
                        scope.launch {
                            operationMessage = when (val request = adapter.requestExit("学生主动申请退出")) {
                                is CareResult.Success -> "退出申请已提交，等待责任教师审核。"
                                is CareResult.Failure -> when (val error = request.error) {
                                    is com.example.mdd_calender.domain.model.CareFailure.Conflict -> error.reason
                                    else -> "退出申请失败，请稍后重试。"
                                }
                            }
                            reload()
                        }
                    },
                    modifier = Modifier.weight(1f),
                )
                is CareResult.Failure -> CareEmptyState("当前没有可用的随访记录", "加入 AA 随访后，你的打卡、复测与教师联系安排会显示在这里。", Icons.Outlined.EventNote, Modifier.padding(24.dp))
            }
            operationMessage?.let { Text(it, Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.primary) }
        }
    }
}

@Composable
fun TeacherRoute(services: AppCareServices, onBack: () -> Unit) {
    var reviewingExits by remember { mutableStateOf(false) }
    val vm: TeacherWorkbenchViewModel = viewModel(
        key = "teacher-workbench",
        factory = object : androidx.lifecycle.ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
                TeacherWorkbenchViewModel(services.teacherService) as T
        },
    )
    Scaffold(topBar = {
        TopAppBar(
            title = { Text(if (reviewingExits) "AA 退出审核" else "教师预警工作台") },
            navigationIcon = { TextButton(onClick = onBack) { Text("返回") } },
            actions = {
                TextButton(onClick = { reviewingExits = !reviewingExits }) {
                    Text(if (reviewingExits) "预警" else "退出审核")
                }
            },
        )
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (reviewingExits) TeacherExitReviewPanel(services) else TeacherWorkbenchRoute(vm)
        }
    }
}

@Composable
private fun TeacherExitReviewPanel(services: AppCareServices) {
    var enrollments by remember { mutableStateOf<CareResult<List<com.example.mdd_calender.domain.model.AaEnrollment>>?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    suspend fun reload() { enrollments = services.teacherFollowUp.listAssignedEnrollments() }
    LaunchedEffect(Unit) { reload() }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("仅展示责任范围内的退出申请。批准时会再次校验稳定观察期、量表复测和未解决安全关注，并取消未来任务。", style = MaterialTheme.typography.bodySmall)
        message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        when (val result = enrollments) {
            null -> Text("加载中…")
            is CareResult.Failure -> Text("退出申请加载失败。")
            is CareResult.Success -> {
                val pending = result.value.filter { it.status == AaStatus.EXIT_REVIEW_PENDING }
                if (pending.isEmpty()) CareEmptyState("暂时没有退出申请", "学生提交退出申请后，你可以在这里查看并审核。", Icons.Outlined.FactCheck)
                else LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(pending, key = { it.enrollmentId }) { enrollment ->
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("学生：${enrollment.studentId}", style = MaterialTheme.typography.titleMedium)
                                Text("申请理由：${enrollment.exitReview?.reason.orEmpty()}")
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(onClick = {
                                        scope.launch {
                                            message = when (val reviewed = services.teacherService.reviewExit(enrollment.enrollmentId, true, "责任教师批准")) {
                                                is CareResult.Success -> "已批准退出，未来任务已取消。"
                                                is CareResult.Failure -> (reviewed.error as? com.example.mdd_calender.domain.model.CareFailure.Conflict)?.reason ?: "批准失败。"
                                            }
                                            reload()
                                        }
                                    }) { Text("批准") }
                                    OutlinedButton(onClick = {
                                        scope.launch {
                                            message = when (services.teacherService.reviewExit(enrollment.enrollmentId, false, "继续观察")) {
                                                is CareResult.Success -> "已驳回，学生继续随访。"
                                                is CareResult.Failure -> "驳回失败。"
                                            }
                                            reload()
                                        }
                                    }) { Text("驳回") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
