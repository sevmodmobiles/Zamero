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
    fun scale(room: Room): Double {
        val n = room.outline.size
        return scaleOf(room, List(n) { i -> dist(room.outline[i], room.outline[(i + 1) % n]) })
    }

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

    /**
     * Переводит точку эскиза в метры плана. Опора — первая вершина контура,
     * масштаб по осям — отношение габаритов плана и эскиза. Так при правке
     * отдельной вершины остальные точки остаются на месте.
     */
    fun toPlan(room: Room, plan: RoomPlan, p: Point): Point {
        val (kx, ky) = axisScale(room.outline, plan.vertices)
        val o = room.outline.first()
        val v = plan.vertices.first()
        return Point(v.x + (p.x - o.x) * kx, v.y + (p.y - o.y) * ky)
    }

    /** Обратное преобразование: точка плана (м) → координаты эскиза. */
    fun toSketch(room: Room, plan: RoomPlan, p: Point): Point {
        val (kx, ky) = axisScale(room.outline, plan.vertices)
        val o = room.outline.first()
        val v = plan.vertices.first()
        return Point(o.x + (p.x - v.x) / kx, o.y + (p.y - v.y) / ky)
    }

    private fun axisScale(sketch: List<Point>, plan: List<Point>): Pair<Double, Double> {
        val sw = sketch.maxOf { it.x } - sketch.minOf { it.x }
        val sh = sketch.maxOf { it.y } - sketch.minOf { it.y }
        val pw = plan.maxOf { it.x } - plan.minOf { it.x }
        val ph = plan.maxOf { it.y } - plan.minOf { it.y }
        val kx = if (sw > 1e-9) pw / sw else 1.0
        val ky = if (sh > 1e-9) ph / sh else kx
        return kx to ky
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
