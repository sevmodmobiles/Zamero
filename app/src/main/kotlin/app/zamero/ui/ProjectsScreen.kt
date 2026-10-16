package app.zamero.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.zamero.core.Geometry
import app.zamero.core.Project
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectsScreen(vm: MeasureViewModel) {
    var creating by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Zamero") },
                actions = {
                    // Нажатие на номер версии — проверить обновления вручную.
                    TextButton(onClick = { vm.checkUpdate(manual = true) }) { Text("v${vm.versionName}") }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { creating = true }) { Text("Новый объект +") }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { UpdateBanner(vm) }
            if (vm.projects.isEmpty()) {
                item {
                    Text(
                        "Объектов пока нет. Нажмите «Новый объект +», укажите адрес, заказчика " +
                            "и исполнителя — и начинайте рисовать помещения.",
                    )
                }
            }
            items(vm.projects, key = { it.id }) { p ->
                ProjectCard(p, onClick = { vm.openProject(p.id) })
            }
        }
    }

    if (creating) {
        ProjectInfoDialog(
            title = "Новый объект",
            initial = Project(id = "", address = "", contractor = vm.lastContractor),
            onDismiss = { creating = false },
            onSave = { address, customer, contractor ->
                vm.createProject(address, customer, contractor)
                creating = false
            },
        )
    }
}

@Composable
private fun ProjectCard(p: Project, onClick: () -> Unit) {
    val area = p.rooms.sumOf { Geometry.stats(it).floorArea ?: 0.0 }
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(p.address, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (p.customer.isNotBlank()) Text("Заказчик: ${p.customer}", style = MaterialTheme.typography.bodyMedium)
            Text(
                buildString {
                    if (p.createdAt > 0) append(formatDate(p.createdAt)).append(" · ")
                    append("помещений: ${p.rooms.size}")
                    if (area > 0) append(" · ${fmtArea(area)}")
                },
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun UpdateBanner(vm: MeasureViewModel) {
    val rel = vm.update ?: return
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Доступна версия ${rel.name}", style = MaterialTheme.typography.titleMedium)
            if (rel.notes.isNotBlank()) Text(rel.notes, style = MaterialTheme.typography.bodySmall, maxLines = 6, overflow = TextOverflow.Ellipsis)
            val status = vm.updateStatus
            if (status != null && status.startsWith("Ошибка")) {
                Text(status, color = MaterialTheme.colorScheme.error)
                TextButton(onClick = vm::dismissUpdateError) { Text("Понятно") }
            } else {
                Button(onClick = vm::startUpdate, enabled = status == null) { Text(status ?: "Обновить до ${rel.name}") }
            }
        }
    }
}

/** Карточка объекта: адрес, заказчик, исполнитель. */
@Composable
fun ProjectInfoDialog(
    title: String,
    initial: Project,
    onDismiss: () -> Unit,
    onSave: (address: String, customer: String, contractor: String) -> Unit,
) {
    var address by remember { mutableStateOf(initial.address) }
    var customer by remember { mutableStateOf(initial.customer) }
    var contractor by remember { mutableStateOf(initial.contractor) }
    val caps = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(address, { address = it }, label = { Text("Адрес") }, keyboardOptions = caps)
                OutlinedTextField(customer, { customer = it }, label = { Text("Заказчик") }, singleLine = true, keyboardOptions = caps)
                OutlinedTextField(contractor, { contractor = it }, label = { Text("Исполнитель") }, singleLine = true, keyboardOptions = caps)
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(address.trim(), customer.trim(), contractor.trim()) },
                enabled = address.isNotBlank(),
            ) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

internal fun formatDate(millis: Long): String = SimpleDateFormat("dd.MM.yyyy", Locale.ROOT).format(Date(millis))
