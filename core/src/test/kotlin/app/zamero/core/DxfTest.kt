package app.zamero.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DxfTest {
    private fun m(v: Double) = Measure(v, Source.LASER)

    private val project = Project(
        id = "p", address = "ул. Ленина, 1", customer = "Иванов", contractor = "ООО Замер",
        rooms = listOf(
            Room(
                "r1", "Кухня", Outlines.RECTANGLE,
                walls = listOf(m(4.0), m(3.0), m(4.0), m(3.0)),
                height = m(2.7),
                openings = listOf(Opening(OpeningType.WINDOW, m(1.5), m(1.4))),
                engineering = listOf(
                    EngElement("d", EngType.DUCT_RECT, widthMm = 200, heightMm = 100)
                        .withRoute(listOf(Point(0.0, 150.0), Point(400.0, 150.0))),
                ),
            ),
            Room("r2", "Пустая"),
        ),
    )

    @Test
    fun `dxf structure is valid R12`() {
        val dxf = Dxf.build(project)
        val lines = dxf.split("\r\n")
        assertEquals("AC1009", lines[lines.indexOf("\$ACADVER") + 2])
        assertEquals("EOF", lines[lines.size - 2])
        assertEquals(lines.count { it == "SECTION" }, lines.count { it == "ENDSEC" })
        assertEquals(lines.count { it == "POLYLINE" }, lines.count { it == "SEQEND" })
        // Стены + воздуховод (контур в реальную ширину и ось); комната без контура пропущена.
        assertEquals(3, lines.count { it == "POLYLINE" })
        assertTrue("DUCT_RECT" in lines)
    }

    @Test
    fun `walls are in millimeters with y up`() {
        val dxf = Dxf.build(project)
        assertTrue("4000.000" in dxf)
        assertTrue("3000.000" in dxf)
        assertTrue("4000" in dxf.split("\r\n")) // размер стены
    }

    @Test
    fun `cyrillic survives windows-1251 and special symbols are replaced`() {
        val bytes = Dxf.export(project)
        val text = String(bytes, Dxf.CHARSET)
        assertTrue("Кухня" in text)
        assertTrue("Воздуховод прямоугольный 200x100" in text)
        assertTrue('?' !in text, "все символы должны кодироваться в windows-1251")
        assertEquals("%%c110", Dxf.cadText("Ø110"))
    }
}
