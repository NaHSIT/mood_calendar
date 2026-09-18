package com.example.mdd_calender.data.care

import androidx.room.withTransaction
import com.example.mdd_calender.data.MoodDatabase
import com.example.mdd_calender.domain.model.AaEnrollment
import com.example.mdd_calender.domain.model.AaStatus
import com.example.mdd_calender.domain.model.ActorContext
import com.example.mdd_calender.domain.model.ActorRole
import com.example.mdd_calender.domain.model.AlertDisposition
import com.example.mdd_calender.domain.model.AlertEvent
import com.example.mdd_calender.domain.model.AuditEvent
import com.example.mdd_calender.domain.model.CareFailure
import com.example.mdd_calender.domain.model.CareResult
import com.example.mdd_calender.domain.model.ConcernLevel
import com.example.mdd_calender.domain.model.DeliveryRecord
import com.example.mdd_calender.domain.model.DeliveryStatus
import com.example.mdd_calender.domain.model.ExitReview
import com.example.mdd_calender.domain.model.ExitReviewDecision
import com.example.mdd_calender.domain.model.EvaluationResult
import com.example.mdd_calender.domain.model.FollowUpTask
import com.example.mdd_calender.domain.model.FollowUpTaskStatus
import com.example.mdd_calender.domain.model.FollowUpTaskType
import com.example.mdd_calender.domain.model.InterventionCase
import com.example.mdd_calender.domain.model.InterventionStartRequest
import com.example.mdd_calender.domain.model.InterventionStartResult
import com.example.mdd_calender.domain.model.InterventionStatus
import com.example.mdd_calender.domain.model.PhysiologySignal
import com.example.mdd_calender.domain.model.SystemUtcClock
import com.example.mdd_calender.domain.model.TeacherAlertSummary
import com.example.mdd_calender.domain.model.UtcClock
import com.example.mdd_calender.domain.port.AlertRepository
import com.example.mdd_calender.domain.port.AuditRepository
import com.example.mdd_calender.domain.port.CareAdministrationPort
import com.example.mdd_calender.domain.port.FollowUpRepository
import com.example.mdd_calender.domain.port.EvaluationRepository
import com.example.mdd_calender.domain.port.InterventionRepository
import com.example.mdd_calender.domain.port.SessionProvider
import com.example.mdd_calender.domain.policy.AaExitPolicy
import com.example.mdd_calender.security.AndroidKeystoreCipher
import com.example.mdd_calender.security.AuthenticatedCipher
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.util.UUID

private suspend fun SessionProvider.actorWithRole(role: ActorRole): CareResult<ActorContext> = when (val result = currentActor()) {
    is CareResult.Failure -> result
    is CareResult.Success -> if (result.value.role == role) result else CareResult.Failure(CareFailure.Forbidden("${role.name} session required"))
}

private fun String.utf8() = toByteArray(StandardCharsets.UTF_8)
private fun ByteArray.utf8() = toString(StandardCharsets.UTF_8)
private fun Set<String>.store() = sorted().joinToString(",")
private fun String.restoreSet() = if (isBlank()) emptySet() else split(',').toSet()

