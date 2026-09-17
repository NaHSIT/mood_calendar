package com.example.mdd_calender.feature.followup

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FollowUpServiceTest {
    private val day = 24L * 60 * 60 * 1_000
    private val clock = MutableClock(100 * day)
    private val store = MemoryFollowUpStore()
    private val verifiedAssessments = mutableSetOf<String>()
    private val service = FollowUpService(
        store = store,
        assessmentVerifier = AssessmentEvidenceVerifier { it.assessmentId in verifiedAssessments },
        clock = clock,
    )

    @Test
    fun duplicateInterventionAndRecoveryAreIdempotent() = runBlocking {
        val signal = signal("event-1", "case-1", "alert-1")
        val first = service.onInterventionStarted(signal).success()
        val second = service.onInterventionStarted(signal).success()

        assertEquals(first.enrollmentId, second.enrollmentId)
        assertEquals(4, store.listTasks(first.enrollmentId).size)
        assertEquals(0, service.recoverTaskSchedules().success())
        assertEquals(4, store.listTasks(first.enrollmentId).size)
    }

    @Test
    fun transactionFailureCanBeRetriedWithoutPartialEnrollmentOrTasks() = runBlocking {
        store.failNextTransaction = true
        assertEquals("TRANSACTION_FAILED", (service.onInterventionStarted(signal("e1", "c1", "a1")) as FollowUpResult.Rejected).reason)
        assertTrue(store.enrollments.isEmpty())
        assertTrue(store.tasks.isEmpty())

        val enrollment = service.onInterventionStarted(signal("e1", "c1", "a1")).success()
        assertEquals(4, store.listTasks(enrollment.enrollmentId).size)
    }

    @Test
    fun differentAlertsForStudentReuseOneActiveEnrollment() = runBlocking {
        val first = service.onInterventionStarted(signal("e1", "c1", "a1")).success()
        val second = service.onInterventionStarted(signal("e2", "c2", "a2")).success()

        assertEquals(first.enrollmentId, second.enrollmentId)
        assertEquals(setOf("a1", "a2"), second.alertIds)
        assertEquals(setOf("c1", "c2"), second.interventionCaseIds)
    }

    @Test
    fun overdueTaskCreatesContactNeedButDoesNotChangeAaRiskState() = runBlocking {
        val enrollment = service.onInterventionStarted(signal("e1", "c1", "a1")).success()
        clock.now = enrollment.enrolledAtMillis + 20 * day

        val student = TrustedFollowUpActor("student-user", FollowUpRole.STUDENT, "student-1")
        val view = service.studentView(student).success()
        assertTrue(view.tasks.any { it.status == FollowUpTaskStatus.OVERDUE })
        assertEquals(AaStatus.ACTIVE, view.status)

        val teacher = TrustedFollowUpActor("teacher-1", FollowUpRole.TEACHER)
        assertTrue(service.teacherList(teacher).success().single().needsContact)
    }

    @Test
    fun assessmentRequiresMatchingDurablySavedReceiptAndCompletionIsIdempotent() = runBlocking {
        val enrollment = service.onInterventionStarted(signal("e1", "c1", "a1")).success()
        val task = store.listTasks(enrollment.enrollmentId).single { it.type == FollowUpTaskType.FULL_ASSESSMENT }
        val forged = AssessmentCompletionReceipt(task.taskId, "fake", "student-1", "PHQ_9", clock.now)
        assertEquals("ASSESSMENT_NOT_VERIFIED", (service.completeAssessmentTask(forged) as FollowUpResult.Rejected).reason)

        verifiedAssessments += "assessment-1"
        val receipt = forged.copy(assessmentId = "assessment-1")
        val completed = service.completeAssessmentTask(receipt).success()
        assertEquals(FollowUpTaskStatus.COMPLETED, completed.status)
        assertEquals(completed, service.completeAssessmentTask(receipt).success())

        verifiedAssessments += "assessment-2"
        val duplicate = service.completeAssessmentTask(receipt.copy(assessmentId = "assessment-2"))
        assertEquals("TASK_ALREADY_COMPLETED", (duplicate as FollowUpResult.Rejected).reason)
    }

    @Test
    fun studentAndTeacherQueriesAreScopeLimited() = runBlocking {
        val enrollment = service.onInterventionStarted(signal("e1", "c1", "a1")).success()
        assertTrue(service.studentView(TrustedFollowUpActor("s2", FollowUpRole.STUDENT, "student-2")) is FollowUpResult.Rejected)
        assertTrue(service.teacherList(TrustedFollowUpActor("teacher-2", FollowUpRole.TEACHER)).success().isEmpty())

        val moodTask = store.listTasks(enrollment.enrollmentId).first { it.type == FollowUpTaskType.MOOD_CHECK_IN }
        val otherStudent = TrustedFollowUpActor("s2", FollowUpRole.STUDENT, "student-2")
        assertEquals("FORBIDDEN", (service.completeSimpleTask(otherStudent, moodTask.taskId) as FollowUpResult.Rejected).reason)
    }

    @Test
    fun exitRejectsUnstableOrSafetyConcernThenSupportsRejectAndApprove() = runBlocking {
        val enrollment = service.onInterventionStarted(signal("e1", "c1", "a1")).success()
        val teacher = TrustedFollowUpActor("teacher-1", FollowUpRole.TEACHER)
        val student = TrustedFollowUpActor("student-user", FollowUpRole.STUDENT, "student-1")

        store.stableSince["student-1"] = clock.now - day
        val unstable = service.requestExit(student, enrollment.enrollmentId, "STABLE_OBSERVATION")
        assertEquals("STABILITY_WINDOW_NOT_MET", (unstable as FollowUpResult.Rejected).reason)
        store.stableSince["student-1"] = clock.now - 20 * day
        store.unsafeStudents += "student-1"
        val unsafe = service.requestExit(student, enrollment.enrollmentId, "STABLE_OBSERVATION")
        assertEquals("UNRESOLVED_SAFETY_CONCERN", (unsafe as FollowUpResult.Rejected).reason)

        store.unsafeStudents.clear()
        service.requestExit(student, enrollment.enrollmentId, "STABLE_OBSERVATION").success()
        assertEquals(AaStatus.ACTIVE, service.reviewExit(teacher, enrollment.enrollmentId, false, "MORE_OBSERVATION").success().status)

        service.requestExit(student, enrollment.enrollmentId, "STABLE_OBSERVATION").success()
        assertEquals(AaStatus.EXITED, service.reviewExit(teacher, enrollment.enrollmentId, true).success().status)
        assertTrue(store.listTasks(enrollment.enrollmentId).all {
            it.status == FollowUpTaskStatus.CANCELLED || it.status == FollowUpTaskStatus.COMPLETED
        })
    }

    @Test
    fun interventionAfterExitCreatesNewEnrollmentAndTasks() = runBlocking {
        val first = service.onInterventionStarted(signal("e1", "c1", "a1")).success()
        val teacher = TrustedFollowUpActor("teacher-1", FollowUpRole.TEACHER)
        val student = TrustedFollowUpActor("student-user", FollowUpRole.STUDENT, "student-1")
        store.stableSince["student-1"] = clock.now - 20 * day
        service.requestExit(student, first.enrollmentId, "STABLE_OBSERVATION")
        service.reviewExit(teacher, first.enrollmentId, true)

        val second = service.onInterventionStarted(signal("e2", "c2", "a2")).success()
        assertNotEquals(first.enrollmentId, second.enrollmentId)
        assertEquals(AaStatus.ACTIVE, second.status)
        assertEquals(2, second.cycle)
        assertEquals(4, store.listTasks(second.enrollmentId).size)
    }

    @Test
    fun transferImmediatelyRevokesOldTeacherScope() = runBlocking {
        val enrollment = service.onInterventionStarted(signal("e1", "c1", "a1")).success()
        val oldTeacher = TrustedFollowUpActor("teacher-1", FollowUpRole.TEACHER)
        service.transferResponsibility(oldTeacher, enrollment.enrollmentId, "teacher-2").success()

        assertTrue(service.teacherList(oldTeacher).success().isEmpty())
        assertEquals(1, service.teacherList(TrustedFollowUpActor("teacher-2", FollowUpRole.TEACHER)).success().size)
    }

    @Test
    fun pauseAndResumeKeepTerminalTasksUntouched() = runBlocking {
        val enrollment = service.onInterventionStarted(signal("e1", "c1", "a1")).success()
        val teacher = TrustedFollowUpActor("teacher-1", FollowUpRole.TEACHER)
        val contact = store.listTasks(enrollment.enrollmentId).single { it.type == FollowUpTaskType.TEACHER_CONTACT }
        service.completeSimpleTask(teacher, contact.taskId).success()

        service.setPaused(teacher, enrollment.enrollmentId, true).success()
        assertTrue(store.listTasks(enrollment.enrollmentId).filter { it.taskId != contact.taskId }
            .all { it.status == FollowUpTaskStatus.PAUSED })
        assertEquals(FollowUpTaskStatus.COMPLETED, store.findTask(contact.taskId)!!.status)

        service.setPaused(teacher, enrollment.enrollmentId, false).success()
        assertFalse(store.listTasks(enrollment.enrollmentId).any { it.status == FollowUpTaskStatus.PAUSED })
    }

    private fun signal(event: String, case: String, alert: String) = InterventionStartedSignal(
        eventId = event,
        caseId = case,
        alertId = alert,
        studentId = "student-1",
        responsibleTeacherId = "teacher-1",
        startedAtMillis = clock.now,
    )
}

