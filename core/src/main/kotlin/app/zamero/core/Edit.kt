package app.zamero.core

import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/** Активная точка плана: вершина контура или точка трассы/дорисованной линии. */
sealed interface PointRef {
    data class Vertex(val index: Int) : PointRef
    data class RoutePoint(val elementId: String, val index: Int) : PointRef
}

/**
 * Правка геометрии плана: перетаскивание и удаление точек.
 * Все координаты на входе — метры плана.
 */
object Edit {

    /** Точки ближе этого (м) считаются одной: тянутся и удаляются вместе. */
    const val SAME_POINT_M = 0.02

    /**
     * Переводит эскиз в масштаб плана: вершины, трассы и колонны ложатся туда, где
     * они видны на плане. После этого точка эскиза = точка плана / масштаб, и правка
     * одной точки не сдвигает остальные.
     */
    fun rebase(room: Room): Room {
        val plan = Plan.room(room) ?: return room
        val s = Plan.scale(room)
        fun k(p: Point) = Point(p.x / s, p.y / s)
        return room.copy(
            outline = plan.vertices.map(::k),
            engineering = room.engineering.map { e -> if (e.hasRoute) e.copy(route = Plan.route(room, plan, e).map(::k)) else e },
            columns = room.columns.map { c -> c.copy(center = k(Columns.plan(room, plan, c).center)) },
        )
    }

    /** Все активные точки с положением на плане (м). [include] — какие элементы учитывать. */
    fun points(room: Room, include: (EngElement) -> Boolean = { true }): List<Pair<PointRef, Point>> {
        val plan = Plan.room(room) ?: return emptyList()
        val out = ArrayList<Pair<PointRef, Point>>()
        plan.vertices.forEachIndexed { i, p -> out += PointRef.Vertex(i) to p }
        room.engineering.filter { it.hasRoute && include(it) }.forEach { e ->
            Plan.route(room, plan, e).forEachIndexed { i, p -> out += PointRef.RoutePoint(e.id, i) to p }
        }
        return out
    }

    /**
     * Перетаскивает точку [ref] в [target]. Вместе с ней едут точки, совпадавшие
     * с ней (например, конец перегородки в углу). Конечное положение притягивается
     * к другим точкам ближе [snap] м, иначе выравнивается с соседними точками по
     * осям помещения. Длины изменившихся отрезков сбрасываются — их нужно перемерить.
     */
    fun movePoint(room: Room, ref: PointRef, target: Point, snap: Double): Room {
        val plan = Plan.room(room) ?: return room
        val angle = Columns.baseAngle(plan)
        val s = Plan.scale(room)
        val base = rebase(room)
        val all = points(base)
        val origin = all.firstOrNull { it.first == ref }?.second ?: return room
        val group = all.filter { dist(it.second, origin) < SAME_POINT_M }.map { it.first }.toSet()

        val others = all.filter { it.first !in group }
        val nearest = others.minByOrNull { dist(it.second, target) }
        val p = if (nearest != null && dist(nearest.second, target) <= snap) {
            nearest.second
        } else {
            align(target, neighbours(base, all, ref), angle, snap)
        }
        val sketch = Point(p.x / s, p.y / s)
        var result = base
        for (g in group) result = place(result, g, sketch)
        return result
    }

    /** Ставит точку эскиза и сбрасывает длины примыкающих отрезков. */
    private fun place(room: Room, ref: PointRef, sketch: Point): Room = when (ref) {
        is PointRef.Vertex -> {
            val n = room.outline.size
            val i = ref.index
            room.copy(
                outline = room.outline.toMutableList().also { it[i] = sketch },
                walls = room.walls.mapIndexed { j, w -> if (j == i || j == (i - 1 + n) % n) null else w },
            )
        }
        is PointRef.RoutePoint -> room.copy(
            engineering = room.engineering.map { e ->
                if (e.id != ref.elementId) {
                    e
                } else {
                    e.copy(
                        route = e.route.toMutableList().also { it[ref.index] = sketch },
                        segments = e.segments.mapIndexed { j, m -> if (j == ref.index || j == ref.index - 1) null else m },
                    )
                }
            },
        )
    }

    /** Соседние точки по контуру или по трассе. */
    private fun neighbours(room: Room, all: List<Pair<PointRef, Point>>, ref: PointRef): List<Point> {
        val refs = when (ref) {
            is PointRef.Vertex -> {
                val n = room.outline.size
                listOf(PointRef.Vertex((ref.index - 1 + n) % n), PointRef.Vertex((ref.index + 1) % n))
            }
            is PointRef.RoutePoint -> listOf(PointRef.RoutePoint(ref.elementId, ref.index - 1), PointRef.RoutePoint(ref.elementId, ref.index + 1))
        }
        return refs.mapNotNull { r -> all.firstOrNull { it.first == r }?.second }
    }

    /**
     * Выравнивание по осям помещения: если точка почти на одной «горизонтали» или
     * «вертикали» с соседней — ставим ровно.
     */
    private fun align(p: Point, neighbours: List<Point>, angle: Double, tol: Double): Point {
        val ux = cos(angle)
        val uy = sin(angle)
        fun u(q: Point) = q.x * ux + q.y * uy
        fun v(q: Point) = -q.x * uy + q.y * ux
        var pu = u(p)
        var pv = v(p)
        var uDone = false
        var vDone = false
        for (n in neighbours) {
            if (!uDone && kotlin.math.abs(pu - u(n)) <= tol) { pu = u(n); uDone = true }
            if (!vDone && kotlin.math.abs(pv - v(n)) <= tol) { pv = v(n); vDone = true }
        }
        return Point(pu * ux - pv * uy, pu * uy + pv * ux)
    }

