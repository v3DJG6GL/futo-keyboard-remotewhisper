package org.futo.inputmethod.latin.uix.settings.pages.learnedwords

import androidx.annotation.PluralsRes
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.SliderDefaults
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Formats a plural resource whose text contains `%1$d`, setting the number in monospace so it
 * reads as the value the adjacent slider controls.
 */
@Composable
internal fun countLabel(@PluralsRes id: Int, count: Int): AnnotatedString {
    val template = LocalContext.current.resources.getQuantityString(id, count)
    val parts = template.split("%1\$d", limit = 2)
    return buildAnnotatedString {
        append(parts[0])
        withStyle(SpanStyle(fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.primary)) {
            append(count.toString())
        }
        if (parts.size > 1) append(parts[1])
    }
}

/**
 * A whole-number setting shown as a sentence containing its value, with a large, finger-friendly
 * slider below and − / + buttons for exact single steps.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CountSlider(
    @PluralsRes label: Int,
    value: Int,
    range: IntRange,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    val haptics = LocalHapticFeedback.current
    // Track the thumb continuously while dragging; commit whole numbers.
    var position by remember(value) { mutableFloatStateOf(value.toFloat()) }
    val shown = position.roundToInt()

    fun commit(newValue: Int) {
        val clamped = newValue.coerceIn(range)
        position = clamped.toFloat()
        onValueChange(clamped)
    }

    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(countLabel(label, shown), style = MaterialTheme.typography.titleMedium)
        if (subtitle != null) {
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = 4.dp)
        ) {
            StepButton("−", enabled = shown > range.first) { commit(shown - 1) }

            val colors = SliderDefaults.colors(
                // Snap points stay; their dots are only noise at this density.
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent,
            )
            val interaction = remember { MutableInteractionSource() }
            Slider(
                value = position,
                onValueChange = {
                    if (it.roundToInt() != position.roundToInt()) {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                    position = it
                },
                onValueChangeFinished = { commit(position.roundToInt()) },
                valueRange = range.first.toFloat()..range.last.toFloat(),
                steps = (range.last - range.first - 1).coerceAtLeast(0),
                colors = colors,
                interactionSource = interaction,
                thumb = {
                    Box(
                        Modifier
                            .size(width = 20.dp, height = 44.dp)
                            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(10.dp))
                    )
                },
                track = { state ->
                    SliderDefaults.Track(
                        sliderState = state,
                        colors = colors,
                        modifier = Modifier.height(12.dp),
                        drawStopIndicator = null,
                    )
                },
                modifier = Modifier.weight(1f).heightIn(min = 56.dp),
            )

            StepButton("+", enabled = shown < range.last) { commit(shown + 1) }
        }
    }
}

@Composable
private fun StepButton(symbol: String, enabled: Boolean, onClick: () -> Unit) {
    FilledTonalIconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(48.dp),
    ) {
        Text(symbol, style = MaterialTheme.typography.titleLarge, fontFamily = FontFamily.Monospace)
    }
}

/** One chip per language; the active one is filled and checked. */
@Composable
internal fun LanguageChips(
    locales: List<Locale>,
    selected: Locale?,
    onSelect: (Locale) -> Unit,
) {
    val displayLocale = LocalConfiguration.current.locales[0]
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        locales.forEach { locale ->
            val isSelected = locale == selected
            FilterChip(
                selected = isSelected,
                onClick = { onSelect(locale) },
                label = { Text(locale.getDisplayName(displayLocale)) },
                leadingIcon = if (isSelected) {
                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                } else null,
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                    selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        }
    }
}
