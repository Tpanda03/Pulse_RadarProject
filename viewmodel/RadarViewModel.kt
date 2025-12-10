package com.group4.pulse.viewmodel

import android.app.Application
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.group4.pulse.data.RadarDetection
import com.group4.pulse.service.SimulatedRadarService
import com.group4.pulse.service.BleService
import com.group4.pulse.service.NetworkService
import com.group4.pulse.utils.ExportUtils
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

class RadarViewModel(application: Application) : AndroidViewModel(application) {

    private val simulatedRadarService = SimulatedRadarService()
    private val bleService = BleService(application.applicationContext)
    private val networkService = NetworkService()
    private val exportUtils = ExportUtils(application.applicationContext)

    // Check if running on emulator
    private val isEmulator = Build.FINGERPRINT.contains("generic") ||
            Build.FINGERPRINT.contains("emulator") ||
            Build.MODEL.contains("Emulator")

    // State flows for UI
    private val _detections = MutableStateFlow<List<RadarDetection>>(emptyList())
    val detections: StateFlow<List<RadarDetection>> = _detections.asStateFlow()

    private val _connectionStatus = MutableStateFlow(ConnectionStatus.DISCONNECTED)
    val connectionStatus: StateFlow<ConnectionStatus> = _connectionStatus.asStateFlow()

    private val _connectionMode = MutableStateFlow(if (isEmulator) ConnectionMode.NETWORK else ConnectionMode.BLUETOOTH)
    val connectionMode: StateFlow<ConnectionMode> = _connectionMode.asStateFlow()

    private val _bleDevices = MutableStateFlow<List<BleService.BleDeviceInfo>>(emptyList())
    val bleDevices: StateFlow<List<BleService.BleDeviceInfo>> = _bleDevices.asStateFlow()

    private val _signalStrength = MutableStateFlow(0)
    val signalStrength: StateFlow<Int> = _signalStrength.asStateFlow()

    private val _lastUpdateTime = MutableStateFlow(System.currentTimeMillis())
    val lastUpdateTime: StateFlow<Long> = _lastUpdateTime.asStateFlow()

    private val _isSimulationRunning = MutableStateFlow(false)
    val isSimulationRunning: StateFlow<Boolean> = _isSimulationRunning.asStateFlow()

    private val _piIpAddress = MutableStateFlow("192.168.1.100")
    val piIpAddress: StateFlow<String> = _piIpAddress.asStateFlow()

    private val _connectionError = MutableStateFlow<String?>(null)
    val connectionError: StateFlow<String?> = _connectionError.asStateFlow()

    // Settings
    private val _updateInterval = MutableStateFlow(5000L)
    val updateInterval: StateFlow<Long> = _updateInterval.asStateFlow()

    private val _maxDetections = MutableStateFlow(30)
    val maxDetections: StateFlow<Int> = _maxDetections.asStateFlow()

    private val _enableRadarVisualization = MutableStateFlow(true)
    val enableRadarVisualization: StateFlow<Boolean> = _enableRadarVisualization.asStateFlow()

    init {
        // Listen to BLE service detections
        viewModelScope.launch {
            bleService.detections.collect { detection ->
                addRealDetection(detection)
            }
        }

        // Monitor BLE connection state
        viewModelScope.launch {
            bleService.connectionState.collect { state ->
                if (_connectionMode.value == ConnectionMode.BLUETOOTH) {
                    _connectionStatus.value = when (state) {
                        BleService.BleConnectionState.CONNECTED -> {
                            _signalStrength.value = 75
                            ConnectionStatus.CONNECTED
                        }
                        BleService.BleConnectionState.CONNECTING -> ConnectionStatus.CONNECTING
                        BleService.BleConnectionState.SCANNING -> ConnectionStatus.SCANNING
                        BleService.BleConnectionState.ERROR -> ConnectionStatus.ERROR
                        else -> ConnectionStatus.DISCONNECTED
                    }
                }
            }
        }

        // Monitor BLE available devices
        viewModelScope.launch {
            bleService.availableDevices.collect { devices ->
                _bleDevices.value = devices
            }
        }

        // Monitor BLE errors
        viewModelScope.launch {
            bleService.lastError.collect { error ->
                if (_connectionMode.value == ConnectionMode.BLUETOOTH) {
                    _connectionError.value = error
                }
            }
        }

        // Listen to network service detections
        viewModelScope.launch {
            networkService.detections.collect { detection ->
                if (_connectionMode.value == ConnectionMode.NETWORK) {
                    addRealDetection(detection)
                }
            }
        }

        // Monitor network connection state
        viewModelScope.launch {
            networkService.connectionState.collect { state ->
                if (_connectionMode.value == ConnectionMode.NETWORK) {
                    _connectionStatus.value = when (state) {
                        NetworkService.NetworkConnectionState.CONNECTED -> {
                            _signalStrength.value = 85
                            ConnectionStatus.CONNECTED
                        }
                        NetworkService.NetworkConnectionState.CONNECTING -> ConnectionStatus.CONNECTING
                        NetworkService.NetworkConnectionState.ERROR -> ConnectionStatus.ERROR
                        else -> ConnectionStatus.DISCONNECTED
                    }
                }
            }
        }

        // Monitor network errors
        viewModelScope.launch {
            networkService.lastError.collect { error ->
                if (_connectionMode.value == ConnectionMode.NETWORK) {
                    _connectionError.value = error
                }
            }
        }
    }

