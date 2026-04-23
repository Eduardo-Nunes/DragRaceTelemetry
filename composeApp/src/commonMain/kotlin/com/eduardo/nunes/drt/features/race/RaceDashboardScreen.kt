package com.eduardo.nunes.drt.features.race

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eduardo.nunes.drt.core.bluetooth.BluetoothStatus
import com.eduardo.nunes.drt.core.ui.utils.KeepScreenOn
import com.eduardo.nunes.drt.features.race.model.Checkpoint
import com.eduardo.nunes.drt.features.race.model.RaceSession
import com.eduardo.nunes.drt.features.race.model.TelemetryState
import com.eduardo.nunes.drt.features.race.model.TerminalState
import com.eduardo.nunes.drt.plataform.RequireBluetoothPermissions

@Composable
fun RaceDashboardScreen(
    viewModel: RaceTelemetryViewModel,
    onShowSnackbar: (String) -> Unit,
    isMenuOpen: Boolean,
    onMenuClick: () -> Unit
) {
    // 1. Coleta Segura de Estado (Lifecycle Awareness)
    val state by viewModel.state.collectAsStateWithLifecycle()

    KeepScreenOn()

    // 2. Tratamento Correto de Effects (One-Off Events)
    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is RaceTelemetryContract.Effect.ShowError -> {
                    onShowSnackbar(effect.message)
                }
                is RaceTelemetryContract.Effect.Race0to100Completed -> {
                    onShowSnackbar("0-100 km/h em ${effect.formattedTime}s!")
                }
                is RaceTelemetryContract.Effect.Race201mCompleted -> {
                    onShowSnackbar("Puxada 201m concluída em ${effect.formattedTime}s!")
                }
            }
        }
    }

    RequireBluetoothPermissions {
        // RequireLocationPermissions {
        RaceDashboardContent(
            state = state,
            onIntent = viewModel::handleIntent,
            isMenuOpen = isMenuOpen,
            onMenuClick = onMenuClick
        )
        // }
    }
}
@Composable
private fun RaceDashboardContent(
    state: RaceTelemetryContract.State,
    onIntent: (RaceTelemetryContract.Intent) -> Unit,
    isMenuOpen: Boolean,
    onMenuClick: () -> Unit
) {
    MaterialTheme(colorScheme = darkColorScheme()) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(0xFF0F0F13) // Leve ajuste para match com a imagem de fundo
        ) {
            // BoxWithConstraints lê o tamanho exato disponível na tela (Android, iOS, Desktop)
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {

                // Se a largura for maior que a altura, consideramos "Modo Paisagem"
                val isLandscape = maxWidth > maxHeight

                if (isLandscape) {
                    RaceDashboardHorizontalLayout(state, onIntent, isMenuOpen, onMenuClick)
                } else {
                    RaceDashboardVerticalLayout(state, onIntent, isMenuOpen, onMenuClick)
                }
            }
        }
    }
}

@Composable
private fun RaceDashboardVerticalLayout(
    state: RaceTelemetryContract.State,
    onIntent: (RaceTelemetryContract.Intent) -> Unit,
    isMenuOpen: Boolean,
    onMenuClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Top Section: Header + Terminal
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                Box(modifier = Modifier.align(Alignment.CenterStart)) {
                    androidx.compose.animation.AnimatedVisibility(
                        visible = !isMenuOpen,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        IconButton(onClick = onMenuClick) {
                            Icon(Icons.Filled.ChevronRight, contentDescription = "Menu", tint = Color.White)
                        }
                    }
                }

                StatusHeader(
                    status = state.bluetoothStatus,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(top = 4.dp),
                    onScanClick = { onIntent(RaceTelemetryContract.Intent.StartScanning) },
                    onConnectClick = { onIntent(RaceTelemetryContract.Intent.ConnectToDevice) },
                    onDisconnectClick = { onIntent(RaceTelemetryContract.Intent.DisconnectDevice) }
                )
            }
        }

        // Middle Section: Terminal + Velocímetro flexíveis
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (state.terminal.isVisible) {
                Box(modifier = Modifier.heightIn(max = 100.dp)) {
                    TerminalLogComponent(logs = state.terminal.logs)
                }
            }
            SpeedometerSection(
                speed = state.telemetry.speed,
                rpm = state.telemetry.rpm
            )
        }

        // Bottom Section: Cronômetros e Botões ancorados
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Timer0to100Section(
                formattedTime = state.race.run0to100.formattedTime,
                isCompleted = state.race.run0to100.isCompleted,
                isRecording = state.race.isRecording
            )

            RaceSplitsSection(
                currentDistance = state.race.currentDistance,
                currentTimer = state.race.formattedCurrentTimer,
                formatted60ft = state.race.run60ft.formattedTime,
                formatted100m = state.race.run100m.formattedTime,
                formatted201m = state.race.run201m.formattedTime,
                isRecording = state.race.isRecording
            )

            ActionButtons(
                isRecording = state.race.isRecording,
                bluetoothStatus = state.bluetoothStatus,
                onArmClick = { onIntent(RaceTelemetryContract.Intent.StartRace) },
                onStopClick = { onIntent(RaceTelemetryContract.Intent.StopRace) }
            )
        }
    }
}

