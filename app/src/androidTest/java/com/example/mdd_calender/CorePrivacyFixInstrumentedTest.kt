package com.example.mdd_calender

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.mdd_calender.data.MoodDatabase
import com.example.mdd_calender.data.care.RoomAlertRepository
import com.example.mdd_calender.data.care.RoomAssessmentRepository
import com.example.mdd_calender.feature.assessment.AssessmentScorer
import com.example.mdd_calender.feature.assessment.AssessmentType
import com.example.mdd_calender.feature.assessment.ScoreResult
import com.example.mdd_calender.feature.assessment.toAssessmentRecord
import com.example.mdd_calender.data.care.RoomCareAdministration
import com.example.mdd_calender.data.care.RoomConsentRepository
import com.example.mdd_calender.data.care.RoomFollowUpRepository
import com.example.mdd_calender.data.care.RoomInterventionRepository
import com.example.mdd_calender.data.care.RoomStudentHealthRepository
import com.example.mdd_calender.domain.model.ActorContext
import com.example.mdd_calender.domain.model.ActorRole
import com.example.mdd_calender.domain.model.AlertEvent
import com.example.mdd_calender.domain.model.AlertDisposition
import com.example.mdd_calender.domain.model.CareResult
import com.example.mdd_calender.domain.model.ConcernLevel
import com.example.mdd_calender.domain.model.DataDomain
import com.example.mdd_calender.domain.model.ExitReviewDecision
import com.example.mdd_calender.domain.model.FollowUpTask
import com.example.mdd_calender.domain.model.FollowUpTaskStatus
import com.example.mdd_calender.domain.model.FollowUpTaskType
import com.example.mdd_calender.domain.model.HealthSampleType
import com.example.mdd_calender.domain.model.HealthScope
import com.example.mdd_calender.domain.model.InterventionCase
import com.example.mdd_calender.domain.model.InterventionStartRequest
import com.example.mdd_calender.domain.model.InterventionStatus
import com.example.mdd_calender.domain.model.RawHealthSample
import com.example.mdd_calender.domain.model.SampleQuality
import com.example.mdd_calender.domain.model.UtcClock
import com.example.mdd_calender.domain.policy.AaExitPolicy
import com.example.mdd_calender.security.AndroidKeystoreCipher
import com.example.mdd_calender.security.DemoSessionProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class CorePrivacyFixInstrumentedTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun currentStudentCanDeleteOnlySelectedRawHealthScopes() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, MoodDatabase::class.java).allowMainThreadQueries().build()
        try {
            val studentId = "delete-student"
            val studentSession = DemoSessionProvider(ActorContext(studentId, ActorRole.STUDENT, DataDomain.DEMO))
            val cipher = AndroidKeystoreCipher("test-${UUID.randomUUID()}")
            val consent = RoomConsentRepository(db.careDao(), studentSession, UtcClock { 1_000 })
            assertTrue(consent.updateForCurrentStudent(setOf(HealthScope.HEART_RATE, HealthScope.SLEEP), true) is CareResult.Success)
            val health = RoomStudentHealthRepository(db.careDao(), studentSession, cipher)
            val samples = listOf(
                RawHealthSample("heart-1", studentId, "demo", HealthSampleType.HEART_RATE_BPM, 10, 72.0, "bpm", SampleQuality.GOOD, true, 1),
                RawHealthSample("sleep-1", studentId, "demo", HealthSampleType.SLEEP_DURATION_MINUTES, 20, 480.0, "min", SampleQuality.GOOD, true, 1),
            )
            assertTrue(health.saveForCurrentStudent(samples) is CareResult.Success)

            val deleted = health.deleteForCurrentStudent(setOf(HealthScope.HEART_RATE)) as CareResult.Success
            assertEquals(1, deleted.value)
            val remaining = health.listForCurrentStudent(0, 100) as CareResult.Success
            assertEquals(listOf("sleep-1"), remaining.value.map { it.sampleId })
        } finally {
            db.close()
        }
    }

    @Test
    fun aaExitRequiresObservationAndResolvedTasksThenCancelsNewFutureTasks() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, MoodDatabase::class.java).allowMainThreadQueries().build()
        try {
            var now = 1_000L
            val clock = UtcClock { now }
            val cipher = AndroidKeystoreCipher("test-${UUID.randomUUID()}")
            val system = DemoSessionProvider(ActorContext("system", ActorRole.SYSTEM, DataDomain.DEMO))
            val student = DemoSessionProvider(ActorContext("exit-student", ActorRole.STUDENT, DataDomain.DEMO))
            val teacher = DemoSessionProvider(ActorContext("exit-teacher", ActorRole.TEACHER, DataDomain.DEMO))
            val admin = RoomCareAdministration(db.careDao(), system, cipher)
            admin.upsertStudent("exit-student", "DEMO-EXIT")
            admin.assignTeacher("exit-student", "exit-teacher")
            RoomAlertRepository(db.careDao(), system, cipher).saveInternal(
                AlertEvent("exit-alert", "exit-student", "DEMO-EXIT", ConcernLevel.MODERATE, setOf("SAFETY_REVIEW_REQUIRED"), now, "risk-v1", "eval-exit", "eval-exit")
            )
            admin.createIntervention(InterventionCase("exit-case", "exit-student", "exit-teacher", "exit-alert", InterventionStatus.PENDING_CONFIRMATION, null, null))
            val interventions = RoomInterventionRepository(db, teacher, cipher, clock)
            interventions.acknowledge("exit-case", "ack-exit")
            interventions.startWithAa(InterventionStartRequest("exit-case", "follow-v1", "start-exit"))
            admin.createFollowUpTask(FollowUpTask("required-task", "exit-student", FollowUpTaskType.ASSESSMENT_RETAKE, now + 1, null, null, FollowUpTaskStatus.PENDING))

            val policy = AaExitPolicy(minimumObservationMillis = 100, requireAllFollowUpTasksResolved = true)
            val studentFollowUp = RoomFollowUpRepository(db.careDao(), student, cipher, clock, policy)
            now += 99
            assertTrue(studentFollowUp.requestExit("stable") is CareResult.Failure)
            now += 1
            assertTrue(studentFollowUp.requestExit("stable") is CareResult.Failure)
            assertTrue(studentFollowUp.completeCurrentStudentTask("required-task", now) is CareResult.Failure)
            val score = AssessmentScorer.score(AssessmentType.PHQ_9, List(9) { 0 }) as ScoreResult.Complete
            val assessment = score.toAssessmentRecord("exit-retake", "exit-student", "test", List(9) { 0 }, now)
            assertTrue(RoomAssessmentRepository(db.careDao(), student, cipher).saveForCurrentStudent(assessment) is CareResult.Success)
            assertTrue(studentFollowUp.completeAssessmentForCurrentStudent("required-task", assessment.assessmentId) is CareResult.Success)
            assertTrue(studentFollowUp.requestExit("stable") is CareResult.Failure)
            val teacherAlerts = RoomAlertRepository(db.careDao(), teacher, cipher)
            teacherAlerts.updateTeacherDisposition("exit-alert", AlertDisposition.ACKNOWLEDGED)
            teacherAlerts.updateTeacherDisposition("exit-alert", AlertDisposition.CLOSED)
            val pending = studentFollowUp.requestExit("stable") as CareResult.Success

            admin.createFollowUpTask(FollowUpTask("future-task", "exit-student", FollowUpTaskType.CHECK_IN, now + 100, null, null, FollowUpTaskStatus.PENDING))
            val teacherFollowUp = RoomFollowUpRepository(db.careDao(), teacher, cipher, clock, policy)
            assertTrue(teacherFollowUp.reviewExit(pending.value.enrollmentId, ExitReviewDecision.APPROVED, "reviewed") is CareResult.Success)
            assertEquals(FollowUpTaskStatus.CANCELLED.name, db.careDao().taskForStudent("future-task", "exit-student", DataDomain.DEMO.name)?.status)
        } finally {
            db.close()
        }
    }
}
