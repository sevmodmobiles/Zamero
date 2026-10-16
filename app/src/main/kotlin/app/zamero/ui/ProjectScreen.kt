package app.zamero.ui

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.zamero.core.Geometry
import app.zamero.core.Report
import app.zamero.core.Room

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectScreen(vm: MeasureViewModel) {
    val context = LocalContext.current
    val project = vm.project
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Room?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(project.name, modifier = Modifier.clickable { renaming = true }) },
                actions = {
                    TextButton(
                        enabled = project.rooms.isNotEmpty(),
                        onClick = {
                            val send = Intent(Intent.ACTION_SEND)
                                .setType("text/plain")
                                .putExtra(Intent.EXTRA_SUBJECT, "Обмер: ${project.name}")
                                .putExtra(Intent.EXTRA_TEXT, Report.text(project))
                            context.startActivity(Intent.createChooser(send, "Отправить обмер"))
                        },
                    ) { Text("Отправить") }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = vm::addRoom) { Text("+ Помещение") }
        },
    ) { padding ->
        val total = project.rooms.sumOf { Geometry.stats(it).floorArea ?: 0.0 }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                if (project.rooms.isEmpty()) {
                    Text(
                        "Добавьте первое помещение. Значения можно вводить вручную, " +
                            "с Bluetooth-рулетки или камерой телефона.",
                    )
                } else {
                    Text("Общая площадь: ${fmtArea(total)}", style = MaterialTheme.typography.titleLarge)
                }
            }
            items(project.rooms, key = { it.id }) { room ->
                RoomCard(room, onOpen = { vm.openRoom(room.id) }, onDelete = { deleting = room })
            }
        }
    }

    if (renaming) {
        NameDialog("Название объекта", project.name, onDismiss = { renaming = false }) {
            vm.renameProject(it)
            renaming = false
        }
    }
    deleting?.let { room ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Удалить «${room.name}»?") },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteRoom(room.id)
                    deleting = null
                }) { Text("Удалить") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Отмена") } },
        )
    }
}

@Composable
private fun RoomCard(room: Room, onOpen: () -> Unit, onDelete: () -> Unit) {
    val stats = Geometry.stats(room)
    Card(Modifier.fillMaxWidth().clickable(onClick = onOpen)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(room.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    stats.floorArea?.let { "${fmtArea(it)} · ${room.shape.title.lowercase()}" } ?: "Обмер не закончен",
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (stats.needsRecheck) {
                    Text(
                        "Контур не сходится на ${fmtCm(stats.closureError!!)} — перемерьте",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            TextButton(onClick = onDelete) { Text("Удалить") }
        }
    }
}

@Composable
internal fun NameDialog(title: String, initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true) },
        confirmButton = {
            TextButton(onClick = { onSave(text.trim()) }, enabled = text.isNotBlank()) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}
