package com.example.mdd_calender.feature.health

import com.example.mdd_calender.domain.model.CareResult
import com.example.mdd_calender.domain.model.ConsentState
import com.example.mdd_calender.domain.model.HealthConsent
import com.example.mdd_calender.domain.model.HealthSampleType
import com.example.mdd_calender.domain.model.RawHealthSample
import com.example.mdd_calender.domain.model.SampleQuality
import com.example.mdd_calender.domain.model.SignalType
import com.example.mdd_calender.domain.port.PhysiologySignalExtractor
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleBasedPhysiologySignalExtractorTest {
    private val now = 2_000_000_000_000L
    private val extractor = RuleBasedPhysiologySignalExtractor()

    @Test
    fun elevatedHeartRateProducesCoarseSimulatedSignal() {
        val report = extractor.extract(
            studentId = "demo-student",
            samples = List(3) { sample(HealthMetric.HEART_RATE, 106.0 + it, now - it * 1_000L) },
            consentRevision = 7,
            nowEpochMillis = now,
        )

        assertEquals(ExtractionStatus.SIGNALS_FOUND, report.status)
        assertEquals(PhysiologyPattern.ELEVATED_RESTING_HEART_RATE, report.signals.single().pattern)
        assertEquals(7, report.signals.single().consentRevision)
        assertTrue(report.signals.single().isSimulated)
    }

    @Test
    fun lowQualityAndStaleDataDoNotProduceSignals() {
        val lowQuality = extractor.extract(
            "demo-student",
            listOf(sample(HealthMetric.HEART_RATE, 120.0, now, quality = 0.2)),
            1,
            now,
        )
        val stale = extractor.extract(
            "demo-student",
            listOf(sample(HealthMetric.SLEEP, 4.0, now - 4 * DAY)),
            1,
            now,
        )

        assertEquals(ExtractionStatus.LOW_QUALITY, lowQuality.status)
        assertTrue(lowQuality.signals.isEmpty())
        assertEquals(ExtractionStatus.STALE_DATA, stale.status)
        assertTrue(stale.signals.isEmpty())
    }

    @Test
    fun noDataIsDifferentFromNormalPattern() {
        val missing = extractor.extract("demo-student", emptyList(), 1, now)
        val normal = extractor.extract(
            "demo-student",
            List(3) { sample(HealthMetric.SLEEP, 7.5, now - it * 1_000L) },
            1,
            now,
        )

        assertEquals(ExtractionStatus.NO_DATA, missing.status)
        assertEquals(ExtractionStatus.NO_PATTERN, normal.status)
    }

    @Test
    fun frozenDomainExtractorContractReturnsAuxiliarySignal() = runBlocking {
        val domainExtractor: PhysiologySignalExtractor = extractor
        val samples = List(3) { index ->
            RawHealthSample(
                sampleId = "sample-$index",
                ownerId = "demo-student",
                source = "demo-health-provider",
                type = HealthSampleType.HEART_RATE_BPM,
                measuredAtEpochMillis = now - index * 1_000L,
                value = 106.0 + index,
                unit = "bpm",
                quality = SampleQuality.GOOD,
                simulated = true,
                consentRevision = 7,
            )
        }
        val consent = HealthConsent(
            studentId = "demo-student",
            scopes = setOf(com.example.mdd_calender.domain.model.HealthScope.HEART_RATE),
            state = ConsentState.GRANTED,
            revision = 7,
            changedAtEpochMillis = now,
        )

        val result = domainExtractor.extract(samples, consent)

        assertTrue(result is CareResult.Success)
        val signals = (result as CareResult.Success).value
        assertEquals(1, signals.size)
        assertEquals(SignalType.ELEVATED_HEART_RATE, signals.single().type)
        assertEquals(7, signals.single().consentRevision)
        assertTrue(signals.single().simulated)
    }

    private fun sample(
        metric: HealthMetric,
        value: Double,
        time: Long,
        quality: Double = 0.9,
    ) = HealthSample("demo-student", metric, time, value, if (metric == HealthMetric.HEART_RATE) "bpm" else "hours", quality, HealthDataDomain.DEMO)

    private companion object {
        const val DAY = 24L * 60 * 60 * 1000
    }
}
