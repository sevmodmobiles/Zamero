package app.zamero.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ElevationTest {
    private fun m(v: Double) = Measure(v, Source.LASER)

    private val duct = EngElement("d", EngType.DUCT_RECT, widthMm = 400, heightMm = 200, elevationMm = 2700)
        .withRoute(listOf(Point(0.0, 0.0), Point(100.0, 0.0), Point(100.0, 100.0), Point(200.0, 100.0)))
        .copy(segments = listOf(m(2.0), m(1.5), m(1.0)))

    private fun room() = Room("r", "Зал", Outlines.RECTANGLE, walls = listOf(m(4.0), m(3.0), m(4.0), m(3.0)), engineering = listOf(duct))

    @Test
    fun `common height and per-segment drops`() {
        val r = Edit.setSegmentElevation(room(), "d", 1, 2400)
        val e = r.engineering[0]
        assertEquals(2700, e.elevationOf(0))
        assertEquals(2400, e.elevationOf(1))
        assertEquals(2700, e.elevationOf(2))
        assertEquals(0.6, e.risersLength, 1e-9) // вниз 300 и вверх 300
        assertEquals(4.5 + 0.6, e.fullLength!!, 1e-9)
        assertTrue(e.label.endsWith("(с перепадами)"))
        // Сброс — снова как у всей трассы.
        assertEquals(0.0, Edit.setSegmentElevation(r, "d", 1, null).engineering[0].risersLength, 1e-9)
    }

    @Test
    fun `splitting and merging keep segment heights`() {
        val r = Edit.setSegmentElevation(room(), "d", 2, 2400)
        val split = Edit.deleteSegment(r, "d", 0, "d2")
        assertEquals(listOf(null, 2400), split.engineering[0].segmentElevationsMm)
        val merged = Edit.deletePoint(r, PointRef.RoutePoint("d", 2))
        assertEquals(2, merged.engineering[0].segments.size)
        assertEquals(2, merged.engineering[0].segmentElevationsMm.size)
    }

    @Test
    fun `drops appear in report and dxf`() {
        val r = Edit.setSegmentElevation(room(), "d", 1, 2400)
        val p = Project("p", "Адрес", rooms = listOf(r))
        assertTrue("высоты участков от пола: 2700 → 2400 → 2700 мм" in Report.text(p))
        val dxf = Dxf.build(p).split("\r\n")
        assertTrue("h=2400" in dxf)
        assertTrue("перепад -300" in dxf && "перепад 300" in dxf)
    }
}
