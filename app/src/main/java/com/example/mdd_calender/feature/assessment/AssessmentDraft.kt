package com.example.mdd_calender.feature.assessment

class AssessmentDraft private constructor(
    val type: AssessmentType,
    val answers: List<Int?>,
) {
    val answeredCount: Int get() = answers.count { it != null }
    val isComplete: Boolean get() = answeredCount == type.itemCount
    val progress: Float get() = answeredCount.toFloat() / type.itemCount

    fun answer(itemIndex: Int, value: Int): AssessmentDraft {
        require(itemIndex in answers.indices) { "Item index is outside this questionnaire" }
        require(value in 0..3) { "Answer must be between 0 and 3" }
        return AssessmentDraft(type, answers.toMutableList().apply { this[itemIndex] = value })
    }

    fun clear(itemIndex: Int): AssessmentDraft {
        require(itemIndex in answers.indices) { "Item index is outside this questionnaire" }
        return AssessmentDraft(type, answers.toMutableList().apply { this[itemIndex] = null })
    }

    fun score(): ScoreResult = AssessmentScorer.score(type, answers)

    companion object {
        fun empty(type: AssessmentType): AssessmentDraft =
            AssessmentDraft(type, List(type.itemCount) { null })

        fun restore(type: AssessmentType, answers: List<Int?>): AssessmentDraft {
            require(answers.size == type.itemCount) { "Saved draft has the wrong item count" }
            require(answers.all { it == null || it in 0..3 }) { "Saved draft has an invalid answer" }
            return AssessmentDraft(type, answers.toList())
        }
    }
}
