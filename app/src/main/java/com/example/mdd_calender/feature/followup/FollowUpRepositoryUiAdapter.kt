package com.example.mdd_calender.feature.followup

import com.example.mdd_calender.domain.model.AaStatus as DomainAaStatus
import com.example.mdd_calender.domain.model.AssessmentState
import com.example.mdd_calender.domain.model.CareFailure
import com.example.mdd_calender.domain.model.CareResult
import com.example.mdd_calender.domain.model.ExitReviewDecision
import com.example.mdd_calender.domain.model.FollowUpTask as DomainTask
import com.example.mdd_calender.domain.model.FollowUpTaskStatus as DomainTaskStatus
import com.example.mdd_calender.domain.model.FollowUpTaskType as DomainTaskType
import com.example.mdd_calender.domain.port.AssessmentRepository
import com.example.mdd_calender.domain.port.FollowUpRepository

/**
 * Boundary adapter for A's frozen repository contract. F's state-machine service remains
 * storage-agnostic; this adapter is the only place where domain models are mapped to F UI models.
 */
class FollowUpRepositoryUiAdapter(
    private val followUpRepository: FollowUpRepository,
    private val assessmentRepository: AssessmentRepository? = null,
) {
    suspend fun studentState(): CareResult<StudentFollowUpUiState> = when (val enrollment = followUpRepository.currentStudentEnrollment()) {
        is CareResult.Failure -> enrollment
        is CareResult.Success -> when (val tasks = followUpRepository.currentStudentTasks()) {
            is CareResult.Failure -> tasks
            is CareResult.Success -> CareResult.Success(
                StudentFollowUpUiState(
                    isTracking = enrollment.value.status != DomainAaStatus.EXITED,
                    statusLabel = enrollment.value.status.studentLabel(),
                    policyDisclosure = "演示随访周期，未经专业审核；AA 为业务跟踪状态，不是诊断。",
                    tasks = tasks.value.map(DomainTask::toUi),
                    exitRequestPending = enrollment.value.status == DomainAaStatus.EXIT_REVIEW_PENDING,
                ),
            )
        }
    }

    suspend fun completeTask(taskId: String, completedAtEpochMillis: Long): CareResult<DomainTask> =
        followUpRepository.completeCurrentStudentTask(taskId, completedAtEpochMillis)

    /** Assessment IDs are accepted only after B's repository confirms a completed record for the student. */
    suspend fun completeAssessmentTask(
        taskId: String,
        assessmentId: String,
        completedAtEpochMillis: Long,
    ): CareResult<DomainTask> {
        val assessments = assessmentRepository
            ?: return CareResult.Failure(CareFailure.NotConfigured("assessment repository"))
        val record = when (val result = assessments.getForCurrentStudent(assessmentId)) {
            is CareResult.Failure -> return result
            is CareResult.Success -> result.value
        }
        if (record.state != AssessmentState.COMPLETED) {
            return CareResult.Failure(CareFailure.InvalidInput("Assessment is not completed"))
        }
        val task = when (val result = followUpRepository.currentStudentTasks()) {
            is CareResult.Failure -> return result
            is CareResult.Success -> result.value.firstOrNull { it.taskId == taskId }
                ?: return CareResult.Failure(CareFailure.NotFound("follow-up task", taskId))
        }
        if (task.type != DomainTaskType.ASSESSMENT_RETAKE || task.studentId != record.studentId) {
            return CareResult.Failure(CareFailure.InvalidInput("Assessment does not match follow-up task"))
        }
        return followUpRepository.completeCurrentStudentTask(taskId, completedAtEpochMillis)
    }

    suspend fun requestExit(reason: String): CareResult<com.example.mdd_calender.domain.model.AaEnrollment> =
        followUpRepository.requestExit(reason)

    suspend fun teacherState(): CareResult<TeacherFollowUpUiState> =
        when (val result = followUpRepository.listAssignedEnrollments()) {
            is CareResult.Failure -> result
            is CareResult.Success -> CareResult.Success(
                TeacherFollowUpUiState(
                    enrollments = result.value.map { enrollment ->
                        TeacherEnrollmentSummaryUi(
                            enrollmentId = enrollment.enrollmentId,
                            studentCode = enrollment.studentId,
                            aaStatusLabel = enrollment.status.studentLabel(),
                            completionLabel = "进入详情查看",
                            pendingContactCount = 0,
                            exitReviewPending = enrollment.status == DomainAaStatus.EXIT_REVIEW_PENDING,
                            exitReason = enrollment.exitReview?.reason,
                        )
                    },
                    policyDisclosure = "演示随访周期，未经专业审核；AA 为业务跟踪状态，不是诊断。",
                ),
            )
        }

    suspend fun reviewExit(
        enrollmentId: String,
        approve: Boolean,
        note: String,
    ): CareResult<com.example.mdd_calender.domain.model.AaEnrollment> {
        if (note.isBlank()) return CareResult.Failure(CareFailure.InvalidInput("Exit review note is required"))
        return followUpRepository.reviewExit(
            enrollmentId,
            if (approve) ExitReviewDecision.APPROVED else ExitReviewDecision.REJECTED,
            note.trim(),
        )
    }
}

private fun DomainAaStatus.studentLabel() = when (this) {
    DomainAaStatus.TRACKING -> "跟踪中"
    DomainAaStatus.EXIT_REVIEW_PENDING -> "待退出审核"
    DomainAaStatus.EXITED -> "已退出"
}

private fun DomainTask.toUi() = StudentFollowUpTaskUi(
    taskId = taskId,
    title = when (type) {
        DomainTaskType.CHECK_IN -> "简短心情打卡"
        DomainTaskType.ASSESSMENT_RETAKE -> "完整量表复测"
        DomainTaskType.TEACHER_REVIEW -> "教师联系"
    },
    dueLabel = "到期时间：$dueAtEpochMillis",
    status = when (status) {
        DomainTaskStatus.PENDING -> FollowUpTaskUiStatus.UPCOMING
        DomainTaskStatus.COMPLETED -> FollowUpTaskUiStatus.COMPLETED
        DomainTaskStatus.OVERDUE -> FollowUpTaskUiStatus.OVERDUE
        DomainTaskStatus.CANCELLED -> FollowUpTaskUiStatus.CANCELLED
    },
    actionLabel = when {
        type == DomainTaskType.TEACHER_REVIEW -> null
        status == DomainTaskStatus.COMPLETED || status == DomainTaskStatus.CANCELLED -> null
        else -> "完成任务"
    },
)
