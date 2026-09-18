package com.example.mdd_calender

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.mdd_calender.data.MoodDatabase
import com.example.mdd_calender.domain.model.CareResult
import com.example.mdd_calender.feature.assessment.*
import com.example.mdd_calender.feature.teacher.TeacherWorkbenchRoute
import com.example.mdd_calender.feature.teacher.TeacherWorkbenchViewModel
import com.example.mdd_calender.integration.app.AppCareServices
import com.example.mdd_calender.integration.app.AssessmentRoute
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.rules.Timeout

@RunWith(AndroidJUnit4::class)
class CareUiInstrumentedTest {
    @get:Rule(order = 0) val timeout: Timeout = Timeout.seconds(45)
    @get:Rule(order = 1) val compose = createComposeRule()
    private lateinit var db: MoodDatabase
    private lateinit var services: AppCareServices
    @Before fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, MoodDatabase::class.java).build()
        services = AppCareServices(context, db)
    }
    @After fun cleanup() { db.close() }

    @Test fun questionnaireDraftSurvivesSavedStateRestorationAndTypeSwitch() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent { MaterialTheme { AssessmentRoute(services, onBack = {}) } }
        compose.onNodeWithTag("answer-0-2").performScrollTo().performClick()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("answer-0-2").performScrollTo().assertIsSelected()
        compose.onNodeWithTag("assessment-list").performScrollToIndex(0)
        compose.onNodeWithText("GAD-7", substring = false).performClick()
        compose.onNodeWithTag("answer-0-1").performScrollTo().performClick()
        compose.onNodeWithTag("assessment-list").performScrollToIndex(0)
        compose.onNodeWithText("PHQ-9", substring = false).performClick()
        compose.onNodeWithTag("answer-0-2").performScrollTo().assertIsSelected()
    }

    @Test fun completeQuestionnaireSubmitsOnceAndDisplaysSuccess() {
        compose.setContent { MaterialTheme { AssessmentRoute(services, onBack = {}) } }
        for (index in 0..8) {
            compose.onNodeWithTag("assessment-list").performScrollToIndex(index + 1)
            compose.onNodeWithTag("answer-$index-${if (index < 5) 2 else 0}").performScrollTo().performClick()
        }
        compose.onNodeWithTag("assessment-list").performScrollToIndex(10)
        compose.onNodeWithText("提交量表").performScrollTo().performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("已提交").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("已提交").assertIsNotEnabled()
        runBlocking {
            assertEquals(1, (services.assessments.listForCurrentStudent() as CareResult.Success).value.size)
            assertEquals(1, (services.teacherService.loadInbox() as CareResult.Success).value.size)
        }
    }

    @Test fun teacherConfirmationRefreshesDetailAndAllowsStartAndClosure() {
        runBlocking {
            val answers = listOf(2,2,2,2,2,0,0,0,0)
            val score = AssessmentScorer.score(AssessmentType.PHQ_9, answers) as ScoreResult.Complete
            val result = services.submitAssessmentAndTriggerCare(score.toAssessmentRecord("ui-teacher", "demo-student", "test", answers, System.currentTimeMillis()))
            check(result is CareResult.Success) { result }
        }
        lateinit var vm: TeacherWorkbenchViewModel
        compose.runOnUiThread { vm = TeacherWorkbenchViewModel(services.teacherService) }
        compose.setContent { MaterialTheme { TeacherWorkbenchRoute(vm) } }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("学生代号：DEMO-001").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("学生代号：DEMO-001").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("确认预警").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("确认预警").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("启动干预").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("启动干预").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("联系及处理记录（必填）").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("联系及处理记录（必填）").performScrollTo().performTextInput("演示处理记录")
        compose.onNodeWithText("记录处理并结束本次干预").performScrollTo().performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("本次干预已结束").fetchSemanticsNodes().isNotEmpty() }
    }
}
