package com.group4.pulse.service

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.os.Build
import android.os.ParcelUuid
import android.util.Log
import com.group4.pulse.data.RadarDetection
import com.group4.pulse.data.ObjectType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.*

/**
 * BLE Service for communication with Raspberry Pi
 *
 * Expected GATT Structure:
 * - Service UUID: 00001101-0000-1000-8000-00805F9B34FB
 * - Characteristic UUID (Radar Data): 00002A01-0000-1000-8000-00805F9B34FB
 *
 * Data Format (20 bytes):
 * - Bytes 0-3: X position (float, meters)
 * - Bytes 4-7: Y position (float, meters)
 * - Bytes 8-11: Depth (float, meters)
 * - Bytes 12-15: SNR (float, dB)
 * - Bytes 16-17: Timestamp offset (uint16, ms)
 * - Byte 18: Object type (enum)
 * - Byte 19: Confidence (0-255 mapped to 0-1)
 */
@SuppressLint("MissingPermission")
class BleService(private val context: Context) {

    companion object {
        private const val TAG = "BleService"

        // UUIDs for BLE communication with Raspberry Pi
        val SERVICE_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        val CHARACTERISTIC_RADAR_DATA: UUID = UUID.fromString("00002A01-0000-1000-8000-00805F9B34FB")
        val CHARACTERISTIC_COMMAND: UUID = UUID.fromString("00002A02-0000-1000-8000-00805F9B34FB")
        val DESCRIPTOR_CONFIG: UUID = UUID.fromString("00002902-0000-1000-8000-00805F9B34FB")

        const val SCAN_PERIOD = 10000L // 10 seconds
    }

    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter
    }

    private var bluetoothGatt: BluetoothGatt? = null
    private var bluetoothLeScanner: BluetoothLeScanner? = null
    private var scanning = false

    // State flows
    private val _connectionState = MutableStateFlow(BleConnectionState.DISCONNECTED)
    val connectionState: StateFlow<BleConnectionState> = _connectionState

    private val _availableDevices = MutableStateFlow<List<BleDeviceInfo>>(emptyList())
    val availableDevices: StateFlow<List<BleDeviceInfo>> = _availableDevices

    private val _detections = MutableSharedFlow<RadarDetection>()
    val detections: SharedFlow<RadarDetection> = _detections

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError

    enum class BleConnectionState {
        DISCONNECTED,
        SCANNING,
        CONNECTING,
        CONNECTED,
        ERROR
    }

    data class BleDeviceInfo(
        val name: String,
        val address: String,
        val rssi: Int
    )

    /**
     * Check if Bluetooth is enabled
     */
    fun isBluetoothEnabled(): Boolean {
        return bluetoothAdapter?.isEnabled == true
    }

    /**
     * Start scanning for BLE devices
     */
    fun startScan() {
        if (!isBluetoothEnabled()) {
            _lastError.value = "Bluetooth is not enabled"
            _connectionState.value = BleConnectionState.ERROR
            return
        }

        bluetoothLeScanner = bluetoothAdapter?.bluetoothLeScanner

        if (scanning) {
            return
        }

        val scanFilters = listOf(
            ScanFilter.Builder()
                .setServiceUuid(ParcelUuid(SERVICE_UUID))
                .build()
        )

        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        scanning = true
        _connectionState.value = BleConnectionState.SCANNING
        _availableDevices.value = emptyList()

        bluetoothLeScanner?.startScan(scanFilters, scanSettings, scanCallback)

        // Stop scanning after a period
        CoroutineScope(Dispatchers.Main).launch {
            kotlinx.coroutines.delay(SCAN_PERIOD)
            stopScan()
        }

        Log.d(TAG, "Started BLE scan")
    }

    /**
     * Stop scanning for BLE devices
     */
    fun stopScan() {
        if (scanning) {
            scanning = false
            bluetoothLeScanner?.stopScan(scanCallback)

            if (_connectionState.value == BleConnectionState.SCANNING) {
                _connectionState.value = BleConnectionState.DISCONNECTED
            }

            Log.d(TAG, "Stopped BLE scan")
        }
    }

    /**
     * Connect to a BLE device
     */
    fun connect(deviceAddress: String) {
        stopScan()

        val device = bluetoothAdapter?.getRemoteDevice(deviceAddress)
        if (device == null) {
            _lastError.value = "Device not found"
            _connectionState.value = BleConnectionState.ERROR
            return
        }

        _connectionState.value = BleConnectionState.CONNECTING

        bluetoothGatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        } else {
            device.connectGatt(context, false, gattCallback)
        }

        Log.d(TAG, "Connecting to device: $deviceAddress")
    }

    /**
     * Disconnect from BLE device
     */
    fun disconnect() {
        bluetoothGatt?.disconnect()
        bluetoothGatt?.close()
        bluetoothGatt = null
        _connectionState.value = BleConnectionState.DISCONNECTED

        Log.d(TAG, "Disconnected from device")
    }

    /**
     * Send command to Raspberry Pi
     */
    fun sendCommand(command: ByteArray) {
        val service = bluetoothGatt?.getService(SERVICE_UUID)
        val characteristic = service?.getCharacteristic(CHARACTERISTIC_COMMAND)

        characteristic?.let {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                bluetoothGatt?.writeCharacteristic(it, command, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
            } else {
                it.value = command
                bluetoothGatt?.writeCharacteristic(it)
            }
        }
    }

    /**
     * Scan callback for BLE devices
     */
    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            val deviceName = device.name ?: "Unknown Device"

            // Filter for PULSE devices
            if (deviceName.contains("PULSE", ignoreCase = true) ||
                deviceName.contains("Raspberry", ignoreCase = true)) {

                val deviceInfo = BleDeviceInfo(
                    name = deviceName,
                    address = device.address,
                    rssi = result.rssi
                )

                val currentList = _availableDevices.value.toMutableList()
                val existingIndex = currentList.indexOfFirst { it.address == device.address }

                if (existingIndex >= 0) {
                    currentList[existingIndex] = deviceInfo
                } else {
                    currentList.add(deviceInfo)
                }

                _availableDevices.value = currentList
                Log.d(TAG, "Found device: $deviceName at ${device.address}")
            }
        }

        override fun onScanFailed(errorCode: Int) {
            _lastError.value = "Scan failed with error code: $errorCode"
            _connectionState.value = BleConnectionState.ERROR
            scanning = false

            Log.e(TAG, "Scan failed: $errorCode")
        }
    }

    /**
     * GATT callback for BLE connection events
     */
    private val gattCallback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Log.d(TAG, "Connected to GATT server")
                    _connectionState.value = BleConnectionState.CONNECTED
                    gatt?.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.d(TAG, "Disconnected from GATT server")
                    _connectionState.value = BleConnectionState.DISCONNECTED
                    bluetoothGatt?.close()
                    bluetoothGatt = null
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "Services discovered")

                val service = gatt?.getService(SERVICE_UUID)
                if (service != null) {
                    enableNotifications(gatt)
                } else {
                    Log.e(TAG, "Radar service not found")
                    _lastError.value = "Radar service not found on device"
                }
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            if (characteristic.uuid == CHARACTERISTIC_RADAR_DATA) {
                parseRadarData(value)
            }
        }

        // For older Android versions
        @Deprecated("Deprecated in API 33")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?
        ) {
            if (characteristic?.uuid == CHARACTERISTIC_RADAR_DATA) {
                characteristic.value?.let { parseRadarData(it) }
            }
        }
    }

    /**
     * Enable notifications for radar data characteristic
     */
    private fun enableNotifications(gatt: BluetoothGatt) {
        val service = gatt.getService(SERVICE_UUID)
        val characteristic = service?.getCharacteristic(CHARACTERISTIC_RADAR_DATA)

        characteristic?.let { char ->
            gatt.setCharacteristicNotification(char, true)

            val descriptor = char.getDescriptor(DESCRIPTOR_CONFIG)
            descriptor?.let { desc ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    gatt.writeDescriptor(desc, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                } else {
                    desc.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    gatt.writeDescriptor(desc)
                }

                Log.d(TAG, "Enabled notifications for radar data")
            }
        }
    }

    /**
     * Parse binary radar data from Raspberry Pi
     */
    private fun parseRadarData(data: ByteArray) {
        if (data.size >= 20) {
            try {
                val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)

                val xPosition = buffer.getFloat(0)
                val yPosition = buffer.getFloat(4)
                val depth = buffer.getFloat(8)
                val snr = buffer.getFloat(12)
                val timestamp = buffer.getShort(16).toInt() and 0xFFFF
                val objectType = data[18].toInt()
                val confidence = (data[19].toInt() and 0xFF) / 255f

                val detection = RadarDetection(
                    objectId = UUID.randomUUID().toString(),
                    xPosition = xPosition,
                    yPosition = yPosition,
                    depth = depth,
                    snr = snr,
                    timestamp = System.currentTimeMillis(),
                    confidence = confidence,
                    objectType = ObjectType.values().getOrNull(objectType) ?: ObjectType.UNKNOWN
                )

                CoroutineScope(Dispatchers.Main).launch {
                    _detections.emit(detection)
                }

                Log.d(TAG, "Received detection: X=$xPosition, Y=$yPosition, Depth=$depth")

            } catch (e: Exception) {
                Log.e(TAG, "Error parsing radar data", e)
                _lastError.value = "Error parsing data: ${e.message}"
            }
        } else {
            Log.w(TAG, "Received incomplete data packet: ${data.size} bytes")
        }
    }
}