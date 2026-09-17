package com.example.mdd_calender.integration.app

import android.content.Context
import com.example.mdd_calender.data.MoodDatabase
import com.example.mdd_calender.data.care.RoomAlertRepository
import com.example.mdd_calender.data.care.RoomAssessmentRepository
import com.example.mdd_calender.data.care.RoomAuditRepository
import com.example.mdd_calender.data.care.RoomConsentRepository
import com.example.mdd_calender.data.care.RoomCareAdministration
import com.example.mdd_calender.data.care.RoomEvaluationRepository
import com.example.mdd_calender.data.care.RoomFollowUpRepository
import com.example.mdd_calender.data.care.RoomInterventionRepository
import com.example.mdd_calender.data.care.RoomStudentHealthRepository
import com.example.mdd_calender.domain.model.ActorContext
import com.example.mdd_calender.domain.model.ActorRole
import com.example.mdd_calender.domain.model.DataDomain
import com.example.mdd_calender.domain.model.CareResult
import com.example.mdd_calender.domain.model.DeliveryRecord
import com.example.mdd_calender.domain.model.InterventionCase
import com.example.mdd_calender.domain.model.InterventionStatus
import com.example.mdd_calender.domain.port.RiskEvaluationInput
import com.example.mdd_calender.domain.port.SessionProvider
import com.example.mdd_calender.feature.health.DemoHealthProvider
import com.example.mdd_calender.feature.health.DemoHealthScenario
import com.example.mdd_calender.feature.health.DomainHealthDataProviderAdapter
import com.example.mdd_calender.feature.risk.DemonstrationRiskEvaluator
import com.example.mdd_calender.feature.risk.AlertEventFactory
import com.example.mdd_calender.feature.teacher.TeacherWorkbenchService
import com.example.mdd_calender.integration.school.SimulatedSchoolPlatformGateway
import com.example.mdd_calender.domain.port.SchoolPlatformGateway
import com.example.mdd_calender.security.AndroidKeystoreCipher
import com.example.mdd_calender.security.DemoSessionProvider

/**
 * Single composition root for care features.
 *
 * The demo identity is deliberately fixed to the DEMO data domain. Production
 * authentication must replace [session] before any real student data is used.
 */
class AppCareServices(context: Context) {
    private val database = MoodDatabase.getDatabase(context.applicationContext)
    val session: SessionProvider = DemoSessionProvider(
        ActorContext("demo-student", ActorRole.STUDENT, DataDomain.DEMO),
    )
    val teacherSession: SessionProvider = DemoSessionProvider(
        ActorContext("demo-teacher", ActorRole.TEACHER, DataDomain.DEMO),
    )
    private val systemSession: SessionProvider = DemoSessionProvider(
        ActorContext("demo-system", ActorRole.SYSTEM, DataDomain.DEMO),
    )
    val cipher = AndroidKeystoreCipher()

    val assessments = RoomAssessmentRepository(database.careDao(), session, cipher)
    val consent = RoomConsentRepository(database.careDao(), session)
    val studentHealth = RoomStudentHealthRepository(database.careDao(), session, cipher)
    val evaluations = RoomEvaluationRepository(database.careDao(), session)
    val alerts = RoomAlertRepository(database.careDao(), session, cipher)
    val interventions = RoomInterventionRepository(database, session, cipher)
    val followUp = RoomFollowUpRepository(database.careDao(), session, cipher)
    val audit = RoomAuditRepository(database.careDao(), session)

    /** Local-only provider used by the prototype; it never reads a device health store. */
    val demoHealthProvider = DemoHealthProvider(DemoHealthScenario.BALANCED)
    val healthDataProvider = DomainHealthDataProviderAdapter(demoHealthProvider)
    val riskEvaluator = DemonstrationRiskEvaluator()
    private val systemAssessments = RoomAssessmentRepository(database.careDao(), systemSession, cipher)
    private val systemEvaluations = RoomEvaluationRepository(database.careDao(), systemSession)
    private val systemAlerts = RoomAlertRepository(database.careDao(), systemSession, cipher)
    private val administration = RoomCareAdministration(database.careDao(), systemSession, cipher)
    val schoolGateway: SchoolPlatformGateway = SimulatedSchoolPlatformGateway("demo-teacher")
    val teacherService = TeacherWorkbenchService(
        alerts = RoomAlertRepository(database.careDao(), teacherSession, cipher),
        interventions = RoomInterventionRepository(database, teacherSession, cipher),
        gateway = schoolGateway,
        session = teacherSession,
        audit = RoomAuditRepository(database.careDao(), teacherSession),
    )

