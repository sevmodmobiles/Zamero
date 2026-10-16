package app.zamero.laser

import app.zamero.core.LaserParsers
import java.util.UUID

/** BLE-сервис рулетки: где искать измерение и как его разобрать. */
data class LaserProfile(
    val title: String,
    val service: UUID,
    val characteristic: UUID,
    val parse: (ByteArray) -> Double?,
)

object LaserProfiles {
    /** Стандартный дескриптор для включения уведомлений. */
    val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    val all = listOf(
        // Leica DISTO (серии D1/D2/D110/D510 и др.): расстояние — float32 в метрах.
        LaserProfile(
            title = "Leica DISTO",
            service = UUID.fromString("3ab10100-f831-4395-b29d-570977d5bf94"),
            characteristic = UUID.fromString("3ab10101-f831-4395-b29d-570977d5bf94"),
            parse = LaserParsers::float32Meters,
        ),
        // Рулетки с «последовательным портом» Nordic UART: значение приходит текстом.
        LaserProfile(
            title = "UART",
            service = UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e"),
            characteristic = UUID.fromString("6e400003-b5a3-f393-e0a9-e50e24dcca9e"),
            parse = LaserParsers::asciiMeters,
        ),
        // Модули HM-10/JDY (часто стоят в недорогих рулетках): тоже текст.
        LaserProfile(
            title = "HM-10",
            service = UUID.fromString("0000ffe0-0000-1000-8000-00805f9b34fb"),
            characteristic = UUID.fromString("0000ffe1-0000-1000-8000-00805f9b34fb"),
            parse = LaserParsers::asciiMeters,
        ),
    )
}
