package app.zamero.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.zamero.core.Column
import app.zamero.core.Edit
import app.zamero.core.EngElement
import app.zamero.core.EngType
import app.zamero.core.Measure
import app.zamero.core.MeasureFlow
import app.zamero.core.MeasureTarget
import app.zamero.core.Opening
import app.zamero.core.OpeningType
import app.zamero.core.Point
import app.zamero.core.PointRef
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
    /** Этап работы с объектом: сначала помещения, потом инженерия. */
    var engineeringStage by mutableStateOf(false)
        private set

    /** Открытое окно ввода длины. */
    var dialogTarget by mutableStateOf<MeasureTarget?>(null)
        private set
    var lastLaser by mutableStateOf<LaserReading?>(null)
        private set
    /** Элемент инженерии, для которого сейчас рисуется трасса. */
    var routingElementId by mutableStateOf<String?>(null)
        private set

    /** История для «Отменить»: предыдущие состояния всех объектов. */
    private val history = ArrayDeque<List<Project>>()
    private val future = ArrayDeque<List<Project>>()
    var canUndo by mutableStateOf(false)
        private set
    var canRedo by mutableStateOf(false)
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
        // Обновления проверяет MainActivity.onResume — и при запуске, и при каждом возвращении.
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
        engineeringStage = false
    }

    fun setStage(engineering: Boolean) {
        engineeringStage = engineering
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
        cancelRouting()
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
        dialogTarget = if (goNext) MeasureFlow.nextEmpty(updated, target, engineeringStage) else null
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

    // --- Инженерия ------------------------------------------------------------

    /** Добавляет элемент и сразу переходит к рисованию его трассы. */
    fun addEngineering(type: EngType, params: EngParams) {
        val e = params.applyTo(EngElement(id = UUID.randomUUID().toString(), type = type))
        editRoom { copy(engineering = engineering + e) }
        routingElementId = e.id
    }

    /** Дорисованный элемент: трасса уже есть (координаты эскиза). */
    fun addElement(type: EngType, params: EngParams, route: List<Point>) {
        val e = params.applyTo(EngElement(id = UUID.randomUUID().toString(), type = type)).withRoute(route)
        editRoom { copy(engineering = engineering + e) }
    }

    fun editEngineering(id: String, params: EngParams) = editElement(id) { params.applyTo(this) }

    fun deleteEngineering(id: String) {
        if (routingElementId == id) routingElementId = null
        val target = dialogTarget
        if (target is MeasureTarget.EngSegment && target.elementId == id) dialogTarget = null
        editRoom { copy(engineering = engineering.filterNot { it.id == id }) }
    }

    fun startRouting(id: String) {
        routingElementId = id
    }

    fun cancelRouting() {
        val id = routingElementId ?: return
        routingElementId = null
        // Элемент без трассы, который только что добавили, не нужен.
        if (openRoom?.engineering?.firstOrNull { it.id == id }?.hasRoute == false) deleteEngineering(id)
    }

    /** Трасса в координатах эскиза помещения. */
    fun setRoute(id: String, points: List<Point>) {
        editElement(id) { withRoute(points) }
        routingElementId = null
    }

    private fun editElement(id: String, block: EngElement.() -> EngElement) =
        editRoom { copy(engineering = engineering.map { if (it.id == id) it.block() else it }) }

    // --- Колонны --------------------------------------------------------------

    fun addColumn(column: Column) = editRoom { copy(columns = columns + column) }

    fun setColumnRound(id: String, round: Boolean) = editRoom {
        copy(columns = columns.map { if (it.id == id) it.copy(round = round, depth = if (round) null else it.depth) else it })
    }

    fun deleteColumn(id: String) {
        val target = dialogTarget
        if (target is MeasureTarget.ColumnValue && target.columnId == id) dialogTarget = null
        editRoom { copy(columns = columns.filterNot { it.id == id }) }
    }

    // --- Отмена --------------------------------------------------------------

    fun undo() {
        val previous = history.removeLastOrNull() ?: return
        future.addLast(projects)
        restore(previous)
    }

    fun redo() {
        val next = future.removeLastOrNull() ?: return
        history.addLast(projects)
        restore(next)
    }

    private fun restore(list: List<Project>) {
        canUndo = history.isNotEmpty()
        canRedo = future.isNotEmpty()
        projects = list
        dialogTarget = null
        routingElementId = null
        if (openProject == null) closeProject()
        viewModelScope.launch(Dispatchers.IO) { store.save(list) }
    }

    // --- Правка точек --------------------------------------------------------

    /** Перетаскивание активной точки (координаты плана, м). */
    fun movePoint(ref: PointRef, target: Point, snap: Double) = editRoom { Edit.movePoint(this, ref, target, snap) }

    fun deletePoint(ref: PointRef) = editRoom { Edit.deletePoint(Edit.rebase(this), ref) }

    // --- Обновление ----------------------------------------------------------

    val versionName: String = runCatching { Updater.currentVersionName(app) }.getOrDefault("?")
    /** Окно «Доступна версия» уже закрыли «Позже» для этой версии. */
    var updatePostponed by mutableStateOf<String?>(null)
        private set
    /** Результат ручной проверки — показывается всплывающим сообщением. */
    var updateMessage by mutableStateOf<String?>(null)
        private set
    private var lastCheck = 0L

    /**
     * Проверка обновлений. Автоматически — при каждом возврате в приложение
     * (не чаще раза в 2 минуты), вручную — по нажатию на номер версии.
     */
    fun checkUpdate(manual: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!manual && now - lastCheck < 2 * 60_000) return
        lastCheck = now
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { Updater.checkForUpdate(app) } }
            result.onSuccess { update = it }
            if (manual) {
                updateMessage = result.fold(
                    onSuccess = { rel -> if (rel == null) "Установлена последняя версия $versionName" else null },
                    onFailure = { "Не удалось проверить обновления: ${it.message}" },
                )
            }
        }
    }

    fun postponeUpdate() {
        updatePostponed = update?.name
    }

    fun clearUpdateMessage() {
        updateMessage = null
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
        history.addLast(projects)
        if (history.size > HISTORY_SIZE) history.removeFirst()
        future.clear()
        canUndo = true
        canRedo = false
        projects = list
        viewModelScope.launch(Dispatchers.IO) { store.save(list) }
    }

    override fun onCleared() {
        laser.disconnect()
    }

    private companion object {
        const val HISTORY_SIZE = 50
    }
}

/** Параметры инженерной линии из окна ввода. */
data class EngParams(
    val diameterMm: Int?,
    val widthMm: Int?,
    val heightMm: Int?,
    val elevationMm: Int?,
    val note: String,
) {
    fun applyTo(e: EngElement) = e.copy(
        diameterMm = diameterMm,
        widthMm = widthMm,
        heightMm = heightMm,
        elevationMm = elevationMm,
        note = note,
    )
}
