package app.zamero.core

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Превращает линию, нарисованную пальцем, в «правильный» многоугольник:
 * 1) упрощает кривую до ломаной (алгоритм Дугласа — Пекера);
 * 2) убирает почти прямые изломы и короткие «дрожания»;
 * 3) доворачивает стены к углам, кратным 90° (или 45°, если стена явно косая),
 *    относительно самой длинной стены;
 * 4) строит вершины как пересечения выровненных стен — контур замыкается сам.
 */
object Straighten {

    /** Изломы меньше этого угла считаем прямой линией. */
    private const val COLLINEAR_DEG = 25.0
    /** Отклонение от прямого угла, которое ещё доворачиваем до 90°. */
    private const val RIGHT_ANGLE_SNAP_DEG = 20.0
    /** Доворот главной оси к горизонтали/вертикали экрана. */
    private const val AXIS_SNAP_DEG = 12.0

    fun polygon(stroke: List<Point>): List<Point>? {
        if (stroke.size < 3) return null
        val diag = diagonal(stroke)
        if (diag < 1e-6) return null

        var pts = simplifyClosed(stroke, epsilon = diag * 0.04)
        pts = removeNoise(pts, minEdge = diag * 0.06)
        if (pts.size < 3) return null

        val edges = snapEdges(pts) ?: return null
        val result = intersect(edges) ?: return null
        return normalize(result)
    }

    // --- 1. Упрощение -------------------------------------------------------

    private fun simplifyClosed(stroke: List<Point>, epsilon: Double): List<Point> {
        val closed = stroke + stroke.first()
        val kept = rdp(closed, epsilon)
        return kept.dropLast(1) // последняя точка совпадает с первой
    }

    private fun rdp(points: List<Point>, epsilon: Double): List<Point> {
        if (points.size < 3) return points
        val first = points.first()
        val last = points.last()
        var maxDist = -1.0
        var index = 0
        for (i in 1 until points.lastIndex) {
            val d = distanceToSegment(points[i], first, last)
            if (d > maxDist) {
                maxDist = d
                index = i
            }
        }
        return if (maxDist > epsilon) {
            rdp(points.subList(0, index + 1), epsilon).dropLast(1) + rdp(points.subList(index, points.size), epsilon)
        } else {
            listOf(first, last)
        }
    }

    // --- 2. Удаление шума --------------------------------------------------

    private fun removeNoise(input: List<Point>, minEdge: Double): List<Point> {
        val pts = input.toMutableList()
        var changed = true
        while (changed && pts.size >= 3) {
            changed = false
            // Слишком короткие стены стягиваем в точку.
            for (i in pts.indices) {
                val j = (i + 1) % pts.size
                if (dist(pts[i], pts[j]) < minEdge) {
                    pts[i] = mid(pts[i], pts[j])
                    pts.removeAt(j)
                    changed = true
                    break
                }
            }
            if (changed) continue
            // Почти прямые изломы убираем.
            for (i in pts.indices) {
                val prev = pts[(i - 1 + pts.size) % pts.size]
                val next = pts[(i + 1) % pts.size]
                if (abs(turnDeg(prev, pts[i], next)) < COLLINEAR_DEG) {
                    pts.removeAt(i)
                    changed = true
                    break
                }
            }
        }
        return pts
    }

    // --- 3. Выравнивание углов ---------------------------------------------

    /** Стена после выравнивания: направление (радианы) и точка, через которую она проходит. */
    private data class Edge(val angle: Double, val a: Point, val b: Point) {
        val mid: Point get() = Point((a.x + b.x) / 2, (a.y + b.y) / 2)
    }

