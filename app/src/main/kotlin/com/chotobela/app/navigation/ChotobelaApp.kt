package com.chotobela.app.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.chotobela.feature.download.DownloadsScreen
import com.chotobela.feature.home.HomeScreen
import com.chotobela.feature.library.LibraryScreen
import com.chotobela.feature.player.PlayerRoute
import com.chotobela.feature.profile.ProfileScreen
import com.chotobela.feature.settings.SettingsScreen
import com.chotobela.feature.store.GameDetailRoute
import com.chotobela.feature.store.StoreScreen

@Composable
fun ChotobelaApp(navController: NavHostController = rememberNavController()) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = Destination.fromRoute(backStackEntry?.destination?.route)
    val showBottomBar = currentDestination != null

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    Destination.entries.forEach { destination ->
                        NavigationBarItem(
                            selected = destination == currentDestination,
                            onClick = { navController.navigateToTab(destination) },
                            icon = { Icon(destination.icon, contentDescription = null) },
                            label = { Text(androidx.compose.ui.res.stringResource(destination.labelRes)) }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Destination.HOME.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Destination.HOME.route) {
                HomeScreen(
                    onGameClick = { gameId -> navController.navigate(Routes.gameDetails(gameId)) },
                    onSeeAllLibrary = { navController.navigateToTab(Destination.LIBRARY) }
                )
            }
            composable(Destination.LIBRARY.route) {
                LibraryScreen(
                    onPlayGame = { gameId -> navController.navigate(Routes.player(gameId)) }
                )
            }
            composable(Destination.STORE.route) {
                StoreScreen(
                    onGameClick = { gameId -> navController.navigate(Routes.gameDetails(gameId)) }
                )
            }
            composable(Destination.DOWNLOADS.route) {
                DownloadsScreen()
            }
            composable(Destination.PROFILE.route) {
                ProfileScreen(onOpenSettings = { navController.navigate(Routes.SETTINGS) })
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(onBack = { navController.popBackStack() })
            }
            composable(
                route = Routes.GAME_DETAILS,
                arguments = listOf(navArgument("gameId") { type = NavType.StringType })
            ) {
                GameDetailRoute(
                    onBack = { navController.popBackStack() },
                    onDownloaded = { navController.navigateToTab(Destination.DOWNLOADS) }
                )
            }
            composable(
                route = Routes.PLAYER,
                arguments = listOf(navArgument("gameId") { type = NavType.StringType })
            ) {
                PlayerRoute(onExit = { navController.popBackStack() })
            }
        }
    }
}

private fun NavHostController.navigateToTab(destination: Destination) {
    navigate(destination.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
