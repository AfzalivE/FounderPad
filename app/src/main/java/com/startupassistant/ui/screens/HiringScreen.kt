package com.startupassistant.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.startupassistant.AppGraph
import com.startupassistant.ai.JobPostPolisher
import com.startupassistant.data.Level
import com.startupassistant.data.MockHiringRepository.Companion.usd
import kotlinx.coroutines.launch

@Composable
fun HiringScreen(modifier: Modifier = Modifier) {
    val hiring = AppGraph.hiring
    val roles = remember { hiring.roles() }
    var roleId by rememberSaveable { mutableStateOf(roles.first().id) }
    var level by rememberSaveable { mutableStateOf(Level.MID) }
    var location by rememberSaveable { mutableStateOf("San Francisco") }
    var company by rememberSaveable { mutableStateOf("") }
    var pitch by rememberSaveable { mutableStateOf("") }
    var post by rememberSaveable { mutableStateOf<String?>(null) }
    var polishError by remember { mutableStateOf<String?>(null) }
    var polishing by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val polisher = remember { JobPostPolisher(context) }
    DisposableEffect(Unit) { onDispose { polisher.close() } }
    val scope = rememberCoroutineScope()

    val salary = remember(roleId, level, location) { hiring.salary(roleId, level, location.ifBlank { "Remote" }) }
    val candidates = remember(roleId) { hiring.candidates(roleId) }
    val role = roles.first { it.id == roleId }

    LazyColumn(
        modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            ScreenHeader("Hiring", "Plan your first hires, write a job post and see who is available.")
            SampleDataNote("There is no free salary or candidate API.")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                roles.forEach { r -> FilterChip(selected = r.id == roleId, onClick = { roleId = r.id; post = null }, label = { Text(r.title) }) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Level.entries.forEach { l ->
                    FilterChip(selected = l == level, onClick = { level = l }, label = { Text(l.name.lowercase().replaceFirstChar { it.uppercase() }) })
                }
            }
            OutlinedTextField(location, { location = it }, Modifier.fillMaxWidth(), label = { Text("Location or Remote") }, singleLine = true)
            Text(role.summary, Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            InfoCard {
                Text("Base salary", style = MaterialTheme.typography.labelMedium)
                Text(usd(salary.medianUsd), style = MaterialTheme.typography.headlineSmall)
                Text("${usd(salary.lowUsd)} to ${usd(salary.highUsd)}")
                Text("Equity: ${salary.equityPercentLow}% to ${salary.equityPercentHigh}%", Modifier.padding(top = 4.dp))
            }
        }

        item {
            Text("Write a job post", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
            OutlinedTextField(company, { company = it }, Modifier.fillMaxWidth(), label = { Text("Company name") }, singleLine = true)
            OutlinedTextField(pitch, { pitch = it }, Modifier.fillMaxWidth(), label = { Text("What does your company do?") }, minLines = 2)
            Button(
                onClick = { post = hiring.draftJobPost(roleId, level, company.trim(), pitch, location.ifBlank { "Remote" }).body },
                enabled = company.isNotBlank(),
                modifier = Modifier.padding(top = 8.dp),
            ) { Text("Write job post") }
        }

        post?.let { text ->
            item {
                InfoCard { SelectionContainer { Text(text) } }
                Text("Rewrite on device with Gemini Nano:", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 8.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    JobPostPolisher.Style.entries.forEach { style ->
                        OutlinedButton(
                            enabled = !polishing,
                            onClick = {
                                polishing = true
                                polishError = null
                                scope.launch {
                                    try {
                                        post = polisher.polish(text, style)
                                    } catch (e: Exception) {
                                        polishError = e.localizedMessage ?: "Rewriting failed."
                                    } finally {
                                        polishing = false
                                    }
                                }
                            },
                        ) { Text(style.label) }
                    }
                }
                if (polishing) Text("Rewriting…")
                polishError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        }

        item { Text("Candidates (${candidates.size})", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp)) }
        items(candidates, key = { it.id }) { c ->
            InfoCard {
                Text(c.name, style = MaterialTheme.typography.titleSmall)
                Text(c.headline)
                Text(
                    "${c.location} · ${c.yearsExperience} yrs · ${c.level.name.lowercase()}" + if (c.openToRemote) " · open to remote" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(c.skills.joinToString(" · "), style = MaterialTheme.typography.labelSmall)
            }
        }

        item {
            Text("Before your first hire", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
            hiring.checklist.forEach { Text("• $it") }
            Text("", Modifier.fillMaxWidth().padding(bottom = 16.dp))
        }
    }
}
