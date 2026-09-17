package com.example.mdd_calender.feature.assessment

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AssessmentScoringTest {
    @Test
    fun phq9_allBandBoundariesAreStable() {
        assertBands(
            AssessmentType.PHQ_9,
            0 to SymptomBand.MINIMAL,
            4 to SymptomBand.MINIMAL,
            5 to SymptomBand.MILD,
            9 to SymptomBand.MILD,
            10 to SymptomBand.MODERATE,
            14 to SymptomBand.MODERATE,
            15 to SymptomBand.MODERATELY_SEVERE,
            19 to SymptomBand.MODERATELY_SEVERE,
            20 to SymptomBand.SEVERE,
            27 to SymptomBand.SEVERE,
        )
    }

    @Test
    fun gad7_allBandBoundariesAreStable() {
        assertBands(
            AssessmentType.GAD_7,
            0 to SymptomBand.MINIMAL,
            4 to SymptomBand.MINIMAL,
            5 to SymptomBand.MILD,
            9 to SymptomBand.MILD,
            10 to SymptomBand.MODERATE,
            14 to SymptomBand.MODERATE,
            15 to SymptomBand.SEVERE,
            21 to SymptomBand.SEVERE,
        )
    }

    @Test
    fun missingItemsCannotProduceACompleteScore() {
        val result = AssessmentScorer.score(
            AssessmentType.GAD_7,
            listOf(0, null, 1, null, 2, 0, 3),
        )

        assertEquals(ScoreResult.Incomplete(listOf(1, 3)), result)
    }

    @Test
    fun invalidAnswerAndWrongLengthAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            AssessmentScorer.score(AssessmentType.GAD_7, List(7) { 4 })
        }
        assertThrows(IllegalArgumentException::class.java) {
            AssessmentScorer.score(AssessmentType.PHQ_9, List(8) { 0 })
        }
        assertThrows(IllegalArgumentException::class.java) {
            AssessmentScorer.bandFor(AssessmentType.PHQ_9, 28)
        }
    }

    @Test
    fun phq9ItemNineFlagsReviewWithoutChangingScore() {
        val result = AssessmentScorer.score(
            AssessmentType.PHQ_9,
            List(8) { 0 } + 1,
        ) as ScoreResult.Complete

        assertEquals(1, result.total)
        assertEquals(SymptomBand.MINIMAL, result.symptomBand)
        assertTrue(result.safetyReviewRequired)
    }

    @Test
    fun gad7NeverUsesThePhq9SafetyFlag() {
        val result = AssessmentScorer.score(
            AssessmentType.GAD_7,
            List(7) { 3 },
        ) as ScoreResult.Complete

        assertFalse(result.safetyReviewRequired)
    }

    private fun assertBands(type: AssessmentType, vararg cases: Pair<Int, SymptomBand>) {
        cases.forEach { (score, expected) ->
            assertEquals("$type at $score", expected, AssessmentScorer.bandFor(type, score))
        }
    }
}
