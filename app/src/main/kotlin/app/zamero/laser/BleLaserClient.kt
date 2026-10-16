package app.zamero.laser

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import app.zamero.core.toHex
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

data class FoundDevice(val address: String, val name: String, val rssi: Int)

sealed interface LaserState {
    data object Idle : LaserState
    data class Scanning(val devices: List<FoundDevice>) : LaserState
    data class Connecting(val name: String) : LaserState
    /** [profile] == null — протокол неизвестен, пишем сырые данные в журнал. */
    data class Connected(val name: String, val profile: String?) : LaserState
    data class Error(val message: String) : LaserState
}

/**
 * Подключение к Bluetooth-рулетке. Разрешения проверяет UI до вызова методов.
 * Каждое измерение, сделанное кнопкой на рулетке, приходит в [readings] в метрах.
 */
@SuppressLint("MissingPermission")
class BleLaserClient(context: Context) {
    private val appContext = context.applicationContext
    private val adapter = appContext.getSystemService(BluetoothManager::class.java)?.adapter
    private val main = Handler(Looper.getMainLooper())

    private val _state = MutableStateFlow<LaserState>(LaserState.Idle)
    val state: StateFlow<LaserState> = _state

    private val _readings = MutableSharedFlow<Double>(extraBufferCapacity = 16)
    val readings: SharedFlow<Double> = _readings

    private val _log = MutableStateFlow<List<String>>(emptyList())
    /** Журнал для неизвестных дальномеров: сервисы и сырые пакеты в HEX. */
    val log: StateFlow<List<String>> = _log

    private var gatt: BluetoothGatt? = null
    private var activeProfile: LaserProfile? = null
    /** Характеристики, на которые ещё нужно подписаться (BLE разрешает одну операцию за раз). */
    private val pendingSubscriptions = ArrayDeque<BluetoothGattCharacteristic>()
    private val found = LinkedHashMap<String, FoundDevice>()

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val name = result.scanRecord?.deviceName ?: result.device.name ?: return
            found[result.device.address] = FoundDevice(result.device.address, name, result.rssi)
            if (_state.value is LaserState.Scanning) {
                _state.value = LaserState.Scanning(found.values.sortedByDescending { it.rssi })
            }
        }

        override fun onScanFailed(errorCode: Int) {
            _state.value = LaserState.Error("Не удалось начать поиск (код $errorCode)")
        }
    }

    fun startScan() {
        val a = adapter
        if (a == null) {
            _state.value = LaserState.Error("На телефоне нет Bluetooth")
            return
        }
        if (!a.isEnabled) {
            _state.value = LaserState.Error("Включите Bluetooth")
            return
        }
        disconnect()
        found.clear()
        _log.value = emptyList()
        _state.value = LaserState.Scanning(emptyList())
        a.bluetoothLeScanner?.startScan(scanCallback)
        main.postDelayed(::stopScan, SCAN_TIMEOUT_MS)
    }

    fun stopScan() {
        main.removeCallbacksAndMessages(null)
        runCatching { adapter?.bluetoothLeScanner?.stopScan(scanCallback) }
    }

    fun connect(device: FoundDevice) {
        stopScan()
        val remote = adapter?.getRemoteDevice(device.address) ?: return
        _state.value = LaserState.Connecting(device.name)
        gatt = remote.connectGatt(appContext, false, gattCallback(device.name), BluetoothDevice.TRANSPORT_LE)
    }

    fun disconnect() {
        stopScan()
        gatt?.let {
            it.disconnect()
            it.close()
        }
        gatt = null
        activeProfile = null
        pendingSubscriptions.clear()
        _state.value = LaserState.Idle
    }

    private fun gattCallback(name: String) = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> g.discoverServices()
                BluetoothProfile.STATE_DISCONNECTED -> {
                    g.close()
                    if (gatt == g) {
                        gatt = null
                        _state.value = LaserState.Error("Рулетка $name отключилась")
                    }
                }
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            for (profile in LaserProfiles.all) {
                val ch = g.getService(profile.service)?.getCharacteristic(profile.characteristic) ?: continue
                activeProfile = profile
                pendingSubscriptions += ch
                subscribeNext(g)
                _state.value = LaserState.Connected(name, profile.title)
                return
            }
            // Протокол неизвестен: подписываемся на всё, что умеет присылать данные, и ведём журнал.
            g.services.forEach { service ->
                log("Сервис ${service.uuid}")
                service.characteristics.forEach { ch ->
                    val canNotify = ch.properties and
                        (BluetoothGattCharacteristic.PROPERTY_NOTIFY or BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0
                    log("  ${ch.uuid}${if (canNotify) " [уведомления]" else ""}")
                    if (canNotify) pendingSubscriptions += ch
                }
            }
            _state.value = LaserState.Connected(name, null)
            subscribeNext(g)
        }

        override fun onDescriptorWrite(g: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            subscribeNext(g)
        }

        override fun onCharacteristicChanged(g: BluetoothGatt, ch: BluetoothGattCharacteristic, value: ByteArray) {
            handle(ch, value)
        }

        // Android 12 и ниже вызывают только этот вариант.
        @Deprecated("Deprecated in Android 13")
        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(g: BluetoothGatt, ch: BluetoothGattCharacteristic) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                handle(ch, ch.value ?: return)
            }
        }
    }

    private fun handle(ch: BluetoothGattCharacteristic, value: ByteArray) {
        val profile = activeProfile
        if (profile == null) {
            log("${ch.uuid.toString().take(8)}: ${value.toHex()}")
            return
        }
        if (ch.uuid != profile.characteristic) return
        profile.parse(value)?.let { _readings.tryEmit(it) }
    }

    private fun log(line: String) {
        _log.value = (_log.value + line).takeLast(LOG_LINES)
    }

    private fun subscribeNext(g: BluetoothGatt) {
        val ch = pendingSubscriptions.removeFirstOrNull() ?: return
        if (!enableNotifications(g, ch)) subscribeNext(g)
    }

    /** Включает уведомления; false — если писать дескриптор не нужно (ждать ответа не будем). */
    @Suppress("DEPRECATION")
    private fun enableNotifications(g: BluetoothGatt, ch: BluetoothGattCharacteristic): Boolean {
        g.setCharacteristicNotification(ch, true)
        val descriptor = ch.getDescriptor(LaserProfiles.CCCD) ?: return false
        val value = if (ch.properties and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0) {
            BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
        } else {
            BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            g.writeDescriptor(descriptor, value) == BluetoothGatt.GATT_SUCCESS
        } else {
            descriptor.value = value
            g.writeDescriptor(descriptor)
        }
    }

    companion object {
        private const val SCAN_TIMEOUT_MS = 15_000L
        private const val LOG_LINES = 60
    }
}
