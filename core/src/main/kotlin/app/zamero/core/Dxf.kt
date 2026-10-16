package app.zamero.core

import java.nio.charset.Charset
import java.util.Locale
import kotlin.math.atan2

/**
 * Экспорт обмера в DXF (формат AutoCAD R12 — открывается любой версией AutoCAD,
 * nanoCAD, BricsCAD, LibreCAD). Единицы — миллиметры. Помещения раскладываются
 * в ряд слева направо, у каждого — стены, размеры, проёмы списком и инженерия по слоям.
 */
object Dxf {
    /** Кодировка текста в DXF R12 для кириллицы. */
    val CHARSET: Charset = Charset.forName("windows-1251")

    private const val GAP_MM = 1500.0
    private const val TEXT_MM = 150.0
    private const val SMALL_TEXT_MM = 100.0

    private data class Layer(val name: String, val color: Int)

    private val baseLayers = listOf(
        Layer("WALLS", 7),
        Layer("DIMENSIONS", 2),
        Layer("TEXT", 7),
        Layer("COLUMNS", 1),
        Layer("AXES", 8),
    )

    fun export(project: Project): ByteArray = build(project).toByteArray(CHARSET)

    fun build(project: Project): String {
        val w = Writer()
        val layers = baseLayers +
            EngType.entries.filter { it.category == Category.CONSTRUCTION }.map { Layer(it.layer, it.dxfColor) } +
            Discipline.choices.flatMap { (d, types) -> types.map { Layer("${d.code}_${it.layer}", it.dxfColor) } }

        w.section("HEADER") {
            pair(9, "\$ACADVER"); pair(1, "AC1009")
            pair(9, "\$DWGCODEPAGE"); pair(3, "ANSI_1251")
        }
        w.section("TABLES") {
            pair(0, "TABLE"); pair(2, "LAYER"); pair(70, layers.size)
            layers.forEach { l ->
                pair(0, "LAYER"); pair(2, l.name); pair(70, 0); pair(62, l.color); pair(6, "CONTINUOUS")
            }
            pair(0, "ENDTAB")
        }
        w.section("ENTITIES") {
            var offsetX = 0.0
            text(0.0, 900.0, TEXT_MM * 1.5, "Обмер: ${project.address}", "TEXT")
            val info = listOfNotNull(
                project.customer.takeIf { it.isNotBlank() }?.let { "Заказчик: $it" },
                project.contractor.takeIf { it.isNotBlank() }?.let { "Исполнитель: $it" },
            ).joinToString("   ")
            if (info.isNotEmpty()) text(0.0, 600.0, TEXT_MM, info, "TEXT")

            for (room in project.rooms) {
                val plan = Plan.room(room) ?: continue
                // В DXF ось Y направлена вверх, на экране — вниз: отражаем.
                val minX = plan.vertices.minOf { it.x }
                val maxY = plan.vertices.maxOf { it.y }
                fun tx(p: Point) = Point(offsetX + (p.x - minX) * 1000, -(p.y - maxY) * 1000 - 0.0)
                val width = (plan.vertices.maxOf { it.x } - minX) * 1000
                val height = (maxY - plan.vertices.minOf { it.y }) * 1000

                // Название и площадь над помещением.
                val stats = Geometry.stats(room)
                val title = buildString {
                    append(room.name)
                    stats.floorArea?.let { append(" — ").append("%.2f".format(Locale.ROOT, it)).append(" м²") }
                    if (plan.estimated) append(" (не все стены измерены)")
                }
                text(offsetX, height + 500, TEXT_MM, title, "TEXT")

                // Стены одной полилинией.
                polyline(plan.vertices.map(::tx), closed = true, layer = "WALLS")
                // Размеры стен.
                plan.walls.forEach { wall ->
                    val a = tx(wall.a)
                    val b = tx(wall.b)
                    val mid = Point((a.x + b.x) / 2, (a.y + b.y) / 2)
                    var angle = Math.toDegrees(atan2(b.y - a.y, b.x - a.x))
                    if (angle > 90) angle -= 180
                    if (angle <= -90) angle += 180
                    val len = wall.measured?.millimeters?.toString() ?: "~${Math.round(Plan.length(wall) * 1000)}"
                    text(mid.x, mid.y + 60, SMALL_TEXT_MM, len, "DIMENSIONS", angle)
                }
                // Высота и проёмы — списком под помещением.
                var listY = -400.0
                room.height?.let {
                    text(offsetX, listY, SMALL_TEXT_MM, "Высота: ${it.millimeters} мм", "TEXT"); listY -= 180
                }
                room.openings.forEachIndexed { i, o ->
                    val size = "${o.width?.millimeters ?: "?"}×${o.height?.millimeters ?: "?"}"
                    text(offsetX, listY, SMALL_TEXT_MM, "${MeasureFlow.openingTitle(room, i)}: $size мм", "TEXT"); listY -= 180
                }
                // Инженерия на своих слоях.
                room.engineering.filter { it.hasRoute }.forEach { e ->
                    val meters = Plan.route(room, plan, e)
                    val pts = meters.map(::tx)
                    val w = Footprint.width(e)
                    if (w != null) {
                        // Проекция на пол в реальную ширину, ось — на слое осей.
                        polyline(Footprint.outline(meters, w).map(::tx), closed = true, layer = e.dxfLayer)
                        polyline(pts, closed = false, layer = "AXES")
                    } else {
                        polyline(pts, closed = false, layer = e.dxfLayer)
                    }
                    val a = pts[0]
                    val b = pts[1]
                    val caption = (e.system?.let { "${it.title}: " } ?: "") + e.label
                    text((a.x + b.x) / 2, (a.y + b.y) / 2 + 60, SMALL_TEXT_MM, caption, e.dxfLayer)
                }
                // Колонны: контур, оси и привязки к стенам.
                room.columns.forEachIndexed { i, col ->
                    val cp = Columns.plan(room, plan, col)
                    val c = tx(cp.center)
                    if (cp.round) {
                        circle(c, cp.width * 500, "COLUMNS")
                    } else {
                        polyline(cp.corners.map(::tx), closed = true, layer = "COLUMNS")
                    }
                    val reach = maxOf(cp.width, cp.depth) / 2 + 0.4
                    for (dir in listOf(cp.u, cp.v)) {
                        val a = tx(Point(cp.center.x - dir.x * reach, cp.center.y - dir.y * reach))
                        val b = tx(Point(cp.center.x + dir.x * reach, cp.center.y + dir.y * reach))
                        polyline(listOf(a, b), closed = false, layer = "AXES")
                    }
                    val name = "К${i + 1}" + (col.sizeText?.let { " $it" } ?: "")
                    text(c.x, c.y + (cp.depth * 500) + 120, SMALL_TEXT_MM, name, "COLUMNS")
                    listOf(cp.footU to col.offsetU, cp.footV to col.offsetV).forEach { (foot, m) ->
                        if (foot == null) return@forEach
                        val f = tx(foot)
                        polyline(listOf(f, c), closed = false, layer = "DIMENSIONS")
                        val len = m?.millimeters?.toString()
                            ?: "~${Math.round(kotlin.math.hypot(cp.center.x - foot.x, cp.center.y - foot.y) * 1000)}"
                        var angle = Math.toDegrees(atan2(c.y - f.y, c.x - f.x))
                        if (angle > 90) angle -= 180
                        if (angle <= -90) angle += 180
                        text((f.x + c.x) / 2, (f.y + c.y) / 2 + 60, SMALL_TEXT_MM, len, "DIMENSIONS", angle)
                    }
                }
                offsetX += width + GAP_MM
            }
        }
        w.line(0, "EOF")
        return w.toString()
    }

