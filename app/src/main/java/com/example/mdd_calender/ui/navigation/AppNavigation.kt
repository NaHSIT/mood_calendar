package com.example.mdd_calender.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.mdd_calender.ui.screens.CalendarScreenV3
import com.example.mdd_calender.ui.screens.EditorScreen
import com.example.mdd_calender.ui.screens.HomeScreen
import com.example.mdd_calender.ui.screens.SettingsScreen
import com.example.mdd_calender.ui.MoodViewModel
import com.example.mdd_calender.integration.app.AppCareServices
import com.example.mdd_calender.integration.app.AssessmentRoute
import com.example.mdd_calender.integration.app.FollowUpRoute
import com.example.mdd_calender.integration.app.HealthRoute
import com.example.mdd_calender.ui.screens.DemoRoleEntryScreen
import com.example.mdd_calender.ui.screens.TeacherMainScreen
import com.example.mdd_calender.feature.teacher.TeacherWorkbenchViewModel
import com.example.mdd_calender.domain.model.ActorRole

object Route {
    const val ENTRY = "entry"
    const val MAIN = "main"
    const val TEACHER_MAIN = "teacher_main"
    const val HOME = "home"
    const val CALENDAR = "calendar"
    const val EDITOR = "editor/{date}/{id}"
    const val SETTINGS = "settings"
    const val ANALYSIS = "analysis"
    const val ANNIVERSARY = "anniversary"
    const val DAY_DETAIL = "day_detail/{date}"
    const val ASSESSMENT = "assessment?followUpTaskId={followUpTaskId}"
    const val HEALTH = "health"
    
    fun createDayDetailRoute(date: String) = "day_detail/$date"
    fun createEditorRoute(date: String, id: Int) = "editor/$date/$id"
    fun createAssessmentRoute(followUpTaskId: String? = null) =
        if (followUpTaskId == null) "assessment" else "assessment?followUpTaskId=${android.net.Uri.encode(followUpTaskId)}"
}

@Composable
fun AppNavigation(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    viewModel: MoodViewModel,
    careServices: AppCareServices? = null,
) {
    NavHost(
        navController = navController,
        startDestination = Route.ENTRY,
        modifier = modifier
    ) {
        composable(Route.ENTRY) {
            fun enter(role: ActorRole) {
                val destination = when (landingForRole(role)) {
                    RoleLanding.STUDENT_SPACE -> Route.MAIN
                    RoleLanding.TEACHER_WORKBENCH -> Route.TEACHER_MAIN
                    RoleLanding.DENIED -> return
                }
                navController.navigate(destination) { launchSingleTop = true }
            }
            DemoRoleEntryScreen(
                onStudentDemo = { enter(ActorRole.STUDENT) },
                onTeacherDemo = { enter(ActorRole.TEACHER) },
            )
        }
        composable(Route.MAIN) {
            com.example.mdd_calender.ui.screens.MainScreen(
                parentNavController = navController,
                viewModel = viewModel,
                careServices = careServices,
                onLogout = {
                    navController.navigate(Route.ENTRY) {
                        popUpTo(Route.MAIN) { inclusive = true }
                        launchSingleTop = true
                    }
                },
            )
        }
        composable(Route.TEACHER_MAIN) {
            val services = requireNotNull(careServices)
            TeacherMainScreen(
                viewModel = androidx.lifecycle.viewmodel.compose.viewModel {
                    TeacherWorkbenchViewModel(services.teacherService)
                },
                onLogout = {
                    navController.navigate(Route.ENTRY) {
                        popUpTo(Route.TEACHER_MAIN) { inclusive = true }
                        launchSingleTop = true
                    }
                },
            )
        }
        
        composable(
            route = Route.DAY_DETAIL,
            arguments = listOf(navArgument("date") { type = NavType.StringType })
        ) { backStackEntry ->
            val date = backStackEntry.arguments?.getString("date") ?: ""
            com.example.mdd_calender.ui.screens.DayDetailScreen(
                viewModel = viewModel,
                date = date,
                onBack = { navController.popBackStack() },
                onNavigateToEditor = { id -> 
                    navController.navigate(Route.createEditorRoute(date, id)) 
                }
            )
        }

        composable(
            route = Route.EDITOR,
            arguments = listOf(
                navArgument("date") { type = NavType.StringType },
                navArgument("id") { type = NavType.IntType }
            )
        ) { backStackEntry ->
            val date = backStackEntry.arguments?.getString("date") ?: ""
            val id = backStackEntry.arguments?.getInt("id") ?: 0
            EditorScreen(
                viewModel = viewModel,
                date = date,
                id = id,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Route.SETTINGS) {
            SettingsScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }

        // Analysis is now inside MainScreen
        // composable(Route.ANALYSIS) is removed from root nav graph

        composable(Route.ANNIVERSARY) {
            com.example.mdd_calender.ui.screens.AnniversaryScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Route.ASSESSMENT,
            arguments = listOf(navArgument("followUpTaskId") { type = NavType.StringType; nullable = true; defaultValue = null }),
        ) { backStackEntry ->
            AssessmentRoute(
                requireNotNull(careServices),
                followUpTaskId = backStackEntry.arguments?.getString("followUpTaskId"),
                onBack = { navController.popBackStack() },
            )
        }
        composable(Route.HEALTH) {
            HealthRoute(requireNotNull(careServices), onBack = { navController.popBackStack() })
        }
    }
}
