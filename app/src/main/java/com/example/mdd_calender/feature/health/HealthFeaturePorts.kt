package com.example.mdd_calender.feature.health

/** Feature-side boundary. Integration supplies an adapter to A's frozen repositories. */
interface HealthConsentGateway {
    suspend fun current(studentId: String): HealthConsentSnapshot
    suspend fun update(studentId: String, metric: HealthMetric, enabled: Boolean): HealthConsentSnapshot
}

interface StudentHealthGateway {
    /** Must check consent revision and persist atomically; false means the callback became stale. */
    suspend fun persistIfConsentCurrent(
        studentId: String,
        metric: HealthMetric,
        expectedConsentRevision: Long,
        samples: List<HealthSample>,
        signals: List<CoarsePhysiologySignal>,
    ): Boolean
    suspend fun deleteRawSamples(studentId: String, metrics: Set<HealthMetric>)
    suspend fun rawSamplesForOwner(studentId: String): List<HealthSample>
}

interface HealthConsentChangeSink {
    suspend fun publish(event: HealthConsentChanged)
}

interface HealthProviderClient {
    fun capabilities(): List<HealthProviderCapability>
    suspend fun requestPermission(metric: HealthMetric): Boolean
    suspend fun revokePermission(metric: HealthMetric)
    suspend fun fetch(
        taskId: String,
        studentId: String,
        metric: HealthMetric,
        sinceEpochMillis: Long,
    ): ProviderFetchResult
    fun cancel(taskId: String)
}

fun interface FeatureSignalExtractor {
    fun extract(
        studentId: String,
        samples: List<HealthSample>,
        consentRevision: Long,
        nowEpochMillis: Long,
    ): SignalExtractionReport
}
