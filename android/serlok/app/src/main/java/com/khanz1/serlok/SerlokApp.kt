package com.khanz1.serlok

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.khanz1.serlok.data.AppSettings
import com.khanz1.serlok.navigation.Routes
import com.khanz1.serlok.ui.main.MainScaffold
import com.khanz1.serlok.ui.screens.auth.AuthScreen
import com.khanz1.serlok.ui.screens.onboarding.OnboardingScreen
import com.khanz1.serlok.ui.screens.room.RoomScreen
import com.khanz1.serlok.ui.theme.SerlokTheme

@Composable
fun SerlokApp() {
    SerlokTheme {
        val navController = rememberNavController()

        val startDestination = when {
            !AppSettings.onboardingDone -> Routes.ONBOARDING
            !AppSettings.isLoggedIn     -> Routes.AUTH
            else                        -> Routes.MAIN
        }

        NavHost(
            navController    = navController,
            startDestination = startDestination,
            enterTransition  = { fadeIn(tween(200)) + slideInHorizontally(tween(200)) { it / 4 } },
            exitTransition   = { fadeOut(tween(200)) + slideOutHorizontally(tween(200)) { -it / 4 } },
            popEnterTransition  = { fadeIn(tween(200)) + slideInHorizontally(tween(200)) { -it / 4 } },
            popExitTransition   = { fadeOut(tween(200)) + slideOutHorizontally(tween(200)) { it / 4 } },
        ) {
            composable(Routes.ONBOARDING) {
                OnboardingScreen(
                    onFinish = {
                        navController.navigate(Routes.AUTH) {
                            popUpTo(Routes.ONBOARDING) { inclusive = true }
                        }
                    }
                )
            }

            composable(Routes.AUTH) {
                AuthScreen(
                    onNavigateToHome = {
                        navController.navigate(Routes.MAIN) {
                            popUpTo(Routes.AUTH) { inclusive = true }
                        }
                    }
                )
            }

            composable(Routes.MAIN) {
                MainScaffold(
                    onEnterRoom = { navController.navigate(Routes.ROOM) },
                    onLogout    = {
                        navController.navigate(Routes.AUTH) {
                            popUpTo(0) { inclusive = true }
                        }
                    },
                )
            }

            composable(Routes.ROOM) {
                RoomScreen(
                    onLeaveRoom = { navController.popBackStack() },
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}
