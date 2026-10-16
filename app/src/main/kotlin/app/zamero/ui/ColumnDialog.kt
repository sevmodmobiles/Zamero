package app.zamero.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.zamero.core.Column as ColumnModel
import app.zamero.core.MeasureFlow
import app.zamero.core.MeasureTarget
import app.zamero.core.Room

/** Новая колонна сразу после рисования: тип и размеры (можно ввести позже). */
@Composable
fun NewColumnDialog(
    detectedRound: Boolean,
    approxWidthM: Double,
    approxDepthM: Double,
    onSave: (round: Boolean, widthM: Double?, depthM: Double?) -> Unit,
    onDismiss: () -> Unit,
) {
    var round by remember { mutableStateOf(detectedRound) }
    var width by remember { mutableStateOf("") }
    var depth by remember { mutableStateOf("") }
    val numbers = KeyboardOptions(keyboardType = KeyboardType.Number)
    fun approx(m: Double) = "≈ ${Math.round(m * 100) * 10}"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Колонна") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !round, onClick = { round = false }, label = { Text("Прямоугольная") })
                    FilterChip(selected = round, onClick = { round = true }, label = { Text("Круглая") })
                }
                if (round) {
                    OutlinedTextField(
                        width, { width = it.filter(Char::isDigit).take(5) },
                        label = { Text("Диаметр") }, placeholder = { Text(approx((approxWidthM + approxDepthM) / 2)) },
                        suffix = { Text("мм") }, singleLine = true, keyboardOptions = numbers,
                    )
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            width, { width = it.filter(Char::isDigit).take(5) },
                            label = { Text("Ширина ↔") }, placeholder = { Text(approx(approxWidthM)) },
                            singleLine = true, keyboardOptions = numbers, modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            depth, { depth = it.filter(Char::isDigit).take(5) },
                            label = { Text("Глубина ↕") }, placeholder = { Text(approx(approxDepthM)) },
                            singleLine = true, keyboardOptions = numbers, modifier = Modifier.weight(1f),
                        )
                    }
                }
                Text(
                    "Через центр пройдут оси, параллельные стенам. Привязку осей к стенам " +
                        "можно измерить сразу или позже — нажатием на колонну.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(round, parseMm(width), if (round) null else parseMm(depth)) }) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

/** Карточка существующей колонны: тип, размеры и привязки — каждое значение меряется отдельно. */
@Composable
fun ColumnInfoDialog(
    room: Room,
    column: ColumnModel,
    onPick: (MeasureTarget) -> Unit,
    onRoundChange: (Boolean) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(MeasureFlow.columnTitle(room, column.id), color = ColumnColor) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !column.round, onClick = { onRoundChange(false) }, label = { Text("Прямоугольная") })
                    FilterChip(selected = column.round, onClick = { onRoundChange(true) }, label = { Text("Круглая") })
                }
                MeasureFlow.columnDims(column).forEach { dim ->
                    val t = MeasureTarget.ColumnValue(column.id, dim)
                    val value = MeasureFlow.valueOf(room, t)
                    Row(
                        Modifier.fillMaxWidth().clickable { onPick(t) }.padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            MeasureFlow.label(room, t).substringAfter(": "),
                            modifier = Modifier.weight(1f),
                            fontWeight = if (value == null) FontWeight.Bold else null,
                        )
                        Text(value?.display() ?: "ввести", color = if (value == null) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Unspecified)
                    }
                }
                Text(
                    "Привязка — расстояние от стены до оси колонны.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Готово") } },
        dismissButton = {
            TextButton(onClick = onDelete) { Text("Удалить", color = MaterialTheme.colorScheme.error) }
        },
    )
}
