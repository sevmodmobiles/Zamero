package app.zamero.core

/**
 * Подбор обменов. Строит граф «A хочет помещение B» и ищет в нём циклы:
 * цикл длины 2 — прямой обмен, длиннее — цепочка, где каждый получает то, что хотел.
 *
 * Доплаты: каждый участник платит разницу между стоимостью получаемого и отдаваемого
 * помещения. Сумма доплат по циклу всегда равна нулю, поэтому деньги просто
 * перераспределяются внутри цепочки. Цикл допустим, только если доплата каждого
 * не превышает его [Wish.maxExtraPayment].
 */
class MatchingEngine(
    listings: List<Listing>,
    private val maxChainLength: Int = 4,
) {
    init {
        require(maxChainLength >= 2) { "maxChainLength must be >= 2" }
    }

    private val listings: List<Listing> = listings.distinctBy { it.id }
    private val index: Map<String, Int> = this.listings.withIndex().associate { (i, l) -> l.id to i }

    /** wants[a] — индексы объявлений, чьё помещение подходит владельцу a, с оценкой. */
    private val wants: List<List<Pair<Int, Double>>> = this.listings.map { a ->
        this.listings.mapIndexedNotNull { j, b ->
            if (a.id == b.id) return@mapIndexedNotNull null
            val s = Fit.score(b.have, a.want)
            if (s > 0.0) j to s else null
        }
    }

    /** Все допустимые обмены с участием объявления, лучшие первыми. */
    fun findFor(listingId: String): List<ExchangeChain> {
        val start = index[listingId] ?: return emptyList()
        val result = mutableListOf<ExchangeChain>()
        search(start, allowed = { true }, result)
        return result.sortedByDescending { it.score }
    }

    /** Все допустимые обмены в базе (каждый цикл ровно один раз), лучшие первыми. */
    fun findAll(): List<ExchangeChain> {
        val result = mutableListOf<ExchangeChain>()
        for (start in listings.indices) {
            // Цикл учитываем только от его минимального индекса — так нет дублей.
            search(start, allowed = { it > start }, result)
        }
        return result.sortedByDescending { it.score }
    }

    /**
     * Жадно выбирает непересекающиеся обмены, чтобы одновременно провести
     * как можно больше хороших сделок (одно объявление — в одном обмене).
     */
    fun bestPlan(): List<ExchangeChain> {
        val taken = HashSet<String>()
        return findAll().filter { chain ->
            val ids = chain.participantIds
            if (ids.any { it in taken }) false else { taken += ids; true }
        }
    }

    private fun search(start: Int, allowed: (Int) -> Boolean, out: MutableList<ExchangeChain>) {
        val path = ArrayList<Int>().apply { add(start) }
        val scores = ArrayList<Double>()
        val onPath = BooleanArray(listings.size).also { it[start] = true }

        fun dfs(current: Int) {
            for ((next, s) in wants[current]) {
                if (next == start && path.size >= 2) {
                    scores.add(s)
                    buildChain(path, scores)?.let(out::add)
                    scores.removeAt(scores.lastIndex)
                } else if (!onPath[next] && allowed(next) && path.size < maxChainLength) {
                    onPath[next] = true
                    path.add(next)
                    scores.add(s)
                    dfs(next)
                    scores.removeAt(scores.lastIndex)
                    path.removeAt(path.lastIndex)
                    onPath[next] = false
                }
            }
        }
        dfs(start)
    }

    /** path[i] получает помещение path[i+1] (последний — помещение первого). */
    private fun buildChain(path: List<Int>, scores: List<Double>): ExchangeChain? {
        val steps = path.indices.map { i ->
            val gives = listings[path[i]]
            val receives = listings[path[(i + 1) % path.size]]
            val payment = receives.have.estimatedValue - gives.have.estimatedValue
            if (payment > gives.want.maxExtraPayment) return null
            ExchangeStep(gives, receives, payment, scores[i])
        }
        return ExchangeChain(steps)
    }
}
