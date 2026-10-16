package app.zamero.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FootprintTest {
    private fun assertNear(e: Point, a: Point) {
        assertEquals(e.x, a.x, 1e-9)
        assertEquals(e.y, a.y, 1e-9)
    }

    @Test
    fun `straight run is a rectangle of the given width`() {
        val o = Footprint.outline(listOf(Point(0.0, 0.0), Point(2.0, 0.0)), 0.2)
        assertEquals(4, o.size)
        assertNear(Point(0.0, 0.1), o[0])
        assertNear(Point(2.0, 0.1), o[1])
        assertNear(Point(2.0, -0.1), o[2])
        assertNear(Point(0.0, -0.1), o[3])
    }

    @Test
    fun `corner gets a sharp joint`() {
        val o = Footprint.outline(listOf(Point(0.0, 0.0), Point(2.0, 0.0), Point(2.0, 2.0)), 0.2)
        assertNear(Point(1.9, 0.1), o[1])
        assertNear(Point(2.1, -0.1), o[4])
    }

    @Test
    fun `plan width comes from the section`() {
        assertEquals(0.11, Footprint.width(EngElement("p", EngType.PIPE, diameterMm = 110))!!, 1e-12)
        assertEquals(0.3, Footprint.width(EngElement("t", EngType.TRAY, widthMm = 300, heightMm = 80))!!, 1e-12)
        assertEquals(0.1, Footprint.width(EngElement("w", EngType.PARTITION, widthMm = 100))!!, 1e-12)
        assertNull(Footprint.width(EngElement("l", EngType.LINE)))
        assertNull(Footprint.width(EngElement("d", EngType.DUCT_RECT)))
    }
}
