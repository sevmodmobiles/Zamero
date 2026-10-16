package app.zamero.core

import java.util.Locale

/** Текстовый отчёт по объекту — для отправки в мессенджер или почту. */
object Report {

    fun text(project: Project): String = buildString {
        appendLine("Обмер: ${project.name}")
        appendLine()
        var total = 0.0
        project.rooms.forEach { room ->
            val s = Geometry.stats(room)
            appendLine("■ ${room.name} (${room.shape.title.lowercase()})")
            room.walls.forEachIndexed { i, w -> appendLine("  Стена ${i + 1}: ${w.fmt()}") }
            appendLine("  Высота: ${room.height.fmt()}")
            room.openings.indices.forEach { i ->
                val o = room.openings[i]
                appendLine("  ${MeasureFlow.label(room, MeasureTarget.OpeningWidth(i)).substringBefore(':')}: ${o.width.fmt()} × ${o.height.fmt()}")
            }
            s.floorArea?.let { appendLine("  Площадь пола: ${m2(it)}"); total += it }
            s.perimeter?.let { appendLine("  Периметр: ${m(it)}") }
            s.wallAreaNet?.let { appendLine("  Площадь стен без проёмов: ${m2(it)}") }
            s.volume?.let { appendLine("  Объём: ${"%.2f".format(Locale.ROOT, it)} м³") }
            if (s.needsRecheck) appendLine("  ⚠ Контур не сходится на ${cm(s.closureError!!)} — перемерьте стены")
            appendLine()
        }
        append("Итого площадь: ${m2(total)}")
    }

    private fun Measure?.fmt(): String = this?.let { "${m(it.meters)} (${it.source.title})" } ?: "—"
    private fun m(v: Double) = "%.3f м".format(Locale.ROOT, v)
    private fun m2(v: Double) = "%.2f м²".format(Locale.ROOT, v)
    private fun cm(v: Double) = "%.1f см".format(Locale.ROOT, v * 100)
}
