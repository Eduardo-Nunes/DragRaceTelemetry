package com.eduardo.nunes.drt.plataform

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS
import android.widget.Toast
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.google.accompanist.permissions.MultiplePermissionsState
import com.google.accompanist.permissions.PermissionState
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale

@OptIn(ExperimentalPermissionsApi::class)
@Composable
actual fun RequireBluetoothPermissions(content: @Composable () -> Unit) {
    val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        listOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT
        )
    } else {
        listOf(
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
        )
    }

    val bluetoothPermissionsState = rememberMultiplePermissionsState(permissions)

    LaunchedEffect(Unit) {
        if (!bluetoothPermissionsState.allPermissionsGranted) {
            bluetoothPermissionsState.launchMultiplePermissionRequest()
        }
    }

    if (bluetoothPermissionsState.allPermissionsGranted) {
        content()
    } else {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val rationaleText = if (bluetoothPermissionsState.shouldShowRationale) {
                    "Precisamos dessa permissão para que o app funcione corretamente. Por favor, conceda o acesso ao Bluetooth nas configurações ou a seguir."
                } else {
                    "Permissões de Bluetooth são necessárias para conectar ao adaptador OBD2."
                }

                Text(
                    text = rationaleText,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = { bluetoothPermissionsState.launchMultiplePermissionRequest() }) {
                    Text("Conceder Permissões")
                }
            }
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
actual fun RequireLocationPermissions(content: @Composable () -> Unit) {
    val fineLocationPermissions: List<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            )
        } else {
            listOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    val context = LocalContext.current

    // Approximate location access is sufficient for most of use cases
    val locationPermissionState = rememberPermissionState(
        Manifest.permission.ACCESS_COARSE_LOCATION,
    )

    // When precision is important request both permissions but make sure to handle the case where
    // the user only grants ACCESS_COARSE_LOCATION
    val fineLocationPermissionState = rememberMultiplePermissionsState(
        fineLocationPermissions
    )

    // In really rare use cases, accessing background location might be needed.
    val bgLocationPermissionState = rememberPermissionState(
        Manifest.permission.ACCESS_BACKGROUND_LOCATION,
    )

    // Keeps track of the rationale dialog state, needed when the user requires further rationale
    var rationaleState by remember {
        mutableStateOf<RationaleState?>(null)
    }

    Box(
        Modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Show rationale dialog when needed
            rationaleState?.run { PermissionRationaleDialog(rationaleState = this) }

            PermissionRequestButton(
                isGranted = locationPermissionState.status.isGranted,
                title = "Approximate location access",
            ) {
                if (locationPermissionState.status.shouldShowRationale) {
                    rationaleState = RationaleState(
                        "Request approximate location access",
                        "In order to use this feature please grant access by accepting " + "the location permission dialog." + "\n\nWould you like to continue?",
                    ) { proceed ->
                        if (proceed) {
                            locationPermissionState.launchPermissionRequest()
                        }
                        rationaleState = null
                    }
                } else {
                    locationPermissionState.launchPermissionRequest()
                }
            }

            PermissionRequestButton(
                isGranted = fineLocationPermissionState.allPermissionsGranted,
                title = "Precise location access",
            ) {
                if (fineLocationPermissionState.shouldShowRationale) {
                    rationaleState = RationaleState(
                        "Request Precise Location",
                        "In order to use this feature please grant access by accepting " + "the location permission dialog." + "\n\nWould you like to continue?",
                    ) { proceed ->
                        if (proceed) {
                            fineLocationPermissionState.launchMultiplePermissionRequest()
                        }
                        rationaleState = null
                    }
                } else {
                    fineLocationPermissionState.launchMultiplePermissionRequest()
                }
            }

            // Background location permission needed from Android Q,
            // before Android Q, granting Fine or Coarse location access automatically grants Background
            // location access
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                PermissionRequestButton(
                    isGranted = bgLocationPermissionState.status.isGranted,
                    title = "Background location access",
                ) {
                    if (locationPermissionState.status.isGranted || fineLocationPermissionState.allPermissionsGranted) {
                        if (bgLocationPermissionState.status.shouldShowRationale) {
                            rationaleState = RationaleState(
                                "Request background location",
                                "In order to use this feature please grant access by accepting " + "the background location permission dialog." + "\n\nWould you like to continue?",
                            ) { proceed ->
                                if (proceed) {
                                    bgLocationPermissionState.launchPermissionRequest()
                                }
                                rationaleState = null
                            }
                        } else {
                            bgLocationPermissionState.launchPermissionRequest()
                        }
                    } else {
                        Toast.makeText(
                            context,
                            "Please grant either Approximate location access permission or Fine" + "location access permission",
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                }
            }
        }
        FloatingActionButton(
            modifier = Modifier.align(Alignment.BottomEnd),
            onClick = { context.startActivity(Intent(ACTION_LOCATION_SOURCE_SETTINGS)) },
        ) {
            Icon(Icons.Outlined.Settings, "Location Settings")
        }
    }
//    val locationPermissionsState = rememberMultiplePermissionsState(
//        fineLocationPermissions
//    )
//
//    val areLocationPermissionGranted = locationPermissionsState.allPermissionsGranted
//
//    LaunchedEffect(Unit) {
//        if (!areLocationPermissionGranted) {
//            locationPermissionsState.launchMultiplePermissionRequest()
//        }
//    }
//
//    if (areLocationPermissionGranted) {
//        content()
//    } else {
//        Surface(
//            modifier = Modifier.fillMaxSize(),
//            color = MaterialTheme.colorScheme.background
//        ) {
//            Column(
//                modifier = Modifier
//                    .fillMaxSize()
//                    .padding(24.dp),
//                verticalArrangement = Arrangement.Center,
//                horizontalAlignment = Alignment.CenterHorizontally
//            ) {
//                val rationaleText = if (locationPermissionsState.shouldShowRationale) {
//                    "O app depende da localização para funcionar. Por favor, conceda a permissão."
//                } else {
//                    "Permissões de Localização são necessárias para telemetria de velocidade."
//                }
//
//                Text(
//                    text = rationaleText,
//                    textAlign = TextAlign.Center,
//                    color = MaterialTheme.colorScheme.onBackground
//                )
//                Spacer(modifier = Modifier.height(16.dp))
//                Button(onClick = { locationPermissionsState.launchMultiplePermissionRequest() }) {
//                    Text("Conceder Permissões")
//                }
//            }
//        }
//    }
}

