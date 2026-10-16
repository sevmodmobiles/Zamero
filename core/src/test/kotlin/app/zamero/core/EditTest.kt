package app.zamero.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EditTest {
    private fun m(v: Double) = Measure(v, Source.LASER)

    private fun assertNear(e: Point, a: Point, eps: Double = 1e-6) {
        assertEquals(e.x, a.x, eps)
        assertEquals(e.y, a.y, eps)
    }

    /** Комната 4×3 м, все стены измерены; перегородка от угла (4,0) к (2,0)…(2,3). */
    private fun room(): Room {
        val partition = EngElement("p", EngType.PARTITION)
            .withRoute(listOf(Point(400.0, 0.0), Point(200.0, 0.0), Point(200.0, 300.0)))
            .copy(segments = listOf(m(2.0), m(3.0)))
        return Room(
            "r", "Зал", Outlines.RECTANGLE,
            walls = listOf(m(4.0), m(3.0), m(4.0), m(3.0)),
            engineering = listOf(partition),
        )
    }

    private fun pos(room: Room, ref: PointRef) = Edit.points(room).first { it.first == ref }.second

    @Test
    fun `rebase keeps everything where it is on the plan`() {
        val r = room()
        val before = Edit.points(r).toMap()
        val after = Edit.points(Edit.rebase(r)).toMap()
        before.forEach { (ref, p) -> assertNear(p, after.getValue(ref)) }
    }

    @Test
    fun `dragging a corner stretches both walls and drags attached lines`() {
        // Тянем угол (4,0) вправо на 1 м: перегородка, начинавшаяся в этом углу, едет следом.
        val r = Edit.movePoint(room(), PointRef.Vertex(1), Point(5.0, 0.0), snap = 0.05)
        assertNear(Point(5.0, 0.0), pos(r, PointRef.Vertex(1)))
        assertNear(Point(5.0, 0.0), pos(r, PointRef.RoutePoint("p", 0)))
        // Остальные точки на месте.
        assertNear(Point(0.0, 0.0), pos(r, PointRef.Vertex(0)))
        assertNear(Point(4.0, 3.0), pos(r, PointRef.Vertex(2)))
        assertNear(Point(2.0, 3.0), pos(r, PointRef.RoutePoint("p", 2)))
        // Длины изменившихся стен сброшены, остальные сохранены.
        assertNull(r.walls[0])
        assertNull(r.walls[1])
        assertEquals(4.0, r.walls[2]!!.meters)
        assertNull(r.engineering[0].segments[0])
        assertNotNull(r.engineering[0].segments[1])
    }

    @Test
    fun `dragged point snaps to another point and aligns to axes`() {
        // Почти в угол (0,3) — прилипает к нему.
        val r1 = Edit.movePoint(room(), PointRef.RoutePoint("p", 2), Point(0.03, 2.98), snap = 0.1)
        assertNear(Point(0.0, 3.0), pos(r1, PointRef.RoutePoint("p", 2)))
        // Чуть мимо вертикали соседа — выравнивается.
        val r2 = Edit.movePoint(room(), PointRef.RoutePoint("p", 2), Point(2.06, 2.0), snap = 0.1)
        assertNear(Point(2.0, 2.0), pos(r2, PointRef.RoutePoint("p", 2)))
    }

    @Test
    fun `deleting a vertex merges two walls`() {
        val r = Edit.deletePoint(Edit.rebase(room().withOutline(Outlines.L_SHAPE)), PointRef.Vertex(3))
        assertEquals(5, r.outline.size)
        assertEquals(5, r.walls.size)
        assertEquals(3, Edit.deletePoint(Edit.rebase(room()), PointRef.Vertex(0)).outline.size)
        assertEquals(3, Edit.deletePoint(Edit.deletePoint(room(), PointRef.Vertex(0)), PointRef.Vertex(0)).outline.size)
    }

    @Test
    fun `deleting route points shortens and finally removes the line`() {
        var r = Edit.deletePoint(room(), PointRef.RoutePoint("p", 1))
        assertEquals(2, r.engineering[0].route.size)
        assertEquals(listOf<Measure?>(null), r.engineering[0].segments)
        r = Edit.deletePoint(r, PointRef.RoutePoint("p", 0))
        assertEquals(0, r.engineering.size)
    }

    @Test
    fun `erasing segments shortens or splits the line`() {
        val line = EngElement("t", EngType.TRAY, widthMm = 200, heightMm = 60)
            .withRoute(listOf(Point(0.0, 0.0), Point(100.0, 0.0), Point(100.0, 100.0), Point(200.0, 100.0)))
            .copy(segments = listOf(m(1.0), m(1.0), m(1.0)))
        val r = room().copy(engineering = listOf(line))
        // Средний участок — две линии с теми же свойствами.
        val split = Edit.deleteSegment(r, "t", 1, "t2")
        assertEquals(listOf("t", "t2"), split.engineering.map { it.id })
        assertEquals(2, split.engineering[0].route.size)
        assertEquals(2, split.engineering[1].route.size)
        assertEquals(200, split.engineering[1].widthMm)
        // Крайний — укорачивается.
        val shorter = Edit.deleteSegment(r, "t", 0, "x")
        assertEquals(listOf("t"), shorter.engineering.map { it.id })
        assertEquals(3, shorter.engineering[0].route.size)
        // Единственный — линия исчезает.
        assertEquals(0, Edit.deleteSegment(Edit.deleteSegment(split, "t", 0, "y"), "t2", 0, "z").engineering.size)
    }

    @Test
    fun `lasso deletes several segments at once`() {
        val line = EngElement("t", EngType.TRAY, widthMm = 200, heightMm = 60)
            .withRoute(List(6) { Point(it * 100.0, 0.0) }) // 5 участков
        val other = EngElement("o", EngType.PIPE, diameterMm = 50).withRoute(listOf(Point(0.0, 50.0), Point(100.0, 50.0)))
        val r = room().copy(engineering = listOf(line, other))
        var n = 0
        val out = Edit.deleteSegments(r, setOf("t" to 1, "t" to 3, "o" to 0)) { "new${n++}" }
        // Остались участки 0, 2 и 4 линии «t» — три отдельные линии; труба удалена целиком.
        assertEquals(listOf("t", "new0", "new1"), out.engineering.map { it.id })
        assertEquals(listOf(2, 2, 2), out.engineering.map { it.route.size })
        assertEquals(Point(200.0, 0.0), out.engineering[1].route[0])
    }

    @Test
    fun `erased contour walls leave a gap`() {
        val r = Edit.deleteWalls(room(), setOf(1))
        assertEquals(setOf(1), r.removedWalls)
        assertEquals(4, r.outline.size) // соседние стены не тронуты
        assertNull(Geometry.stats(r).floorArea) // контур не замкнут — площади нет
        assertEquals(listOf(MeasureTarget.Wall(0), MeasureTarget.Wall(2), MeasureTarget.Wall(3)), MeasureFlow.targets(r).take(3))
        val dxf = Dxf.build(Project("p", "А", rooms = listOf(r))).split("\r\n")
        // Стены — отдельными линиями (3 уцелевшие) + перегородка из room().
        assertEquals(3 + 1, dxf.count { it == "POLYLINE" })
        assertTrue("Контур не замкнут (стёрты стены 2)" in Report.text(Project("p", "А", rooms = listOf(r))))
    }
}
