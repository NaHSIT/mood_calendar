package com.example.mdd_calender.ui.screens

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.AssignmentTurnedIn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.compose.ui.unit.dp
import com.example.mdd_calender.ui.MoodViewModel
import com.example.mdd_calender.ui.navigation.Route
import com.example.mdd_calender.integration.app.AppCareServices
import com.example.mdd_calender.integration.app.FollowUpRoute

sealed class BottomNavItem(val route: String, val title: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    object Home : BottomNavItem(Route.HOME, "情绪日历", Icons.Default.Home)
    object Analysis : BottomNavItem(Route.ANALYSIS, "数据洞察", Icons.Default.Info)
    object FollowUp : BottomNavItem("student_follow_up", "随访任务", Icons.Default.AssignmentTurnedIn)
    object Profile : BottomNavItem("student_profile", "我的", Icons.Default.Person)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    parentNavController: NavHostController,
    viewModel: MoodViewModel,
    careServices: AppCareServices? = null,
    onLogout: () -> Unit = {},
) {
    val bottomNavController = rememberNavController()
    val items = listOf(
        BottomNavItem.Home,
        BottomNavItem.Analysis,
        BottomNavItem.FollowUp,
        BottomNavItem.Profile,
    )

    Scaffold(
        bottomBar = {
            val navBackStackEntry by bottomNavController.currentBackStackEntryAsState()
            val currentRoute = navBackStackEntry?.destination?.route

            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f),
                tonalElevation = 8.dp
            ) {
                items.forEach { item ->
                    NavigationBarItem(
                        icon = { Icon(item.icon, contentDescription = item.title) },
                        label = { Text(item.title) },
                        selected = currentRoute == item.route || (item == BottomNavItem.Home && currentRoute == Route.CALENDAR),
                        onClick = {
                            if (currentRoute != item.route) {
                                bottomNavController.navigate(item.route) {
                                    // Pop up to the start destination of the graph to
                                    // avoid building up a large stack of destinations
                                    // on the back stack as users select items
                                    popUpTo(bottomNavController.graph.startDestinationId) {
                                        saveState = true
                                    }
                                    // Avoid multiple copies of the same destination when
                                    // reselecting the same item
                                    launchSingleTop = true
                                    // Restore state when reselecting a previously selected item
                                    restoreState = true
                                }
                            }
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        // We use a nested NavHost for the bottom navigation area
        NavHost(
            navController = bottomNavController,
            startDestination = Route.HOME,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Route.HOME) {
                HomeScreen(
                    viewModel = viewModel,
                    onNavigateToCalendar = { bottomNavController.navigate(Route.CALENDAR) },
                    onNavigateToDayDetail = { date ->
                        parentNavController.navigate(Route.createDayDetailRoute(date))
                    },
                    onNavigateToSettings = { parentNavController.navigate(Route.SETTINGS) },
                    onNavigateToAnalysis = { bottomNavController.navigate(Route.ANALYSIS) },
                    onNavigateToAnniversary = { parentNavController.navigate(Route.ANNIVERSARY) },
                    onNavigateToAssessment = { parentNavController.navigate(Route.createAssessmentRoute()) },
                    onNavigateToFollowUp = { bottomNavController.navigate("student_follow_up") },
                    careServices = careServices,
                )
            }
            composable(Route.CALENDAR) {
                CalendarScreenV3(
                    viewModel = viewModel,
                    onBack = { bottomNavController.popBackStack() },
                    onDateClick = { date ->
                        parentNavController.navigate(Route.createDayDetailRoute(date.toString()))
                    }
                )
            }
            composable(Route.ANALYSIS) {
                AnalysisScreen(
                    viewModel = viewModel,
                    careServices = careServices,
                    onAssessment = { parentNavController.navigate(Route.createAssessmentRoute()) },
                    onDayHistory = { date -> parentNavController.navigate(Route.createDayDetailRoute(date)) },
                    onNewEntry = { parentNavController.navigate(Route.createEditorRoute(java.time.LocalDate.now().toString(), 0)) },
                    onBack = { bottomNavController.navigate(Route.HOME) { launchSingleTop = true } }
                )
            }
            composable("student_follow_up") {
                FollowUpRoute(
                    services = requireNotNull(careServices),
                    onAssessmentTask = { parentNavController.navigate(Route.createAssessmentRoute(it)) },
                    onBack = { bottomNavController.navigate(Route.HOME) { launchSingleTop = true } },
                    showBack = false,
                )
            }
            composable("student_profile") {
                StudentProfileScreen(
                    onHealth = { parentNavController.navigate(Route.HEALTH) },
                    onSettings = { parentNavController.navigate(Route.SETTINGS) },
                    onLogout = onLogout,
                )
            }
        }
    }
}
