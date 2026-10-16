package app.zamero.core

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StraightenTest {

    /** Имитирует рисование пальцем: густые точки вдоль контура с дрожанием руки. */
    private fun handDrawn(corners: List<Point>, jitter: Double, seed: Int = 1, closeGap: Double = 0.0): List<Point> {
        val rnd = Random(seed)
        val out = mutableListOf<Point>()
        for (i in corners.indices) {
            val a = corners[i]
            val b = corners[(i + 1) % corners.size]
            val steps = (hypot(b.x - a.x, b.y - a.y) / 5).toInt().coerceAtLeast(2)
            for (s in 0 until steps) {
                val t = s.toDouble() / steps
                out += Point(
                    a.x + (b.x - a.x) * t + rnd.nextDouble(-jitter, jitter),
                    a.y + (b.y - a.y) * t + rnd.nextDouble(-jitter, jitter),
                )
            }
        }
        // Палец обычно не попадает точно в начальную точку.
        return if (closeGap > 0) out.dropLast((closeGap / 5).toInt()) else out
    }

    /** Все углы между соседними стенами кратны [stepDeg]. */
    private fun assertAnglesMultipleOf(poly: List<Point>, stepDeg: Double) {
        for (i in poly.indices) {
            val a = poly[i]
            val b = poly[(i + 1) % poly.size]
            val deg = Math.toDegrees(atan2(b.y - a.y, b.x - a.x))
            val rem = abs(deg / stepDeg - Math.round(deg / stepDeg))
            assertTrue(rem < 1e-6, "wall $i at $deg° is not a multiple of $stepDeg°")
        }
    }

    @Test
    fun `wobbly rectangle becomes a clean axis-aligned rectangle`() {
        val corners = listOf(Point(100.0, 100.0), Point(500.0, 110.0), Point(495.0, 400.0), Point(105.0, 395.0))
        val poly = assertNotNull(Straighten.polygon(handDrawn(corners, jitter = 6.0, closeGap = 20.0)))
        assertEquals(4, poly.size)
        assertAnglesMultipleOf(poly, 90.0)
        // Левая верхняя вершина первая, стена 1 идёт вправо.
        assertTrue(abs(poly[0].x) < 1e-6 && abs(poly[0].y) < 1e-6)
        assertTrue(poly[1].x > 300 && abs(poly[1].y) < 1e-6)
    }

    @Test
    fun `rotated drawing keeps its rotation but gets right angles`() {
        val c = Point(300.0, 300.0)
        val angle = 30 * PI / 180
        val corners = listOf(Point(-200.0, -120.0), Point(200.0, -120.0), Point(200.0, 120.0), Point(-200.0, 120.0)).map {
            Point(c.x + it.x * cos(angle) - it.y * sin(angle), c.y + it.x * sin(angle) + it.y * cos(angle))
        }
        val poly = assertNotNull(Straighten.polygon(handDrawn(corners, jitter = 4.0)))
        assertEquals(4, poly.size)
        for (i in poly.indices) {
            val p = poly[(i + 3) % 4]
            val a = poly[i]
            val n = poly[(i + 1) % 4]
            val dot = (a.x - p.x) * (n.x - a.x) + (a.y - p.y) * (n.y - a.y)
            assertTrue(abs(dot) < 1e-6, "corner $i is not 90°")
        }
    }

    @Test
    fun `L-shaped room keeps six walls`() {
        val corners = listOf(
            Point(0.0, 0.0), Point(500.0, 0.0), Point(500.0, 400.0),
            Point(200.0, 400.0), Point(200.0, 250.0), Point(0.0, 250.0),
        )
        val poly = assertNotNull(Straighten.polygon(handDrawn(corners, jitter = 5.0, seed = 7)))
        assertEquals(6, poly.size)
        assertAnglesMultipleOf(poly, 90.0)
    }

    @Test
    fun `clearly diagonal wall snaps to 45 degrees`() {
        val corners = listOf(Point(0.0, 0.0), Point(300.0, 0.0), Point(400.0, 100.0), Point(400.0, 300.0), Point(0.0, 300.0))
        val poly = assertNotNull(Straighten.polygon(handDrawn(corners, jitter = 3.0, seed = 3)))
        assertEquals(5, poly.size)
        assertAnglesMultipleOf(poly, 45.0)
    }

    @Test
    fun `scribble that is too small or too short is rejected`() {
        assertNull(Straighten.polygon(listOf(Point(0.0, 0.0), Point(1.0, 1.0))))
        assertNull(Straighten.polygon(List(10) { Point(5.0, 5.0) }))
    }

    @Test
    fun `result is clockwise`() {
        // Рисуем против часовой стрелки — результат всё равно по часовой.
        val corners = listOf(Point(0.0, 0.0), Point(0.0, 300.0), Point(400.0, 300.0), Point(400.0, 0.0))
        val poly = assertNotNull(Straighten.polygon(handDrawn(corners, jitter = 2.0)))
        assertTrue(Straighten.signedArea(poly) > 0)
    }
}
