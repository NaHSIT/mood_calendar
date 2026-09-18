package com.example.mdd_calender.feature.teacher

import com.example.mdd_calender.domain.model.AlertDisposition
import com.example.mdd_calender.domain.model.AaEnrollment
import com.example.mdd_calender.domain.model.AaStatus
import com.example.mdd_calender.domain.model.CareFailure
import com.example.mdd_calender.domain.model.CareResult
import com.example.mdd_calender.domain.model.ExitReviewDecision
import com.example.mdd_calender.domain.model.InterventionCase
import com.example.mdd_calender.domain.model.InterventionStartRequest
import com.example.mdd_calender.domain.model.InterventionStatus
import com.example.mdd_calender.domain.model.TeacherAlertSummary
import com.example.mdd_calender.domain.port.AlertRepository
import com.example.mdd_calender.domain.port.AuditRepository
import com.example.mdd_calender.domain.port.FollowUpRepository
import com.example.mdd_calender.domain.port.InterventionRepository
import com.example.mdd_calender.domain.port.SchoolAlertDto
import com.example.mdd_calender.domain.port.SchoolPlatformGateway
import com.example.mdd_calender.domain.port.SessionProvider
import java.util.UUID

data class TeacherInboxItem(
    val summary: TeacherAlertSummary,
    val intervention: InterventionCase?,
)

data class TeacherAlertDetails(
    val item: TeacherInboxItem,
    val delivery: com.example.mdd_calender.domain.model.DeliveryRecord? = null,
)

data class TeacherExitReviewItem(
    val enrollmentId: String,
    val requestedAtEpochMillis: Long,
    val reason: String,
)

