package org.futo.inputmethod.latin.uix.settings.pages.learnedwords

import androidx.annotation.PluralsRes
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
 * A whole-number setting shown as a sentence containing its value, with a full-width slider
 * below. The value is only changed with the slider.
 */
@Composable
internal fun CountSlider(
    @PluralsRes label: Int,
    value: Int,
    range: IntRange,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    // Track the thumb continuously while dragging; commit whole numbers.
    var position by remember(value) { mutableFloatStateOf(value.toFloat()) }
    val shown = position.roundToInt()

    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(countLabel(label, shown), style = MaterialTheme.typography.titleMedium)
        if (subtitle != null) {
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Slider(
            value = position,
            onValueChange = { position = it },
            onValueChangeFinished = { onValueChange(position.roundToInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = (range.last - range.first - 1).coerceAtLeast(0),
        )
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
