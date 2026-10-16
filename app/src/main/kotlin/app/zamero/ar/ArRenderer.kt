package app.zamero.ar

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import com.google.ar.core.Anchor
import com.google.ar.core.DepthPoint
import com.google.ar.core.Frame
import com.google.ar.core.HitResult
import com.google.ar.core.Plane
import com.google.ar.core.Point
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
    /** Расстояние от начальной точки до прицела, м. */
    val liveDistance: Double?,
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
            Action.PLACE -> if (hit != null) {
                val a = start
                if (a == null) {
                    start = hit.createAnchor()
                } else {
                    onMeasured(distance(a.pose, hit.hitPose))
                }
            }
            Action.RESET -> {
                start?.detach()
                start = null
            }
            null -> Unit
        }

        val a = start?.takeIf { it.trackingState == TrackingState.TRACKING }
        onFrame(
            ArFrameState(
                tracking = true,
                onSurface = hit != null,
                startPlaced = start != null,
                liveDistance = if (a != null && hit != null) distance(a.pose, hit.hitPose) else null,
            ),
        )
    }

    private fun centerHit(frame: Frame): HitResult? =
        frame.hitTest(width / 2f, height / 2f).firstOrNull { hit ->
            when (val t = hit.trackable) {
                is Plane -> t.isPoseInPolygon(hit.hitPose)
                is DepthPoint -> true
                is Point -> t.orientationMode == Point.OrientationMode.ESTIMATED_SURFACE_NORMAL
                else -> false
            }
        }

    private fun distance(a: Pose, b: Pose): Double {
        val dx = a.tx() - b.tx()
        val dy = a.ty() - b.ty()
        val dz = a.tz() - b.tz()
        return sqrt((dx * dx + dy * dy + dz * dz).toDouble())
    }
}
