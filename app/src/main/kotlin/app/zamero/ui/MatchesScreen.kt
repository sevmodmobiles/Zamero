package app.zamero.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.zamero.core.ExchangeChain
import app.zamero.core.ExchangeStep
import app.zamero.core.Property
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MatchesScreen(myId: String, chains: List<ExchangeChain>, onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Варианты: ${chains.size}") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Назад") } },
            )
        },
    ) { padding ->
        if (chains.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding).padding(24.dp), contentAlignment = Alignment.Center) {
                Text(
                    "Пока вариантов нет. Мы сообщим, как только появится подходящий обмен. " +
                        "Расширьте список районов или допустимую доплату — вариантов станет больше.",
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(chains) { chain -> ChainCard(myId, chain) }
            }
        }
    }
}

@Composable
private fun ChainCard(myId: String, chain: ExchangeChain) {
    val mine = chain.stepFor(myId) ?: return
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                if (chain.isDirect) "Прямой обмен" else "Цепочка из ${chain.size} участников",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Text("Вы получаете: ${mine.receives.have.describe()}", fontWeight = FontWeight.SemiBold)
            Text(paymentText(mine.payment))
            Text("Совпадение с пожеланиями: ${(mine.fitScore * 100).toInt()}%")
            if (!chain.isDirect) {
                HorizontalDivider()
                chain.steps.forEach { step -> Text(step.describe(myId), style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}

private fun ExchangeStep.describe(myId: String): String {
    val who = if (gives.id == myId) "Вы" else gives.ownerName
    val from = if (receives.id == myId) "ваше помещение" else "помещение: ${receives.ownerName}"
    return "$who → $from, ${receives.have.district}"
}

private fun Property.describe(): String =
    "${type.title.lowercase()}, $rooms комн., ${areaSqm.toInt()} м², $district"

private fun paymentText(payment: Long): String = when {
    payment > 0 -> "Ваша доплата: ${rub(payment)}"
    payment < 0 -> "Вам доплатят: ${rub(abs(payment))}"
    else -> "Без доплат"
}

private fun rub(amount: Long): String =
    NumberFormat.getIntegerInstance(Locale.forLanguageTag("ru")).format(amount) + " ₽"
