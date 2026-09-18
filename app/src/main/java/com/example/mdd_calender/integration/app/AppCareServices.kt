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
import com.example.mdd_calender.domain.model.ConsentState
import com.example.mdd_calender.domain.model.DeliveryRecord
import com.example.mdd_calender.domain.model.InterventionCase
import com.example.mdd_calender.domain.model.InterventionStatus
import com.example.mdd_calender.domain.model.FollowUpTask
import com.example.mdd_calender.domain.model.FollowUpTaskStatus
import com.example.mdd_calender.domain.model.FollowUpTaskType
import com.example.mdd_calender.domain.model.HealthConsent
import com.example.mdd_calender.domain.model.HealthPullRequest
import com.example.mdd_calender.domain.model.HealthSampleType
import com.example.mdd_calender.domain.model.HealthScope
import com.example.mdd_calender.domain.model.RawHealthSample
import com.example.mdd_calender.domain.port.RiskEvaluationInput
import com.example.mdd_calender.domain.port.SessionProvider
import com.example.mdd_calender.feature.health.DemoHealthProvider
import com.example.mdd_calender.feature.health.DemoHealthScenario
import com.example.mdd_calender.feature.health.DomainHealthDataProviderAdapter
import com.example.mdd_calender.feature.health.HealthConsentSnapshot
import com.example.mdd_calender.feature.health.HealthDataDomain
import com.example.mdd_calender.feature.health.HealthMetric
import com.example.mdd_calender.feature.health.HealthSample
import com.example.mdd_calender.feature.health.RawHealthDataView
import com.example.mdd_calender.feature.health.RuleBasedPhysiologySignalExtractor
import com.example.mdd_calender.feature.risk.DemonstrationRiskEvaluator
import com.example.mdd_calender.feature.risk.AlertEventFactory
import com.example.mdd_calender.feature.teacher.TeacherWorkbenchService
import com.example.mdd_calender.integration.school.SimulatedSchoolPlatformGateway
import com.example.mdd_calender.domain.port.SchoolPlatformGateway
import com.example.mdd_calender.security.AndroidKeystoreCipher
import com.example.mdd_calender.security.DemoSessionProvider
import com.example.mdd_calender.domain.model.DeliveryStatus
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Single composition root for care features.
 *
 * The demo identity is deliberately fixed to the DEMO data domain. Production
 * authentication must replace [session] before any real student data is used.
 */
