package app.zamero.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.zamero.core.EngElement
import app.zamero.core.EngType
import app.zamero.core.Section

/** Типовые диаметры, мм — выбираются одним касанием. */
private fun commonDiameters(type: EngType): List<Int> = when (type) {
    EngType.PIPE -> listOf(16, 20, 25, 32, 40, 50, 110)
    EngType.DRAIN -> listOf(16, 20, 32, 40, 50)
    EngType.DUCT_ROUND -> listOf(100, 125, 160, 200, 250, 315, 400)
    else -> emptyList()
}

private fun commonRect(type: EngType): List<Pair<Int, Int>> = when (type) {
    EngType.TRAY -> listOf(100 to 50, 200 to 50, 200 to 80, 300 to 80, 400 to 100)
    EngType.DUCT_RECT -> listOf(200 to 100, 300 to 150, 400 to 200, 500 to 250, 600 to 300)
    else -> emptyList()
}

/** Сечение, отметка и комментарий инженерной линии. */
@Composable
fun EngParamsDialog(
    type: EngType,
    initial: EngElement?,
    onSave: (EngParams) -> Unit,
    onDismiss: () -> Unit,
) {
    var diameter by remember { mutableStateOf(initial?.diameterMm?.toString() ?: "") }
    var width by remember { mutableStateOf(initial?.widthMm?.toString() ?: "") }
    var height by remember { mutableStateOf(initial?.heightMm?.toString() ?: "") }
    var elevation by remember { mutableStateOf(initial?.elevationMm?.toString() ?: "") }
    var note by remember { mutableStateOf(initial?.note ?: "") }
    val numbers = KeyboardOptions(keyboardType = KeyboardType.Number)
    fun digits(v: String) = v.filter { it.isDigit() }.take(5)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(18.dp).background(engColor(type), CircleShape))
                Text(type.title)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (type.section == Section.ROUND) {
                    OutlinedTextField(
                        diameter, { diameter = digits(it) },
                        label = { Text("Диаметр") }, suffix = { Text("мм") },
                        singleLine = true, keyboardOptions = numbers,
                    )
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        commonDiameters(type).forEach { d ->
                            FilterChip(selected = diameter == d.toString(), onClick = { diameter = d.toString() }, label = { Text("Ø$d") })
                        }
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            width, { width = digits(it) }, label = { Text("Ширина") }, singleLine = true,
                            keyboardOptions = numbers, modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            height, { height = digits(it) }, label = { Text("Высота") }, singleLine = true,
                            keyboardOptions = numbers, modifier = Modifier.weight(1f),
                        )
                    }
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        commonRect(type).forEach { (w, h) ->
                            FilterChip(
                                selected = width == w.toString() && height == h.toString(),
                                onClick = { width = w.toString(); height = h.toString() },
                                label = { Text("${w}×$h") },
                            )
                        }
                    }
                }
                OutlinedTextField(
                    elevation, { elevation = digits(it) },
                    label = { Text("Отметка от пола") }, suffix = { Text("мм") },
                    singleLine = true, keyboardOptions = numbers,
                )
                OutlinedTextField(note, { note = it }, label = { Text("Комментарий") })
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(
                    EngParams(
                        diameterMm = diameter.toIntOrNull(),
                        widthMm = width.toIntOrNull(),
                        heightMm = height.toIntOrNull(),
                        elevationMm = elevation.toIntOrNull(),
                        note = note.trim(),
                    ),
                )
            }) { Text(if (initial == null) "Далее: трасса" else "Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}
