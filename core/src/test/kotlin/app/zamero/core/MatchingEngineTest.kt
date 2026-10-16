package app.zamero.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MatchingEngineTest {

    private fun listing(
        id: String,
        district: String,
        value: Long,
        wantDistricts: List<String>,
        maxExtraPayment: Long = 0,
        rooms: Int = 1,
        city: String = "Москва",
    ) = Listing(
        id = id,
        ownerName = id,
        have = Property(PropertyType.APARTMENT, city, district, rooms, 40.0, value),
        want = Wish(city, districts = wantDistricts, maxExtraPayment = maxExtraPayment),
    )

    @Test
    fun `direct swap is found once for both sides`() {
        val a = listing("a", "Север", 10, listOf("Юг"))
        val b = listing("b", "Юг", 10, listOf("Север"))
        val engine = MatchingEngine(listOf(a, b))

        val all = engine.findAll()
        assertEquals(1, all.size)
        assertTrue(all.single().isDirect)
        assertEquals(1, engine.findFor("a").size)
        assertEquals(1, engine.findFor("b").size)
    }

    @Test
    fun `three-way chain is found when no direct swap exists`() {
        val a = listing("a", "A", 10, listOf("B"))
        val b = listing("b", "B", 10, listOf("C"))
        val c = listing("c", "C", 10, listOf("A"))
        val engine = MatchingEngine(listOf(a, b, c))

        val chains = engine.findAll()
        assertEquals(1, chains.size)
        assertEquals(3, chains.single().size)
        assertEquals("b", engine.findFor("a").single().stepFor("a")!!.receives.id)
    }

    @Test
    fun `chains longer than the limit are ignored`() {
        val ids = listOf("A", "B", "C", "D", "E")
        val listings = ids.mapIndexed { i, d -> listing(d, d, 10, listOf(ids[(i + 1) % ids.size])) }

        assertTrue(MatchingEngine(listings, maxChainLength = 4).findAll().isEmpty())
        assertEquals(1, MatchingEngine(listings, maxChainLength = 5).findAll().size)
    }

    @Test
    fun `payments balance to zero and respect limits`() {
        val cheap = listing("cheap", "A", 10_000_000, listOf("B"), maxExtraPayment = 3_000_000)
        val pricey = listing("pricey", "B", 12_000_000, listOf("A"), maxExtraPayment = -1_000_000)
        val chain = MatchingEngine(listOf(cheap, pricey)).findAll().single()

        assertEquals(2_000_000, chain.stepFor("cheap")!!.payment)
        assertEquals(-2_000_000, chain.stepFor("pricey")!!.payment)
        assertEquals(0, chain.steps.sumOf { it.payment })
    }

    @Test
    fun `swap is rejected when someone would pay more than allowed`() {
        val cheap = listing("cheap", "A", 10_000_000, listOf("B"), maxExtraPayment = 1_000_000)
        val pricey = listing("pricey", "B", 12_000_000, listOf("A"), maxExtraPayment = -1_000_000)
        assertTrue(MatchingEngine(listOf(cheap, pricey)).findAll().isEmpty())
    }

    @Test
    fun `different cities never match`() {
        val a = listing("a", "A", 10, listOf("B"), city = "Москва")
        val b = listing("b", "B", 10, listOf("A"), city = "Казань")
        assertTrue(MatchingEngine(listOf(a, b)).findAll().isEmpty())
    }

    @Test
    fun `best plan uses each listing at most once and prefers direct swaps`() {
        // a <-> b напрямую, и a -> b -> c -> a цепочкой: должен выиграть прямой обмен.
        val a = listing("a", "A", 10, listOf("B"))
        val b = listing("b", "B", 10, listOf("A", "C"))
        val c = listing("c", "C", 10, listOf("A"))
        val engine = MatchingEngine(listOf(a, b, c))

        assertEquals(2, engine.findAll().size)
        val plan = engine.bestPlan()
        assertEquals(1, plan.size)
        assertTrue(plan.single().isDirect)
    }

    @Test
    fun `sample data contains a three-way chain for Anna`() {
        val chains = MatchingEngine(SampleData.listings).findFor("anna")
        assertEquals(1, chains.size)
        assertEquals(listOf("anna", "boris", "vera"), chains.single().participantIds.toList())
    }
}
