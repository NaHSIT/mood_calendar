package com.example.mdd_calender.domain.model

enum class ConcernLevel { LOW, MILD, MODERATE, HIGH, INSUFFICIENT_DATA }
enum class DispositionPriority { ROUTINE, REVIEW, PROMPT_SAFETY_REVIEW }
enum class EvaluationDataSufficiency { COMPLETE, PARTIAL, INSUFFICIENT }

data class EvaluationResult(
    val evaluationId: String,
    val studentId: String,
    val assessmentIds: List<String>,
    val baseConcernLevel: ConcernLevel,
    val dispositionPriority: DispositionPriority,
    val auxiliaryTags: Set<String>,
    val dataSufficiency: EvaluationDataSufficiency,
    val ruleVersion: String,
    val generatedAtEpochMillis: Long,
)

data class AlertEvent(
    val eventId: String,
    val studentId: String,
    val studentCode: String,
    val concernLevel: ConcernLevel,
    val minimalReasonTags: Set<String>,
    val occurredAtEpochMillis: Long,
    val ruleVersion: String,
    val evaluationId: String,
    val deduplicationKey: String,
)

enum class AlertDisposition { NEW, ACKNOWLEDGED, IN_PROGRESS, CLOSED }

data class TeacherAlertSummary(
    val eventId: String,
    val studentCode: String,
    val concernLevel: ConcernLevel,
    val minimalReasonTags: Set<String>,
    val occurredAtEpochMillis: Long,
    val disposition: AlertDisposition,
)

enum class DeliveryStatus { PENDING, SIMULATED_DELIVERED, RETRY_PENDING, FAILED }

data class DeliveryRecord(
    val deliveryId: String,
    val alertId: String,
    val recipientId: String,
    val attemptCount: Int,
    val nextRetryAtEpochMillis: Long?,
    val simulated: Boolean,
    val idempotencyKey: String,
    val status: DeliveryStatus,
    val receipt: String?,
)
