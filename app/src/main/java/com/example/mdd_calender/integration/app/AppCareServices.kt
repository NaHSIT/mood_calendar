package com.example.mdd_calender.integration.app

import android.content.Context
import com.example.mdd_calender.data.MoodDatabase
import com.example.mdd_calender.data.care.RoomAlertRepository
import com.example.mdd_calender.data.care.RoomAssessmentRepository
import com.example.mdd_calender.data.care.RoomAuditRepository
import com.example.mdd_calender.data.care.RoomConsentRepository
import com.example.mdd_calender.data.care.RoomEvaluationRepository
import com.example.mdd_calender.data.care.RoomFollowUpRepository
import com.example.mdd_calender.data.care.RoomInterventionRepository
import com.example.mdd_calender.data.care.RoomStudentHealthRepository
import com.example.mdd_calender.domain.model.ActorContext
import com.example.mdd_calender.domain.model.ActorRole
import com.example.mdd_calender.domain.model.DataDomain
import com.example.mdd_calender.domain.port.SessionProvider
import com.example.mdd_calender.feature.health.DemoHealthProvider
import com.example.mdd_calender.feature.health.DemoHealthScenario
import com.example.mdd_calender.feature.health.DomainHealthDataProviderAdapter
import com.example.mdd_calender.feature.risk.DemonstrationRiskEvaluator
import com.example.mdd_calender.security.AndroidKeystoreCipher
import com.example.mdd_calender.security.DemoSessionProvider

/**
 * Single composition root for care features.
 *
 * The demo identity is deliberately fixed to the DEMO data domain. Production
 * authentication must replace [session] before any real student data is used.
 */
class AppCareServices(context: Context) {
    private val database = MoodDatabase.getDatabase(context.applicationContext)
    val session: SessionProvider = DemoSessionProvider(
        ActorContext("demo-student", ActorRole.STUDENT, DataDomain.DEMO),
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
}
