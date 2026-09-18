package com.example.mdd_calender.data.care

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CareDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertStudent(value: StudentEntity)
    @Query("SELECT * FROM care_students WHERE studentId=:studentId AND dataDomain=:domain")
    suspend fun student(studentId: String, domain: String): StudentEntity?
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertAssignment(value: TeacherAssignmentEntity): Long
    @Query("SELECT EXISTS(SELECT 1 FROM care_teacher_assignments WHERE teacherId=:teacherId AND studentId=:studentId AND dataDomain=:domain)")
    suspend fun isAssigned(teacherId: String, studentId: String, domain: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertAssessment(value: AssessmentEntity)
    @Query("SELECT * FROM care_assessments WHERE studentId=:studentId AND dataDomain=:domain ORDER BY completedAtEpochMillis DESC")
    suspend fun assessmentsForOwner(studentId: String, domain: String): List<AssessmentEntity>
    @Query("SELECT * FROM care_assessments WHERE assessmentId=:id AND studentId=:studentId AND dataDomain=:domain")
    suspend fun assessmentForOwner(id: String, studentId: String, domain: String): AssessmentEntity?
    @Query("SELECT * FROM care_assessments WHERE assessmentId=:id AND dataDomain=:domain")
    suspend fun assessment(id: String, domain: String): AssessmentEntity?
    @Query("SELECT * FROM care_assessments WHERE studentId=:studentId AND dataDomain=:domain AND state='COMPLETED' AND completedAtEpochMillis>=:since")
    suspend fun completedAssessments(studentId: String, domain: String, since: Long): List<AssessmentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertConsent(value: ConsentEntity)
    @Query("SELECT * FROM care_consents WHERE studentId=:studentId AND dataDomain=:domain")
    suspend fun consent(studentId: String, domain: String): ConsentEntity?
    @Query("SELECT * FROM care_consents WHERE studentId=:studentId AND dataDomain=:domain")
    fun observeConsent(studentId: String, domain: String): Flow<ConsentEntity?>

    @Transaction
    suspend fun reviseConsent(studentId: String, domain: String, scopes: String, state: String, now: Long): ConsentEntity {
        val old = consent(studentId, domain)
        if (old != null && old.scopes == scopes && old.state == state) return old
        return ConsentEntity(studentId, domain, scopes, state, (old?.revision ?: 0) + 1, now).also { upsertConsent(it) }
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertSamples(values: List<HealthSampleEntity>)

    /** The final authorization check and write share a transaction, including late provider callbacks. */
    @Transaction
    suspend fun saveSamplesIfAuthorized(ownerId: String, domain: String, values: List<HealthSampleEntity>): Boolean {
        val current = consent(ownerId, domain) ?: return false
        if (current.state != "GRANTED") return false
        val scopes = current.scopes.split(',').toSet()
        if (values.any {
                it.ownerId != ownerId || it.dataDomain != domain || it.consentRevision != current.revision ||
                    (if (it.type == "HEART_RATE_BPM") "HEART_RATE" else "SLEEP") !in scopes
            }) return false
        upsertSamples(values)
        return true
    }
    @Query("SELECT * FROM care_health_samples WHERE ownerId=:ownerId AND dataDomain=:domain AND measuredAtEpochMillis BETWEEN :from AND :to ORDER BY measuredAtEpochMillis")
    suspend fun samples(ownerId: String, domain: String, from: Long, to: Long): List<HealthSampleEntity>
    @Query("SELECT * FROM care_health_samples WHERE sampleId=:id AND ownerId=:ownerId AND dataDomain=:domain")
    suspend fun sampleForOwner(id: String, ownerId: String, domain: String): HealthSampleEntity?
    @Query("SELECT * FROM care_health_samples WHERE sampleId=:id AND dataDomain=:domain")
    suspend fun sample(id: String, domain: String): HealthSampleEntity?
    @Query("DELETE FROM care_health_samples WHERE ownerId=:ownerId AND dataDomain=:domain AND type IN (:types)")
    suspend fun deleteSamplesForOwner(ownerId: String, domain: String, types: List<String>): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertSignals(values: List<SignalEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertEvaluation(value: EvaluationEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertAlert(value: AlertEntity): Long
    @Update suspend fun updateAlert(value: AlertEntity)
    @Query("SELECT * FROM care_alerts WHERE deduplicationKey=:key AND dataDomain=:domain")
    suspend fun alertByDeduplicationKey(key: String, domain: String): AlertEntity?
    @Query("SELECT * FROM care_alerts WHERE eventId=:eventId AND dataDomain=:domain")
    suspend fun alert(eventId: String, domain: String): AlertEntity?
    @Query("SELECT a.* FROM care_alerts a INNER JOIN care_teacher_assignments t ON t.studentId=a.studentId AND t.dataDomain=a.dataDomain WHERE t.teacherId=:teacherId AND a.dataDomain=:domain ORDER BY a.occurredAtEpochMillis DESC")
    suspend fun alertsForTeacher(teacherId: String, domain: String): List<AlertEntity>
    @Query("SELECT a.* FROM care_alerts a INNER JOIN care_teacher_assignments t ON t.studentId=a.studentId AND t.dataDomain=a.dataDomain WHERE a.eventId=:eventId AND t.teacherId=:teacherId AND a.dataDomain=:domain")
    suspend fun alertForTeacher(eventId: String, teacherId: String, domain: String): AlertEntity?
    @Query("SELECT COUNT(*) FROM care_alerts WHERE studentId=:studentId AND dataDomain=:domain AND disposition!='CLOSED' AND (',' || minimalReasonTags || ',') LIKE '%,SAFETY_REVIEW_REQUIRED,%'")
    suspend fun unresolvedSafetyAlertCount(studentId: String, domain: String): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertDelivery(value: DeliveryEntity): Long
    @Update suspend fun updateDelivery(value: DeliveryEntity)
    @Query("SELECT * FROM care_deliveries WHERE idempotencyKey=:key AND dataDomain=:domain")
    suspend fun deliveryByIdempotencyKey(key: String, domain: String): DeliveryEntity?
    @Query("SELECT * FROM care_deliveries WHERE dataDomain=:domain AND status IN ('PENDING','RETRY_PENDING') AND (nextRetryAtEpochMillis IS NULL OR nextRetryAtEpochMillis<=:now)")
    suspend fun dueDeliveries(domain: String, now: Long): List<DeliveryEntity>
    @Query("SELECT * FROM care_deliveries WHERE alertId=:alertId AND dataDomain=:domain ORDER BY attemptCount DESC LIMIT 1")
    suspend fun deliveryForAlert(alertId: String, domain: String): DeliveryEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertIntervention(value: InterventionEntity): Long
    @Update suspend fun updateIntervention(value: InterventionEntity)
    @Query("SELECT * FROM care_interventions WHERE assignedTeacherId=:teacherId AND dataDomain=:domain ORDER BY startedAtEpochMillis DESC")
    suspend fun interventionsForTeacher(teacherId: String, domain: String): List<InterventionEntity>
    @Query("SELECT * FROM care_interventions WHERE caseId=:caseId AND assignedTeacherId=:teacherId AND dataDomain=:domain")
    suspend fun interventionForTeacher(caseId: String, teacherId: String, domain: String): InterventionEntity?
    @Query("SELECT * FROM care_interventions WHERE caseId=:caseId AND dataDomain=:domain")
    suspend fun intervention(caseId: String, domain: String): InterventionEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertEnrollment(value: AaEnrollmentEntity): Long
    @Update suspend fun updateEnrollment(value: AaEnrollmentEntity)
    @Query("SELECT * FROM care_aa_enrollments WHERE studentId=:studentId AND dataDomain=:domain AND activeSlot='ACTIVE' LIMIT 1")
    suspend fun activeEnrollment(studentId: String, domain: String): AaEnrollmentEntity?
    @Query("SELECT * FROM care_aa_enrollments WHERE enrollmentId=:id AND dataDomain=:domain")
    suspend fun enrollment(id: String, domain: String): AaEnrollmentEntity?
    @Query("SELECT e.* FROM care_aa_enrollments e INNER JOIN care_teacher_assignments t ON t.studentId=e.studentId AND t.dataDomain=e.dataDomain WHERE t.teacherId=:teacherId AND e.dataDomain=:domain")
    suspend fun enrollmentsForTeacher(teacherId: String, domain: String): List<AaEnrollmentEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertTask(value: FollowUpTaskEntity): Long
    @Update suspend fun updateTask(value: FollowUpTaskEntity)
    @Query("SELECT * FROM care_follow_up_tasks WHERE studentId=:studentId AND dataDomain=:domain ORDER BY dueAtEpochMillis")
    suspend fun tasksForStudent(studentId: String, domain: String): List<FollowUpTaskEntity>
    @Query("SELECT * FROM care_follow_up_tasks WHERE taskId=:id AND studentId=:studentId AND dataDomain=:domain")
    suspend fun taskForStudent(id: String, studentId: String, domain: String): FollowUpTaskEntity?

    @Transaction
    suspend fun ensureEnrollmentTasks(enrollment: AaEnrollmentEntity) {
        val day = 24L * 60 * 60 * 1000
        listOf(Triple("check-in", "CHECK_IN", 1), Triple("assessment-retake", "ASSESSMENT_RETAKE", 7), Triple("teacher-review", "TEACHER_REVIEW", 3))
            .forEach { (suffix, kind, days) -> insertTask(FollowUpTaskEntity(
                "${enrollment.enrollmentId}:$suffix", enrollment.studentId, enrollment.dataDomain, kind,
                enrollment.enrolledAtEpochMillis + days * day, null, null, "PENDING")) }
    }
    @Query("SELECT COUNT(*) FROM care_follow_up_tasks WHERE studentId=:studentId AND dataDomain=:domain AND type='ASSESSMENT_RETAKE' AND status='COMPLETED' AND completedAtEpochMillis>=:since")
    suspend fun completedAssessmentRetakeCount(studentId: String, domain: String, since: Long): Int
    @Query("SELECT COUNT(*) FROM care_alerts WHERE studentId=:studentId AND dataDomain=:domain AND minimalReasonTags LIKE '%SAFETY_REVIEW_REQUIRED%' AND disposition!='CLOSED'")
    suspend fun unresolvedSafetyConcernCount(studentId: String, domain: String): Int
    @Query("UPDATE care_follow_up_tasks SET status='CANCELLED' WHERE studentId=:studentId AND dataDomain=:domain AND taskId LIKE :taskPrefix AND status NOT IN ('COMPLETED', 'CANCELLED')")
    suspend fun cancelOpenTasks(studentId: String, domain: String, taskPrefix: String): Int

    @Transaction
    suspend fun completeTaskChecked(id: String, studentId: String, domain: String, kind: String, now: Long, assessmentId: String? = null): FollowUpTaskEntity? {
        val task = taskForStudent(id, studentId, domain) ?: return null
        if (task.type != kind || task.status == "CANCELLED") return null
        if (task.status == "COMPLETED") return task
        val enrollment = activeEnrollment(studentId, domain) ?: return null
        if (task.dueAtEpochMillis < enrollment.enrolledAtEpochMillis) return null
        if (kind == "ASSESSMENT_RETAKE") {
            val evidence = assessmentId?.let { assessmentForOwner(it, studentId, domain) } ?: return null
            val completed = evidence.completedAtEpochMillis ?: return null
            if (evidence.state != "COMPLETED" || completed < enrollment.enrolledAtEpochMillis || completed > now) return null
        }
        val updated = task.copy(status = "COMPLETED", completedAtEpochMillis = now, relatedAssessmentId = assessmentId)
        updateTask(updated)
        return updated
    }

    /** A recent completed reassessment is evidence; elapsed time alone is not stability. */
    suspend fun hasStableReassessment(studentId: String, domain: String, enrolledAt: Long, now: Long): Boolean {
        val window = maxOf(enrolledAt, now - 14L * 24 * 60 * 60 * 1000)
        val latest = completedAssessments(studentId, domain, window)
            .filter { (it.completedAtEpochMillis ?: Long.MAX_VALUE) <= now }
            .groupBy { it.type }.values.map { rows -> rows.maxBy { it.completedAtEpochMillis ?: 0 } }
        return latest.isNotEmpty() && latest.all { it.totalScore < 10 } && tasksForStudent(studentId, domain).any {
            it.type == "ASSESSMENT_RETAKE" && it.status == "COMPLETED" &&
                it.relatedAssessmentId in latest.map { record -> record.assessmentId }
        }
    }

    @Transaction
    suspend fun finalizeExitReview(
        enrollmentId: String,
        teacherId: String,
        domain: String,
        approved: Boolean,
        encryptedReview: String,
        now: Long,
        minimumObservationMillis: Long,
        requireResolvedTasks: Boolean,
    ): Int {
        val current = enrollment(enrollmentId, domain) ?: return EXIT_NOT_FOUND
        if (!isAssigned(teacherId, current.studentId, domain)) return EXIT_FORBIDDEN
        if (current.status != "EXIT_REVIEW_PENDING") return EXIT_INVALID_STATE
        if (approved && unresolvedSafetyAlertCount(current.studentId, domain) > 0) return EXIT_SAFETY_BLOCKED
        if (approved && (now - current.enrolledAtEpochMillis < minimumObservationMillis ||
                !hasStableReassessment(current.studentId, domain, current.enrolledAtEpochMillis, now))) return EXIT_NOT_STABLE
        if (approved && requireResolvedTasks && tasksForStudent(current.studentId, domain).any {
                it.dueAtEpochMillis <= now && it.status !in setOf("COMPLETED", "CANCELLED")
            }) return EXIT_TASKS_PENDING

        updateEnrollment(
            current.copy(
                status = if (approved) "EXITED" else "TRACKING",
                activeSlot = if (approved) null else "ACTIVE",
                encryptedExitReview = encryptedReview,
            )
        )
        if (approved) {
            tasksForStudent(current.studentId, domain)
                .filter { it.status != "COMPLETED" && it.status != "CANCELLED" }
                .forEach { updateTask(it.copy(status = "CANCELLED")) }
        }
        return EXIT_UPDATED
    }

    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertAudit(value: AuditEntity): Long

    companion object {
        const val EXIT_UPDATED = 0
        const val EXIT_NOT_FOUND = 1
        const val EXIT_FORBIDDEN = 2
        const val EXIT_INVALID_STATE = 3
        const val EXIT_SAFETY_BLOCKED = 4
        const val EXIT_NOT_STABLE = 5
        const val EXIT_TASKS_PENDING = 6
    }
}
