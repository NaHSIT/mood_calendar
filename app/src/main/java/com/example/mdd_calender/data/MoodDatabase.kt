package com.example.mdd_calender.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.mdd_calender.data.care.AaEnrollmentEntity
import com.example.mdd_calender.data.care.AlertEntity
import com.example.mdd_calender.data.care.AssessmentEntity
import com.example.mdd_calender.data.care.AuditEntity
import com.example.mdd_calender.data.care.CareDao
import com.example.mdd_calender.data.care.ConsentEntity
import com.example.mdd_calender.data.care.DeliveryEntity
import com.example.mdd_calender.data.care.EvaluationEntity
import com.example.mdd_calender.data.care.FollowUpTaskEntity
import com.example.mdd_calender.data.care.HealthSampleEntity
import com.example.mdd_calender.data.care.InterventionEntity
import com.example.mdd_calender.data.care.SignalEntity
import com.example.mdd_calender.data.care.StudentEntity
import com.example.mdd_calender.data.care.TeacherAssignmentEntity

@Database(
    entities = [
        MoodRecord::class, AnniversaryRecord::class, StudentEntity::class,
        TeacherAssignmentEntity::class, AssessmentEntity::class, ConsentEntity::class,
        HealthSampleEntity::class, SignalEntity::class, EvaluationEntity::class,
        AlertEntity::class, DeliveryEntity::class, InterventionEntity::class,
        AaEnrollmentEntity::class, FollowUpTaskEntity::class, AuditEntity::class,
    ],
    version = 4,
    exportSchema = false,
)
abstract class MoodDatabase : RoomDatabase() {
    abstract fun moodDao(): MoodDao
    abstract fun anniversaryDao(): AnniversaryDao
    abstract fun careDao(): CareDao

