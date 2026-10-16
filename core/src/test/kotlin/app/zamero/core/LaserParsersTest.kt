package app.zamero.core

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LaserParsersTest {
    @Test
    fun `float32 little endian`() {
        val bytes = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putFloat(3.457f).array()
        assertEquals(3.457, LaserParsers.float32Meters(bytes)!!, 1e-6)
        assertNull(LaserParsers.float32Meters(byteArrayOf(1, 2)))
    }

    @Test
    fun `ascii formats`() {
        assertEquals(1.234, LaserParsers.asciiMeters("1.234".toByteArray())!!, 1e-9)
        assertEquals(1.234, LaserParsers.asciiMeters("D: 1,234m\r\n".toByteArray())!!, 1e-9)
        assertEquals(1.234, LaserParsers.asciiMeters("1234 mm".toByteArray())!!, 1e-9)
        assertEquals(0.5, LaserParsers.asciiMeters("50cm".toByteArray())!!, 1e-9)
        assertNull(LaserParsers.asciiMeters("ERR".toByteArray()))
    }
}
