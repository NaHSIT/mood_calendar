package com.example.mdd_calender.feature.followup

/**
 * F-owned scheduling policy. It deliberately contains no questionnaire scoring rules: increasing
 * follow-up frequency must not change the PHQ-9/GAD-7 two-week recall window.
 */
data class FollowUpPolicy(
    val version: String,
    val demoCycleLabel: String,
    val checkInOffsetsMillis: List<Long>,
    val assessmentOffsetsMillis: List<Long>,
    val teacherContactOffsetsMillis: List<Long>,
    val stableObservationMillis: Long,
    val minimumCompletedAssessmentsForExit: Int,
) {
    init {
        require(version.isNotBlank())
        require(demoCycleLabel.isNotBlank())
        require(stableObservationMillis >= 0)
        require(minimumCompletedAssessmentsForExit >= 0)
        require(checkInOffsetsMillis.all { it >= 0 })
        require(assessmentOffsetsMillis.all { it >= 0 })
        require(teacherContactOffsetsMillis.all { it >= 0 })
    }
}

enum class PlannedTaskKind { CHECK_IN, ASSESSMENT_RETAKE, TEACHER_CONTACT }

data class PlannedFollowUpTask(
    val stableKey: String,
    val kind: PlannedTaskKind,
    val dueAtEpochMillis: Long,
)

class FollowUpTaskPlanner {
    fun plan(
        enrollmentId: String,
        enrolledAtEpochMillis: Long,
        policy: FollowUpPolicy,
    ): List<PlannedFollowUpTask> {
        require(enrollmentId.isNotBlank())

        return buildList {
            addPlans(enrollmentId, enrolledAtEpochMillis, policy, PlannedTaskKind.CHECK_IN, policy.checkInOffsetsMillis)
            addPlans(
                enrollmentId,
                enrolledAtEpochMillis,
                policy,
                PlannedTaskKind.ASSESSMENT_RETAKE,
                policy.assessmentOffsetsMillis,
            )
            addPlans(
                enrollmentId,
                enrolledAtEpochMillis,
                policy,
                PlannedTaskKind.TEACHER_CONTACT,
                policy.teacherContactOffsetsMillis,
            )
        }.sortedWith(compareBy(PlannedFollowUpTask::dueAtEpochMillis, PlannedFollowUpTask::stableKey))
    }

    private fun MutableList<PlannedFollowUpTask>.addPlans(
        enrollmentId: String,
        enrolledAtEpochMillis: Long,
        policy: FollowUpPolicy,
        kind: PlannedTaskKind,
        offsetsMillis: List<Long>,
    ) {
        offsetsMillis.distinct().sorted().forEachIndexed { index, offset ->
            val dueAt = Math.addExact(enrolledAtEpochMillis, offset)
            add(
                PlannedFollowUpTask(
                    stableKey = "$enrollmentId:${policy.version}:${kind.name}:$index:$dueAt",
                    kind = kind,
                    dueAtEpochMillis = dueAt,
                ),
            )
        }
    }
}

enum class ExitIneligibilityReason {
    OBSERVATION_PERIOD_NOT_MET,
    UNRESOLVED_SAFETY_CONCERN,
    INSUFFICIENT_COMPLETED_ASSESSMENTS,
}

data class ExitEligibility(
    val eligibleForHumanReview: Boolean,
    val reasons: Set<ExitIneligibilityReason>,
)

class ExitEligibilityPolicy {
    fun evaluate(
        nowEpochMillis: Long,
        stableSinceEpochMillis: Long,
        unresolvedSafetyConcernCount: Int,
        completedAssessmentTimesEpochMillis: List<Long>,
        policy: FollowUpPolicy,
    ): ExitEligibility {
        require(nowEpochMillis >= stableSinceEpochMillis)
        require(unresolvedSafetyConcernCount >= 0)

        val reasons = buildSet {
            if (nowEpochMillis - stableSinceEpochMillis < policy.stableObservationMillis) {
                add(ExitIneligibilityReason.OBSERVATION_PERIOD_NOT_MET)
            }
            if (unresolvedSafetyConcernCount > 0) {
                add(ExitIneligibilityReason.UNRESOLVED_SAFETY_CONCERN)
            }
            val completedDuringObservation = completedAssessmentTimesEpochMillis.count {
                it in stableSinceEpochMillis..nowEpochMillis
            }
            if (completedDuringObservation < policy.minimumCompletedAssessmentsForExit) {
                add(ExitIneligibilityReason.INSUFFICIENT_COMPLETED_ASSESSMENTS)
            }
        }
        return ExitEligibility(eligibleForHumanReview = reasons.isEmpty(), reasons = reasons)
    }
}