class RoomAlertRepository(
    private val dao: CareDao,
    private val session: SessionProvider,
    private val cipher: AuthenticatedCipher,
) : AlertRepository {
    override suspend fun saveInternal(event: AlertEvent): CareResult<AlertEvent> {
        val actor = when (val result = session.actorWithRole(ActorRole.SYSTEM)) {
            is CareResult.Failure -> return result
            is CareResult.Success -> result.value
        }
        val student = dao.student(event.studentId, actor.dataDomain.name)
            ?: return CareResult.Failure(CareFailure.NotFound("student", event.studentId))
        if (student.studentCode != event.studentCode) return CareResult.Failure(CareFailure.InvalidInput("Student code does not match student"))
        val entity = event.toEntity(actor.dataDomain.name)
        if (dao.insertAlert(entity) == -1L) {
            return CareResult.Success(dao.alertByDeduplicationKey(event.deduplicationKey, actor.dataDomain.name)!!.toModel())
        }
        return CareResult.Success(event)
    }

    override suspend fun listTeacherSummaries(): CareResult<List<TeacherAlertSummary>> {
        val actor = when (val result = session.actorWithRole(ActorRole.TEACHER)) {
            is CareResult.Failure -> return result
            is CareResult.Success -> result.value
        }
        return CareResult.Success(dao.alertsForTeacher(actor.actorId, actor.dataDomain.name).map(AlertEntity::toSummary))
    }

    override suspend fun getTeacherSummary(eventId: String): CareResult<TeacherAlertSummary> {
        val actor = when (val result = session.actorWithRole(ActorRole.TEACHER)) {
            is CareResult.Failure -> return result
            is CareResult.Success -> result.value
        }
        val value = dao.alertForTeacher(eventId, actor.actorId, actor.dataDomain.name) ?: run {
            if (dao.alert(eventId, actor.dataDomain.name) != null) return CareResult.Failure(CareFailure.Forbidden("Teacher is not assigned to this student"))
            return CareResult.Failure(CareFailure.NotFound("alert", eventId))
        }
        return CareResult.Success(value.toSummary())
    }

    override suspend fun updateTeacherDisposition(eventId: String, disposition: AlertDisposition): CareResult<TeacherAlertSummary> {
        val actor = when (val result = session.actorWithRole(ActorRole.TEACHER)) {
            is CareResult.Failure -> return result
            is CareResult.Success -> result.value
        }
        val entity = dao.alertForTeacher(eventId, actor.actorId, actor.dataDomain.name) ?: run {
            if (dao.alert(eventId, actor.dataDomain.name) != null) return CareResult.Failure(CareFailure.Forbidden("Teacher is not assigned to this student"))
            return CareResult.Failure(CareFailure.NotFound("alert", eventId))
        }
        val current = AlertDisposition.valueOf(entity.disposition)
        val allowed = when (current) {
            AlertDisposition.NEW -> disposition == AlertDisposition.ACKNOWLEDGED
            AlertDisposition.ACKNOWLEDGED -> disposition in setOf(AlertDisposition.IN_PROGRESS, AlertDisposition.CLOSED)
            AlertDisposition.IN_PROGRESS -> disposition == AlertDisposition.CLOSED
            AlertDisposition.CLOSED -> disposition == AlertDisposition.CLOSED
        }
        if (!allowed) return CareResult.Failure(CareFailure.Conflict("Invalid alert disposition transition: $current -> $disposition"))
        val updated = entity.copy(disposition = disposition.name)
        dao.updateAlert(updated)
        return CareResult.Success(updated.toSummary())
    }

    override suspend fun saveDeliveryInternal(delivery: DeliveryRecord): CareResult<DeliveryRecord> {
        val actor = when (val result = session.actorWithRole(ActorRole.SYSTEM)) {
            is CareResult.Failure -> return result
            is CareResult.Success -> result.value
        }
        if (dao.alert(delivery.alertId, actor.dataDomain.name) == null) return CareResult.Failure(CareFailure.NotFound("alert", delivery.alertId))
        val existing = dao.deliveryByIdempotencyKey(delivery.idempotencyKey, actor.dataDomain.name)
        val stableDeliveryId = existing?.deliveryId ?: delivery.deliveryId
        val encryptedReceipt = delivery.receipt?.let {
            when (val encrypted = cipher.encrypt(it.utf8(), AndroidKeystoreCipher.aad("delivery", stableDeliveryId))) {
                is CareResult.Failure -> return encrypted
                is CareResult.Success -> encrypted.value
            }
        }
        val stable = delivery.copy(deliveryId = stableDeliveryId)
        val entity = stable.toEntity(actor.dataDomain.name, encryptedReceipt)
        if (existing == null) dao.insertDelivery(entity) else dao.updateDelivery(entity)
        return CareResult.Success(stable)
    }

    override suspend fun getDeliveryInternal(idempotencyKey: String): CareResult<DeliveryRecord> {
        val actor = when (val result = session.actorWithRole(ActorRole.SYSTEM)) {
            is CareResult.Failure -> return result
            is CareResult.Success -> result.value
        }
        val entity = dao.deliveryByIdempotencyKey(idempotencyKey, actor.dataDomain.name)
            ?: return CareResult.Failure(CareFailure.NotFound("delivery", idempotencyKey))
        return entity.toModel(cipher)
    }
}

