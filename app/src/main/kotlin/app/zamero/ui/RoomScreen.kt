package app.zamero.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.zamero.core.Category
import app.zamero.core.Column as ColumnModel
import app.zamero.core.ColumnDim
import app.zamero.core.Columns
import app.zamero.core.Measure
import app.zamero.core.Source
import app.zamero.core.EngElement
import app.zamero.core.EngType
import app.zamero.core.Geometry
import app.zamero.core.MeasureFlow
import app.zamero.core.MeasureTarget
import app.zamero.core.OpeningType
import app.zamero.core.Outlines
import app.zamero.core.Plan
import app.zamero.core.Point
import app.zamero.core.Snap
import app.zamero.core.Room
import app.zamero.core.Straighten
import app.zamero.laser.LaserState
import java.util.Locale
import java.util.UUID
import kotlin.math.atan2

/** Окно параметров инженерии: новый элемент выбранного типа или правка существующего. */
private data class EngEdit(val type: EngType, val element: EngElement?)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoomScreen(vm: MeasureViewModel, room: Room) {
    val context = LocalContext.current
    var renaming by remember { mutableStateOf(false) }
    var laserDialog by remember { mutableStateOf(false) }
    var engEdit by remember { mutableStateOf<EngEdit?>(null) }
    var redrawing by remember(room.id) { mutableStateOf(false) }
    var specOpen by remember { mutableStateOf(false) }
    // «Дорисовать»: рисуем линию, потом выбираем, что это, потом параметры.
    var drawingExtra by remember(room.id) { mutableStateOf(false) }
    var extraRoute by remember(room.id) { mutableStateOf<List<Point>?>(null) }
    var extraType by remember(room.id) { mutableStateOf<EngType?>(null) }
    // Колонна, распознанная в рисунке (координаты плана, м), и открытая колонна.
    var newColumn by remember(room.id) { mutableStateOf<Columns.Detected?>(null) }
    var openColumnId by remember(room.id) { mutableStateOf<String?>(null) }
    val laserState by vm.laser.state.collectAsState()
    val laserConnected = laserState is LaserState.Connected
    val target = vm.dialogTarget
    val routing = vm.routingElementId?.let { id -> room.engineering.firstOrNull { it.id == id } }
    val eng = vm.engineeringStage

