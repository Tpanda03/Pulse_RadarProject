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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RadarApp() {
    val navController = rememberNavController()
    val viewModel: RadarViewModel = viewModel()

    Scaffold(
        topBar = {
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
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = navBackStackEntry?.destination?.route

        NavigationBarItem(
            icon = { Icon(Icons.Default.Home, contentDescription = "Dashboard") },
            label = { Text("Dashboard") },
            selected = currentRoute == "dashboard",
            onClick = {
                navController.navigate("dashboard") {
                    popUpTo(navController.graph.startDestinationId)
                    launchSingleTop = true
                }
            }
        )

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

@Composable
fun RadarNavHost(
    navController: NavHostController,
    viewModel: RadarViewModel,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = "dashboard",
        modifier = modifier
    ) {
        composable("dashboard") {
            DashboardScreen(viewModel = viewModel)
        }
        composable("visualize") {
            VisualizationScreen(viewModel = viewModel)
        }
        composable("settings") {
            SettingsScreen(viewModel = viewModel)
        }
    }
}