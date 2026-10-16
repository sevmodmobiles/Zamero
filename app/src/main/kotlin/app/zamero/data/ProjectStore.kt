package app.zamero.data

import android.content.Context
import app.zamero.core.Measure
import app.zamero.core.Opening
import app.zamero.core.OpeningType
import app.zamero.core.Project
import app.zamero.core.Room
import app.zamero.core.Shape
import app.zamero.core.Source
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Хранит объект обмера в JSON-файле во внутренней памяти приложения. */
class ProjectStore(context: Context) {
    private val file = File(context.filesDir, "project.json")

    fun load(): Project? = runCatching {
        if (file.exists()) JSONObject(file.readText()).toProject() else null
    }.getOrNull()

    @Synchronized
    fun save(project: Project) {
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(project.toJson().toString())
        tmp.renameTo(file)
    }

    private fun Project.toJson() = JSONObject()
        .put("id", id)
        .put("name", name)
        .put("rooms", JSONArray(rooms.map { it.toJson() }))

    private fun Room.toJson() = JSONObject()
        .put("id", id)
        .put("name", name)
        .put("shape", shape.name)
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

    private fun Measure?.toJson(): JSONObject? =
        this?.let { JSONObject().put("m", it.meters).put("src", it.source.name) }

    private fun JSONObject.toProject() = Project(
        id = getString("id"),
        name = getString("name"),
        rooms = getJSONArray("rooms").objects().map { it.toRoom() },
    )

    private fun JSONObject.toRoom(): Room {
        val shape = Shape.valueOf(getString("shape"))
        val walls = getJSONArray("walls").let { arr -> List(arr.length()) { arr.optJSONObject(it).toMeasure() } }
        return Room(
            id = getString("id"),
            name = getString("name"),
            shape = shape,
            walls = List(shape.wallCount) { walls.getOrNull(it) },
            height = optJSONObject("height").toMeasure(),
            openings = getJSONArray("openings").objects().map { o ->
                Opening(
                    type = OpeningType.valueOf(o.getString("type")),
                    width = o.optJSONObject("width").toMeasure(),
                    height = o.optJSONObject("height").toMeasure(),
                )
            },
        )
    }

    private fun JSONObject?.toMeasure(): Measure? =
        this?.let { Measure(it.getDouble("m"), Source.valueOf(it.getString("src"))) }

    private fun JSONArray.objects(): List<JSONObject> = List(length()) { getJSONObject(it) }
}
