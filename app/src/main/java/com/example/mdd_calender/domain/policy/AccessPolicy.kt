package com.example.mdd_calender.domain.policy

import com.example.mdd_calender.domain.model.ActorContext
import com.example.mdd_calender.domain.model.ActorRole
import com.example.mdd_calender.domain.model.CareFailure
import com.example.mdd_calender.domain.model.CareResult
import com.example.mdd_calender.domain.model.DataDomain

enum class ProtectedResource {
    OWN_ASSESSMENT,
    OWN_RAW_HEALTH,
    OWN_CONSENT,
    TEACHER_ALERT_SUMMARY,
    TEACHER_WORK_ITEM,
    INTERNAL_EVALUATION_INPUT,
    ADMINISTRATION,
}

object AccessPolicy {
    fun requireDomain(actor: ActorContext, recordDomain: DataDomain): CareResult<Unit> =
        if (actor.dataDomain == recordDomain) CareResult.Success(Unit)
        else CareResult.Failure(CareFailure.Forbidden("Cross-domain access is not allowed"))

    fun requireOwner(actor: ActorContext, ownerId: String, recordDomain: DataDomain): CareResult<Unit> {
        val domain = requireDomain(actor, recordDomain)
        if (domain is CareResult.Failure) return domain
        return if (actor.role == ActorRole.STUDENT && actor.actorId == ownerId) CareResult.Success(Unit)
        else CareResult.Failure(CareFailure.Forbidden("Student-owned data is only available to its owner"))
    }

    fun requireTeacher(actor: ActorContext, recordDomain: DataDomain, isAssigned: Boolean): CareResult<Unit> {
        val domain = requireDomain(actor, recordDomain)
        if (domain is CareResult.Failure) return domain
        return if (actor.role == ActorRole.TEACHER && isAssigned) CareResult.Success(Unit)
        else CareResult.Failure(CareFailure.Forbidden("Teacher is not assigned to this student"))
    }

    fun requireSystem(actor: ActorContext, recordDomain: DataDomain): CareResult<Unit> {
        val domain = requireDomain(actor, recordDomain)
        if (domain is CareResult.Failure) return domain
        return if (actor.role == ActorRole.SYSTEM) CareResult.Success(Unit)
        else CareResult.Failure(CareFailure.Forbidden("This operation requires the internal system channel"))
    }

    fun permits(role: ActorRole, resource: ProtectedResource): Boolean = when (resource) {
        ProtectedResource.OWN_ASSESSMENT,
        ProtectedResource.OWN_RAW_HEALTH,
        ProtectedResource.OWN_CONSENT -> role == ActorRole.STUDENT
        ProtectedResource.TEACHER_ALERT_SUMMARY,
        ProtectedResource.TEACHER_WORK_ITEM -> role == ActorRole.TEACHER
        ProtectedResource.INTERNAL_EVALUATION_INPUT,
        ProtectedResource.ADMINISTRATION -> role == ActorRole.SYSTEM
    }
}
