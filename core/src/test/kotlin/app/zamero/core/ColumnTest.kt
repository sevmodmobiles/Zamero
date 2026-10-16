package app.zamero.core

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ColumnTest {
    private fun m(v: Double) = Measure(v, Source.LASER)

    private fun drawnSquare(cx: Double, cy: Double, half: Double, seed: Int = 1): List<Point> {
        val rnd = Random(seed)
        val corners = listOf(Point(cx - half, cy - half), Point(cx + half, cy - half), Point(cx + half, cy + half), Point(cx - half, cy + half))
        val out = mutableListOf<Point>()
        for (i in 0 until 4) {
            val a = corners[i]
            val b = corners[(i + 1) % 4]
            for (s in 0 until 15) {
                val t = s / 15.0
                out += Point(a.x + (b.x - a.x) * t + rnd.nextDouble(-0.5, 0.5), a.y + (b.y - a.y) * t + rnd.nextDouble(-0.5, 0.5))
            }
        }
        return out
    }

    private fun drawnCircle(cx: Double, cy: Double, r: Double, seed: Int = 1): List<Point> {
        val rnd = Random(seed)
        return List(60) { i ->
            val a = 2 * PI * i / 60
            Point(cx + r * cos(a) + rnd.nextDouble(-0.5, 0.5), cy + r * sin(a) + rnd.nextDouble(-0.5, 0.5))
        }
    }

    @Test
    fun `square and circle are told apart`() {
        val sq = assertNotNull(Columns.detect(drawnSquare(100.0, 100.0, 20.0), 0.0))
        assertFalse(sq.round)
        assertEquals(100.0, sq.center.x, 1.0)
        assertEquals(40.0, sq.width, 2.0)
        val ci = assertNotNull(Columns.detect(drawnCircle(50.0, 60.0, 15.0), 0.0))
        assertTrue(ci.round)
        assertEquals(30.0, ci.width, 2.0)
    }

    @Test
    fun `open line is not a column`() {
        val line = List(20) { Point(it * 5.0, 0.0) }
        assertNull(Columns.detect(line, 0.0))
    }

    private fun room(): Room {
        val col = Column("c1", round = false, center = Point(100.0, 100.0), approxWidth = 0.4, approxDepth = 0.4)
        return Room("r", "Зал", Outlines.RECTANGLE, walls = listOf(m(4.0), m(3.0), m(4.0), m(3.0)), columns = listOf(col))
    }

    @Test
    fun `reference walls are the nearest perpendicular ones`() {
        val r = room()
        val plan = assertNotNull(Plan.room(r))
        val center = Plan.toPlan(r, plan, r.columns[0].center) // (1.0, 1.0) м
        val (u, v) = Columns.referenceWalls(plan, center)
        assertEquals(3, u) // левая стена (стена 4)
        assertEquals(0, v) // верхняя стена (стена 1)
    }

    @Test
    fun `measured offsets move the column`() {
        var r = room()
        val plan = assertNotNull(Plan.room(r))
        r = r.copy(columns = r.columns.map { it.copy(refWallU = 3, refWallV = 0) })
        r = MeasureFlow.set(r, MeasureTarget.ColumnValue("c1", ColumnDim.OFFSET_U), m(1.5))
        r = MeasureFlow.set(r, MeasureTarget.ColumnValue("c1", ColumnDim.OFFSET_V), m(0.8))
        r = MeasureFlow.set(r, MeasureTarget.ColumnValue("c1", ColumnDim.WIDTH), m(0.5))
        r = MeasureFlow.set(r, MeasureTarget.ColumnValue("c1", ColumnDim.DEPTH), m(0.3))
        val cp = Columns.plan(r, plan, r.columns[0])
        assertEquals(1.5, cp.center.x, 1e-9)
        assertEquals(0.8, cp.center.y, 1e-9)
        assertEquals(0.0, cp.footU!!.x, 1e-9)
        assertEquals(0.8, cp.footU!!.y, 1e-9)
        assertEquals("500×300", r.columns[0].sizeText)
        assertEquals("Колонна 1: ось ↔ стена 4", MeasureFlow.label(r, MeasureTarget.ColumnValue("c1", ColumnDim.OFFSET_U)))
    }

    @Test
    fun `columns are exported to dxf`() {
        val r = room().let { it.copy(columns = it.columns.map { c -> c.copy(refWallU = 3, refWallV = 0) } + Column("c2", true, Point(300.0, 200.0), 0.3, 0.3, width = m(0.4))) }
        val dxf = Dxf.build(Project("p", "Адрес", rooms = listOf(r)))
        val lines = dxf.split("\r\n")
        assertEquals(1, lines.count { it == "CIRCLE" })
        assertTrue("COLUMNS" in lines && "AXES" in lines)
        assertTrue("К2 %%c400" in lines)
        assertTrue('?' !in String(Dxf.export(Project("p", "Адрес", rooms = listOf(r))), Dxf.CHARSET))
    }
}
