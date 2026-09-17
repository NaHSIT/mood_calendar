package com.example.mdd_calender.data.care

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
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

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertSamples(values: List<HealthSampleEntity>)
    @Query("SELECT * FROM care_health_samples WHERE ownerId=:ownerId AND dataDomain=:domain AND measuredAtEpochMillis BETWEEN :from AND :to ORDER BY measuredAtEpochMillis")
    suspend fun samples(ownerId: String, domain: String, from: Long, to: Long): List<HealthSampleEntity>
    @Query("SELECT * FROM care_health_samples WHERE sampleId=:id AND ownerId=:ownerId AND dataDomain=:domain")
    suspend fun sampleForOwner(id: String, ownerId: String, domain: String): HealthSampleEntity?
    @Query("SELECT * FROM care_health_samples WHERE sampleId=:id AND dataDomain=:domain")
    suspend fun sample(id: String, domain: String): HealthSampleEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertSignals(values: List<SignalEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertEvaluation(value: EvaluationEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertAlert(value: AlertEntity): Long
    @Query("SELECT * FROM care_alerts WHERE deduplicationKey=:key AND dataDomain=:domain")
    suspend fun alertByDeduplicationKey(key: String, domain: String): AlertEntity?
    @Query("SELECT * FROM care_alerts WHERE eventId=:eventId AND dataDomain=:domain")
    suspend fun alert(eventId: String, domain: String): AlertEntity?
    @Query("SELECT a.* FROM care_alerts a INNER JOIN care_teacher_assignments t ON t.studentId=a.studentId AND t.dataDomain=a.dataDomain WHERE t.teacherId=:teacherId AND a.dataDomain=:domain ORDER BY a.occurredAtEpochMillis DESC")
    suspend fun alertsForTeacher(teacherId: String, domain: String): List<AlertEntity>
    @Query("SELECT a.* FROM care_alerts a INNER JOIN care_teacher_assignments t ON t.studentId=a.studentId AND t.dataDomain=a.dataDomain WHERE a.eventId=:eventId AND t.teacherId=:teacherId AND a.dataDomain=:domain")
    suspend fun alertForTeacher(eventId: String, teacherId: String, domain: String): AlertEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertDelivery(value: DeliveryEntity): Long
    @Update suspend fun updateDelivery(value: DeliveryEntity)
    @Query("SELECT * FROM care_deliveries WHERE idempotencyKey=:key AND dataDomain=:domain")
    suspend fun deliveryByIdempotencyKey(key: String, domain: String): DeliveryEntity?

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

    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertAudit(value: AuditEntity): Long
}
