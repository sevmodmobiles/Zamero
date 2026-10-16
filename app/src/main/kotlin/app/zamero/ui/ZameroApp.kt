package app.zamero.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun ZameroApp(vm: MeasureViewModel = viewModel()) {
    val room = vm.openRoom
    if (room == null) {
        ProjectScreen(vm)
    } else {
        BackHandler { vm.closeRoom() }
        RoomScreen(vm, room)
    }
}
