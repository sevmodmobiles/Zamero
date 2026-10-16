package app.zamero.ui

import app.zamero.core.Measure
import java.util.Locale

internal fun fmtMeters(v: Double) = "%.3f м".format(Locale.ROOT, v)
internal fun fmtArea(v: Double) = "%.2f м²".format(Locale.ROOT, v)
internal fun fmtCm(v: Double) = "%.1f см".format(Locale.ROOT, v * 100)
internal fun Measure.display() = "${fmtMeters(meters)} · ${source.title}"

/** Разбирает ручной ввод: «3.45», «3,45» — метры; «345 см», «3450мм» тоже понимаем. */
internal fun parseManual(text: String): Double? {
    val t = text.trim().lowercase().replace(',', '.')
    val number = Regex("""^(\d+(?:\.\d+)?)\s*(мм|mm|см|cm|м|m)?$""").find(t) ?: return null
    val v = number.groupValues[1].toDoubleOrNull() ?: return null
    val meters = when (number.groupValues[2]) {
        "мм", "mm" -> v / 1000
        "см", "cm" -> v / 100
        else -> v
    }
    return meters.takeIf { it > 0 }
}
