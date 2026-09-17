package com.example.mdd_calender.data.care

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "care_students",
    primaryKeys = ["studentId", "dataDomain"],
    indices = [Index(value = ["studentCode", "dataDomain"], unique = true)],
)
data class StudentEntity(val studentId: String, val studentCode: String, val dataDomain: String)

@Entity(
    tableName = "care_teacher_assignments",
    primaryKeys = ["teacherId", "studentId", "dataDomain"],
    indices = [Index("studentId")],
)
data class TeacherAssignmentEntity(val teacherId: String, val studentId: String, val dataDomain: String)

@Entity(tableName = "care_assessments", indices = [Index(value = ["studentId", "dataDomain"])])
data class AssessmentEntity(
    @PrimaryKey val assessmentId: String,
    val studentId: String,
    val dataDomain: String,
    val type: String,
    val instrumentVersion: String,
    val encryptedAnswers: String,
    val totalScore: Int,
    val symptomBand: String,
    val state: String,
    val completedAtEpochMillis: Long?,
    val scoringVersion: String,
)

@Entity(tableName = "care_consents", primaryKeys = ["studentId", "dataDomain"])
data class ConsentEntity(
    val studentId: String,
    val dataDomain: String,
    val scopes: String,
    val state: String,
    val revision: Long,
    val changedAtEpochMillis: Long,
)

@Entity(tableName = "care_health_samples", indices = [Index(value = ["ownerId", "dataDomain", "measuredAtEpochMillis"])])
data class HealthSampleEntity(
    @PrimaryKey val sampleId: String,
    val ownerId: String,
    val dataDomain: String,
    val source: String,
    val type: String,
    val measuredAtEpochMillis: Long,
    val encryptedValueAndUnit: String,
    val quality: String,
    val simulated: Boolean,
    val consentRevision: Long,
)

@Entity(tableName = "care_signals", indices = [Index(value = ["studentId", "dataDomain", "validUntilEpochMillis"])])
data class SignalEntity(
    @PrimaryKey val signalId: String,
    val studentId: String,
    val dataDomain: String,
    val type: String,
    val windowStartEpochMillis: Long,
    val windowEndEpochMillis: Long,
    val quality: String,
    val validUntilEpochMillis: Long,
    val ruleVersion: String,
    val consentRevision: Long,
    val simulated: Boolean,
)

@Entity(tableName = "care_evaluations", indices = [Index(value = ["studentId", "dataDomain"])])
data class EvaluationEntity(
    @PrimaryKey val evaluationId: String,
    val studentId: String,
    val dataDomain: String,
    val assessmentIds: String,
    val baseConcernLevel: String,
    val dispositionPriority: String,
    val auxiliaryTags: String,
    val dataSufficiency: String,
    val ruleVersion: String,
    val generatedAtEpochMillis: Long,
)

@Entity(
    tableName = "care_alerts",
    indices = [Index(value = ["deduplicationKey", "dataDomain"], unique = true), Index("studentId")],
)
data class AlertEntity(
    @PrimaryKey val eventId: String,
    val studentId: String,
    val studentCode: String,
    val dataDomain: String,
    val concernLevel: String,
    val minimalReasonTags: String,
    val occurredAtEpochMillis: Long,
    val ruleVersion: String,
    val evaluationId: String,
    val deduplicationKey: String,
    val disposition: String,
)

@Entity(
    tableName = "care_deliveries",
    indices = [Index(value = ["idempotencyKey", "dataDomain"], unique = true), Index("alertId")],
)
data class DeliveryEntity(
    @PrimaryKey val deliveryId: String,
    val alertId: String,
    val recipientId: String,
    val dataDomain: String,
    val attemptCount: Int,
    val nextRetryAtEpochMillis: Long?,
    val simulated: Boolean,
    val idempotencyKey: String,
    val status: String,
    val encryptedReceipt: String?,
)

@Entity(tableName = "care_interventions", indices = [Index("studentId"), Index(value = ["assignedTeacherId", "dataDomain"])])
data class InterventionEntity(
    @PrimaryKey val caseId: String,
    val studentId: String,
    val assignedTeacherId: String,
    val alertId: String,
    val dataDomain: String,
    val status: String,
    val startedAtEpochMillis: Long?,
    val encryptedMinimalActionNote: String?,
    val lastIdempotencyKey: String?,
)

@Entity(
    tableName = "care_aa_enrollments",
    indices = [Index(value = ["studentId", "dataDomain", "activeSlot"], unique = true), Index("interventionId")],
)
data class AaEnrollmentEntity(
    @PrimaryKey val enrollmentId: String,
    val studentId: String,
    val interventionId: String,
    val dataDomain: String,
    val enrolledAtEpochMillis: Long,
    val status: String,
    val activeSlot: String?,
    val followUpPolicyVersion: String,
    val encryptedExitReview: String?,
)

@Entity(tableName = "care_follow_up_tasks", indices = [Index(value = ["studentId", "dataDomain", "dueAtEpochMillis"])])
data class FollowUpTaskEntity(
    @PrimaryKey val taskId: String,
    val studentId: String,
    val dataDomain: String,
    val type: String,
    val dueAtEpochMillis: Long,
    val completedAtEpochMillis: Long?,
    val relatedAssessmentId: String?,
    val status: String,
)

@Entity(tableName = "care_audit_events", indices = [Index(value = ["dataDomain", "occurredAtEpochMillis"])])
data class AuditEntity(
    @PrimaryKey val auditId: String,
    val actorId: String,
    val dataDomain: String,
    val action: String,
    val objectCode: String,
    val result: String,
    val occurredAtEpochMillis: Long,
)