    /**
     * Стирает точку. Вершина контура: две соседние стены сливаются в одну (контур
     * остаётся не меньше треугольника). Точка линии: соседние участки сливаются,
     * линия короче двух точек удаляется. Совпадающие точки других линий не трогаем.
     */
    fun deletePoint(room: Room, ref: PointRef): Room = when (ref) {
        is PointRef.Vertex -> {
            val n = room.outline.size
            if (n <= 3) {
                room
            } else {
                val i = ref.index
                val prev = (i - 1 + n) % n
                val walls = room.walls.indices.filter { it != i }.map { j -> if (j == prev) null else room.walls[j] }
                fun shift(w: Int) = when {
                    w < 0 -> -1
                    w == i || w == prev -> -1
                    w > i -> w - 1
                    else -> w
                }
                room.copy(
                    outline = room.outline.filterIndexed { j, _ -> j != i },
                    walls = walls,
                    columns = room.columns.map { c ->
                        val u = shift(c.refWallU)
                        val v = shift(c.refWallV)
                        c.copy(
                            refWallU = u, refWallV = v,
                            offsetU = if (u < 0) null else c.offsetU,
                            offsetV = if (v < 0) null else c.offsetV,
                        )
                    },
                )
            }
        }
        is PointRef.RoutePoint -> {
            val e = room.engineering.firstOrNull { it.id == ref.elementId }
            if (e == null || e.route.size <= 2) {
                room.copy(engineering = room.engineering.filterNot { it.id == ref.elementId })
            } else {
                val i = ref.index
                val segments = when (i) {
                    0 -> e.segments.drop(1)
                    e.route.lastIndex -> e.segments.dropLast(1)
                    else -> e.segments.filterIndexed { j, _ -> j != i }.mapIndexed { j, m -> if (j == i - 1) null else m }
                }
                // Высоты: у слитого участка остаётся высота левого.
                val elevations = when (i) {
                    0 -> e.segmentElevationsMm.drop(1)
                    e.route.lastIndex -> e.segmentElevationsMm.dropLast(1)
                    else -> e.segmentElevationsMm.filterIndexed { j, _ -> j != i }
                }
                val updated = e.copy(
                    route = e.route.filterIndexed { j, _ -> j != i },
                    segments = segments,
                    segmentElevationsMm = elevations,
                )
                room.copy(engineering = room.engineering.map { if (it.id == e.id) updated else it })
            }
        }
    }

    /**
     * Стирает участок [index] линии: крайний — линия укорачивается, средний — линия
     * делится на две (вторая получает [newId]), единственный — линия удаляется.
     */
    fun deleteSegment(room: Room, elementId: String, index: Int, newId: String): Room {
        val e = room.engineering.firstOrNull { it.id == elementId } ?: return room
        if (index !in e.segments.indices) return room
        val parts = listOf(
            Triple(e.route.subList(0, index + 1), e.segments.subList(0, index), e.segmentElevationsMm.subList(0, index)),
            Triple(
                e.route.subList(index + 1, e.route.size),
                e.segments.subList(index + 1, e.segments.size),
                e.segmentElevationsMm.subList(index + 1, e.segments.size),
            ),
        ).filter { (route, _, _) -> route.size >= 2 }
        val replaced = parts.mapIndexed { i, (route, segments, elevations) ->
            e.copy(
                id = if (i == 0) e.id else newId,
                route = route.toList(),
                segments = segments.toList(),
                segmentElevationsMm = elevations.toList(),
            )
        }
        val at = room.engineering.indexOf(e)
        val list = room.engineering.toMutableList().apply {
            removeAt(at)
            addAll(at, replaced)
        }
        return room.copy(engineering = list)
    }

    /**
     * Стирает сразу несколько участков (выбор лассо). Оставшиеся подряд идущие участки
     * каждой линии становятся отдельными линиями: первая сохраняет id, остальные
     * получают новые из [newId].
     */
    fun deleteSegments(room: Room, segments: Set<Pair<String, Int>>, newId: () -> String): Room {
        if (segments.isEmpty()) return room
        val result = ArrayList<EngElement>()
        for (e in room.engineering) {
            val drop = segments.filter { it.first == e.id }.map { it.second }.toSet()
            if (drop.isEmpty()) {
                result += e
                continue
            }
            // Серии подряд идущих оставшихся участков.
            var first = true
            var i = 0
            while (i < e.segments.size) {
                if (i in drop) { i++; continue }
                val start = i
                while (i < e.segments.size && i !in drop) i++
                result += e.copy(
                    id = if (first) e.id else newId(),
                    route = e.route.subList(start, i + 1).toList(),
                    segments = e.segments.subList(start, i).toList(),
                    segmentElevationsMm = e.segmentElevationsMm.subList(start, i).toList(),
                )
                first = false
            }
        }
        return room.copy(engineering = result)
    }

    /** Своя высота участка (мм) или null — как у всей трассы. */
    fun setSegmentElevation(room: Room, elementId: String, index: Int, mm: Int?): Room = room.copy(
        engineering = room.engineering.map { e ->
            if (e.id != elementId || index !in e.segmentElevationsMm.indices) {
                e
            } else {
                e.copy(segmentElevationsMm = e.segmentElevationsMm.toMutableList().also { it[index] = mm })
            }
        },
    )

    private fun dist(a: Point, b: Point) = hypot(b.x - a.x, b.y - a.y)
}
