package app.zamero.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.drag
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
import androidx.compose.ui.draw.clipToBounds
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
import app.zamero.core.Category
import app.zamero.core.Column
import app.zamero.core.ColumnPlan
import app.zamero.core.Columns
import app.zamero.core.Discipline
import app.zamero.core.Edit
import app.zamero.core.EngElement
import app.zamero.core.EngType
import app.zamero.core.Footprint
import app.zamero.core.MeasureTarget
import app.zamero.core.Plan
import app.zamero.core.Point
import app.zamero.core.PointRef
import app.zamero.core.Room
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.min

enum class CanvasMode { VIEW, DRAW_OUTLINE, DRAW_ROUTE, DRAW_EXTRA }

/** Цвет слоя инженерной системы. */
internal fun systemColor(d: Discipline): Color = when (d) {
    Discipline.EOM -> Color(0xFFF57C00)
    Discipline.OVIK -> Color(0xFF00ACC1)
    Discipline.VK -> Color(0xFF1E88E5)
}

/** Цвет линии: инженерия — по системе (слою), стены и линии — по типу. */
internal fun engColor(e: EngElement): Color = e.system?.let(::systemColor) ?: engColor(e.type)

/** Цвет инженерной линии по типу. */
internal fun engColor(type: EngType): Color = when (type) {
    EngType.WALL -> Color(0xFF8D6E63)
    EngType.PARTITION -> Color(0xFF78909C)
    EngType.LINE -> Color(0xFFAFB42B)
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

    fun metersPerPx(): Double = if (scale > 0f) 1.0 / (scale * zoom) else 0.0

    fun toPlan(s: Offset): Point {
        val base = (s - pan - center) / zoom + center
        return Point(((base.x - ox) / scale).toDouble(), ((base.y - oy) / scale).toDouble())
    }
}

/** Инструмент редактора на нижней панели. */
enum class Tool { SELECT, ERASE, MEASURE }

/** Что попало под палец в режиме «Стереть». */
sealed interface EraseHit {
    data class PointHit(val ref: PointRef) : EraseHit
    data class Segment(val target: MeasureTarget) : EraseHit
    data class ColumnHit(val id: String) : EraseHit
}

/**
 * Рабочее поле плана.
 * VIEW + SELECT — нажатие выбирает стену/участок для замера, за активные точки можно
 * тянуть (соседние линии тянутся следом), двумя пальцами — масштаб и сдвиг.
 * VIEW + ERASE — нажатие стирает точку, линию или колонну.
 * VIEW + MEASURE — два нажатия меряют расстояние по плану.
 * DRAW_OUTLINE — рисуем контур (координаты экрана), DRAW_ROUTE/EXTRA — линию (метры плана).
 */