class AppCareServices(
    context: Context,
    private val database: MoodDatabase = MoodDatabase.getDatabase(context.applicationContext),
    val schoolGateway: SchoolPlatformGateway = SimulatedSchoolPlatformGateway("demo-teacher"),
) {
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
    private val systemHealth = RoomStudentHealthRepository(database.careDao(), systemSession, cipher)
    private val systemEvaluations = RoomEvaluationRepository(database.careDao(), systemSession)
    private val systemAlerts = RoomAlertRepository(database.careDao(), systemSession, cipher)
    private val administration = RoomCareAdministration(database.careDao(), systemSession, cipher)
    private val deliveryMutex = Mutex()
    val teacherService = TeacherWorkbenchService(
        alerts = RoomAlertRepository(database.careDao(), teacherSession, cipher),
        interventions = RoomInterventionRepository(database, teacherSession, cipher),
        followUps = RoomFollowUpRepository(database.careDao(), teacherSession, cipher),
        gateway = schoolGateway,
        session = teacherSession,
        audit = RoomAuditRepository(database.careDao(), teacherSession),
        deliveryDispatcher = { enqueueAndDeliver(it) },
    )
    val teacherFollowUp = RoomFollowUpRepository(database.careDao(), teacherSession, cipher)

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
        val evaluationAt = System.currentTimeMillis()
        val completed = when (val result = systemAssessments.completedForEvaluation(
            record.studentId,
            DemonstrationRiskEvaluator.assessmentWindowStart(evaluationAt),
        )) {
            is CareResult.Failure -> return result
            is CareResult.Success -> result.value
        }
        val physiologySignals = when (val result = currentPhysiologySignals(record.studentId, evaluationAt)) {
            is CareResult.Failure -> emptyList()
            is CareResult.Success -> result.value
        }
        val evaluation = when (val result = riskEvaluator.evaluate(
            RiskEvaluationInput(
                studentId = record.studentId,
                assessments = completed,
                auxiliarySignals = physiologySignals,
                policyVersion = DemonstrationRiskEvaluator.RULE_VERSION,
                generatedAtEpochMillis = evaluationAt,
            ),
        )) {
            is CareResult.Failure -> return result
            is CareResult.Success -> result.value
        }
        when (val stored = systemEvaluations.saveEvaluationInternal(evaluation)) {
            is CareResult.Failure -> return stored
            is CareResult.Success -> Unit
        }
        val alert = AlertEventFactory.fromEvaluation(evaluation, studentCode = "DEMO-001") ?:
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
        val deliveryRecord = when (val result = enqueueAndDeliver(alert.eventId)) {
            is CareResult.Failure -> return result
            is CareResult.Success -> result.value
        }
        return CareResult.Success(CareChainReceipt(record.assessmentId, evaluation.evaluationId, alert.eventId, deliveryRecord))
    }

    /** Durable local outbox: retry when the application returns to the foreground. */
    suspend fun retryPendingDeliveries() {
        for (pending in database.careDao().dueDeliveries(DataDomain.DEMO.name, System.currentTimeMillis())) {
            enqueueAndDeliver(pending.alertId)
        }
    }

    private suspend fun enqueueAndDeliver(eventId: String): CareResult<DeliveryRecord> = deliveryMutex.withLock {
        val alert = database.careDao().alert(eventId, DataDomain.DEMO.name)
            ?: return@withLock CareResult.Failure(com.example.mdd_calender.domain.model.CareFailure.NotFound("alert", eventId))
        val existing = (systemAlerts.getDeliveryInternal(alert.deduplicationKey) as? CareResult.Success)?.value
        if (existing?.status == DeliveryStatus.SIMULATED_DELIVERED || existing?.status == DeliveryStatus.FAILED ||
            (existing?.nextRetryAtEpochMillis ?: 0) > System.currentTimeMillis()) return@withLock CareResult.Success(requireNotNull(existing))
        val pending = existing ?: DeliveryRecord(
            "delivery:$eventId", eventId, "demo-teacher", 0, null, true,
            alert.deduplicationKey, DeliveryStatus.PENDING, null,
        )
        when (val saved = systemAlerts.saveDeliveryInternal(pending)) {
            is CareResult.Failure -> return@withLock saved
            is CareResult.Success -> Unit
        }
        val delivery = schoolGateway.deliver(
            com.example.mdd_calender.domain.port.SchoolAlertDto(
                eventId = alert.eventId,
                studentCode = alert.studentCode,
                concernLevel = alert.concernLevel,
                minimalReasonTags = alert.minimalReasonTags.split(',').filter { it.isNotBlank() }.toSet(),
                occurredAtEpochMillis = alert.occurredAtEpochMillis,
                simulated = true,
            ),
            alert.deduplicationKey,
        )
        val attempt = pending.attemptCount + 1
        val deliveryRecord = when (delivery) {
            is CareResult.Failure -> pending.copy(
                attemptCount = attempt,
                status = if (attempt >= 3) DeliveryStatus.FAILED else DeliveryStatus.RETRY_PENDING,
                nextRetryAtEpochMillis = if (attempt >= 3) null else System.currentTimeMillis() + 60_000,
            )
            is CareResult.Success -> if (attempt >= 3 && delivery.value.status == DeliveryStatus.RETRY_PENDING) {
                delivery.value.copy(attemptCount = attempt, status = DeliveryStatus.FAILED, nextRetryAtEpochMillis = null)
            } else delivery.value.copy(attemptCount = attempt)
        }
        systemAlerts.saveDeliveryInternal(deliveryRecord)
    }

    private suspend fun currentPhysiologySignals(
        studentId: String,
        nowEpochMillis: Long,
    ): CareResult<List<com.example.mdd_calender.domain.model.PhysiologySignal>> {
        val currentConsent = when (val result = consent.getForCurrentStudent()) {
            is CareResult.Failure -> return result
            is CareResult.Success -> result.value
        }
        if (currentConsent.state != ConsentState.GRANTED || currentConsent.scopes.isEmpty()) {
            return CareResult.Success(emptyList())
        }
        val request = HealthPullRequest(
            studentId = studentId,
            scopes = currentConsent.scopes,
            fromEpochMillis = nowEpochMillis - HEALTH_SIGNAL_WINDOW_MILLIS,
            toEpochMillis = nowEpochMillis,
            consentRevision = currentConsent.revision,
        )
        val samples = when (val result = systemHealth.samplesForExtraction(request)) {
            is CareResult.Failure -> return result
            is CareResult.Success -> result.value
        }
        val signals = when (val result = RuleBasedPhysiologySignalExtractor().extract(samples, currentConsent)) {
            is CareResult.Failure -> return result
            is CareResult.Success -> result.value
        }
        return when (val stored = systemEvaluations.saveSignalsInternal(signals)) {
            is CareResult.Failure -> stored
            is CareResult.Success -> CareResult.Success(signals)
        }
    }

    suspend fun healthConsentSnapshot(): CareResult<HealthConsentSnapshot> = when (val result = consent.getForCurrentStudent()) {
        is CareResult.Failure -> result
        is CareResult.Success -> CareResult.Success(result.value.toFeatureSnapshot())
    }

    /** Persists consent first, then stores samples against that exact revision. */
    suspend fun updateHealthConsent(metric: HealthMetric, enabled: Boolean): CareResult<HealthConsentSnapshot> {
        val current = when (val result = consent.getForCurrentStudent()) {
            is CareResult.Failure -> return result
            is CareResult.Success -> result.value
        }
        val scope = metric.toDomainScope()
        val updatedScopes = if (enabled) current.scopes + scope else current.scopes - scope
        val providerResult = if (enabled) {
            healthDataProvider.requestAuthorization(setOf(scope))
        } else {
            healthDataProvider.revokeAuthorization(setOf(scope))
        }
        if (providerResult is CareResult.Failure) return providerResult
        val updated = when (val result = consent.updateForCurrentStudent(updatedScopes, updatedScopes.isNotEmpty())) {
            is CareResult.Failure -> return result
            is CareResult.Success -> result.value
        }
        if (updated.scopes.isNotEmpty()) {
            val now = System.currentTimeMillis()
            val request = HealthPullRequest(
                studentId = updated.studentId,
                scopes = updated.scopes,
                fromEpochMillis = now - 14L * 24 * 60 * 60 * 1000,
                toEpochMillis = now,
                consentRevision = updated.revision,
            )
            when (val pulled = healthDataProvider.pull(request)) {
                is CareResult.Failure -> return pulled
                is CareResult.Success -> when (val saved = studentHealth.saveForCurrentStudent(pulled.value)) {
                    is CareResult.Failure -> return saved
                    is CareResult.Success -> Unit
                }
            }
        }
        return CareResult.Success(updated.toFeatureSnapshot())
    }

    suspend fun rawHealthDataView(): CareResult<RawHealthDataView> {
        val now = System.currentTimeMillis()
        return when (val result = studentHealth.listForCurrentStudent(0, now)) {
            is CareResult.Failure -> result
            is CareResult.Success -> CareResult.Success(
                RawHealthDataView.Data(result.value.map(RawHealthSample::toFeatureSample), isDemo = true),
            )
        }
    }

    suspend fun deleteRawHealthData(metrics: Set<HealthMetric>): CareResult<Int> {
        return studentHealth.deleteForCurrentStudent(metrics.map { it.toDomainScope() }.toSet())
    }

    /** Materializes the initial AA schedule idempotently once teacher startWithAa has enrolled the student. */
    suspend fun ensureFollowUpTasks(): CareResult<Unit> {
        val enrollment = when (val result = followUp.currentStudentEnrollment()) {
            is CareResult.Failure -> return result
            is CareResult.Success -> result.value
        }
        val day = 24L * 60 * 60 * 1000
        val tasks = listOf(
            FollowUpTask(
                taskId = "${enrollment.enrollmentId}:check-in",
                studentId = enrollment.studentId,
                type = FollowUpTaskType.CHECK_IN,
                dueAtEpochMillis = enrollment.enrolledAtEpochMillis + day,
                completedAtEpochMillis = null,
                relatedAssessmentId = null,
                status = FollowUpTaskStatus.PENDING,
            ),
            FollowUpTask(
                taskId = "${enrollment.enrollmentId}:assessment-retake",
                studentId = enrollment.studentId,
                type = FollowUpTaskType.ASSESSMENT_RETAKE,
                dueAtEpochMillis = enrollment.enrolledAtEpochMillis + 7 * day,
                completedAtEpochMillis = null,
                relatedAssessmentId = null,
                status = FollowUpTaskStatus.PENDING,
            ),
            FollowUpTask(
                taskId = "${enrollment.enrollmentId}:teacher-review",
                studentId = enrollment.studentId,
                type = FollowUpTaskType.TEACHER_REVIEW,
                dueAtEpochMillis = enrollment.enrolledAtEpochMillis + 3 * day,
                completedAtEpochMillis = null,
                relatedAssessmentId = null,
                status = FollowUpTaskStatus.PENDING,
            ),
        )
        for (task in tasks) when (val result = administration.createFollowUpTask(task)) {
            is CareResult.Failure -> if (result.error !is com.example.mdd_calender.domain.model.CareFailure.Conflict) return result
            is CareResult.Success -> Unit
        }
        return CareResult.Success(Unit)
    }

    private companion object {
        const val HEALTH_SIGNAL_WINDOW_MILLIS = 72L * 60 * 60 * 1000
    }
}

