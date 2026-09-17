package com.example.mdd_calender.feature.health

enum class HealthMetric { HEART_RATE, SLEEP }

enum class HealthDataDomain { DEMO, REAL }

enum class HealthProviderAvailability { AVAILABLE, NOT_CONNECTED, UNSUPPORTED }

data class HealthProviderCapability(
    val metric: HealthMetric,
    val availability: HealthProviderAvailability,
    val domain: HealthDataDomain,
    val description: String,
)

data class HealthConsentSnapshot(
    val studentId: String,
    val enabledMetrics: Set<HealthMetric> = emptySet(),
    val revision: Long = 0,
    val changedAtEpochMillis: Long,
    val domain: HealthDataDomain,
) {
    fun allows(metric: HealthMetric): Boolean = metric in enabledMetrics
}

data class HealthSample(
    val ownerId: String,
    val metric: HealthMetric,
    val observedAtEpochMillis: Long,
    val value: Double,
    val unit: String,
    val quality: Double,
    val domain: HealthDataDomain,
) {
    init {
        require(quality in 0.0..1.0) { "quality must be between 0 and 1" }
    }
}

enum class PhysiologyPattern { ELEVATED_RESTING_HEART_RATE, SHORT_SLEEP_PATTERN }

data class CoarsePhysiologySignal(
    val studentId: String,
    val pattern: PhysiologyPattern,
    val windowStartEpochMillis: Long,
    val windowEndEpochMillis: Long,
    val quality: Double,
    val validUntilEpochMillis: Long,
    val ruleVersion: String,
    val consentRevision: Long,
    val isSimulated: Boolean,
)

enum class ExtractionStatus { SIGNALS_FOUND, NO_PATTERN, NO_DATA, LOW_QUALITY, STALE_DATA }

data class SignalExtractionReport(
    val status: ExtractionStatus,
    val signals: List<CoarsePhysiologySignal> = emptyList(),
    val explanation: String,
)

sealed interface ProviderFetchResult {
    data class Samples(val values: List<HealthSample>) : ProviderFetchResult
    data object NotConnected : ProviderFetchResult
    data object Cancelled : ProviderFetchResult
    data class TemporaryFailure(val reason: String) : ProviderFetchResult
}

sealed interface HealthSyncResult {
    data object NotAuthorized : HealthSyncResult
    data object NotConnected : HealthSyncResult
    data object Cancelled : HealthSyncResult
    data object DiscardedAfterConsentChange : HealthSyncResult
    data object DataDomainMismatch : HealthSyncResult
    data class Completed(val report: SignalExtractionReport) : HealthSyncResult
    data class Failed(val reason: String) : HealthSyncResult
}

data class HealthConsentChanged(
    val studentId: String,
    val previousRevision: Long,
    val currentRevision: Long,
    val disabledMetrics: Set<HealthMetric>,
)

sealed interface RawHealthDataView {
    data object AccessDenied : RawHealthDataView
    data object NotConnected : RawHealthDataView
    data class Data(val samples: List<HealthSample>, val isDemo: Boolean) : RawHealthDataView
}