class RoomEvaluationRepository(
    private val dao: CareDao,
    private val session: SessionProvider,
) : EvaluationRepository {
    override suspend fun saveSignalsInternal(signals: List<PhysiologySignal>): CareResult<Unit> {
        val actor = when (val result = session.actorWithRole(ActorRole.SYSTEM)) { is CareResult.Failure -> return result; is CareResult.Success -> result.value }
        if (signals.any { dao.student(it.studentId, actor.dataDomain.name) == null }) return CareResult.Failure(CareFailure.NotFound("student", "signal owner"))
        dao.upsertSignals(signals.map { signal ->
            SignalEntity(signal.signalId, signal.studentId, actor.dataDomain.name, signal.type.name, signal.windowStartEpochMillis, signal.windowEndEpochMillis, signal.quality.name, signal.validUntilEpochMillis, signal.ruleVersion, signal.consentRevision, signal.simulated)
        })
        return CareResult.Success(Unit)
    }

    override suspend fun saveEvaluationInternal(result: EvaluationResult): CareResult<EvaluationResult> {
        val actor = when (val value = session.actorWithRole(ActorRole.SYSTEM)) { is CareResult.Failure -> return value; is CareResult.Success -> value.value }
        if (dao.student(result.studentId, actor.dataDomain.name) == null) return CareResult.Failure(CareFailure.NotFound("student", result.studentId))
        dao.upsertEvaluation(EvaluationEntity(result.evaluationId, result.studentId, actor.dataDomain.name, result.assessmentIds.joinToString(","), result.baseConcernLevel.name, result.dispositionPriority.name, result.auxiliaryTags.store(), result.dataSufficiency.name, result.ruleVersion, result.generatedAtEpochMillis))
        return CareResult.Success(result)
    }
}

private fun AlertEvent.toEntity(domain: String) = AlertEntity(
    eventId, studentId, studentCode, domain, concernLevel.name, minimalReasonTags.store(), occurredAtEpochMillis,
    ruleVersion, evaluationId, deduplicationKey, AlertDisposition.NEW.name,
)
private fun AlertEntity.toModel() = AlertEvent(eventId, studentId, studentCode, ConcernLevel.valueOf(concernLevel), minimalReasonTags.restoreSet(), occurredAtEpochMillis, ruleVersion, evaluationId, deduplicationKey)
private fun AlertEntity.toSummary() = TeacherAlertSummary(eventId, studentCode, ConcernLevel.valueOf(concernLevel), minimalReasonTags.restoreSet(), occurredAtEpochMillis, AlertDisposition.valueOf(disposition))
private fun DeliveryRecord.toEntity(domain: String, encrypted: String?) = DeliveryEntity(deliveryId, alertId, recipientId, domain, attemptCount, nextRetryAtEpochMillis, simulated, idempotencyKey, status.name, encrypted)
private fun DeliveryEntity.toModel(cipher: AuthenticatedCipher): CareResult<DeliveryRecord> {
    val receipt = encryptedReceipt?.let {
        when (val decoded = cipher.decrypt(it, AndroidKeystoreCipher.aad("delivery", deliveryId))) {
            is CareResult.Failure -> return decoded
            is CareResult.Success -> decoded.value.utf8()
        }
    }
    return CareResult.Success(DeliveryRecord(deliveryId, alertId, recipientId, attemptCount, nextRetryAtEpochMillis, simulated, idempotencyKey, DeliveryStatus.valueOf(status), receipt))
}

