package com.startupassistant.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.startupassistant.AppGraph
import java.time.LocalDate
import kotlin.math.roundToLong

@Composable
fun BooksScreen(modifier: Modifier = Modifier) {
    val books = AppGraph.books
    val transactions by books.transactions.collectAsStateWithLifecycle()
    val summary = remember(transactions) { books.summary() }

    var description by rememberSaveable { mutableStateOf("") }
    var amount by rememberSaveable { mutableStateOf("") }
    var moneyIn by rememberSaveable { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            ScreenHeader("Books", "Record money in and out. See your burn rate and your runway.")
            SampleDataNote("There is no free bookkeeping API, so data is kept in memory.")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Stat("Cash", usd(summary.cashBalanceCents), Modifier.weight(1f))
                Stat("Monthly burn", usd(summary.averageMonthlyBurnCents), Modifier.weight(1f))
                Stat("Runway", summary.runwayMonths?.let { "$it mo" } ?: "Profitable", Modifier.weight(1f))
            }
        }

        item {
            Text("Add a transaction", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !moneyIn, onClick = { moneyIn = false }, label = { Text("Money out") })
                FilterChip(selected = moneyIn, onClick = { moneyIn = true }, label = { Text("Money in") })
            }
            OutlinedTextField(description, { description = it }, Modifier.fillMaxWidth(), label = { Text("Description, for example: AWS") }, singleLine = true)
            OutlinedTextField(
                amount, { amount = it }, Modifier.fillMaxWidth(),
                label = { Text("Amount (USD)") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
            Button(
                modifier = Modifier.padding(top = 8.dp),
                onClick = {
                    val cents = amount.toDoubleOrNull()?.times(100)?.roundToLong()
                    if (cents == null || cents <= 0 || description.isBlank()) {
                        error = "Enter a description and an amount more than 0."
                    } else {
                        books.add(LocalDate.now(), description, if (moneyIn) cents else -cents)
                        description = ""; amount = ""; error = null
                    }
                },
            ) { Text("Add") }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Text("The category is picked from the description.", style = MaterialTheme.typography.bodySmall)
        }

        item {
            Text("Expenses by category", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
            val max = (summary.expensesByCategory.values.maxOrNull() ?: 1L).coerceAtLeast(1L)
            InfoCard {
                summary.expensesByCategory.entries.sortedByDescending { it.value }.forEach { (cat, cents) ->
                    Row(Modifier.fillMaxWidth()) {
                        Text(cat.name.lowercase(), Modifier.weight(1f))
                        Text(usd(cents))
                    }
                    LinearProgressIndicator(
                        progress = { cents.toFloat() / max },
                        modifier = Modifier.fillMaxWidth().height(6.dp).padding(bottom = 4.dp),
                    )
                }
            }
        }

        item {
            Text("By month", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
            InfoCard {
                summary.months.forEach { m ->
                    Row(Modifier.fillMaxWidth()) {
                        Text(m.month, Modifier.weight(1f))
                        Text("in ${usd(m.incomeCents)}", Modifier.weight(1f), textAlign = TextAlign.End)
                        Text("out ${usd(m.expenseCents)}", Modifier.weight(1f), textAlign = TextAlign.End)
                        Text(
                            usd(m.netCents), Modifier.weight(1f), textAlign = TextAlign.End,
                            color = if (m.netCents < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
            Text("Transactions", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
        }

        items(transactions, key = { it.id }) { t ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Box(Modifier.weight(1f)) {
                    Text("${t.description}\n${t.date} · ${t.category.name.lowercase()}", style = MaterialTheme.typography.bodyMedium)
                }
                Text(
                    usd(t.amountCents),
                    color = if (t.amountCents < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier = Modifier) {
    InfoCard(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Text(value, style = MaterialTheme.typography.titleMedium)
    }
}
