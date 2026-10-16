package app.zamero.core

import kotlin.math.hypot

/**
 * Проекция линии заданной ширины на пол: контур полосы вокруг осевой.
 * В изломах — «острый» стык (как у реального лотка или воздуховода).
 */
object Footprint {

    /** Ширина на плане, м: ширина сечения, диаметр, толщина стены; null — линия без ширины. */
    fun width(e: EngElement): Double? = when (e.type.section) {
        Section.ROUND -> e.diameterMm
        Section.RECT -> e.widthMm
        Section.NONE -> if (e.type.hasThickness) e.widthMm else null
    }?.takeIf { it > 0 }?.let { it / 1000.0 }

    /** Замкнутый контур полосы: левая сторона вперёд, правая — назад. */
    fun outline(route: List<Point>, width: Double): List<Point> {
        if (route.size < 2) return emptyList()
        val half = width / 2
        val normals = route.zipWithNext { a, b ->
            val len = hypot(b.x - a.x, b.y - a.y).takeIf { it > 1e-12 } ?: 1.0
            Point(-(b.y - a.y) / len, (b.x - a.x) / len)
        }
        val left = ArrayList<Point>(route.size)
        val right = ArrayList<Point>(route.size)
        for (i in route.indices) {
            val (dir, scale) = when (i) {
                0 -> normals.first() to half
                route.lastIndex -> normals.last() to half
                else -> {
                    val n1 = normals[i - 1]
                    val n2 = normals[i]
                    val mx = n1.x + n2.x
                    val my = n1.y + n2.y
                    val ml = hypot(mx, my)
                    if (ml < 1e-9) {
                        n1 to half // разворот на 180° — без стыка
                    } else {
                        val m = Point(mx / ml, my / ml)
                        val cos = m.x * n1.x + m.y * n1.y
                        m to (half / cos).coerceAtMost(half * 3)
                    }
                }
            }
            val p = route[i]
            left += Point(p.x + dir.x * scale, p.y + dir.y * scale)
            right += Point(p.x - dir.x * scale, p.y - dir.y * scale)
        }
        return left + right.asReversed()
    }
}
