package com.example.mdd_calender.feature.followup

/**
 * Feature-owned bridge until A's frozen FollowUpRepository is integrated.
 * Implementations must make [transaction] atomic: either every mutation commits, or none does.
 */
interface FollowUpStore {
    suspend fun <T> transaction(block: suspend FollowUpStore.() -> T): T
    suspend fun findEnrollment(enrollmentId: String): AaEnrollment?
    suspend fun findActiveEnrollment(studentId: String): AaEnrollment?
    suspend fun listActiveEnrollments(): List<AaEnrollment>
    suspend fun saveEnrollment(enrollment: AaEnrollment)
    suspend fun wasInterventionEventHandled(eventId: String): Boolean
    suspend fun markInterventionEventHandled(eventId: String)
    suspend fun findTask(taskId: String): FollowUpTask?
    suspend fun listTasks(enrollmentId: String): List<FollowUpTask>
    suspend fun saveTask(task: FollowUpTask)
    suspend fun hasUnresolvedSafetyConcern(studentId: String): Boolean
    suspend fun stableObservationSinceMillis(studentId: String): Long?
    suspend fun listEnrollments(studentId: String): List<AaEnrollment>
}

/** A/B integration must verify durable assessment storage; knowing an assessment ID is insufficient. */
fun interface AssessmentEvidenceVerifier {
    suspend fun isDurablySaved(receipt: AssessmentCompletionReceipt): Boolean
}
