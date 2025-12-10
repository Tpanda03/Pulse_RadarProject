package com.group4.pulse.service

import com.group4.pulse.data.RadarDetection
import com.group4.pulse.data.ObjectType
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.InetSocketAddress
import java.net.Socket
import java.util.UUID

/**
 * Network service for TCP/IP communication with Raspberry Pi
 * Using this for emulator testing since Android emulator doesn't support Bluetooth
 */
class NetworkService {

    companion object {
        // Default connection settings
        const val DEFAULT_PORT = 5555
        const val CONNECTION_TIMEOUT = 5000 // 5 seconds
        const val READ_TIMEOUT = 30000 // 30 seconds
    }

    private var socket: Socket? = null
    private var reader: BufferedReader? = null
    private var writer: PrintWriter? = null
    private var connectionJob: Job? = null

    private val _connectionState = MutableStateFlow(NetworkConnectionState.DISCONNECTED)
    val connectionState: StateFlow<NetworkConnectionState> = _connectionState.asStateFlow()

    private val _detections = MutableSharedFlow<RadarDetection>()
    val detections: SharedFlow<RadarDetection> = _detections.asSharedFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    enum class NetworkConnectionState {
        DISCONNECTED,
        CONNECTING,
        CONNECTED,
        ERROR
    }

    /**
     * Connect to Raspberry Pi server
     * For emulator: Use 10.0.2.2 to connect to host machine
     * For real network: Use actual Pi IP address
     */
    fun connect(ipAddress: String, port: Int = DEFAULT_PORT) {
        disconnect() // Ensure clean state

        connectionJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                _connectionState.value = NetworkConnectionState.CONNECTING
                _lastError.value = null

                // Create socket with timeout
                socket = Socket().apply {
                    soTimeout = READ_TIMEOUT
                    connect(InetSocketAddress(ipAddress, port), CONNECTION_TIMEOUT)
                }

                // Setup readers/writers
                reader = BufferedReader(InputStreamReader(socket?.getInputStream()))
                writer = PrintWriter(socket?.getOutputStream(), true)

                _connectionState.value = NetworkConnectionState.CONNECTED

                // Start listening for data
                listenForData()

            } catch (e: Exception) {
                _connectionState.value = NetworkConnectionState.ERROR
                _lastError.value = "Connection failed: ${e.message}"
                disconnect()
            }
        }
    }

    /**
     * Connect to emulator's host machine (your computer)
     * Use this when testing with Android emulator
     */
    fun connectToEmulatorHost(port: Int = DEFAULT_PORT) {
        // 10.0.2.2 is the special alias to the host loopback interface (127.0.0.1 on your PC)
        connect("10.0.2.2", port)
    }

    /**
     * Connect to Raspberry Pi on local network
     */
    fun connectToPi(piIpAddress: String, port: Int = DEFAULT_PORT) {
        connect(piIpAddress, port)
    }

    private suspend fun listenForData() {
        withContext(Dispatchers.IO) {
            try {
                while (_connectionState.value == NetworkConnectionState.CONNECTED) {
                    val line = reader?.readLine() ?: break
                    processReceivedData(line)
                }
            } catch (e: Exception) {
                if (_connectionState.value == NetworkConnectionState.CONNECTED) {
                    _connectionState.value = NetworkConnectionState.ERROR
                    _lastError.value = "Connection lost: ${e.message}"
                }
            }
        }
    }

    private suspend fun processReceivedData(data: String) {
        try {
            val json = JSONObject(data)
            val type = json.getString("type")

            when (type) {
                "detection" -> {
                    val detectionData = json.getJSONObject("data")
                    val detection = parseDetection(detectionData)
                    _detections.emit(detection)
                }
                "connection" -> {
                    // Connection confirmation message
                    val message = json.getString("message")
                    println("Server message: $message")
                }
                else -> {
                    println("Unknown message type: $type")
                }
            }
        } catch (e: Exception) {
            println("Error parsing data: ${e.message}")
        }
    }

    private fun parseDetection(json: JSONObject): RadarDetection {
        return RadarDetection(
            objectId = json.optString("object_id", UUID.randomUUID().toString()),
            xPosition = json.getDouble("x_position").toFloat(),
            yPosition = json.getDouble("y_position").toFloat(),
            depth = json.getDouble("depth").toFloat(),
            snr = json.getDouble("snr").toFloat(),
            timestamp = json.optLong("timestamp", System.currentTimeMillis()),
            confidence = (json.optInt("confidence", 200) / 255f).coerceIn(0f, 1f),
            objectType = try {
                ObjectType.values()[json.optInt("object_type", 0)]
            } catch (e: Exception) {
                ObjectType.UNKNOWN
            }
        )
    }

    /**
     * Send command to Raspberry Pi
     */
    fun sendCommand(command: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val json = JSONObject().apply {
                    put("type", "command")
                    put("command", command)
                    put("timestamp", System.currentTimeMillis())
                }
                writer?.println(json.toString())
            } catch (e: Exception) {
                _lastError.value = "Failed to send command: ${e.message}"
            }
        }
    }

    /**
     * Disconnect from server
     */
    fun disconnect() {
        connectionJob?.cancel()

        try {
            reader?.close()
            writer?.close()
            socket?.close()
        } catch (e: Exception) {
            // Ignore close exceptions
        } finally {
            reader = null
            writer = null
            socket = null
            _connectionState.value = NetworkConnectionState.DISCONNECTED
        }
    }

    /**
     * Test if connection is active
     */
    fun isConnected(): Boolean {
        return _connectionState.value == NetworkConnectionState.CONNECTED &&
                socket?.isConnected == true &&
                socket?.isClosed == false
    }
}