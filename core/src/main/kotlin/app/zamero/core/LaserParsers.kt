package app.zamero.core

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Разбор данных, приходящих от Bluetooth-рулеток. Возвращают расстояние в метрах
 * или null, если пакет не содержит измерения.
 */
object LaserParsers {

    /** Float32 little-endian в метрах (так передают, например, Leica DISTO). */
    fun float32Meters(bytes: ByteArray): Double? {
        if (bytes.size < 4) return null
        val v = ByteBuffer.wrap(bytes, 0, 4).order(ByteOrder.LITTLE_ENDIAN).float.toDouble()
        return v.takeIf { it.isFinite() && it > 0.0 && it < MAX_RANGE_M }
    }

    private val NUMBER = Regex("""(\d+(?:[.,]\d+)?)\s*(mm|cm|m|ft|мм|см|м)?""", RegexOption.IGNORE_CASE)

    /**
     * Текстовый формат (рулетки с UART-профилем): «1.234», «1.234m», «1234 mm».
     * Без единиц считаем, что значение в метрах.
     */
    fun asciiMeters(bytes: ByteArray): Double? {
        val text = bytes.toString(Charsets.US_ASCII)
        val match = NUMBER.find(text) ?: return null
        val number = match.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return null
        val meters = when (match.groupValues[2].lowercase()) {
            "mm", "мм" -> number / 1000
            "cm", "см" -> number / 100
            "ft" -> number * 0.3048
            else -> number
        }
        return meters.takeIf { it > 0.0 && it < MAX_RANGE_M }
    }

    const val MAX_RANGE_M = 300.0
}
