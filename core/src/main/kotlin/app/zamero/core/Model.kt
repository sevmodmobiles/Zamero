package app.zamero.core

/** Тип помещения. */
enum class PropertyType(val title: String) {
    APARTMENT("Квартира"),
    ROOM("Комната"),
    HOUSE("Дом"),
    COMMERCIAL("Коммерческое"),
}

/** Помещение, которое человек отдаёт в обмен («Что у меня есть»). */
data class Property(
    val type: PropertyType,
    val city: String,
    val district: String,
    val rooms: Int,
    val areaSqm: Double,
    /** Оценочная стоимость в рублях — из неё считаются доплаты. */
    val estimatedValue: Long,
)

/**
 * Что человек хочет получить («Что я ищу»). Пустое множество означает «любой».
 *
 * [maxExtraPayment] — сколько человек готов доплатить в рублях. Отрицательное
 * значение означает, что он сам хочет получить не меньше |maxExtraPayment|
 * (например, при переезде в меньшую квартиру).
 */
data class Wish(
    val city: String,
    val types: Set<PropertyType> = emptySet(),
    /** Районы в порядке предпочтения; пустой список — любой район. */
    val districts: List<String> = emptyList(),
    val roomsMin: Int = 0,
    val roomsMax: Int = Int.MAX_VALUE,
    val areaMinSqm: Double = 0.0,
    val maxExtraPayment: Long = 0,
)

/** Объявление: что есть + что нужно. */
data class Listing(
    val id: String,
    val ownerName: String,
    val have: Property,
    val want: Wish,
)
