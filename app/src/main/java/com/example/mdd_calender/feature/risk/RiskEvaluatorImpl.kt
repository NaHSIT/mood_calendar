package com.example.mdd_calender.feature.risk

import com.example.mdd_calender.domain.model.*
import com.example.mdd_calender.domain.port.RiskEvaluationInput
import com.example.mdd_calender.domain.port.RiskEvaluator
import java.security.MessageDigest

/** Deterministic, explainable demonstration policy; not a clinical prediction model. */
class DemonstrationRiskEvaluator : RiskEvaluator {
    override suspend fun evaluate(input: RiskEvaluationInput): CareResult<EvaluationResult> {
        if (input.studentId.isBlank() || input.policyVersion.isBlank()) {
            return CareResult.Failure(CareFailure.InvalidInput("studentId and policyVersion are required"))
        }
        val valid = input.assessments
            .filter { it.studentId == input.studentId && it.completedAtEpochMillis <= input.generatedAtEpochMillis }
            .groupBy { it.type }
            .mapValues { (_, values) -> values.maxBy { it.completedAtEpochMillis } }
            .values
            .filter { it.completedAtEpochMillis >= assessmentWindowStart(input.generatedAtEpochMillis) }

        val phq = valid.firstOrNull { it.type == AssessmentType.PHQ_9 }
        val gad = valid.firstOrNull { it.type == AssessmentType.GAD_7 }
        val levels = listOfNotNull(phq?.totalScore?.toConcern(), gad?.totalScore?.toConcern())
        val base = levels.maxByOrNull(::rank) ?: ConcernLevel.INSUFFICIENT_DATA
        val tags = linkedSetOf<String>()
        if (phq == null) tags += "PHQ_9_MISSING_OR_EXPIRED"
        if (gad == null) tags += "GAD_7_MISSING_OR_EXPIRED"
        if (phq?.safetyItemNonZero == true) tags += SAFETY_REVIEW_REQUIRED

        val usableSignals = input.auxiliarySignals.filter {
            it.studentId == input.studentId && it.quality != SampleQuality.POOR &&
                it.validUntilEpochMillis >= input.generatedAtEpochMillis &&
                it.windowEndEpochMillis <= input.generatedAtEpochMillis
        }
        usableSignals.forEach { tags += it.type.name }
        val priority = when {
            SAFETY_REVIEW_REQUIRED in tags -> DispositionPriority.PROMPT_SAFETY_REVIEW
            base == ConcernLevel.HIGH || base == ConcernLevel.MODERATE || usableSignals.isNotEmpty() -> DispositionPriority.REVIEW
            else -> DispositionPriority.ROUTINE
        }
        val sufficiency = when {
            valid.isEmpty() -> EvaluationDataSufficiency.INSUFFICIENT
            phq != null && gad != null -> EvaluationDataSufficiency.COMPLETE
            else -> EvaluationDataSufficiency.PARTIAL
        }
        val evaluationId = stableId(input)
        return CareResult.Success(EvaluationResult(
            evaluationId = evaluationId,
            studentId = input.studentId,
            assessmentIds = valid.map { it.assessmentId }.sorted(),
            baseConcernLevel = base,
            dispositionPriority = priority,
            auxiliaryTags = tags,
            dataSufficiency = sufficiency,
            ruleVersion = input.policyVersion,
            generatedAtEpochMillis = input.generatedAtEpochMillis,
        ))
    }

    private fun stableId(input: RiskEvaluationInput): String {
        val payload = buildString {
            append(input.studentId).append('|').append(input.policyVersion)
            input.assessments.sortedBy { it.assessmentId }.forEach { append('|').append(it.assessmentId).append(':').append(it.totalScore).append(':').append(it.safetyItemNonZero).append(':').append(it.completedAtEpochMillis) }
            input.auxiliarySignals.sortedBy { it.signalId }.forEach { append('|').append(it.signalId).append(':').append(it.validUntilEpochMillis).append(':').append(it.quality) }
        }
        return "eval-" + sha256(payload).take(24)
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }

    private fun Int.toConcern() = when (this) {
        in 15..Int.MAX_VALUE -> ConcernLevel.HIGH
        in 10..14 -> ConcernLevel.MODERATE
        in 5..9 -> ConcernLevel.MILD
        else -> ConcernLevel.LOW
    }
    private fun rank(level: ConcernLevel) = when (level) { ConcernLevel.LOW -> 0; ConcernLevel.MILD -> 1; ConcernLevel.MODERATE -> 2; ConcernLevel.HIGH -> 3; ConcernLevel.INSUFFICIENT_DATA -> -1 }

    companion object {
        const val RULE_VERSION = "demo-risk-1"
        const val SAFETY_REVIEW_REQUIRED = "SAFETY_REVIEW_REQUIRED"
        const val ASSESSMENT_VALIDITY_WINDOW_MS = 14L * 24 * 60 * 60 * 1000

        /** Earliest completion time that should be loaded for an evaluation at [generatedAtEpochMillis]. */
        fun assessmentWindowStart(generatedAtEpochMillis: Long): Long =
            (generatedAtEpochMillis - ASSESSMENT_VALIDITY_WINDOW_MS).coerceAtLeast(0L)
    }
}

object AlertEventFactory {
    fun fromEvaluation(result: EvaluationResult, studentCode: String, occurredAtEpochMillis: Long = result.generatedAtEpochMillis): AlertEvent? {
        if (result.baseConcernLevel !in setOf(ConcernLevel.MODERATE, ConcernLevel.HIGH) && DemonstrationRiskEvaluator.SAFETY_REVIEW_REQUIRED !in result.auxiliaryTags) return null
        val key = "${result.studentId}|${result.evaluationId}|${result.baseConcernLevel}|${result.auxiliaryTags.sorted().joinToString(",")}"
        return AlertEvent("alert-${sha256(key).take(24)}", result.studentId, studentCode, result.baseConcernLevel, result.auxiliaryTags, occurredAtEpochMillis, result.ruleVersion, result.evaluationId, key)
    }
    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
}
