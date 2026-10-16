package app.zamero.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.zamero.ar.ArMeasureActivity
import app.zamero.core.Geometry
import app.zamero.core.MeasureFlow
import app.zamero.core.MeasureTarget
import app.zamero.core.OpeningType
import app.zamero.core.Room
import app.zamero.core.Shape
import app.zamero.core.Source
import app.zamero.laser.LaserState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoomScreen(vm: MeasureViewModel, room: Room) {
    var renaming by remember { mutableStateOf(false) }
    var laserDialog by remember { mutableStateOf(false) }
    val laserState by vm.laser.state.collectAsState()
    val haptic = LocalHapticFeedback.current

    // Вибро-отклик на каждое записанное значение: можно не смотреть на экран.
    LaunchedEffect(vm.lastRecord) {
        if (vm.lastRecord != null) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { TextButton(onClick = vm::closeRoom) { Text("Назад") } },
                title = { Text(room.name, modifier = Modifier.clickable { renaming = true }) },
                actions = {
                    TextButton(onClick = { laserDialog = true }) {
                        Text(if (laserState is LaserState.Connected) "Рулетка ✓" else "Рулетка")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Shape.entries.forEach { shape ->
                    FilterChip(
                        selected = room.shape == shape,
                        onClick = { vm.setShape(shape) },
                        label = { Text(shape.title) },
                    )
                }
            }

            RoomSketch(room, vm.focus, onWallClick = { vm.select(MeasureTarget.Wall(it)) })

            CaptureCard(vm, room, laserState)

            MeasureFlow.targets(room).forEach { target ->
                if (target is MeasureTarget.OpeningWidth) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            MeasureFlow.label(room, target).substringBefore(':'),
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { vm.removeOpening(target.index) }) { Text("Убрать") }
                    }
                }
                TargetRow(room, target, focused = vm.focus == target, onSelect = { vm.select(target) }, onClear = { vm.clear(target) })
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { vm.addOpening(OpeningType.WINDOW) }) { Text("+ Окно") }
                OutlinedButton(onClick = { vm.addOpening(OpeningType.DOOR) }) { Text("+ Дверь") }
            }

            StatsCard(room)
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
private fun CaptureCard(vm: MeasureViewModel, room: Room, laserState: LaserState) {
    val target = vm.focus
    val camera = rememberLauncherForActivityResult(ArMeasureActivity.Contract()) { meters ->
        meters?.let { vm.record(it, Source.CAMERA) }
    }
    var text by remember(target) { mutableStateOf("") }
    val submit = {
        parseManual(text)?.let {
            vm.record(it, Source.MANUAL)
            text = ""
        }
    }

    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (target == null) {
                Text("Всё измерено ✓", style = MaterialTheme.typography.titleLarge)
                Text("Чтобы перемерить, нажмите на нужное поле ниже.")
            } else {
                Text("Сейчас: ${MeasureFlow.label(room, target)}", style = MaterialTheme.typography.titleLarge)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        label = { Text("Метры, или «345 см»") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { submit() }),
                        modifier = Modifier.weight(1f),
                    )
                    Button(onClick = { submit() }, enabled = parseManual(text) != null) { Text("OK") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    FilledTonalButton(onClick = { camera.launch(Unit) }) { Text("Камера") }
                    Text(
                        when (laserState) {
                            is LaserState.Connected -> "или нажмите кнопку замера на рулетке"
                            else -> "Рулетка не подключена"
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            vm.lastRecord?.let {
                Text("Записано: ${it.label} — ${it.measure.display()}", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun TargetRow(room: Room, target: MeasureTarget, focused: Boolean, onSelect: () -> Unit, onClear: () -> Unit) {
    val value = MeasureFlow.valueOf(room, target)
    val bg = if (focused) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bg, RoundedCornerShape(8.dp))
            .clickable(onClick = onSelect)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(MeasureFlow.label(room, target), modifier = Modifier.weight(1f), fontWeight = if (focused) FontWeight.Bold else null)
        Text(value?.display() ?: "—")
        if (value != null) TextButton(onClick = onClear) { Text("×") }
    }
}

@Composable
private fun RoomSketch(room: Room, focus: MeasureTarget?, onWallClick: (Int) -> Unit) {
    val polygon = Geometry.sketch(room)
    val measurer = rememberTextMeasurer()
    val lineColor = MaterialTheme.colorScheme.onSurface
    val focusColor = MaterialTheme.colorScheme.primary
    val focusedWall = (focus as? MeasureTarget.Wall)?.index
    val labelStyle = TextStyle(fontSize = 13.sp, color = lineColor)

    Canvas(Modifier.fillMaxWidth().height(180.dp)) {
        val xs = polygon.vertices.map { it.first }
        val ys = polygon.vertices.map { -it.second }
        val pad = 28.dp.toPx()
        val w = (xs.max() - xs.min()).coerceAtLeast(0.01)
        val h = (ys.max() - ys.min()).coerceAtLeast(0.01)
        val scale = minOf((size.width - 2 * pad) / w, (size.height - 2 * pad) / h).toFloat()
        val ox = (size.width - w.toFloat() * scale) / 2 - xs.min().toFloat() * scale
        val oy = (size.height - h.toFloat() * scale) / 2 - ys.min().toFloat() * scale
        fun p(i: Int): Offset {
            val k = i % xs.size
            return Offset(ox + xs[k].toFloat() * scale, oy + ys[k].toFloat() * scale)
        }
        for (i in xs.indices) {
            val focused = i == focusedWall
            drawLine(
                color = if (focused) focusColor else lineColor,
                start = p(i),
                end = p(i + 1),
                strokeWidth = (if (focused) 6.dp else 3.dp).toPx(),
                cap = StrokeCap.Round,
            )
            val mid = (p(i) + p(i + 1)) / 2f
            val label = measurer.measure("${i + 1}", labelStyle.copy(fontWeight = if (focused) FontWeight.Bold else null))
            // Подпись выносим наружу от центра контура.
            val center = Offset(size.width / 2, size.height / 2)
            val dir = (mid - center).let { d -> d / d.getDistance().coerceAtLeast(1f) }
            val pos = mid + dir * 14.dp.toPx() - Offset(label.size.width / 2f, label.size.height / 2f)
            drawText(label, topLeft = pos)
        }
    }
    // Быстрый выбор стены — кнопками под эскизом.
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        room.walls.indices.forEach { i ->
            FilterChip(selected = i == focusedWall, onClick = { onWallClick(i) }, label = { Text("Стена ${i + 1}") })
        }
    }
}

@Composable
private fun StatsCard(room: Room) {
    val s = Geometry.stats(room)
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Итоги", style = MaterialTheme.typography.titleMedium)
            Text("Площадь пола: ${s.floorArea?.let(::fmtArea) ?: "—"}")
            Text("Периметр: ${s.perimeter?.let(::fmtMeters) ?: "—"}")
            Text("Площадь стен без проёмов: ${s.wallAreaNet?.let(::fmtArea) ?: "—"}")
            Text("Объём: ${s.volume?.let { "%.2f м³".format(java.util.Locale.ROOT, it) } ?: "—"}")
            s.closureError?.let { e ->
                Text(
                    if (s.needsRecheck) "⚠ Контур не сходится на ${fmtCm(e)} — перемерьте стены" else "Контур сходится (расхождение ${fmtCm(e)})",
                    color = if (s.needsRecheck) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