class RoomInterventionRepository(
    private val database: MoodDatabase,
    private val session: SessionProvider,
    private val cipher: AuthenticatedCipher,
    private val clock: UtcClock = SystemUtcClock,
) : InterventionRepository {
    private val dao get() = database.careDao()

    override suspend fun listAssignedToCurrentTeacher(): CareResult<List<InterventionCase>> {
        val actor = when (val result = session.actorWithRole(ActorRole.TEACHER)) {
            is CareResult.Failure -> return result
            is CareResult.Success -> result.value
        }
        return decodeInterventions(dao.interventionsForTeacher(actor.actorId, actor.dataDomain.name))
    }

    override suspend fun getAssignedToCurrentTeacher(caseId: String): CareResult<InterventionCase> {
        val actor = when (val result = session.actorWithRole(ActorRole.TEACHER)) {
            is CareResult.Failure -> return result
            is CareResult.Success -> result.value
        }
        val entity = dao.interventionForTeacher(caseId, actor.actorId, actor.dataDomain.name) ?: run {
            if (dao.intervention(caseId, actor.dataDomain.name) != null) return CareResult.Failure(CareFailure.Forbidden("Teacher is not responsible for this intervention"))
            return CareResult.Failure(CareFailure.NotFound("intervention", caseId))
        }
        return entity.toModel(cipher)
    }

    override suspend fun acknowledge(caseId: String, idempotencyKey: String): CareResult<InterventionCase> {
        val actor = when (val result = session.actorWithRole(ActorRole.TEACHER)) {
            is CareResult.Failure -> return result
            is CareResult.Success -> result.value
        }
        val entity = dao.interventionForTeacher(caseId, actor.actorId, actor.dataDomain.name) ?: run {
            if (dao.intervention(caseId, actor.dataDomain.name) != null) return CareResult.Failure(CareFailure.Forbidden("Teacher is not responsible for this intervention"))
            return CareResult.Failure(CareFailure.NotFound("intervention", caseId))
        }
        if (entity.lastIdempotencyKey == idempotencyKey) return entity.toModel(cipher)
        if (entity.status != InterventionStatus.PENDING_CONFIRMATION.name) return CareResult.Failure(CareFailure.Conflict("Intervention cannot be acknowledged from ${entity.status}"))
        val updated = entity.copy(status = InterventionStatus.CONFIRMED.name, lastIdempotencyKey = idempotencyKey)
        dao.updateIntervention(updated)
        return updated.toModel(cipher)
    }

    override suspend fun startWithAa(request: InterventionStartRequest): CareResult<InterventionStartResult> {
        val actor = when (val result = session.actorWithRole(ActorRole.TEACHER)) {
            is CareResult.Failure -> return result
            is CareResult.Success -> result.value
        }
        return try {
            database.withTransaction {
                val original = dao.interventionForTeacher(request.caseId, actor.actorId, actor.dataDomain.name)
                    ?: return@withTransaction if (dao.intervention(request.caseId, actor.dataDomain.name) != null) CareResult.Failure(CareFailure.Forbidden("Teacher is not responsible for this intervention")) else CareResult.Failure(CareFailure.NotFound("intervention", request.caseId))
                val existingEnrollment = dao.activeEnrollment(original.studentId, actor.dataDomain.name)
                if (original.lastIdempotencyKey == request.idempotencyKey && existingEnrollment != null) {
                    dao.ensureEnrollmentTasks(existingEnrollment)
                    val intervention = when (val mapped = original.toModel(cipher)) { is CareResult.Failure -> return@withTransaction mapped; is CareResult.Success -> mapped.value }
                    val enrollment = when (val mapped = existingEnrollment.toModel(cipher)) { is CareResult.Failure -> return@withTransaction mapped; is CareResult.Success -> mapped.value }
                    return@withTransaction CareResult.Success(InterventionStartResult(intervention, enrollment, false))
                }
                if (original.status !in setOf(InterventionStatus.CONFIRMED.name, InterventionStatus.ACTIVE.name)) return@withTransaction CareResult.Failure(CareFailure.Conflict("Intervention must be confirmed before start"))
                val updated = original.copy(status = InterventionStatus.ACTIVE.name, startedAtEpochMillis = original.startedAtEpochMillis ?: clock.nowEpochMillis(), lastIdempotencyKey = request.idempotencyKey)
                dao.updateIntervention(updated)
                var created = false
                val enrollmentEntity = existingEnrollment ?: AaEnrollmentEntity(
                    UUID.randomUUID().toString(), original.studentId, original.caseId, actor.dataDomain.name,
                    clock.nowEpochMillis(), AaStatus.TRACKING.name, "ACTIVE", request.followUpPolicyVersion, null,
                ).also {
                    created = dao.insertEnrollment(it) != -1L
                }.let { if (created) it else dao.activeEnrollment(original.studentId, actor.dataDomain.name)!! }
                dao.ensureEnrollmentTasks(enrollmentEntity)
                dao.alert(original.alertId, actor.dataDomain.name)?.let { dao.updateAlert(it.copy(disposition = AlertDisposition.IN_PROGRESS.name)) }
                val intervention = when (val mapped = updated.toModel(cipher)) { is CareResult.Failure -> return@withTransaction mapped; is CareResult.Success -> mapped.value }
                val enrollment = when (val mapped = enrollmentEntity.toModel(cipher)) { is CareResult.Failure -> return@withTransaction mapped; is CareResult.Success -> mapped.value }
                CareResult.Success(InterventionStartResult(intervention, enrollment, created))
            }
        } catch (_: Exception) {
            CareResult.Failure(CareFailure.TemporarilyUnavailable("Intervention and AA transaction failed"))
        }
    }

    private fun decodeInterventions(values: List<InterventionEntity>): CareResult<List<InterventionCase>> {
        val output = mutableListOf<InterventionCase>()
        for (value in values) when (val mapped = value.toModel(cipher)) { is CareResult.Failure -> return mapped; is CareResult.Success -> output += mapped.value }
        return CareResult.Success(output)
    }

    override suspend fun closeWithNote(caseId: String, note: String): CareResult<InterventionCase> {
        val actor = when (val result = session.actorWithRole(ActorRole.TEACHER)) { is CareResult.Failure -> return result; is CareResult.Success -> result.value }
        if (note.isBlank()) return CareResult.Failure(CareFailure.InvalidInput("请填写联系及处理记录"))
        val encrypted = when (val result = cipher.encrypt(note.utf8(), AndroidKeystoreCipher.aad("intervention", caseId))) { is CareResult.Failure -> return result; is CareResult.Success -> result.value }
        return database.withTransaction {
            val entity = dao.interventionForTeacher(caseId, actor.actorId, actor.dataDomain.name)
                ?: return@withTransaction CareResult.Failure(CareFailure.Forbidden("Teacher is not responsible for this intervention"))
            if (!dao.isAssigned(actor.actorId, entity.studentId, actor.dataDomain.name)) return@withTransaction CareResult.Failure(CareFailure.Forbidden("Teacher assignment changed"))
            if (entity.status == InterventionStatus.CLOSED.name) return@withTransaction entity.toModel(cipher)
            if (entity.status != InterventionStatus.ACTIVE.name) return@withTransaction CareResult.Failure(CareFailure.Conflict("请先启动干预"))
            val updated = entity.copy(status = InterventionStatus.CLOSED.name, encryptedMinimalActionNote = encrypted)
            dao.updateIntervention(updated)
            dao.alert(entity.alertId, actor.dataDomain.name)?.let { dao.updateAlert(it.copy(disposition = AlertDisposition.CLOSED.name)) }
            dao.activeEnrollment(entity.studentId, actor.dataDomain.name)?.let { enrollment ->
                dao.ensureEnrollmentTasks(enrollment)
                dao.completeTaskChecked("${enrollment.enrollmentId}:teacher-review", entity.studentId, actor.dataDomain.name, FollowUpTaskType.TEACHER_REVIEW.name, clock.nowEpochMillis())
            }
            updated.toModel(cipher)
        }
    }
}

