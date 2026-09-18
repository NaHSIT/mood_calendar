package com.example.mdd_calender

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.mdd_calender.data.MoodDatabase
import com.example.mdd_calender.data.care.*
import com.example.mdd_calender.domain.model.*
import com.example.mdd_calender.feature.assessment.AssessmentScorer
import com.example.mdd_calender.feature.assessment.ScoreResult
import com.example.mdd_calender.feature.assessment.toAssessmentRecord
import com.example.mdd_calender.integration.app.AppCareServices
import com.example.mdd_calender.integration.school.SimulatedSchoolPlatformGateway
import com.example.mdd_calender.security.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Real Room + Android Keystore; never touches the user's calendar database. */
@RunWith(AndroidJUnit4::class)
class CareWorkflowInstrumentedTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private fun database() = Room.inMemoryDatabaseBuilder(context, MoodDatabase::class.java).build()
    private fun assessment(id: String, answers: List<Int>) =
        (AssessmentScorer.score(com.example.mdd_calender.feature.assessment.AssessmentType.PHQ_9, answers) as ScoreResult.Complete)
            .toAssessmentRecord(id, "demo-student", "test", answers, System.currentTimeMillis())
    private fun <T> CareResult<T>.value(): T {
        assertTrue("Expected success, got $this", this is CareResult.Success)
        return (this as CareResult.Success).value
    }

    @Test fun moderateAssessmentWithoutHealthCreatesTeacherAlertAndAutomaticAaTasks() = runBlocking {
        val db = database()
        try {
            val services = AppCareServices(context, db)
            val record = assessment("moderate", listOf(2,2,2,2,2,0,0,0,0))
            val first = services.submitAssessmentAndTriggerCare(record).value()
            assertNotNull(first.alertId)
            assertEquals(DeliveryStatus.SIMULATED_DELIVERED, first.delivery?.status)
            services.submitAssessmentAndTriggerCare(record).value()
            assertEquals(1, services.assessments.listForCurrentStudent().value().size)
            val inbox = services.teacherService.loadInbox().value()
            assertEquals(1, inbox.size)
            assertEquals("DEMO-001", inbox.single().summary.studentCode)
            assertEquals(ConcernLevel.MODERATE, inbox.single().summary.concernLevel)
            val caseId = requireNotNull(inbox.single().intervention).caseId
            services.teacherService.acknowledge(caseId).value()
            assertEquals(InterventionStatus.CONFIRMED, services.teacherService.loadDetails(first.alertId!!).value().item.intervention?.status)
            services.teacherService.startIntervention(caseId, "test").value()
            services.teacherService.startIntervention(caseId, "test").value()
            val tasks = services.followUp.currentStudentTasks().value()
            assertEquals(3, tasks.size)
            assertEquals(AaStatus.TRACKING, services.followUp.currentStudentEnrollment().value().status)
            val teacherTask = tasks.single { it.type == FollowUpTaskType.TEACHER_REVIEW }
            assertTrue(services.followUp.completeCurrentStudentTask(teacherTask.taskId, 0) is CareResult.Failure)
            val retake = tasks.single { it.type == FollowUpTaskType.ASSESSMENT_RETAKE }
            assertTrue(services.followUp.completeCurrentStudentTask(retake.taskId, 0) is CareResult.Failure)
            assertTrue(services.followUp.completeAssessmentForCurrentStudent(retake.taskId, "missing") is CareResult.Failure)
            val low = assessment("low-retake", List(9) { 0 })
            services.submitAssessmentAndTriggerCare(low).value()
            assertEquals("low-retake", services.followUp.completeAssessmentForCurrentStudent(retake.taskId, low.assessmentId).value().relatedAssessmentId)
            val checkIn = tasks.single { it.type == FollowUpTaskType.CHECK_IN }
            val completed = services.followUp.completeCurrentStudentTask(checkIn.taskId, 0).value()
            assertEquals(completed, services.followUp.completeCurrentStudentTask(checkIn.taskId, Long.MAX_VALUE).value())
            assertTrue(services.teacherService.closeIntervention(caseId, " ") is CareResult.Failure)
            services.teacherService.closeIntervention(caseId, "演示：已联系并安排继续随访").value()
            assertEquals(AlertDisposition.CLOSED, services.teacherService.loadDetails(first.alertId).value().item.summary.disposition)
            assertEquals(AaStatus.TRACKING, services.followUp.currentStudentEnrollment().value().status)
            assertEquals(FollowUpTaskStatus.COMPLETED, services.followUp.currentStudentTasks().value().single { it.taskId == teacherTask.taskId }.status)
            assertTrue(services.followUp.requestExit("申请") is CareResult.Failure)
        } finally { db.close() }
    }

    @Test fun healthDecryptionFailureDoesNotBlockPrimaryAssessment() = runBlocking {
        val db = database()
        try {
            val services = AppCareServices(context, db)
            val consent = services.consent.updateForCurrentStudent(setOf(HealthScope.HEART_RATE), true).value()
            db.careDao().upsertSamples(listOf(HealthSampleEntity("broken", "demo-student", "DEMO", "demo", "HEART_RATE_BPM", System.currentTimeMillis(), "invalid-ciphertext", "GOOD", true, consent.revision)))
            val result = services.submitAssessmentAndTriggerCare(assessment("with-broken-health", listOf(2,2,2,2,2,0,0,0,0))).value()
            assertNotNull(result.alertId)
            assertEquals(ConcernLevel.MODERATE, services.teacherService.loadInbox().value().single().summary.concernLevel)
        } finally { db.close() }
    }

    @Test fun lateHealthCallbackCannotWriteAfterConsentRevoked() = runBlocking {
        val db = database()
        try {
            val session = DemoSessionProvider(ActorContext("demo-student", ActorRole.STUDENT, DataDomain.DEMO))
            val consent = RoomConsentRepository(db.careDao(), session)
            val authorized = consent.updateForCurrentStudent(setOf(HealthScope.HEART_RATE), true).value()
            val delegate = AndroidKeystoreCipher()
            val revokingCipher = object : AuthenticatedCipher {
                override fun encrypt(plainText: ByteArray, associatedData: ByteArray): CareResult<String> {
                    runBlocking { consent.updateForCurrentStudent(emptySet(), false).value() }
                    return delegate.encrypt(plainText, associatedData)
                }
                override fun decrypt(cipherText: String, associatedData: ByteArray) = delegate.decrypt(cipherText, associatedData)
            }
            val health = RoomStudentHealthRepository(db.careDao(), session, revokingCipher)
            val saved = health.saveForCurrentStudent(listOf(RawHealthSample("late", "demo-student", "demo", HealthSampleType.HEART_RATE_BPM, 100, 72.0, "bpm", SampleQuality.GOOD, true, authorized.revision)))
            assertTrue(saved is CareResult.Failure)
            assertTrue(db.careDao().samples("demo-student", "DEMO", 0, Long.MAX_VALUE).isEmpty())
        } finally { db.close() }
    }

    @Test fun pendingDeliverySurvivesServiceRecreationAndRetriesOnce() = runBlocking {
        val db = database()
        try {
            val gateway = SimulatedSchoolPlatformGateway("demo-teacher", clock = { 0L })
            gateway.setNextOutcome(SimulatedSchoolPlatformGateway.NextOutcome.RETRY)
            val services = AppCareServices(context, db, gateway)
            val receipt = services.submitAssessmentAndTriggerCare(assessment("retry", listOf(2,2,2,2,2,0,0,0,0))).value()
            assertEquals(DeliveryStatus.RETRY_PENDING, receipt.delivery?.status)
            val recreated = AppCareServices(context, db)
            recreated.retryPendingDeliveries()
            val persisted = db.careDao().deliveryForAlert(receipt.alertId!!, "DEMO")!!
            assertEquals(DeliveryStatus.SIMULATED_DELIVERED.name, persisted.status)
            assertEquals(2, persisted.attemptCount)
            recreated.retryPendingDeliveries()
            assertEquals(persisted, db.careDao().deliveryForAlert(receipt.alertId, "DEMO"))
        } finally { db.close() }
    }
}
