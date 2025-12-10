package com.group4.pulse.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.group4.pulse.data.RadarDetection
import com.group4.pulse.viewmodel.RadarViewModel
import kotlin.math.*

@OptIn(ExperimentalTextApi::class)
@Composable
fun VisualizationScreen(viewModel: RadarViewModel) {
    val detections by viewModel.detections.collectAsState()
    val isSimulationRunning by viewModel.isSimulationRunning.collectAsState()
    val enableVisualization by viewModel.enableRadarVisualization.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Title Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Text(
                "Detection Grid Visualization",
                modifier = Modifier.padding(16.dp),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (enableVisualization) {
            // Grid Display
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black)
            ) {
                DetectionGrid(
                    detections = detections,
                    gridSize = 12f, // 12x12 meter grid (-6m to 6m range)
                    maxDepth = 6f // 6m max range sensor
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Depth Scale Legend
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp)
                ) {
                    Text(
                        "Depth Scale (Distance from Sensor)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    DepthGradientBar()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("0m", fontSize = 12.sp)
                        Text("3m", fontSize = 12.sp)
                        Text("6m", fontSize = 12.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Statistics
            DetectionStatistics(detections)
        } else {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Text(
                    "Visualization disabled. Enable in Settings.",
                    modifier = Modifier.padding(32.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalTextApi::class)
@Composable
fun DetectionGrid(
    detections: List<RadarDetection>,
    gridSize: Float,
    maxDepth: Float
) {
    val textMeasurer = rememberTextMeasurer()

    Canvas(modifier = Modifier.fillMaxSize()) {
        val canvasSize = size.minDimension
        val padding = canvasSize * 0.1f
        val gridPixelSize = canvasSize - 2 * padding
        val pixelsPerMeter = gridPixelSize / gridSize
        val center = size.center

        // Draw grid background
        drawRect(
            color = Color(0xFF0A0A0A),
            topLeft = Offset(padding, padding),
            size = Size(gridPixelSize, gridPixelSize)
        )

        // Draw grid lines
        val gridLineColor = Color(0xFF1A1A1A)
        val majorGridColor = Color(0xFF2A2A2A)
        val gridStep = 1f // Grid lines every 1 meter for 12m grid
        val numLines = (gridSize / gridStep).toInt() + 1

        for (i in 0 until numLines) {
            val offset = i * gridStep * pixelsPerMeter
            val isMajor = i % 2 == 0 // Major lines every 2 meters
            val color = if (isMajor) majorGridColor else gridLineColor
            val strokeWidth = if (isMajor) 2f else 1f

            // Vertical lines
            drawLine(
                color = color,
                start = Offset(padding + offset, padding),
                end = Offset(padding + offset, padding + gridPixelSize),
                strokeWidth = strokeWidth
            )

            // Horizontal lines
            drawLine(
                color = color,
                start = Offset(padding, padding + offset),
                end = Offset(padding + gridPixelSize, padding + offset),
                strokeWidth = strokeWidth
            )
        }

        // Draw center axes at origin (0,0) - now at center of canvas
        // Y-axis (vertical line at x=0)
        drawLine(
            color = Color(0xFF606060),
            start = Offset(center.x, padding),
            end = Offset(center.x, padding + gridPixelSize),
            strokeWidth = 3f
        )
        // X-axis (horizontal line at y=0)
        drawLine(
            color = Color(0xFF606060),
            start = Offset(padding, center.y),
            end = Offset(padding + gridPixelSize, center.y),
            strokeWidth = 3f
        )

        // Draw arrows to indicate direction
        val arrowSize = 15f
        // Y-axis arrow (pointing up for positive Y)
        drawLine(
            color = Color(0xFF606060),
            start = Offset(center.x, padding),
            end = Offset(center.x - arrowSize / 2, padding + arrowSize),
            strokeWidth = 3f
        )
        drawLine(
            color = Color(0xFF606060),
            start = Offset(center.x, padding),
            end = Offset(center.x + arrowSize / 2, padding + arrowSize),
            strokeWidth = 3f
        )
        // X-axis arrow (pointing right for positive X)
        drawLine(
            color = Color(0xFF606060),
            start = Offset(padding + gridPixelSize, center.y),
            end = Offset(padding + gridPixelSize - arrowSize, center.y - arrowSize / 2),
            strokeWidth = 3f
        )
        drawLine(
            color = Color(0xFF606060),
            start = Offset(padding + gridPixelSize, center.y),
            end = Offset(padding + gridPixelSize - arrowSize, center.y + arrowSize / 2),
            strokeWidth = 3f
        )

        // Draw axis labels
        val labelStyle = TextStyle(
            color = Color.Gray,
            fontSize = 10.sp
        )

        // X-axis labels (now -6 to 6m range)
        val xLabels = listOf(-6f, -4f, -2f, 0f, 2f, 4f, 6f)
        xLabels.forEach { value ->
            val x = center.x + (value * pixelsPerMeter)
            val label = "${value.toInt()}"
            val textLayout = textMeasurer.measure(label, labelStyle)
            drawIntoCanvas { canvas ->
                textLayout.let {
                    val offset = Offset(
                        x - it.size.width / 2,
                        center.y + 5
                    )
                    it.draw(canvas, Color.Gray, offset)
                }
            }
        }

        // Y-axis labels (now -6 to 6m range)
        val yLabels = listOf(-6f, -4f, -2f, 0f, 2f, 4f, 6f)
        yLabels.forEach { value ->
            val y = center.y - (value * pixelsPerMeter) // Inverted for standard coords
            val label = "${value.toInt()}"
            val textLayout = textMeasurer.measure(label, labelStyle)
            drawIntoCanvas { canvas ->
                textLayout.let {
                    val offset = Offset(
                        center.x - it.size.width - 5,
                        y - it.size.height / 2
                    )
                    it.draw(canvas, Color.Gray, offset)
                }
            }
        }

        // Draw axis labels with directional information
        val xLabel = "X (meters) →"
        val yLabel = "Y (meters) ↑"
        val originLabel = "Origin (0,0)"
        val axisLabelStyle = TextStyle(
            color = Color.LightGray,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )

        // X-axis label at bottom
        val xTextLayout = textMeasurer.measure(xLabel, axisLabelStyle)
        drawIntoCanvas { canvas ->
            xTextLayout.draw(
                canvas,
                Color.LightGray,
                Offset(center.x - xTextLayout.size.width / 2, size.height - 20)
            )
        }

        // Y-axis label on left side
        val yTextLayout = textMeasurer.measure(yLabel, axisLabelStyle)
        drawIntoCanvas { canvas ->
            yTextLayout.draw(
                canvas,
                Color.LightGray,
                Offset(5f, center.y - yTextLayout.size.height / 2)
            )
        }

        // Origin label at center
        val originLabelStyle = TextStyle(
            color = Color(0xFF909090),
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium
        )
        val originTextLayout = textMeasurer.measure(originLabel, originLabelStyle)
        drawIntoCanvas { canvas ->
            originTextLayout.draw(
                canvas,
                Color(0xFF909090),
                Offset(center.x + 5, center.y + 5)
            )
        }

        // Draw detections
        // Coordinate system:
        // - X position: -6m to +6m (left to right)
        // - Y position: -6m to +6m (bottom to top)
        // - Depth (Z): 0m to 6m (always positive, represents distance from sensor)
        detections.forEach { detection ->
            // Convert world coordinates to canvas coordinates
            val x = center.x + (detection.xPosition * pixelsPerMeter)
            val y = center.y - (detection.yPosition * pixelsPerMeter) // Invert Y for standard coordinate system

            // Skip if outside grid bounds (-6 to 6m range for X and Y)
            if (detection.xPosition < -6 || detection.xPosition > 6 ||
                detection.yPosition < -6 || detection.yPosition > 6) {
                return@forEach
            }

            // Calculate color based on depth (Z-axis, always positive 0-6m)
            // Depth represents distance from sensor, not position
            val depthColor = getDepthColor(detection.depth, maxDepth)

            // Draw detection point with glow effect
            // Outer glow
            drawCircle(
                color = depthColor.copy(alpha = 0.2f),
                radius = 12.dp.toPx(),
                center = Offset(x, y)
            )

            // Middle glow
            drawCircle(
                color = depthColor.copy(alpha = 0.4f),
                radius = 8.dp.toPx(),
                center = Offset(x, y)
            )

            // Inner dot
            drawCircle(
                color = depthColor,
                radius = 4.dp.toPx(),
                center = Offset(x, y)
            )

            // Draw depth value next to the point
            val depthText = String.format("%.1fm", detection.depth)
            val textStyle = TextStyle(
                color = Color.White,
                fontSize = 8.sp
            )
            val textLayout = textMeasurer.measure(depthText, textStyle)
            drawIntoCanvas { canvas ->
                textLayout.draw(
                    canvas,
                    Color.White,
                    Offset(x + 10, y - 10)
                )
            }
        }
    }
}

private fun TextLayoutResult.draw(
    canvas: androidx.compose.ui.graphics.Canvas,
    gray: androidx.compose.ui.graphics.Color,
    offset: androidx.compose.ui.geometry.Offset
) {
}

fun getDepthColor(depth: Float, maxDepth: Float): Color {
    val normalized = (depth / maxDepth).coerceIn(0f, 1f)

    return when {
        normalized < 0.33f -> {
            // Green to Yellow (0-2m for 6m sensor)
            val t = normalized / 0.33f
            Color(
                red = (t * 255).toInt(),
                green = 255,
                blue = 0
            )
        }
        normalized < 0.67f -> {
            // Yellow to Orange (2-4m for 6m sensor)
            val t = (normalized - 0.33f) / 0.34f
            Color(
                red = 255,
                green = (255 * (1 - t * 0.5f)).toInt(),
                blue = 0
            )
        }
        else -> {
            // Orange to Red (4-6m for 6m sensor)
            val t = (normalized - 0.67f) / 0.33f
            Color(
                red = 255,
                green = (128 * (1 - t)).toInt(),
                blue = 0
            )
        }
    }
}

@Composable
fun DepthGradientBar() {
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(20.dp)
    ) {
        val gradientColors = listOf(
            Color(0xFF00FF00), // Green
            Color(0xFFFFFF00), // Yellow
            Color(0xFFFF8800), // Orange
            Color(0xFFFF0000)  // Red
        )

        drawRect(
            brush = Brush.horizontalGradient(gradientColors),
            size = size
        )
    }
}

@Composable
fun DetectionStatistics(detections: List<RadarDetection>) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                "Detection Statistics",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            val avgDepth = if (detections.isNotEmpty())
                detections.map { it.depth }.average() else 0.0
            val avgSnr = if (detections.isNotEmpty())
                detections.map { it.snr }.average() else 0.0
            val maxDepth = detections.maxOfOrNull { it.depth } ?: 0f
            val minDepth = detections.minOfOrNull { it.depth } ?: 0f

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StatItem("Total Detections", "${detections.size}")
                StatItem("Avg Depth", "${String.format("%.2f", avgDepth)} m")
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StatItem("Min Depth", "${String.format("%.2f", minDepth)} m")
                StatItem("Max Depth", "${String.format("%.2f", maxDepth)} m")
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StatItem("Avg SNR", "${String.format("%.1f", avgSnr)} dB")
                StatItem("Grid Range", "±6m (X,Y)")
            }
        }
    }
}

@Composable
fun StatItem(label: String, value: String) {
    Column {
        Text(
            label,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
        Text(
            value,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
    }
}