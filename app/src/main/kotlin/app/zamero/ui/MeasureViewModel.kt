package app.zamero.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.zamero.core.Measure
import app.zamero.core.MeasureFlow
import app.zamero.core.MeasureTarget
import app.zamero.core.Opening
import app.zamero.core.OpeningType
import app.zamero.core.Point
import app.zamero.core.Project
import app.zamero.core.Room
import app.zamero.core.Source
import app.zamero.data.ProjectStore
import app.zamero.laser.BleLaserClient
import app.zamero.update.Updater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

/** Замер с дальномера; [seq] растёт с каждым новым замером, даже если значение то же. */
data class LaserReading(val meters: Double, val seq: Int)

class MeasureViewModel(private val app: Application) : AndroidViewModel(app) {
    private val store = ProjectStore(app)
    val laser = BleLaserClient(app)

    var projects by mutableStateOf(store.load().sortedByDescending { it.createdAt })
        private set
    var openProjectId by mutableStateOf<String?>(null)
        private set
    var openRoomId by mutableStateOf<String?>(null)
        private set
    /** Открытое окно ввода длины. */
    var dialogTarget by mutableStateOf<MeasureTarget?>(null)
        private set
    var lastLaser by mutableStateOf<LaserReading?>(null)
        private set

    var update by mutableStateOf<Updater.Release?>(null)
        private set
    var updateStatus by mutableStateOf<String?>(null)
        private set

    val lastContractor: String get() = store.lastContractor
    val openProject: Project? get() = projects.firstOrNull { it.id == openProjectId }
    val openRoom: Room? get() = openProject?.rooms?.firstOrNull { it.id == openRoomId }

    init {
        viewModelScope.launch {
            laser.readings.collect { lastLaser = LaserReading(it, (lastLaser?.seq ?: 0) + 1) }
        }
        checkUpdate()
    }

    // --- Объекты -------------------------------------------------------------

    fun createProject(address: String, customer: String, contractor: String) {
        val p = Project(
            id = UUID.randomUUID().toString(),
            address = address,
            customer = customer,
            contractor = contractor,
            createdAt = System.currentTimeMillis(),
        )
        store.lastContractor = contractor
        saveProjects(listOf(p) + projects)
        openProjectId = p.id
    }

    fun editProjectInfo(address: String, customer: String, contractor: String) {
        store.lastContractor = contractor
        editProject { copy(address = address, customer = customer, contractor = contractor) }
    }

    fun openProject(id: String) {
        openProjectId = id
        openRoomId = null
    }

    fun closeProject() {
        openProjectId = null
        openRoomId = null
    }

    fun deleteProject(id: String) {
        saveProjects(projects.filterNot { it.id == id })
        if (openProjectId == id) closeProject()
    }

    // --- Помещения -----------------------------------------------------------

    fun addRoom() {
        val project = openProject ?: return
        val room = Room(id = UUID.randomUUID().toString(), name = "Помещение ${project.rooms.size + 1}")
        editProject { copy(rooms = rooms + room) }
        openRoomId = room.id
    }

    fun openRoom(id: String) {
        openRoomId = id
    }

    fun closeRoom() {
        openRoomId = null
        dialogTarget = null
    }

    fun deleteRoom(id: String) {
        editProject { copy(rooms = rooms.filterNot { it.id == id }) }
        if (openRoomId == id) closeRoom()
    }

    fun renameRoom(name: String) = editRoom { copy(name = name) }

    /** Новый контур после рисования пальцем. */
    fun setOutline(points: List<Point>) = editRoom { withOutline(points) }

    // --- Замеры --------------------------------------------------------------

    fun openDialog(target: MeasureTarget) {
        dialogTarget = target
    }

    fun closeDialog() {
        dialogTarget = null
    }

    /**
     * Сохраняет значение (null — стереть). Если [goNext], сразу открывает окно
     * для следующего неизмеренного поля.
     */
    fun saveMeasure(target: MeasureTarget, meters: Double?, source: Source, goNext: Boolean) {
        val room = openRoom ?: return
        val updated = MeasureFlow.set(room, target, meters?.let { Measure(it, source) })
        replaceRoom(updated)
        dialogTarget = if (goNext) MeasureFlow.nextEmpty(updated, target) else null
    }

    fun addOpening(type: OpeningType) {
        editRoom { copy(openings = openings + Opening(type)) }
        val room = openRoom ?: return
        dialogTarget = MeasureTarget.OpeningWidth(room.openings.lastIndex)
    }

    fun removeOpening(index: Int) {
        dialogTarget = null
        editRoom { copy(openings = openings.filterIndexed { i, _ -> i != index }) }
    }

    // --- Обновление ----------------------------------------------------------

    fun checkUpdate() {
        viewModelScope.launch {
            update = withContext(Dispatchers.IO) { runCatching { Updater.checkForUpdate(app) }.getOrNull() }
        }
    }

    fun startUpdate() {
        val rel = update ?: return
        if (updateStatus != null) return
        if (!Updater.ensureInstallPermission(app)) {
            updateStatus = null
            return
        }
        updateStatus = "Скачивание…"
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val apk = Updater.download(app, rel) { pct -> updateStatus = "Скачивание… $pct%" }
                    updateStatus = "Установка…"
                    Updater.install(app, apk)
                }
            }
            updateStatus = result.exceptionOrNull()?.let { "Ошибка обновления: ${it.message}" }
        }
    }

    fun dismissUpdateError() {
        updateStatus = null
    }

    // --- Внутреннее ----------------------------------------------------------

    private fun editProject(block: Project.() -> Project) {
        val id = openProjectId ?: return
        saveProjects(projects.map { if (it.id == id) it.block() else it })
    }

    private fun editRoom(block: Room.() -> Room) {
        openRoom?.let { replaceRoom(it.block()) }
    }

    private fun replaceRoom(room: Room) {
        editProject { copy(rooms = rooms.map { if (it.id == room.id) room else it }) }
    }

    private fun saveProjects(list: List<Project>) {
        projects = list
        viewModelScope.launch(Dispatchers.IO) { store.save(list) }
    }

    override fun onCleared() {
        laser.disconnect()
    }
}