private class MutableClock(var now: Long) : FollowUpClock {
    override fun nowMillis() = now
}

private class MemoryFollowUpStore : FollowUpStore {
    val enrollments = linkedMapOf<String, AaEnrollment>()
    val tasks = linkedMapOf<String, FollowUpTask>()
    val handledEvents = mutableSetOf<String>()
    val unsafeStudents = mutableSetOf<String>()
    val stableSince = mutableMapOf<String, Long>()
    var failNextTransaction = false

    override suspend fun <T> transaction(block: suspend FollowUpStore.() -> T): T {
        val enrollmentSnapshot = LinkedHashMap(enrollments)
        val taskSnapshot = LinkedHashMap(tasks)
        val eventSnapshot = HashSet(handledEvents)
        return try {
            val result = block(this)
            if (failNextTransaction) {
                failNextTransaction = false
                error("synthetic commit failure")
            }
            result
        } catch (error: Throwable) {
            enrollments.clear(); enrollments.putAll(enrollmentSnapshot)
            tasks.clear(); tasks.putAll(taskSnapshot)
            handledEvents.clear(); handledEvents.addAll(eventSnapshot)
            throw error
        }
    }

    override suspend fun findEnrollment(enrollmentId: String) = enrollments[enrollmentId]
    override suspend fun findActiveEnrollment(studentId: String) = enrollments.values.firstOrNull {
        it.studentId == studentId && it.status != AaStatus.EXITED
    }
    override suspend fun listActiveEnrollments() = enrollments.values.filter { it.status != AaStatus.EXITED }
    override suspend fun saveEnrollment(enrollment: AaEnrollment) { enrollments[enrollment.enrollmentId] = enrollment }
    override suspend fun wasInterventionEventHandled(eventId: String) = eventId in handledEvents
    override suspend fun markInterventionEventHandled(eventId: String) { handledEvents += eventId }
    override suspend fun findTask(taskId: String) = tasks[taskId]
    override suspend fun listTasks(enrollmentId: String) = tasks.values.filter { it.enrollmentId == enrollmentId }
    override suspend fun saveTask(task: FollowUpTask) { tasks[task.taskId] = task }
    override suspend fun hasUnresolvedSafetyConcern(studentId: String) = studentId in unsafeStudents
    override suspend fun stableObservationSinceMillis(studentId: String) = stableSince[studentId]
    override suspend fun listEnrollments(studentId: String) = enrollments.values.filter { it.studentId == studentId }
}

private fun <T> FollowUpResult<T>.success(): T = (this as FollowUpResult.Success<T>).value
