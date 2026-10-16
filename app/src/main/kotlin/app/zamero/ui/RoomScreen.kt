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
import app.zamero.core.Geometry
import app.zamero.core.MeasureFlow
import app.zamero.core.MeasureTarget
import app.zamero.core.OpeningType
import app.zamero.core.Outlines
import app.zamero.core.Room
import app.zamero.core.Straighten
import app.zamero.laser.LaserState
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoomScreen(vm: MeasureViewModel, room: Room) {
    val context = LocalContext.current
    var renaming by remember { mutableStateOf(false) }
    var laserDialog by remember { mutableStateOf(false) }
    // Новое помещение сразу открывается в режиме рисования.
    var redrawing by remember(room.id) { mutableStateOf(false) }
    val drawMode = redrawing || !room.hasOutline
    val laserState by vm.laser.state.collectAsState()
    val laserConnected = laserState is LaserState.Connected
    val target = vm.dialogTarget

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { TextButton(onClick = vm::closeRoom) { Text("Назад") } },
                title = { Text(room.name, modifier = Modifier.clickable { renaming = true }) },
                actions = {
                    TextButton(onClick = { laserDialog = true }) {
                        Text(if (laserConnected) "Дальномер ✓" else "Дальномер")
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Text(
                if (drawMode) {
                    "Нарисуйте контур помещения пальцем или стилусом одной линией — она сама выпрямится."
                } else {
                    "Нажмите на стену, чтобы ввести её длину."
                },
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            OutlineCanvas(
                room = room,
                drawMode = drawMode,
                selectedWall = (target as? MeasureTarget.Wall)?.index,
                onStroke = { stroke ->
                    val poly = Straighten.polygon(stroke)
                    if (poly == null) {
                        Toast.makeText(context, "Не получилось распознать контур — нарисуйте замкнутую фигуру крупнее", Toast.LENGTH_SHORT).show()
                    } else {
                        vm.setOutline(poly)
                        redrawing = false
                    }
                },
                onWallTap = { vm.openDialog(MeasureTarget.Wall(it)) },
                modifier = Modifier.fillMaxWidth().height(340.dp).padding(horizontal = 16.dp),
            )
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (drawMode) {
                    OutlinedButton(onClick = { vm.setOutline(Outlines.RECTANGLE); redrawing = false }) { Text("Прямоугольник") }
                    OutlinedButton(onClick = { vm.setOutline(Outlines.L_SHAPE); redrawing = false }) { Text("Г-образная") }
                    if (room.hasOutline) TextButton(onClick = { redrawing = false }) { Text("Отмена") }
                } else {
                    OutlinedButton(onClick = { redrawing = true }) { Text("Перерисовать") }
                    MeasureFlow.nextEmpty(room)?.let { next ->
                        FilledTonalButton(onClick = { vm.openDialog(next) }) { Text("Мерить по порядку") }
                    }
                }
            }
            if (room.hasOutline) {
                MeasureList(vm, room, Modifier.weight(1f))
            }
        }
    }

    if (target != null) {
        key(target) {
            val after = MeasureFlow.nextEmpty(room, target)
            MeasureDialog(
                title = MeasureFlow.label(room, target),
                color = (target as? MeasureTarget.Wall)?.let { wallColor(it.index, room.walls.size) },
                current = MeasureFlow.valueOf(room, target),
                laserConnected = laserConnected,
                lastLaser = vm.lastLaser,
                hasNext = after != null && after != target,
                onSave = { meters, source, goNext -> vm.saveMeasure(target, meters, source, goNext) },
                onDismiss = vm::closeDialog,
            )
        }
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

@Composable
private fun MeasureList(vm: MeasureViewModel, room: Room, modifier: Modifier) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp, 0.dp, 16.dp, 24.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        MeasureFlow.targets(room).forEach { t ->
            if (t is MeasureTarget.OpeningWidth) {
                item(key = "head-${t.index}") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            MeasureFlow.openingTitle(room, t.index),
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { vm.removeOpening(t.index) }) { Text("Убрать") }
                    }
                }
            }
            item(key = t.toString()) {
                val color = (t as? MeasureTarget.Wall)?.let { wallColor(it.index, room.walls.size) }
                MeasureRow(room, t, color, selected = vm.dialogTarget == t, onClick = { vm.openDialog(t) })
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
private fun MeasureRow(room: Room, target: MeasureTarget, color: Color?, selected: Boolean, onClick: () -> Unit) {
    val value = MeasureFlow.valueOf(room, target)
    val bg = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent
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
        Text(MeasureFlow.label(room, target), modifier = Modifier.weight(1f), fontWeight = if (value == null) FontWeight.Bold else null)
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
        }
    }
}