@Composable
private fun RaceDashboardHorizontalLayout(
    state: RaceTelemetryContract.State,
    onIntent: (RaceTelemetryContract.Intent) -> Unit,
    isMenuOpen: Boolean,
    onMenuClick: () -> Unit
) {
    // Row divide a tela horizontalmente em duas metades (50% / 50%)
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 32.dp, bottom = 12.dp, start = 12.dp, end = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Lado Esquerdo: Conexão, Terminal e Velocímetro
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header Compacto
            Box(modifier = Modifier.fillMaxWidth()) {
                Box(modifier = Modifier.align(Alignment.CenterStart)) {
                    androidx.compose.animation.AnimatedVisibility(
                        visible = !isMenuOpen,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        IconButton(onClick = onMenuClick) {
                            Icon(Icons.Filled.ChevronRight, contentDescription = "Menu", tint = Color.White)
                        }
                    }
                }

                StatusHeader(
                    status = state.bluetoothStatus,
                    modifier = Modifier.align(Alignment.Center),
                    onScanClick = { onIntent(RaceTelemetryContract.Intent.StartScanning) },
                    onConnectClick = { onIntent(RaceTelemetryContract.Intent.ConnectToDevice) },
                    onDisconnectClick = { onIntent(RaceTelemetryContract.Intent.DisconnectDevice) }
                )
            }

            if (state.terminal.isVisible) {
                Box(modifier = Modifier.heightIn(max = 80.dp).padding(top = 8.dp)) {
                    TerminalLogComponent(logs = state.terminal.logs)
                }
            }

            // Velocímetro ocupa o resto do espaço na esquerda
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.Center
            ) {
                SpeedometerSection(
                    speed = state.telemetry.speed,
                    rpm = state.telemetry.rpm
                )
            }
        }

        // Lado Direito: Tempos da Corrida e Ação
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Timer0to100Section(
                    formattedTime = state.race.run0to100.formattedTime,
                    isCompleted = state.race.run0to100.isCompleted,
                    isRecording = state.race.isRecording
                )

                RaceSplitsSection(
                    currentDistance = state.race.currentDistance,
                    currentTimer = state.race.formattedCurrentTimer,
                    formatted60ft = state.race.run60ft.formattedTime,
                    formatted100m = state.race.run100m.formattedTime,
                    formatted201m = state.race.run201m.formattedTime,
                    isRecording = state.race.isRecording
                )
            }

            ActionButtons(
                isRecording = state.race.isRecording,
                bluetoothStatus = state.bluetoothStatus,
                onArmClick = { onIntent(RaceTelemetryContract.Intent.StartRace) },
                onStopClick = { onIntent(RaceTelemetryContract.Intent.StopRace) }
            )
        }
    }
}

@Composable
fun StatusHeader(
    status: BluetoothStatus,
    modifier: Modifier = Modifier,
    onScanClick: () -> Unit,
    onConnectClick: () -> Unit,
    onDisconnectClick: () -> Unit
) {
    val onClick: () -> Unit = when (status) {
        is BluetoothStatus.Connected -> onDisconnectClick
        is BluetoothStatus.DeviceFound -> onConnectClick
        is BluetoothStatus.Scanning -> {
            {}
        }
        else -> onScanClick
    }

    Surface(
        onClick = onClick,
        color = Color(0xFF1E1E1E),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
    ) {
        AnimatedContent(
            targetState = status,
            label = "status_header_animation"
        ) { targetStatus ->
            val statusText = when (targetStatus) {
                is BluetoothStatus.Disconnected -> "Desconectado"
                is BluetoothStatus.Scanning -> "Procurando OBD2..."
                is BluetoothStatus.DeviceFound -> "Dispositivo Encontrado"
                is BluetoothStatus.Connected -> "Conectado"
                is BluetoothStatus.ConnectionFailed -> "Falha na Conexão"
                is BluetoothStatus.Connecting -> "Conectando..."
            }

            val statusColor = when (targetStatus) {
                is BluetoothStatus.Connected -> Color(0xFF4CAF50)
                is BluetoothStatus.Scanning,
                is BluetoothStatus.DeviceFound -> Color(0xFFFFC107)
                is BluetoothStatus.ConnectionFailed -> Color(0xFFF44336)
                else -> Color.Gray
            }

            val (actionText, actionColor) = when (targetStatus) {
                is BluetoothStatus.Connected -> Pair("CLIQUE PARA DESCONECTAR", Color(0xFFD32F2F))
                is BluetoothStatus.DeviceFound -> Pair("CLIQUE PARA CONECTAR", Color(0xFFFFC107))
                is BluetoothStatus.Scanning -> Pair("PROCURANDO...", Color.Gray)
                is BluetoothStatus.Connecting -> Pair("CONECTANDO...", Color.Gray)
                else -> Pair("CLIQUE PARA PROCURAR", Color(0xFF1E88E5))
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(vertical = 12.dp, horizontal = 24.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
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

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = actionText,
                    color = actionColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    letterSpacing = 0.5.sp
                )
            }
        }
    }
}

