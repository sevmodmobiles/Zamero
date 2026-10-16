package app.zamero.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.zamero.core.EngType
import app.zamero.core.MeasureTarget
import app.zamero.core.Plan
import app.zamero.core.Point
import app.zamero.core.Room
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.min

enum class CanvasMode { VIEW, DRAW_OUTLINE, DRAW_ROUTE }

/** Цвет инженерной линии по типу. */
internal fun engColor(type: EngType): Color = when (type) {
    EngType.TRAY -> Color(0xFFEF6C00)
    EngType.PIPE -> Color(0xFF1565C0)
    EngType.DRAIN -> Color(0xFF2E7D32)
    EngType.DUCT_ROUND -> Color(0xFF6A1B9A)
    EngType.DUCT_RECT -> Color(0xFF00838F)
}

/** План в метрах → экран: вписывание в область + масштаб и сдвиг пальцами. */
private class View(points: List<Point>, size: IntSize, pad: Float, private val zoom: Float, private val pan: Offset) {
    private val scale: Float
    private val ox: Float
    private val oy: Float
    private val center = Offset(size.width / 2f, size.height / 2f)

    init {
        if (points.isEmpty() || size.width == 0) {
            scale = 1f; ox = 0f; oy = 0f
        } else {
            val minX = points.minOf { it.x }
            val minY = points.minOf { it.y }
            val w = (points.maxOf { it.x } - minX).coerceAtLeast(1e-6)
            val h = (points.maxOf { it.y } - minY).coerceAtLeast(1e-6)
            scale = min((size.width - 2 * pad) / w, (size.height - 2 * pad) / h).toFloat()
            ox = (size.width - w.toFloat() * scale) / 2 - minX.toFloat() * scale
            oy = (size.height - h.toFloat() * scale) / 2 - minY.toFloat() * scale
        }
    }

    fun toScreen(p: Point): Offset {
        val base = Offset(ox + p.x.toFloat() * scale, oy + p.y.toFloat() * scale)
        return (base - center) * zoom + center + pan
    }

    fun toPlan(s: Offset): Point {
        val base = (s - pan - center) / zoom + center
        return Point(((base.x - ox) / scale).toDouble(), ((base.y - oy) / scale).toDouble())
    }
}

/**
 * Рабочее поле плана. VIEW — стены раскрашены, нажатие выбирает стену или участок
 * инженерии, двумя пальцами — масштаб и сдвиг. DRAW_OUTLINE — рисуем контур
 * (координаты экрана), DRAW_ROUTE — трассу (координаты плана, метры).
 */
