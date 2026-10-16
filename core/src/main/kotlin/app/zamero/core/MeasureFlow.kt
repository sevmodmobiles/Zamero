package app.zamero.core

/** Что именно измеряем — поле, в которое попадёт следующее значение. */
sealed interface MeasureTarget {
    data class Wall(val index: Int) : MeasureTarget
    data object Height : MeasureTarget
    data class OpeningWidth(val index: Int) : MeasureTarget
    data class OpeningHeight(val index: Int) : MeasureTarget
    /** Участок [index] инженерной линии [elementId]. */
    data class EngSegment(val elementId: String, val index: Int) : MeasureTarget
    /** Размер или привязка колонны. */
    data class ColumnValue(val columnId: String, val dim: ColumnDim) : MeasureTarget
}

/**
 * Пошаговый обмер: приложение само подсказывает, что мерить дальше, а значение
 * с рулетки или камеры сразу попадает в нужное поле. Руки заняты рулеткой —
 * экран трогать почти не нужно.
 */
object MeasureFlow {

    fun targets(room: Room): List<MeasureTarget> = buildList {
        room.walls.indices.filter(room::hasWall).forEach { add(MeasureTarget.Wall(it)) }
        add(MeasureTarget.Height)
        room.openings.indices.forEach {
            add(MeasureTarget.OpeningWidth(it))
            add(MeasureTarget.OpeningHeight(it))
        }
        room.engineering.forEach { e -> e.segments.indices.forEach { add(MeasureTarget.EngSegment(e.id, it)) } }
        room.columns.forEach { c -> columnDims(c).forEach { add(MeasureTarget.ColumnValue(c.id, it)) } }
    }

    /** Что меряем у колонны: размеры и обе привязки (если опорная стена найдена). */
    fun columnDims(c: Column): List<ColumnDim> = buildList {
        add(ColumnDim.WIDTH)
        if (!c.round) add(ColumnDim.DEPTH)
        if (c.refWallU >= 0) add(ColumnDim.OFFSET_U)
        if (c.refWallV >= 0) add(ColumnDim.OFFSET_V)
    }

    fun valueOf(room: Room, target: MeasureTarget): Measure? = when (target) {
        is MeasureTarget.Wall -> room.walls.getOrNull(target.index)
        MeasureTarget.Height -> room.height
        is MeasureTarget.OpeningWidth -> room.openings.getOrNull(target.index)?.width
        is MeasureTarget.OpeningHeight -> room.openings.getOrNull(target.index)?.height
        is MeasureTarget.EngSegment -> room.engineering.firstOrNull { it.id == target.elementId }?.segments?.getOrNull(target.index)
        is MeasureTarget.ColumnValue -> room.columns.firstOrNull { it.id == target.columnId }?.let { c ->
            when (target.dim) {
                ColumnDim.WIDTH -> c.width
                ColumnDim.DEPTH -> c.depth
                ColumnDim.OFFSET_U -> c.offsetU
                ColumnDim.OFFSET_V -> c.offsetV
            }
        }
    }

    fun set(room: Room, target: MeasureTarget, value: Measure?): Room = when (target) {
        is MeasureTarget.Wall -> room.copy(walls = room.walls.toMutableList().also { it[target.index] = value })
        MeasureTarget.Height -> room.copy(height = value)
        is MeasureTarget.OpeningWidth -> room.updateOpening(target.index) { copy(width = value) }
        is MeasureTarget.OpeningHeight -> room.updateOpening(target.index) { copy(height = value) }
        is MeasureTarget.EngSegment -> room.copy(
            engineering = room.engineering.map { e ->
                if (e.id == target.elementId && target.index in e.segments.indices) {
                    e.copy(segments = e.segments.toMutableList().also { it[target.index] = value })
                } else {
                    e
                }
            },
        )
        is MeasureTarget.ColumnValue -> room.copy(
            columns = room.columns.map { c ->
                if (c.id != target.columnId) {
                    c
                } else {
                    when (target.dim) {
                        ColumnDim.WIDTH -> c.copy(width = value)
                        ColumnDim.DEPTH -> c.copy(depth = value)
                        ColumnDim.OFFSET_U -> c.copy(offsetU = value)
                        ColumnDim.OFFSET_V -> c.copy(offsetV = value)
                    }
                }
            },
        )
    }

