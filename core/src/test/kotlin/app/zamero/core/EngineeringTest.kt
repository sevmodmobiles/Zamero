package app.zamero.core

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class EngineeringTest {
    private fun m(v: Double) = Measure(v, Source.LASER)

    private fun wobbly(points: List<Point>, jitter: Double, seed: Int = 1): List<Point> {
        val rnd = Random(seed)
        val out = mutableListOf<Point>()
        for (i in 0 until points.size - 1) {
            val a = points[i]
            val b = points[i + 1]
            val steps = (hypot(b.x - a.x, b.y - a.y) / 5).toInt().coerceAtLeast(2)
            for (s in 0 until steps) {
                val t = s.toDouble() / steps
                out += Point(a.x + (b.x - a.x) * t + rnd.nextDouble(-jitter, jitter), a.y + (b.y - a.y) * t + rnd.nextDouble(-jitter, jitter))
            }
        }
        out += points.last()
        return out
    }

    @Test
    fun `hand-drawn route becomes an L with right angle`() {
        val route = assertNotNull(Straighten.polyline(wobbly(listOf(Point(50.0, 50.0), Point(400.0, 60.0), Point(395.0, 300.0)), 4.0)))
        assertEquals(3, route.size)
        val a1 = Math.toDegrees(atan2(route[1].y - route[0].y, route[1].x - route[0].x))
        val a2 = Math.toDegrees(atan2(route[2].y - route[1].y, route[2].x - route[1].x))
        assertEquals(0.0, a1, 1e-6)
        assertEquals(90.0, a2, 1e-6)
    }

    @Test
    fun `straight stroke stays one segment`() {
        val route = assertNotNull(Straighten.polyline(wobbly(listOf(Point(0.0, 0.0), Point(300.0, 8.0)), 3.0)))
        assertEquals(2, route.size)
        assertTrue(abs(route[0].y - route[1].y) < 1e-6)
    }

    @Test
    fun `route aligns to rotated room walls`() {
        val base = Math.toRadians(30.0)
        assertEquals(base, Straighten.snapAngle(Math.toRadians(33.0), base), 1e-9)
        assertEquals(base + Math.PI / 2, Straighten.snapAngle(Math.toRadians(115.0), base), 1e-9)
    }

    private fun roomWithPipe(): Room {
        val pipe = EngElement("p1", EngType.PIPE, diameterMm = 110, elevationMm = 300)
            .withRoute(listOf(Point(0.0, 100.0), Point(200.0, 100.0), Point(200.0, 300.0)))
        return Room(
            "r", "Кухня", Outlines.RECTANGLE,
            walls = listOf(m(4.0), m(3.0), m(4.0), m(3.0)),
            engineering = listOf(pipe),
        )
    }

    @Test
    fun `segments are measured through the common flow`() {
        var room = roomWithPipe()
        assertEquals(MeasureTarget.Height, MeasureFlow.nextEmpty(room))
        room = MeasureFlow.set(room, MeasureTarget.EngSegment("p1", 0), m(2.1))
        assertEquals(2.1, MeasureFlow.valueOf(room, MeasureTarget.EngSegment("p1", 0))!!.meters)
        assertEquals("Труба 1, участок 1", MeasureFlow.label(room, MeasureTarget.EngSegment("p1", 0)))
        assertEquals("Труба Ø110, h=300", room.engineering[0].label)
    }

    @Test
    fun `route in plan meters uses measured lengths`() {
        var room = roomWithPipe()
        room = MeasureFlow.set(room, MeasureTarget.EngSegment("p1", 0), m(2.5))
        val plan = assertNotNull(Plan.room(room))
        val pts = Plan.route(room, plan, room.engineering[0])
        assertEquals(0.0, pts[0].x, 1e-9)
        assertEquals(1.0, pts[0].y, 1e-9) // 100 из 300 по эскизу = 1 м из 3
        assertEquals(2.5, pts[1].x, 1e-9) // измерено
        assertEquals(2.0, hypot(pts[2].x - pts[1].x, pts[2].y - pts[1].y), 1e-9) // по эскизу: 200 из 300 → 2 м
    }

    @Test
    fun `plan estimates unmeasured walls from measured ones`() {
        val room = Room("r", "Зал", Outlines.RECTANGLE).let { MeasureFlow.set(it, MeasureTarget.Wall(0), m(8.0)) }
        val plan = assertNotNull(Plan.room(room))
        assertTrue(plan.estimated)
        assertEquals(6.0, Plan.length(plan.walls[1]), 1e-9) // 300/400 × 8 м
    }

    @Test
    fun `sketch and plan coordinates convert both ways`() {
        val room = roomWithPipe()
        val plan = assertNotNull(Plan.room(room))
        val p = Point(123.0, 45.0)
        val back = Plan.toSketch(room, plan, Plan.toPlan(room, plan, p))
        assertEquals(p.x, back.x, 1e-9)
        assertEquals(p.y, back.y, 1e-9)
    }
}