private fun InterventionEntity.toModel(cipher: AuthenticatedCipher): CareResult<InterventionCase> {
    val note = encryptedMinimalActionNote?.let {
        when (val decoded = cipher.decrypt(it, AndroidKeystoreCipher.aad("intervention", caseId))) {
            is CareResult.Failure -> return decoded
            is CareResult.Success -> decoded.value.utf8()
        }
    }
    return CareResult.Success(InterventionCase(caseId, studentId, assignedTeacherId, alertId, InterventionStatus.valueOf(status), startedAtEpochMillis, note))
}

class RoomFollowUpRepository(
    private val dao: CareDao,
    private val session: SessionProvider,
    private val cipher: AuthenticatedCipher,
    private val clock: UtcClock = SystemUtcClock,
    private val exitPolicy: AaExitPolicy = AaExitPolicy(),
) : FollowUpRepository {
    override suspend fun currentStudentEnrollment(): CareResult<AaEnrollment> {
        val actor = when (val result = session.actorWithRole(ActorRole.STUDENT)) { is CareResult.Failure -> return result; is CareResult.Success -> result.value }
        val value = dao.activeEnrollment(actor.actorId, actor.dataDomain.name) ?: return CareResult.Failure(CareFailure.NotFound("active AA enrollment", actor.actorId))
        return value.toModel(cipher)
    }

    override suspend fun currentStudentTasks(): CareResult<List<FollowUpTask>> {
        val actor = when (val result = session.actorWithRole(ActorRole.STUDENT)) { is CareResult.Failure -> return result; is CareResult.Success -> result.value }
        return CareResult.Success(dao.tasksForStudent(actor.actorId, actor.dataDomain.name).map(FollowUpTaskEntity::toModel))
    }

    override suspend fun completeCurrentStudentTask(taskId: String, completedAtEpochMillis: Long): CareResult<FollowUpTask> {
        val actor = when (val result = session.actorWithRole(ActorRole.STUDENT)) { is CareResult.Failure -> return result; is CareResult.Success -> result.value }
        val task = dao.taskForStudent(taskId, actor.actorId, actor.dataDomain.name)
            ?: return CareResult.Failure(CareFailure.NotFound("follow-up task", taskId))
        if (task.type != FollowUpTaskType.CHECK_IN.name) return CareResult.Failure(CareFailure.Forbidden("此任务需量表复测或教师处理，不能直接打卡完成"))
        val updated = dao.completeTaskChecked(taskId, actor.actorId, actor.dataDomain.name, task.type, clock.nowEpochMillis())
            ?: return CareResult.Failure(CareFailure.Conflict("任务已取消或当前未处于随访中"))
        return CareResult.Success(updated.toModel())
    }

    override suspend fun completeAssessmentForCurrentStudent(taskId: String, assessmentId: String): CareResult<FollowUpTask> {
        val actor = when (val result = session.actorWithRole(ActorRole.STUDENT)) { is CareResult.Failure -> return result; is CareResult.Success -> result.value }
        val updated = dao.completeTaskChecked(taskId, actor.actorId, actor.dataDomain.name, FollowUpTaskType.ASSESSMENT_RETAKE.name, clock.nowEpochMillis(), assessmentId)
            ?: return CareResult.Failure(CareFailure.InvalidInput("复测必须已保存、属于本人且在本次随访开始之后"))
        return CareResult.Success(updated.toModel())
    }

    override suspend fun requestExit(reason: String): CareResult<AaEnrollment> {
        val actor = when (val result = session.actorWithRole(ActorRole.STUDENT)) { is CareResult.Failure -> return result; is CareResult.Success -> result.value }
        if (reason.isBlank()) return CareResult.Failure(CareFailure.InvalidInput("Exit reason is required"))
        val entity = dao.activeEnrollment(actor.actorId, actor.dataDomain.name) ?: return CareResult.Failure(CareFailure.NotFound("active AA enrollment", actor.actorId))
        if (entity.status != AaStatus.TRACKING.name) return CareResult.Failure(CareFailure.Conflict("Exit request is already pending or tracking has ended"))
        val now = clock.nowEpochMillis()
        if (now - entity.enrolledAtEpochMillis < exitPolicy.minimumObservationMillis) {
            return CareResult.Failure(CareFailure.Conflict("尚未达到 AA 最短观察期"))
        }
        if (dao.unresolvedSafetyAlertCount(actor.actorId, actor.dataDomain.name) > 0) {
            return CareResult.Failure(CareFailure.Conflict("仍有未处理的安全关注，暂不能退出 AA"))
        }
        if (!dao.hasStableReassessment(actor.actorId, actor.dataDomain.name, entity.enrolledAtEpochMillis, now)) {
            return CareResult.Failure(CareFailure.Conflict("需要近期低关注复测结果，且复测任务已完成"))
        }
        if (exitPolicy.requireAllFollowUpTasksResolved && dao.tasksForStudent(actor.actorId, actor.dataDomain.name).any {
                it.dueAtEpochMillis <= now && it.status != FollowUpTaskStatus.COMPLETED.name && it.status != FollowUpTaskStatus.CANCELLED.name
            }
        ) {
            return CareResult.Failure(CareFailure.Conflict("请先完成已到期的随访任务"))
        }
        val review = ExitReview(now, reason, null, null, null, null)
        val encrypted = when (val result = encryptReview(review, entity.enrollmentId, cipher)) { is CareResult.Failure -> return result; is CareResult.Success -> result.value }
        val updated = entity.copy(status = AaStatus.EXIT_REVIEW_PENDING.name, encryptedExitReview = encrypted)
        dao.updateEnrollment(updated)
        return updated.toModel(cipher)
    }

    override suspend fun listAssignedEnrollments(): CareResult<List<AaEnrollment>> {
        val actor = when (val result = session.actorWithRole(ActorRole.TEACHER)) { is CareResult.Failure -> return result; is CareResult.Success -> result.value }
        val output = mutableListOf<AaEnrollment>()
        for (entity in dao.enrollmentsForTeacher(actor.actorId, actor.dataDomain.name)) when (val mapped = entity.toModel(cipher)) { is CareResult.Failure -> return mapped; is CareResult.Success -> output += mapped.value }
        return CareResult.Success(output)
    }

    override suspend fun reviewExit(enrollmentId: String, decision: ExitReviewDecision, note: String): CareResult<AaEnrollment> {
        if (note.isBlank()) return CareResult.Failure(CareFailure.InvalidInput("请填写审核备注"))
        val actor = when (val result = session.actorWithRole(ActorRole.TEACHER)) { is CareResult.Failure -> return result; is CareResult.Success -> result.value }
        val entity = dao.enrollment(enrollmentId, actor.dataDomain.name) ?: return CareResult.Failure(CareFailure.NotFound("AA enrollment", enrollmentId))
        if (!dao.isAssigned(actor.actorId, entity.studentId, actor.dataDomain.name)) return CareResult.Failure(CareFailure.Forbidden("Teacher is not assigned to this student"))
        val current = when (val mapped = entity.toModel(cipher)) { is CareResult.Failure -> return mapped; is CareResult.Success -> mapped.value }
        val pending = current.exitReview ?: return CareResult.Failure(CareFailure.Conflict("No exit request is pending"))
        if (current.status != AaStatus.EXIT_REVIEW_PENDING) return CareResult.Failure(CareFailure.Conflict("Enrollment is not pending exit review"))
        val reviewed = pending.copy(reviewedAtEpochMillis = clock.nowEpochMillis(), reviewerId = actor.actorId, decision = decision, reviewNote = note)
        val encrypted = when (val result = encryptReview(reviewed, enrollmentId, cipher)) { is CareResult.Failure -> return result; is CareResult.Success -> result.value }
        return when (dao.finalizeExitReview(enrollmentId, actor.actorId, actor.dataDomain.name, decision == ExitReviewDecision.APPROVED, encrypted, clock.nowEpochMillis(), exitPolicy.minimumObservationMillis, exitPolicy.requireAllFollowUpTasksResolved)) {
            CareDao.EXIT_UPDATED -> dao.enrollment(enrollmentId, actor.dataDomain.name)!!.toModel(cipher)
            CareDao.EXIT_NOT_FOUND -> CareResult.Failure(CareFailure.NotFound("AA enrollment", enrollmentId))
            CareDao.EXIT_FORBIDDEN -> CareResult.Failure(CareFailure.Forbidden("Teacher is not assigned to this student"))
            CareDao.EXIT_INVALID_STATE -> CareResult.Failure(CareFailure.Conflict("Enrollment is not pending exit review"))
            CareDao.EXIT_SAFETY_BLOCKED -> CareResult.Failure(CareFailure.Conflict("Unresolved safety concern blocks AA exit"))
            CareDao.EXIT_NOT_STABLE -> CareResult.Failure(CareFailure.Conflict("观察期或最近复测不满足退出条件"))
            CareDao.EXIT_TASKS_PENDING -> CareResult.Failure(CareFailure.Conflict("仍有未完成的到期随访任务"))
            else -> CareResult.Failure(CareFailure.TemporarilyUnavailable("AA exit review could not be saved"))
        }
    }
}

