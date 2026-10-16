package app.zamero.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.zamero.core.PropertyType
import kotlin.math.abs
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListingFormScreen(
    form: FormState,
    onChange: (FormState.() -> FormState) -> Unit,
    onSearch: () -> Unit,
) {
    Scaffold(topBar = { TopAppBar(title = { Text("Замеро — обмен помещений") }) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionTitle("1. Что у меня есть")
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PropertyType.entries.forEach { t ->
                    FilterChip(
                        selected = form.type == t,
                        onClick = { onChange { copy(type = t) } },
                        label = { Text(t.title) },
                    )
                }
            }
            OutlinedTextField(
                value = form.city, onValueChange = { v -> onChange { copy(city = v) } },
                label = { Text("Город") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = form.district, onValueChange = { v -> onChange { copy(district = v) } },
                label = { Text("Район") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            Stepper("Комнат", form.rooms) { v -> onChange { copy(rooms = v) } }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField("Площадь, м²", form.area, Modifier.weight(1f)) { v -> onChange { copy(area = v) } }
                NumberField("Стоимость, млн ₽", form.valueMillions, Modifier.weight(1f)) { v ->
                    onChange { copy(valueMillions = v) }
                }
            }

            SectionTitle("2. Что я ищу")
            OutlinedTextField(
                value = form.wantDistricts, onValueChange = { v -> onChange { copy(wantDistricts = v) } },
                label = { Text("Районы через запятую, по важности") },
                supportingText = { Text("Пусто — любой район") },
                modifier = Modifier.fillMaxWidth(),
            )
            Stepper("Комнат не меньше", form.wantRoomsMin) { v -> onChange { copy(wantRoomsMin = v) } }

            val pay = form.extraPaymentMillions
            Text(
                when {
                    pay > 0 -> "Готов доплатить до ${pay.formatMln()} млн ₽"
                    pay < 0 -> "Хочу получить от ${abs(pay).formatMln()} млн ₽"
                    else -> "Обмен без доплат"
                },
            )
            Slider(
                value = pay,
                onValueChange = { v -> onChange { copy(extraPaymentMillions = (v * 2).roundToInt() / 2f) } },
                valueRange = -10f..10f,
            )

            Button(onClick = onSearch, enabled = form.isValid, modifier = Modifier.fillMaxWidth()) {
                Text("Найти варианты обмена")
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium)
}

@Composable
private fun NumberField(label: String, value: String, modifier: Modifier, onValue: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { v -> onValue(v.replace(',', '.').filter { it.isDigit() || it == '.' }) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier,
    )
}

@Composable
private fun Stepper(label: String, value: Int, onValue: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, modifier = Modifier.weight(1f))
        Button(onClick = { onValue((value - 1).coerceAtLeast(1)) }) { Text("−") }
        Text("$value", style = MaterialTheme.typography.titleMedium)
        Button(onClick = { onValue(value + 1) }) { Text("+") }
    }
}

private fun Float.formatMln(): String = if (this % 1f == 0f) "${toInt()}" else "%.1f".format(this)
