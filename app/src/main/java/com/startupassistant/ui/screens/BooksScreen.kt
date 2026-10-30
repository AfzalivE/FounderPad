package com.startupassistant.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Timelapse
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.startupassistant.AppGraph
import com.startupassistant.data.Transaction
import com.startupassistant.data.TxCategory
import kotlinx.coroutines.delay
import java.time.LocalDate
import kotlin.math.roundToLong

private val TxCategory.icon: ImageVector
    get() = when (this) {
        TxCategory.REVENUE -> Icons.AutoMirrored.Filled.TrendingUp
        TxCategory.FUNDING -> Icons.Filled.Savings
        TxCategory.PAYROLL -> Icons.Filled.People
        TxCategory.SOFTWARE -> Icons.Filled.Code
        TxCategory.RENT -> Icons.Filled.Home
        TxCategory.MARKETING -> Icons.Filled.Campaign
        TxCategory.LEGAL -> Icons.Filled.Gavel
        TxCategory.TRAVEL -> Icons.Filled.Flight
        TxCategory.OTHER -> Icons.Filled.Receipt
    }

private val TxCategory.label: String
    get() = name.lowercase().replaceFirstChar { it.uppercase() }

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
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "header") {
            Column {
                ScreenHeader("Books", "Record money in and out. See your burn rate and your runway.", Icons.Filled.AccountBalanceWallet, TabShapes.books)
                CashHero(
                    cashCents = summary.cashBalanceCents,
                    burnCents = summary.averageMonthlyBurnCents,
                    runwayMonths = summary.runwayMonths,
                    modifier = Modifier.enterStagger(2),
                )
            }
        }

        item(key = "add") {
            Column(Modifier.enterStagger(3)) {
                SectionTitle("Add a transaction")
                InfoCard {
                    ConnectedChoice(
                        options = listOf("Money out", "Money in"),
                        selected = if (moneyIn) 1 else 0,
                        onSelect = { moneyIn = it == 1 },
                    )
                    OutlinedTextField(
                        description, { description = it }, Modifier.fillMaxWidth().padding(top = 8.dp),
                        label = { Text("Description, for example: AWS") }, singleLine = true, shape = MaterialTheme.shapes.medium,
                    )
                    OutlinedTextField(
                        amount, { amount = it }, Modifier.fillMaxWidth().padding(top = 8.dp),
                        label = { Text("Amount (USD)") }, singleLine = true, shape = MaterialTheme.shapes.medium,
                        prefix = { Text("$") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )
                    AnimatedVisibility(error != null, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                        Text(error.orEmpty(), color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
                    }
                    Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "The category is picked from the description.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f).padding(end = 8.dp),
                        )
                        Button(
                            onClick = {
                                val cents = amount.toDoubleOrNull()?.times(100)?.roundToLong()
                                if (cents == null || cents <= 0 || description.isBlank()) {
                                    error = "Enter a description and an amount more than 0."
                                } else {
                                    books.add(LocalDate.now(), description, if (moneyIn) cents else -cents)
                                    description = ""; amount = ""; error = null
                                }
                            },
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.size(6.dp))
                            Text("Add")
                        }
                    }
                }
            }
        }

        item(key = "categories") {
            Column(Modifier.enterStagger(4)) {
                SectionTitle("Expenses by category")
                val max = (summary.expensesByCategory.values.maxOrNull() ?: 1L).coerceAtLeast(1L)
                InfoCard {
                    summary.expensesByCategory.entries.sortedByDescending { it.value }.forEachIndexed { i, (cat, cents) ->
                        CategoryBar(cat, cents, cents.toFloat() / max, i)
                    }
                }
            }
        }

        item(key = "months") {
            Column(Modifier.enterStagger(5)) {
                SectionTitle("By month")
                InfoCard {
                    Row(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
                        listOf("Month", "In", "Out", "Net").forEachIndexed { i, h ->
                            Text(
                                h, Modifier.weight(1f),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = if (i == 0) TextAlign.Start else TextAlign.End,
                            )
                        }
                    }
                    summary.months.forEach { m ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(m.month, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                            Text(usd(m.incomeCents), Modifier.weight(1f), textAlign = TextAlign.End, style = MaterialTheme.typography.bodyMedium)
                            Text(usd(m.expenseCents), Modifier.weight(1f), textAlign = TextAlign.End, style = MaterialTheme.typography.bodyMedium)
                            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                                AmountPill(m.netCents)
                            }
                        }
                    }
                }
                SectionTitle("Transactions")
            }
        }

        itemsIndexed(transactions, key = { _, t -> t.id }) { i, t ->
            TransactionRow(t, Modifier.animateItem().enterStagger(i))
        }
        item(key = "bottom") { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
internal fun CashHero(cashCents: Long, burnCents: Long, runwayMonths: Double?, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Card(
        modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
    ) {
        Column(
            Modifier
                .background(Brush.linearGradient(listOf(scheme.primary, scheme.tertiary)))
                .padding(22.dp),
        ) {
            Text("Cash", style = MaterialTheme.typography.labelLarge, color = scheme.onPrimary.copy(alpha = 0.85f))
            AnimatedUsd(cashCents, style = MaterialTheme.typography.displaySmall, color = scheme.onPrimary)
            Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                HeroStat(Icons.Filled.LocalFireDepartment, "Monthly burn", Modifier.weight(1f)) {
                    AnimatedUsd(burnCents, style = MaterialTheme.typography.titleLarge, color = scheme.onPrimary)
                }
                HeroStat(Icons.Filled.Timelapse, "Runway", Modifier.weight(1f)) {
                    Text(
                        runwayMonths?.let { "%.1f mo".format(it) } ?: "Profitable",
                        style = MaterialTheme.typography.titleLarge,
                        color = scheme.onPrimary,
                    )
                }
            }
        }
    }
}

