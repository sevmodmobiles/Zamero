package app.zamero.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.zamero.ar.ArMeasureActivity
import app.zamero.core.Measure
import app.zamero.core.Source

/**
 * Окно ввода длины в миллиметрах. Если подключён дальномер — показывает последний
 * замер, а новый замер, сделанный при открытом окне, подставляется сам.
 */
@Composable
fun MeasureDialog(
    title: String,
    color: Color?,
    current: Measure?,
    laserConnected: Boolean,
    lastLaser: LaserReading?,
    hasNext: Boolean,
    onSave: (meters: Double?, source: Source, goNext: Boolean) -> Unit,
    onDismiss: () -> Unit,
    /** Подпись кнопки сброса значения. */
    clearLabel: String = "Стереть",
    /** Подсказка под полем. */
    hint: String? = null,
) {
    val openedSeq = remember { lastLaser?.seq ?: 0 }
    var text by remember { mutableStateOf(current?.millimeters?.toString() ?: "") }
    var source by remember { mutableStateOf(current?.source ?: Source.MANUAL) }

    fun put(meters: Double, from: Source) {
        text = Math.round(meters * 1000).toString()
        source = from
    }

    // Замер, сделанный кнопкой дальномера при открытом окне, сразу попадает в поле.
    LaunchedEffect(lastLaser?.seq) {
        val r = lastLaser
        if (r != null && r.seq > openedSeq) put(r.meters, Source.LASER)
    }
    val camera = rememberLauncherForActivityResult(ArMeasureActivity.Contract()) { meters ->
        meters?.let { put(it, Source.CAMERA) }
    }
    val value = parseMm(text)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (color != null) Box(Modifier.size(18.dp).background(color, CircleShape))
                Text(title)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { v ->
                        text = v.filter { it.isDigit() }.take(6)
                        source = Source.MANUAL
                    },
                    label = { Text(if (hint == null) "Длина" else "Высота") },
                    supportingText = if (hint != null) {
                        { Text(hint) }
                    } else {
                        null
                    },
                    suffix = { Text("мм") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                if (laserConnected) {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                        Row(
                            Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("Дальномер", style = MaterialTheme.typography.labelMedium)
                                Text(
                                    lastLaser?.let { fmtMm(it.meters) } ?: "нажмите кнопку замера",
                                    style = MaterialTheme.typography.titleLarge,
                                )
                            }
                            if (lastLaser != null) {
                                TextButton(onClick = { put(lastLaser.meters, Source.LASER) }) { Text("Вставить") }
                            }
                        }
                    }
                } else {
                    Text("Дальномер не подключён", style = MaterialTheme.typography.bodySmall)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { camera.launch(Unit) }) { Text("Камерой ≈") }
                    if (current != null) {
                        TextButton(onClick = { onSave(null, Source.MANUAL, false) }) {
                            Text(clearLabel, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row {
                TextButton(onClick = { onSave(value, source, false) }, enabled = value != null) { Text("Сохранить") }
                if (hasNext) {
                    TextButton(onClick = { onSave(value, source, true) }, enabled = value != null) { Text("Далее →") }
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}
