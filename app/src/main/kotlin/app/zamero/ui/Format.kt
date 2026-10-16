package app.zamero.ui

import androidx.compose.ui.graphics.Color
import app.zamero.core.Measure
import app.zamero.core.Report
import java.util.Locale

internal fun fmtMm(meters: Double) = Report.mm(meters)
internal fun fmtArea(v: Double) = "%.2f м²".format(Locale.ROOT, v)
internal fun Measure.display() = "${fmtMm(meters)} · ${source.title}"

/** Цвета стен: соседние стены всегда разного цвета, хорошо различимы на белом и тёмном фоне. */
internal val WallColors = listOf(
    Color(0xFFE53935), // красный
    Color(0xFF1E88E5), // синий
    Color(0xFF43A047), // зелёный
    Color(0xFFFB8C00), // оранжевый
    Color(0xFF8E24AA), // фиолетовый
    Color(0xFF00ACC1), // бирюзовый
    Color(0xFF6D4C41), // коричневый
    Color(0xFFD81B60), // малиновый
    Color(0xFF7CB342), // салатовый
    Color(0xFF3949AB), // индиго
)

internal fun wallColor(index: Int, count: Int): Color {
    val c = index % WallColors.size
    // Последняя стена соседствует с первой — не даём им совпасть по цвету
    // (предпоследняя при этом последняя в палитре, так что второй цвет свободен).
    return if (index == count - 1 && count > 1 && c == 0) WallColors[1] else WallColors[c]
}

/** Разбирает ввод в миллиметрах: «3456», «3 456». */
internal fun parseMm(text: String): Double? =
    text.filter { it.isDigit() }.toLongOrNull()?.takeIf { it in 1..300_000 }?.let { it / 1000.0 }
