package app.zamero.core

import kotlin.math.abs
import kotlin.math.hypot

/** Результаты расчёта по помещению. Null — данных пока не хватает. */
data class RoomStats(
    val floorArea: Double?,
    val perimeter: Double?,
    /** Насколько контур не сходится, м. Большое значение — вероятная ошибка замера. */
    val closureError: Double?,
    val wallAreaGross: Double?,
    val openingsArea: Double,
    val wallAreaNet: Double?,
    val volume: Double?,
) {
    val needsRecheck: Boolean get() = (closureError ?: 0.0) > CLOSURE_TOLERANCE_M

    companion object {
        /** Допустимое расхождение контура: 3 см. */
        const val CLOSURE_TOLERANCE_M = 0.03
    }
}

object Geometry {

    fun stats(room: Room): RoomStats {
        val lengths = room.walls.map { it?.meters }
        // Площадь и периметр — только у замкнутого контура со всеми замерами.
        val complete = room.hasOutline && room.isClosed && lengths.all { it != null }
        val perimeter = if (complete) lengths.sumOf { it!! } else null

        var floorArea: Double? = null
        var closure: Double? = null
        if (complete) {
            val polygon = measuredPolygon(room.outline, lengths.map { it!! })
            floorArea = polygon.area
            closure = polygon.closureError
        }

        val height = room.height?.meters
        val openingsArea = room.openings.sumOf { o -> (o.width?.meters ?: 0.0) * (o.height?.meters ?: 0.0) }
        val gross = if (perimeter != null && height != null) perimeter * height else null
        return RoomStats(
            floorArea = floorArea,
            perimeter = perimeter,
            closureError = closure,
            wallAreaGross = gross,
            openingsArea = openingsArea,
            wallAreaNet = gross?.let { (it - openingsArea).coerceAtLeast(0.0) },
            volume = if (floorArea != null && height != null) floorArea * height else null,
        )
    }

    /** Замкнутый контур в метрах: вершины, площадь и исходная невязка. */
    data class Polygon(val vertices: List<Point>, val area: Double, val closureError: Double)

    /**
     * Контур из направлений стен (берутся с эскиза) и измеренных длин.
     * Реальные замеры почти никогда не сходятся идеально, поэтому невязку по каждой оси
     * распределяем между стенами пропорционально их проекции на эту ось
     * (для прямоугольника это даёт среднее противоположных стен), а затем считаем
     * площадь по формуле Гаусса.
     */
    fun measuredPolygon(outline: List<Point>, lengths: List<Double>): Polygon {
        require(outline.size == lengths.size && outline.size >= 3)
        val n = outline.size
        val dx = DoubleArray(n)
        val dy = DoubleArray(n)
        for (i in 0 until n) {
            val a = outline[i]
            val b = outline[(i + 1) % n]
            val len = hypot(b.x - a.x, b.y - a.y).takeIf { it > 0 } ?: 1.0
            dx[i] = lengths[i] * (b.x - a.x) / len
            dy[i] = lengths[i] * (b.y - a.y) / len
        }
        val gapX = dx.sum()
        val gapY = dy.sum()
        val closure = hypot(gapX, gapY)
        distribute(dx, gapX)
        distribute(dy, gapY)

        val vertices = ArrayList<Point>(n)
        var x = 0.0
        var y = 0.0
        for (i in 0 until n) {
            vertices.add(Point(x, y))
            x += dx[i]
            y += dy[i]
        }
        return Polygon(vertices, abs(Straighten.signedArea(vertices)), closure)
    }

    /** Контур для показа: по замерам, если все стены измерены, иначе эскиз. */
    fun display(room: Room): List<Point> {
        if (!room.hasOutline) return emptyList()
        val lengths = room.walls.map { it?.meters }
        return if (lengths.all { it != null && it > 0 }) {
            measuredPolygon(room.outline, lengths.map { it!! }).vertices
        } else {
            room.outline
        }
    }

    private fun distribute(deltas: DoubleArray, gap: Double) {
        val total = deltas.sumOf { abs(it) }
        if (total == 0.0) return
        for (i in deltas.indices) deltas[i] -= gap * abs(deltas[i]) / total
    }
}