@Composable
private fun HeroStat(icon: ImageVector, label: String, modifier: Modifier = Modifier, value: @Composable () -> Unit) {
    val onPrimary = MaterialTheme.colorScheme.onPrimary
    Surface(color = onPrimary.copy(alpha = 0.16f), contentColor = onPrimary, shape = MaterialTheme.shapes.medium, modifier = modifier) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
                Text(label, style = MaterialTheme.typography.labelMedium)
            }
            value()
        }
    }
}

@Composable
private fun CategoryBar(cat: TxCategory, cents: Long, fraction: Float, index: Int) {
    // Bars grow from zero, one after the other.
    val grow = remember { Animatable(0f) }
    LaunchedEffect(fraction) {
        delay(index * 60L)
        grow.animateTo(fraction, tween(700, easing = FastOutSlowInEasing))
    }
    Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        IconDot(cat.icon, MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer)
        Column(Modifier.weight(1f)) {
            Row(Modifier.fillMaxWidth()) {
                Text(cat.label, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                Text(usd(cents), style = MaterialTheme.typography.bodyMedium)
            }
            Box(
                Modifier
                    .padding(top = 6.dp)
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(grow.value.coerceIn(0f, 1f))
                        .height(8.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Brush.horizontalGradient(listOf(MaterialTheme.colorScheme.tertiary, MaterialTheme.colorScheme.primary))),
                )
            }
        }
    }
}

@Composable
private fun AmountPill(cents: Long) {
    val negative = cents < 0
    Surface(
        color = if (negative) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
        contentColor = if (negative) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer,
        shape = RoundedCornerShape(50),
    ) {
        Text(usd(cents), style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
    }
}

@Composable
private fun TransactionRow(t: Transaction, modifier: Modifier = Modifier) {
    val moneyIn = t.amountCents >= 0
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.medium, modifier = modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            IconDot(
                t.category.icon,
                if (moneyIn) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
                if (moneyIn) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column(Modifier.weight(1f)) {
                Text(t.description, style = MaterialTheme.typography.titleSmall)
                Text(
                    "${t.date} · ${t.category.label}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                (if (moneyIn) "+" else "") + usd(t.amountCents),
                style = MaterialTheme.typography.titleMedium,
                color = if (moneyIn) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