    /**
     * Start scanning for BLE devices
     */
    fun startBleScan() {
        if (_connectionMode.value == ConnectionMode.BLUETOOTH) {
            bleService.startScan()
        }
    }

    /**
     * Stop scanning for BLE devices
     */
    fun stopBleScan() {
        bleService.stopScan()
    }

    /**
     * Connect to a specific BLE device
     */
    fun connectToBleDevice(deviceAddress: String) {
        bleService.connect(deviceAddress)
    }

    /**
     * Connect to Raspberry Pi based on current mode
     */
    fun connectToRadar() {
        _connectionError.value = null

        when (_connectionMode.value) {
            ConnectionMode.NETWORK -> {
                if (isEmulator) {
                    networkService.connectToEmulatorHost()
                } else {
                    networkService.connectToPi(_piIpAddress.value)
                }
            }
            ConnectionMode.BLUETOOTH -> {
                if (!bleService.isBluetoothEnabled()) {
                    _connectionError.value = "Please enable Bluetooth"
                    _connectionStatus.value = ConnectionStatus.ERROR
                } else {
                    // Start scanning for devices
                    startBleScan()
                }
            }
            ConnectionMode.SIMULATION -> {
                startSimulation()
            }
        }
    }

    /**
     * Disconnect from current connection
     */
    fun disconnect() {
        when (_connectionMode.value) {
            ConnectionMode.NETWORK -> networkService.disconnect()
            ConnectionMode.BLUETOOTH -> bleService.disconnect()
            ConnectionMode.SIMULATION -> stopSimulation()
        }
        _connectionStatus.value = ConnectionStatus.DISCONNECTED
        _signalStrength.value = 0
        _bleDevices.value = emptyList()
    }

    /**
     * Send command to connected device
     */
    fun sendCommand(command: String) {
        when (_connectionMode.value) {
            ConnectionMode.NETWORK -> networkService.sendCommand(command)
            ConnectionMode.BLUETOOTH -> bleService.sendCommand(command.toByteArray())
            else -> { /* No-op for simulation */ }
        }
    }

    /**
     * Add detection from real hardware
     */
    private fun addRealDetection(detection: RadarDetection) {
        val updatedList = (_detections.value + detection)
            .distinctBy { it.objectId }
            .sortedByDescending { it.timestamp }
            .take(_maxDetections.value)

        _detections.value = updatedList
        _lastUpdateTime.value = System.currentTimeMillis()
    }

    /**
     * Start simulation mode
     */
    private fun startSimulation() {
        _isSimulationRunning.value = true
        _connectionStatus.value = ConnectionStatus.SIMULATED

        viewModelScope.launch {
            while (_isSimulationRunning.value) {
                val currentDetections = simulatedRadarService.generateDetections()

                if (currentDetections != _detections.value) {
                    _detections.value = currentDetections
                    _lastUpdateTime.value = System.currentTimeMillis()
                    _signalStrength.value = (65..95).random()
                }
                delay(_updateInterval.value)
            }
        }
    }

    /**
     * Stop simulation
     */
    private fun stopSimulation() {
        _isSimulationRunning.value = false
    }

    fun toggleSimulation() {
        if (_isSimulationRunning.value) {
            stopSimulation()
        } else {
            startSimulation()
        }
    }

    fun clearDetections() {
        simulatedRadarService.clearDetections()
        _detections.value = emptyList()
    }

    fun setConnectionMode(mode: ConnectionMode) {
        disconnect()
        _connectionMode.value = mode
        _connectionError.value = null
    }

    fun setPiIpAddress(ip: String) {
        _piIpAddress.value = ip
    }

    fun updateSettings(
        interval: Long? = null,
        maxDetections: Int? = null,
        enableVisualization: Boolean? = null
    ) {
        interval?.let { _updateInterval.value = it }
        maxDetections?.let { _maxDetections.value = it }
        enableVisualization?.let { _enableRadarVisualization.value = it }
    }

    suspend fun exportData(format: ExportFormat): Result<String> {
        return try {
            val filename = when (format) {
                ExportFormat.CSV -> exportUtils.exportToCsv(_detections.value)
                ExportFormat.JSON -> exportUtils.exportToJson(_detections.value)
            }
            Result.success(filename)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

enum class ConnectionStatus {
    DISCONNECTED,
    SCANNING,
    CONNECTING,
    CONNECTED,
    SIMULATED,
    ERROR
}

enum class ConnectionMode {
    BLUETOOTH,
    NETWORK,
    SIMULATION
}

enum class ExportFormat {
    CSV,
    JSON
}