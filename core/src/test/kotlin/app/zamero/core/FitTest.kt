package app.zamero.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FitTest {
    private val flat = Property(PropertyType.APARTMENT, "Москва", "Арбат", 2, 50.0, 20_000_000)

    @Test
    fun `hard filters`() {
        assertTrue(Fit.fits(flat, Wish("москва")))
        assertTrue(!Fit.fits(flat, Wish("Москва", types = setOf(PropertyType.HOUSE))))
        assertTrue(!Fit.fits(flat, Wish("Москва", roomsMin = 3)))
        assertTrue(!Fit.fits(flat, Wish("Москва", areaMinSqm = 60.0)))
        assertTrue(!Fit.fits(flat, Wish("Москва", districts = listOf("Марьино"))))
    }

    @Test
    fun `preferred district scores higher`() {
        val first = Fit.score(flat, Wish("Москва", districts = listOf("Арбат", "Марьино")))
        val second = Fit.score(flat, Wish("Москва", districts = listOf("Марьино", "Арбат")))
        assertEquals(1.0, first)
        assertTrue(second < first)
    }
}