    /** Относится ли поле к этапу «Инженерия» (участки инженерных трасс). */
    fun isEngineering(room: Room, target: MeasureTarget): Boolean =
        target is MeasureTarget.EngSegment &&
            room.engineering.firstOrNull { it.id == target.elementId }?.type?.category == Category.ENGINEERING

    /** Поля этапа: помещения (всё, кроме инженерии) или только инженерия. */
    fun targets(room: Room, engineering: Boolean): List<MeasureTarget> =
        targets(room).filter { isEngineering(room, it) == engineering }

    /**
     * Первое пустое поле после [current] (по кругу), или null, если всё измерено.
     * [engineering] — ограничить этапом (null — все поля).
     */
    fun nextEmpty(room: Room, current: MeasureTarget? = null, engineering: Boolean? = null): MeasureTarget? {
        val all = if (engineering == null) targets(room) else targets(room, engineering)
        if (all.isEmpty()) return null
        val start = current?.let { all.indexOf(it) + 1 } ?: 0
        return (all.indices).asSequence()
            .map { all[(start + it) % all.size] }
            .firstOrNull { valueOf(room, it) == null }
    }

    /** Записывает значение в [target] и возвращает обновлённое помещение и следующее поле. */
    fun record(room: Room, target: MeasureTarget, value: Measure): Pair<Room, MeasureTarget?> {
        val updated = set(room, target, value)
        return updated to nextEmpty(updated, target)
    }

    fun label(room: Room, target: MeasureTarget): String = when (target) {
        is MeasureTarget.Wall -> "Стена ${target.index + 1}"
        MeasureTarget.Height -> "Высота потолка"
        is MeasureTarget.OpeningWidth -> "${openingTitle(room, target.index)}: ширина"
        is MeasureTarget.OpeningHeight -> "${openingTitle(room, target.index)}: высота"
        is MeasureTarget.EngSegment -> "${engTitle(room, target.elementId)}, участок ${target.index + 1}"
        is MeasureTarget.ColumnValue -> "${columnTitle(room, target.columnId)}: ${columnDimTitle(room, target)}"
    }

    fun columnTitle(room: Room, columnId: String): String =
        "Колонна ${room.columns.indexOfFirst { it.id == columnId } + 1}"

    private fun columnDimTitle(room: Room, target: MeasureTarget.ColumnValue): String {
        val c = room.columns.first { it.id == target.columnId }
        return when (target.dim) {
            ColumnDim.WIDTH -> if (c.round) "диаметр" else "ширина"
            ColumnDim.DEPTH -> "глубина"
            ColumnDim.OFFSET_U -> "ось ↔ стена ${c.refWallU + 1}"
            ColumnDim.OFFSET_V -> "ось ↕ стена ${c.refWallV + 1}"
        }
    }

    /** «Труба 2» — номер среди элементов того же типа. */
    fun engTitle(room: Room, elementId: String): String {
        val index = room.engineering.indexOfFirst { it.id == elementId }
        if (index < 0) return "Элемент"
        val type = room.engineering[index].type
        val number = room.engineering.take(index + 1).count { it.type == type }
        return "${type.title} $number"
    }

    fun openingTitle(room: Room, index: Int): String {
        val type = room.openings[index].type
        val number = room.openings.take(index + 1).count { it.type == type }
        return "${type.title} $number"
    }

    private fun Room.updateOpening(index: Int, block: Opening.() -> Opening): Room =
        copy(openings = openings.toMutableList().also { it[index] = it[index].block() })
}
