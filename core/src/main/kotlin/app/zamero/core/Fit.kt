package app.zamero.core

/** Правила, по которым помещение подходит под пожелание. */
object Fit {

    /** Жёсткая проверка: подходит ли помещение вообще. */
    fun fits(property: Property, wish: Wish): Boolean =
        property.city.equals(wish.city, ignoreCase = true) &&
            (wish.types.isEmpty() || property.type in wish.types) &&
            (wish.districts.isEmpty() || wish.districts.any { it.equals(property.district, ignoreCase = true) }) &&
            property.rooms in wish.roomsMin..wish.roomsMax &&
            property.areaSqm >= wish.areaMinSqm

    /**
     * Насколько хорошо помещение подходит, от 0 до 1 (0 — не подходит).
     * Учитывает порядок предпочтения районов и запас по площади.
     */
    fun score(property: Property, wish: Wish): Double {
        if (!fits(property, wish)) return 0.0
        val districtScore = if (wish.districts.isEmpty()) {
            1.0
        } else {
            val rank = wish.districts.indexOfFirst { it.equals(property.district, ignoreCase = true) }
            1.0 - 0.5 * rank / wish.districts.size
        }
        val areaScore = if (wish.areaMinSqm <= 0.0) {
            1.0
        } else {
            // Каждые +20% площади сверх минимума немного повышают оценку, максимум +1.
            (0.8 + (property.areaSqm / wish.areaMinSqm - 1.0)).coerceIn(0.8, 1.0)
        }
        return districtScore * areaScore
    }
}
