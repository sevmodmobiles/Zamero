package app.zamero.ui

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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.zamero.core.Category
import app.zamero.core.Geometry
import app.zamero.core.MeasureFlow
import app.zamero.core.Project
import app.zamero.core.Room

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectScreen(vm: MeasureViewModel, project: Project) {
    val context = LocalContext.current
    var editing by remember { mutableStateOf(false) }
    var deletingRoom by remember { mutableStateOf<Room?>(null) }
    var deletingProject by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { TextButton(onClick = vm::closeProject) { Text("Назад") } },
                title = { Text(project.address, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                actions = {
                    TextButton(onClick = { Export.shareDxf(context, project) }) { Text("DXF") }
                    TextButton(onClick = { Export.shareText(context, project) }) { Text("Отчёт") }
                },
            )
        },
        floatingActionButton = {
            if (!vm.engineeringStage) {
                ExtendedFloatingActionButton(onClick = vm::addRoom) { Text("Помещение +") }
            }
        },
    ) { padding ->
        val total = project.rooms.sumOf { Geometry.stats(it).floorArea ?: 0.0 }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Card(Modifier.fillMaxWidth().clickable { editing = true }) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(project.address, style = MaterialTheme.typography.titleMedium)
                        Text("Заказчик: ${project.customer.ifBlank { "—" }}")
                        Text("Исполнитель: ${project.contractor.ifBlank { "—" }}")
                        if (project.createdAt > 0) Text("Дата: ${formatDate(project.createdAt)}", style = MaterialTheme.typography.bodySmall)
                        Text("Нажмите, чтобы изменить", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            item {
                // Этапы: сначала рисуем и меряем все помещения, потом прокладываем инженерию.
                val drawn = project.rooms.count { it.hasOutline }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !vm.engineeringStage,
                        onClick = { vm.setStage(false) },
                        label = { Text("1. Помещения") },
                    )
                    FilterChip(
                        selected = vm.engineeringStage,
                        onClick = { vm.setStage(true) },
                        enabled = drawn > 0,
                        label = { Text("2. Инженерия") },
                    )
                }
            }
            item {
                val drawn = project.rooms.count { it.hasOutline }
                when {
                    project.rooms.isEmpty() -> Text("Добавьте помещение и нарисуйте его контур пальцем.")
                    vm.engineeringStage && drawn < project.rooms.size -> Text(
                        "Нарисовано помещений: $drawn из ${project.rooms.size}. Инженерию можно прокладывать " +
                            "в нарисованных; остальные сначала нарисуйте на этапе «Помещения».",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    vm.engineeringStage -> Text("Выберите помещение, чтобы проложить трассы.", style = MaterialTheme.typography.bodyMedium)
                    else -> Text("Общая площадь: ${fmtArea(total)}", style = MaterialTheme.typography.titleLarge)
                }
            }
            items(project.rooms, key = { it.id }) { room ->
                if (vm.engineeringStage) {
                    EngRoomCard(room, onOpen = { vm.openRoom(room.id) })
                } else {
                    RoomCard(room, onOpen = { vm.openRoom(room.id) }, onDelete = { deletingRoom = room })
                }
            }
            item {
                OutlinedButton(onClick = { deletingProject = true }) { Text("Удалить объект") }
            }
        }
    }

    if (editing) {
        ProjectInfoDialog(
            title = "Объект",
            initial = project,
            onDismiss = { editing = false },
            onSave = { address, customer, contractor ->
                vm.editProjectInfo(address, customer, contractor)
                editing = false
            },
        )
    }
    deletingRoom?.let { room ->
        ConfirmDialog("Удалить «${room.name}»?", onDismiss = { deletingRoom = null }) {
            vm.deleteRoom(room.id)
            deletingRoom = null
        }
    }
    if (deletingProject) {
        ConfirmDialog("Удалить объект со всеми помещениями?", onDismiss = { deletingProject = false }) {
            vm.deleteProject(project.id)
            deletingProject = false
        }
    }
}

@Composable
private fun RoomCard(room: Room, onOpen: () -> Unit, onDelete: () -> Unit) {
    val stats = Geometry.stats(room)
    val measured = room.walls.count { it != null }
    Card(Modifier.fillMaxWidth().clickable(onClick = onOpen)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(room.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    if (!room.hasOutline) {
                        "Контур не нарисован"
                    } else {
                        stats.floorArea?.let(::fmtArea) ?: "Измерено стен: $measured из ${room.walls.size}"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (stats.needsRecheck) {
                    Text(
                        "Контур не сходится на ${fmtMm(stats.closureError!!)} — перемерьте",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            TextButton(onClick = onDelete) { Text("Удалить") }
        }
    }
}

/** Помещение на этапе «Инженерия»: сколько трасс и что осталось измерить. */
@Composable
private fun EngRoomCard(room: Room, onOpen: () -> Unit) {
    val lines = room.engineering.filter { it.type.category == Category.ENGINEERING }
    val left = MeasureFlow.targets(room, engineering = true).count { MeasureFlow.valueOf(room, it) == null }
    Card(Modifier.fillMaxWidth().clickable(enabled = room.hasOutline, onClick = onOpen)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(room.name, style = MaterialTheme.typography.titleMedium)
            Text(
                when {
                    !room.hasOutline -> "Контур не нарисован"
                    lines.isEmpty() -> "Трасс пока нет"
                    left > 0 -> "Трасс: ${lines.size} · не измерено участков: $left"
                    else -> "Трасс: ${lines.size} · всё измерено"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (room.hasOutline) Color.Unspecified else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun ConfirmDialog(title: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Удалить") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

@Composable
internal fun NameDialog(title: String, initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { androidx.compose.material3.OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true) },
        confirmButton = {
            TextButton(onClick = { onSave(text.trim()) }, enabled = text.isNotBlank()) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}
