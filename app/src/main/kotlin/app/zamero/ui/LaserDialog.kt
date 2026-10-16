package app.zamero.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.zamero.laser.BleLaserClient
import app.zamero.laser.LaserState

private val BLE_PERMISSIONS = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
    arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
} else {
    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
}

@Composable
fun LaserDialog(client: BleLaserClient, state: LaserState, onDismiss: () -> Unit) {
    val log by client.log.collectAsState()
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.values.all { it }) client.startScan()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Bluetooth-дальномер") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when (state) {
                    LaserState.Idle -> Text(
                        "Включите дальномер и Bluetooth на телефоне, затем нажмите «Найти». " +
                            "Поддерживаются Leica DISTO и дальномеры с UART/HM-10 модулем. " +
                            "С незнакомым дальномером приложение покажет журнал данных — " +
                            "пришлите его снимок, и мы добавим поддержку.",
                    )
                    is LaserState.Scanning -> {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        if (state.devices.isEmpty()) Text("Ищем устройства…")
                        LazyColumn(Modifier.heightIn(max = 280.dp)) {
                            items(state.devices, key = { it.address }) { d ->
                                Column(
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable { client.connect(d) }
                                        .padding(vertical = 8.dp),
                                ) {
                                    Text(d.name, style = MaterialTheme.typography.bodyLarge)
                                    Text("${d.address} · сигнал ${d.rssi} дБм", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                    is LaserState.Connecting -> {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        Text("Подключаемся к ${state.name}…")
                    }
                    is LaserState.Connected -> if (state.profile != null) {
                        Text(
                            "Подключено: ${state.name} (${state.profile}).\n" +
                                "Откройте стену и нажмите кнопку замера на дальномере — значение подставится само.",
                        )
                    } else {
                        Text(
                            "Подключено: ${state.name}, но протокол пока неизвестен. " +
                                "Сделайте 2–3 замера (например, 1000 и 2000 мм) и пришлите снимок журнала:",
                        )
                        LazyColumn(Modifier.heightIn(max = 240.dp)) {
                            items(log) { line ->
                                Text(line, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                            }
                        }
                    }
                    is LaserState.Error -> Text(state.message, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            when (state) {
                is LaserState.Connected, is LaserState.Connecting ->
                    TextButton(onClick = client::disconnect) { Text("Отключить") }
                else -> TextButton(onClick = { permissions.launch(BLE_PERMISSIONS) }) { Text("Найти") }
            }
        },
        dismissButton = {
            TextButton(onClick = {
                if (state is LaserState.Scanning) client.stopScan()
                onDismiss()
            }) { Text("Готово") }
        },
    )
}
