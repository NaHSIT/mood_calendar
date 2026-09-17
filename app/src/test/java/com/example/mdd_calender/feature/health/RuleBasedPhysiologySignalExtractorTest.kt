package com.example.mdd_calender.feature.health

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
