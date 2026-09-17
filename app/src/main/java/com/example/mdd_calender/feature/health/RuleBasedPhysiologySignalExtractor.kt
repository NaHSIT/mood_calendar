package com.example.mdd_calender.feature.health

data class DemoSignalRules(
    val ruleVersion: String = "demo-physiology-v1",
    val minimumQuality: Double = 0.6,
    val maximumAgeMillis: Long = 72L * 60 * 60 * 1000,
    val signalValidityMillis: Long = 24L * 60 * 60 * 1000,
    val elevatedHeartRateBpm: Double = 100.0,
    val shortSleepHours: Double = 6.0,
    val minimumHeartRateSamples: Int = 3,
    val minimumSleepSamples: Int = 2,
)

/** Demo-only coarse rules. These thresholds are not clinical claims or a risk diagnosis. */
class RuleBasedPhysiologySignalExtractor(
    private val rules: DemoSignalRules = DemoSignalRules(),
) : PhysiologySignalExtractor {
    override fun extract(
        studentId: String,
        samples: List<HealthSample>,
        consentRevision: Long,
        nowEpochMillis: Long,
    ): SignalExtractionReport {
        if (samples.isEmpty()) return SignalExtractionReport(
            ExtractionStatus.NO_DATA,
            explanation = "没有可用的授权数据",
        )
        require(samples.all { it.ownerId == studentId }) { "samples must belong to the same student" }

        val recent = samples.filter { nowEpochMillis - it.observedAtEpochMillis in 0..rules.maximumAgeMillis }
        if (recent.isEmpty()) return SignalExtractionReport(
            ExtractionStatus.STALE_DATA,
            explanation = "数据已超过演示规则的时效窗口",
        )
        val qualified = recent.filter { it.quality >= rules.minimumQuality }
        if (qualified.isEmpty()) return SignalExtractionReport(
            ExtractionStatus.LOW_QUALITY,
            explanation = "数据质量不足，未生成辅助信号",
        )

        val signals = buildList {
            val heartRate = qualified.filter { it.metric == HealthMetric.HEART_RATE }
            if (heartRate.size >= rules.minimumHeartRateSamples && heartRate.map { it.value }.average() >= rules.elevatedHeartRateBpm) {
                add(signal(studentId, PhysiologyPattern.ELEVATED_RESTING_HEART_RATE, heartRate, consentRevision, nowEpochMillis))
            }
            val sleep = qualified.filter { it.metric == HealthMetric.SLEEP }
            if (sleep.size >= rules.minimumSleepSamples && sleep.map { it.value }.average() < rules.shortSleepHours) {
                add(signal(studentId, PhysiologyPattern.SHORT_SLEEP_PATTERN, sleep, consentRevision, nowEpochMillis))
            }
        }
        return SignalExtractionReport(
            status = if (signals.isEmpty()) ExtractionStatus.NO_PATTERN else ExtractionStatus.SIGNALS_FOUND,
            signals = signals,
            explanation = if (signals.isEmpty()) "未匹配演示辅助规则" else "匹配到粗粒度演示信号，仅供人工复核参考",
        )
    }

    private fun signal(
        studentId: String,
        pattern: PhysiologyPattern,
        samples: List<HealthSample>,
        consentRevision: Long,
        nowEpochMillis: Long,
    ) = CoarsePhysiologySignal(
        studentId = studentId,
        pattern = pattern,
        windowStartEpochMillis = samples.minOf { it.observedAtEpochMillis },
        windowEndEpochMillis = samples.maxOf { it.observedAtEpochMillis },
        quality = samples.map { it.quality }.average(),
        validUntilEpochMillis = nowEpochMillis + rules.signalValidityMillis,
        ruleVersion = rules.ruleVersion,
        consentRevision = consentRevision,
        isSimulated = samples.all { it.domain == HealthDataDomain.DEMO },
    )
}
