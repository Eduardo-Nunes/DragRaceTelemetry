package com.eduardo.nunes.drt.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.navigation.compose.rememberNavController
import com.eduardo.nunes.drt.plataform.RequireBluetoothPermissions
import com.eduardo.nunes.drt.di.appModule
import org.koin.compose.KoinApplication
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun App() {
    // Inicialização do Koin na raiz da aplicação
    KoinApplication(application = {
        modules(appModule)
    }) {
        MaterialTheme(colorScheme = darkColorScheme()) {
            val snackbarHostState = remember { SnackbarHostState() }
            val coroutineScope = rememberCoroutineScope()
            
            // Injeta o ViewModel Principal (para navegação e controle global)
            val appMainViewModel = koinViewModel<AppMainViewModel>()

            RequireBluetoothPermissions {
                val navController = rememberNavController()

                AppNavigation(
                    navController,
                    appMainViewModel,
                    snackbarHostState,
                    coroutineScope
                )
            }
        }
    }
}
