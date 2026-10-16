package app.zamero.ar

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import com.google.ar.core.Anchor
import com.google.ar.core.DepthPoint
import com.google.ar.core.Frame
import com.google.ar.core.HitResult
import com.google.ar.core.Plane
import com.google.ar.core.Pose
import com.google.ar.core.Session
import com.google.ar.core.TrackingState
import java.util.concurrent.atomic.AtomicReference
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.sqrt

/** Что сейчас видит прицел — передаётся в UI на каждом кадре. */
data class ArFrameState(
    val tracking: Boolean,
    /** Прицел попадает на распознанную поверхность. */
    val onSurface: Boolean,
    val startPlaced: Boolean,
    /** Расстояние от начальной точки до прицела, м (сглаженное). */
    val liveDistance: Double?,
    /** Идёт усреднение точки — телефон нужно держать неподвижно. */
    val sampling: Boolean = false,
    /** Расстояние от камеры до прицела, м: чем дальше, тем ниже точность. */
    val range: Double? = null,
)

/**
 * Замер «через видоискатель»: в центре экрана прицел. Первое нажатие ставит
 * начальную точку на поверхности, второе фиксирует расстояние до прицела.
 */
class ArRenderer(
    private val displayRotation: () -> Int,
    private val onFrame: (ArFrameState) -> Unit,
    private val onMeasured: (Double) -> Unit,
) : GLSurfaceView.Renderer {

    enum class Action { PLACE, RESET }

    @Volatile
    var session: Session? = null

    private val pending = AtomicReference<Action?>(null)
    private val background = BackgroundRenderer()
    private var textureBoundTo: Session? = null
    private var width = 0
    private var height = 0
    private var viewportChanged = false
    private var start: Anchor? = null
    /** Накопленные положения прицела для усреднения точки. */
    private var samples: MutableList<FloatArray>? = null
    private val liveWindow = ArrayDeque<Double>()

    fun request(action: Action) = pending.set(action)

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        background.createOnGlThread()
        textureBoundTo = null
    }

    override fun onSurfaceChanged(gl: GL10?, w: Int, h: Int) {
        GLES20.glViewport(0, 0, w, h)
        width = w
        height = h
        viewportChanged = true
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        val s = session ?: return
        if (textureBoundTo !== s) {
            s.setCameraTextureName(background.textureId)
            textureBoundTo = s
            viewportChanged = true
        }
        if (viewportChanged && width > 0) {
            s.setDisplayGeometry(displayRotation(), width, height)
            viewportChanged = false
        }
        val frame = runCatching { s.update() }.getOrNull() ?: return
        background.draw(frame)

        if (frame.camera.trackingState != TrackingState.TRACKING) {
            onFrame(ArFrameState(tracking = false, onSurface = false, startPlaced = start != null, liveDistance = null))
            return
        }

        val hit = centerHit(frame)
        when (pending.getAndSet(null)) {
            Action.PLACE -> if (samples == null) samples = mutableListOf()
            Action.RESET -> {
                start?.detach()
                start = null
                samples = null
                liveWindow.clear()
            }
            null -> Unit
        }

        // Точку ставим не по одному кадру, а по медиане нескольких — дрожание руки и шум
        // глубины гасятся.
        val acc = samples
        if (acc != null && hit != null) {
            acc += hit.hitPose.translation
            if (acc.size >= SAMPLES) {
                val p = median(acc)
                samples = null
                val a = start
                if (a == null) {
                    start = s.createAnchor(Pose.makeTranslation(p))
                    liveWindow.clear()
                } else {
                    onMeasured(distance(a.pose.translation, p))
                }
            }
        }

        val a = start?.takeIf { it.trackingState == TrackingState.TRACKING }
        val live = if (a != null && hit != null) {
            liveWindow.addLast(distance(a.pose.translation, hit.hitPose.translation))
            if (liveWindow.size > LIVE_WINDOW) liveWindow.removeFirst()
            liveWindow.sorted()[liveWindow.size / 2]
        } else {
            null
        }
        onFrame(
            ArFrameState(
                tracking = true,
                onSurface = hit != null,
                startPlaced = start != null,
                liveDistance = live,
                sampling = samples != null,
                range = hit?.let { distance(frame.camera.pose.translation, it.hitPose.translation) },
            ),
        )
    }

    /**
     * Попадание прицела: сначала распознанные плоскости (самые точные), затем карта
     * глубины. Отдельные «точки-особенности» не используем — они сильно шумят.
     */
    private fun centerHit(frame: Frame): HitResult? {
        val hits = frame.hitTest(width / 2f, height / 2f)
        return hits.firstOrNull { hit ->
            val t = hit.trackable
            t is Plane && t.trackingState == TrackingState.TRACKING && t.subsumedBy == null && t.isPoseInPolygon(hit.hitPose)
        } ?: hits.firstOrNull { it.trackable is DepthPoint }
    }

    private fun median(points: List<FloatArray>): FloatArray =
        FloatArray(3) { k -> points.map { it[k] }.sorted()[points.size / 2] }

    private fun distance(a: FloatArray, b: FloatArray): Double {
        val dx = a[0] - b[0]
        val dy = a[1] - b[1]
        val dz = a[2] - b[2]
        return sqrt((dx * dx + dy * dy + dz * dz).toDouble())
    }

    private companion object {
        /** Кадров на одну точку (~0.5 с при 30 к/с). */
        const val SAMPLES = 15
        const val LIVE_WINDOW = 9
    }
}
