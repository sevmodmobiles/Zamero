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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
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
import app.zamero.core.EngElement
import app.zamero.core.EngType
import app.zamero.core.Geometry
import app.zamero.core.MeasureFlow
import app.zamero.core.MeasureTarget
import app.zamero.core.OpeningType
import app.zamero.core.Outlines
import app.zamero.core.Plan
import app.zamero.core.Room
import app.zamero.core.Straighten
import app.zamero.laser.LaserState
import java.util.Locale
import kotlin.math.atan2

/** Окно параметров инженерии: новый элемент выбранного типа или правка существующего. */
private data class EngEdit(val type: EngType, val element: EngElement?)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoomScreen(vm: MeasureViewModel, room: Room) {
    val context = LocalContext.current
    var renaming by remember { mutableStateOf(false) }
    var laserDialog by remember { mutableStateOf(false) }
    var engMenu by remember { mutableStateOf(false) }
    var engEdit by remember { mutableStateOf<EngEdit?>(null) }
    var redrawing by remember(room.id) { mutableStateOf(false) }
    val laserState by vm.laser.state.collectAsState()
    val laserConnected = laserState is LaserState.Connected
    val target = vm.dialogTarget
    val routing = vm.routingElementId?.let { id -> room.engineering.firstOrNull { it.id == id } }

    val mode = when {
        redrawing || !room.hasOutline -> CanvasMode.DRAW_OUTLINE
        routing != null -> CanvasMode.DRAW_ROUTE
        else -> CanvasMode.VIEW
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { TextButton(onClick = vm::closeRoom) { Text("Назад") } },
                title = { Text(room.name, modifier = Modifier.clickable { renaming = true }) },
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
                    CanvasMode.DRAW_OUTLINE -> "Нарисуйте контур помещения пальцем или стилусом одной линией — она сама выпрямится."
                    CanvasMode.DRAW_ROUTE -> "Проведите трассу «${routing?.label}» поверх плана — участки выровняются по стенам."
                    CanvasMode.VIEW -> "Нажмите на стену или участок инженерии, чтобы ввести длину. Двумя пальцами — масштаб, двойное касание — сброс."
                },
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
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
                    val element = routing ?: return@PlanCanvas
                    val plan = Plan.room(room) ?: return@PlanCanvas
                    // Выравниваем трассу по направлению первой стены, затем переводим в координаты эскиза.
                    val w0 = plan.walls.first()
                    val base = atan2(w0.b.y - w0.a.y, w0.b.x - w0.a.x)
                    val line = Straighten.polyline(planStroke, base)
                    if (line == null) {
                        Toast.makeText(context, "Трасса слишком короткая — проведите линию длиннее", Toast.LENGTH_SHORT).show()
                    } else {
                        vm.setRoute(element.id, line.map { Plan.toSketch(room, plan, it) })
                    }
                },
                onTap = vm::openDialog,
                modifier = Modifier.fillMaxWidth().height(340.dp).padding(horizontal = 16.dp),
            )
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
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
                    CanvasMode.VIEW -> {
                        Box {
                            FilledTonalButton(onClick = { engMenu = true }) { Text("Инженерия ▾") }
                            DropdownMenu(expanded = engMenu, onDismissRequest = { engMenu = false }) {
                                EngType.entries.forEach { type ->
                                    DropdownMenuItem(
                                        text = { Text(type.title) },
                                        leadingIcon = { Box(Modifier.size(14.dp).background(engColor(type), CircleShape)) },
                                        onClick = {
                                            engMenu = false
                                            engEdit = EngEdit(type, null)
                                        },
                                    )
                                }
                            }
                        }
                        MeasureFlow.nextEmpty(room)?.let { next ->
                            OutlinedButton(onClick = { vm.openDialog(next) }) { Text("Мерить по порядку") }
                        }
                        OutlinedButton(onClick = { redrawing = true }) { Text("Перерисовать") }
                    }
                }
            }
            if (room.hasOutline) {
                MeasureList(vm, room, onEditEng = { engEdit = EngEdit(it.type, it) }, modifier = Modifier.weight(1f))
            }
        }
    }

    if (target != null) {
        key(target) {
            val after = MeasureFlow.nextEmpty(room, target)
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
            onSave = { params ->
                if (edit.element == null) vm.addEngineering(edit.type, params) else vm.editEngineering(edit.element.id, params)
                engEdit = null
            },
            onDismiss = { engEdit = null },
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

private fun targetColor(room: Room, target: MeasureTarget): Color? = when (target) {
    is MeasureTarget.Wall -> wallColor(target.index, room.walls.size)
    is MeasureTarget.EngSegment -> room.engineering.firstOrNull { it.id == target.elementId }?.let { engColor(it.type) }
    else -> null
}

@Composable
private fun MeasureList(vm: MeasureViewModel, room: Room, onEditEng: (EngElement) -> Unit, modifier: Modifier) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp, 0.dp, 16.dp, 24.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        MeasureFlow.targets(room).forEach { t ->
            when {
                t is MeasureTarget.OpeningWidth -> item(key = "opening-${t.index}") {
                    SectionHeader(MeasureFlow.openingTitle(room, t.index)) {
                        TextButton(onClick = { vm.removeOpening(t.index) }) { Text("Убрать") }
                    }
                }
                t is MeasureTarget.EngSegment && t.index == 0 -> {
                    val e = room.engineering.first { it.id == t.elementId }
                    item(key = "eng-${e.id}") {
                        SectionHeader("${MeasureFlow.engTitle(room, e.id)} · ${e.label.removePrefix(e.type.title).trimStart(',', ' ')}", engColor(e.type)) {
                            TextButton(onClick = { onEditEng(e) }) { Text("Изм.") }
                            TextButton(onClick = { vm.startRouting(e.id) }) { Text("Трасса") }
                            TextButton(onClick = { vm.deleteEngineering(e.id) }) { Text("Удал.") }
                        }
                    }
                }
            }
            item(key = t.toString()) {
                MeasureRow(room, t, targetColor(room, t), selected = vm.dialogTarget == t, onClick = { vm.openDialog(t) })
            }
        }
        item {
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { vm.addOpening(OpeningType.WINDOW) }) { Text("+ Окно") }
                OutlinedButton(onClick = { vm.addOpening(OpeningType.DOOR) }) { Text("+ Дверь") }
            }
        }
        item { StatsCard(room) }
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
private fun StatsCard(room: Room) {
    val s = Geometry.stats(room)
    Card(Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Итоги", style = MaterialTheme.typography.titleMedium)
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
            room.engineering.forEach { e ->
                e.totalLength?.let { Text("${MeasureFlow.engTitle(room, e.id)}: всего ${fmtMm(it)}") }
            }
        }
    }
}
