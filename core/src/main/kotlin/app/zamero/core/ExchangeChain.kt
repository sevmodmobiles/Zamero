package app.zamero.core

import kotlin.math.pow

/**
 * Один участник обмена: отдаёт [gives] и получает [receives].
 * [payment] > 0 — участник доплачивает, < 0 — получает доплату.
 */
data class ExchangeStep(
    val gives: Listing,
    val receives: Listing,
    val payment: Long,
    val fitScore: Double,
)

/**
 * Замкнутый обмен: каждый участник получает помещение следующего.
 * Длина 2 — прямой обмен, 3 и больше — цепочка.
 */
data class ExchangeChain(val steps: List<ExchangeStep>) {
    val size: Int get() = steps.size
    val isDirect: Boolean get() = size == 2
    val participantIds: Set<String> get() = steps.mapTo(LinkedHashSet()) { it.gives.id }

    /**
     * Итоговая оценка: средняя пригодность, уменьшенная за длину цепочки
     * (чем больше участников, тем выше риск, что кто-то сорвёт сделку).
     */
    val score: Double
        get() = steps.map { it.fitScore }.average() * LENGTH_PENALTY.pow(size - 2)

    fun stepFor(listingId: String): ExchangeStep? = steps.firstOrNull { it.gives.id == listingId }

    companion object {
        const val LENGTH_PENALTY = 0.85
    }
}
