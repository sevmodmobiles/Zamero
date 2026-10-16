package app.zamero.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MeasureFlowTest {
    private fun laser(v: Double) = Measure(v, Source.LASER)

    @Test
    fun `readings fill fields in order and advance automatically`() {
        var room = Room("r", "Кухня", openings = listOf(Opening(OpeningType.WINDOW))).withOutline(Outlines.RECTANGLE)
        var target = MeasureFlow.nextEmpty(room)
        val readings = listOf(3.1, 2.8, 3.1, 2.8, 2.7, 1.5, 1.4)
        for (v in readings) {
            val (r, next) = MeasureFlow.record(room, target!!, laser(v))
            room = r
            target = next
        }
        assertNull(target)
        assertEquals(listOf(3.1, 2.8, 3.1, 2.8), room.walls.map { it!!.meters })
        assertEquals(2.7, room.height!!.meters)
        assertEquals(1.5, room.openings[0].width!!.meters)
        assertEquals(1.4, room.openings[0].height!!.meters)
    }

    @Test
    fun `re-measuring a field jumps to the next empty one`() {
        var room = Room("r", "Кухня").withOutline(Outlines.RECTANGLE)
        room = MeasureFlow.set(room, MeasureTarget.Wall(0), laser(3.0))
        room = MeasureFlow.set(room, MeasureTarget.Wall(1), laser(2.0))
        val (_, next) = MeasureFlow.record(room, MeasureTarget.Wall(0), laser(3.05))
        assertEquals(MeasureTarget.Wall(2), next)
    }

    @Test
    fun `redrawing with the same wall count keeps measurements`() {
        val room = MeasureFlow.set(Room("r", "Зал").withOutline(Outlines.RECTANGLE), MeasureTarget.Wall(0), laser(5.0))
        assertEquals(5.0, room.withOutline(Outlines.RECTANGLE.reversed()).walls[0]!!.meters)
        assertEquals(List(6) { null }, room.withOutline(Outlines.L_SHAPE).walls)
    }

    @Test
    fun `labels number openings by type`() {
        val room = Room("r", "Зал", openings = listOf(Opening(OpeningType.WINDOW), Opening(OpeningType.DOOR), Opening(OpeningType.WINDOW)))
        assertEquals("Окно 2: ширина", MeasureFlow.label(room, MeasureTarget.OpeningWidth(2)))
        assertEquals("Дверь 1: высота", MeasureFlow.label(room, MeasureTarget.OpeningHeight(1)))
    }
}