@Composable
fun PlanCanvas(
    room: Room,
    mode: CanvasMode,
    selected: MeasureTarget?,
    onOutline: (List<Point>) -> Unit,
    onRoute: (List<Point>) -> Unit,
    /** Нажатие на линию: что выбрано и где палец (координаты холста) — для меню. */
    onTap: (MeasureTarget, Offset) -> Unit,
    onColumnTap: (String) -> Unit,
    modifier: Modifier = Modifier,
    /** Этап «Инженерия»: стены и колонны — бледный фон, активны только трассы. */
    engineeringStage: Boolean = false,
    tool: Tool = Tool.SELECT,
    /** Выключенные слои инженерии — не рисуются и не нажимаются. */
    hiddenSystems: Set<Discipline> = emptySet(),
    onPointMove: (PointRef, Point, Double) -> Unit = { _, _, _ -> },
    /** Режим «Стереть»: касание — добавить/убрать линию из выбора. */
    onErase: (EraseHit) -> Unit = {},
    /** Режим «Стереть»: обвели петлю — всё, что внутри. */
    onLasso: (Set<EraseHit>) -> Unit = {},
    /** Выбранное к удалению — подсвечивается красным пунктиром. */
    pendingErase: Set<EraseHit> = emptySet(),
    measureA: Point? = null,
    measureB: Point? = null,
    onMeasureTap: (Point) -> Unit = {},
) {
    val density = LocalDensity.current
    val pad = with(density) { 44.dp.toPx() }
    val hitRadius = with(density) { 28.dp.toPx() }
    val handleRadius = with(density) { 22.dp.toPx() }
    var size by remember { mutableStateOf(IntSize.Zero) }
    var zoom by remember(room.id) { mutableFloatStateOf(1f) }
    var pan by remember(room.id) { mutableStateOf(Offset.Zero) }
    var dragRef by remember { mutableStateOf<PointRef?>(null) }
    var dragPos by remember { mutableStateOf(Offset.Zero) }
    val stroke = remember { mutableStateListOf<Offset>() }
    val measurer = rememberTextMeasurer()

    val basePlan = if (mode == CanvasMode.DRAW_OUTLINE) null else Plan.room(room)
    val view = View(basePlan?.vertices ?: emptyList(), size, pad, zoom, pan)
    val snapMeters = handleRadius * view.metersPerPx()

    // На этапе «Помещения» инженерию не показываем; на этапе «Инженерия» дорисованные стены — фон.
    fun visible(e: EngElement) =
        if (e.type.category == Category.ENGINEERING) engineeringStage && e.system?.let { it !in hiddenSystems } != false else true
    fun isActive(e: EngElement) = (e.type.category == Category.ENGINEERING) == engineeringStage
    fun draggable(r: PointRef) = when (r) {
        is PointRef.Vertex -> !engineeringStage
        is PointRef.RoutePoint -> room.engineering.firstOrNull { it.id == r.elementId }?.let(::isActive) == true
    }

    // Во время перетаскивания показываем результат вживую; вид не перестраиваем,
    // а сдвигаем план так, чтобы неподвижные вершины остались на месте.
    var shown = room
    var delta = Point(0.0, 0.0)
    val ref = dragRef
    if (ref != null && basePlan != null) {
        val preview = Edit.movePoint(room, ref, view.toPlan(dragPos), snapMeters)
        val previewPlan = Plan.room(preview)
        val origin = Edit.points(room).firstOrNull { it.first == ref }?.second
        val anchor = basePlan.vertices.indices.firstOrNull { i ->
            origin == null || kotlin.math.hypot(basePlan.vertices[i].x - origin.x, basePlan.vertices[i].y - origin.y) > Edit.SAME_POINT_M
        }
        if (previewPlan != null && anchor != null && anchor < previewPlan.vertices.size) {
            shown = preview
            delta = Point(basePlan.vertices[anchor].x - previewPlan.vertices[anchor].x, basePlan.vertices[anchor].y - previewPlan.vertices[anchor].y)
        }
    }
    val plan = if (shown === room) basePlan else Plan.room(shown)
    val toScreen: (Point) -> Offset = { p -> view.toScreen(Point(p.x + delta.x, p.y + delta.y)) }

    val walls = plan?.walls?.map { toScreen(it.a) to toScreen(it.b) } ?: emptyList()
    val routesMeters = if (plan == null) emptyList() else shown.engineering
        .filter { it.hasRoute && visible(it) }
        .map { e -> e to Plan.route(shown, plan, e) }
    val routes = routesMeters.map { (e, pts) -> e to pts.map(toScreen) }
    // Проекция на пол в реальную ширину (лотки, воздуховоды, трубы, стены с толщиной).
    val footprints = routesMeters.mapNotNull { (e, pts) ->
        Footprint.width(e)?.let { w -> e to Footprint.outline(pts, w).map(toScreen) }
    }.toMap()
    val pxPerMeter = view.metersPerPx().let { if (it > 0) 1.0 / it else 0.0 }
    val columns = if (plan == null) emptyList() else shown.columns.map { it to Columns.plan(shown, plan, it) }
    val allPoints = if (plan == null) emptyList() else Edit.points(shown, ::visible).map { it.first to toScreen(it.second) }
    val handles = allPoints.filter { draggable(it.first) }
    val wallAlpha = if (engineeringStage) 0.3f else 1f

    val current by rememberUpdatedState(Triple(walls, routes, view))
    val currentColumns by rememberUpdatedState(columns)
    val currentHandles by rememberUpdatedState(handles)
    val currentPoints by rememberUpdatedState(allPoints)
    val toolState by rememberUpdatedState(tool)
    val snapState by rememberUpdatedState(snapMeters)
    val pxPerMeterState by rememberUpdatedState(pxPerMeter)
    val onColumnTapState by rememberUpdatedState(onColumnTap)
    val onOutlineState by rememberUpdatedState(onOutline)
    val onRouteState by rememberUpdatedState(onRoute)
    val onTapState by rememberUpdatedState(onTap)
    val onPointMoveState by rememberUpdatedState(onPointMove)
    val onEraseState by rememberUpdatedState(onErase)
    val onLassoState by rememberUpdatedState(onLasso)
    val onMeasureTapState by rememberUpdatedState(onMeasureTap)

    val outlineColor = MaterialTheme.colorScheme.outlineVariant
    val strokeColor = MaterialTheme.colorScheme.primary
    val labelBg = MaterialTheme.colorScheme.surface
    val handleFill = MaterialTheme.colorScheme.surface
    val handleStroke = MaterialTheme.colorScheme.onSurface

    fun nearestPoint(list: List<Pair<PointRef, Offset>>, pos: Offset, radius: Float) =
        list.minByOrNull { (it.second - pos).getDistance() }?.takeIf { (it.second - pos).getDistance() <= radius }

    val gestures = when (mode) {
        CanvasMode.DRAW_OUTLINE, CanvasMode.DRAW_ROUTE, CanvasMode.DRAW_EXTRA -> Modifier.pointerInput(mode) {
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
                        val (w, r, v) = current
                        val hitColumn = currentColumns.firstOrNull { (_, cp) ->
                            val c = v.toScreen(cp.center)
                            val half = maxOf(cp.width, cp.depth) / 2
                            val edge = v.toScreen(Point(cp.center.x + cp.u.x * half, cp.center.y + cp.u.y * half))
                            (pos - c).getDistance() <= maxOf((edge - c).getDistance(), hitRadius)
                        }
                        var best: MeasureTarget? = null
                        var bestDist = hitRadius
                        // Инженерия лежит поверх стен — проверяем её первой и с небольшим приоритетом.
                        r.filter { isActive(it.first) }.forEach { (e, pts) ->
                            // Внутри полосы трассы — тоже попадание.
                            val half = ((Footprint.width(e) ?: 0.0) / 2 * pxPerMeterState).toFloat()
                            for (i in 0 until pts.size - 1) {
                                val d = (distanceToSegment(pos, pts[i], pts[i + 1]) - half).coerceAtLeast(0f) * 0.8f
                                if (d < bestDist) { bestDist = d; best = MeasureTarget.EngSegment(e.id, i) }
                            }
                        }
                        if (!engineeringStage) w.forEachIndexed { i, (a, b) ->
                            if (!room.hasWall(i)) return@forEachIndexed
                            val d = distanceToSegment(pos, a, b)
                            if (d < bestDist) { bestDist = d; best = MeasureTarget.Wall(i) }
                        }
                        when (toolState) {
                            Tool.SELECT -> when {
                                hitColumn != null && !engineeringStage -> onColumnTapState(hitColumn.first.id)
                                else -> best?.let { onTapState(it, pos) }
                            }
                            Tool.ERASE -> when {
                                // Касание добавляет линию или колонну в выбор (или убирает из него).
                                best != null -> onEraseState(EraseHit.Segment(best!!))
                                hitColumn != null && !engineeringStage -> onEraseState(EraseHit.ColumnHit(hitColumn.first.id))
                            }
                            Tool.MEASURE -> {
                                // Точка замера прилипает к ближайшей активной точке.
                                val point = nearestPoint(currentPoints, pos, handleRadius)
                                onMeasureTapState(point?.let { v.toPlan(it.second) } ?: v.toPlan(pos))
                            }
                        }
                    },
                )
            }
            .pointerInput("lasso") {
                // Лассо: обводим пальцем — всё внутри петли выбирается к удалению.
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    if (toolState != Tool.ERASE) return@awaitEachGesture
                    stroke.clear()
                    stroke.add(down.position)
                    var pinch = false
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.changes.count { it.pressed } > 1) {
                            pinch = true // двумя пальцами — масштаб, не лассо
                            break
                        }
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        if (change.positionChange() != Offset.Zero) {
                            stroke.add(change.position)
                            change.consume()
                        }
                    }
                    val loop = stroke.toList()
                    stroke.clear()
                    if (pinch || pathLength(loop) < viewConfiguration.touchSlop * 4) return@awaitEachGesture
                    val (_, r, v) = current
                    val picked = mutableSetOf<EraseHit>()
                    r.filter { isActive(it.first) }.forEach { (e, pts) ->
                        for (i in 0 until pts.size - 1) {
                            val mid = Offset((pts[i].x + pts[i + 1].x) / 2, (pts[i].y + pts[i + 1].y) / 2)
                            if (insidePolygon(mid, loop)) picked += EraseHit.Segment(MeasureTarget.EngSegment(e.id, i))
                        }
                    }
                    if (!engineeringStage) {
                        // Стены контура тоже стираются (контур при этом остаётся замкнутым).
                        current.first.forEachIndexed { i, (a, b) ->
                            if (room.hasWall(i) && insidePolygon(Offset((a.x + b.x) / 2, (a.y + b.y) / 2), loop)) {
                                picked += EraseHit.Segment(MeasureTarget.Wall(i))
                            }
                        }
                        currentColumns.forEach { (col, cp) ->
                            if (insidePolygon(v.toScreen(cp.center), loop)) picked += EraseHit.ColumnHit(col.id)
                        }
                    }
                    onLassoState(picked)
                }
            }
            .pointerInput("drag") {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    if (toolState != Tool.SELECT) return@awaitEachGesture
                    val hit = nearestPoint(currentHandles, down.position, handleRadius) ?: return@awaitEachGesture
                    val start = awaitTouchSlopOrCancellation(down.id) { change, _ -> change.consume() }
                        ?: return@awaitEachGesture
                    dragRef = hit.first
                    dragPos = start.position
                    val completed = drag(start.id) { change ->
                        dragPos = change.position
                        change.consume()
                    }
                    if (completed) onPointMoveState(hit.first, current.third.toPlan(dragPos), snapState)
                    dragRef = null
                }
            }
    }

    Box(
        modifier
            .border(1.dp, outlineColor, RoundedCornerShape(12.dp))
            .clipToBounds()
            .onSizeChanged { size = it }
            .then(gestures),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawGrid(outlineColor)
            val n = walls.size
            walls.forEachIndexed { i, (a, b) ->
                if (!shown.hasWall(i)) return@forEachIndexed // стёртая стена — разрыв
                val sel = selected == MeasureTarget.Wall(i)
                drawLine(wallColor(i, n).copy(alpha = wallAlpha), a, b, (if (sel) 9.dp else 5.dp).toPx(), StrokeCap.Round)
            }
            if (!engineeringStage) {
                walls.forEachIndexed { i, (a, b) ->
                    if (!shown.hasWall(i)) return@forEachIndexed
                    val text = shown.walls.getOrNull(i)?.let { "${i + 1}: ${it.millimeters}" } ?: "${i + 1}"
                    label(measurer, text, wallColor(i, n), a, b, outward = true, labelBg)
                }
            }
            routes.forEach { (e, pts) ->
                val active = isActive(e)
                val color = engColor(e).copy(alpha = if (active) 1f else wallAlpha)
                val dash = if (e.type == EngType.DRAIN) PathEffect.dashPathEffect(floatArrayOf(18f, 10f)) else null
                val width = when (e.type) {
                    EngType.WALL -> 5.dp
                    EngType.LINE -> 2.dp
                    else -> 4.dp
                }
                val band = footprints[e]
                if (band != null && band.size >= 4) {
                    val path = Path().apply {
                        moveTo(band[0].x, band[0].y)
                        band.drop(1).forEach { lineTo(it.x, it.y) }
                        close()
                    }
                    drawPath(path, color.copy(alpha = color.alpha * 0.3f))
                    drawPath(path, color, style = Stroke(1.5.dp.toPx(), pathEffect = dash))
                    // Ось и выделенный участок.
                    for (i in 0 until pts.size - 1) {
                        val sel = selected == MeasureTarget.EngSegment(e.id, i)
                        drawLine(
                            color, pts[i], pts[i + 1], (if (sel) 4.dp else 1.dp).toPx(),
                            pathEffect = if (sel) null else PathEffect.dashPathEffect(floatArrayOf(12f, 6f, 3f, 6f)),
                        )
                    }
                } else {
                    for (i in 0 until pts.size - 1) {
                        val sel = selected == MeasureTarget.EngSegment(e.id, i)
                        drawLine(color, pts[i], pts[i + 1], (if (sel) width + 4.dp else width).toPx(), StrokeCap.Round, dash)
                    }
                }
                if (active) for (i in 0 until pts.size - 1) {
                    val len = e.segments.getOrNull(i)?.millimeters
                    val own = e.segmentElevationsMm.getOrNull(i)?.let { "h=$it" }
                    val parts = listOfNotNull(
                        if (i == 0) e.sectionText.ifEmpty { e.type.title } else null,
                        len?.toString(),
                        own,
                    )
                    if (parts.isEmpty()) continue
                    label(measurer, parts.joinToString(" · "), color, pts[i], pts[i + 1], outward = false, labelBg)
                }
                // Перепад высот в узле: кружок и величина.
                if (active) for (i in 0 until pts.size - 2) {
                    val h1 = e.elevationOf(i)
                    val h2 = e.elevationOf(i + 1)
                    if (h1 == null || h2 == null || h1 == h2) continue
                    val j = pts[i + 1]
                    drawCircle(labelBg, 9.dp.toPx(), j)
                    drawCircle(color, 9.dp.toPx(), j, style = Stroke(2.dp.toPx()))
                    val drop = measurer.measure(
                        "↕${kotlin.math.abs(h2 - h1)}",
                        TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, color = color),
                    )
                    drawText(drop, topLeft = Offset(j.x + 11.dp.toPx(), j.y - drop.size.height - 2.dp.toPx()))
                }
            }
            columns.forEach { (col, cp) ->
                drawColumn(toScreen, cp, col, selected, measurer, labelBg, "К${shown.columns.indexOf(col) + 1}", faded = engineeringStage)
            }
            // Выбранное к удалению — красный пунктир поверх.
            if (pendingErase.isNotEmpty()) {
                val red = Color(0xFFE53935)
                val dash = PathEffect.dashPathEffect(floatArrayOf(14f, 8f))
                pendingErase.forEach { hit ->
                    when (hit) {
                        is EraseHit.Segment -> {
                            (hit.target as? MeasureTarget.Wall)?.let { w ->
                                walls.getOrNull(w.index)?.let { (a, b) -> drawLine(red, a, b, 9.dp.toPx(), StrokeCap.Round, dash) }
                                return@forEach
                            }
                            val t = hit.target as? MeasureTarget.EngSegment ?: return@forEach
                            val pts = routes.firstOrNull { it.first.id == t.elementId }?.second ?: return@forEach
                            if (t.index + 1 >= pts.size) return@forEach
                            drawLine(red, pts[t.index], pts[t.index + 1], 7.dp.toPx(), StrokeCap.Round, dash)
                        }
                        is EraseHit.ColumnHit -> {
                            val cp = columns.firstOrNull { it.first.id == hit.id }?.second ?: return@forEach
                            val c = toScreen(cp.center)
                            val edge = toScreen(Point(cp.center.x + cp.u.x * maxOf(cp.width, cp.depth) * 0.75, cp.center.y + cp.u.y * maxOf(cp.width, cp.depth) * 0.75))
                            drawCircle(red, maxOf((edge - c).getDistance(), 14.dp.toPx()), c, style = Stroke(3.dp.toPx(), pathEffect = dash))
                        }
                        is EraseHit.PointHit -> Unit
                    }
                }
            }
            // Активные точки — за них можно тянуть, к ним прилипают линии и линейка.
            if (mode == CanvasMode.VIEW && tool != Tool.ERASE) {
                handles.forEach { (r, p) ->
                    val active = r == dragRef
                    val radius = (if (active) 8.dp else 5.dp).toPx()
                    drawCircle(if (active) strokeColor else handleFill, radius, p)
                    drawCircle(handleStroke, radius, p, style = Stroke(1.5.dp.toPx()))
                }
            }
            // Линейка.
            if (tool == Tool.MEASURE && plan != null) {
                val a = measureA?.let(toScreen)
                val b = measureB?.let(toScreen)
                if (measureA != null && measureB != null && a != null && b != null) {
                    drawLine(strokeColor, a, b, 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 6f)))
                    val meters = kotlin.math.hypot(measureB.x - measureA.x, measureB.y - measureA.y)
                    val text = (if (plan.estimated) "≈ " else "") + fmtMm(meters)
                    label(measurer, text, strokeColor, a, b, outward = true, labelBg)
                }
                listOfNotNull(a, b).forEach { drawCircle(strokeColor, 6.dp.toPx(), it) }
            }
            if (stroke.size > 1) {
                val path = Path().apply {
                    moveTo(stroke[0].x, stroke[0].y)
                    for (i in 1 until stroke.size) lineTo(stroke[i].x, stroke[i].y)
                }
                val lasso = tool == Tool.ERASE && mode == CanvasMode.VIEW
                drawPath(
                    path,
                    if (lasso) Color(0xFFE53935) else strokeColor,
                    style = Stroke(
                        (if (lasso) 2.dp else 4.dp).toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round,
                        pathEffect = if (lasso) PathEffect.dashPathEffect(floatArrayOf(12f, 8f)) else null,
                    ),
                )
            }
        }
    }
}

