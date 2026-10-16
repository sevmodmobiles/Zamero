package app.zamero.core

/** Форма сечения: круглое, прямоугольное или без сечения (стены, линии). */
enum class Section { ROUND, RECT, NONE }

/** Строительные элементы (дорисованные стены) или инженерные сети. */
enum class Category { CONSTRUCTION, ENGINEERING }

/** Что прокладываем или дорисовываем. [layer] — имя слоя в DXF. */
enum class EngType(
    val title: String,
    val section: Section,
    val layer: String,
    val dxfColor: Int,
    val category: Category = Category.ENGINEERING,
) {
    WALL("Доп. стена", Section.NONE, "WALLS_EXTRA", 7, Category.CONSTRUCTION),
    PARTITION("Перегородка", Section.NONE, "PARTITIONS", 8, Category.CONSTRUCTION),
    LINE("Линия", Section.NONE, "LINES", 9, Category.CONSTRUCTION),
    TRAY("Лоток", Section.RECT, "TRAY", 30),
    PIPE("Труба", Section.ROUND, "PIPE", 5),
    DRAIN("Дренаж", Section.ROUND, "DRAIN", 3),
    DUCT_ROUND("Воздуховод круглый", Section.ROUND, "DUCT_ROUND", 6),
    DUCT_RECT("Воздуховод прямоугольный", Section.RECT, "DUCT_RECT", 4),
    ;

    /** У стен и перегородок задаётся толщина (хранится в widthMm). */
    val hasThickness: Boolean get() = this == WALL || this == PARTITION
}

/**
 * Инженерная линия в помещении: трасса нарисована поверх плана (в координатах эскиза
 * помещения), длины участков — из замеров.
 */
data class EngElement(
    val id: String,
    val type: EngType,
    val route: List<Point> = emptyList(),
    val segments: List<Measure?> = List((route.size - 1).coerceAtLeast(0)) { null },
    /** Диаметр для круглых сечений, мм. */
    val diameterMm: Int? = null,
    /** Ширина × высота для прямоугольных, мм; для стен и перегородок widthMm — толщина. */
    val widthMm: Int? = null,
    val heightMm: Int? = null,
    /** Отметка (высота оси или низа) от чистого пола, мм. */
    val elevationMm: Int? = null,
    val note: String = "",
) {
    init {
        require(segments.size == (route.size - 1).coerceAtLeast(0)) { "segments must match route" }
    }

    val hasRoute: Boolean get() = route.size >= 2

    fun withRoute(points: List<Point>): EngElement {
        val count = (points.size - 1).coerceAtLeast(0)
        return copy(route = points, segments = if (count == segments.size) segments else List(count) { null })
    }

    /** «Ø110», «200×60» или пусто, если размеры не заданы. */
    val sectionText: String
        get() = when (type.section) {
            Section.ROUND -> diameterMm?.let { "Ø$it" } ?: ""
            Section.RECT -> if (widthMm != null && heightMm != null) "${widthMm}×$heightMm" else ""
            Section.NONE -> if (type.hasThickness) widthMm?.let { "$it мм" } ?: "" else ""
        }

    /** Подпись для плана и отчёта: «Лоток 200×60, h=2800». */
    val label: String
        get() = buildString {
            append(type.title)
            if (sectionText.isNotEmpty()) append(' ').append(sectionText)
            elevationMm?.let { append(", h=").append(it) }
        }

    val totalLength: Double? get() = if (segments.all { it != null }) segments.sumOf { it!!.meters } else null
}