    companion object {
        @Volatile
        private var INSTANCE: MoodDatabase? = null

        fun getDatabase(context: Context): MoodDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    MoodDatabase::class.java,
                    "mood_database"
                )
                .addMigrations(MIGRATION_3_4)
                .build()
                INSTANCE = instance
                instance
            }
        }

        /** Only v3 is evidenced in repository history. Unknown v1/v2 databases fail closed. */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `care_students` (`studentId` TEXT NOT NULL, `studentCode` TEXT NOT NULL, `dataDomain` TEXT NOT NULL, PRIMARY KEY(`studentId`, `dataDomain`))")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_care_students_studentCode_dataDomain` ON `care_students` (`studentCode`, `dataDomain`)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `care_teacher_assignments` (`teacherId` TEXT NOT NULL, `studentId` TEXT NOT NULL, `dataDomain` TEXT NOT NULL, PRIMARY KEY(`teacherId`, `studentId`, `dataDomain`))")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_care_teacher_assignments_studentId` ON `care_teacher_assignments` (`studentId`)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `care_assessments` (`assessmentId` TEXT NOT NULL, `studentId` TEXT NOT NULL, `dataDomain` TEXT NOT NULL, `type` TEXT NOT NULL, `instrumentVersion` TEXT NOT NULL, `encryptedAnswers` TEXT NOT NULL, `totalScore` INTEGER NOT NULL, `symptomBand` TEXT NOT NULL, `state` TEXT NOT NULL, `completedAtEpochMillis` INTEGER, `scoringVersion` TEXT NOT NULL, PRIMARY KEY(`assessmentId`))")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_care_assessments_studentId_dataDomain` ON `care_assessments` (`studentId`, `dataDomain`)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `care_consents` (`studentId` TEXT NOT NULL, `dataDomain` TEXT NOT NULL, `scopes` TEXT NOT NULL, `state` TEXT NOT NULL, `revision` INTEGER NOT NULL, `changedAtEpochMillis` INTEGER NOT NULL, PRIMARY KEY(`studentId`, `dataDomain`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `care_health_samples` (`sampleId` TEXT NOT NULL, `ownerId` TEXT NOT NULL, `dataDomain` TEXT NOT NULL, `source` TEXT NOT NULL, `type` TEXT NOT NULL, `measuredAtEpochMillis` INTEGER NOT NULL, `encryptedValueAndUnit` TEXT NOT NULL, `quality` TEXT NOT NULL, `simulated` INTEGER NOT NULL, `consentRevision` INTEGER NOT NULL, PRIMARY KEY(`sampleId`))")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_care_health_samples_ownerId_dataDomain_measuredAtEpochMillis` ON `care_health_samples` (`ownerId`, `dataDomain`, `measuredAtEpochMillis`)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `care_signals` (`signalId` TEXT NOT NULL, `studentId` TEXT NOT NULL, `dataDomain` TEXT NOT NULL, `type` TEXT NOT NULL, `windowStartEpochMillis` INTEGER NOT NULL, `windowEndEpochMillis` INTEGER NOT NULL, `quality` TEXT NOT NULL, `validUntilEpochMillis` INTEGER NOT NULL, `ruleVersion` TEXT NOT NULL, `consentRevision` INTEGER NOT NULL, `simulated` INTEGER NOT NULL, PRIMARY KEY(`signalId`))")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_care_signals_studentId_dataDomain_validUntilEpochMillis` ON `care_signals` (`studentId`, `dataDomain`, `validUntilEpochMillis`)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `care_evaluations` (`evaluationId` TEXT NOT NULL, `studentId` TEXT NOT NULL, `dataDomain` TEXT NOT NULL, `assessmentIds` TEXT NOT NULL, `baseConcernLevel` TEXT NOT NULL, `dispositionPriority` TEXT NOT NULL, `auxiliaryTags` TEXT NOT NULL, `dataSufficiency` TEXT NOT NULL, `ruleVersion` TEXT NOT NULL, `generatedAtEpochMillis` INTEGER NOT NULL, PRIMARY KEY(`evaluationId`))")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_care_evaluations_studentId_dataDomain` ON `care_evaluations` (`studentId`, `dataDomain`)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `care_alerts` (`eventId` TEXT NOT NULL, `studentId` TEXT NOT NULL, `studentCode` TEXT NOT NULL, `dataDomain` TEXT NOT NULL, `concernLevel` TEXT NOT NULL, `minimalReasonTags` TEXT NOT NULL, `occurredAtEpochMillis` INTEGER NOT NULL, `ruleVersion` TEXT NOT NULL, `evaluationId` TEXT NOT NULL, `deduplicationKey` TEXT NOT NULL, `disposition` TEXT NOT NULL, PRIMARY KEY(`eventId`))")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_care_alerts_deduplicationKey_dataDomain` ON `care_alerts` (`deduplicationKey`, `dataDomain`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_care_alerts_studentId` ON `care_alerts` (`studentId`)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `care_deliveries` (`deliveryId` TEXT NOT NULL, `alertId` TEXT NOT NULL, `recipientId` TEXT NOT NULL, `dataDomain` TEXT NOT NULL, `attemptCount` INTEGER NOT NULL, `nextRetryAtEpochMillis` INTEGER, `simulated` INTEGER NOT NULL, `idempotencyKey` TEXT NOT NULL, `status` TEXT NOT NULL, `encryptedReceipt` TEXT, PRIMARY KEY(`deliveryId`))")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_care_deliveries_idempotencyKey_dataDomain` ON `care_deliveries` (`idempotencyKey`, `dataDomain`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_care_deliveries_alertId` ON `care_deliveries` (`alertId`)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `care_interventions` (`caseId` TEXT NOT NULL, `studentId` TEXT NOT NULL, `assignedTeacherId` TEXT NOT NULL, `alertId` TEXT NOT NULL, `dataDomain` TEXT NOT NULL, `status` TEXT NOT NULL, `startedAtEpochMillis` INTEGER, `encryptedMinimalActionNote` TEXT, `lastIdempotencyKey` TEXT, PRIMARY KEY(`caseId`))")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_care_interventions_studentId` ON `care_interventions` (`studentId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_care_interventions_assignedTeacherId_dataDomain` ON `care_interventions` (`assignedTeacherId`, `dataDomain`)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `care_aa_enrollments` (`enrollmentId` TEXT NOT NULL, `studentId` TEXT NOT NULL, `interventionId` TEXT NOT NULL, `dataDomain` TEXT NOT NULL, `enrolledAtEpochMillis` INTEGER NOT NULL, `status` TEXT NOT NULL, `activeSlot` TEXT, `followUpPolicyVersion` TEXT NOT NULL, `encryptedExitReview` TEXT, PRIMARY KEY(`enrollmentId`))")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_care_aa_enrollments_studentId_dataDomain_activeSlot` ON `care_aa_enrollments` (`studentId`, `dataDomain`, `activeSlot`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_care_aa_enrollments_interventionId` ON `care_aa_enrollments` (`interventionId`)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `care_follow_up_tasks` (`taskId` TEXT NOT NULL, `studentId` TEXT NOT NULL, `dataDomain` TEXT NOT NULL, `type` TEXT NOT NULL, `dueAtEpochMillis` INTEGER NOT NULL, `completedAtEpochMillis` INTEGER, `relatedAssessmentId` TEXT, `status` TEXT NOT NULL, PRIMARY KEY(`taskId`))")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_care_follow_up_tasks_studentId_dataDomain_dueAtEpochMillis` ON `care_follow_up_tasks` (`studentId`, `dataDomain`, `dueAtEpochMillis`)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `care_audit_events` (`auditId` TEXT NOT NULL, `actorId` TEXT NOT NULL, `dataDomain` TEXT NOT NULL, `action` TEXT NOT NULL, `objectCode` TEXT NOT NULL, `result` TEXT NOT NULL, `occurredAtEpochMillis` INTEGER NOT NULL, PRIMARY KEY(`auditId`))")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_care_audit_events_dataDomain_occurredAtEpochMillis` ON `care_audit_events` (`dataDomain`, `occurredAtEpochMillis`)")
            }
        }
    }
}
