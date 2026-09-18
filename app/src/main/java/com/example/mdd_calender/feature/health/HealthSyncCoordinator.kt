package com.example.mdd_calender.feature.health

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class HealthSyncCoordinator(
    private val consentGateway: HealthConsentGateway,
    private val healthGateway: StudentHealthGateway,
    private val provider: HealthProviderClient,
    private val extractor: FeatureSignalExtractor,
    private val consentChangeSink: HealthConsentChangeSink,
    private val nowEpochMillis: () -> Long = System::currentTimeMillis,
    private val taskIdFactory: () -> String = { UUID.randomUUID().toString() },
) {
    private val activeTasks = ConcurrentHashMap<Pair<String, HealthMetric>, String>()

    suspend fun sync(studentId: String, metric: HealthMetric, sinceEpochMillis: Long): HealthSyncResult {
        val initialConsent = consentGateway.current(studentId)
        if (!initialConsent.allows(metric)) return HealthSyncResult.NotAuthorized

        val capability = provider.capabilities().firstOrNull { it.metric == metric }
            ?: return HealthSyncResult.NotConnected
        if (capability.availability != HealthProviderAvailability.AVAILABLE) return HealthSyncResult.NotConnected
        if (capability.domain != initialConsent.domain) return HealthSyncResult.DataDomainMismatch

        val taskId = taskIdFactory()
        val key = studentId to metric
        activeTasks.put(key, taskId)?.let(provider::cancel)
        val fetched = try {
            provider.fetch(taskId, studentId, metric, sinceEpochMillis)
        } catch (error: Exception) {
            return HealthSyncResult.Failed(error.message ?: "provider failure")
        } finally {
            activeTasks.remove(key, taskId)
        }

        val latestConsent = consentGateway.current(studentId)
        if (latestConsent.revision != initialConsent.revision || !latestConsent.allows(metric)) {
            provider.cancel(taskId)
            return HealthSyncResult.DiscardedAfterConsentChange
        }
        return when (fetched) {
            ProviderFetchResult.NotConnected -> HealthSyncResult.NotConnected
            ProviderFetchResult.Cancelled -> HealthSyncResult.Cancelled
            is ProviderFetchResult.TemporaryFailure -> HealthSyncResult.Failed(fetched.reason)
            is ProviderFetchResult.Samples -> {
                if (fetched.values.any { it.domain != initialConsent.domain || it.ownerId != studentId || it.metric != metric }) {
                    return HealthSyncResult.DataDomainMismatch
                }
                val report = extractor.extract(studentId, fetched.values, initialConsent.revision, nowEpochMillis())
                val persisted = healthGateway.persistIfConsentCurrent(
                    studentId = studentId,
                    metric = metric,
                    expectedConsentRevision = initialConsent.revision,
                    samples = fetched.values,
                    signals = report.signals,
                )
                if (persisted) HealthSyncResult.Completed(report) else HealthSyncResult.DiscardedAfterConsentChange
            }
        }
    }

    suspend fun setConsent(studentId: String, metric: HealthMetric, enabled: Boolean): HealthConsentSnapshot {
        val previous = consentGateway.current(studentId)
        if (previous.allows(metric) == enabled) return previous
        if (enabled && !provider.requestPermission(metric)) return previous
        if (!enabled) {
            activeTasks.remove(studentId to metric)?.let(provider::cancel)
            provider.revokePermission(metric)
        }
        val current = consentGateway.update(studentId, metric, enabled)
        consentChangeSink.publish(
            HealthConsentChanged(
                studentId = studentId,
                previousRevision = previous.revision,
                currentRevision = current.revision,
                disabledMetrics = if (enabled) emptySet() else setOf(metric),
            ),
        )
        return current
    }

    suspend fun deleteRawData(studentId: String, metrics: Set<HealthMetric>) {
        healthGateway.deleteRawSamples(studentId, metrics)
    }

    suspend fun rawDataFor(requestingStudentId: String, ownerStudentId: String): RawHealthDataView {
        if (requestingStudentId != ownerStudentId) return RawHealthDataView.AccessDenied
        if (provider.capabilities().all { it.availability != HealthProviderAvailability.AVAILABLE }) {
            return RawHealthDataView.NotConnected
        }
        val samples = healthGateway.rawSamplesForOwner(ownerStudentId)
        return RawHealthDataView.Data(samples, isDemo = samples.all { it.domain == HealthDataDomain.DEMO })
    }
}
