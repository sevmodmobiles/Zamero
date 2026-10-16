package app.zamero.data

import android.content.Context
import app.zamero.core.Column
import app.zamero.core.Discipline
import app.zamero.core.EngElement
import app.zamero.core.EngType
import app.zamero.core.Measure
import app.zamero.core.Opening
import app.zamero.core.OpeningType
import app.zamero.core.Point
import app.zamero.core.Project
import app.zamero.core.Room
import app.zamero.core.Source
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Хранит все объекты обмера в JSON-файле во внутренней памяти приложения. */
class ProjectStore(context: Context) {
    private val file = File(context.filesDir, "projects.json")
    private val prefs = context.getSharedPreferences("zamero", Context.MODE_PRIVATE)

    /** Исполнитель из последнего объекта — подставляем по умолчанию. */
    var lastContractor: String
        get() = prefs.getString("contractor", "") ?: ""
        set(value) = prefs.edit().putString("contractor", value).apply()

    fun load(): List<Project> = runCatching {
        if (file.exists()) JSONArray(file.readText()).objects().map { it.toProject() } else emptyList()
    }.getOrDefault(emptyList())

    @Synchronized
    fun save(projects: List<Project>) {
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(JSONArray(projects.map { it.toJson() }).toString())
        tmp.renameTo(file)
    }

    private fun Project.toJson() = JSONObject()
        .put("id", id)
        .put("address", address)
        .put("customer", customer)
        .put("contractor", contractor)
        .put("createdAt", createdAt)
        .put("rooms", JSONArray(rooms.map { it.toJson() }))

    private fun Room.toJson() = JSONObject()
        .put("id", id)
        .put("name", name)
        .put("outline", JSONArray(outline.map { JSONArray().put(it.x).put(it.y) }))
        .put("walls", JSONArray(walls.map { it.toJson() ?: JSONObject.NULL }))
        .put("height", height.toJson() ?: JSONObject.NULL)
        .put(
            "openings",
            JSONArray(
                openings.map { o ->
                    JSONObject()
                        .put("type", o.type.name)
                        .put("width", o.width.toJson() ?: JSONObject.NULL)
                        .put("height", o.height.toJson() ?: JSONObject.NULL)
                },
            ),
        )
        .put("engineering", JSONArray(engineering.map { it.toJson() }))
        .put("columns", JSONArray(columns.map { it.toJson() }))

    private fun Column.toJson() = JSONObject()
        .put("id", id)
        .put("round", round)
        .put("center", JSONArray().put(center.x).put(center.y))
        .put("approxW", approxWidth)
        .put("approxD", approxDepth)
        .put("width", width.toJson() ?: JSONObject.NULL)
        .put("depth", depth.toJson() ?: JSONObject.NULL)
        .put("offsetU", offsetU.toJson() ?: JSONObject.NULL)
        .put("offsetV", offsetV.toJson() ?: JSONObject.NULL)
        .put("refU", refWallU)
        .put("refV", refWallV)

    private fun JSONObject.toColumn() = Column(
        id = getString("id"),
        round = getBoolean("round"),
        center = getJSONArray("center").let { Point(it.getDouble(0), it.getDouble(1)) },
        approxWidth = getDouble("approxW"),
        approxDepth = getDouble("approxD"),
        width = optJSONObject("width").toMeasure(),
        depth = optJSONObject("depth").toMeasure(),
        offsetU = optJSONObject("offsetU").toMeasure(),
        offsetV = optJSONObject("offsetV").toMeasure(),
        refWallU = optInt("refU", -1),
        refWallV = optInt("refV", -1),
    )

    private fun EngElement.toJson() = JSONObject()
        .put("id", id)
        .put("type", type.name)
        .put("route", JSONArray(route.map { JSONArray().put(it.x).put(it.y) }))
        .put("segments", JSONArray(segments.map { it.toJson() ?: JSONObject.NULL }))
        .put("diameter", diameterMm ?: JSONObject.NULL)
        .put("width", widthMm ?: JSONObject.NULL)
        .put("height", heightMm ?: JSONObject.NULL)
        .put("elevation", elevationMm ?: JSONObject.NULL)
        .put("note", note)
        .put("system", system?.name ?: JSONObject.NULL)

    private fun JSONObject.toEng(): EngElement {
        val type = EngType.valueOf(getString("type"))
        val route = getJSONArray("route").points()
        val segs = getJSONArray("segments").let { arr -> List(arr.length()) { arr.optJSONObject(it).toMeasure() } }
        return EngElement(
            id = getString("id"),
            type = type,
            route = route,
            segments = List((route.size - 1).coerceAtLeast(0)) { segs.getOrNull(it) },
            diameterMm = optIntOrNull("diameter"),
            widthMm = optIntOrNull("width"),
            heightMm = optIntOrNull("height"),
            elevationMm = optIntOrNull("elevation"),
            note = optString("note"),
            system = if (isNull("system") || !has("system")) Discipline.defaultFor(type) else Discipline.valueOf(getString("system")),
        )
    }

    private fun JSONObject.optIntOrNull(key: String): Int? = if (isNull(key)) null else optInt(key)

    private fun JSONArray.points(): List<Point> =
        List(length()) { i -> getJSONArray(i).let { Point(it.getDouble(0), it.getDouble(1)) } }

    private fun Measure?.toJson(): JSONObject? =
        this?.let { JSONObject().put("m", it.meters).put("src", it.source.name) }

    private fun JSONObject.toProject() = Project(
        id = getString("id"),
        address = getString("address"),
        customer = optString("customer"),
        contractor = optString("contractor"),
        createdAt = optLong("createdAt"),
        rooms = getJSONArray("rooms").objects().map { it.toRoom() },
    )

    private fun JSONObject.toRoom(): Room {
        val outline = getJSONArray("outline").points()
        val walls = getJSONArray("walls").let { arr -> List(arr.length()) { arr.optJSONObject(it).toMeasure() } }
        return Room(
            id = getString("id"),
            name = getString("name"),
            outline = outline,
            walls = List(outline.size) { walls.getOrNull(it) },
            height = optJSONObject("height").toMeasure(),
            openings = getJSONArray("openings").objects().map { o ->
                Opening(
                    type = OpeningType.valueOf(o.getString("type")),
                    width = o.optJSONObject("width").toMeasure(),
                    height = o.optJSONObject("height").toMeasure(),
                )
            },
            engineering = optJSONArray("engineering")?.objects()?.map { it.toEng() } ?: emptyList(),
            columns = optJSONArray("columns")?.objects()?.map { it.toColumn() } ?: emptyList(),
        )
    }

    private fun JSONObject?.toMeasure(): Measure? =
        this?.let { Measure(it.getDouble("m"), Source.valueOf(it.getString("src"))) }

    private fun JSONArray.objects(): List<JSONObject> = List(length()) { getJSONObject(it) }
}
