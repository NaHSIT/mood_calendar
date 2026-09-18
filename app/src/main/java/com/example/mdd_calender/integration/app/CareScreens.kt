@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.mdd_calender.integration.app

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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
    Column(
        Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("校园照护", style = MaterialTheme.typography.headlineSmall)
        Text("原型闭环入口；演示身份与真实学校认证隔离。", style = MaterialTheme.typography.bodyMedium)
        Button(onClick = onAssessment, Modifier.fillMaxWidth()) { Text("填写量表") }
        Button(onClick = onHealth, Modifier.fillMaxWidth()) { Text("健康数据授权") }
        Button(onClick = onFollowUp, Modifier.fillMaxWidth()) { Text("我的 AA 随访") }
        OutlinedButton(onClick = onTeacher, Modifier.fillMaxWidth()) { Text("教师预警工作台（演示）") }
    }
}

@Composable
fun AssessmentRoute(services: AppCareServices, followUpTaskId: String? = null, onBack: () -> Unit) {
    var type by remember { mutableStateOf(AssessmentType.PHQ_9) }
    val answers = remember(type) { mutableStateListOf<Int?>().apply { repeat(type.itemCount) { add(null) } } }
    var message by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val descriptor = QuestionnaireCatalog.descriptor(type)
    val score = AssessmentScorer.score(type, answers.toList())

    Scaffold(topBar = { TopAppBar(title = { Text("量表评估") }) }) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = type == AssessmentType.PHQ_9, onClick = { type = AssessmentType.PHQ_9 }, label = { Text("PHQ-9") })
                    FilterChip(selected = type == AssessmentType.GAD_7, onClick = { type = AssessmentType.GAD_7 }, label = { Text("GAD-7") })
                }
                Text(descriptor.recallPeriod, style = MaterialTheme.typography.bodySmall)
                Text(descriptor.contentNotice, style = MaterialTheme.typography.bodySmall)
            }
            itemsIndexed(descriptor.items) { index, question ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(question)
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            descriptor.responseOptions.forEach { option ->
                                FilterChip(
                                    selected = answers[index] == option.score,
                                    onClick = { answers[index] = option.score },
                                    label = { Text("${option.score} ${option.label}") },
                                )
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
                Button(
                    enabled = score is ScoreResult.Complete,
                    onClick = {
                        val complete = score as? ScoreResult.Complete ?: return@Button
                        scope.launch {
                            val record = complete.toAssessmentRecord(
                                assessmentId = UUID.randomUUID().toString(),
                                studentId = "demo-student",
                                instrumentVersion = descriptor.version,
                                answers = answers.map { requireNotNull(it) },
                                completedAtEpochMillis = System.currentTimeMillis(),
                            )
                            message = when (services.submitAssessmentAndTriggerCare(record)) {
                                is CareResult.Success -> {
                                    if (followUpTaskId != null) {
                                        val adapter = FollowUpRepositoryUiAdapter(services.followUp, services.assessments)
                                        when (adapter.completeAssessmentTask(followUpTaskId, record.assessmentId, System.currentTimeMillis())) {
                                            is CareResult.Success -> "复测已保存，随访任务已完成。"
                                            is CareResult.Failure -> "复测已保存，但随访任务更新失败。"
                                        }
                                    } else "已保存并完成评估链；有关注事件时已进入模拟投递。"
                                }
                                is CareResult.Failure -> "保存失败，请稍后重试。"
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("提交量表") }
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
                is CareResult.Failure -> Text("当前没有可用的随访记录。", Modifier.padding(20.dp))
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
                if (pending.isEmpty()) Text("当前没有待审核的退出申请。")
                else LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(pending, key = { it.enrollmentId }) { enrollment ->
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("学生：${enrollment.studentId}", style = MaterialTheme.typography.titleMedium)
                                Text("申请理由：${enrollment.exitReview?.reason.orEmpty()}")
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(onClick = {
                                        scope.launch {
                                            message = when (val reviewed = services.teacherFollowUp.reviewExit(enrollment.enrollmentId, ExitReviewDecision.APPROVED, "责任教师批准")) {
                                                is CareResult.Success -> "已批准退出，未来任务已取消。"
                                                is CareResult.Failure -> (reviewed.error as? com.example.mdd_calender.domain.model.CareFailure.Conflict)?.reason ?: "批准失败。"
                                            }
                                            reload()
                                        }
                                    }) { Text("批准") }
                                    OutlinedButton(onClick = {
                                        scope.launch {
                                            message = when (services.teacherFollowUp.reviewExit(enrollment.enrollmentId, ExitReviewDecision.REJECTED, "继续观察")) {
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
