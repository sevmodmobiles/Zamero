package app.zamero.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun ZameroApp(vm: MeasureViewModel = viewModel()) {
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
