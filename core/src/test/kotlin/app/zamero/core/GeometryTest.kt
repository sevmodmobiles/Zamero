package app.zamero.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GeometryTest {
    private fun m(v: Double) = Measure(v, Source.MANUAL)

    private fun room(shape: Shape, vararg walls: Double, height: Double? = null, openings: List<Opening> = emptyList()) =
        Room("r", "Комната", shape, walls.map { m(it) }, height?.let(::m), openings)

    @Test
    fun `rectangle area perimeter and volume`() {
        val s = Geometry.stats(room(Shape.RECTANGLE, 4.0, 3.0, 4.0, 3.0, height = 2.5))
        assertEquals(12.0, s.floorArea!!, 1e-9)
        assertEquals(14.0, s.perimeter!!, 1e-9)
        assertEquals(30.0, s.volume!!, 1e-9)
        assertEquals(35.0, s.wallAreaGross!!, 1e-9)
        assertEquals(0.0, s.closureError!!, 1e-9)
        assertFalse(s.needsRecheck)
    }

    @Test
    fun `small mismatch of opposite walls is averaged`() {
        val s = Geometry.stats(room(Shape.RECTANGLE, 4.0, 3.0, 4.02, 3.0))
        assertEquals(4.01 * 3.0, s.floorArea!!, 1e-3)
        assertEquals(0.02, s.closureError!!, 1e-9)
        assertFalse(s.needsRecheck)
    }

    @Test
    fun `large mismatch asks for recheck`() {
        assertTrue(Geometry.stats(room(Shape.RECTANGLE, 4.0, 3.0, 4.2, 3.0)).needsRecheck)
    }

    @Test
    fun `L-shaped room area`() {
        // 5×4 без угла 2×1.5: стены 5, 4, 3, 1.5, 2, 2.5
        val s = Geometry.stats(room(Shape.L_SHAPE, 5.0, 4.0, 3.0, 1.5, 2.0, 2.5))
        assertEquals(5.0 * 4.0 - 2.0 * 1.5, s.floorArea!!, 1e-9)
        assertEquals(0.0, s.closureError!!, 1e-9)
    }

    @Test
    fun `openings are subtracted from wall area`() {
        val window = Opening(OpeningType.WINDOW, m(1.5), m(1.4))
        val door = Opening(OpeningType.DOOR, m(0.9), m(2.0))
        val s = Geometry.stats(room(Shape.RECTANGLE, 4.0, 3.0, 4.0, 3.0, height = 2.5, openings = listOf(window, door)))
        assertEquals(1.5 * 1.4 + 0.9 * 2.0, s.openingsArea, 1e-9)
        assertEquals(35.0 - s.openingsArea, s.wallAreaNet!!, 1e-9)
    }

    @Test
    fun `incomplete room has no area`() {
        val r = Room("r", "Комната").let { MeasureFlow.set(it, MeasureTarget.Wall(0), m(4.0)) }
        val s = Geometry.stats(r)
        assertNull(s.floorArea)
        assertNull(s.perimeter)
    }

    @Test
    fun `sketch uses template until all walls are measured`() {
        Shape.entries.forEach { shape ->
            val sketch = Geometry.sketch(Room("r", "Комната", shape))
            assertEquals(shape.wallCount, sketch.vertices.size)
            assertEquals(0.0, sketch.closureError, 1e-9)
        }
        val measured = Geometry.sketch(room(Shape.RECTANGLE, 2.0, 1.0, 2.0, 1.0))
        assertEquals(listOf(0.0 to 0.0, 2.0 to 0.0, 2.0 to -1.0, 0.0 to -1.0), measured.vertices)
    }
}
