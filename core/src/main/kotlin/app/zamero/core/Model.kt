package app.zamero.core

/** Откуда взялось значение — важно для доверия к замеру. */
enum class Source(val title: String) {
    MANUAL("вручную"),
    LASER("дальномер"),
    CAMERA("камера"),
}

/** Одно измеренное расстояние в метрах. */
data class Measure(val meters: Double, val source: Source) {
    val millimeters: Long get() = Math.round(meters * 1000)
}

/** Точка на эскизе (условные единицы, ось y направлена вниз, как на экране). */
data class Point(val x: Double, val y: Double)

enum class OpeningType(val title: String) {
    WINDOW("Окно"),
    DOOR("Дверь"),
}

/** Проём в стене: окно или дверь. */
data class Opening(
    val type: OpeningType,
    val width: Measure? = null,
    val height: Measure? = null,
)

/**
 * Помещение. [outline] — спрямлённый контур, нарисованный пальцем: вершины по порядку,
 * стена i идёт от вершины i к вершине i+1. Из контура берутся только направления стен,
 * длины — из замеров [walls].
 */
data class Room(
    val id: String,
    val name: String,
    val outline: List<Point> = emptyList(),
    val walls: List<Measure?> = List(outline.size) { null },
    val height: Measure? = null,
    val openings: List<Opening> = emptyList(),
    /** Инженерные линии, привязанные к плану этого помещения. */
    val engineering: List<EngElement> = emptyList(),
) {
    init {
        require(walls.size == outline.size) { "walls.size must match outline" }
    }

    val hasOutline: Boolean get() = outline.size >= 3

    /** Новый контур: замеры стен сохраняем, только если число стен не изменилось. */
    fun withOutline(points: List<Point>): Room =
        copy(outline = points, walls = if (points.size == walls.size) walls else List(points.size) { null })
}

/** Объект обмера: адрес, заказчик, исполнитель и помещения. */
data class Project(
    val id: String,
    val address: String,
    val customer: String = "",
    val contractor: String = "",
    val createdAt: Long = 0,
    val rooms: List<Room> = emptyList(),
)

/** Готовые контуры для быстрого старта. */
object Outlines {
    val RECTANGLE = listOf(Point(0.0, 0.0), Point(400.0, 0.0), Point(400.0, 300.0), Point(0.0, 300.0))
    val L_SHAPE = listOf(
        Point(0.0, 0.0), Point(500.0, 0.0), Point(500.0, 400.0),
        Point(200.0, 400.0), Point(200.0, 250.0), Point(0.0, 250.0),
    )
}
