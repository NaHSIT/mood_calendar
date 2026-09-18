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
) : FeatureSignalExtractor, com.example.mdd_calender.domain.port.PhysiologySignalExtractor {
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

    override suspend fun extract(
        samples: List<com.example.mdd_calender.domain.model.RawHealthSample>,
        consent: com.example.mdd_calender.domain.model.HealthConsent,
    ): com.example.mdd_calender.domain.model.CareResult<List<com.example.mdd_calender.domain.model.PhysiologySignal>> {
        val localSamples = samples.mapNotNull { sample ->
            val metric = when (sample.type) {
                com.example.mdd_calender.domain.model.HealthSampleType.HEART_RATE_BPM -> HealthMetric.HEART_RATE
                com.example.mdd_calender.domain.model.HealthSampleType.SLEEP_DURATION_MINUTES -> HealthMetric.SLEEP
                com.example.mdd_calender.domain.model.HealthSampleType.SLEEP_QUALITY -> null
            }
            metric?.let {
                HealthSample(
                    ownerId = sample.ownerId,
                    metric = it,
                    observedAtEpochMillis = sample.measuredAtEpochMillis,
                    value = if (sample.type == com.example.mdd_calender.domain.model.HealthSampleType.SLEEP_DURATION_MINUTES) sample.value / 60.0 else sample.value,
                    unit = sample.unit,
                    quality = when (sample.quality) {
                        com.example.mdd_calender.domain.model.SampleQuality.POOR -> 0.25
                        com.example.mdd_calender.domain.model.SampleQuality.FAIR -> 0.6
                        com.example.mdd_calender.domain.model.SampleQuality.GOOD -> 0.9
                    },
                    domain = if (sample.simulated) HealthDataDomain.DEMO else HealthDataDomain.REAL,
                )
            }
        }
        val report = extract(
            studentId = consent.studentId,
            samples = localSamples,
            consentRevision = consent.revision,
            nowEpochMillis = samples.maxOfOrNull { it.measuredAtEpochMillis } ?: 0L,
        )
        val signals = report.signals.map { signal ->
            com.example.mdd_calender.domain.model.PhysiologySignal(
                signalId = "${signal.studentId}-${signal.pattern}-${signal.windowEndEpochMillis}",
                studentId = signal.studentId,
                type = when (signal.pattern) {
                    PhysiologyPattern.ELEVATED_RESTING_HEART_RATE -> com.example.mdd_calender.domain.model.SignalType.ELEVATED_HEART_RATE
                    PhysiologyPattern.SHORT_SLEEP_PATTERN -> com.example.mdd_calender.domain.model.SignalType.LOW_SLEEP
                },
                windowStartEpochMillis = signal.windowStartEpochMillis,
                windowEndEpochMillis = signal.windowEndEpochMillis,
                quality = when {
                    signal.quality < 0.4 -> com.example.mdd_calender.domain.model.SampleQuality.POOR
                    signal.quality < 0.75 -> com.example.mdd_calender.domain.model.SampleQuality.FAIR
                    else -> com.example.mdd_calender.domain.model.SampleQuality.GOOD
                },
                validUntilEpochMillis = signal.validUntilEpochMillis,
                ruleVersion = signal.ruleVersion,
                consentRevision = signal.consentRevision,
                simulated = signal.isSimulated,
            )
        }
        return com.example.mdd_calender.domain.model.CareResult.Success(signals)
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
