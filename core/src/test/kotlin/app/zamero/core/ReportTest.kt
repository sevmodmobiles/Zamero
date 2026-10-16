package app.zamero.core

import kotlin.test.Test
import kotlin.test.assertTrue

class ReportTest {
    @Test
    fun `report contains totals and recheck warning`() {
        fun m(v: Double) = Measure(v, Source.LASER)
        val good = Room("1", "Кухня", Outlines.RECTANGLE, walls = listOf(m(3.0), m(2.0), m(3.0), m(2.0)), height = m(2.7))
        val bad = Room("2", "Спальня", Outlines.RECTANGLE, walls = listOf(m(4.0), m(3.0), m(4.3), m(3.0)))
        val text = Report.text(Project("p", "ул. Ленина, 1", customer = "Иванов", rooms = listOf(good, bad)))
        assertTrue("Площадь пола: 6.00 м²" in text)
        assertTrue("перемерьте" in text)
        assertTrue("Итого площадь:" in text)
        assertTrue("Заказчик: Иванов" in text)
        assertTrue("Стена 1: 3 000 мм" in text)
    }
}
