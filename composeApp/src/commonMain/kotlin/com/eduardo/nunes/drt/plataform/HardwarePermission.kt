package com.eduardo.nunes.drt.plataform

import androidx.compose.runtime.Composable

@Composable
expect fun RequireBluetoothPermissions(content: @Composable () -> Unit)

@Composable
expect fun RequireLocationPermissions(content: @Composable () -> Unit)
