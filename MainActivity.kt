package com.group4.pulse

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.*
import com.group4.pulse.ui.screens.*
import com.group4.pulse.ui.theme.*
import com.group4.pulse.viewmodel.RadarViewModel

/* Main entry point for the PULSE Android application. Hosts the Compose UI and initializes the app theme. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            RadarDetectionTheme {
                RadarApp()
            }
        }
    }
}

/* Root composable that sets up the app scaffold with navigation. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RadarApp() {
    val navController = rememberNavController()
    val viewModel: RadarViewModel = viewModel()  // Shared ViewModel across all screens

    Scaffold(
        topBar = {
            // Centered title bar with themed colors
            CenterAlignedTopAppBar(
                title = { Text("UWB Radar Detection System") },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        bottomBar = {
            RadarBottomNavigation(navController = navController)
        }
    ) { paddingValues ->
        // Main content area with proper insets
        RadarNavHost(
            navController = navController,
            viewModel = viewModel,
            modifier = Modifier.padding(paddingValues)
        )
    }
}

@Composable
fun RadarBottomNavigation(navController: NavHostController) {
    NavigationBar {
        // Track current route for selection highlighting
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = navBackStackEntry?.destination?.route

        // Dashboard - main connection and status screen
        NavigationBarItem(
            icon = { Icon(Icons.Default.Home, contentDescription = "Dashboard") },
            label = { Text("Dashboard") },
            selected = currentRoute == "dashboard",
            onClick = {
                navController.navigate("dashboard") {
                    popUpTo(navController.graph.startDestinationId)  // Avoid stacking duplicates
                    launchSingleTop = true
                }
            }
        )

        // Grid View - radar visualization screen
        NavigationBarItem(
            icon = { Icon(Icons.Default.LocationOn, contentDescription = "Visualize") },
            label = { Text("Grid View") },
            selected = currentRoute == "visualize",
            onClick = {
                navController.navigate("visualize") {
                    popUpTo(navController.graph.startDestinationId)
                    launchSingleTop = true
                }
            }
        )

        // Settings - BLE configuration and app preferences
        NavigationBarItem(
            icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
            label = { Text("Settings") },
            selected = currentRoute == "settings",
            onClick = {
                navController.navigate("settings") {
                    popUpTo(navController.graph.startDestinationId)
                    launchSingleTop = true
                }
            }
        )
    }
}

/* All screens share the same RadarViewModel for consistent state. */
@Composable
fun RadarNavHost(
    navController: NavHostController,
    viewModel: RadarViewModel,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = "dashboard",  // App opens to Dashboard
        modifier = modifier
    ) {
        composable("dashboard") {
            DashboardScreen(viewModel = viewModel)  // BLE connection & status
        }
        composable("visualize") {
            VisualizationScreen(viewModel = viewModel)  // Polar radar display
        }
        composable("settings") {
            SettingsScreen(viewModel = viewModel)  // Config options
        }
    }
}
