package app.zamero.core

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Колонна. Оси колонны параллельны стенам помещения: ось U — вдоль первой стены
 * («горизонталь» плана), ось V — поперёк. Положение задаётся привязками —
 * расстояниями от осей колонны до выбранных стен.
 */
data class Column(
    val id: String,
    val round: Boolean,
    /** Центр по эскизу (координаты эскиза помещения). */
    val center: Point,
    /** Размеры по рисунку, м — пока не измерены. */
    val approxWidth: Double,
    val approxDepth: Double,
    /** Ширина вдоль U (для круглой — диаметр). */
    val width: Measure? = null,
    /** Глубина вдоль V (для круглой не используется). */
    val depth: Measure? = null,
    /** Расстояние от стены [refWallU] до вертикальной оси колонны (вдоль U). */
    val offsetU: Measure? = null,
    /** Расстояние от стены [refWallV] до горизонтальной оси колонны (вдоль V). */
    val offsetV: Measure? = null,
    val refWallU: Int = -1,
    val refWallV: Int = -1,
) {
    /** «500×400» или «Ø400» по замерам; null, если размеры ещё не введены. */
    val sizeText: String?
        get() = if (round) {
            width?.let { "Ø${it.millimeters}" }
        } else {
            val w = width ?: return null
            val d = depth ?: return null
            "${w.millimeters}×${d.millimeters}"
        }
}

/** Какое значение колонны меряем. */
enum class ColumnDim { WIDTH, DEPTH, OFFSET_U, OFFSET_V }

/** Колонна на плане в метрах. */
data class ColumnPlan(
    val center: Point,
    val width: Double,
    val depth: Double,
    /** Направление оси U, радианы. */
    val angle: Double,
    val round: Boolean,
    /** Основание перпендикуляра от центра на опорную стену (для размерной линии). */
    val footU: Point?,
    val footV: Point?,
) {
    val u: Point get() = Point(cos(angle), sin(angle))
    val v: Point get() = Point(-sin(angle), cos(angle))

    /** Углы прямоугольной колонны по часовой стрелке. */
    val corners: List<Point>
        get() {
            val hw = width / 2
            val hd = depth / 2
            return listOf(-1.0 to -1.0, 1.0 to -1.0, 1.0 to 1.0, -1.0 to 1.0).map { (a, b) ->
                Point(center.x + u.x * hw * a + v.x * hd * b, center.y + u.y * hw * a + v.y * hd * b)
            }
        }
}

object Columns {

    /** Распознанная по рисунку колонна (в координатах рисунка). */
    data class Detected(val round: Boolean, val center: Point, val width: Double, val depth: Double)

    /**
     * Похожа ли линия на маленькую замкнутую фигуру. Круг от квадрата отличаем
     * по «округлости» выпуклой оболочки: 4πS/P² = 1 у круга и ≈0.785 у квадрата.
     * Размеры — по габаритам вдоль осей U/V (угол [base] — направление первой стены).
     */
    fun detect(stroke: List<Point>, base: Double): Detected? {
        if (stroke.size < 8) return null
        val xs = stroke.map { it.x }
        val ys = stroke.map { it.y }
        val diag = hypot(xs.max() - xs.min(), ys.max() - ys.min())
        if (diag < 1e-9) return null
        if (dist(stroke.first(), stroke.last()) > diag * 0.3) return null

        val hull = convexHull(stroke)
        if (hull.size < 3) return null
        val area = abs(Straighten.signedArea(hull))
        val perimeter = hull.indices.sumOf { dist(hull[it], hull[(it + 1) % hull.size]) }
        val roundness = 4 * PI * area / (perimeter * perimeter)

        val ux = cos(base)
        val uy = sin(base)
        val us = stroke.map { it.x * ux + it.y * uy }
        val vs = stroke.map { -it.x * uy + it.y * ux }
        val w = us.max() - us.min()
        val d = vs.max() - vs.min()
        val cu = (us.max() + us.min()) / 2
        val cv = (vs.max() + vs.min()) / 2
        val center = Point(cu * ux - cv * uy, cu * uy + cv * ux)
        val round = roundness > 0.88
        return if (round) Detected(true, center, (w + d) / 2, (w + d) / 2) else Detected(false, center, w, d)
    }

    /** Направление осей колонн в помещении — вдоль первой стены. */
    fun baseAngle(plan: Plan.RoomPlan): Double {
        val w = plan.walls.first()
        return atan2(w.b.y - w.a.y, w.b.x - w.a.x)
    }

