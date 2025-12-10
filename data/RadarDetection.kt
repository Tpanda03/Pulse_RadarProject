package com.group4.pulse.data

import java.util.UUID

data class RadarDetection(
    val objectId: String = UUID.randomUUID().toString(),
    val xPosition: Float, // meters (horizontal position)
    val yPosition: Float, // meters (vertical position)
    val depth: Float, // meters (0-10m depth)
    val snr: Float, // dB
    val timestamp: Long = System.currentTimeMillis(),
    val confidence: Float = 0.8f, // Detection confidence (0-1)
    val objectType: ObjectType = ObjectType.UNKNOWN
)

enum class ObjectType {
    HUMAN,
    VEHICLE,
    METALLIC,
    ORGANIC,
    UNKNOWN
}

data class BleDevice(
    val name: String,
    val address: String,
    val rssi: Int,
    val isConnected: Boolean = false
)
