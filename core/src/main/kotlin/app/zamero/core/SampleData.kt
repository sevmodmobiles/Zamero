package app.zamero.core

/** Демонстрационные объявления, пока нет сервера. */
object SampleData {
    private const val CITY = "Москва"

    val listings: List<Listing> = listOf(
        Listing(
            id = "anna", ownerName = "Анна",
            have = Property(PropertyType.APARTMENT, CITY, "Марьино", 1, 38.0, 9_500_000),
            want = Wish(CITY, setOf(PropertyType.APARTMENT), listOf("Хамовники", "Арбат"), roomsMin = 1, maxExtraPayment = 4_000_000),
        ),
        Listing(
            id = "boris", ownerName = "Борис",
            have = Property(PropertyType.APARTMENT, CITY, "Хамовники", 1, 35.0, 13_000_000),
            want = Wish(CITY, setOf(PropertyType.APARTMENT), listOf("Бутово", "Ясенево"), roomsMin = 2, maxExtraPayment = 0),
        ),
        Listing(
            id = "vera", ownerName = "Вера",
            have = Property(PropertyType.APARTMENT, CITY, "Бутово", 2, 54.0, 12_000_000),
            want = Wish(CITY, setOf(PropertyType.APARTMENT), listOf("Марьино", "Люблино"), roomsMin = 1, maxExtraPayment = -1_000_000),
        ),
        Listing(
            id = "gleb", ownerName = "Глеб",
            have = Property(PropertyType.APARTMENT, CITY, "Арбат", 2, 60.0, 20_000_000),
            want = Wish(CITY, setOf(PropertyType.APARTMENT), listOf("Марьино"), roomsMin = 1, maxExtraPayment = -5_000_000),
        ),
        Listing(
            id = "dina", ownerName = "Дина",
            have = Property(PropertyType.ROOM, CITY, "Люблино", 1, 18.0, 4_000_000),
            want = Wish(CITY, districts = listOf("Марьино"), roomsMin = 1, maxExtraPayment = 6_000_000),
        ),
    )
}
