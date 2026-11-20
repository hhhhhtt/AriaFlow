package dev.arialite

import org.junit.Assert.*
import org.junit.Test

class SpeedChartTest {
    @Test
    fun zeroWindowKeepsScaleWithoutDrawingSeries() {
        val plot =
            SpeedWindow(
                listOf(
                    SpeedSample(
                        0,
                        42,
                        42,
                    ), // Outside the window, retained for boundary interpolation.
                    SpeedSample(2000, 0, 0),
                    SpeedSample(62000, 0, 0),
                )
            )
        assertEquals(1048576f, plot.ceiling, 0f)
        assertTrue(plot.runs(false).isEmpty())
        assertTrue(plot.runs(true).isEmpty())
    }

    @Test
    fun nonzeroSeriesKeepsRealZeroEndpoints() {
        for (upload in listOf(false, true)) {
            val plot =
                SpeedWindow(
                    listOf(SpeedSample(0, 0, 0), SpeedSample(2000, 42, 42), SpeedSample(4000, 0, 0))
                )
            val points = plot.runs(upload).single()
            assertEquals(3, points.size)
            assertEquals(1f, points.first().y, 0f)
            assertEquals(1f, points.last().y, 0f)
        }
    }

    @Test
    fun partialWindowUsesFullWidth() {
        val plot = SpeedWindow(listOf(0L, 2000L, 4500L).map { SpeedSample(it, 42, 0) })
        val line = plot.runs(false).single()
        assertEquals(0f, line.first().x, 0f)
        assertEquals(1f, line.last().x, 0f)
    }

    @Test
    fun matureWindowSlidesAndBreaksAtMissingSamples() {
        val plot =
            SpeedWindow(
                (0L..70000L step 2000L).map { SpeedSample(it, if (it == 40000L) null else 42, 0) }
            )
        assertEquals(10000L, plot.start)
        assertEquals(2, plot.runs(false).size)
        assertEquals(1f, plot.runs(false).last().last().x, 0f)
    }

    @Test
    fun irregularCubicStaysInsideEverySampleInterval() {
        val p =
            listOf(
                PlotPoint(0f, .3f),
                PlotPoint(.02f, .9f),
                PlotPoint(.6f, .91f),
                PlotPoint(.7f, .2f),
                PlotPoint(1f, .2f),
            )
        val d = tangents(p)
        for (i in 0 until p.lastIndex) {
            val a = p[i]
            val b = p[i + 1]
            val dx = (b.x - a.x) / 3f
            for (j in 0..100) {
                val t = j / 100f
                val u = 1 - t
                val y =
                    u * u * u * a.y +
                        3 * u * u * t * (a.y + d[i] * dx) +
                        3 * u * t * t * (b.y - d[i + 1] * dx) +
                        t * t * t * b.y
                assertTrue(y in minOf(a.y, b.y) - .00001f..maxOf(a.y, b.y) + .00001f)
            }
        }
    }
}
