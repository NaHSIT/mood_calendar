package com.example.mdd_calender.domain.port

import com.example.mdd_calender.domain.model.AaEnrollment
import com.example.mdd_calender.domain.model.AuditEvent
import com.example.mdd_calender.domain.model.CareResult
import com.example.mdd_calender.domain.model.ExitReviewDecision
import com.example.mdd_calender.domain.model.FollowUpTask
import com.example.mdd_calender.domain.model.InterventionCase
import com.example.mdd_calender.domain.model.InterventionStartRequest
import com.example.mdd_calender.domain.model.InterventionStartResult

interface InterventionRepository {
    suspend fun listAssignedToCurrentTeacher(): CareResult<List<InterventionCase>>
    suspend fun getAssignedToCurrentTeacher(caseId: String): CareResult<InterventionCase>
    suspend fun acknowledge(caseId: String, idempotencyKey: String): CareResult<InterventionCase>
    suspend fun startWithAa(request: InterventionStartRequest): CareResult<InterventionStartResult>
}

interface FollowUpRepository {
    suspend fun currentStudentEnrollment(): CareResult<AaEnrollment>
    suspend fun currentStudentTasks(): CareResult<List<FollowUpTask>>
    suspend fun completeCurrentStudentTask(taskId: String, completedAtEpochMillis: Long): CareResult<FollowUpTask>
    suspend fun requestExit(reason: String): CareResult<AaEnrollment>
    suspend fun listAssignedEnrollments(): CareResult<List<AaEnrollment>>
    suspend fun reviewExit(enrollmentId: String, decision: ExitReviewDecision, note: String): CareResult<AaEnrollment>
}

interface AuditRepository {
    suspend fun append(event: AuditEvent): CareResult<Unit>
}

/** Restricted setup port for demo fixtures and trusted backend synchronization. */
interface CareAdministrationPort {
    suspend fun upsertStudent(studentId: String, studentCode: String): CareResult<Unit>
    suspend fun assignTeacher(studentId: String, teacherId: String): CareResult<Unit>
    suspend fun createIntervention(case: InterventionCase): CareResult<InterventionCase>
    suspend fun createFollowUpTask(task: FollowUpTask): CareResult<FollowUpTask>
}
