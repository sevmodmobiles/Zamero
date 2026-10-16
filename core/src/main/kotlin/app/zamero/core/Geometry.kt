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
        val complete = lengths.all { it != null }
        val perimeter = if (complete) lengths.sumOf { it!! } else null

        var floorArea: Double? = null
        var closure: Double? = null
        if (complete) {
            val polygon = closedPolygon(lengths.map { it!! }, room.shape.turns)
            floorArea = polygon.area
            closure = polygon.closureError
        }

        val height = room.height?.meters
        val openingsArea = room.openings.sumOf { o ->
            val w = o.width?.meters ?: 0.0
            val h = o.height?.meters ?: 0.0
            w * h
        }
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

    /** Замкнутый контур: вершины (x, y) в метрах, площадь и исходная невязка. */
    data class Polygon(val vertices: List<Pair<Double, Double>>, val area: Double, val closureError: Double)

    /** Типичные пропорции формы — для эскиза, пока не все стены измерены. */
    fun templateLengths(shape: Shape): List<Double> = when (shape) {
        Shape.RECTANGLE -> listOf(4.0, 3.0, 4.0, 3.0)
        Shape.L_SHAPE -> listOf(5.0, 4.0, 3.0, 1.5, 2.0, 2.5)
    }

    /** Контур для эскиза: измеренные стены, если все есть, иначе шаблон. */
    fun sketch(room: Room): Polygon {
        val measured = room.walls.map { it?.meters }
        val lengths = if (measured.all { it != null && it > 0 }) measured.map { it!! } else templateLengths(room.shape)
        return closedPolygon(lengths, room.shape.turns)
    }

    /**
     * Строит контур из длин стен и поворотов на 90°. Реальные замеры почти никогда
     * не сходятся идеально, поэтому невязку по каждой оси распределяем между стенами
     * этой оси пропорционально их длине (для прямоугольника это даёт среднее
     * противоположных стен), а затем считаем площадь по формуле Гаусса.
     */
    fun closedPolygon(lengths: List<Double>, turns: List<Turn>): Polygon {
        require(lengths.size == turns.size)
        // Направления: 0 — +x, 1 — -y, 2 — -x, 3 — +y (обход по часовой стрелке).
        val dirs = IntArray(lengths.size)
        var dir = 0
        for (i in lengths.indices) {
            dirs[i] = dir
            dir = (dir + if (turns[i] == Turn.RIGHT) 1 else 3) % 4
        }
        val dx = DoubleArray(lengths.size) { i -> lengths[i] * DX[dirs[i]] }
        val dy = DoubleArray(lengths.size) { i -> lengths[i] * DY[dirs[i]] }

        val gapX = dx.sum()
        val gapY = dy.sum()
        val closure = hypot(gapX, gapY)

        distribute(dx, gapX)
        distribute(dy, gapY)

        val vertices = ArrayList<Pair<Double, Double>>(lengths.size)
        var x = 0.0
        var y = 0.0
        var twiceArea = 0.0
        for (i in lengths.indices) {
            vertices.add(x to y)
            val nx = x + dx[i]
            val ny = y + dy[i]
            twiceArea += x * ny - nx * y
            x = nx
            y = ny
        }
        return Polygon(vertices = vertices, area = abs(twiceArea) / 2, closureError = closure)
    }

    private fun distribute(deltas: DoubleArray, gap: Double) {
        val total = deltas.sumOf { abs(it) }
        if (total == 0.0) return
        for (i in deltas.indices) deltas[i] -= gap * abs(deltas[i]) / total
    }

    private val DX = intArrayOf(1, 0, -1, 0)
    private val DY = intArrayOf(0, -1, 0, 1)
}
