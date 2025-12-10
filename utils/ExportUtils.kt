package com.group4.pulse.utils

import android.content.Context
import com.group4.pulse.data.RadarDetection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class ExportUtils(private val context: Context) {

    private val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
    private val timestampFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())

    suspend fun exportToCsv(detections: List<RadarDetection>): String = withContext(Dispatchers.IO) {
        val timestamp = dateFormat.format(Date())
        val filename = "radar_data_$timestamp.csv"
        val file = File(context.getExternalFilesDir(null), filename)

        val csvContent = buildString {
            // Header
            appendLine("Timestamp,Object_ID,X_Position_m,Y_Position_m,Depth_m,SNR_dB,Confidence,Object_Type")

            // Data rows
            detections.forEach { detection ->
                append(timestampFormat.format(Date(detection.timestamp))).append(",")
                append(detection.objectId).append(",")
                append(String.format("%.3f", detection.xPosition)).append(",")
                append(String.format("%.3f", detection.yPosition)).append(",")
                append(String.format("%.3f", detection.depth)).append(",")
                append(String.format("%.2f", detection.snr)).append(",")
                append(String.format("%.3f", detection.confidence)).append(",")
                appendLine(detection.objectType.name)
            }
        }

        file.writeText(csvContent)
        filename
    }

    suspend fun exportToJson(detections: List<RadarDetection>): String = withContext(Dispatchers.IO) {
        val timestamp = dateFormat.format(Date())
        val filename = "radar_data_$timestamp.json"
        val file = File(context.getExternalFilesDir(null), filename)

        val jsonRoot = JSONObject().apply {
            put("export_timestamp", System.currentTimeMillis())
            put("export_date", timestampFormat.format(Date()))
            put("detection_count", detections.size)

            val detectionsArray = JSONArray()
            detections.forEach { detection ->
                val detectionObj = JSONObject().apply {
                    put("timestamp", detection.timestamp)
                    put("timestamp_formatted", timestampFormat.format(Date(detection.timestamp)))
                    put("object_id", detection.objectId)
                    put("x_position_m", detection.xPosition)
                    put("y_position_m", detection.yPosition)
                    put("depth_m", detection.depth)
                    put("snr_db", detection.snr)
                    put("confidence", detection.confidence)
                    put("object_type", detection.objectType.name)
                }
                detectionsArray.put(detectionObj)
            }
            put("detections", detectionsArray)

            // Add metadata
            val metadata = JSONObject().apply {
                put("app_version", "1.0.0")
                put("radar_mode", "FMCW")
                put("max_depth_m", 10)
                put("grid_size_m", "20x20")
                put("depth_resolution_m", 0.1)
            }
            put("metadata", metadata)
        }

        file.writeText(jsonRoot.toString(4)) // Pretty print with 4-space indentation
        filename
    }

    fun getExportDirectory(): File? {
        return context.getExternalFilesDir(null)
    }

    fun listExportedFiles(): List<File> {
        val directory = getExportDirectory()
        return directory?.listFiles { file ->
            file.name.startsWith("radar_data_") &&
                    (file.name.endsWith(".csv") || file.name.endsWith(".json"))
        }?.toList() ?: emptyList()
    }

    fun deleteExportedFile(filename: String): Boolean {
        val file = File(getExportDirectory(), filename)
        return if (file.exists()) {
            file.delete()
        } else {
            false
        }
    }

    fun getFileSize(filename: String): Long {
        val file = File(getExportDirectory(), filename)
        return if (file.exists()) file.length() else 0L
    }
}