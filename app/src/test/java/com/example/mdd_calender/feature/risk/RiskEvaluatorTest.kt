package com.example.mdd_calender.feature.risk

import com.example.mdd_calender.domain.model.*
import com.example.mdd_calender.domain.port.RiskEvaluationInput
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class RiskEvaluatorTest {
    private val evaluator = DemonstrationRiskEvaluator()
    private val now = 1_700_000_000_000L

    private fun assessment(id: String, type: AssessmentType, score: Int, safety: Boolean = false, at: Long = now) = AssessmentForEvaluation(id, "s1", type, score, SymptomBand.MODERATE, safety, at, "score-1")
    private fun input(vararg a: AssessmentForEvaluation, signals: List<PhysiologySignal> = emptyList()) = RiskEvaluationInput("s1", a.toList(), signals, DemonstrationRiskEvaluator.RULE_VERSION, now)

    @Test fun `highest valid scale wins without adding`() = runBlocking {
        val result = (evaluator.evaluate(input(assessment("p", AssessmentType.PHQ_9, 12), assessment("g", AssessmentType.GAD_7, 6))) as CareResult.Success).value
        assertEquals(ConcernLevel.MODERATE, result.baseConcernLevel)
        assertEquals(EvaluationDataSufficiency.COMPLETE, result.dataSufficiency)
    }

    @Test fun `query window starts fourteen days before evaluation`() {
        assertEquals(
            now - 14L * 24 * 60 * 60 * 1000,
            DemonstrationRiskEvaluator.assessmentWindowStart(now),
        )
        assertEquals(0L, DemonstrationRiskEvaluator.assessmentWindowStart(1L))
    }

    @Test fun `older valid scale is retained when latest scale is submitted`() = runBlocking {
        val earlierPhq = assessment("p", AssessmentType.PHQ_9, 16, at = now - 7L * 24 * 60 * 60 * 1000)
        val latestGad = assessment("g", AssessmentType.GAD_7, 2, at = now)
        val result = (evaluator.evaluate(input(earlierPhq, latestGad)) as CareResult.Success).value

        assertEquals(ConcernLevel.HIGH, result.baseConcernLevel)
        assertEquals(listOf("g", "p"), result.assessmentIds)
        assertEquals(EvaluationDataSufficiency.COMPLETE, result.dataSufficiency)
    }

    @Test fun `expired scales produce insufficient data`() = runBlocking {
        val result = (evaluator.evaluate(input(assessment("p", AssessmentType.PHQ_9, 20, at = now - 15L * 24 * 60 * 60 * 1000))) as CareResult.Success).value
        assertEquals(ConcernLevel.INSUFFICIENT_DATA, result.baseConcernLevel)
        assertNull(AlertEventFactory.fromEvaluation(result, "STU-1"))
    }

    @Test fun `safety item creates prompt review even with low total`() = runBlocking {
        val result = (evaluator.evaluate(input(assessment("p", AssessmentType.PHQ_9, 2, safety = true))) as CareResult.Success).value
        assertEquals(DispositionPriority.PROMPT_SAFETY_REVIEW, result.dispositionPriority)
        assertNotNull(AlertEventFactory.fromEvaluation(result, "STU-1"))
    }

    @Test fun `physiology does not raise low scale to alert`() = runBlocking {
        val signal = PhysiologySignal("h", "s1", SignalType.LOW_SLEEP, now - 1000, now - 500, SampleQuality.GOOD, now + 1000, "health-1", 1, true)
        val result = (evaluator.evaluate(input(assessment("p", AssessmentType.PHQ_9, 2), signals = listOf(signal))) as CareResult.Success).value
        assertEquals(ConcernLevel.LOW, result.baseConcernLevel)
        assertNull(AlertEventFactory.fromEvaluation(result, "STU-1"))
    }

    @Test fun `same input is deterministic`() = runBlocking {
        val first = (evaluator.evaluate(input(assessment("p", AssessmentType.PHQ_9, 15))) as CareResult.Success).value
        val second = (evaluator.evaluate(input(assessment("p", AssessmentType.PHQ_9, 15))) as CareResult.Success).value
        assertEquals(first.evaluationId, second.evaluationId)
        assertEquals(AlertEventFactory.fromEvaluation(first, "STU-1")?.eventId, AlertEventFactory.fromEvaluation(second, "STU-1")?.eventId)
    }
}
