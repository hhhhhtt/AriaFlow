package dev.arialite

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.semantics.*
import kotlin.math.*

internal data class PlotPoint(val x: Float, val y: Float)

internal class SpeedWindow(history: List<SpeedSample>) {
    val end = history.lastOrNull()?.time ?: 0L
    val start = maxOf(history.firstOrNull()?.time ?: end, end - 60000)
    // Keep the preceding point to clip continuous curves exactly at the left edge.
    val samples =
        history
            .dropWhile { it.time < start }
            .let { visible ->
                listOfNotNull(history.lastOrNull { it.time < start && start - it.time <= 10000 }) +
                    visible
            }
    val span = maxOf(1L, end - start)
    val maxSpeed =
        maxOf(1024L * 1024L, samples.maxOfOrNull { maxOf(it.down ?: 0, it.up ?: 0) } ?: 0)
    val ceiling = 2.0.pow(ceil(log2(maxSpeed.toDouble()))).toFloat()

    fun runs(upload: Boolean): List<List<PlotPoint>> {
        if (samples.none { it.time >= start && ((it.down ?: 0L) > 0L || (it.up ?: 0L) > 0L) })
            return emptyList()
        val result = mutableListOf<MutableList<PlotPoint>>()
        var previous: Long? = null
        for (sample in samples) {
            val speed = if (upload) sample.up else sample.down
            if (speed == null) {
                previous = null
                continue
            }
            if (previous == sample.time) continue
            if (previous == null || sample.time - previous !in 1..10000) result.add(mutableListOf())
            result
                .last()
                .add(PlotPoint((sample.time - start).toFloat() / span, 1f - speed / ceiling))
            previous = sample.time
        }
        return result
    }
}

// Monotone Hermite tangents: weighted harmonic mean for unequal sampling intervals.
internal fun tangents(p: List<PlotPoint>): FloatArray {
    val h = FloatArray(p.size - 1) { p[it + 1].x - p[it].x }
    val slopes = FloatArray(h.size) { (p[it + 1].y - p[it].y) / h[it] }
    return FloatArray(p.size) { i ->
        when (i) {
            0 -> slopes.first()
            p.lastIndex -> slopes.last()
            else -> {
                val a = slopes[i - 1]
                val b = slopes[i]
                if (a * b <= 0) 0f
                else {
                    val w1 = 2 * h[i] + h[i - 1]
                    val w2 = h[i] + 2 * h[i - 1]
                    (w1 + w2) / (w1 / a + w2 / b)
                }
            }
        }
    }
}

@Composable
internal fun SpeedChart(state: AppState) {
    val c = LocalFlow.current
    val m = FlowMetric
    val plot = remember(state.history) { SpeedWindow(state.history) }
    val downloads = remember(plot) { plot.runs(false) }
    fun readout(speed: Long?) = speed?.let { bytes(it) + "/s" } ?: "—"
    val latest = state.history.lastOrNull()
    val uploads = remember(plot) { plot.runs(true) }
    Surface(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(m.cardRadius),
        color = c.card,
        shadowElevation = m.shadowY,
    ) {
        Column(Modifier.padding(m.s12), verticalArrangement = Arrangement.spacedBy(m.s8)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.AutoMirrored.Outlined.ShowChart, null, Modifier.size(m.icon), tint = c.accent)
                Spacer(Modifier.width(m.s6))
                Text("网络速度", Modifier.weight(1f), style = FlowType.cardTitle, color = c.textPrimary)
                Text(
                    "↓ ${readout(latest?.down)}",
                    style = FlowType.readout,
                    color = c.downloadColor,
                )
                Spacer(Modifier.width(m.s8))
                Text("↑ ${readout(latest?.up)}", style = FlowType.readout, color = c.uploadColor)
            }
            Box(Modifier.fillMaxWidth().height(m.chartHeight)) {
                Canvas(
                    Modifier.fillMaxSize().semantics { contentDescription = "最近一分钟网络速度，蓝色下载，橙色上传" }
                ) {
                    val w = size.width
                    val h = size.height
                    val dash =
                        PathEffect.dashPathEffect(floatArrayOf(m.gridDash.toPx(), m.gridGap.toPx()))
                    for (i in 0..4) {
                        val fraction = i / 4f
                        drawLine(
                            c.grid,
                            Offset(w * fraction, 0f),
                            Offset(w * fraction, h),
                            m.gridStroke.toPx(),
                            pathEffect = dash,
                        )
                    }
                    for (i in 0..2) {
                        val fraction = i / 2f
                        drawLine(
                            c.grid,
                            Offset(0f, h * fraction),
                            Offset(w, h * fraction),
                            m.gridStroke.toPx(),
                            pathEffect = dash,
                        )
                    }
                    fun render(points: List<PlotPoint>, upload: Boolean) {
                        if (upload && points.size < 3) return
                        val ink = if (upload) c.uploadColor else c.downloadColor
                        val width = if (upload) m.uploadStroke.toPx() else m.chartStroke.toPx()
                        if (points.size < 3) {
                            val last = points.last()
                            // With two distant samples avoid suggesting a full-width trend.
                            if (points.size == 2 && (last.x - points.first().x) * w <= m.s24.toPx())
                                drawLine(
                                    ink,
                                    Offset(points.first().x * w, points.first().y * h),
                                    Offset(last.x * w, last.y * h),
                                    width,
                                )
                            points.forEach {
                                drawCircle(
                                    ink,
                                    m.s2.toPx(),
                                    Offset((it.x * w).coerceIn(0f, w), it.y * h),
                                )
                            }
                            return
                        }
                        val slopes = tangents(points)
                        val path =
                            Path().apply {
                                moveTo(points.first().x * w, points.first().y * h)
                                for (i in 0 until points.lastIndex) {
                                    val a = points[i]
                                    val b = points[i + 1]
                                    val dx = (b.x - a.x) / 3f
                                    cubicTo(
                                        (a.x + dx) * w,
                                        (a.y + slopes[i] * dx) * h,
                                        (b.x - dx) * w,
                                        (b.y - slopes[i + 1] * dx) * h,
                                        b.x * w,
                                        b.y * h,
                                    )
                                }
                            }
                        if (!upload) {
                            val fill =
                                Path().apply {
                                    addPath(path)
                                    lineTo(points.last().x * w, h)
                                    lineTo(points.first().x * w, h)
                                    close()
                                }
                            drawPath(
                                fill,
                                Brush.verticalGradient(
                                    listOf(
                                        ink.copy(alpha = m.chartFillAlpha),
                                        ink.copy(alpha = 0f),
                                    ),
                                    endY = h,
                                ),
                            )
                        }
                        drawPath(path, ink, style = Stroke(width))
                    }
                    clipRect {
                        downloads.forEach { render(it, false) }
                        uploads.forEach { render(it, true) }
                    }
                }
                Column(Modifier.fillMaxHeight().padding(start = m.s4)) {
                    for (i in 2 downTo 1) {
                        Box(Modifier.weight(1f).padding(top = m.s2)) {
                            Text(
                                "${bytes((plot.ceiling * i / 2).toLong())}/s",
                                Modifier.background(c.axisBacking, RoundedCornerShape(m.s4))
                                    .padding(horizontal = m.s2),
                                style = FlowType.axis,
                                color = c.textSecondary,
                            )
                        }
                    }
                }
            }
        }
    }
}