@Composable
fun PlanCanvas(
    room: Room,
    mode: CanvasMode,
    selected: MeasureTarget?,
    onOutline: (List<Point>) -> Unit,
    onRoute: (List<Point>) -> Unit,
    onTap: (MeasureTarget) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val pad = with(density) { 44.dp.toPx() }
    val hitRadius = with(density) { 28.dp.toPx() }
    var size by remember { mutableStateOf(IntSize.Zero) }
    var zoom by remember(room.id) { mutableFloatStateOf(1f) }
    var pan by remember(room.id) { mutableStateOf(Offset.Zero) }
    val stroke = remember { mutableStateListOf<Offset>() }
    val measurer = rememberTextMeasurer()

    val plan = if (mode == CanvasMode.DRAW_OUTLINE) null else Plan.room(room)
    val view = View(plan?.vertices ?: emptyList(), size, pad, zoom, pan)
    val walls = plan?.walls?.map { view.toScreen(it.a) to view.toScreen(it.b) } ?: emptyList()
    val routes = if (plan == null) emptyList() else room.engineering.filter { it.hasRoute }.map { e ->
        e to Plan.route(room, plan, e).map(view::toScreen)
    }

    val current by rememberUpdatedState(Triple(walls, routes, view))
    val onOutlineState by rememberUpdatedState(onOutline)
    val onRouteState by rememberUpdatedState(onRoute)
    val onTapState by rememberUpdatedState(onTap)

    val outlineColor = MaterialTheme.colorScheme.outlineVariant
    val strokeColor = MaterialTheme.colorScheme.primary
    val labelBg = MaterialTheme.colorScheme.surface

    val gestures = when (mode) {
        CanvasMode.DRAW_OUTLINE, CanvasMode.DRAW_ROUTE -> Modifier.pointerInput(mode) {
            awaitEachGesture {
                val down = awaitFirstDown()
                stroke.clear()
                stroke.add(down.position)
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) break
                    if (change.positionChange() != Offset.Zero) {
                        stroke.add(change.position)
                        change.consume()
                    }
                }
                val screen = stroke.toList()
                stroke.clear()
                if (mode == CanvasMode.DRAW_OUTLINE) {
                    onOutlineState(screen.map { Point(it.x.toDouble(), it.y.toDouble()) })
                } else {
                    onRouteState(screen.map(current.third::toPlan))
                }
            }
        }
        CanvasMode.VIEW -> Modifier
            .pointerInput("transform") {
                detectTransformGestures { centroid, panChange, zoomChange, _ ->
                    val newZoom = (zoom * zoomChange).coerceIn(0.5f, 8f)
                    val k = newZoom / zoom
                    // Масштабируем относительно точки между пальцами.
                    pan = (pan - centroid + Offset(size.width / 2f, size.height / 2f)) * k +
                        centroid - Offset(size.width / 2f, size.height / 2f) + panChange
                    zoom = newZoom
                }
            }
            .pointerInput("tap") {
                detectTapGestures(
                    onDoubleTap = { zoom = 1f; pan = Offset.Zero },
                    onTap = { pos ->
                        val (w, r, _) = current
                        var best: MeasureTarget? = null
                        var bestDist = hitRadius
                        // Инженерия лежит поверх стен — проверяем её первой и с небольшим приоритетом.
                        r.forEach { (e, pts) ->
                            for (i in 0 until pts.size - 1) {
                                val d = distanceToSegment(pos, pts[i], pts[i + 1]) * 0.8f
                                if (d < bestDist) { bestDist = d; best = MeasureTarget.EngSegment(e.id, i) }
                            }
                        }
                        w.forEachIndexed { i, (a, b) ->
                            val d = distanceToSegment(pos, a, b)
                            if (d < bestDist) { bestDist = d; best = MeasureTarget.Wall(i) }
                        }
                        best?.let(onTapState)
                    },
                )
            }
    }

    Box(
        modifier
            .border(1.dp, outlineColor, RoundedCornerShape(12.dp))
            .onSizeChanged { size = it }
            .then(gestures),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawGrid(outlineColor)
            val n = walls.size
            walls.forEachIndexed { i, (a, b) ->
                val sel = selected == MeasureTarget.Wall(i)
                drawLine(wallColor(i, n), a, b, (if (sel) 9.dp else 5.dp).toPx(), StrokeCap.Round)
            }
            walls.forEachIndexed { i, (a, b) ->
                val text = room.walls.getOrNull(i)?.let { "${i + 1}: ${it.millimeters}" } ?: "${i + 1}"
                label(measurer, text, wallColor(i, n), a, b, outward = true, labelBg)
            }
            routes.forEach { (e, pts) ->
                val color = engColor(e.type)
                val dash = if (e.type == EngType.DRAIN) PathEffect.dashPathEffect(floatArrayOf(18f, 10f)) else null
                for (i in 0 until pts.size - 1) {
                    val sel = selected == MeasureTarget.EngSegment(e.id, i)
                    drawLine(color, pts[i], pts[i + 1], (if (sel) 8.dp else 4.dp).toPx(), StrokeCap.Round, dash)
                }
                pts.forEach { drawCircle(color, 4.dp.toPx(), it) }
                for (i in 0 until pts.size - 1) {
                    val len = e.segments.getOrNull(i)?.millimeters
                    val text = if (i == 0) "${e.sectionText.ifEmpty { e.type.title }}${len?.let { " · $it" } ?: ""}" else len?.toString() ?: continue
                    label(measurer, text, color, pts[i], pts[i + 1], outward = false, labelBg)
                }
            }
            if (stroke.size > 1) {
                val path = Path().apply {
                    moveTo(stroke[0].x, stroke[0].y)
                    for (i in 1 until stroke.size) lineTo(stroke[i].x, stroke[i].y)
                }
                drawPath(path, strokeColor, style = Stroke(4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
    }
}

private fun DrawScope.drawGrid(color: Color) {
    val step = 24.dp.toPx()
    var x = step
    while (x < size.width) {
        drawLine(color.copy(alpha = 0.35f), Offset(x, 0f), Offset(x, size.height), 1f)
        x += step
    }
    var y = step
    while (y < size.height) {
        drawLine(color.copy(alpha = 0.35f), Offset(0f, y), Offset(size.width, y), 1f)
        y += step
    }
}

/** Подпись у середины отрезка: снаружи контура для стен, сбоку для инженерии. */
private fun DrawScope.label(
    measurer: TextMeasurer,
    text: String,
    color: Color,
    a: Offset,
    b: Offset,
    outward: Boolean,
    bg: Color,
) {
    val len = hypot(b.x - a.x, b.y - a.y).coerceAtLeast(1f)
    // Для обхода по часовой стрелке (ось y вниз) эта нормаль смотрит наружу.
    var normal = Offset((b.y - a.y) / len, -(b.x - a.x) / len)
    if (!outward) normal = -normal
    val mid = Offset((a.x + b.x) / 2, (a.y + b.y) / 2)
    val layout = measurer.measure(text, TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = color))
    val w = layout.size.width.toFloat()
    val h = layout.size.height.toFloat()
    val center = mid + normal * (h * 0.5f + 6.dp.toPx() + abs(normal.x) * w * 0.5f)
    val topLeft = Offset(center.x - w / 2, center.y - h / 2)
    drawRoundRect(
        bg.copy(alpha = 0.85f),
        topLeft = topLeft - Offset(4.dp.toPx(), 1.dp.toPx()),
        size = Size(w + 8.dp.toPx(), h + 2.dp.toPx()),
        cornerRadius = CornerRadius(6.dp.toPx()),
    )
    drawText(layout, topLeft = topLeft)
}

private fun distanceToSegment(p: Offset, a: Offset, b: Offset): Float {
    val dx = b.x - a.x
    val dy = b.y - a.y
    val len2 = dx * dx + dy * dy
    if (len2 == 0f) return (p - a).getDistance()
    val t = (((p.x - a.x) * dx + (p.y - a.y) * dy) / len2).coerceIn(0f, 1f)
    return (p - Offset(a.x + t * dx, a.y + t * dy)).getDistance()
}