    val mode = when {
        !eng && (redrawing || !room.hasOutline) -> CanvasMode.DRAW_OUTLINE
        routing != null -> CanvasMode.DRAW_ROUTE
        drawingExtra -> CanvasMode.DRAW_EXTRA
        else -> CanvasMode.VIEW
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { TextButton(onClick = vm::closeRoom) { Text("Назад") } },
                title = {
                    Text(
                        if (eng) "Инженерия · ${room.name}" else room.name,
                        maxLines = 1,
                        modifier = Modifier.clickable { renaming = true },
                    )
                },
                actions = {
                    if (vm.canUndo) TextButton(onClick = vm::undo) { Text("↶") }
                    TextButton(onClick = { laserDialog = true }) {
                        Text(if (laserConnected) "Дальномер ✓" else "Дальномер")
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Text(
                when (mode) {
                    CanvasMode.DRAW_OUTLINE -> "Нарисуйте контур одной линией — она выпрямится сама"
                    CanvasMode.DRAW_ROUTE -> "Проведите трассу «${routing?.label}» поверх плана"
                    CanvasMode.DRAW_EXTRA -> if (eng) "Проведите трассу пальцем — она выровняется вдоль стен" else "Дорисуйте стену или перегородку; обведите колонну"
                    CanvasMode.VIEW -> summary(room, eng)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            PlanCanvas(
                room = room,
                mode = mode,
                selected = target,
                onOutline = { stroke ->
                    val poly = Straighten.polygon(stroke)
                    if (poly == null) {
                        Toast.makeText(context, "Не получилось распознать контур — нарисуйте замкнутую фигуру крупнее", Toast.LENGTH_SHORT).show()
                    } else {
                        vm.setOutline(poly)
                        redrawing = false
                    }
                },
                onRoute = { planStroke ->
                    val plan = Plan.room(room) ?: return@PlanCanvas
                    // Маленькая замкнутая фигура в режиме «Дорисовать» — это колонна.
                    if (routing == null && !eng) {
                        val col = Columns.detect(planStroke, Columns.baseAngle(plan))
                        if (col != null && maxOf(col.width, col.depth) < planDiagonal(plan) * 0.35) {
                            drawingExtra = false
                            newColumn = col
                            return@PlanCanvas
                        }
                    }
                    val line = straightenAndSnap(room, plan, planStroke, excludeId = routing?.id)
                    if (line == null) {
                        Toast.makeText(context, "Линия слишком короткая — проведите длиннее", Toast.LENGTH_SHORT).show()
                        return@PlanCanvas
                    }
                    val sketch = line.map { Plan.toSketch(room, plan, it) }
                    if (routing != null) {
                        vm.setRoute(routing.id, sketch)
                    } else {
                        drawingExtra = false
                        extraRoute = sketch
                    }
                },
                onTap = vm::openDialog,
                onColumnTap = { openColumnId = it },
                engineeringStage = eng,
                modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 8.dp),
            )
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                when (mode) {
                    CanvasMode.DRAW_OUTLINE -> {
                        OutlinedButton(onClick = { vm.setOutline(Outlines.RECTANGLE); redrawing = false }) { Text("Прямоугольник") }
                        OutlinedButton(onClick = { vm.setOutline(Outlines.L_SHAPE); redrawing = false }) { Text("Г-образная") }
                        if (room.hasOutline) TextButton(onClick = { redrawing = false }) { Text("Отмена") }
                    }
                    CanvasMode.DRAW_ROUTE -> TextButton(onClick = vm::cancelRouting) { Text("Отмена") }
                    CanvasMode.DRAW_EXTRA -> TextButton(onClick = { drawingExtra = false }) { Text("Отмена") }
                    CanvasMode.VIEW -> {
                        FilledTonalButton(onClick = { drawingExtra = true }) { Text(if (eng) "Проложить трассу" else "Дорисовать") }
                        OutlinedButton(onClick = { specOpen = true }) { Text("Спецификация ▴") }
                        MeasureFlow.nextEmpty(room, engineering = eng)?.let { next ->
                            OutlinedButton(onClick = { vm.openDialog(next) }) { Text("Мерить по порядку") }
                        }
                        if (!eng) OutlinedButton(onClick = { redrawing = true }) { Text("Перерисовать") }
                    }
                }
            }
        }
    }

    if (specOpen && room.hasOutline) {
        ModalBottomSheet(onDismissRequest = { specOpen = false }) {
            Text(
                "Спецификация",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            MeasureList(
                vm,
                room,
                engineering = eng,
                onEditEng = { engEdit = EngEdit(it.type, it); specOpen = false },
                onPick = { t -> specOpen = false; vm.openDialog(t) },
                onStartRoute = { e -> specOpen = false; vm.startRouting(e.id) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    if (target != null) {
        key(target) {
            val after = MeasureFlow.nextEmpty(room, target, eng)
            MeasureDialog(
                title = MeasureFlow.label(room, target),
                color = targetColor(room, target),
                current = MeasureFlow.valueOf(room, target),
                laserConnected = laserConnected,
                lastLaser = vm.lastLaser,
                hasNext = after != null && after != target,
                onSave = { meters, source, goNext -> vm.saveMeasure(target, meters, source, goNext) },
                onDismiss = vm::closeDialog,
            )
        }
    }
    engEdit?.let { edit ->
        EngParamsDialog(
            type = edit.type,
            initial = edit.element,
            confirmText = if (edit.element == null) "Далее: трасса" else "Сохранить",
            onSave = { params ->
                if (edit.element == null) vm.addEngineering(edit.type, params) else vm.editEngineering(edit.element.id, params)
                engEdit = null
            },
            onDismiss = { engEdit = null },
        )
    }
    newColumn?.let { det ->
        NewColumnDialog(
            detectedRound = det.round,
            approxWidthM = det.width,
            approxDepthM = det.depth,
            onSave = { round, w, d ->
                Plan.room(room)?.let { plan ->
                    val (refU, refV) = Columns.referenceWalls(plan, det.center)
                    vm.addColumn(
                        ColumnModel(
                            id = UUID.randomUUID().toString(),
                            round = round,
                            center = Plan.toSketch(room, plan, det.center),
                            approxWidth = det.width,
                            approxDepth = if (round) det.width else det.depth,
                            width = w?.let { Measure(it, Source.MANUAL) },
                            depth = d?.let { Measure(it, Source.MANUAL) },
                            refWallU = refU,
                            refWallV = refV,
                        ),
                    )
                }
                newColumn = null
            },
            onDismiss = { newColumn = null },
        )
    }
    openColumnId?.let { id ->
        val col = room.columns.firstOrNull { it.id == id }
        if (col == null) {
            openColumnId = null
        } else {
            ColumnInfoDialog(
                room = room,
                column = col,
                onPick = { t -> openColumnId = null; vm.openDialog(t) },
                onRoundChange = { vm.setColumnRound(id, it) },
                onDelete = { openColumnId = null; vm.deleteColumn(id) },
                onDismiss = { openColumnId = null },
            )
        }
    }
    val pendingRoute = extraRoute
    if (pendingRoute != null && extraType == null) {
        AlertDialog(
            onDismissRequest = { extraRoute = null },
            title = { Text(if (eng) "Что прокладываем?" else "Что дорисовали?") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    run {
                        val category = if (eng) Category.ENGINEERING else Category.CONSTRUCTION
                        EngType.entries.filter { it.category == category }.forEach { type ->
                            Row(
                                Modifier.fillMaxWidth().clickable { extraType = type }.padding(vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Box(Modifier.size(14.dp).background(engColor(type), CircleShape))
                                Text(type.title)
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { extraRoute = null }) { Text("Отмена") } },
        )
    }
    val pendingType = extraType
    if (pendingRoute != null && pendingType != null) {
        EngParamsDialog(
            type = pendingType,
            initial = null,
            confirmText = "Сохранить",
            onSave = { params ->
                vm.addElement(pendingType, params, pendingRoute)
                extraRoute = null
                extraType = null
            },
            onDismiss = { extraRoute = null; extraType = null },
        )
    }
    if (renaming) {
        NameDialog("Название помещения", room.name, onDismiss = { renaming = false }) {
            vm.renameRoom(it)
            renaming = false
        }
    }
    if (laserDialog) {
        LaserDialog(vm.laser, laserState, onDismiss = { laserDialog = false })
    }
}

/**
 * Выпрямляет нарисованную линию вдоль стен помещения и притягивает её концы
 * к стенам и уже нарисованным линиям. Всё в метрах плана.
 */
private fun planDiagonal(plan: Plan.RoomPlan): Double {
    val xs = plan.vertices.map { it.x }
    val ys = plan.vertices.map { it.y }
    return kotlin.math.hypot(xs.max() - xs.min(), ys.max() - ys.min())
}

private fun straightenAndSnap(room: Room, plan: Plan.RoomPlan, stroke: List<Point>, excludeId: String?): List<Point>? {
    val w0 = plan.walls.first()
    val base = atan2(w0.b.y - w0.a.y, w0.b.x - w0.a.x)
    val line = Straighten.polyline(stroke, base) ?: return null
    val targets = plan.walls.map { it.a to it.b } +
        room.engineering.filter { it.hasRoute && it.id != excludeId }.flatMap { e -> Plan.route(room, plan, e).zipWithNext() }
    val xs = plan.vertices.map { it.x }
    val ys = plan.vertices.map { it.y }
    val diag = kotlin.math.hypot(xs.max() - xs.min(), ys.max() - ys.min())
    return Snap.extendToWalls(line, targets, tolerance = diag * 0.06)
}

/** Короткая сводка под заголовком: площадь и сколько осталось измерить на этом этапе. */
private fun summary(room: Room, engineering: Boolean): String {
    val all = MeasureFlow.targets(room, engineering)
    val left = all.count { MeasureFlow.valueOf(room, it) == null }
    if (engineering) {
        return if (all.isEmpty()) "Нажмите «Проложить трассу» и проведите линию по плану"
        else if (left == 0) "Все участки трасс измерены" else "Не измерено участков: $left из ${all.size} · нажмите на трассу"
    }
    val area = Geometry.stats(room).floorArea?.let { "Площадь ${fmtArea(it)} · " } ?: ""
    return area + if (left == 0) "всё измерено" else "осталось измерить: $left из ${all.size} · нажмите на стену"
}

private fun targetColor(room: Room, target: MeasureTarget): Color? = when (target) {
    is MeasureTarget.Wall -> wallColor(target.index, room.walls.size)
    is MeasureTarget.EngSegment -> room.engineering.firstOrNull { it.id == target.elementId }?.let { engColor(it.type) }
    is MeasureTarget.ColumnValue -> ColumnColor
    else -> null
}

@Composable
private fun MeasureList(
    vm: MeasureViewModel,
    room: Room,
    engineering: Boolean,
    onEditEng: (EngElement) -> Unit,
    onPick: (MeasureTarget) -> Unit,
    onStartRoute: (EngElement) -> Unit,
    modifier: Modifier,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp, 0.dp, 16.dp, 24.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        MeasureFlow.targets(room, engineering).forEach { t ->
            when {
                t is MeasureTarget.OpeningWidth -> item(key = "opening-${t.index}") {
                    SectionHeader(MeasureFlow.openingTitle(room, t.index)) {
                        TextButton(onClick = { vm.removeOpening(t.index) }) { Text("Убрать") }
                    }
                }
                t is MeasureTarget.ColumnValue && t.dim == ColumnDim.WIDTH -> item(key = "col-${t.columnId}") {
                    SectionHeader(MeasureFlow.columnTitle(room, t.columnId), ColumnColor) {
                        TextButton(onClick = { vm.deleteColumn(t.columnId) }) { Text("Удал.") }
                    }
                }
                t is MeasureTarget.EngSegment && t.index == 0 -> {
                    val e = room.engineering.first { it.id == t.elementId }
                    item(key = "eng-${e.id}") {
                        SectionHeader("${MeasureFlow.engTitle(room, e.id)} · ${e.label.removePrefix(e.type.title).trimStart(',', ' ')}", engColor(e.type)) {
                            TextButton(onClick = { onEditEng(e) }) { Text("Изм.") }
                            TextButton(onClick = { onStartRoute(e) }) { Text("Трасса") }
                            TextButton(onClick = { vm.deleteEngineering(e.id) }) { Text("Удал.") }
                        }
                    }
                }
            }
            item(key = t.toString()) {
                MeasureRow(room, t, targetColor(room, t), selected = vm.dialogTarget == t, onClick = { onPick(t) })
            }
        }
        if (!engineering) {
            item {
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { vm.addOpening(OpeningType.WINDOW) }) { Text("+ Окно") }
                    OutlinedButton(onClick = { vm.addOpening(OpeningType.DOOR) }) { Text("+ Дверь") }
                }
            }
        } else if (room.engineering.none { it.type.category == Category.ENGINEERING }) {
            item { Text("Трасс пока нет — нажмите «Проложить трассу».", modifier = Modifier.padding(vertical = 8.dp)) }
        }
        item { StatsCard(room, engineering) }
    }
}

@Composable
private fun SectionHeader(title: String, color: Color? = null, actions: @Composable () -> Unit) {
    Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            color = color ?: Color.Unspecified,
            modifier = Modifier.weight(1f),
        )
        actions()
    }
}

@Composable
private fun MeasureRow(room: Room, target: MeasureTarget, color: Color?, selected: Boolean, onClick: () -> Unit) {
    val value = MeasureFlow.valueOf(room, target)
    val bg = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent
    val label = when (target) {
        is MeasureTarget.EngSegment -> "Участок ${target.index + 1}"
        is MeasureTarget.ColumnValue -> MeasureFlow.label(room, target).substringAfter(": ").replaceFirstChar { it.uppercase() }
        else -> MeasureFlow.label(room, target)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bg, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(14.dp).background(color ?: Color.Transparent, CircleShape))
        Text(label, modifier = Modifier.weight(1f), fontWeight = if (value == null) FontWeight.Bold else null)
        Text(value?.display() ?: "ввести", color = if (value == null) MaterialTheme.colorScheme.primary else Color.Unspecified)
    }
}

@Composable
private fun StatsCard(room: Room, engineering: Boolean) {
    val s = Geometry.stats(room)
    Card(Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Итоги", style = MaterialTheme.typography.titleMedium)
            if (engineering) {
                room.engineering.filter { it.type.category == Category.ENGINEERING }.forEach { e ->
                    Text("${MeasureFlow.engTitle(room, e.id)}: ${e.totalLength?.let(::fmtMm) ?: "не все участки измерены"}")
                }
                return@Column
            }
            Text("Площадь пола: ${s.floorArea?.let(::fmtArea) ?: "—"}")
            Text("Периметр: ${s.perimeter?.let(::fmtMm) ?: "—"}")
            Text("Площадь стен без проёмов: ${s.wallAreaNet?.let(::fmtArea) ?: "—"}")
            Text("Объём: ${s.volume?.let { "%.2f м³".format(Locale.ROOT, it) } ?: "—"}")
            s.closureError?.let { e ->
                Text(
                    if (s.needsRecheck) "⚠ Контур не сходится на ${fmtMm(e)} — перемерьте стены" else "Контур сходится (расхождение ${fmtMm(e)})",
                    color = if (s.needsRecheck) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            room.engineering.filter { it.type.category == Category.CONSTRUCTION }.forEach { e ->
                e.totalLength?.let { Text("${MeasureFlow.engTitle(room, e.id)}: всего ${fmtMm(it)}") }
            }
        }
    }
}