    private class Writer {
        private val sb = StringBuilder()

        fun line(code: Int, value: String) {
            sb.append(code).append("\r\n").append(value).append("\r\n")
        }

        fun pair(code: Int, value: String) = line(code, value)
        fun pair(code: Int, value: Int) = line(code, value.toString())
        fun pair(code: Int, value: Double) = line(code, "%.3f".format(Locale.ROOT, value))

        fun section(name: String, body: Writer.() -> Unit) {
            pair(0, "SECTION"); pair(2, name)
            body()
            pair(0, "ENDSEC")
        }

        fun text(x: Double, y: Double, h: Double, value: String, layer: String, rotation: Double = 0.0) {
            pair(0, "TEXT"); pair(8, layer)
            pair(10, x); pair(20, y); pair(30, 0.0)
            pair(40, h); pair(1, cadText(value))
            if (rotation != 0.0) pair(50, rotation)
            // Выравнивание по центру, чтобы подписи стояли над серединой стены.
            pair(72, 1); pair(11, x); pair(21, y); pair(31, 0.0)
        }

        fun circle(center: Point, radius: Double, layer: String) {
            pair(0, "CIRCLE"); pair(8, layer)
            pair(10, center.x); pair(20, center.y); pair(30, 0.0)
            pair(40, radius)
        }

        fun polyline(points: List<Point>, closed: Boolean, layer: String) {
            if (points.size < 2) return
            pair(0, "POLYLINE"); pair(8, layer); pair(66, 1)
            pair(10, 0.0); pair(20, 0.0); pair(30, 0.0)
            pair(70, if (closed) 1 else 0)
            points.forEach { p ->
                pair(0, "VERTEX"); pair(8, layer)
                pair(10, p.x); pair(20, p.y); pair(30, 0.0)
            }
            pair(0, "SEQEND"); pair(8, layer)
        }

        override fun toString() = sb.toString()
    }

    /** Символы, которых нет в windows-1251, заменяем на коды AutoCAD или ASCII. */
    fun cadText(value: String): String = value
        .replace("Ø", "%%c")
        .replace('×', 'x')
        .replace('≈', '~')
        .replace("²", "2")
        .replace("³", "3")
        .replace('↔', '-')
        .replace('↕', '|')
        .replace('\n', ' ')
}
