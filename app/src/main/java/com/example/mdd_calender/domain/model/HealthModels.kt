package com.example.mdd_calender.domain.model

enum class HealthScope { HEART_RATE, SLEEP }
enum class ConsentState { GRANTED, REVOKED }

data class HealthConsent(
    val studentId: String,
    val scopes: Set<HealthScope>,
    val state: ConsentState,
    val revision: Long,
    val changedAtEpochMillis: Long,
)

enum class HealthSampleType { HEART_RATE_BPM, SLEEP_DURATION_MINUTES, SLEEP_QUALITY }
enum class SampleQuality { POOR, FAIR, GOOD }

data class RawHealthSample(
    val sampleId: String,
    val ownerId: String,
    val source: String,
    val type: HealthSampleType,
    val measuredAtEpochMillis: Long,
    val value: Double,
    val unit: String,
    val quality: SampleQuality,
    val simulated: Boolean,
    val consentRevision: Long,
)

enum class SignalType { ELEVATED_HEART_RATE, LOW_SLEEP, IRREGULAR_SLEEP }

data class PhysiologySignal(
    val signalId: String,
    val studentId: String,
    val type: SignalType,
    val windowStartEpochMillis: Long,
    val windowEndEpochMillis: Long,
    val quality: SampleQuality,
    val validUntilEpochMillis: Long,
    val ruleVersion: String,
    val consentRevision: Long,
    val simulated: Boolean,
)

enum class HealthCapability { AVAILABLE, NOT_INSTALLED, NOT_SUPPORTED, NOT_CONFIGURED }

data class HealthPullRequest(
    val studentId: String,
    val scopes: Set<HealthScope>,
    val fromEpochMillis: Long,
    val toEpochMillis: Long,
    val consentRevision: Long,
)
