package app.zamero.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Teal = Color(0xFF00796B)
private val TealLight = Color(0xFF4DB6AC)

@Composable
fun ZameroTheme(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme()) {
        darkColorScheme(primary = TealLight, secondary = TealLight)
    } else {
        lightColorScheme(primary = Teal, secondary = Teal)
    }
    MaterialTheme(colorScheme = colors, content = content)
}
