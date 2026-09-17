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
import com.example.mdd_calender.integration.app.TeacherRoute

object Route {
    const val MAIN = "main"
    const val HOME = "home"
    const val CALENDAR = "calendar"
    const val EDITOR = "editor/{date}/{id}"
    const val SETTINGS = "settings"
    const val ANALYSIS = "analysis"
    const val ANNIVERSARY = "anniversary"
    const val DAY_DETAIL = "day_detail/{date}"
    const val ASSESSMENT = "assessment"
    const val HEALTH = "health"
    const val FOLLOW_UP = "follow_up"
    const val TEACHER = "teacher"
    
    fun createDayDetailRoute(date: String) = "day_detail/$date"
    fun createEditorRoute(date: String, id: Int) = "editor/$date/$id"
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
        startDestination = Route.MAIN,
        modifier = modifier
    ) {
        composable(Route.MAIN) {
            com.example.mdd_calender.ui.screens.MainScreen(
                parentNavController = navController,
                viewModel = viewModel,
                careServices = careServices,
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

        composable(Route.ASSESSMENT) {
            AssessmentRoute(requireNotNull(careServices), onBack = { navController.popBackStack() })
        }
        composable(Route.HEALTH) {
            HealthRoute(requireNotNull(careServices), onBack = { navController.popBackStack() })
        }
        composable(Route.FOLLOW_UP) {
            FollowUpRoute(requireNotNull(careServices), onBack = { navController.popBackStack() })
        }
        composable(Route.TEACHER) {
            TeacherRoute(requireNotNull(careServices), onBack = { navController.popBackStack() })
        }
    }
}
