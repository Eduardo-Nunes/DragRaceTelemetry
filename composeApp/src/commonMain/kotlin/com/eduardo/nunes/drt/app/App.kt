package com.eduardo.nunes.drt.app

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.eduardo.nunes.drt.di.appModule
import org.koin.compose.KoinApplication
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun App() {
    KoinApplication(application = {
        modules(appModule)
    }) {
        MaterialTheme(colorScheme = darkColorScheme()) {
            val snackbarHostState = remember { SnackbarHostState() }
            val coroutineScope = rememberCoroutineScope()
            val appMainViewModel = koinViewModel<AppMainViewModel>()
            val navController = rememberNavController()

            var showNavigationRail by remember { mutableStateOf(false) }
            Row {
                AnimatedVisibility(
                    visible = showNavigationRail,
                    enter = slideInHorizontally(initialOffsetX = { -it }) + expandHorizontally(),
                    exit = slideOutHorizontally(targetOffsetX = { -it }) + shrinkHorizontally()
                ) {
                    NavigationRail(
                        modifier = Modifier.fillMaxHeight(),
                        header = {
                            IconButton(onClick = { showNavigationRail = false }) {
                                Icon(Icons.Filled.Menu, contentDescription = "Menu")
                            }
                        }
                    ) {
                        NavigationRailItem(
                            icon = { Icon(Icons.Default.History, contentDescription = "Histórico") },
                            label = { Text("Histórico") },
                            selected = false,
                            onClick = {
                                appMainViewModel.handleIntent(AppMainContract.Intent.NavigateTo("history"))
                                showNavigationRail = false
                            }
                        )
                        NavigationRailItem(
                            icon = { Icon(Icons.Default.Settings, contentDescription = "Configurações") },
                            label = { Text("Configurações") },
                            selected = false,
                            onClick = {
                                appMainViewModel.handleIntent(AppMainContract.Intent.NavigateTo("settings"))
                                showNavigationRail = false
                            }
                        )
                        NavigationRailItem(
                            icon = { Icon(Icons.Default.CleaningServices, contentDescription = "Limpar Logs") },
                            label = { Text("Limpar Logs") },
                            selected = false,
                            onClick = {
                                appMainViewModel.handleIntent(AppMainContract.Intent.ClearLogs)
                                showNavigationRail = false
                            }
                        )
                    }
                }

                AppNavigation(
                    navController = navController,
                    appMainViewModel = appMainViewModel,
                    snackbarHostState = snackbarHostState,
                    coroutineScope = coroutineScope,
                    isMenuOpen = showNavigationRail,
                    onMenuClick = { showNavigationRail = !showNavigationRail }
                )
            }
        }
    }
}