@Composable
fun TerminalLogComponent(logs: List<String>) {
    val listState = rememberLazyListState()

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
            items(logs) { log ->
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
        modifier = Modifier.padding(vertical = 12.dp)
    ) {
        Text(
            text = speed.toString(),
            fontSize = 140.sp, // Ajustado para match com a UI base
            fontWeight = FontWeight.Bold,
            color = Color.White,
            lineHeight = 140.sp
        )
        Text(
            text = "km/h",
            fontSize = 24.sp,
            color = Color.LightGray,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "$rpm RPM",
            fontSize = 20.sp,
            color = Color(0xFF64B5F6), // Azul para match visual da screenshot
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun Timer0to100Section(formattedTime: String, isCompleted: Boolean, isRecording: Boolean) {
    val color = when {
        isCompleted -> Color(0xFF4CAF50)
        isRecording -> Color(0xFFE53935)
        else -> Color.DarkGray
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .background(Color(0xFF151515), RoundedCornerShape(16.dp))
            .padding(vertical = 16.dp, horizontal = 24.dp)
            .fillMaxWidth()
    ) {
        Text(
            text = "0 - 100 km/h",
            color = Color.Gray,
            fontSize = 14.sp
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "${formattedTime}s",
            fontSize = 36.sp,
            fontWeight = FontWeight.Black,
            color = color
        )
    }
}

@Composable
fun RaceSplitsSection(
    currentDistance: Double,
    currentTimer: String,
    formatted60ft: String,
    formatted100m: String,
    formatted201m: String,
    isRecording: Boolean
) {
    Column(
        modifier = Modifier
            .background(Color(0xFF151515), RoundedCornerShape(16.dp))
            .padding(20.dp)
            .fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            Column {
                Text("Distância", color = Color.Gray, fontSize = 12.sp)
                Text(
                    "${currentDistance.toInt()}m",
                    color = Color.White,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("Tempo Atual", color = Color.Gray, fontSize = 12.sp)
                Text(
                    text = "${currentTimer}s",
                    color = if (isRecording) Color(0xFFE53935) else Color.White,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Black
                )
            }
        }

        HorizontalDivider(color = Color(0xFF2A2A2A))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            SplitItem(label = "60 pés", time = formatted60ft)
            SplitItem(label = "100m", time = formatted100m)
            SplitItem(label = "201m (1/8 mi)", time = formatted201m)
        }
    }
}

@Composable
fun SplitItem(label: String, time: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, color = Color.Gray, fontSize = 12.sp)
        Text(
            text = time,
            color = if (time == "--.---") Color.DarkGray else Color(0xFF4CAF50),
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun ActionButtons(
    isRecording: Boolean,
    bluetoothStatus: BluetoothStatus,
    onArmClick: () -> Unit,
    onStopClick: () -> Unit
) {
    val isConnected = bluetoothStatus is BluetoothStatus.Connected

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        AnimatedContent(
            targetState = isRecording,
            label = "recording_button_animation"
        ) { recording ->
            if (recording) {
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
                        containerColor = Color(0xFF2E2E2E), // Base cinza escura para "desarmado/conectado"
                        contentColor = Color.White,
                        disabledContainerColor = Color(0xFF1A1A1A), // Desativado bem escuro
                        disabledContentColor = Color.DarkGray
                    ),
                    shape = RoundedCornerShape(12.dp),
                    enabled = isConnected
                ) {
                    Text(
                        text = "ARMAR PUXADA",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isConnected) Color.White else Color(0xFF424242)
                    )
                }
            }
        }
    }
}

@Preview
@Composable
fun RaceDashboardPreview() {
    RaceDashboardContent(
        state = RaceTelemetryContract.State(
            bluetoothStatus = BluetoothStatus.Connected("obd2"),
            telemetry = TelemetryState(
                speed = 105,
                rpm = 3500
            ),
            race = RaceSession(
                isRecording = false,
                currentDistance = 180.0,
                currentTimerMs = 7091L,
                run0to100 = Checkpoint(7000),
                run60ft = Checkpoint(1500),
                run100m = Checkpoint(4000),
                run201m = Checkpoint()
            ),
            terminal = TerminalState(isVisible = true)
        ),
        onIntent = {},
        isMenuOpen = false,
        onMenuClick = {}
    )
}