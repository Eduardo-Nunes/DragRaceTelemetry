package com.eduardo.nunes.drt.app

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.eduardo.nunes.drt.features.history.HistoryScreen
import com.eduardo.nunes.drt.features.race.RaceDashboardScreen
import com.eduardo.nunes.drt.features.race.RaceTelemetryViewModel
import com.eduardo.nunes.drt.features.settings.SettingsScreen
import com.eduardo.nunes.drt.features.settings.SettingsViewModel
import com.eduardo.nunes.drt.features.splash.SplashScreen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun AppNavigation(
    navController: NavHostController,
    appMainViewModel: AppMainViewModel,
    snackbarHostState: SnackbarHostState,
    coroutineScope: CoroutineScope,
    modifier: Modifier = Modifier
) {
    // Ouve os eventos de Navegação vindos do AppMainViewModel
    LaunchedEffect(Unit) {
        appMainViewModel.effect.collectLatest { effect ->
            when (effect) {
                is AppMainContract.Effect.NavigateTo -> {
                    navController.navigate(effect.route) {
                        if (effect.route == "dashboard") {
                            popUpTo("splash") { inclusive = true }
                        }
                    }
                }

                is AppMainContract.Effect.NavigateBack -> {
                    navController.popBackStack()
                }
            }
        }
    }

    // Configuração do Jetpack Compose Navigation Multiplatform
    NavHost(
        navController = navController,
        startDestination = "splash",
        modifier = modifier,
        // Configuração Global de Animações
        enterTransition = {
            slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(200)) + fadeIn()
        },
        exitTransition = {
            slideOutHorizontally(targetOffsetX = { -it }, animationSpec = tween(200)) + fadeOut()
        },
        popEnterTransition = {
            slideInHorizontally(initialOffsetX = { -it }, animationSpec = tween(200)) + fadeIn()
        },
        popExitTransition = {
            slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(200)) + fadeOut()
        }
    ) {
        composable(
            route = "splash",
            enterTransition = { fadeIn(animationSpec = tween(400)) },
            exitTransition = { fadeOut(animationSpec = tween(400)) }
        ) {
            SplashScreen(
                onSplashFinished = {
                    appMainViewModel.handleIntent(AppMainContract.Intent.NavigateTo("dashboard"))
                }
            )
        }
        composable(
            "dashboard",
            enterTransition = { fadeIn(animationSpec = tween(400)) }
        ) {
            // Injeta o ViewModel específico desta tela
            val dashboardViewModel = koinViewModel<RaceTelemetryViewModel>()

            RaceDashboardScreen(
                viewModel = dashboardViewModel,
                onShowSnackbar = { message ->
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar(message)
                    }
                },
                onNavigateToSettings = {
                    appMainViewModel.handleIntent(
                        AppMainContract.Intent.NavigateTo(
                            "settings"
                        )
                    )
                },
                onNavigateToHistory = {
                    appMainViewModel.handleIntent(
                        AppMainContract.Intent.NavigateTo(
                            "history"
                        )
                    )
                }
            )
        }
        composable("settings") {
            // Injeta o ViewModel específico desta tela
            val settingsViewModel = koinViewModel<SettingsViewModel>()

            SettingsScreen(
                viewModel = settingsViewModel,
                onBack = {
                    appMainViewModel.handleIntent(AppMainContract.Intent.NavigateBack)
                }
            )
        }
        composable("history") {
            HistoryScreen(
                onBack = {
                    appMainViewModel.handleIntent(AppMainContract.Intent.NavigateBack)
                }
            )
        }
    }
}
