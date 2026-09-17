package com.example.mdd_calender.domain.model

enum class InterventionStatus { PENDING_CONFIRMATION, CONFIRMED, ACTIVE, PENDING_CLOSURE, CLOSED }

data class InterventionCase(
    val caseId: String,
    val studentId: String,
    val assignedTeacherId: String,
    val alertId: String,
    val status: InterventionStatus,
    val startedAtEpochMillis: Long?,
    val minimalActionNote: String?,
)

enum class AaStatus { TRACKING, EXIT_REVIEW_PENDING, EXITED }
enum class ExitReviewDecision { APPROVED, REJECTED }

data class ExitReview(
    val requestedAtEpochMillis: Long,
    val reason: String,
    val reviewedAtEpochMillis: Long?,
    val reviewerId: String?,
    val decision: ExitReviewDecision?,
    val reviewNote: String?,
)

data class AaEnrollment(
    val enrollmentId: String,
    val studentId: String,
    val interventionId: String,
    val enrolledAtEpochMillis: Long,
    val status: AaStatus,
    val followUpPolicyVersion: String,
    val exitReview: ExitReview?,
)

enum class FollowUpTaskType { CHECK_IN, ASSESSMENT_RETAKE, TEACHER_REVIEW }
enum class FollowUpTaskStatus { PENDING, COMPLETED, OVERDUE, CANCELLED }

data class FollowUpTask(
    val taskId: String,
    val studentId: String,
    val type: FollowUpTaskType,
    val dueAtEpochMillis: Long,
    val completedAtEpochMillis: Long?,
    val relatedAssessmentId: String?,
    val status: FollowUpTaskStatus,
)

data class AuditEvent(
    val auditId: String,
    val actorId: String,
    val action: String,
    val objectCode: String,
    val result: String,
    val occurredAtEpochMillis: Long,
)

data class InterventionStartRequest(
    val caseId: String,
    val followUpPolicyVersion: String,
    val idempotencyKey: String,
)

data class InterventionStartResult(
    val intervention: InterventionCase,
    val enrollment: AaEnrollment,
    val enrollmentCreated: Boolean,
)
