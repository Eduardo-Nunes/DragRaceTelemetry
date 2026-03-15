package com.eduardo.nunes.drt.features.race

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.collectLatest
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
fun RaceDashboardScreen(
    viewModel: RaceTelemetryViewModel,
    onShowSnackbar: (String) -> Unit,
    onNavigateToSettings: () -> Unit
) {
    val state by viewModel.state.collectAsState()

    // Ouve os Effects (Eventos disparados pelo ViewModel)
    LaunchedEffect(Unit) {
        viewModel.effect.collectLatest { effect ->
            when (effect) {
                is RaceTelemetryContract.Effect.ShowError -> onShowSnackbar(effect.message)
                is RaceTelemetryContract.Effect.RaceCompleted -> {
                    onShowSnackbar("Puxada 0-100 concluída em ${effect.formattedTime} segundos!")
                }
            }
        }
    }

    RaceDashboardContent(
        state = state,
        onIntent = viewModel::handleIntent,
        onNavigateToSettings = onNavigateToSettings
    )
}

@Composable
fun RaceDashboardContent(
    state: RaceTelemetryContract.State,
    onIntent: (RaceTelemetryContract.Intent) -> Unit,
    onNavigateToSettings: () -> Unit
) {
    // Forçando um Dark Theme para o Dashboard Automotivo
    MaterialTheme(colorScheme = darkColorScheme()) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {

                // Top Section: Configurações + Status + Terminal
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Ícone de engrenagem alinhado à direita
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                        contentAlignment = Alignment.CenterEnd
                    ) {
                        IconButton(
                            onClick = onNavigateToSettings,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Configurações",
                                tint = Color.Gray
                            )
                        }
                    }

                    // Header: Status do Bluetooth
                    StatusHeader(status = state.bluetoothStatus)

                    // Terminal View (Mostrado apenas se Ativado)
                    if (state.showTerminalLogs) {
                        TerminalLogComponent(logs = state.terminalLogs)
                    }
                }

                // Central: Velocímetro
                SpeedometerSection(speed = state.currentSpeed, rpm = state.currentRpm)

                // Timer: Cronômetro de precisão (0-100 km/h)
                TimerSection(
                    formattedTime = state.formattedTimer0to100,
                    hasStarted = state.timer0to100 != null,
                    isRecording = state.isRecording
                )

                // Footer: Botões de Ação
                ActionButtons(
                    isRecording = state.isRecording,
                    bluetoothStatus = state.bluetoothStatus,
                    onScanClick = { onIntent(RaceTelemetryContract.Intent.StartScanning) },
                    onConnectClick = { onIntent(RaceTelemetryContract.Intent.ConnectToDevice) },
                    onDisconnectClick = { onIntent(RaceTelemetryContract.Intent.DisconnectDevice) },
                    onArmClick = { onIntent(RaceTelemetryContract.Intent.StartRace) },
                    onStopClick = { onIntent(RaceTelemetryContract.Intent.StopRace) }
                )
            }
        }
    }
}

@Composable
fun StatusHeader(status: RaceTelemetryContract.BluetoothStatus) {
    val statusText = when (status) {
        is RaceTelemetryContract.BluetoothStatus.Disconnected -> "Desconectado"
        is RaceTelemetryContract.BluetoothStatus.Scanning -> "Procurando OBD2..."
        is RaceTelemetryContract.BluetoothStatus.DeviceFound -> "Dispositivo Encontrado! Conectando..."
        is RaceTelemetryContract.BluetoothStatus.Connected -> "Conectado: ECU via OBD2 ${status.deviceName}"
        is RaceTelemetryContract.BluetoothStatus.ConnectionFailed -> "Falha na Conexão"
    }

    val statusColor = when (status) {
        is RaceTelemetryContract.BluetoothStatus.Connected -> Color(0xFF4CAF50) // Verde
        is RaceTelemetryContract.BluetoothStatus.Scanning,
        is RaceTelemetryContract.BluetoothStatus.DeviceFound -> Color(0xFFFFC107) // Amarelo
        is RaceTelemetryContract.BluetoothStatus.ConnectionFailed -> Color(0xFFF44336) // Vermelho
        else -> Color.Gray
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF1E1E1E), RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .background(statusColor, CircleShape)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = statusText,
            color = Color.White,
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp
        )
    }
}

