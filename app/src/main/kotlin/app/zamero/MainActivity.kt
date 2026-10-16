package app.zamero

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import app.zamero.ui.MeasureViewModel
import app.zamero.ui.ZameroApp
import app.zamero.ui.theme.ZameroTheme

class MainActivity : ComponentActivity() {
    private val vm: MeasureViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ZameroTheme {
                ZameroApp(vm)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Приложение может часами висеть в памяти — проверяем при каждом возвращении.
        vm.checkUpdate()
    }
}
