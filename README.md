# PULSE Android Application

**Portable UAV Life-detection System for Emergency Scenarios**

A real-time radar detection visualization app for search and rescue operations, built with Kotlin and Jetpack Compose.

---

## Overview

PULSE Android App provides real-time visualization of radar detections from mmWave sensors (LD2410C/RD-03D) mounted on a Raspberry Pi 4. The app receives detection data via Bluetooth Low Energy and renders targets on a 2D spatial grid with depth-based color coding.

**Key Features:**
- Real-time 2D grid visualization (±6m range)
- Bluetooth Low Energy (BLE) connectivity
- TCP/IP network mode for development
- Simulation mode for testing
- Detection data export (CSV/JSON)
- MVVM architecture with Jetpack Compose

---

## Architecture

### MVVM Pattern
```
UI Layer (Compose) → ViewModel → Service Layer → Hardware/Simulation
```

### Project Structure
```
app/src/main/java/com/group4/pulse/
├── data/
│   └── RadarDetection.kt          # Detection data model
├── service/
│   ├── BleService.kt              # Bluetooth Low Energy communication
│   ├── NetworkService.kt          # TCP/IP socket communication
│   └── SimulatedRadarService.kt   # Synthetic data generation
├── viewmodel/
│   └── RadarViewModel.kt          # State management & business logic
├── ui/
│   ├── screens/
│   │   ├── DashboardScreen.kt     # Connection status & controls
│   │   ├── VisualizationScreen.kt # 2D detection grid
│   │   └── SettingsScreen.kt      # Configuration interface
│   └── theme/
│       └── Theme.kt               # Material 3 design system
├── utils/
│   └── ExportUtils.kt             # CSV/JSON export utilities
└── MainActivity.kt                # App entry point
```

---

## Getting Started

### Prerequisites
- Android Studio Hedgehog (2023.1.1) or newer
- Android device with BLE support (API 26+) or emulator
- Raspberry Pi 4 running PULSE radar service

### Installation

1. **Clone the repository**
```bash
   git clone https://github.com/your-org/pulse-android.git
   cd pulse-android
```

2. **Open in Android Studio**
   - File → Open → Select project directory
   - Wait for Gradle sync to complete

3. **Build and Run**
   - Connect Android device via USB (enable USB debugging)
   - Click Run or press Shift+F10

---

## Connection Modes

### 1. Bluetooth Mode (Physical Device Required)
- **Setup:** Ensure Bluetooth and Location permissions are granted
- **Connect:** Settings → Select "Bluetooth" → Scan → Select "PULSE-Radar"
- **Data:** 20-byte packets via GATT notifications

### 2. Network Mode (Emulator Compatible)
- **Emulator:** Settings → IP Address: `10.0.2.2` (connects to host machine)
- **Physical Network:** Enter Raspberry Pi IP address (e.g., `192.168.1.100`)
- **Port:** TCP 8888
- **Use Case:** Development and high-rate data collection

### 3. Simulation Mode
- **Setup:** No hardware required
- **Purpose:** UI testing and demonstration
- **Data:** Synthetic detections with realistic movement patterns

---

## Usage

### Dashboard Screen
- **Connection Status:** CONNECTED/DISCONNECTED/SCANNING
- **Signal Strength:** Real-time RSSI indicator
- **Detection Count:** Live target counter
- **Controls:** Connect/Disconnect buttons

### Visualization Screen
- **Grid:** ±6m coordinate plane (X/Y axes)
- **Detections:** Color-coded by depth
  - Green: 0-2m (close)
  - Yellow: 2-4m (medium)
  - Red: 4-6m (far)
- **Statistics:** Total detections, avg/min/max depth, SNR

### Settings Screen
- **Connection Mode:** Bluetooth/Network/Simulation
- **Update Interval:** 1-10 seconds
- **Max Detections:** 10-50 targets
- **Pi IP Address:** Network mode configuration
- **Export:** Save detections as CSV/JSON

---

## Data Format

### RadarDetection Model
```kotlin
data class RadarDetection(
    val xPosition: Float,    // X coordinate (-6 to 6 meters)
    val yPosition: Float,    // Y coordinate (-6 to 6 meters)
    val depth: Float,        // Distance from sensor (0-6 meters)
    val snr: Float,          // Signal-to-noise ratio (dB)
    val timestamp: Long,     // Unix timestamp (ms)
    val objectId: Int        // Unique target identifier
)
```

### BLE Packet Format (20 bytes)
| Bytes | Field | Type | Range |
|-------|-------|------|-------|
| 0-3 | X Position | Float | -6 to 6m |
| 4-7 | Y Position | Float | -6 to 6m |
| 8-11 | Depth | Float | 0 to 6m |
| 12-15 | SNR | Float | 0 to 100 dB |
| 16-19 | Object ID | UInt32 | 0 to 2³² |

---

## Technical Details

### Dependencies
```gradle
// Jetpack Compose
implementation "androidx.compose.ui:ui:1.5.4"
implementation "androidx.compose.material3:material3:1.1.2"

// Coroutines & Flow
implementation "org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3"

// ViewModel
implementation "androidx.lifecycle:lifecycle-viewmodel-compose:2.6.2"

// Bluetooth Low Energy
implementation "androidx.bluetooth:bluetooth:1.0.0-alpha01"
```

### Permissions Required
```xml
<!-- Bluetooth -->
<uses-permission android:name="android.permission.BLUETOOTH_SCAN" />
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />

<!-- Location (required for BLE scanning on Android 10+) -->
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />

<!-- Storage (for data export) -->
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />

<!-- Network -->
<uses-permission android:name="android.permission.INTERNET" />
```

### Coordinate System
- **Origin (0,0):** Center of grid
- **X-axis:** Left (-6m) to Right (+6m)
- **Y-axis:** Bottom (-6m) to Top (+6m)
- **Depth:** Always positive (0-6m, perpendicular distance)

---

## Testing

### Unit Tests
```bash
./gradlew test
```

### Instrumented Tests
```bash
./gradlew connectedAndroidTest
```

### Testing Strategy
1. **Simulation Mode:** Test UI without hardware
2. **Network Mode:** Test with emulator + Pi TCP server
3. **BLE Mode:** Test with physical device + Pi BLE service

---

## Export Format

### CSV Example
```csv
timestamp,x_position,y_position,depth,snr,object_id
1703091234567,1.5,2.3,2.8,45.6,1001
1703091234668,1.6,2.4,2.9,46.2,1001
```

### JSON Example
```json
[
  {
    "timestamp": 1703091234567,
    "xPosition": 1.5,
    "yPosition": 2.3,
    "depth": 2.8,
    "snr": 45.6,
    "objectId": 1001
  }
]
```

---

## Contributing

This is a senior capstone project for Illinois Institute of Technology.

**Team Members:**
- Tejash Panda - Android Development & BLE Integration
- Pablo - Raspberry Pi Software & Sensor Integration
- Michael - Signal Processing & CFAR Algorithms
- Adrian - Hardware Integration & RF Design

---

## License

This project is part of academic coursework at Illinois Institute of Technology.

---

## Related Repositories

- [PULSE-RaspberryPi](https://github.com/your-org/pulse-pi) - Sensor processing & BLE server
- [PULSE-Hardware](https://github.com/your-org/pulse-hardware) - PCB designs & schematics

---

## Support

For questions or issues:
- Open a GitHub issue
- Contact: tpanda@hawk.iit.edu

---

**Built for Search and Rescue Operations**
