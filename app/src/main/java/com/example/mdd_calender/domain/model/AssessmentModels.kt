package com.example.mdd_calender.domain.model

enum class AssessmentType { PHQ_9, GAD_7 }
enum class AssessmentState { DRAFT, COMPLETED }
enum class SymptomBand { MINIMAL, MILD, MODERATE, MODERATELY_SEVERE, SEVERE }

data class AssessmentRecord(
    val assessmentId: String,
    val studentId: String,
    val type: AssessmentType,
    val instrumentVersion: String,
    val answers: List<Int>,
    val totalScore: Int,
    val symptomBand: SymptomBand,
    val state: AssessmentState,
    val completedAtEpochMillis: Long?,
    val scoringVersion: String,
)

data class AssessmentForEvaluation(
    val assessmentId: String,
    val studentId: String,
    val type: AssessmentType,
    val totalScore: Int,
    val symptomBand: SymptomBand,
    val safetyItemNonZero: Boolean,
    val completedAtEpochMillis: Long,
    val scoringVersion: String,
)
