package com.example.mdd_calender.domain.port

import com.example.mdd_calender.domain.model.AlertEvent
import com.example.mdd_calender.domain.model.AssessmentForEvaluation
import com.example.mdd_calender.domain.model.CareResult
import com.example.mdd_calender.domain.model.DeliveryRecord
import com.example.mdd_calender.domain.model.EvaluationResult
import com.example.mdd_calender.domain.model.PhysiologySignal
import com.example.mdd_calender.domain.model.TeacherAlertSummary

data class RiskEvaluationInput(
    val studentId: String,
    val assessments: List<AssessmentForEvaluation>,
    val auxiliarySignals: List<PhysiologySignal>,
    val policyVersion: String,
    val generatedAtEpochMillis: Long,
)

fun interface RiskEvaluator {
    suspend fun evaluate(input: RiskEvaluationInput): CareResult<EvaluationResult>
}

interface AlertRepository {
    /** Internal writer. Requires a SYSTEM session and is idempotent by deduplicationKey. */
    suspend fun saveInternal(event: AlertEvent): CareResult<AlertEvent>
    suspend fun listTeacherSummaries(): CareResult<List<TeacherAlertSummary>>
    suspend fun getTeacherSummary(eventId: String): CareResult<TeacherAlertSummary>
    suspend fun saveDeliveryInternal(delivery: DeliveryRecord): CareResult<DeliveryRecord>
    suspend fun getDeliveryInternal(idempotencyKey: String): CareResult<DeliveryRecord>
}

/** Internal persistence for derived, non-raw health signals and evaluation outcomes. */
interface EvaluationRepository {
    suspend fun saveSignalsInternal(signals: List<PhysiologySignal>): CareResult<Unit>
    suspend fun saveEvaluationInternal(result: EvaluationResult): CareResult<EvaluationResult>
}

data class SchoolAlertDto(
    val eventId: String,
    val studentCode: String,
    val concernLevel: String,
    val minimalReasonTags: Set<String>,
    val occurredAtEpochMillis: Long,
    val simulated: Boolean,
)

interface SchoolPlatformGateway {
    suspend fun deliver(alert: SchoolAlertDto, idempotencyKey: String): CareResult<DeliveryRecord>
    suspend fun queryReceipt(deliveryId: String): CareResult<DeliveryRecord>
    suspend fun submitDisposition(eventId: String, disposition: String): CareResult<Unit>
}
