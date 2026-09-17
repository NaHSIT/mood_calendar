package com.example.mdd_calender.feature.followup

fun interface FollowUpClock {
    fun nowMillis(): Long
}

data class FollowUpTaskRule(
    val type: FollowUpTaskType,
    val delayMillis: Long,
    val requiredAssessmentType: String? = null,
)

/** Executable schedule used by the service; policy values are demonstration defaults only. */
data class FollowUpSchedulePolicy(
    val version: String,
    val dueWindowMillis: Long,
    val minimumStableObservationMillis: Long,
    val taskRules: List<FollowUpTaskRule>,
    val demoNotice: String,
) {
    init {
        require(version.isNotBlank())
        require(dueWindowMillis >= 0)
        require(minimumStableObservationMillis > 0)
        require(taskRules.all { it.delayMillis >= 0 })
        require(taskRules.filter { it.type == FollowUpTaskType.FULL_ASSESSMENT }
            .all { !it.requiredAssessmentType.isNullOrBlank() })
    }

    companion object {
        private const val DAY = 24L * 60 * 60 * 1_000

        val DEMO = FollowUpSchedulePolicy(
            version = "demo-followup-v1",
            dueWindowMillis = DAY,
            minimumStableObservationMillis = 14 * DAY,
            taskRules = listOf(
                FollowUpTaskRule(FollowUpTaskType.MOOD_CHECK_IN, 3 * DAY),
                FollowUpTaskRule(FollowUpTaskType.TEACHER_CONTACT, 7 * DAY),
                FollowUpTaskRule(FollowUpTaskType.MOOD_CHECK_IN, 10 * DAY),
                // Scheduling a retest does not alter its two-week questionnaire recall window.
                FollowUpTaskRule(FollowUpTaskType.FULL_ASSESSMENT, 14 * DAY, "PHQ_9_OR_GAD_7"),
            ),
            demoNotice = "演示随访周期，未经专业审核；AA 为业务跟踪状态，不是诊断。",
        )
    }
}

fun FollowUpTask.effectiveStatus(nowMillis: Long, dueWindowMillis: Long): FollowUpTaskStatus {
    if (status in setOf(
            FollowUpTaskStatus.COMPLETED,
            FollowUpTaskStatus.CANCELLED,
            FollowUpTaskStatus.PAUSED,
        )
    ) return status
    return when {
        nowMillis > dueAtMillis + dueWindowMillis -> FollowUpTaskStatus.OVERDUE
        nowMillis >= dueAtMillis -> FollowUpTaskStatus.DUE
        else -> FollowUpTaskStatus.UPCOMING
    }
}

