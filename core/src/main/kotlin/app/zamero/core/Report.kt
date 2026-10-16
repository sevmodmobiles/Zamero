package app.zamero.core

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Текстовый отчёт по объекту — для отправки в мессенджер или почту. */
object Report {

    fun text(project: Project): String = buildString {
        appendLine("ОБМЕР ПОМЕЩЕНИЙ")
        appendLine("Адрес: ${project.address}")
        if (project.customer.isNotBlank()) appendLine("Заказчик: ${project.customer}")
        if (project.contractor.isNotBlank()) appendLine("Исполнитель: ${project.contractor}")
        if (project.createdAt > 0) {
            appendLine("Дата: ${SimpleDateFormat("dd.MM.yyyy", Locale.ROOT).format(Date(project.createdAt))}")
        }
        appendLine()
        var total = 0.0
        project.rooms.forEach { room ->
            val s = Geometry.stats(room)
            appendLine("■ ${room.name}")
            room.walls.forEachIndexed { i, w -> appendLine("  Стена ${i + 1}: ${w.fmt()}") }
            appendLine("  Высота: ${room.height.fmt()}")
            room.openings.indices.forEach { i ->
                val o = room.openings[i]
                appendLine("  ${MeasureFlow.openingTitle(room, i)}: ${o.width.fmt()} × ${o.height.fmt()}")
            }
            s.floorArea?.let { appendLine("  Площадь пола: ${m2(it)}"); total += it }
            s.perimeter?.let { appendLine("  Периметр: ${mm(it)}") }
            s.wallAreaNet?.let { appendLine("  Площадь стен без проёмов: ${m2(it)}") }
            s.volume?.let { appendLine("  Объём: ${"%.2f".format(Locale.ROOT, it)} м³") }
            if (s.needsRecheck) appendLine("  ⚠ Контур не сходится на ${mm(s.closureError!!)} — перемерьте стены")
            if (room.engineering.isNotEmpty()) appendLine("  Дополнительно:")
            room.engineering.forEach { e ->
                val segs = e.segments.joinToString(" + ") { it?.millimeters?.toString() ?: "?" }
                val total = e.totalLength?.let { " = ${mm(it)}" } ?: ""
                val details = e.label.removePrefix(e.type.title).trimStart(',', ' ')
                val system = e.system?.let { "[${it.title}] " } ?: ""
                appendLine("  • $system${MeasureFlow.engTitle(room, e.id)}${if (details.isNotEmpty()) ": $details" else ""}")
                if (e.segments.isNotEmpty()) appendLine("    участки: $segs$total")
                if (e.hasHeightChanges) {
                    val heights = e.segments.indices.joinToString(" → ") { e.elevationOf(it)?.toString() ?: "?" }
                    appendLine("    высоты участков от пола: $heights мм; перепады ${mm(e.risersLength)}")
                    e.fullLength?.let { appendLine("    длина с перепадами: ${mm(it)}") }
                }
                if (e.note.isNotBlank()) appendLine("    ${e.note}")
            }
            room.columns.forEach { c ->
                val title = MeasureFlow.columnTitle(room, c.id)
                appendLine("  • $title (${if (c.round) "круглая" else "прямоугольная"}): ${c.sizeText ?: "размер не измерен"}")
                MeasureFlow.columnDims(c).filter { it == ColumnDim.OFFSET_U || it == ColumnDim.OFFSET_V }.forEach { d ->
                    val label = MeasureFlow.label(room, MeasureTarget.ColumnValue(c.id, d)).substringAfter(": ")
                    val v = MeasureFlow.valueOf(room, MeasureTarget.ColumnValue(c.id, d))
                    appendLine("    $label: ${v.fmt()}")
                }
            }
            appendLine()
        }
        append("Итого площадь: ${m2(total)}")
    }

    private fun Measure?.fmt(): String = this?.let { "${mm(it.meters)} (${it.source.title})" } ?: "—"
    private fun m2(v: Double) = "%.2f м²".format(Locale.ROOT, v)

    /** Миллиметры с разделением разрядов: «3 456 мм». */
    fun mm(meters: Double): String {
        val v = Math.round(meters * 1000)
        val grouped = v.toString().reversed().chunked(3).joinToString(" ").reversed()
        return "$grouped мм"
    }
}
