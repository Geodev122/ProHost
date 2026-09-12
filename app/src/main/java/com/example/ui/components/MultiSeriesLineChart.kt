package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** One named, colored series of (dayBucketMillis, value) points for [MultiSeriesLineChart]. */
data class ChartSeries(
    val label: String,
    val color: Color,
    val points: List<Pair<Long, Double>>
)

/**
 * A small Canvas-based multi-line chart — this app has no charting library and no
 * existing chart component to reuse (the one other Canvas usage,
 * LebanonMapCanvas.kt, draws a map, not a data series). Built specifically for the
 * Owners & Payments tab's 3-line comparison (Pro Host upgrades / listings published /
 * Whish settlements, each by date) rather than as a general-purpose library — kept
 * deliberately simple: no zoom/pan/tooltips, just a clear, correctly-scaled read of
 * up to a few series over the same time axis.
 */
@Composable
fun MultiSeriesLineChart(
    series: List<ChartSeries>,
    modifier: Modifier = Modifier
) {
    val allPoints = series.flatMap { it.points }
    val minX = allPoints.minOfOrNull { it.first }
    val maxX = allPoints.maxOfOrNull { it.first }
    val maxY = (allPoints.maxOfOrNull { it.second } ?: 0.0).coerceAtLeast(1.0)

    Column(modifier = modifier.fillMaxWidth()) {
        // Legend
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            series.forEach { s ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Canvas(modifier = Modifier.size(10.dp)) {
                        drawRect(color = s.color)
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(s.label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (minX == null || maxX == null || minX == maxX) {
            Text(
                "Not enough data in this range to draw a chart.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 24.dp)
            )
            return
        }

        val axisColor = MaterialTheme.colorScheme.onSurfaceVariant
        val gridColor = MaterialTheme.colorScheme.outlineVariant

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
        ) {
            val leftPad = 4f
            val bottomPad = 4f
            val chartWidth = size.width - leftPad
            val chartHeight = size.height - bottomPad

            // Horizontal gridlines at 0%, 50%, 100% of maxY
            listOf(0f, 0.5f, 1f).forEach { fraction ->
                val y = chartHeight - (chartHeight * fraction)
                drawLine(
                    color = gridColor,
                    start = Offset(leftPad, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1f
                )
            }

            fun xFor(millis: Long): Float =
                leftPad + (chartWidth * ((millis - minX).toFloat() / (maxX - minX).toFloat()))
            fun yFor(value: Double): Float =
                chartHeight - (chartHeight * (value / maxY)).toFloat()

            series.forEach { s ->
                val sorted = s.points.sortedBy { it.first }
                if (sorted.size < 2) {
                    // A single point still deserves a visible mark.
                    sorted.firstOrNull()?.let { (x, y) ->
                        drawCircle(color = s.color, radius = 4f, center = Offset(xFor(x), yFor(y)))
                    }
                    return@forEach
                }
                val path = androidx.compose.ui.graphics.Path()
                sorted.forEachIndexed { index, (x, y) ->
                    val point = Offset(xFor(x), yFor(y))
                    if (index == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
                }
                drawPath(path = path, color = s.color, style = Stroke(width = 4f))
            }
        }

        // X-axis start/end date labels
        val sdf = remember(minX, maxX) { SimpleDateFormat("MMM d", Locale.US) }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(sdf.format(Date(minX)), style = MaterialTheme.typography.labelSmall, color = axisColor)
            Text(sdf.format(Date(maxX)), style = MaterialTheme.typography.labelSmall, color = axisColor)
        }
    }
}
