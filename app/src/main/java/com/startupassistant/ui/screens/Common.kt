package com.startupassistant.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.RoundedPolygon
import kotlinx.coroutines.delay
import kotlin.math.roundToLong

/** Shapes that give each tab and avatar its own character. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
object TabShapes {
    val ask get() = MaterialShapes.Cookie9Sided
    val events get() = MaterialShapes.Sunny
    val hiring get() = MaterialShapes.Clover4Leaf
    val books get() = MaterialShapes.Cookie6Sided
    val avatars: List<RoundedPolygon>
        get() = listOf(
            MaterialShapes.Cookie4Sided, MaterialShapes.Pentagon, MaterialShapes.Clover4Leaf,
            MaterialShapes.Gem, MaterialShapes.Sunny, MaterialShapes.Arch,
        )
}

/** A shape from [MaterialShapes] filled with a gradient, with an icon in the center. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ShapeBadge(
    icon: ImageVector,
    polygon: RoundedPolygon,
    modifier: Modifier = Modifier,
    size: Dp = 52.dp,
    spin: Boolean = false,
    colors: List<Color> = listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary),
    iconTint: Color = MaterialTheme.colorScheme.onPrimary,
) {
    val shape = remember(polygon) { polygon.toShape() }
    val rotation = if (spin) {
        val t = rememberInfiniteTransition(label = "spin")
        t.animateFloat(0f, 360f, infiniteRepeatable(tween(14_000, easing = LinearEasing)), label = "angle").value
    } else {
        0f
    }
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .matchParentSize()
                .graphicsLayer { rotationZ = rotation }
                .clip(shape)
                .background(Brush.linearGradient(colors)),
        )
        Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(size * 0.46f))
    }
}

/** A slow "breathing" scale, used to make hero shapes feel alive. */
@Composable
fun rememberPulse(): Float {
    val t = rememberInfiniteTransition(label = "pulse")
    return t.animateFloat(
        0.94f, 1.04f,
        infiniteRepeatable(tween(1_800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "scale",
    ).value
}

@Composable
fun ScreenHeader(title: String, subtitle: String, icon: ImageVector, polygon: RoundedPolygon) {
    Row(
        Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 12.dp).enterStagger(0),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ShapeBadge(icon, polygon)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineLarge)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun SampleDataNote(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp).enterStagger(1),
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Outlined.Info, contentDescription = null, modifier = Modifier.size(18.dp))
            Text("Sample data. $text", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleLarge,
        modifier = modifier.padding(top = 20.dp, bottom = 4.dp),
    )
}

@Composable
fun InfoCard(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    content: @Composable () -> Unit,
) {
    Card(
        modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = color),
    ) {
        Column(Modifier.padding(18.dp)) { content() }
    }
}

/** A round avatar with initials, in one of the Expressive shapes. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun InitialsAvatar(name: String, index: Int, modifier: Modifier = Modifier) {
    val polygons = TabShapes.avatars
    val shape = remember(index) { polygons[index % polygons.size].toShape() }
    val palette = listOf(
        MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer,
        MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer,
        MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer,
    )
    val (bg, fg) = palette[index % palette.size]
    val initials = name.split(" ").filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }
    Box(modifier.size(48.dp).clip(shape).background(bg), contentAlignment = Alignment.Center) {
        Text(initials, color = fg, style = MaterialTheme.typography.titleMedium)
    }
}

/** A small round icon on a tonal background. */
@Composable
fun IconDot(icon: ImageVector, container: Color, content: Color, modifier: Modifier = Modifier) {
    Box(modifier.size(40.dp).clip(CircleShape).background(container), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(20.dp))
    }
}

/** A dollar amount that counts up to its new value. */
@Composable
fun AnimatedUsd(cents: Long, style: TextStyle, modifier: Modifier = Modifier, color: Color = Color.Unspecified) {
    val dollars = remember { Animatable(0f) }
    LaunchedEffect(cents) { dollars.animateTo((cents / 100).toFloat(), tween(900, easing = FastOutSlowInEasing)) }
    Text(usd(dollars.value.roundToLong() * 100), style = style, color = color, modifier = modifier)
}

/**
 * Fades and slides an item up the first time it shows. The index adds a short
 * delay, so a list comes in as a cascade. The "shown" flag is saved, so items do
 * not animate again when they scroll back into view.
 */
fun Modifier.enterStagger(index: Int): Modifier = composed {
    var shown by rememberSaveable { mutableStateOf(false) }
    val progress = remember { Animatable(if (shown) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (!shown) {
            delay(index.coerceAtMost(8) * 45L)
            progress.animateTo(1f, spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow))
            shown = true
        }
    }
    graphicsLayer {
        alpha = progress.value.coerceIn(0f, 1f)
        translationY = (1f - progress.value) * 40.dp.toPx()
        val s = 0.96f + 0.04f * progress.value
        scaleX = s
        scaleY = s
    }
}

/** A connected button group where one option is selected. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ConnectedChoice(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
        options.forEachIndexed { i, label ->
            ToggleButton(
                checked = i == selected,
                onCheckedChange = { onSelect(i) },
                modifier = Modifier.weight(1f).semantics { role = Role.RadioButton },
                shapes = when (i) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
            ) { Text(label) }
        }
    }
}

fun usd(cents: Long): String {
    val dollars = cents / 100
    val sign = if (dollars < 0) "-" else ""
    return "$sign$" + "%,d".format(kotlin.math.abs(dollars))
}
