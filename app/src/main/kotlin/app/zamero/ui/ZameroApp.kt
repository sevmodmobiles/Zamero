package app.zamero.ui

import androidx.activity.compose.BackHandler
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun ZameroApp(vm: MeasureViewModel = viewModel()) {
    val context = LocalContext.current
    vm.updateMessage?.let { msg ->
        LaunchedEffect(msg) {
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            vm.clearUpdateMessage()
        }
    }
    // Новая версия — предлагаем на любом экране, пока не нажали «Позже».
    val rel = vm.update
    if (rel != null && vm.updatePostponed != rel.name) {
        val status = vm.updateStatus
        val busy = status != null && !status.startsWith("Ошибка")
        AlertDialog(
            onDismissRequest = vm::postponeUpdate,
            title = { Text("Доступна версия ${rel.name}") },
            text = {
                Column {
                    Text("Сейчас установлена ${vm.versionName}.")
                    if (rel.notes.isNotBlank()) Text(rel.notes, maxLines = 10)
                    if (status != null) Text(status)
                }
            },
            confirmButton = {
                TextButton(enabled = !busy, onClick = {
                    vm.dismissUpdateError()
                    vm.startUpdate()
                }) { Text(if (busy) status!! else "Обновить") }
            },
            dismissButton = { TextButton(onClick = vm::postponeUpdate) { Text("Позже") } },
        )
    }
    val project = vm.openProject
    val room = vm.openRoom
    when {
        project == null -> ProjectsScreen(vm)
        room == null -> {
            BackHandler { vm.closeProject() }
            ProjectScreen(vm, project)
        }
        else -> {
            BackHandler { vm.closeRoom() }
            RoomScreen(vm, room)
        }
    }
}
