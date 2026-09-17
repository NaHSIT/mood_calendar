package com.example.mdd_calender.feature.followup

/** AA is a care-tracking state, not a diagnosis. */
enum class AaStatus { ACTIVE, PAUSED, EXIT_REVIEW, EXITED }

enum class FollowUpTaskType { MOOD_CHECK_IN, FULL_ASSESSMENT, TEACHER_CONTACT }

enum class FollowUpTaskStatus { UPCOMING, DUE, OVERDUE, PAUSED, COMPLETED, CANCELLED }

enum class FollowUpRole { STUDENT, TEACHER, SYSTEM }

data class TrustedFollowUpActor(
    val actorId: String,
    val role: FollowUpRole,
    val studentId: String? = null,
    val demoData: Boolean = true,
)

data class InterventionStartedSignal(
    val eventId: String,
    val caseId: String,
    val alertId: String,
    val studentId: String,
    val responsibleTeacherId: String,
    val startedAtMillis: Long,
)

data class AaEnrollment(
    val enrollmentId: String,
    val studentId: String,
    val interventionCaseIds: Set<String>,
    val alertIds: Set<String>,
    val responsibleTeacherId: String,
    val enrolledAtMillis: Long,
    val status: AaStatus,
    val policyVersion: String,
    val cycle: Int,
    val exitRequest: ExitRequest? = null,
)

data class FollowUpTask(
    val taskId: String,
    val enrollmentId: String,
    val studentId: String,
    val type: FollowUpTaskType,
    val dueAtMillis: Long,
    val status: FollowUpTaskStatus = FollowUpTaskStatus.UPCOMING,
    val completedAtMillis: Long? = null,
    val assessmentId: String? = null,
    val requiredAssessmentType: String? = null,
)

data class ExitRequest(
    val requestedBy: String,
    val requestedAtMillis: Long,
    val stableSinceMillis: Long,
    val rationaleCode: String,
    val reviewedBy: String? = null,
    val reviewedAtMillis: Long? = null,
    val rejectionReasonCode: String? = null,
)

data class AssessmentCompletionReceipt(
    val followUpTaskId: String,
    val assessmentId: String,
    val studentId: String,
    val assessmentType: String,
    val savedAtMillis: Long,
)

data class StudentTaskItem(
    val taskId: String,
    val type: FollowUpTaskType,
    val dueAtMillis: Long,
    val status: FollowUpTaskStatus,
)

data class StudentFollowUpView(
    val status: AaStatus,
    val policyNotice: String,
    val tasks: List<StudentTaskItem>,
)

/** Deliberately contains no assessment answers, scores, diary or raw health data. */
data class TeacherFollowUpSummary(
    val enrollmentId: String,
    val studentId: String,
    val status: AaStatus,
    val completedTasks: Int,
    val totalTasks: Int,
    val needsContact: Boolean,
    val nextDueAtMillis: Long?,
)

sealed interface FollowUpResult<out T> {
    data class Success<T>(val value: T) : FollowUpResult<T>
    data class Rejected(val reason: String) : FollowUpResult<Nothing>
}