/**
 * Simple screen that manages the location permission state
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun LocationPermissions(text: String, rationale: String, locationState: PermissionState) {
    LocationPermissions(
        text = text,
        rationale = rationale,
        locationState = rememberMultiplePermissionsState(
            permissions = listOf(
                locationState.permission
            )
        )
    )
}

/**
 * Simple screen that manages the location permission state
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun LocationPermissions(text: String, rationale: String, locationState: MultiplePermissionsState) {
    var showRationale by remember(locationState) {
        mutableStateOf(false)
    }
    if (showRationale) {
        PermissionRationaleDialog(rationaleState = RationaleState(
            title = "Location Permission Access",
            rationale = rationale,
            onRationaleReply = { proceed ->
                if (proceed) {
                    locationState.launchMultiplePermissionRequest()
                }
                showRationale = false
            }
        ))
    }
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        PermissionRequestButton(isGranted = false, title = text) {
            if (locationState.shouldShowRationale) {
                showRationale = true
            } else {
                locationState.launchMultiplePermissionRequest()
            }
        }
    }
}

/**
 * A button that shows the title or the request permission action.
 */
@Composable
fun PermissionRequestButton(isGranted: Boolean, title: String, onClick: () -> Unit) {
    if (isGranted) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Outlined.CheckCircle, title, modifier = Modifier.size(48.dp))
            Spacer(Modifier.size(10.dp))
            Text(text = title, modifier = Modifier.background(Color.Transparent))
        }
    } else {
        Button(onClick = onClick) {
            Text("Request $title")
        }
    }
}

/**
 * Simple AlertDialog that displays the given rationale state
 */
@Composable
fun PermissionRationaleDialog(rationaleState: RationaleState) {
    AlertDialog(onDismissRequest = { rationaleState.onRationaleReply(false) }, title = {
        Text(text = rationaleState.title)
    }, text = {
        Text(text = rationaleState.rationale)
    }, confirmButton = {
        TextButton(onClick = {
            rationaleState.onRationaleReply(true)
        }) {
            Text("Continue")
        }
    }, dismissButton = {
        TextButton(onClick = {
            rationaleState.onRationaleReply(false)
        }) {
            Text("Dismiss")
        }
    })
}

