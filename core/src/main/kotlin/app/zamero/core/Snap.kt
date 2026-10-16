package app.zamero.core

import kotlin.math.abs
import kotlin.math.hypot

/**
 * Притягивание концов дорисованной линии к существующим стенам: крайний участок
 * удлиняется или укорачивается вдоль своего направления до ближайшей стены,
 * если она ближе [tolerance]. Углы при этом не меняются.
 */
object Snap {

    fun extendToWalls(route: List<Point>, walls: List<Pair<Point, Point>>, tolerance: Double): List<Point> {
        if (route.size < 2 || walls.isEmpty()) return route
        val out = route.toMutableList()
        snapEnd(out, end = 0, inner = 1, walls, tolerance)
        snapEnd(out, end = out.lastIndex, inner = out.lastIndex - 1, walls, tolerance)
        return out
    }

    /** Концы линии, оказавшиеся ближе [tolerance] к готовой точке, ставятся точно в неё. */
    fun endsToPoints(route: List<Point>, points: List<Point>, tolerance: Double): List<Point> {
        if (route.size < 2 || points.isEmpty()) return route
        val out = route.toMutableList()
        for (i in listOf(0, out.lastIndex)) {
            val nearest = points.minBy { hypot(it.x - out[i].x, it.y - out[i].y) }
            if (hypot(nearest.x - out[i].x, nearest.y - out[i].y) <= tolerance) out[i] = nearest
        }
        return out
    }

    private fun snapEnd(pts: MutableList<Point>, end: Int, inner: Int, walls: List<Pair<Point, Point>>, tol: Double) {
        val p = pts[end]
        val q = pts[inner]
        val len = hypot(p.x - q.x, p.y - q.y)
        if (len < 1e-9) return
        // Направление от внутренней точки к концу: t > 0 — удлинение, t < 0 — укорачивание.
        val dx = (p.x - q.x) / len
        val dy = (p.y - q.y) / len
        var best: Double? = null
        for ((a, b) in walls) {
            val t = rayHit(p, dx, dy, a, b) ?: continue
            // Укорачивать участок до нуля и дальше нельзя.
            if (abs(t) <= tol && len + t > len * 0.2 && (best == null || abs(t) < abs(best))) best = t
        }
        best?.let { pts[end] = Point(p.x + dx * it, p.y + dy * it) }
    }

    /** Параметр t точки пересечения прямой p + t·d с отрезком ab, или null. */
    private fun rayHit(p: Point, dx: Double, dy: Double, a: Point, b: Point): Double? {
        val ex = b.x - a.x
        val ey = b.y - a.y
        val denom = dx * ey - dy * ex
        if (abs(denom) < 1e-12) return null
        val wx = a.x - p.x
        val wy = a.y - p.y
        val t = (wx * ey - wy * ex) / denom
        val u = (wx * dy - wy * dx) / denom
        return if (u in -1e-9..1 + 1e-9) t else null
    }
}
