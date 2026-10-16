package app.zamero.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.zamero.core.Geometry
import app.zamero.core.Point
import app.zamero.core.Room
import kotlin.math.hypot
import kotlin.math.min

/** Вписывает контур в область рисования с отступами. */
private class Fit(points: List<Point>, size: IntSize, pad: Float) {
    private val scale: Float
    private val ox: Float
    private val oy: Float

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

    fun map(p: Point) = Offset(ox + p.x.toFloat() * scale, oy + p.y.toFloat() * scale)
}

/**
 * Эскиз помещения. В режиме рисования ведёте пальцем/стилусом — по отпусканию линия
 * уходит в [onStroke] (там её спрямляют). В обычном режиме стены раскрашены, нажатие
 * на стену вызывает [onWallTap].
 */
@Composable
fun OutlineCanvas(
    room: Room,
    drawMode: Boolean,
    selectedWall: Int?,
    onStroke: (List<Point>) -> Unit,
    onWallTap: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val pad = with(density) { 40.dp.toPx() }
    val hitRadius = with(density) { 32.dp.toPx() }
    var size by remember { mutableStateOf(IntSize.Zero) }
    val stroke = remember { mutableStateListOf<Offset>() }
    val measurer = rememberTextMeasurer()

    val shown = Geometry.display(room)
    val fit = Fit(shown, size, pad)
    val screen = shown.map(fit::map)
    val currentScreen by rememberUpdatedState(screen)
    val currentOnStroke by rememberUpdatedState(onStroke)
    val currentOnTap by rememberUpdatedState(onWallTap)

    val outlineColor = MaterialTheme.colorScheme.outlineVariant
    val strokeColor = MaterialTheme.colorScheme.primary
    val labelBg = MaterialTheme.colorScheme.surface

    val gestures = if (drawMode) {
        Modifier.pointerInput("draw") {
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
                val points = stroke.map { Point(it.x.toDouble(), it.y.toDouble()) }
                stroke.clear()
                currentOnStroke(points)
            }
        }
    } else {
        Modifier.pointerInput("tap") {
            detectTapGestures { pos ->
                val pts = currentScreen
                if (pts.size < 3) return@detectTapGestures
                val best = pts.indices.minBy { i -> distanceToSegment(pos, pts[i], pts[(i + 1) % pts.size]) }
                if (distanceToSegment(pos, pts[best], pts[(best + 1) % pts.size]) <= hitRadius) currentOnTap(best)
            }
        }
    }

    Box(
        modifier
            .border(1.dp, outlineColor, RoundedCornerShape(12.dp))
            .onSizeChanged { size = it }
            .then(gestures),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            // Сетка — помогает рисовать ровнее.
            val step = 24.dp.toPx()
            var x = step
            while (x < this.size.width) {
                drawLine(outlineColor.copy(alpha = 0.35f), Offset(x, 0f), Offset(x, this.size.height), 1f)
                x += step
            }
            var y = step
            while (y < this.size.height) {
                drawLine(outlineColor.copy(alpha = 0.35f), Offset(0f, y), Offset(this.size.width, y), 1f)
                y += step
            }

            if (drawMode) {
                if (stroke.size > 1) {
                    val path = Path().apply {
                        moveTo(stroke[0].x, stroke[0].y)
                        for (i in 1 until stroke.size) lineTo(stroke[i].x, stroke[i].y)
                    }
                    drawPath(path, strokeColor, style = Stroke(4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
                return@Canvas
            }

            val n = screen.size
            if (n < 3) return@Canvas
            for (i in 0 until n) {
                val a = screen[i]
                val b = screen[(i + 1) % n]
                val selected = i == selectedWall
                drawLine(
                    color = wallColor(i, n),
                    start = a,
                    end = b,
                    strokeWidth = (if (selected) 9.dp else 5.dp).toPx(),
                    cap = StrokeCap.Round,
                )
            }
            // Подписи: номер стены и длина, снаружи контура.
            for (i in 0 until n) {
                val a = screen[i]
                val b = screen[(i + 1) % n]
                val len = hypot(b.x - a.x, b.y - a.y).coerceAtLeast(1f)
                val normal = Offset((b.y - a.y) / len, -(b.x - a.x) / len) // наружу для обхода по часовой
                val mid = Offset((a.x + b.x) / 2, (a.y + b.y) / 2)
                val text = room.walls.getOrNull(i)?.let { "${i + 1}: ${it.millimeters}" } ?: "${i + 1}"
                val layout = measurer.measure(
                    text,
                    TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold, color = wallColor(i, n)),
                )
                val w = layout.size.width.toFloat()
                val h = layout.size.height.toFloat()
                val center = mid + normal * (h * 0.5f + 8.dp.toPx() + kotlin.math.abs(normal.x) * w * 0.5f)
                val topLeft = Offset(center.x - w / 2, center.y - h / 2)
                drawRoundRect(
                    labelBg.copy(alpha = 0.85f),
                    topLeft = topLeft - Offset(4.dp.toPx(), 1.dp.toPx()),
                    size = Size(w + 8.dp.toPx(), h + 2.dp.toPx()),
                    cornerRadius = CornerRadius(6.dp.toPx()),
                )
                drawText(layout, topLeft = topLeft)
            }
        }
    }
}

private fun distanceToSegment(p: Offset, a: Offset, b: Offset): Float {
    val dx = b.x - a.x
    val dy = b.y - a.y
    val len2 = dx * dx + dy * dy
    if (len2 == 0f) return (p - a).getDistance()
    val t = (((p.x - a.x) * dx + (p.y - a.y) * dy) / len2).coerceIn(0f, 1f)
    return (p - Offset(a.x + t * dx, a.y + t * dy)).getDistance()
}
