package com.example.mdd_calender.feature.followup

enum class FollowUpTaskUiStatus { UPCOMING, DUE, OVERDUE, PAUSED, COMPLETED, CANCELLED }

data class StudentFollowUpTaskUi(
    val taskId: String,
    val title: String,
    val dueLabel: String,
    val status: FollowUpTaskUiStatus,
    val actionLabel: String?,
)

data class StudentFollowUpUiState(
    val isTracking: Boolean,
    val statusLabel: String,
    val policyDisclosure: String,
    val tasks: List<StudentFollowUpTaskUi>,
    val exitRequestPending: Boolean,
)

data class TeacherEnrollmentSummaryUi(
    val enrollmentId: String,
    val studentCode: String,
    val aaStatusLabel: String,
    val completionLabel: String,
    val pendingContactCount: Int,
)

data class TeacherFollowUpUiState(
    val enrollments: List<TeacherEnrollmentSummaryUi>,
    val policyDisclosure: String,
)

