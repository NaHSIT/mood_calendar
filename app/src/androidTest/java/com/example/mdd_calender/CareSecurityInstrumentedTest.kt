package com.example.mdd_calender

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.mdd_calender.data.MoodDatabase
import com.example.mdd_calender.data.care.RoomAlertRepository
import com.example.mdd_calender.data.care.RoomAssessmentRepository
import com.example.mdd_calender.data.care.RoomCareAdministration
import com.example.mdd_calender.data.care.RoomInterventionRepository
import com.example.mdd_calender.domain.model.ActorContext
import com.example.mdd_calender.domain.model.ActorRole
import com.example.mdd_calender.domain.model.AlertEvent
import com.example.mdd_calender.domain.model.AssessmentRecord
import com.example.mdd_calender.domain.model.AssessmentState
import com.example.mdd_calender.domain.model.AssessmentType
import com.example.mdd_calender.domain.model.CareResult
import com.example.mdd_calender.domain.model.ConcernLevel
import com.example.mdd_calender.domain.model.DataDomain
import com.example.mdd_calender.domain.model.InterventionCase
import com.example.mdd_calender.domain.model.InterventionStartRequest
import com.example.mdd_calender.domain.model.InterventionStatus
import com.example.mdd_calender.domain.model.SymptomBand
import com.example.mdd_calender.security.AndroidKeystoreCipher
import com.example.mdd_calender.security.DemoSessionProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class CareSecurityInstrumentedTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test fun encryptedEnvelopeIsRandomAndAuthenticated() {
        val cipher = AndroidKeystoreCipher("test-${UUID.randomUUID()}")
        val aad = AndroidKeystoreCipher.aad("assessment", "a-1")
        val one = cipher.encrypt("0,1,2".encodeToByteArray(), aad) as CareResult.Success
        val two = cipher.encrypt("0,1,2".encodeToByteArray(), aad) as CareResult.Success
        assertNotEquals(one.value, two.value)
        assertEquals("0,1,2", ((cipher.decrypt(one.value, aad) as CareResult.Success).value).decodeToString())
        assertTrue(cipher.decrypt(one.value, AndroidKeystoreCipher.aad("assessment", "a-2")) is CareResult.Failure)
        val tampered = one.value.dropLast(1) + if (one.value.last() == 'A') "B" else "A"
        assertTrue(cipher.decrypt(tampered, aad) is CareResult.Failure)
    }

    @Test fun repositoriesEnforceOwnerAndTeacherScopeAndRetryIsIdempotent() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, MoodDatabase::class.java).allowMainThreadQueries().build()
        try {
            val cipher = AndroidKeystoreCipher("test-${UUID.randomUUID()}")
            val systemSession = DemoSessionProvider(ActorContext("system", ActorRole.SYSTEM, DataDomain.DEMO))
            val studentOneSession = DemoSessionProvider(ActorContext("s-1", ActorRole.STUDENT, DataDomain.DEMO))
            val studentTwoSession = DemoSessionProvider(ActorContext("s-2", ActorRole.STUDENT, DataDomain.DEMO))
            val teacherSession = DemoSessionProvider(ActorContext("t-1", ActorRole.TEACHER, DataDomain.DEMO))
            val otherTeacherSession = DemoSessionProvider(ActorContext("t-2", ActorRole.TEACHER, DataDomain.DEMO))
            val admin = RoomCareAdministration(db.careDao(), systemSession, cipher)
            admin.upsertStudent("s-1", "DEMO-001")
            admin.upsertStudent("s-2", "DEMO-002")
            admin.assignTeacher("s-1", "t-1")
            val record = AssessmentRecord("assessment-1", "s-1", AssessmentType.PHQ_9, "1", List(9) { 1 }, 9, SymptomBand.MILD, AssessmentState.COMPLETED, 1000, "score-v1")
            assertTrue(RoomAssessmentRepository(db.careDao(), studentOneSession, cipher).saveForCurrentStudent(record) is CareResult.Success)
            assertTrue(RoomAssessmentRepository(db.careDao(), studentTwoSession, cipher).getForCurrentStudent("assessment-1") is CareResult.Failure)

            val event = AlertEvent("alert-1", "s-1", "DEMO-001", ConcernLevel.MODERATE, setOf("SCORE_BAND"), 1000, "risk-v1", "evaluation-1", "evaluation-1")
            assertTrue(RoomAlertRepository(db.careDao(), systemSession, cipher).saveInternal(event) is CareResult.Success)
            assertTrue(RoomAlertRepository(db.careDao(), otherTeacherSession, cipher).getTeacherSummary("alert-1") is CareResult.Failure)
            assertTrue(RoomAlertRepository(db.careDao(), teacherSession, cipher).getTeacherSummary("alert-1") is CareResult.Success)

            val case = InterventionCase("case-1", "s-1", "t-1", "alert-1", InterventionStatus.PENDING_CONFIRMATION, null, "minimal note")
            assertTrue(admin.createIntervention(case) is CareResult.Success)
            val interventions = RoomInterventionRepository(db, teacherSession, cipher)
            assertTrue(interventions.acknowledge("case-1", "ack-1") is CareResult.Success)
            val first = interventions.startWithAa(InterventionStartRequest("case-1", "follow-v1", "start-1")) as CareResult.Success
            val retry = interventions.startWithAa(InterventionStartRequest("case-1", "follow-v1", "start-1")) as CareResult.Success
            assertEquals(first.value.enrollment.enrollmentId, retry.value.enrollment.enrollmentId)
            assertEquals(1, db.query("SELECT COUNT(*) FROM care_aa_enrollments WHERE studentId='s-1' AND activeSlot='ACTIVE'", null).use { it.moveToFirst(); it.getInt(0) })
        } finally { db.close() }
    }

    @Test fun migrationFromV3PreservesExistingMoodAndAnniversaryRows() {
        val name = "migration-${UUID.randomUUID()}.db"
        val path = context.getDatabasePath(name)
        path.parentFile?.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(path, null).use { sqlite ->
            sqlite.execSQL("CREATE TABLE mood_records (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, date TEXT NOT NULL, time TEXT NOT NULL, moodType TEXT NOT NULL, note TEXT, content TEXT, imageUris TEXT, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL)")
            sqlite.execSQL("CREATE TABLE anniversary_records (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, title TEXT NOT NULL, targetDate TEXT NOT NULL, isCountdown INTEGER NOT NULL, colorHex TEXT NOT NULL, createdAt INTEGER NOT NULL)")
            sqlite.execSQL("INSERT INTO mood_records VALUES (1,'2026-09-17','12:00','CALM','kept',NULL,NULL,1,1)")
            sqlite.execSQL("INSERT INTO anniversary_records VALUES (1,'kept','2027-01-01',1,'#FF9800',1)")
            sqlite.version = 3
        }
        try {
            val db = Room.databaseBuilder(context, MoodDatabase::class.java, name).addMigrations(MoodDatabase.MIGRATION_3_4).allowMainThreadQueries().build()
            try {
                assertEquals(1, db.query("SELECT COUNT(*) FROM mood_records WHERE note='kept'", null).use { it.moveToFirst(); it.getInt(0) })
                assertEquals(1, db.query("SELECT COUNT(*) FROM anniversary_records WHERE title='kept'", null).use { it.moveToFirst(); it.getInt(0) })
            } finally { db.close() }
        } finally { context.deleteDatabase(name) }
    }
}
