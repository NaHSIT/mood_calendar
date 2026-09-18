package com.example.mdd_calender.feature.followup

import com.example.mdd_calender.domain.model.AaEnrollment
import com.example.mdd_calender.domain.model.AaStatus
import com.example.mdd_calender.domain.model.CareFailure
import com.example.mdd_calender.domain.model.CareResult
import com.example.mdd_calender.domain.model.ExitReview
import com.example.mdd_calender.domain.model.ExitReviewDecision
import com.example.mdd_calender.domain.model.FollowUpTask
import com.example.mdd_calender.domain.port.FollowUpRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FollowUpRepositoryUiAdapterTest {
    @Test
    fun teacherStateExposesPendingExitReviewAndReason() = runBlocking {
        val repository = FakeDomainFollowUpRepository(
            enrollments = listOf(enrollment(AaStatus.EXIT_REVIEW_PENDING, "近期状态稳定，希望结束随访")),
        )

        val state = (FollowUpRepositoryUiAdapter(repository).teacherState() as CareResult.Success).value
        val item = state.enrollments.single()

        assertTrue(item.exitReviewPending)
        assertEquals("近期状态稳定，希望结束随访", item.exitReason)
        assertEquals("待退出审核", item.aaStatusLabel)
    }

    @Test
    fun normalEnrollmentDoesNotShowReviewActions() = runBlocking {
        val repository = FakeDomainFollowUpRepository(listOf(enrollment(AaStatus.TRACKING)))

        val state = (FollowUpRepositoryUiAdapter(repository).teacherState() as CareResult.Success).value

        assertFalse(state.enrollments.single().exitReviewPending)
    }

    @Test
    fun reviewRequiresNoteAndDelegatesDecision() = runBlocking {
        val repository = FakeDomainFollowUpRepository(listOf(enrollment(AaStatus.EXIT_REVIEW_PENDING, "申请退出")))
        val adapter = FollowUpRepositoryUiAdapter(repository)

        val blank = adapter.reviewExit("aa-1", approve = true, note = "  ")
        assertTrue(blank is CareResult.Failure && blank.error is CareFailure.InvalidInput)
        assertEquals(null, repository.lastReview)

        adapter.reviewExit("aa-1", approve = false, note = " 继续观察 ")
        assertEquals(Triple("aa-1", ExitReviewDecision.REJECTED, "继续观察"), repository.lastReview)
    }

    private fun enrollment(status: AaStatus, reason: String? = null) = AaEnrollment(
        enrollmentId = "aa-1",
        studentId = "STU-001",
        interventionId = "case-1",
        enrolledAtEpochMillis = 1_000L,
        status = status,
        followUpPolicyVersion = "demo-v1",
        exitReview = reason?.let {
            ExitReview(
                requestedAtEpochMillis = 2_000L,
                reason = it,
                reviewedAtEpochMillis = null,
                reviewerId = null,
                decision = null,
                reviewNote = null,
            )
        },
    )
}

private class FakeDomainFollowUpRepository(
    private val enrollments: List<AaEnrollment>,
) : FollowUpRepository {
    var lastReview: Triple<String, ExitReviewDecision, String>? = null

    override suspend fun currentStudentEnrollment(): CareResult<AaEnrollment> =
        CareResult.Success(enrollments.first())

    override suspend fun currentStudentTasks(): CareResult<List<FollowUpTask>> = CareResult.Success(emptyList())

    override suspend fun completeCurrentStudentTask(
        taskId: String,
        completedAtEpochMillis: Long,
    ): CareResult<FollowUpTask> = CareResult.Failure(CareFailure.NotFound("follow-up task", taskId))

    override suspend fun requestExit(reason: String): CareResult<AaEnrollment> =
        CareResult.Success(enrollments.first())

    override suspend fun listAssignedEnrollments(): CareResult<List<AaEnrollment>> = CareResult.Success(enrollments)

    override suspend fun reviewExit(
        enrollmentId: String,
        decision: ExitReviewDecision,
        note: String,
    ): CareResult<AaEnrollment> {
        lastReview = Triple(enrollmentId, decision, note)
        return CareResult.Success(enrollments.first())
    }
}

