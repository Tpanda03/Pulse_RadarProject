# PULSE Radar Detection App

Android application for the PULSE (Portable UAV Life-detection System for Emergency Scenarios) project. Visualizes real-time radar detections from a Raspberry Pi-based mmWave sensor system via BLE.

## Tech Stack

- **Language**: Kotlin
- **UI**: Jetpack Compose + Material 3
- **Architecture**: MVVM with StateFlow
- **Navigation**: Jetpack Navigation Compose

## Project Structure
```
com.group4.pulse/
├── MainActivity.kt          # App entry, navigation setup
├── data/
│   └── RadarDetection.kt    # Data models
├── service/
│   ├── BleService.kt        # BLE communication
│   └── SimulatedRadarService.kt
├── viewmodel/
│   └── RadarViewModel.kt    # State management
├── ui/screens/
│   ├── DashboardScreen.kt
│   ├── VisualizationScreen.kt
│   └── SettingsScreen.kt
└── utils/
    └── ExportUtils.kt       # CSV/JSON export
```

## Data Models
```kotlin
data class RadarDetection(
    val objectId: String = UUID.randomUUID().toString(),
    val xPosition: Float,    // meters (horizontal)
    val yPosition: Float,    // meters (vertical)
    val depth: Float,        // meters (0-10m)
    val snr: Float,          // dB
    val timestamp: Long = System.currentTimeMillis(),
    val confidence: Float = 0.8f,
    val objectType: ObjectType = ObjectType.UNKNOWN
)

enum class ObjectType { HUMAN, VEHICLE, METALLIC, ORGANIC, UNKNOWN }
```

## BLE Protocol

**Service UUID**: `00001101-0000-1000-8000-00805F9B34FB`  
**Radar Data Characteristic**: `00002A01-0000-1000-8000-00805F9B34FB`

**20-byte Packet Format**:
| Bytes | Field | Type |
|-------|-------|------|
| 0-3 | Range | float |
| 4-7 | SNR | float |
| 8-15 | Object ID | 8 bytes |
| 16-17 | Timestamp offset | uint16 |
| 18 | Object type | enum |
| 19 | Confidence | 0-255 → 0-1 |

## ViewModel State
```kotlin
val detections: StateFlow<List<RadarDetection>>
val connectionStatus: StateFlow<ConnectionStatus>  // DISCONNECTED, CONNECTING, CONNECTED, SIMULATED
val signalStrength: StateFlow<Int>                 // 0-100
val isSimulationRunning: StateFlow<Boolean>
```

## Connection Modes

| Mode | Description |
|------|-------------|
| **Simulation** | Generates synthetic detections every 8s (default) |
| **BLE** | Connects to Pi via Bluetooth Low Energy |
| **Network** | TCP socket (`10.0.2.2` for emulator) |

## Visualization

- **Grid**: ±10m coordinate plane (20×20m total)
- **Color coding**: Green (near) → Red (far) based on depth
- **Max detections**: 30 (configurable)
- **Update interval**: 5 seconds (configurable)

## Screens

- **Dashboard**: Connection status, detection count, signal strength, start/stop controls
- **Grid View**: 2D Canvas rendering of detections with depth-based coloring
- **Settings**: Update interval, max detections, connection mode, IP config, data export

## Data Export

Exports detection history to app storage:
```kotlin
suspend fun exportData(format: ExportFormat): Result<String>
// Formats: CSV, JSON
```

## Permissions Required
```xml
<uses-permission android:name="android.permission.BLUETOOTH_SCAN" />
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
```

## Part of PULSE Project

Senior capstone project at Illinois Tech developing radar-based human detection for search and rescue operations.
