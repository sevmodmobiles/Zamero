package app.zamero.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.zamero.core.Measure
import app.zamero.core.MeasureFlow
import app.zamero.core.Opening
import app.zamero.core.OpeningType
import app.zamero.core.Project
import app.zamero.core.Room
import app.zamero.core.Shape
import app.zamero.core.Source
import app.zamero.core.MeasureTarget
import app.zamero.data.ProjectStore
import app.zamero.laser.BleLaserClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.UUID

/** Последнее записанное значение — для подтверждения на экране. */
data class LastRecord(val label: String, val measure: Measure)

class MeasureViewModel(app: Application) : AndroidViewModel(app) {
    private val store = ProjectStore(app)
    val laser = BleLaserClient(app)

    var project by mutableStateOf(store.load() ?: Project(id = "main", name = "Мой объект"))
        private set
    var openRoomId by mutableStateOf<String?>(null)
        private set
    /** Поле, куда попадёт следующее значение (с рулетки, камеры или клавиатуры). */
    var focus by mutableStateOf<MeasureTarget?>(null)
        private set
    var lastRecord by mutableStateOf<LastRecord?>(null)
        private set

    val openRoom: Room? get() = project.rooms.firstOrNull { it.id == openRoomId }

    init {
        viewModelScope.launch {
            laser.readings.collect { record(it, Source.LASER) }
        }
    }

    /** Записать измерение в текущее поле и перейти к следующему пустому. */
    fun record(meters: Double, source: Source) {
        val room = openRoom ?: return
        val target = focus ?: MeasureFlow.nextEmpty(room) ?: return
        val measure = Measure(meters, source)
        val (updated, next) = MeasureFlow.record(room, target, measure)
        lastRecord = LastRecord(MeasureFlow.label(room, target), measure)
        replaceRoom(updated)
        focus = next
    }

    fun select(target: MeasureTarget) {
        focus = target
    }

    fun clear(target: MeasureTarget) {
        val room = openRoom ?: return
        replaceRoom(MeasureFlow.set(room, target, null))
        focus = target
    }

    fun addRoom() {
        val n = project.rooms.size + 1
        val room = Room(id = UUID.randomUUID().toString(), name = "Помещение $n")
        updateProject(project.copy(rooms = project.rooms + room))
        openRoom(room.id)
    }

    fun openRoom(id: String) {
        openRoomId = id
        lastRecord = null
        focus = openRoom?.let { MeasureFlow.nextEmpty(it) }
    }

    fun closeRoom() {
        openRoomId = null
        focus = null
    }

    fun deleteRoom(id: String) {
        updateProject(project.copy(rooms = project.rooms.filterNot { it.id == id }))
        if (openRoomId == id) closeRoom()
    }

    fun renameRoom(name: String) = editRoom { copy(name = name) }

    fun renameProject(name: String) = updateProject(project.copy(name = name))

    fun setShape(shape: Shape) {
        editRoom { withShape(shape) }
        focus = openRoom?.let { MeasureFlow.nextEmpty(it) }
    }

    fun addOpening(type: OpeningType) {
        editRoom { copy(openings = openings + Opening(type)) }
        val room = openRoom ?: return
        focus = MeasureTarget.OpeningWidth(room.openings.lastIndex)
    }

    fun removeOpening(index: Int) {
        editRoom { copy(openings = openings.filterIndexed { i, _ -> i != index }) }
        focus = openRoom?.let { MeasureFlow.nextEmpty(it) }
    }

    private fun editRoom(block: Room.() -> Room) {
        openRoom?.let { replaceRoom(it.block()) }
    }

    private fun replaceRoom(room: Room) {
        updateProject(project.copy(rooms = project.rooms.map { if (it.id == room.id) room else it }))
    }

    private fun updateProject(p: Project) {
        project = p
        viewModelScope.launch(Dispatchers.IO) { store.save(p) }
    }

    override fun onCleared() {
        laser.disconnect()
    }
}
