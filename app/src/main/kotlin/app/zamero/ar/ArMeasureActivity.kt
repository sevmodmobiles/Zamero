package app.zamero.ar

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.opengl.GLSurfaceView
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import app.zamero.ui.theme.ZameroTheme
import com.google.ar.core.ArCoreApk
import com.google.ar.core.Config
import com.google.ar.core.Session
import com.google.ar.core.exceptions.CameraNotAvailableException
import com.google.ar.core.exceptions.UnavailableException
import java.util.Locale

/** Экран замера камерой. Возвращает расстояние в метрах через [Contract]. */
class ArMeasureActivity : ComponentActivity() {

    private var session: Session? = null
    private var installRequested = false
    private lateinit var glView: GLSurfaceView
    private lateinit var renderer: ArRenderer

    private var frameState by mutableStateOf(ArFrameState(false, false, false, null))
    private var measured by mutableStateOf<Double?>(null)
    private var fatalError by mutableStateOf<String?>(null)

    private val cameraPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) fatalError = "Без доступа к камере замер через видоискатель невозможен"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        renderer = ArRenderer(
            displayRotation = ::displayRotation,
            onFrame = { state -> runOnUiThread { frameState = state } },
            onMeasured = { d -> runOnUiThread { measured = d } },
        )
        glView = GLSurfaceView(this).apply {
            preserveEGLContextOnPause = true
            setEGLContextClientVersion(2)
            setEGLConfigChooser(8, 8, 8, 8, 16, 0)
            setRenderer(renderer)
            renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
        }

        setContent { ZameroTheme { Screen() } }
    }

    override fun onResume() {
        super.onResume()
        if (fatalError != null) return
        if (session == null) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                cameraPermission.launch(Manifest.permission.CAMERA)
                return
            }
            try {
                if (ArCoreApk.getInstance().requestInstall(this, !installRequested) ==
                    ArCoreApk.InstallStatus.INSTALL_REQUESTED
                ) {
                    installRequested = true
                    return
                }
                session = Session(this).also { it.configure(configFor(it)) }
            } catch (e: UnavailableException) {
                fatalError = "Это устройство не поддерживает замер камерой (ARCore). " +
                    "Используйте рулетку или ручной ввод."
                return
            }
        }
        try {
            session?.resume()
        } catch (e: CameraNotAvailableException) {
            fatalError = "Камера занята другим приложением"
            session = null
            return
        }
        renderer.session = session
        glView.onResume()
    }

    override fun onPause() {
        super.onPause()
        if (session != null) {
            glView.onPause()
            session?.pause()
        }
    }

    override fun onDestroy() {
        renderer.session = null
        session?.close()
        session = null
        super.onDestroy()
    }

    private fun configFor(s: Session) = Config(s).apply {
        planeFindingMode = Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL
        focusMode = Config.FocusMode.AUTO
        updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
        // Карта глубины даёт точки на стенах без текстуры, если телефон её поддерживает.
        depthMode = if (s.isDepthModeSupported(Config.DepthMode.AUTOMATIC)) {
            Config.DepthMode.AUTOMATIC
        } else {
            Config.DepthMode.DISABLED
        }
    }

    private fun displayRotation(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            display?.rotation ?: 0
        } else {
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.rotation
        }

    private fun finishWith(meters: Double) {
        setResult(Activity.RESULT_OK, Intent().putExtra(EXTRA_METERS, meters))
        finish()
    }

    @androidx.compose.runtime.Composable
    private fun Screen() {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            AndroidView(factory = { glView }, modifier = Modifier.fillMaxSize())
            Crosshair(
                active = frameState.onSurface,
                modifier = Modifier.align(Alignment.Center),
            )
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.6f))
                    .navigationBarsPadding()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val error = fatalError
                val result = measured
                when {
                    error != null -> {
                        Text(error, color = Color.White)
                        Button(onClick = ::finish) { Text("Закрыть") }
                    }
                    result != null -> {
                        Text(meters(result), color = Color.White, style = MaterialTheme.typography.displaySmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedButton(onClick = {
                                measured = null
                                renderer.request(ArRenderer.Action.RESET)
                            }) { Text("Заново", color = Color.White) }
                            Button(onClick = { finishWith(result) }) { Text("Записать") }
                        }
                    }
                    else -> {
                        Text(hint(frameState), color = Color.White)
                        frameState.range?.takeIf { it > MAX_GOOD_RANGE_M }?.let {
                            Text("Подойдите ближе: дальше ${MAX_GOOD_RANGE_M.toInt()} м точность заметно падает", color = Color(0xFFFFB74D))
                        }
                        frameState.liveDistance?.let {
                            Text(meters(it), color = Color.White, style = MaterialTheme.typography.displaySmall)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            if (frameState.startPlaced) {
                                OutlinedButton(onClick = { renderer.request(ArRenderer.Action.RESET) }) {
                                    Text("Сбросить", color = Color.White)
                                }
                            }
                            Button(
                                onClick = { renderer.request(ArRenderer.Action.PLACE) },
                                enabled = frameState.tracking && frameState.onSurface && !frameState.sampling,
                            ) { Text(if (frameState.sampling) "Замер…" else if (frameState.startPlaced) "Конец" else "Начало") }
                        }
                    }
                }
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun Crosshair(active: Boolean, modifier: Modifier) {
        val color = if (active) Color(0xFF4DB6AC) else Color.White
        Canvas(modifier.size(48.dp)) {
            val c = Offset(size.width / 2, size.height / 2)
            drawCircle(color, radius = size.minDimension / 2.5f, center = c, style = Stroke(width = 3.dp.toPx()))
            drawLine(color, Offset(c.x - 10.dp.toPx(), c.y), Offset(c.x + 10.dp.toPx(), c.y), strokeWidth = 2.dp.toPx())
            drawLine(color, Offset(c.x, c.y - 10.dp.toPx()), Offset(c.x, c.y + 10.dp.toPx()), strokeWidth = 2.dp.toPx())
        }
    }

    private fun hint(s: ArFrameState): String = when {
        s.sampling -> "Держите телефон неподвижно…"
        !s.tracking -> "Медленно поводите телефоном, чтобы камера «увидела» помещение"
        !s.onSurface -> "Наведите прицел на пол или стену. Если поверхность не находится — поводите телефоном, гладкие однотонные стены камера видит плохо"
        !s.startPlaced -> "Наведите прицел на начало отрезка и нажмите «Начало»"
        else -> "Наведите прицел на конец отрезка и нажмите «Конец»"
    }

    private fun meters(v: Double) = "%.2f м".format(Locale.ROOT, v)

    /** Запуск экрана замера; результат — метры или null, если пользователь вышел. */
    class Contract : ActivityResultContract<Unit, Double?>() {
        override fun createIntent(context: Context, input: Unit) = Intent(context, ArMeasureActivity::class.java)

        override fun parseResult(resultCode: Int, intent: Intent?): Double? =
            if (resultCode == Activity.RESULT_OK) intent?.getDoubleExtra(EXTRA_METERS, -1.0)?.takeIf { it > 0 } else null
    }

    companion object {
        private const val EXTRA_METERS = "meters"
        private const val MAX_GOOD_RANGE_M = 3.0
    }
}
