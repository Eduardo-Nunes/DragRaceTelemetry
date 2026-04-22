package com.eduardo.nunes.drt.app

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.rememberNavController
import com.eduardo.nunes.drt.di.appModule
import org.koin.compose.KoinApplication
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.logger.Level
import org.koin.dsl.koinConfiguration

@Composable
fun App() {
    KoinApplication(configuration = koinConfiguration {
        modules(appModule)
    }, logLevel = Level.INFO, content = {
        MaterialTheme(colorScheme = darkColorScheme()) {
            val snackbarHostState = remember { SnackbarHostState() }
            val coroutineScope = rememberCoroutineScope()
            val appMainViewModel = koinViewModel<AppMainViewModel>()
            val navController = rememberNavController()

            val state by appMainViewModel.state.collectAsState()
            Row {
                AnimatedVisibility(
                    visible = state.showNavigationRail,
                    enter = slideInHorizontally(initialOffsetX = { -it }) + expandHorizontally(),
                    exit = slideOutHorizontally(targetOffsetX = { -it }) + shrinkHorizontally()
                ) {
                    NavigationRail(
                        modifier = Modifier.fillMaxHeight().padding(top = 12.dp),
                        header = {
                            IconButton(onClick = {
                                appMainViewModel.handleIntent(AppMainContract.Intent.OpenCloseNavRail)
                            }) {
                                Icon(Icons.Filled.ChevronLeft, contentDescription = "Menu")
                            }
                        }
                    ) {
                        state.menuItems.forEach { item ->
                            NavigationRailItem(
                                icon = {
                                    Icon(item.icon, contentDescription = item.description)
                                },
                                label = { Text(item.title) },
                                selected = false,
                                onClick = {
                                    appMainViewModel.handleIntent(item.intent)
                                })
                        }
                    }
                }

                AppNavigation(
                    navController = navController,
                    appMainViewModel = appMainViewModel,
                    snackbarHostState = snackbarHostState,
                    coroutineScope = coroutineScope,
                    isMenuOpen = state.showNavigationRail,
                    onMenuClick = { appMainViewModel.handleIntent(AppMainContract.Intent.OpenCloseNavRail) })
            }
        }
    })
}