private fun FollowUpTaskEntity.toModel() = FollowUpTask(taskId, studentId, FollowUpTaskType.valueOf(type), dueAtEpochMillis, completedAtEpochMillis, relatedAssessmentId, FollowUpTaskStatus.valueOf(status))
private fun AaEnrollmentEntity.toModel(cipher: AuthenticatedCipher): CareResult<AaEnrollment> {
    val review = encryptedExitReview?.let { when (val decoded = decryptReview(it, enrollmentId, cipher)) { is CareResult.Failure -> return decoded; is CareResult.Success -> decoded.value } }
    return CareResult.Success(AaEnrollment(enrollmentId, studentId, interventionId, enrolledAtEpochMillis, AaStatus.valueOf(status), followUpPolicyVersion, review))
}
private fun encryptReview(value: ExitReview, id: String, cipher: AuthenticatedCipher): CareResult<String> {
    val json = JSONObject().put("requestedAt", value.requestedAtEpochMillis).put("reason", value.reason)
        .put("reviewedAt", value.reviewedAtEpochMillis).put("reviewerId", value.reviewerId)
        .put("decision", value.decision?.name).put("reviewNote", value.reviewNote).toString()
    return cipher.encrypt(json.utf8(), AndroidKeystoreCipher.aad("aa-exit", id))
}
private fun decryptReview(value: String, id: String, cipher: AuthenticatedCipher): CareResult<ExitReview> = when (val decoded = cipher.decrypt(value, AndroidKeystoreCipher.aad("aa-exit", id))) {
    is CareResult.Failure -> decoded
    is CareResult.Success -> runCatching {
        val json = JSONObject(decoded.value.utf8())
        ExitReview(json.getLong("requestedAt"), json.getString("reason"), json.optLongOrNull("reviewedAt"), json.optStringOrNull("reviewerId"), json.optStringOrNull("decision")?.let(ExitReviewDecision::valueOf), json.optStringOrNull("reviewNote"))
    }.fold(onSuccess = { CareResult.Success(it) }, onFailure = { CareResult.Failure(CareFailure.CryptographyFailure("Decrypted exit review is invalid")) })
}
private fun JSONObject.optLongOrNull(name: String): Long? = if (isNull(name)) null else getLong(name)
private fun JSONObject.optStringOrNull(name: String): String? = if (isNull(name)) null else getString(name)

