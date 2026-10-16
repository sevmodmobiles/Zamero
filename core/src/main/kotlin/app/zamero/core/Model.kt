package app.zamero.core

/** Откуда взялось значение — важно для доверия к замеру. */
enum class Source(val title: String) {
    MANUAL("вручную"),
    LASER("рулетка"),
    CAMERA("камера"),
}

/** Одно измеренное расстояние в метрах. */
data class Measure(val meters: Double, val source: Source)

/** Поворот на углу помещения при обходе по часовой стрелке. */
enum class Turn { RIGHT, LEFT }

/** Форма помещения: стены по порядку обхода и поворот после каждой стены (все углы 90°). */
enum class Shape(val title: String, val turns: List<Turn>) {
    RECTANGLE("Прямоугольник", List(4) { Turn.RIGHT }),
    L_SHAPE("Г-образная", listOf(Turn.RIGHT, Turn.RIGHT, Turn.RIGHT, Turn.LEFT, Turn.RIGHT, Turn.RIGHT)),
    ;

    val wallCount: Int get() = turns.size
}

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

data class Room(
    val id: String,
    val name: String,
    val shape: Shape = Shape.RECTANGLE,
    /** Длины стен в порядке обхода; null — ещё не измерена. */
    val walls: List<Measure?> = List(shape.wallCount) { null },
    val height: Measure? = null,
    val openings: List<Opening> = emptyList(),
) {
    init {
        require(walls.size == shape.wallCount) { "walls.size must match shape" }
    }

    fun withShape(newShape: Shape): Room =
        copy(shape = newShape, walls = List(newShape.wallCount) { walls.getOrNull(it) })
}

/** Объект обмера (квартира, офис) — набор помещений. */
data class Project(
    val id: String,
    val name: String,
    val rooms: List<Room> = emptyList(),
)