    /**
     * Опорные стены для привязки: для оси U — ближайшая стена, перпендикулярная U
     * (её «видно» из центра по горизонтали), для V — перпендикулярная V.
     */
    fun referenceWalls(plan: Plan.RoomPlan, center: Point): Pair<Int, Int> {
        val angle = baseAngle(plan)
        val u = Point(cos(angle), sin(angle))
        val v = Point(-sin(angle), cos(angle))
        return nearestWall(plan, center, u) to nearestWall(plan, center, v)
    }

    private fun nearestWall(plan: Plan.RoomPlan, c: Point, dir: Point): Int {
        var best = -1
        var bestDist = Double.MAX_VALUE
        for (w in plan.walls) {
            val wd = Point(w.b.x - w.a.x, w.b.y - w.a.y)
            val len = hypot(wd.x, wd.y)
            if (len < 1e-9) continue
            // Стена должна быть перпендикулярна направлению привязки.
            if (abs((wd.x * dir.x + wd.y * dir.y) / len) > 0.2) continue
            val dist = abs((c.x - w.a.x) * dir.x + (c.y - w.a.y) * dir.y)
            // Предпочитаем стены, напротив которых находится центр.
            val along = ((c.x - w.a.x) * wd.x + (c.y - w.a.y) * wd.y) / (len * len)
            val penalty = if (along in 0.0..1.0) 0.0 else 1000.0
            if (dist + penalty < bestDist) {
                bestDist = dist + penalty
                best = w.index
            }
        }
        return best
    }

    /** Колонна на плане: центр сдвигается так, чтобы выполнялись измеренные привязки. */
    fun plan(room: Room, plan: Plan.RoomPlan, col: Column): ColumnPlan {
        val angle = baseAngle(plan)
        val u = Point(cos(angle), sin(angle))
        val v = Point(-sin(angle), cos(angle))
        var c = Plan.toPlan(room, plan, col.center)

        fun adjust(ref: Int, dir: Point, m: Measure?): Point? {
            val w = plan.walls.getOrNull(ref) ?: return null
            val s = (c.x - w.a.x) * dir.x + (c.y - w.a.y) * dir.y
            if (m != null) {
                val target = if (s < 0) -m.meters else m.meters
                c = Point(c.x + dir.x * (target - s), c.y + dir.y * (target - s))
            }
            val s2 = (c.x - w.a.x) * dir.x + (c.y - w.a.y) * dir.y
            return Point(c.x - dir.x * s2, c.y - dir.y * s2)
        }

        val footU = adjust(col.refWallU, u, col.offsetU)
        val footV = adjust(col.refWallV, v, col.offsetV)
        // После сдвига по V основание перпендикуляра на стену U тоже сдвигается.
        val fixedFootU = plan.walls.getOrNull(col.refWallU)?.let { w ->
            val s = (c.x - w.a.x) * u.x + (c.y - w.a.y) * u.y
            Point(c.x - u.x * s, c.y - u.y * s)
        } ?: footU
        val width = col.width?.meters ?: col.approxWidth
        val depth = if (col.round) width else col.depth?.meters ?: col.approxDepth
        return ColumnPlan(c, width, depth, angle, col.round, fixedFootU, footV)
    }

    private fun dist(a: Point, b: Point) = hypot(b.x - a.x, b.y - a.y)

    /** Выпуклая оболочка (алгоритм Эндрю). */
    private fun convexHull(points: List<Point>): List<Point> {
        val pts = points.distinct().sortedWith(compareBy({ it.x }, { it.y }))
        if (pts.size < 3) return pts
        fun cross(o: Point, a: Point, b: Point) = (a.x - o.x) * (b.y - o.y) - (a.y - o.y) * (b.x - o.x)
        val lower = mutableListOf<Point>()
        for (p in pts) {
            while (lower.size >= 2 && cross(lower[lower.size - 2], lower.last(), p) <= 0) lower.removeAt(lower.lastIndex)
            lower += p
        }
        val upper = mutableListOf<Point>()
        for (p in pts.asReversed()) {
            while (upper.size >= 2 && cross(upper[upper.size - 2], upper.last(), p) <= 0) upper.removeAt(upper.lastIndex)
            upper += p
        }
        return lower.dropLast(1) + upper.dropLast(1)
    }
}
