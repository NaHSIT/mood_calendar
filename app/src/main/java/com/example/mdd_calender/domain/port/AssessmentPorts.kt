package com.example.mdd_calender.domain.port

import com.example.mdd_calender.domain.model.AssessmentForEvaluation
import com.example.mdd_calender.domain.model.AssessmentRecord
import com.example.mdd_calender.domain.model.CareResult

interface AssessmentRepository {
    suspend fun saveForCurrentStudent(record: AssessmentRecord): CareResult<AssessmentRecord>
    suspend fun listForCurrentStudent(): CareResult<List<AssessmentRecord>>
    suspend fun getForCurrentStudent(assessmentId: String): CareResult<AssessmentRecord>
}

/** Application-internal evaluation input; implementations require a SYSTEM session. */
interface AssessmentEvaluationSource {
    suspend fun completedForEvaluation(studentId: String, sinceEpochMillis: Long): CareResult<List<AssessmentForEvaluation>>
}