class RoomAuditRepository(private val dao: CareDao, private val session: SessionProvider) : AuditRepository {
    override suspend fun append(event: AuditEvent): CareResult<Unit> {
        val actor = when (val result = session.currentActor()) { is CareResult.Failure -> return result; is CareResult.Success -> result.value }
        if (event.actorId != actor.actorId) return CareResult.Failure(CareFailure.Forbidden("Audit actor must match trusted session"))
        dao.insertAudit(AuditEntity(event.auditId, event.actorId, actor.dataDomain.name, event.action, event.objectCode, event.result, event.occurredAtEpochMillis))
        return CareResult.Success(Unit)
    }
}

class RoomCareAdministration(
    private val dao: CareDao,
    private val session: SessionProvider,
    private val cipher: AuthenticatedCipher,
) : CareAdministrationPort {
    private suspend fun system(): CareResult<ActorContext> = session.actorWithRole(ActorRole.SYSTEM)
    override suspend fun upsertStudent(studentId: String, studentCode: String): CareResult<Unit> {
        val actor = when (val result = system()) { is CareResult.Failure -> return result; is CareResult.Success -> result.value }
        dao.upsertStudent(StudentEntity(studentId, studentCode, actor.dataDomain.name)); return CareResult.Success(Unit)
    }
    override suspend fun assignTeacher(studentId: String, teacherId: String): CareResult<Unit> {
        val actor = when (val result = system()) { is CareResult.Failure -> return result; is CareResult.Success -> result.value }
        if (dao.student(studentId, actor.dataDomain.name) == null) return CareResult.Failure(CareFailure.NotFound("student", studentId))
        dao.insertAssignment(TeacherAssignmentEntity(teacherId, studentId, actor.dataDomain.name)); return CareResult.Success(Unit)
    }
    override suspend fun createIntervention(case: InterventionCase): CareResult<InterventionCase> {
        val actor = when (val result = system()) { is CareResult.Failure -> return result; is CareResult.Success -> result.value }
        if (!dao.isAssigned(case.assignedTeacherId, case.studentId, actor.dataDomain.name)) return CareResult.Failure(CareFailure.InvalidInput("Teacher must be assigned before intervention creation"))
        val alert = dao.alert(case.alertId, actor.dataDomain.name) ?: return CareResult.Failure(CareFailure.NotFound("alert", case.alertId))
        if (alert.studentId != case.studentId) return CareResult.Failure(CareFailure.InvalidInput("Alert belongs to another student"))
        val note = case.minimalActionNote?.let { when (val result = cipher.encrypt(it.utf8(), AndroidKeystoreCipher.aad("intervention", case.caseId))) { is CareResult.Failure -> return result; is CareResult.Success -> result.value } }
        val entity = InterventionEntity(case.caseId, case.studentId, case.assignedTeacherId, case.alertId, actor.dataDomain.name, case.status.name, case.startedAtEpochMillis, note, null)
        if (dao.insertIntervention(entity) == -1L) return CareResult.Failure(CareFailure.Conflict("Intervention already exists"))
        return CareResult.Success(case)
    }
    override suspend fun createFollowUpTask(task: FollowUpTask): CareResult<FollowUpTask> {
        val actor = when (val result = system()) { is CareResult.Failure -> return result; is CareResult.Success -> result.value }
        if (dao.student(task.studentId, actor.dataDomain.name) == null) return CareResult.Failure(CareFailure.NotFound("student", task.studentId))
        val entity = FollowUpTaskEntity(task.taskId, task.studentId, actor.dataDomain.name, task.type.name, task.dueAtEpochMillis, task.completedAtEpochMillis, task.relatedAssessmentId, task.status.name)
        if (dao.insertTask(entity) == -1L) return CareResult.Failure(CareFailure.Conflict("Follow-up task already exists"))
        return CareResult.Success(task)
    }
}