    private fun snapEdges(pts: List<Point>): List<Edge>? {
        val raw = pts.indices.map { i -> pts[i] to pts[(i + 1) % pts.size] }
        val longest = raw.maxBy { (a, b) -> dist(a, b) }
        var base = angleOf(longest.first, longest.second)
        val toAxis = (base / (PI / 2)).roundToInt() * (PI / 2)
        if (abs(base - toAxis) < Math.toRadians(AXIS_SNAP_DEG)) base = toAxis

        var edges = raw.map { (a, b) ->
            val rel = normalizeRad(angleOf(a, b) - base)
            val right = (rel / (PI / 2)).roundToInt() * (PI / 2)
            val snapped = if (abs(rel - right) < Math.toRadians(RIGHT_ANGLE_SNAP_DEG)) {
                right
            } else {
                (rel / (PI / 4)).roundToInt() * (PI / 4)
            }
            Edge(normalizeRad(base + snapped), a, b)
        }

        // Соседние стены одного направления сливаем, встречные «шипы» убираем.
        var changed = true
        while (changed && edges.size >= 3) {
            changed = false
            for (i in edges.indices) {
                val j = (i + 1) % edges.size
                val diff = abs(normalizeRad(edges[i].angle - edges[j].angle))
                if (diff < 1e-6) {
                    val merged = Edge(edges[i].angle, edges[i].a, edges[j].b)
                    edges = edges.toMutableList().apply {
                        set(i, merged)
                        removeAt(j)
                    }
                    changed = true
                    break
                }
                if (abs(diff - PI) < 1e-6) {
                    val shorter = if (dist(edges[i].a, edges[i].b) < dist(edges[j].a, edges[j].b)) i else j
                    edges = edges.toMutableList().apply { removeAt(shorter) }
                    changed = true
                    break
                }
            }
        }
        return edges.takeIf { it.size >= 3 }
    }

    // --- 4. Вершины как пересечения ----------------------------------------

    private fun intersect(edges: List<Edge>): List<Point>? = edges.indices.map { i ->
        val prev = edges[(i - 1 + edges.size) % edges.size]
        lineIntersection(prev, edges[i]) ?: return null
    }

    private fun lineIntersection(e1: Edge, e2: Edge): Point? {
        val d1x = cosClean(e1.angle)
        val d1y = sinClean(e1.angle)
        val d2x = cosClean(e2.angle)
        val d2y = sinClean(e2.angle)
        val denom = d1x * d2y - d1y * d2x
        if (abs(denom) < 1e-9) return null
        val p = e1.mid
        val q = e2.mid
        val t = ((q.x - p.x) * d2y - (q.y - p.y) * d2x) / denom
        return Point(p.x + t * d1x, p.y + t * d1y)
    }

    /**
     * Обход по часовой стрелке (на экране), первая вершина — левая верхняя,
     * координаты сдвинуты к началу. Так «Стена 1» всегда начинается в одном месте.
     */
    private fun normalize(pts: List<Point>): List<Point> {
        var list = pts
        if (signedArea(list) < 0) list = list.reversed()
        val start = list.indices.minBy { list[it].x + list[it].y }
        list = list.drop(start) + list.take(start)
        val minX = list.minOf { it.x }
        val minY = list.minOf { it.y }
        return list.map { Point(it.x - minX, it.y - minY) }
    }

    // --- Утилиты ------------------------------------------------------------

    /** Площадь со знаком: > 0 — обход по часовой стрелке при оси y вниз. */
    fun signedArea(pts: List<Point>): Double {
        var s = 0.0
        for (i in pts.indices) {
            val a = pts[i]
            val b = pts[(i + 1) % pts.size]
            s += a.x * b.y - b.x * a.y
        }
        return s / 2
    }

    private fun diagonal(pts: List<Point>): Double =
        hypot(pts.maxOf { it.x } - pts.minOf { it.x }, pts.maxOf { it.y } - pts.minOf { it.y })

    private fun dist(a: Point, b: Point) = hypot(b.x - a.x, b.y - a.y)
    private fun mid(a: Point, b: Point) = Point((a.x + b.x) / 2, (a.y + b.y) / 2)
    private fun angleOf(a: Point, b: Point) = atan2(b.y - a.y, b.x - a.x)

    /** cos/sin без «хвостов» вида 6e-17 для углов, кратных 90°. */
    private fun cosClean(a: Double) = cos(a).let { if (abs(it) < 1e-12) 0.0 else it }
    private fun sinClean(a: Double) = sin(a).let { if (abs(it) < 1e-12) 0.0 else it }

    private fun normalizeRad(a: Double): Double {
        var r = a % (2 * PI)
        if (r <= -PI) r += 2 * PI
        if (r > PI) r -= 2 * PI
        return r
    }

    private fun turnDeg(prev: Point, cur: Point, next: Point): Double =
        Math.toDegrees(normalizeRad(angleOf(cur, next) - angleOf(prev, cur)))

    private fun distanceToSegment(p: Point, a: Point, b: Point): Double {
        val dx = b.x - a.x
        val dy = b.y - a.y
        val len2 = dx * dx + dy * dy
        if (len2 == 0.0) return dist(p, a)
        val t = (((p.x - a.x) * dx + (p.y - a.y) * dy) / len2).coerceIn(0.0, 1.0)
        return hypot(p.x - (a.x + t * dx), p.y - (a.y + t * dy))
    }
}