internal val ColumnColor = Color(0xFFD32F2F)
private val AxisColor = Color(0xFFE57373)

/** Колонна: контур с заливкой, штриховые оси на всю комнату и размерные линии привязок. */
private fun DrawScope.drawColumn(
    toScreen: (Point) -> Offset,
    cp: ColumnPlan,
    col: Column,
    selected: MeasureTarget?,
    measurer: TextMeasurer,
    bg: Color,
    columnTitle: String,
    faded: Boolean,
) {
    val sel = (selected as? MeasureTarget.ColumnValue)?.columnId == col.id
    val k = if (faded) 0.3f else 1f
    val cc = ColumnColor.copy(alpha = k)
    val ac = AxisColor.copy(alpha = 0.6f * k)
    val c = toScreen(cp.center)
    // Оси — тонкие штриховые линии через центр, далеко за пределы колонны.
    val dash = PathEffect.dashPathEffect(floatArrayOf(14f, 8f, 3f, 8f))
    val reach = 50.0
    for (dir in listOf(cp.u, cp.v)) {
        val a = toScreen(Point(cp.center.x - dir.x * reach, cp.center.y - dir.y * reach))
        val b = toScreen(Point(cp.center.x + dir.x * reach, cp.center.y + dir.y * reach))
        drawLine(ac, a, b, 1.dp.toPx(), pathEffect = dash)
    }
    val stroke = (if (sel) 4.dp else 2.5.dp).toPx()
    if (cp.round) {
        val r = (toScreen(Point(cp.center.x + cp.width / 2 * cp.u.x, cp.center.y + cp.width / 2 * cp.u.y)) - c).getDistance()
        drawCircle(cc.copy(alpha = 0.25f * k), r, c)
        drawCircle(cc, r, c, style = Stroke(stroke))
    } else {
        val path = Path().apply {
            val pts = cp.corners.map(toScreen)
            moveTo(pts[0].x, pts[0].y)
            pts.drop(1).forEach { lineTo(it.x, it.y) }
            close()
        }
        drawPath(path, cc.copy(alpha = 0.25f * k))
        drawPath(path, cc, style = Stroke(stroke))
    }
    if (faded) return
    // Привязки: линия от стены до оси с длиной.
    listOf(cp.footU to col.offsetU, cp.footV to col.offsetV).forEach { (foot, m) ->
        if (foot == null) return@forEach
        val f = toScreen(foot)
        drawLine(cc, f, c, 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
        drawCircle(cc, 3.dp.toPx(), f)
        val meters = kotlin.math.hypot(cp.center.x - foot.x, cp.center.y - foot.y)
        val text = m?.millimeters?.toString() ?: "~${Math.round(meters * 1000)}"
        label(measurer, text, cc, f, c, outward = true, bg)
    }
    val name = columnTitle + (col.sizeText?.let { " $it" } ?: "")
    val layout = measurer.measure(name, TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold, color = cc))
    drawText(layout, topLeft = Offset(c.x - layout.size.width / 2f, c.y - layout.size.height / 2f))
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

/** Точка внутри многоугольника (лучевой метод); петля лассо замыкается сама. */
private fun insidePolygon(p: Offset, poly: List<Offset>): Boolean {
    if (poly.size < 3) return false
    var inside = false
    var j = poly.lastIndex
    for (i in poly.indices) {
        val a = poly[i]
        val b = poly[j]
        if ((a.y > p.y) != (b.y > p.y) && p.x < (b.x - a.x) * (p.y - a.y) / (b.y - a.y) + a.x) inside = !inside
        j = i
    }
    return inside
}

private fun pathLength(pts: List<Offset>): Float = pts.zipWithNext { a, b -> (b - a).getDistance() }.sum()

private fun distanceToSegment(p: Offset, a: Offset, b: Offset): Float {
    val dx = b.x - a.x
    val dy = b.y - a.y
    val len2 = dx * dx + dy * dy
    if (len2 == 0f) return (p - a).getDistance()
    val t = (((p.x - a.x) * dx + (p.y - a.y) * dy) / len2).coerceIn(0f, 1f)
    return (p - Offset(a.x + t * dx, a.y + t * dy)).getDistance()
}
