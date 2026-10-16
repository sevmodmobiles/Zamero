package app.zamero.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import app.zamero.core.ExchangeChain
import app.zamero.core.Listing
import app.zamero.core.MatchingEngine
import app.zamero.core.Property
import app.zamero.core.PropertyType
import app.zamero.core.SampleData
import app.zamero.core.Wish

/** Поля формы храним строками, чтобы пользователь мог свободно печатать. */
data class FormState(
    val city: String = "Москва",
    val type: PropertyType = PropertyType.APARTMENT,
    val district: String = "",
    val rooms: Int = 1,
    val area: String = "",
    val valueMillions: String = "",
    val wantDistricts: String = "",
    val wantRoomsMin: Int = 1,
    /** Готов доплатить (млн ₽). Отрицательное — хочу получить доплату. */
    val extraPaymentMillions: Float = 0f,
) {
    val isValid: Boolean
        get() = district.isNotBlank() &&
            area.toDoubleOrNull()?.let { it > 0 } == true &&
            valueMillions.toDoubleOrNull()?.let { it > 0 } == true
}

sealed interface Screen {
    data object Form : Screen
    data class Matches(val chains: List<ExchangeChain>) : Screen
}

class ExchangeViewModel : ViewModel() {
    var form by mutableStateOf(FormState())
        private set
    var screen by mutableStateOf<Screen>(Screen.Form)
        private set

    fun update(transform: FormState.() -> FormState) {
        form = form.transform()
    }

    fun search() {
        val mine = form.toListing() ?: return
        // Пока нет сервера, ищем среди демонстрационных объявлений.
        val engine = MatchingEngine(SampleData.listings + mine)
        screen = Screen.Matches(engine.findFor(mine.id))
    }

    fun backToForm() {
        screen = Screen.Form
    }

    private fun FormState.toListing(): Listing? {
        if (!isValid) return null
        return Listing(
            id = MY_ID,
            ownerName = "Вы",
            have = Property(
                type = type,
                city = city.trim(),
                district = district.trim(),
                rooms = rooms,
                areaSqm = area.toDouble(),
                estimatedValue = (valueMillions.toDouble() * MILLION).toLong(),
            ),
            want = Wish(
                city = city.trim(),
                districts = wantDistricts.split(',').map { it.trim() }.filter { it.isNotEmpty() },
                roomsMin = wantRoomsMin,
                maxExtraPayment = (extraPaymentMillions * MILLION).toLong(),
            ),
        )
    }

    companion object {
        const val MY_ID = "me"
        const val MILLION = 1_000_000
    }
}
