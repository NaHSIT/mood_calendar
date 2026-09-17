package com.example.mdd_calender.feature.assessment

/**
 * Pure assessment scoring. This package deliberately does not mirror the shared domain models:
 * Window A owns those contracts and they have not been frozen yet.
 */
enum class AssessmentType(val itemCount: Int, val maximumScore: Int) {
    PHQ_9(itemCount = 9, maximumScore = 27),
    GAD_7(itemCount = 7, maximumScore = 21),
}

enum class SymptomBand {
    MINIMAL,
    MILD,
    MODERATE,
    MODERATELY_SEVERE,
    SEVERE,
}

sealed interface ScoreResult {
    data class Incomplete(val missingItemIndices: List<Int>) : ScoreResult

    data class Complete(
        val type: AssessmentType,
        val total: Int,
        val symptomBand: SymptomBand,
        val safetyReviewRequired: Boolean,
    ) : ScoreResult
}

object AssessmentScorer {
    const val SCORING_VERSION = "prototype-1"

    fun score(type: AssessmentType, answers: List<Int?>): ScoreResult {
        require(answers.size == type.itemCount) {
            "${type.name} requires exactly ${type.itemCount} answers"
        }
        require(answers.all { it == null || it in 0..3 }) {
            "Each answer must be null or between 0 and 3"
        }

        val missing = answers.mapIndexedNotNull { index, answer -> index.takeIf { answer == null } }
        if (missing.isNotEmpty()) return ScoreResult.Incomplete(missing)

        val total = answers.sumOf { requireNotNull(it) }
        return ScoreResult.Complete(
            type = type,
            total = total,
            symptomBand = bandFor(type, total),
            safetyReviewRequired = type == AssessmentType.PHQ_9 && answers[8] != 0,
        )
    }

    fun bandFor(type: AssessmentType, total: Int): SymptomBand {
        require(total in 0..type.maximumScore) {
            "Score for ${type.name} must be between 0 and ${type.maximumScore}"
        }
        return when (type) {
            AssessmentType.PHQ_9 -> when (total) {
                in 0..4 -> SymptomBand.MINIMAL
                in 5..9 -> SymptomBand.MILD
                in 10..14 -> SymptomBand.MODERATE
                in 15..19 -> SymptomBand.MODERATELY_SEVERE
                else -> SymptomBand.SEVERE
            }

            AssessmentType.GAD_7 -> when (total) {
                in 0..4 -> SymptomBand.MINIMAL
                in 5..9 -> SymptomBand.MILD
                in 10..14 -> SymptomBand.MODERATE
                else -> SymptomBand.SEVERE
            }
        }
    }
}