class TeacherWorkbenchService(
    private val alerts: AlertRepository,
    private val interventions: InterventionRepository,
    private val followUps: FollowUpRepository,
    private val gateway: SchoolPlatformGateway,
    private val session: SessionProvider,
    private val audit: AuditRepository? = null,
    private val clock: () -> Long = System::currentTimeMillis,
    private val deliveryDispatcher: (suspend (String) -> CareResult<com.example.mdd_calender.domain.model.DeliveryRecord>)? = null,
) {
    suspend fun loadPendingExitReviews(): CareResult<List<TeacherExitReviewItem>> =
        when (val result = followUps.listAssignedEnrollments()) {
            is CareResult.Failure -> result
            is CareResult.Success -> CareResult.Success(
                result.value
                    .filter { it.status == AaStatus.EXIT_REVIEW_PENDING && it.exitReview != null }
                    .map { enrollment ->
                        val review = requireNotNull(enrollment.exitReview)
                        TeacherExitReviewItem(
                            enrollmentId = enrollment.enrollmentId,
                            requestedAtEpochMillis = review.requestedAtEpochMillis,
                            reason = review.reason,
                        )
                    }
                    .sortedBy { it.requestedAtEpochMillis },
            )
        }

    suspend fun loadInbox(): CareResult<List<TeacherInboxItem>> {
        val summaries = when (val result = alerts.listTeacherSummaries()) {
            is CareResult.Failure -> return result
            is CareResult.Success -> result.value
        }
        val cases = when (val result = interventions.listAssignedToCurrentTeacher()) {
            is CareResult.Failure -> return result
            is CareResult.Success -> result.value
        }
        val byAlert = cases.associateBy { it.alertId }
        return CareResult.Success(summaries.map { summary ->
            val intervention = byAlert[summary.eventId]
            TeacherInboxItem(summary.withInterventionDisposition(intervention), intervention)
        })
    }

    suspend fun loadDetails(eventId: String): CareResult<TeacherAlertDetails> {
        val summary = when (val result = alerts.getTeacherSummary(eventId)) {
            is CareResult.Failure -> return result
            is CareResult.Success -> result.value
        }
        val intervention = when (val result = interventions.listAssignedToCurrentTeacher()) {
            is CareResult.Failure -> return result
            is CareResult.Success -> result.value.firstOrNull { it.alertId == eventId }
        }
        return CareResult.Success(TeacherAlertDetails(TeacherInboxItem(summary.withInterventionDisposition(intervention), intervention)))
    }

    suspend fun acknowledge(caseId: String): CareResult<InterventionCase> {
        val actor = when (val result = session.currentActor()) {
            is CareResult.Failure -> return result
            is CareResult.Success -> result.value
        }
        val result = interventions.acknowledge(caseId, "ack:${actor.actorId}:$caseId")
        if (result is CareResult.Success) recordAudit(actor.actorId, "teacher_acknowledge", caseId, "success")
        return result
    }

    suspend fun startIntervention(caseId: String, policyVersion: String): CareResult<InterventionStartResultView> {
        val actor = when (val result = session.currentActor()) {
            is CareResult.Failure -> return result
            is CareResult.Success -> result.value
        }
        val current = when (val result = interventions.getAssignedToCurrentTeacher(caseId)) {
            is CareResult.Failure -> return result
            is CareResult.Success -> result.value
        }
        val confirmed = if (current.status == InterventionStatus.PENDING_CONFIRMATION) {
            when (val result = acknowledge(caseId)) {
                is CareResult.Failure -> return result
                is CareResult.Success -> result.value
            }
        } else current
        val result = interventions.startWithAa(
            InterventionStartRequest(
                caseId = confirmed.caseId,
                followUpPolicyVersion = policyVersion,
                idempotencyKey = "start:${actor.actorId}:${confirmed.caseId}",
            ),
        )
        return when (result) {
            is CareResult.Failure -> result
            is CareResult.Success -> {
                recordAudit(actor.actorId, "teacher_start_intervention", caseId, "success")
                CareResult.Success(InterventionStartResultView(result.value.intervention, result.value.enrollment.enrollmentId))
            }
        }
    }

    suspend fun closeIntervention(caseId: String, note: String): CareResult<InterventionCase> {
        val actor = when (val result = session.currentActor()) {
            is CareResult.Failure -> return result
            is CareResult.Success -> result.value
        }
        val result = interventions.closeWithNote(caseId, note.trim())
        if (result is CareResult.Success) recordAudit(actor.actorId, "teacher_close_intervention", caseId, "success")
        return result
    }

    suspend fun simulateDelivery(eventId: String): CareResult<com.example.mdd_calender.domain.model.DeliveryRecord> {
        val actor = when (val result = session.currentActor()) {
            is CareResult.Failure -> return result
            is CareResult.Success -> result.value
        }
        val summary = when (val result = alerts.getTeacherSummary(eventId)) {
            is CareResult.Failure -> return result
            is CareResult.Success -> result.value
        }
        val dto = SchoolAlertDto(
            eventId = summary.eventId,
            studentCode = summary.studentCode,
            concernLevel = summary.concernLevel.name,
            minimalReasonTags = summary.minimalReasonTags,
            occurredAtEpochMillis = summary.occurredAtEpochMillis,
            simulated = true,
        )
        val result = deliveryDispatcher?.invoke(eventId) ?: gateway.deliver(dto, "${summary.eventId}:${summary.occurredAtEpochMillis}")
        if (result is CareResult.Success) recordAudit(actor.actorId, "simulate_alert_delivery", eventId, result.value.status.name)
        return result
    }

    suspend fun reviewExit(
        enrollmentId: String,
        approve: Boolean,
        note: String,
    ): CareResult<AaEnrollment> {
        if (note.isBlank()) return CareResult.Failure(CareFailure.InvalidInput("Review note is required"))
        val actor = when (val result = session.currentActor()) {
            is CareResult.Failure -> return result
            is CareResult.Success -> result.value
        }
        val decision = if (approve) ExitReviewDecision.APPROVED else ExitReviewDecision.REJECTED
        val result = followUps.reviewExit(enrollmentId, decision, note.trim())
        if (result is CareResult.Success) {
            recordAudit(actor.actorId, "teacher_review_aa_exit", enrollmentId, decision.name)
        }
        return result
    }

    private suspend fun recordAudit(actorId: String, action: String, objectCode: String, result: String) {
        audit?.append(
            com.example.mdd_calender.domain.model.AuditEvent(
                auditId = UUID.randomUUID().toString(),
                actorId = actorId,
                action = action,
                objectCode = objectCode,
                result = result,
                occurredAtEpochMillis = clock(),
            ),
        )
    }
}

private fun TeacherAlertSummary.withInterventionDisposition(intervention: InterventionCase?): TeacherAlertSummary {
    val derived = when (intervention?.status) {
        InterventionStatus.PENDING_CONFIRMATION -> AlertDisposition.NEW
        InterventionStatus.CONFIRMED -> AlertDisposition.ACKNOWLEDGED
        InterventionStatus.ACTIVE, InterventionStatus.PENDING_CLOSURE -> AlertDisposition.IN_PROGRESS
        InterventionStatus.CLOSED -> AlertDisposition.CLOSED
        null -> disposition
    }
    return copy(disposition = derived)
}

data class InterventionStartResultView(
    val intervention: InterventionCase,
    val enrollmentId: String,
)
