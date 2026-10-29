package com.startupassistant.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.startupassistant.AppGraph
import com.startupassistant.ai.JobPostPolisher
import com.startupassistant.data.Candidate
import com.startupassistant.data.Level
import com.startupassistant.data.MockHiringRepository.Companion.usd
import com.startupassistant.data.SalaryBenchmark
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
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
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "header") {
            Column {
                ScreenHeader("Hiring", "Plan your first hires, write a job post and see who is available.", Icons.Filled.Groups, TabShapes.hiring)
                SampleDataNote("There is no free salary or candidate API.")
                Row(
                    Modifier.horizontalScroll(rememberScrollState()).enterStagger(2),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    roles.forEach { r ->
                        FilterChip(selected = r.id == roleId, onClick = { roleId = r.id; post = null }, label = { Text(r.title) })
                    }
                }
                ConnectedChoice(
                    options = Level.entries.map { l -> l.name.lowercase().replaceFirstChar { it.uppercase() } },
                    selected = level.ordinal,
                    onSelect = { level = Level.entries[it] },
                    modifier = Modifier.padding(top = 8.dp).enterStagger(3),
                )
                OutlinedTextField(
                    location, { location = it }, Modifier.fillMaxWidth().padding(top = 8.dp).enterStagger(4),
                    label = { Text("Location or Remote") }, singleLine = true,
                    leadingIcon = { Icon(Icons.Filled.LocationOn, contentDescription = null) },
                    shape = MaterialTheme.shapes.medium,
                )
                AnimatedContent(
                    targetState = role.summary,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "summary",
                ) { summary ->
                    Text(summary, Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                SalaryCard(salary, Modifier.enterStagger(5))
            }
        }

        item(key = "post-form") {
            Column {
                SectionTitle("Write a job post")
                OutlinedTextField(
                    company, { company = it }, Modifier.fillMaxWidth(),
                    label = { Text("Company name") }, singleLine = true, shape = MaterialTheme.shapes.medium,
                )
                OutlinedTextField(
                    pitch, { pitch = it }, Modifier.fillMaxWidth().padding(top = 8.dp),
                    label = { Text("What does your company do?") }, minLines = 2, shape = MaterialTheme.shapes.medium,
                )
                Button(
                    onClick = { post = hiring.draftJobPost(roleId, level, company.trim(), pitch, location.ifBlank { "Remote" }).body },
                    enabled = company.isNotBlank(),
                    modifier = Modifier.padding(top = 12.dp),
                ) {
                    Icon(Icons.Filled.EditNote, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                    Text("Write job post")
                }
            }
        }

        post?.let { text ->
            item(key = "post") {
                Column(Modifier.animateItem().enterStagger(0)) {
                    InfoCard(color = MaterialTheme.colorScheme.surfaceContainer) {
                        // Cross-fades when Gemini Nano rewrites the post.
                        AnimatedContent(targetState = text, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "post") { t ->
                            SelectionContainer { Text(t) }
                        }
                    }
                    Row(
                        Modifier.padding(top = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(Icons.Filled.AutoFixHigh, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        Text("Rewrite on device with Gemini Nano", style = MaterialTheme.typography.labelLarge)
                        AnimatedVisibility(polishing) { LoadingIndicator(Modifier.size(32.dp)) }
                    }
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()).padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        JobPostPolisher.Style.entries.forEach { style ->
                            FilledTonalButton(
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
                    AnimatedVisibility(polishError != null, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                        Text(polishError.orEmpty(), color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
                    }
                }
            }
        }

        item(key = "candidates-title") { SectionTitle("Candidates (${candidates.size})", Modifier.animateItem()) }
        itemsIndexed(candidates, key = { _, c -> c.id }) { i, c ->
            CandidateCard(c, i, Modifier.animateItem().enterStagger(i))
        }

        item(key = "checklist") {
            Column(Modifier.animateItem()) {
                SectionTitle("Before your first hire")
                InfoCard {
                    hiring.checklist.forEachIndexed { i, step ->
                        Row(
                            Modifier.padding(vertical = 6.dp).enterStagger(i),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(20.dp))
                            Text(step, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun SalaryCard(salary: SalaryBenchmark, modifier: Modifier = Modifier) {
    // Where the median sits between the low and high end of the range.
    val position by animateFloatAsState(
        if (salary.highUsd > salary.lowUsd) (salary.medianUsd - salary.lowUsd).toFloat() / (salary.highUsd - salary.lowUsd) else 0.5f,
        label = "median",
    )
    InfoCard(modifier, color = MaterialTheme.colorScheme.primaryContainer) {
        Text("Base salary", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
        AnimatedUsd(
            salary.medianUsd * 100L,
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        Box(
            Modifier
                .padding(top = 12.dp)
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.12f)),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(position.coerceIn(0.02f, 1f))
                    .height(10.dp)
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.primary),
            )
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
            Text(usd(salary.lowUsd), Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Text(usd(salary.highUsd), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        Text(
            "Equity: ${salary.equityPercentLow}% to ${salary.equityPercentHigh}%",
            Modifier.padding(top = 8.dp),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CandidateCard(c: Candidate, index: Int, modifier: Modifier = Modifier) {
    InfoCard(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            InitialsAvatar(c.name, index)
            Column(Modifier.weight(1f)) {
                Text(c.name, style = MaterialTheme.typography.titleMedium)
                Text(c.headline, style = MaterialTheme.typography.bodyMedium)
            }
        }
        Row(
            Modifier.padding(top = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(Icons.Filled.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
            Text(
                "${c.location} · ${c.yearsExperience} yrs · ${c.level.name.lowercase()}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (c.openToRemote) {
                Tag("Remote", Icons.Filled.Public, MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
            }
        }
        FlowRow(
            Modifier.padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            c.skills.forEach { Tag(it, null, MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}
