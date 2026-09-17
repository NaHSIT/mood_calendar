package com.example.mdd_calender.feature.followup

class FollowUpService(
    private val store: FollowUpStore,
    private val assessmentVerifier: AssessmentEvidenceVerifier,
    private val clock: FollowUpClock,
    private val policy: FollowUpSchedulePolicy = FollowUpSchedulePolicy.DEMO,
) {
    suspend fun onInterventionStarted(signal: InterventionStartedSignal): FollowUpResult<AaEnrollment> =
        runCatching {
            store.transaction {
                if (wasInterventionEventHandled(signal.eventId)) {
                    return@transaction findActiveEnrollment(signal.studentId)
                        ?: error("Handled intervention has no active enrollment")
                }

                val current = findActiveEnrollment(signal.studentId)
                val enrollment = if (current != null) {
                    current.copy(
                        interventionCaseIds = current.interventionCaseIds + signal.caseId,
                        alertIds = current.alertIds + signal.alertId,
                    )
                } else {
                    val previousCycle = listEnrollments(signal.studentId).maxOfOrNull { it.cycle } ?: 0
                    AaEnrollment(
                        enrollmentId = "aa:${signal.studentId}:${signal.eventId}",
                        studentId = signal.studentId,
                        interventionCaseIds = setOf(signal.caseId),
                        alertIds = setOf(signal.alertId),
                        responsibleTeacherId = signal.responsibleTeacherId,
                        enrolledAtMillis = signal.startedAtMillis,
                        status = AaStatus.ACTIVE,
                        policyVersion = policy.version,
                        cycle = previousCycle + 1,
                    )
                }
                saveEnrollment(enrollment)
                generateMissingTasks(enrollment)
                markInterventionEventHandled(signal.eventId)
                enrollment
            }
        }.fold(
            onSuccess = { FollowUpResult.Success(it) },
            onFailure = { FollowUpResult.Rejected("TRANSACTION_FAILED") },
        )

    /** Safe to call at app startup; deterministic task IDs make this idempotent. */
    suspend fun recoverTaskSchedules(): FollowUpResult<Int> = runCatching {
        store.transaction {
            var generated = 0
            listActiveEnrollments().forEach { generated += generateMissingTasks(it) }
            generated
        }
    }.fold(
        onSuccess = { FollowUpResult.Success(it) },
        onFailure = { FollowUpResult.Rejected("RECOVERY_FAILED") },
    )

    suspend fun completeSimpleTask(actor: TrustedFollowUpActor, taskId: String): FollowUpResult<FollowUpTask> {
        val task = store.findTask(taskId) ?: return FollowUpResult.Rejected("TASK_NOT_FOUND")
        if (!canComplete(actor, task)) return FollowUpResult.Rejected("FORBIDDEN")
        if (task.type == FollowUpTaskType.FULL_ASSESSMENT) {
            return FollowUpResult.Rejected("ASSESSMENT_RECEIPT_REQUIRED")
        }
        if (task.status == FollowUpTaskStatus.COMPLETED) return FollowUpResult.Success(task)
        if (task.status == FollowUpTaskStatus.CANCELLED) return FollowUpResult.Rejected("TASK_CANCELLED")
        val completed = task.copy(status = FollowUpTaskStatus.COMPLETED, completedAtMillis = clock.nowMillis())
        store.saveTask(completed)
        return FollowUpResult.Success(completed)
    }

    suspend fun completeAssessmentTask(receipt: AssessmentCompletionReceipt): FollowUpResult<FollowUpTask> {
        val task = store.findTask(receipt.followUpTaskId) ?: return FollowUpResult.Rejected("TASK_NOT_FOUND")
        if (task.status == FollowUpTaskStatus.COMPLETED) {
            return if (task.assessmentId == receipt.assessmentId) FollowUpResult.Success(task)
            else FollowUpResult.Rejected("TASK_ALREADY_COMPLETED")
        }
        if (task.type != FollowUpTaskType.FULL_ASSESSMENT || task.studentId != receipt.studentId) {
            return FollowUpResult.Rejected("ASSESSMENT_MISMATCH")
        }
        if (!assessmentTypeMatches(task.requiredAssessmentType, receipt.assessmentType)) {
            return FollowUpResult.Rejected("ASSESSMENT_MISMATCH")
        }
        if (!assessmentVerifier.isDurablySaved(receipt)) {
            return FollowUpResult.Rejected("ASSESSMENT_NOT_VERIFIED")
        }
        val completed = task.copy(
            status = FollowUpTaskStatus.COMPLETED,
            completedAtMillis = receipt.savedAtMillis,
            assessmentId = receipt.assessmentId,
        )
        store.saveTask(completed)
        return FollowUpResult.Success(completed)
    }

    suspend fun requestExit(
        actor: TrustedFollowUpActor,
        enrollmentId: String,
        rationaleCode: String,
    ): FollowUpResult<AaEnrollment> {
        val enrollment = store.findEnrollment(enrollmentId) ?: return FollowUpResult.Rejected("NOT_FOUND")
        if (actor.role != FollowUpRole.STUDENT || actor.studentId != enrollment.studentId) {
            return FollowUpResult.Rejected("FORBIDDEN")
        }
        if (enrollment.status != AaStatus.ACTIVE) return FollowUpResult.Rejected("INVALID_STATE")
        val stableSinceMillis = store.stableObservationSinceMillis(enrollment.studentId)
            ?: return FollowUpResult.Rejected("STABLE_OBSERVATION_MISSING")
        if (clock.nowMillis() - stableSinceMillis < policy.minimumStableObservationMillis) {
            return FollowUpResult.Rejected("STABILITY_WINDOW_NOT_MET")
        }
        if (store.hasUnresolvedSafetyConcern(enrollment.studentId)) {
            return FollowUpResult.Rejected("UNRESOLVED_SAFETY_CONCERN")
        }
        val updated = enrollment.copy(
            status = AaStatus.EXIT_REVIEW,
            exitRequest = ExitRequest(
                requestedBy = actor.actorId,
                requestedAtMillis = clock.nowMillis(),
                stableSinceMillis = stableSinceMillis,
                rationaleCode = rationaleCode,
            ),
        )
        store.saveEnrollment(updated)
        return FollowUpResult.Success(updated)
    }

    suspend fun reviewExit(
        actor: TrustedFollowUpActor,
        enrollmentId: String,
        approve: Boolean,
        rejectionReasonCode: String? = null,
    ): FollowUpResult<AaEnrollment> = store.transaction {
        val enrollment = findEnrollment(enrollmentId) ?: return@transaction FollowUpResult.Rejected("NOT_FOUND")
        if (!isResponsibleTeacher(actor, enrollment)) return@transaction FollowUpResult.Rejected("FORBIDDEN")
        val request = enrollment.exitRequest
            ?: return@transaction FollowUpResult.Rejected("NO_EXIT_REQUEST")
        if (enrollment.status != AaStatus.EXIT_REVIEW) {
            return@transaction FollowUpResult.Rejected("INVALID_STATE")
        }
        if (approve && hasUnresolvedSafetyConcern(enrollment.studentId)) {
            return@transaction FollowUpResult.Rejected("UNRESOLVED_SAFETY_CONCERN")
        }
        val reviewedRequest = request.copy(
            reviewedBy = actor.actorId,
            reviewedAtMillis = clock.nowMillis(),
            rejectionReasonCode = if (approve) null else rejectionReasonCode ?: "REVIEWER_REJECTED",
        )
        val updated = enrollment.copy(
            status = if (approve) AaStatus.EXITED else AaStatus.ACTIVE,
            exitRequest = reviewedRequest,
        )
        saveEnrollment(updated)
        if (approve) {
            listTasks(enrollmentId)
                .filter { it.status != FollowUpTaskStatus.COMPLETED && it.status != FollowUpTaskStatus.CANCELLED }
                .forEach { saveTask(it.copy(status = FollowUpTaskStatus.CANCELLED)) }
        }
        FollowUpResult.Success(updated)
    }

    suspend fun transferResponsibility(
        actor: TrustedFollowUpActor,
        enrollmentId: String,
        newTeacherId: String,
    ): FollowUpResult<AaEnrollment> {
        val enrollment = store.findEnrollment(enrollmentId) ?: return FollowUpResult.Rejected("NOT_FOUND")
        if (!isResponsibleTeacher(actor, enrollment) || newTeacherId.isBlank()) {
            return FollowUpResult.Rejected("FORBIDDEN")
        }
        val updated = enrollment.copy(responsibleTeacherId = newTeacherId)
        store.saveEnrollment(updated)
        return FollowUpResult.Success(updated)
    }

    suspend fun setPaused(
        actor: TrustedFollowUpActor,
        enrollmentId: String,
        paused: Boolean,
    ): FollowUpResult<AaEnrollment> = store.transaction {
        val enrollment = findEnrollment(enrollmentId) ?: return@transaction FollowUpResult.Rejected("NOT_FOUND")
        if (!isResponsibleTeacher(actor, enrollment)) return@transaction FollowUpResult.Rejected("FORBIDDEN")
        if (enrollment.status !in setOf(AaStatus.ACTIVE, AaStatus.PAUSED)) {
            return@transaction FollowUpResult.Rejected("INVALID_STATE")
        }
        val updated = enrollment.copy(status = if (paused) AaStatus.PAUSED else AaStatus.ACTIVE)
        saveEnrollment(updated)
        listTasks(enrollmentId)
            .filter { it.status !in setOf(FollowUpTaskStatus.COMPLETED, FollowUpTaskStatus.CANCELLED) }
            .forEach { task ->
                val newStatus = if (paused) FollowUpTaskStatus.PAUSED else FollowUpTaskStatus.UPCOMING
                saveTask(task.copy(status = newStatus))
            }
        FollowUpResult.Success(updated)
    }

    suspend fun studentView(actor: TrustedFollowUpActor): FollowUpResult<StudentFollowUpView> {
        if (actor.role != FollowUpRole.STUDENT || actor.studentId == null) {
            return FollowUpResult.Rejected("FORBIDDEN")
        }
        val enrollment = store.findActiveEnrollment(actor.studentId)
            ?: return FollowUpResult.Rejected("NOT_ENROLLED")
        val now = clock.nowMillis()
        val tasks = store.listTasks(enrollment.enrollmentId)
            .sortedBy { it.dueAtMillis }
            .map { StudentTaskItem(it.taskId, it.type, it.dueAtMillis, it.effectiveStatus(now, policy.dueWindowMillis)) }
        return FollowUpResult.Success(StudentFollowUpView(enrollment.status, policy.demoNotice, tasks))
    }

    suspend fun teacherList(actor: TrustedFollowUpActor): FollowUpResult<List<TeacherFollowUpSummary>> {
        if (actor.role != FollowUpRole.TEACHER) return FollowUpResult.Rejected("FORBIDDEN")
        val now = clock.nowMillis()
        val summaries = store.listActiveEnrollments()
            .filter { it.responsibleTeacherId == actor.actorId }
            .map { enrollment ->
                val tasks = store.listTasks(enrollment.enrollmentId)
                val statuses = tasks.map { it.effectiveStatus(now, policy.dueWindowMillis) }
                TeacherFollowUpSummary(
                    enrollmentId = enrollment.enrollmentId,
                    studentId = enrollment.studentId,
                    status = enrollment.status,
                    completedTasks = statuses.count { it == FollowUpTaskStatus.COMPLETED },
                    totalTasks = tasks.size,
                    needsContact = statuses.any { it == FollowUpTaskStatus.OVERDUE } ||
                        tasks.any { it.type == FollowUpTaskType.TEACHER_CONTACT &&
                            it.effectiveStatus(now, policy.dueWindowMillis) in setOf(FollowUpTaskStatus.DUE, FollowUpTaskStatus.OVERDUE) },
                    nextDueAtMillis = tasks.filter { it.status !in setOf(FollowUpTaskStatus.COMPLETED, FollowUpTaskStatus.CANCELLED) }
                        .minOfOrNull { it.dueAtMillis },
                )
            }
        return FollowUpResult.Success(summaries)
    }

    private suspend fun FollowUpStore.generateMissingTasks(enrollment: AaEnrollment): Int {
        val existing = listTasks(enrollment.enrollmentId).associateBy { it.taskId }
        var generated = 0
        policy.taskRules.forEachIndexed { index, rule ->
            val id = "${enrollment.enrollmentId}:task:$index"
            if (id !in existing) {
                saveTask(
                    FollowUpTask(
                        taskId = id,
                        enrollmentId = enrollment.enrollmentId,
                        studentId = enrollment.studentId,
                        type = rule.type,
                        dueAtMillis = enrollment.enrolledAtMillis + rule.delayMillis,
                        requiredAssessmentType = rule.requiredAssessmentType,
                    ),
                )
                generated++
            }
        }
        return generated
    }

    private suspend fun canComplete(actor: TrustedFollowUpActor, task: FollowUpTask): Boolean {
        if (actor.role == FollowUpRole.STUDENT) return actor.studentId == task.studentId && task.type != FollowUpTaskType.TEACHER_CONTACT
        val enrollment = store.findEnrollment(task.enrollmentId) ?: return false
        return actor.role == FollowUpRole.TEACHER && actor.actorId == enrollment.responsibleTeacherId &&
            task.type == FollowUpTaskType.TEACHER_CONTACT
    }

    private fun isResponsibleTeacher(actor: TrustedFollowUpActor, enrollment: AaEnrollment) =
        actor.role == FollowUpRole.TEACHER && actor.actorId == enrollment.responsibleTeacherId

    private fun assessmentTypeMatches(required: String?, actual: String): Boolean = when (required) {
        "PHQ_9_OR_GAD_7" -> actual == "PHQ_9" || actual == "GAD_7"
        null -> false
        else -> required == actual
    }
}
