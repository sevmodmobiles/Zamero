package app.zamero.core

import kotlin.math.hypot

/**
 * План помещения в метрах — для отрисовки, инженерии и экспорта в AutoCAD.
 * Неизмеренные стены оцениваются по эскизу в масштабе измеренных (или так,
 * чтобы помещение было ~5 м по длинной стороне, если не измерено ничего).
 */
object Plan {

    data class Wall(val index: Int, val a: Point, val b: Point, val measured: Measure?)

    data class RoomPlan(val vertices: List<Point>, val walls: List<Wall>, val estimated: Boolean)

    fun room(room: Room): RoomPlan? {
        if (!room.hasOutline) return null
        val sketch = room.outline
        val n = sketch.size
        val sketchLen = List(n) { i -> dist(sketch[i], sketch[(i + 1) % n]) }
        val scale = scaleOf(room, sketchLen)
        val lengths = List(n) { i -> room.walls[i]?.meters ?: (sketchLen[i] * scale) }
        val poly = Geometry.measuredPolygon(sketch, lengths)
        val v = poly.vertices
        val walls = List(n) { i -> Wall(i, v[i], v[(i + 1) % n], room.walls[i]) }
        return RoomPlan(v, walls, estimated = room.walls.any { it == null })
    }

    /** Метров в одной единице эскиза. */
    private fun scaleOf(room: Room, sketchLen: List<Double>): Double {
        val ratios = room.walls.indices.mapNotNull { i ->
            val m = room.walls[i]?.meters ?: return@mapNotNull null
            if (sketchLen[i] > 0) m / sketchLen[i] else null
        }.sorted()
        if (ratios.isNotEmpty()) return ratios[ratios.size / 2]
        val xs = room.outline.map { it.x }
        val ys = room.outline.map { it.y }
        val longest = maxOf(xs.max() - xs.min(), ys.max() - ys.min()).coerceAtLeast(1e-6)
        return DEFAULT_SIZE_M / longest
    }

    /** Переводит точку эскиза в метры плана (по габаритам эскиза и плана). */
    fun toPlan(room: Room, plan: RoomPlan, p: Point): Point = Box(room.outline).mapTo(Box(plan.vertices), p)

    /** Обратное преобразование: точка плана (м) → координаты эскиза. */
    fun toSketch(room: Room, plan: RoomPlan, p: Point): Point = Box(plan.vertices).mapTo(Box(room.outline), p)

    private class Box(points: List<Point>) {
        val minX = points.minOf { it.x }
        val maxX = points.maxOf { it.x }
        val minY = points.minOf { it.y }
        val maxY = points.maxOf { it.y }

        fun mapTo(other: Box, p: Point) = Point(
            map(p.x, minX, maxX, other.minX, other.maxX),
            map(p.y, minY, maxY, other.minY, other.maxY),
        )

        private fun map(v: Double, sMin: Double, sMax: Double, pMin: Double, pMax: Double): Double =
            if (sMax - sMin < 1e-9) pMin else pMin + (v - sMin) / (sMax - sMin) * (pMax - pMin)
    }

    /**
     * Трасса инженерной линии в метрах: начало — из эскиза, направления участков —
     * из эскиза, длины — из замеров (неизмеренные — по эскизу).
     */
    fun route(room: Room, plan: RoomPlan, element: EngElement): List<Point> {
        if (!element.hasRoute) return emptyList()
        val mapped = element.route.map { toPlan(room, plan, it) }
        val out = ArrayList<Point>(mapped.size)
        out += mapped.first()
        for (i in 0 until mapped.size - 1) {
            val a = mapped[i]
            val b = mapped[i + 1]
            val len = dist(a, b)
            val target = element.segments[i]?.meters ?: len
            val last = out.last()
            out += if (len < 1e-9) last else Point(last.x + (b.x - a.x) / len * target, last.y + (b.y - a.y) / len * target)
        }
        return out
    }

    fun length(wall: Wall): Double = dist(wall.a, wall.b)

    private fun dist(a: Point, b: Point) = hypot(b.x - a.x, b.y - a.y)

    private const val DEFAULT_SIZE_M = 5.0
}