@Composable
fun TerminalLogComponent(logs: List<String>) {
    val listState = rememberLazyListState()

    // Auto-scroll para o final quando novos logs chegam
    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            listState.animateScrollToItem(logs.size - 1)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
            .background(Color.Black, RoundedCornerShape(8.dp))
            .padding(12.dp)
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize()
        ) {
            items(logs, key = { it.hashCode() }) { log ->
                Text(
                    text = log,
                    color = Color.Green,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
            }
        }
    }
}

@Composable
fun SpeedometerSection(speed: Int, rpm: Int) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.padding(vertical = 16.dp)
    ) {
        Text(
            text = speed.toString(),
            fontSize = 120.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            lineHeight = 120.sp
        )
        Text(
            text = "km/h",
            fontSize = 24.sp,
            color = Color.LightGray,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Exibição do RPM menor abaixo da velocidade
        Text(
            text = "$rpm RPM",
            fontSize = 20.sp,
            color = Color(0xFF90CAF9),
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun TimerSection(formattedTime: String, hasStarted: Boolean, isRecording: Boolean) {
    val color =
        if (isRecording) Color(0xFFE53935) else Color(0xFF4CAF50) // Vermelho se armado/gravando, Verde se finalizado

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .background(Color(0xFF121212), RoundedCornerShape(16.dp))
            .padding(24.dp)
            .fillMaxWidth()
    ) {
        Text(
            text = "0 - 100 km/h",
            color = Color.Gray,
            fontSize = 14.sp
        )
        Text(
            text = "${formattedTime}s",
            fontSize = 48.sp,
            fontWeight = FontWeight.Black,
            color = if (hasStarted || isRecording) color else Color.DarkGray
        )
    }
}

@Composable
fun ActionButtons(
    isRecording: Boolean,
    bluetoothStatus: RaceTelemetryContract.BluetoothStatus,
    onScanClick: () -> Unit,
    onConnectClick: () -> Unit,
    onDisconnectClick: () -> Unit,
    onArmClick: () -> Unit,
    onStopClick: () -> Unit
) {
    val isConnected = bluetoothStatus is RaceTelemetryContract.BluetoothStatus.Connected
    val hasDeviceFound = bluetoothStatus is RaceTelemetryContract.BluetoothStatus.DeviceFound

    // Lógica de cores e texto para o botão principal
    val (primaryText, textColor, bgColor) = when (bluetoothStatus) {
        is RaceTelemetryContract.BluetoothStatus.Connected ->
            Triple("DESCONECTAR OBD2", Color.White, Color(0xFFD32F2F))

        is RaceTelemetryContract.BluetoothStatus.DeviceFound -> {
            val deviceName = bluetoothStatus.device.name ?: "OBD2"
            Triple("CONECTAR ($deviceName)", Color.Black, Color(0xFFFFC107))
        }

        is RaceTelemetryContract.BluetoothStatus.Scanning ->
            Triple("PROCURANDO...", Color.White, Color(0xFF1E88E5))

        else ->
            Triple("PROCURAR OBD2", Color.White, Color(0xFF1E88E5))
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Button(
            onClick = when {
                isConnected -> onDisconnectClick
                hasDeviceFound -> onConnectClick
                else -> onScanClick
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = bgColor
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                text = primaryText,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = textColor
            )
        }

        if (isRecording) {
            Button(
                onClick = onStopClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    "CANCELAR PUXADA",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        } else {
            Button(
                onClick = onArmClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF4CAF50),
                    disabledContainerColor = Color(0xFF1E1E1E)
                ),
                shape = RoundedCornerShape(12.dp),
                enabled = isConnected
            ) {
                Text(
                    text = "ARMAR PUXADA",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isConnected) Color.White else Color.Gray
                )
            }
        }
    }
}

@Preview
@Composable
fun RaceDashboardPreview() {
    RaceDashboardContent(
        state = RaceTelemetryContract.State(
            bluetoothStatus = RaceTelemetryContract.BluetoothStatus.Connected("Dispositivo Genérico"),
            currentSpeed = 100,
            currentRpm = 4500,
            timer0to100 = 5450,
            formattedTimer0to100 = "5.450",
            isRecording = true,
            showTerminalLogs = true,
            terminalLogs = listOf(
                "Scanning for OBD2...",
                "Connected Successfully!",
                "TX: 010D",
                "RX: 41 0D 4A",
                "TX: 010C",
                "RX: 41 0C 11 94"
            )
        ),
        onIntent = {},
        onNavigateToSettings = {}
    )
}
