package com.example.mdd_calender.domain.policy

/**
 * Conservative storage-boundary rules for AA exit. Feature policy may be stricter.
 * The demo default is configurable and must not be presented as clinical guidance.
 */
data class AaExitPolicy(
    val minimumObservationMillis: Long = DEFAULT_MINIMUM_OBSERVATION_MILLIS,
    val requireAllFollowUpTasksResolved: Boolean = true,
) {
    init {
        require(minimumObservationMillis >= 0) { "minimumObservationMillis must be non-negative" }
    }

    companion object {
        const val DEFAULT_MINIMUM_OBSERVATION_MILLIS: Long = 14L * 24L * 60L * 60L * 1_000L
    }
}
