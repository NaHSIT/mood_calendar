package com.example.mdd_calender.feature.teacher

import com.example.mdd_calender.domain.model.AaEnrollment
import com.example.mdd_calender.domain.model.AaStatus
import com.example.mdd_calender.domain.model.ActorContext
import com.example.mdd_calender.domain.model.ActorRole
import com.example.mdd_calender.domain.model.AlertEvent
import com.example.mdd_calender.domain.model.CareFailure
import com.example.mdd_calender.domain.model.CareResult
import com.example.mdd_calender.domain.model.DataDomain
import com.example.mdd_calender.domain.model.DeliveryRecord
import com.example.mdd_calender.domain.model.ExitReview
import com.example.mdd_calender.domain.model.ExitReviewDecision
import com.example.mdd_calender.domain.model.FollowUpTask
import com.example.mdd_calender.domain.model.InterventionCase
import com.example.mdd_calender.domain.model.InterventionStartRequest
import com.example.mdd_calender.domain.model.InterventionStartResult
import com.example.mdd_calender.domain.model.TeacherAlertSummary
import com.example.mdd_calender.domain.port.AlertRepository
import com.example.mdd_calender.domain.port.FollowUpRepository
import com.example.mdd_calender.domain.port.InterventionRepository
import com.example.mdd_calender.domain.port.SchoolAlertDto
import com.example.mdd_calender.domain.port.SchoolPlatformGateway
import com.example.mdd_calender.domain.port.SessionProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TeacherExitReviewTest {
    @Test
    fun pendingReviewsAreListedAndApprovalUsesResponsibleTeacherRepository() = runBlocking {
        val repository = FakeFollowUpRepository()
        val service = TeacherWorkbenchService(
            alerts = unusedAlerts(),
            interventions = unusedInterventions(),
            followUps = repository,
            gateway = unusedGateway(),
            session = teacherSession(),
        )

        val reviews = service.loadPendingExitReviews().success()
        assertEquals(listOf("enrollment-pending"), reviews.map { it.enrollmentId })

        val approved = service.reviewExit("enrollment-pending", approve = true, note = "条件已人工复核").success()
        assertEquals(AaStatus.EXITED, approved.status)
        assertEquals(ExitReviewDecision.APPROVED, repository.lastDecision)
        assertEquals("条件已人工复核", repository.lastNote)
    }

    @Test
    fun reviewNoteIsRequired() = runBlocking {
        val repository = FakeFollowUpRepository()
        val service = TeacherWorkbenchService(unusedAlerts(), unusedInterventions(), repository, unusedGateway(), teacherSession())

        val result = service.reviewExit("enrollment-pending", approve = false, note = "  ")

        assertTrue(result is CareResult.Failure && result.error is CareFailure.InvalidInput)
        assertEquals(null, repository.lastDecision)
    }

    private fun teacherSession() = object : SessionProvider {
        private val actor = ActorContext("teacher-1", ActorRole.TEACHER, DataDomain.DEMO)
        override suspend fun currentActor(): CareResult<ActorContext> = CareResult.Success(actor)
        override fun observeActor(): Flow<CareResult<ActorContext>> = flowOf(CareResult.Success(actor))
    }

    private fun unusedAlerts() = object : AlertRepository {
        override suspend fun saveInternal(event: AlertEvent): CareResult<AlertEvent> = error("unused")
        override suspend fun listTeacherSummaries(): CareResult<List<TeacherAlertSummary>> = error("unused")
        override suspend fun getTeacherSummary(eventId: String): CareResult<TeacherAlertSummary> = error("unused")
        override suspend fun saveDeliveryInternal(delivery: DeliveryRecord): CareResult<DeliveryRecord> = error("unused")
        override suspend fun getDeliveryInternal(idempotencyKey: String): CareResult<DeliveryRecord> = error("unused")
    }

    private fun unusedInterventions() = object : InterventionRepository {
        override suspend fun listAssignedToCurrentTeacher(): CareResult<List<InterventionCase>> = error("unused")
        override suspend fun getAssignedToCurrentTeacher(caseId: String): CareResult<InterventionCase> = error("unused")
        override suspend fun acknowledge(caseId: String, idempotencyKey: String): CareResult<InterventionCase> = error("unused")
        override suspend fun startWithAa(request: InterventionStartRequest): CareResult<InterventionStartResult> = error("unused")
    }

    private fun unusedGateway() = object : SchoolPlatformGateway {
        override suspend fun deliver(alert: SchoolAlertDto, idempotencyKey: String): CareResult<DeliveryRecord> = error("unused")
        override suspend fun queryReceipt(deliveryId: String): CareResult<DeliveryRecord> = error("unused")
        override suspend fun submitDisposition(eventId: String, disposition: String): CareResult<Unit> = error("unused")
    }

    private class FakeFollowUpRepository : FollowUpRepository {
        var lastDecision: ExitReviewDecision? = null
        var lastNote: String? = null
        private val pending = enrollment("enrollment-pending", AaStatus.EXIT_REVIEW_PENDING, withReview = true)
        private val tracking = enrollment("enrollment-tracking", AaStatus.TRACKING, withReview = false)

        override suspend fun listAssignedEnrollments(): CareResult<List<AaEnrollment>> = CareResult.Success(listOf(tracking, pending))

        override suspend fun reviewExit(enrollmentId: String, decision: ExitReviewDecision, note: String): CareResult<AaEnrollment> {
            lastDecision = decision
            lastNote = note
            return CareResult.Success(pending.copy(status = if (decision == ExitReviewDecision.APPROVED) AaStatus.EXITED else AaStatus.TRACKING))
        }

        override suspend fun currentStudentEnrollment(): CareResult<AaEnrollment> = error("unused")
        override suspend fun currentStudentTasks(): CareResult<List<FollowUpTask>> = error("unused")
        override suspend fun completeCurrentStudentTask(taskId: String, completedAtEpochMillis: Long): CareResult<FollowUpTask> = error("unused")
        override suspend fun requestExit(reason: String): CareResult<AaEnrollment> = error("unused")

        companion object {
            private fun enrollment(id: String, status: AaStatus, withReview: Boolean) = AaEnrollment(
                enrollmentId = id,
                studentId = "student-1",
                interventionId = "case-1",
                enrolledAtEpochMillis = 1L,
                status = status,
                followUpPolicyVersion = "demo-v1",
                exitReview = if (withReview) ExitReview(2L, "完成稳定观察", null, null, null, null) else null,
            )
        }
    }

    private fun <T> CareResult<T>.success(): T = when (this) {
        is CareResult.Success -> value
        is CareResult.Failure -> error("Expected success but got $error")
    }
}
