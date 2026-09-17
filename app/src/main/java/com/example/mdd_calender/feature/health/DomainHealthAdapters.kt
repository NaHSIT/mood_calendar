package com.example.mdd_calender.feature.health

import com.example.mdd_calender.domain.model.CareFailure
import com.example.mdd_calender.domain.model.CareResult
import com.example.mdd_calender.domain.model.HealthCapability
import com.example.mdd_calender.domain.model.HealthPullRequest
import com.example.mdd_calender.domain.model.HealthSampleType
import com.example.mdd_calender.domain.model.HealthScope
import com.example.mdd_calender.domain.model.RawHealthSample
import com.example.mdd_calender.domain.model.SampleQuality
import com.example.mdd_calender.domain.port.HealthDataProvider
import java.util.UUID

/** Bridges the feature provider to A's frozen HealthDataProvider contract. */
class DomainHealthDataProviderAdapter(
    private val delegate: HealthProviderClient,
    private val nowEpochMillis: () -> Long = System::currentTimeMillis,
) : HealthDataProvider {
    override suspend fun capability(): HealthCapability {
        val capabilities = delegate.capabilities()
        return when {
            capabilities.any { it.availability == HealthProviderAvailability.AVAILABLE } -> HealthCapability.AVAILABLE
            capabilities.any { it.availability == HealthProviderAvailability.NOT_CONNECTED } -> HealthCapability.NOT_CONFIGURED
            else -> HealthCapability.NOT_SUPPORTED
        }
    }

    override suspend fun requestAuthorization(scopes: Set<HealthScope>): CareResult<Set<HealthScope>> {
        val granted = scopes.filter { delegate.requestPermission(it.toFeatureMetric()) }.toSet()
        return CareResult.Success(granted)
    }

    override suspend fun revokeAuthorization(scopes: Set<HealthScope>): CareResult<Unit> {
        scopes.forEach { delegate.revokePermission(it.toFeatureMetric()) }
        return CareResult.Success(Unit)
    }

    override suspend fun cancelSync(studentId: String, consentRevision: Long): CareResult<Unit> {
        // The local provider tracks task IDs; cancellation at the coordinator boundary is authoritative.
        return CareResult.Success(Unit)
    }

    override suspend fun pull(request: HealthPullRequest): CareResult<List<RawHealthSample>> {
        val output = mutableListOf<RawHealthSample>()
        for (scope in request.scopes) {
            val metric = scope.toFeatureMetric()
            when (val result = delegate.fetch(UUID.randomUUID().toString(), request.studentId, metric, request.fromEpochMillis)) {
                ProviderFetchResult.NotConnected -> return CareResult.Failure(CareFailure.NotConfigured("health-${scope.name}"))
                ProviderFetchResult.Cancelled -> return CareResult.Failure(CareFailure.TemporarilyUnavailable("health sync cancelled"))
                is ProviderFetchResult.TemporaryFailure -> return CareResult.Failure(CareFailure.TemporarilyUnavailable(result.reason))
                is ProviderFetchResult.Samples -> result.values
                    .filter { it.observedAtEpochMillis <= request.toEpochMillis }
                    .forEach { output += it.toDomainSample(request.consentRevision) }
            }
        }
        return CareResult.Success(output)
    }

    private fun HealthScope.toFeatureMetric() = when (this) {
        HealthScope.HEART_RATE -> HealthMetric.HEART_RATE
        HealthScope.SLEEP -> HealthMetric.SLEEP
    }

    private fun HealthSample.toDomainSample(consentRevision: Long) = RawHealthSample(
        sampleId = "${ownerId}-${metric}-${observedAtEpochMillis}-${UUID.randomUUID()}",
        ownerId = ownerId,
        source = if (domain == HealthDataDomain.DEMO) "demo-health-provider" else "health-provider",
        type = when (metric) {
            HealthMetric.HEART_RATE -> HealthSampleType.HEART_RATE_BPM
            HealthMetric.SLEEP -> HealthSampleType.SLEEP_DURATION_MINUTES
        },
        measuredAtEpochMillis = observedAtEpochMillis,
        value = if (metric == HealthMetric.SLEEP) value * 60.0 else value,
        unit = if (metric == HealthMetric.SLEEP) "minutes" else unit,
        quality = when {
            quality < 0.4 -> SampleQuality.POOR
            quality < 0.75 -> SampleQuality.FAIR
            else -> SampleQuality.GOOD
        },
        simulated = domain == HealthDataDomain.DEMO,
        consentRevision = consentRevision,
    )
}
