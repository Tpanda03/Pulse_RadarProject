package com.group4.pulse.service

import com.group4.pulse.data.ObjectType
import com.group4.pulse.data.RadarDetection
import kotlin.random.Random

class SimulatedRadarService {

    private var lastGenerationTime = 0L
    private val generationInterval = 8000L // Generate new detection every 8 seconds
    private val existingDetections = mutableListOf<RadarDetection>()
    private val gridSize = 20f // 20x20 meter grid
    private val maxDepth = 10f

    fun generateDetections(): List<RadarDetection> {
        val currentTime = System.currentTimeMillis()

        // Only generate new detection occasionally
        if (currentTime - lastGenerationTime > generationInterval && Random.nextFloat() < 0.4f) {
            lastGenerationTime = currentTime

            // Generate a new static detection at a random position
            val newDetection = RadarDetection(
                xPosition = Random.nextFloat() * gridSize - gridSize/2, // -10 to +10 meters
                yPosition = Random.nextFloat() * gridSize - gridSize/2, // -10 to +10 meters
                depth = Random.nextFloat() * maxDepth, // 0 to 10 meters depth
                snr = Random.nextFloat() * 30 + 5, // 5-35 dB
                confidence = calculateConfidence(Random.nextFloat() * 30 + 5),
                objectType = ObjectType.values().random()
            )

            existingDetections.add(newDetection)

            // Keep only last 30 detections
            if (existingDetections.size > 30) {
                existingDetections.removeAt(0)
            }
        }

        // Return all existing detections (they don't move or change)
        return existingDetections.toList()
    }

    private fun calculateConfidence(snr: Float): Float {
        return ((snr - 5) / 30).coerceIn(0f, 1f)
    }

    fun clearDetections() {
        existingDetections.clear()
        lastGenerationTime = 0L
    }
}