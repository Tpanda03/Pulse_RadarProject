package com.group4.pulse.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.group4.pulse.viewmodel.RadarViewModel
import com.group4.pulse.viewmodel.ConnectionMode
import com.group4.pulse.viewmodel.ConnectionStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: RadarViewModel) {
    val updateInterval by viewModel.updateInterval.collectAsState()
    val maxDetections by viewModel.maxDetections.collectAsState()
    val enableVisualization by viewModel.enableRadarVisualization.collectAsState()
    val connectionMode by viewModel.connectionMode.collectAsState()
    val connectionStatus by viewModel.connectionStatus.collectAsState()
    val piIpAddress by viewModel.piIpAddress.collectAsState()
    val connectionError by viewModel.connectionError.collectAsState()
    val bleDevices by viewModel.bleDevices.collectAsState()

    var intervalSlider by remember { mutableStateOf(updateInterval.toFloat()) }
    var maxDetectionsSlider by remember { mutableStateOf(maxDetections.toFloat()) }
    var ipAddressText by remember { mutableStateOf(piIpAddress) }
    var showBleDeviceDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Connection Mode Selection
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    "Connection Mode",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Connection mode selector
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Bluetooth LE")
                        RadioButton(
                            selected = connectionMode == ConnectionMode.BLUETOOTH,
                            onClick = { viewModel.setConnectionMode(ConnectionMode.BLUETOOTH) }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Network (TCP/IP)")
                        RadioButton(
                            selected = connectionMode == ConnectionMode.NETWORK,
                            onClick = { viewModel.setConnectionMode(ConnectionMode.NETWORK) }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Simulation")
                        RadioButton(
                            selected = connectionMode == ConnectionMode.SIMULATION,
                            onClick = { viewModel.setConnectionMode(ConnectionMode.SIMULATION) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Mode-specific configuration
                when (connectionMode) {
                    ConnectionMode.BLUETOOTH -> {
                        // BLE configuration
                        if (bleDevices.isNotEmpty()) {
                            Text(
                                "Available Devices:",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            // Show first 3 devices
                            bleDevices.take(3).forEach { device ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 2.dp)
                                        .clickable {
                                            viewModel.connectToBleDevice(device.address)
                                        },
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column {
                                            Text(
                                                device.name,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                            Text(
                                                device.address,
                                                fontSize = 10.sp,
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                            )
                                        }
                                        Text(
                                            "${device.rssi} dBm",
                                            fontSize = 10.sp
                                        )
                                    }
                                }
                            }

                            if (bleDevices.size > 3) {
                                TextButton(
                                    onClick = { showBleDeviceDialog = true }
                                ) {
                                    Text("Show all devices (${bleDevices.size})")
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Button(
                            onClick = {
                                when (connectionStatus) {
                                    ConnectionStatus.DISCONNECTED -> viewModel.startBleScan()
                                    ConnectionStatus.SCANNING -> viewModel.stopBleScan()
                                    ConnectionStatus.CONNECTED -> viewModel.disconnect()
                                    else -> {}
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = connectionStatus != ConnectionStatus.CONNECTING
                        ) {
                            Text(
                                when (connectionStatus) {
                                    ConnectionStatus.DISCONNECTED -> "Scan for Devices"
                                    ConnectionStatus.SCANNING -> "Stop Scanning"
                                    ConnectionStatus.CONNECTING -> "Connecting..."
                                    ConnectionStatus.CONNECTED -> "Disconnect"
                                    else -> "Connect"
                                }
                            )
                        }
                    }

                    ConnectionMode.NETWORK -> {
                        // Network configuration
                        OutlinedTextField(
                            value = ipAddressText,
                            onValueChange = {
                                ipAddressText = it
                                viewModel.setPiIpAddress(it)
                            },
                            label = { Text("Raspberry Pi IP Address") },
                            placeholder = { Text("192.168.1.100") },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = connectionStatus != ConnectionStatus.CONNECTED
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            "Port: 5555",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = {
                                when (connectionStatus) {
                                    ConnectionStatus.DISCONNECTED, ConnectionStatus.ERROR -> {
                                        viewModel.connectToRadar()
                                    }
                                    ConnectionStatus.CONNECTED -> {
                                        viewModel.disconnect()
                                    }
                                    else -> {}
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = connectionStatus != ConnectionStatus.CONNECTING
                        ) {
                            Text(
                                when (connectionStatus) {
                                    ConnectionStatus.DISCONNECTED, ConnectionStatus.ERROR -> "Connect"
                                    ConnectionStatus.CONNECTING -> "Connecting..."
                                    ConnectionStatus.CONNECTED -> "Disconnect"
                                    else -> "Connect"
                                }
                            )
                        }
                    }

                    ConnectionMode.SIMULATION -> {
                        // Simulation controls
                        Button(
                            onClick = { viewModel.toggleSimulation() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                if (connectionStatus == ConnectionStatus.SIMULATED)
                                    "Stop Simulation"
                                else
                                    "Start Simulation"
                            )
                        }
                    }
                }

                // Error message
                if (connectionError != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = connectionError!!,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp
                    )
                }

                // Connection status
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        "Status: ",
                        fontSize = 12.sp
                    )
                    Text(
                        connectionStatus.name,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = when (connectionStatus) {
                            ConnectionStatus.CONNECTED, ConnectionStatus.SIMULATED -> MaterialTheme.colorScheme.primary
                            ConnectionStatus.ERROR -> MaterialTheme.colorScheme.error
                            ConnectionStatus.SCANNING -> MaterialTheme.colorScheme.secondary
                            else -> MaterialTheme.colorScheme.onSurface
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Command Controls (only show when connected)
        if (connectionStatus == ConnectionStatus.CONNECTED) {
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        "Send Commands",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.sendCommand("START") }
                        ) {
                            Text("Start")
                        }

                        OutlinedButton(
                            onClick = { viewModel.sendCommand("STOP") }
                        ) {
                            Text("Stop")
                        }

                        OutlinedButton(
                            onClick = { viewModel.sendCommand("CLEAR") }
                        ) {
                            Text("Clear")
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Display Settings
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    "Display Settings",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Update Interval
                Text(
                    "Update Interval: ${(intervalSlider / 1000).toInt()} seconds",
                    fontSize = 14.sp
                )
                Slider(
                    value = intervalSlider,
                    onValueChange = { intervalSlider = it },
                    onValueChangeFinished = {
                        viewModel.updateSettings(interval = intervalSlider.toLong())
                    },
                    valueRange = 500f..10000f,
                    steps = 18,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Max Detections
                Text(
                    "Max Detections: ${maxDetectionsSlider.toInt()}",
                    fontSize = 14.sp
                )
                Slider(
                    value = maxDetectionsSlider,
                    onValueChange = { maxDetectionsSlider = it },
                    onValueChangeFinished = {
                        viewModel.updateSettings(maxDetections = maxDetectionsSlider.toInt())
                    },
                    valueRange = 10f..100f,
                    steps = 8,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Enable Visualization Switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Enable Grid Visualization")
                    Switch(
                        checked = enableVisualization,
                        onCheckedChange = {
                            viewModel.updateSettings(enableVisualization = it)
                        }
                    )
                }
            }
        }
    }

    // BLE Device Selection Dialog
    if (showBleDeviceDialog) {
        Dialog(
            onDismissRequest = { showBleDeviceDialog = false }
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.7f)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        "Select Device",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    LazyColumn {
                        items(bleDevices) { device ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clickable {
                                        viewModel.connectToBleDevice(device.address)
                                        showBleDeviceDialog = false
                                    },
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text(
                                            device.name,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text(
                                            device.address,
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                        )
                                    }
                                    Text("${device.rssi} dBm")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}