    suspend fun submitAssessmentAndTriggerCare(record: com.example.mdd_calender.domain.model.AssessmentRecord): CareResult<CareChainReceipt> {
        when (val prepared = administration.upsertStudent(record.studentId, "DEMO-001")) {
            is CareResult.Failure -> return prepared
            is CareResult.Success -> Unit
        }
        when (val assigned = administration.assignTeacher(record.studentId, "demo-teacher")) {
            is CareResult.Failure -> return assigned
            is CareResult.Success -> Unit
        }
        when (val saved = assessments.saveForCurrentStudent(record)) {
            is CareResult.Failure -> return saved
            is CareResult.Success -> Unit
        }
        val completed = when (val result = systemAssessments.completedForEvaluation(record.studentId, record.completedAtEpochMillis ?: 0L)) {
            is CareResult.Failure -> return result
            is CareResult.Success -> result.value
        }
        val evaluation = when (val result = riskEvaluator.evaluate(
            RiskEvaluationInput(
                studentId = record.studentId,
                assessments = completed,
                auxiliarySignals = emptyList(),
                policyVersion = DemonstrationRiskEvaluator.RULE_VERSION,
                generatedAtEpochMillis = System.currentTimeMillis(),
            ),
        )) {
            is CareResult.Failure -> return result
            is CareResult.Success -> result.value
        }
        when (val stored = systemEvaluations.saveEvaluationInternal(evaluation)) {
            is CareResult.Failure -> return stored
            is CareResult.Success -> Unit
        }
        val alert = AlertEventFactory.fromEvaluation(evaluation, studentCode = record.studentId) ?:
            return CareResult.Success(CareChainReceipt(record.assessmentId, evaluation.evaluationId, null, null))
        when (val stored = systemAlerts.saveInternal(alert)) {
            is CareResult.Failure -> return stored
            is CareResult.Success -> Unit
        }
        when (val intervention = administration.createIntervention(
            InterventionCase(
                caseId = "case:${alert.eventId}",
                studentId = alert.studentId,
                assignedTeacherId = "demo-teacher",
                alertId = alert.eventId,
                status = InterventionStatus.PENDING_CONFIRMATION,
                startedAtEpochMillis = null,
                minimalActionNote = null,
            ),
        )) {
            is CareResult.Failure -> if (intervention.error !is com.example.mdd_calender.domain.model.CareFailure.Conflict) return intervention
            is CareResult.Success -> Unit
        }
        val delivery = schoolGateway.deliver(
            com.example.mdd_calender.domain.port.SchoolAlertDto(
                eventId = alert.eventId,
                studentCode = alert.studentCode,
                concernLevel = alert.concernLevel.name,
                minimalReasonTags = alert.minimalReasonTags,
                occurredAtEpochMillis = alert.occurredAtEpochMillis,
                simulated = true,
            ),
            alert.deduplicationKey,
        )
        val deliveryRecord = when (delivery) {
            is CareResult.Failure -> return delivery
            is CareResult.Success -> delivery.value
        }
        when (val stored = systemAlerts.saveDeliveryInternal(deliveryRecord)) {
            is CareResult.Failure -> return stored
            is CareResult.Success -> Unit
        }
        return CareResult.Success(CareChainReceipt(record.assessmentId, evaluation.evaluationId, alert.eventId, deliveryRecord))
    }
}

data class CareChainReceipt(
    val assessmentId: String,
    val evaluationId: String,
    val alertId: String?,
    val delivery: DeliveryRecord?,
)
