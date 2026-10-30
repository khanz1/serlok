package com.khanz1.serlok.ui.main

import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.khanz1.serlok.R
import com.khanz1.serlok.navigation.Routes
import com.khanz1.serlok.ui.screens.home.HomeScreen
import com.khanz1.serlok.ui.screens.room.RoomViewModel
import com.khanz1.serlok.ui.screens.settings.SettingsScreen
import com.khanz1.serlok.ui.screens.settings.SettingsViewModel

private data class BottomTab(val route: String, val label: Int, val icon: ImageVector)

private val tabs = listOf(
    BottomTab(Routes.HOME, R.string.nav_home, Icons.Rounded.Home),
    BottomTab(Routes.SETTINGS, R.string.nav_settings, Icons.Rounded.Settings),
)

@Composable
fun MainScaffold(onEnterRoom: () -> Unit, onLogout: () -> Unit) {
    val navController = rememberNavController()
    val currentEntry by navController.currentBackStackEntryAsState()
    val activity = LocalActivity.current as? ComponentActivity ?: return
    val roomViewModel: RoomViewModel = viewModel(viewModelStoreOwner = activity)
    val settingsViewModel: SettingsViewModel = viewModel(viewModelStoreOwner = activity)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                tabs.forEach { tab ->
                    val label = stringResource(tab.label)
                    val selected = currentEntry?.destination?.hierarchy?.any { it.route == tab.route } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(tab.route) {
                                popUpTo(Routes.HOME) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(tab.icon, contentDescription = label) },
                        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier.padding(padding),
        ) {
            composable(Routes.HOME) {
                HomeScreen(roomViewModel, onEnterRoom)
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(roomViewModel, settingsViewModel, onLogout)
            }
        }
    }
}
