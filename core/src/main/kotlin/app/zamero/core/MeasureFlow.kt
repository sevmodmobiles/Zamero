package app.zamero.core

/** Что именно измеряем — поле, в которое попадёт следующее значение. */
sealed interface MeasureTarget {
    data class Wall(val index: Int) : MeasureTarget
    data object Height : MeasureTarget
    data class OpeningWidth(val index: Int) : MeasureTarget
    data class OpeningHeight(val index: Int) : MeasureTarget
    /** Участок [index] инженерной линии [elementId]. */
    data class EngSegment(val elementId: String, val index: Int) : MeasureTarget
}

/**
 * Пошаговый обмер: приложение само подсказывает, что мерить дальше, а значение
 * с рулетки или камеры сразу попадает в нужное поле. Руки заняты рулеткой —
 * экран трогать почти не нужно.
 */
object MeasureFlow {

    fun targets(room: Room): List<MeasureTarget> = buildList {
        room.walls.indices.forEach { add(MeasureTarget.Wall(it)) }
        add(MeasureTarget.Height)
        room.openings.indices.forEach {
            add(MeasureTarget.OpeningWidth(it))
            add(MeasureTarget.OpeningHeight(it))
        }
        room.engineering.forEach { e -> e.segments.indices.forEach { add(MeasureTarget.EngSegment(e.id, it)) } }
    }

    fun valueOf(room: Room, target: MeasureTarget): Measure? = when (target) {
        is MeasureTarget.Wall -> room.walls.getOrNull(target.index)
        MeasureTarget.Height -> room.height
        is MeasureTarget.OpeningWidth -> room.openings.getOrNull(target.index)?.width
        is MeasureTarget.OpeningHeight -> room.openings.getOrNull(target.index)?.height
        is MeasureTarget.EngSegment -> room.engineering.firstOrNull { it.id == target.elementId }?.segments?.getOrNull(target.index)
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
    }

    /** Первое пустое поле после [current] (по кругу), или null, если всё измерено. */
    fun nextEmpty(room: Room, current: MeasureTarget? = null): MeasureTarget? {
        val all = targets(room)
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
