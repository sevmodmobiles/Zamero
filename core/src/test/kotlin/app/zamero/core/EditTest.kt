package app.zamero.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

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
}
