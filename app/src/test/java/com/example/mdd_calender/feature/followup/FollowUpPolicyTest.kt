package com.example.mdd_calender.feature.followup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FollowUpPolicyTest {
    private val policy = FollowUpPolicy(
        version = "demo-v1",
        demoCycleLabel = "演示周期，未经专业审核",
        checkInOffsetsMillis = listOf(200, 100, 100),
        assessmentOffsetsMillis = listOf(300),
        teacherContactOffsetsMillis = listOf(250),
        stableObservationMillis = 1_000,
        minimumCompletedAssessmentsForExit = 2,
    )

    @Test
    fun planningIsDeterministicAndDeduplicatesOffsets() {
        val planner = FollowUpTaskPlanner()

        val first = planner.plan("enrollment-1", 1_000, policy)
        val afterRestart = planner.plan("enrollment-1", 1_000, policy)

        assertEquals(first, afterRestart)
        assertEquals(4, first.size)
        assertEquals(listOf(1_100L, 1_200L, 1_250L, 1_300L), first.map { it.dueAtEpochMillis })
        assertEquals(first.size, first.map { it.stableKey }.distinct().size)
    }

    @Test
    fun newEnrollmentDoesNotReuseOldTaskKeys() {
        val planner = FollowUpTaskPlanner()
        val oldKeys = planner.plan("old", 1_000, policy).map { it.stableKey }.toSet()
        val newKeys = planner.plan("new", 1_000, policy).map { it.stableKey }.toSet()

        assertNotEquals(oldKeys, newKeys)
        assertTrue(oldKeys.intersect(newKeys).isEmpty())
    }

    @Test
    fun exitRequiresObservationSafetyClearanceAndCompletedAssessments() {
        val result = ExitEligibilityPolicy().evaluate(
            nowEpochMillis = 1_500,
            stableSinceEpochMillis = 1_000,
            unresolvedSafetyConcernCount = 1,
            completedAssessmentTimesEpochMillis = listOf(1_200),
            policy = policy,
        )

        assertFalse(result.eligibleForHumanReview)
        assertEquals(
            setOf(
                ExitIneligibilityReason.OBSERVATION_PERIOD_NOT_MET,
                ExitIneligibilityReason.UNRESOLVED_SAFETY_CONCERN,
                ExitIneligibilityReason.INSUFFICIENT_COMPLETED_ASSESSMENTS,
            ),
            result.reasons,
        )
    }

    @Test
    fun eligibleOnlyMeansHumanMayReview_notAutomaticExit() {
        val result = ExitEligibilityPolicy().evaluate(
            nowEpochMillis = 2_100,
            stableSinceEpochMillis = 1_000,
            unresolvedSafetyConcernCount = 0,
            completedAssessmentTimesEpochMillis = listOf(1_200, 2_000, 900),
            policy = policy,
        )

        assertTrue(result.eligibleForHumanReview)
        assertTrue(result.reasons.isEmpty())
    }
}
