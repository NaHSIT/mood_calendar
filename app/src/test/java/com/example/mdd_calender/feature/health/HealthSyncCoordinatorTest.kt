package com.example.mdd_calender.feature.health

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HealthSyncCoordinatorTest {
    private val now = 2_000_000_000_000L

    @Test
    fun unauthorizedMetricNeverCallsProvider() = runBlocking {
        val consent = FakeConsentGateway(snapshot(enabled = setOf(HealthMetric.SLEEP)))
        val provider = FakeProvider()
        val store = FakeHealthGateway()
        val coordinator = coordinator(consent, provider, store)

        val result = coordinator.sync("student", HealthMetric.HEART_RATE, 0)

        assertEquals(HealthSyncResult.NotAuthorized, result)
        assertEquals(0, provider.fetchCount)
        assertTrue(store.raw.isEmpty())
    }

    @Test
    fun lateCallbackAfterRevocationIsDiscarded() = runBlocking {
        val consent = FakeConsentGateway(snapshot(enabled = setOf(HealthMetric.HEART_RATE)))
        val provider = FakeProvider().apply {
            beforeReturn = { consent.update("student", HealthMetric.HEART_RATE, false) }
        }
        val store = FakeHealthGateway()
        val coordinator = coordinator(consent, provider, store)

        val result = coordinator.sync("student", HealthMetric.HEART_RATE, 0)

        assertEquals(HealthSyncResult.DiscardedAfterConsentChange, result)
        assertTrue(store.raw.isEmpty())
        assertTrue(provider.cancelled.isNotEmpty())
    }

    @Test
    fun reauthorizationCannotReviveOldTask() = runBlocking {
        val consent = FakeConsentGateway(snapshot(enabled = setOf(HealthMetric.HEART_RATE)))
        val provider = FakeProvider().apply {
            beforeReturn = {
                consent.update("student", HealthMetric.HEART_RATE, false)
                consent.update("student", HealthMetric.HEART_RATE, true)
            }
        }
        val store = FakeHealthGateway()

        val result = coordinator(consent, provider, store).sync("student", HealthMetric.HEART_RATE, 0)

        assertEquals(HealthSyncResult.DiscardedAfterConsentChange, result)
        assertTrue(consent.current("student").allows(HealthMetric.HEART_RATE))
        assertTrue(store.raw.isEmpty())
    }

    @Test
    fun repeatedAuthorizationIsIdempotentAndClosedStateSurvivesNewCoordinator() = runBlocking {
        val consent = FakeConsentGateway(snapshot(enabled = emptySet()))
        val provider = FakeProvider()
        val sink = FakeEventSink()
        val first = coordinator(consent, provider, FakeHealthGateway(), sink)

        first.setConsent("student", HealthMetric.SLEEP, false)
        val second = coordinator(consent, provider, FakeHealthGateway(), sink)
        val result = second.sync("student", HealthMetric.SLEEP, 0)

        assertEquals(0, consent.updateCount)
        assertTrue(sink.events.isEmpty())
        assertEquals(HealthSyncResult.NotAuthorized, result)
        assertEquals(0, provider.fetchCount)
    }

    @Test
    fun realAndDemoDomainsCannotMix() = runBlocking {
        val consent = FakeConsentGateway(snapshot(enabled = setOf(HealthMetric.HEART_RATE), domain = HealthDataDomain.REAL))
        val provider = FakeProvider(domain = HealthDataDomain.DEMO)

        val result = coordinator(consent, provider, FakeHealthGateway()).sync("student", HealthMetric.HEART_RATE, 0)

        assertEquals(HealthSyncResult.DataDomainMismatch, result)
        assertEquals(0, provider.fetchCount)
    }

    @Test
    fun atomicPersistenceRejectsConsentChangeAfterCallbackCheck() = runBlocking {
        val consent = FakeConsentGateway(snapshot(enabled = setOf(HealthMetric.HEART_RATE)))
        val store = FakeHealthGateway().apply { allowPersist = false }

        val result = coordinator(consent, FakeProvider(), store).sync("student", HealthMetric.HEART_RATE, 0)

        assertEquals(HealthSyncResult.DiscardedAfterConsentChange, result)
        assertTrue(store.raw.isEmpty())
    }

    @Test
    fun rawDataDeniesDifferentStudentInsteadOfReturningEmpty() = runBlocking {
        val coordinator = coordinator(
            FakeConsentGateway(snapshot(enabled = emptySet())),
            FakeProvider(),
            FakeHealthGateway(),
        )

        val result = coordinator.rawDataFor("teacher-or-other-student", "student")

        assertEquals(RawHealthDataView.AccessDenied, result)
    }

    private fun coordinator(
        consent: FakeConsentGateway,
        provider: FakeProvider,
        store: FakeHealthGateway,
        sink: FakeEventSink = FakeEventSink(),
    ) = HealthSyncCoordinator(
        consentGateway = consent,
        healthGateway = store,
        provider = provider,
        extractor = RuleBasedPhysiologySignalExtractor(),
        consentChangeSink = sink,
        nowEpochMillis = { now },
        taskIdFactory = { "task-${provider.fetchCount}" },
    )

    private fun snapshot(
        enabled: Set<HealthMetric>,
        domain: HealthDataDomain = HealthDataDomain.DEMO,
    ) = HealthConsentSnapshot("student", enabled, 4, now, domain)

    private class FakeConsentGateway(private var value: HealthConsentSnapshot) : HealthConsentGateway {
        var updateCount = 0
        override suspend fun current(studentId: String) = value
        override suspend fun update(studentId: String, metric: HealthMetric, enabled: Boolean): HealthConsentSnapshot {
            updateCount++
            val metrics = value.enabledMetrics.toMutableSet().apply {
                if (enabled) add(metric) else remove(metric)
            }
            value = value.copy(enabledMetrics = metrics, revision = value.revision + 1)
            return value
        }
    }

    private class FakeProvider(private val domain: HealthDataDomain = HealthDataDomain.DEMO) : HealthProviderClient {
        var fetchCount = 0
        var beforeReturn: suspend () -> Unit = {}
        val cancelled = mutableListOf<String>()
        override fun capabilities() = HealthMetric.entries.map {
            HealthProviderCapability(it, HealthProviderAvailability.AVAILABLE, domain, "test")
        }
        override suspend fun requestPermission(metric: HealthMetric) = true
        override suspend fun revokePermission(metric: HealthMetric) = Unit
        override fun cancel(taskId: String) { cancelled += taskId }
        override suspend fun fetch(taskId: String, studentId: String, metric: HealthMetric, sinceEpochMillis: Long): ProviderFetchResult {
            fetchCount++
            beforeReturn()
            return ProviderFetchResult.Samples(
                listOf(HealthSample(studentId, metric, 2_000_000_000_000L, 105.0, "bpm", 0.9, domain)),
            )
        }
    }

    private class FakeHealthGateway : StudentHealthGateway {
        val raw = mutableListOf<HealthSample>()
        val signals = mutableListOf<CoarsePhysiologySignal>()
        var allowPersist = true
        override suspend fun persistIfConsentCurrent(
            studentId: String,
            metric: HealthMetric,
            expectedConsentRevision: Long,
            samples: List<HealthSample>,
            signals: List<CoarsePhysiologySignal>,
        ): Boolean {
            if (!allowPersist) return false
            raw += samples
            this.signals += signals
            return true
        }
        override suspend fun deleteRawSamples(studentId: String, metrics: Set<HealthMetric>) {
            raw.removeAll { it.ownerId == studentId && it.metric in metrics }
        }
        override suspend fun rawSamplesForOwner(studentId: String) = raw.filter { it.ownerId == studentId }
    }

    private class FakeEventSink : HealthConsentChangeSink {
        val events = mutableListOf<HealthConsentChanged>()
        override suspend fun publish(event: HealthConsentChanged) { events += event }
    }
}