private fun HealthMetric.toDomainScope() = when (this) {
    HealthMetric.HEART_RATE -> HealthScope.HEART_RATE
    HealthMetric.SLEEP -> HealthScope.SLEEP
}

private fun HealthConsent.toFeatureSnapshot() = HealthConsentSnapshot(
    studentId = studentId,
    enabledMetrics = scopes.map {
        when (it) {
            HealthScope.HEART_RATE -> HealthMetric.HEART_RATE
            HealthScope.SLEEP -> HealthMetric.SLEEP
        }
    }.toSet(),
    revision = revision,
    changedAtEpochMillis = changedAtEpochMillis,
    domain = HealthDataDomain.DEMO,
)

private fun RawHealthSample.toFeatureSample() = HealthSample(
    ownerId = ownerId,
    metric = when (type) {
        HealthSampleType.HEART_RATE_BPM -> HealthMetric.HEART_RATE
        HealthSampleType.SLEEP_DURATION_MINUTES, HealthSampleType.SLEEP_QUALITY -> HealthMetric.SLEEP
    },
    observedAtEpochMillis = measuredAtEpochMillis,
    value = if (type == HealthSampleType.SLEEP_DURATION_MINUTES) value / 60.0 else value,
    unit = if (type == HealthSampleType.SLEEP_DURATION_MINUTES) "hours" else unit,
    quality = when (quality) {
        com.example.mdd_calender.domain.model.SampleQuality.POOR -> 0.25
        com.example.mdd_calender.domain.model.SampleQuality.FAIR -> 0.6
        com.example.mdd_calender.domain.model.SampleQuality.GOOD -> 0.9
    },
    domain = HealthDataDomain.DEMO,
)

data class CareChainReceipt(
    val assessmentId: String,
    val evaluationId: String,
    val alertId: String?,
    val delivery: DeliveryRecord?,
)
