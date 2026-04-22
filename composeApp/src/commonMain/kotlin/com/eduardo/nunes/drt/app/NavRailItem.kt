package com.eduardo.nunes.drt.app

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

enum class NavRailItem(
    val icon: ImageVector,
    val title: String,
    val description: String,
    val intent: AppMainContract.Intent
) {
    HISTORY(
        Icons.Default.History,
        "Histórico",
        "Histórico",
        AppMainContract.Intent.NavigateTo("history")
    ),
    SETTINGS(
        Icons.Default.Settings,
        "Configurações",
        "Configurações",
        AppMainContract.Intent.NavigateTo("settings")
    ),
    CLEAR_LOGS(
        Icons.Default.CleaningServices,
        "Limpar Logs",
        "Limpar Logs",
        AppMainContract.Intent.ClearLogs
    ),
}