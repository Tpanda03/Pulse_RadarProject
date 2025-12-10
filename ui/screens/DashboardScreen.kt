package com.group4.pulse.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.group4.pulse.data.RadarDetection
import com.group4.pulse.viewmodel.ConnectionStatus
import com.group4.pulse.viewmodel.ConnectionMode
import com.group4.pulse.viewmodel.ExportFormat
import com.group4.pulse.viewmodel.RadarViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(viewModel: RadarViewModel) {
    val detections by viewModel.detections.collectAsState()
    val connectionStatus by viewModel.connectionStatus.collectAsState()
    val connectionMode by viewModel.connectionMode.collectAsState()
    val signalStrength by viewModel.signalStrength.collectAsState()
    val lastUpdate by viewModel.lastUpdateTime.collectAsState()
    val isSimulationRunning by viewModel.isSimulationRunning.collectAsState()
    val connectionError by viewModel.connectionError.collectAsState()
    val scope = rememberCoroutineScope()

    // Snackbar state for notifications
    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            // Connection Status Panel
            ConnectionStatusCard(
                connectionStatus = connectionStatus,
                connectionMode = connectionMode,
                signalStrength = signalStrength,
                lastUpdate = lastUpdate,
                error = connectionError
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Quick Stats Row
            QuickStatsRow(detections = detections)

            Spacer(modifier = Modifier.height(16.dp))

            // Control Buttons
            ControlButtonsRow(
                viewModel = viewModel,
                isSimulationRunning = isSimulationRunning,
                connectionStatus = connectionStatus,
                connectionMode = connectionMode,
                onExportComplete = { filename ->
                    scope.launch {
                        snackbarHostState.showSnackbar(
                            message = "Data exported to: $filename",
                            duration = SnackbarDuration.Short
                        )
                    }
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Detection List Header
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Active Detections",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                    Badge(
                        containerColor = if (detections.isNotEmpty())
                            MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text("${detections.size}")
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Detection List
            if (detections.isEmpty()) {
                EmptyDetectionsCard()
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(
                        items = detections,
                        key = { it.objectId }
                    ) { detection ->
                        AnimatedVisibility(
                            visible = true,
                            enter = fadeIn() + slideInVertically(),
                            exit = fadeOut() + slideOutVertically()
                        ) {
                            DetectionCard(detection = detection)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ConnectionStatusCard(
    connectionStatus: ConnectionStatus,
    connectionMode: ConnectionMode,
    signalStrength: Int,
    lastUpdate: Long,
    error: String?
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when (connectionStatus) {
                ConnectionStatus.CONNECTED -> MaterialTheme.colorScheme.primaryContainer
                ConnectionStatus.ERROR -> MaterialTheme.colorScheme.errorContainer
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Connection Status with Icon
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Status indicator
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(
                                when (connectionStatus) {
                                    ConnectionStatus.CONNECTED -> Color.Green
                                    ConnectionStatus.CONNECTING -> Color.Yellow
                                    ConnectionStatus.SCANNING -> Color.Blue
                                    ConnectionStatus.DISCONNECTED -> Color.Gray
                                    ConnectionStatus.ERROR -> Color.Red
                                    ConnectionStatus.SIMULATED -> Color.Cyan
                                }
                            )
                    )

                    Column {
                        Text(
                            text = connectionStatus.name.replace("_", " "),
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "Mode: ${connectionMode.name}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Signal Strength Indicator
                if (connectionStatus == ConnectionStatus.CONNECTED ||
                    connectionStatus == ConnectionStatus.SIMULATED) {
                    SignalStrengthIndicator(strength = signalStrength)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Last Update Time
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "Last Update: ${formatTime(lastUpdate)}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (connectionStatus == ConnectionStatus.CONNECTED) {
                    Text(
                        "Live",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Error message if any
            error?.let {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = it,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun SignalStrengthIndicator(strength: Int) {
    Row(
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        val bars = 4
        val activeBars = (strength / 25).coerceIn(0, bars)

        for (i in 1..bars) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height((8 + i * 3).dp)
                    .background(
                        if (i <= activeBars) {
                            when {
                                strength > 75 -> Color.Green
                                strength > 50 -> Color.Yellow
                                else -> Color.Red
                            }
                        } else {
                            Color.Gray.copy(alpha = 0.3f)
                        },
                        RoundedCornerShape(1.dp)
                    )
            )
        }

        Spacer(modifier = Modifier.width(4.dp))
        Text(
            "$strength%",
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun QuickStatsRow(detections: List<RadarDetection>) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val avgDepth = if (detections.isNotEmpty())
            detections.map { it.depth }.average() else 0.0
        val avgSnr = if (detections.isNotEmpty())
            detections.map { it.snr }.average() else 0.0

        QuickStatCard(
            modifier = Modifier.weight(1f),
            title = "Avg Depth",
            value = String.format("%.1f m", avgDepth),
            icon = Icons.Default.Home
        )

        QuickStatCard(
            modifier = Modifier.weight(1f),
            title = "Avg SNR",
            value = String.format("%.1f dB", avgSnr),
            icon = Icons.Default.Clear
        )

        QuickStatCard(
            modifier = Modifier.weight(1f),
            title = "Total",
            value = detections.size.toString(),
            icon = Icons.Default.Add
        )
    }
}

@Composable
fun QuickStatCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = title,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun ControlButtonsRow(
    viewModel: RadarViewModel,
    isSimulationRunning: Boolean,
    connectionStatus: ConnectionStatus,
    connectionMode: ConnectionMode,
    onExportComplete: (String) -> Unit
) {
    val scope = rememberCoroutineScope()

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Start/Stop button (only for simulation mode)
        if (connectionMode == ConnectionMode.SIMULATION) {
            Button(
                onClick = { viewModel.toggleSimulation() },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isSimulationRunning)
                        MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primary
                )
            ) {
                Spacer(modifier = Modifier.width(4.dp))
                Text(if (isSimulationRunning) "Pause" else "Start")
            }
        }

        // Send command buttons (only when connected via BLE/Network)
        if (connectionStatus == ConnectionStatus.CONNECTED) {
            OutlinedButton(
                onClick = { viewModel.sendCommand("START") },
                modifier = Modifier.weight(1f)
            ) {
                Text("Start")
            }

            OutlinedButton(
                onClick = { viewModel.sendCommand("STOP") },
                modifier = Modifier.weight(1f)
            ) {
                Text("Stop")
            }
        }

        // Clear button
        Button(
            onClick = { viewModel.clearDetections() },
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondary
            )
        ) {
            Icon(
                imageVector = Icons.Default.Clear,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text("Clear")
        }

        // Export button
        Button(
            onClick = {
                scope.launch {
                    val result = viewModel.exportData(ExportFormat.CSV)
                    result.onSuccess { filename ->
                        onExportComplete(filename)
                    }
                }
            },
            modifier = Modifier.weight(1f)
        ) {
            Spacer(modifier = Modifier.width(4.dp))
            Text("Export")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetectionCard(detection: RadarDetection) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = { /* Future: Show detailed view */ }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            // Header row with ID and timestamp
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "ID: ${detection.objectId.take(8)}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Text(
                    formatTime(detection.timestamp),
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Position column
                Column {
                    Text(
                        "Position",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Text(
                        "X: ${String.format("%.1f", detection.xPosition)} m",
                        fontSize = 14.sp
                    )
                    Text(
                        "Y: ${String.format("%.1f", detection.yPosition)} m",
                        fontSize = 14.sp
                    )
                }

                // Depth column with color indicator
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "Depth",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(getDepthColor(detection.depth, 10f))
                        )
                        Text(
                            "${String.format("%.2f", detection.depth)} m",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            color = getDepthColor(detection.depth, 10f)
                        )
                    }
                }

                // SNR column
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "Signal",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Badge(
                        containerColor = getSnrColor(detection.snr)
                    ) {
                        Text("${String.format("%.1f", detection.snr)} dB")
                    }
                    Text(
                        "Conf: ${String.format("%.0f", detection.confidence * 100)}%",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}

@Composable
fun EmptyDetectionsCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "No Detections",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                "Waiting for radar data...",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun getSnrColor(snr: Float): Color {
    return when {
        snr > 20 -> Color(0xFF4CAF50) // Green
        snr > 10 -> Color(0xFFFFC107) // Amber
        else -> Color(0xFFFF5722) // Red-Orange
    }
}

fun formatTime(timestamp: Long): String {
    val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    return sdf.format(Date(timestamp))
}