data class RationaleState(
    val title: String,
    val rationale: String,
    val onRationaleReply: (proceed: Boolean) -> Unit,
)
//import android.Manifest
//import android.content.pm.PackageManager
//import android.os.Build
//import androidx.activity.compose.rememberLauncherForActivityResult
//import androidx.activity.result.contract.ActivityResultContracts
//import androidx.compose.foundation.layout.*
//import androidx.compose.material3.Button
//import androidx.compose.material3.MaterialTheme
//import androidx.compose.material3.Surface
//import androidx.compose.material3.Text
//import androidx.compose.runtime.*
//import androidx.compose.ui.Alignment
//import androidx.compose.ui.Modifier
//import androidx.compose.ui.platform.LocalContext
//import androidx.compose.ui.text.style.TextAlign
//import androidx.compose.ui.unit.dp
//import androidx.core.content.ContextCompat
//
//@Composable
//actual fun RequireBluetoothPermissions(content: @Composable () -> Unit) {
//    val context = LocalContext.current
//    val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
//        arrayOf(
//            Manifest.permission.BLUETOOTH_SCAN,
//            Manifest.permission.BLUETOOTH_CONNECT
//        )
//    } else {
//        arrayOf(
//            Manifest.permission.BLUETOOTH,
//            Manifest.permission.BLUETOOTH_ADMIN,
//        )
//    }
//
//    var isGranted by remember {
//        mutableStateOf(
//            permissions.all {
//                ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
//            }
//        )
//    }
//
//    val launcher = rememberLauncherForActivityResult(
//        ActivityResultContracts.RequestMultiplePermissions()
//    ) { result ->
//        isGranted = result.values.all { it }
//    }
//
//    LaunchedEffect(Unit) {
//        if (!isGranted) {
//            launcher.launch(permissions)
//        }
//    }
//
//    if (isGranted) {
//        content()
//    } else {
//        Surface(
//            modifier = Modifier.fillMaxSize(),
//            color = MaterialTheme.colorScheme.background
//        ) {
//            Column(
//                modifier = Modifier
//                    .fillMaxSize()
//                    .padding(24.dp),
//                verticalArrangement = Arrangement.Center,
//                horizontalAlignment = Alignment.CenterHorizontally
//            ) {
//                Text(
//                    text = "Permissões de Bluetooth são necessárias para conectar ao adaptador OBD2.",
//                    textAlign = TextAlign.Center,
//                    color = MaterialTheme.colorScheme.onBackground
//                )
//                Spacer(modifier = Modifier.height(16.dp))
//                Button(onClick = { launcher.launch(permissions) }) {
//                    Text("Conceder Permissões")
//                }
//            }
//        }
//    }
//}
//
//@Composable
//actual fun RequireLocationPermissions(content: @Composable () -> Unit) {
//    val context = LocalContext.current
//    val permissions = arrayOf(
//        Manifest.permission.ACCESS_FINE_LOCATION,
//        Manifest.permission.ACCESS_COARSE_LOCATION
//    )
//
//    var isGranted by remember {
//        mutableStateOf(
//            permissions.any {
//                ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
//            }
//        )
//    }
//
//    val launcher = rememberLauncherForActivityResult(
//        ActivityResultContracts.RequestMultiplePermissions()
//    ) { result ->
//        isGranted = result.values.any { it }
//    }
//
//    LaunchedEffect(Unit) {
//        if (!isGranted) {
//            launcher.launch(permissions)
//        }
//    }
//
//    if (isGranted) {
//        content()
//    } else {
//        Surface(
//            modifier = Modifier.fillMaxSize(),
//            color = MaterialTheme.colorScheme.background
//        ) {
//            Column(
//                modifier = Modifier
//                    .fillMaxSize()
//                    .padding(24.dp),
//                verticalArrangement = Arrangement.Center,
//                horizontalAlignment = Alignment.CenterHorizontally
//            ) {
//                Text(
//                    text = "Permissões de Localização são necessárias para telemetria de velocidade.",
//                    textAlign = TextAlign.Center,
//                    color = MaterialTheme.colorScheme.onBackground
//                )
//                Spacer(modifier = Modifier.height(16.dp))
//                Button(onClick = { launcher.launch(permissions) }) {
//                    Text("Conceder Permissões")
//                }
//            }
//        }
//    }
//}
