package app.zamero.core

import kotlin.test.Test
import kotlin.test.assertEquals

class SnapTest {
    private fun assertNear(expected: Point, actual: Point) {
        assertEquals(expected.x, actual.x, 1e-9)
        assertEquals(expected.y, actual.y, 1e-9)
    }

    private val room = listOf(
        Point(0.0, 0.0) to Point(4.0, 0.0),
        Point(4.0, 0.0) to Point(4.0, 3.0),
        Point(4.0, 3.0) to Point(0.0, 3.0),
        Point(0.0, 3.0) to Point(0.0, 0.0),
    )

    @Test
    fun `partition ends stick to walls`() {
        // Перегородка от верхней стены к нижней, не доведённая на 10 и 15 см.
        val drawn = listOf(Point(2.0, 0.1), Point(2.0, 2.85))
        val snapped = Snap.extendToWalls(drawn, room, tolerance = 0.3)
        assertNear(Point(2.0, 0.0), snapped[0])
        assertNear(Point(2.0, 3.0), snapped[1])
    }

    @Test
    fun `overshoot is trimmed back to the wall`() {
        val drawn = listOf(Point(1.0, 1.5), Point(4.2, 1.5))
        val snapped = Snap.extendToWalls(drawn, room, tolerance = 0.3)
        assertEquals(4.0, snapped[1].x, 1e-9)
        assertEquals(1.5, snapped[1].y, 1e-9)
        assertNear(Point(1.0, 1.5), snapped[0]) // до левой стены 1 м — не трогаем
    }

    @Test
    fun `l-shaped route keeps its corner`() {
        val drawn = listOf(Point(0.2, 1.0), Point(2.0, 1.0), Point(2.0, 2.8))
        val snapped = Snap.extendToWalls(drawn, room, tolerance = 0.3)
        assertNear(Point(0.0, 1.0), snapped[0])
        assertNear(Point(2.0, 1.0), snapped[1])
        assertNear(Point(2.0, 3.0), snapped[2])
    }

    @Test
    fun `construction types have no section but walls have thickness`() {
        val p = EngElement("x", EngType.PARTITION, widthMm = 100)
        assertEquals("Перегородка 100 мм", p.label)
        assertEquals("Линия", EngElement("y", EngType.LINE).label)
    }

    @Test
    fun `line ends stick to existing points`() {
        val drawn = listOf(Point(0.05, 0.04), Point(2.0, 0.0), Point(2.0, 1.0))
        val out = Snap.endsToPoints(drawn, listOf(Point(0.0, 0.0), Point(4.0, 3.0)), tolerance = 0.1)
        assertNear(Point(0.0, 0.0), out[0])
        assertNear(Point(2.0, 1.0), out[2])
    }
}
