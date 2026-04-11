package com.eduardo.nunes.drt.plataform

import androidx.compose.runtime.Composable

@Composable
actual fun RequireBluetoothPermissions(content: @Composable () -> Unit) {
    // No iOS, o Kable e o CoreBluetooth gerenciam automaticamente a requisição 
    // de permissões de Bluetooth quando tentamos inicializar o Scanner.
    content()
}

@Composable
actual fun RequireLocationPermissions(content: @Composable () -> Unit) {
    // CoreLocation lida com permissão via iOS settings
    content()
}
