package org.futo.inputmethod.latin.uix.settings.pages.learnedwords

import androidx.annotation.DrawableRes
import androidx.annotation.PluralsRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
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

/** Leading icon tinted like the icons of FUTO's own setting items. */
internal fun settingIcon(@DrawableRes icon: Int): @Composable () -> Unit = {
    Icon(
        painterResource(icon), contentDescription = null,
        tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f)
    )
}

/** Section label, aligned with the titles of FUTO's setting items (after their 80 dp icon column). */
@Composable
internal fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 80.dp, end = 16.dp, top = 20.dp, bottom = 4.dp)
    )
}

/**
 * A whole-number setting shown as a sentence containing its value, with a slim slider below and
 * − / + buttons for exact single steps. With [icon], it is laid out like FUTO's setting items;
 * [marker] draws a small dot on the track at that value.
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
    @DrawableRes icon: Int? = null,
    marker: Int? = null,
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

    val content = @Composable {
        Column(Modifier.fillMaxWidth()) {
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
                val markerColor = MaterialTheme.colorScheme.onSurfaceVariant
                Box(Modifier.weight(1f).heightIn(min = 48.dp), contentAlignment = Alignment.Center) {
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
                                    .size(width = 4.dp, height = 28.dp)
                                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp))
                            )
                        },
                        track = { state ->
                            SliderDefaults.Track(
                                sliderState = state,
                                colors = colors,
                                modifier = Modifier.height(6.dp),
                                thumbTrackGapSize = 4.dp,
                                drawStopIndicator = null,
                            )
                        },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    )
                    if (marker != null && marker in range && marker != shown) {
                        // The track spans the width minus the thumb width at each end.
                        val fraction = (marker - range.first).toFloat() / (range.last - range.first)
                        Box(Modifier.fillMaxWidth().padding(horizontal = 2.dp)) {
                            Box(
                                Modifier
                                    .fillMaxWidth(fraction.coerceIn(0.001f, 1f))
                                    .height(4.dp)
                            ) {
                                Box(
                                    Modifier
                                        .align(Alignment.CenterEnd)
                                        .offset(x = 2.dp)
                                        .size(4.dp)
                                        .background(markerColor, CircleShape)
                                )
                            }
                        }
                    }
                }

                StepButton("+", enabled = shown < range.last) { commit(shown + 1) }
            }
        }
    }

    if (icon != null) {
        // Same geometry as FUTO's SettingItem: 20 dp, a 48 dp icon column, 12 dp.
        Row(modifier.fillMaxWidth().padding(end = 16.dp, top = 12.dp, bottom = 8.dp)) {
            Spacer(Modifier.width(20.dp))
            Box(Modifier.width(48.dp).padding(top = 2.dp), contentAlignment = Alignment.TopCenter) {
                settingIcon(icon)()
            }
            Spacer(Modifier.width(12.dp))
            Box(Modifier.weight(1f)) { content() }
        }
    } else {
        Box(modifier.fillMaxWidth()) { content() }
    }
}

@Composable
private fun StepButton(symbol: String, enabled: Boolean, onClick: () -> Unit) {
    OutlinedIconButton(
        onClick = onClick,
        enabled = enabled,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.size(40.dp),
    ) {
        Text(symbol, style = MaterialTheme.typography.titleMedium, fontFamily = FontFamily.Monospace)
    }
}

internal val ControlHeight = 40.dp
internal val ControlShape = RoundedCornerShape(8.dp)
internal val ControlGap = 12.dp

enum class ControlStyle { Choice, Menu }

/**
 * The buttons above the word list share one size and shape. [ControlStyle.Choice] is outlined and
 * filled with the primary colour when [selected]; [ControlStyle.Menu] opens a menu or panel.
 */
@Composable
internal fun ControlButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    style: ControlStyle = ControlStyle.Choice,
    @DrawableRes leadingIcon: Int? = null,
    trailing: (@Composable () -> Unit)? = null,
    text: String,
) {
    val colors = MaterialTheme.colorScheme
    val (container, content, border) = when {
        style == ControlStyle.Menu -> Triple(colors.surfaceContainerHigh, colors.onSurface, null)
        selected -> Triple(colors.primary, colors.onPrimary, null)
        else -> Triple(Color.Transparent, colors.onSurfaceVariant, BorderStroke(1.dp, colors.outline))
    }
    Surface(
        onClick = onClick,
        shape = ControlShape,
        color = container,
        contentColor = content,
        border = border,
        modifier = modifier
            .height(ControlHeight)
            .semantics { if (style == ControlStyle.Choice) this.selected = selected },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
            modifier = Modifier.padding(horizontal = 8.dp)
        ) {
            if (leadingIcon != null) {
                Icon(painterResource(leadingIcon), contentDescription = null, modifier = Modifier.size(18.dp))
            }
            Text(
                text,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            trailing?.invoke()
        }
    }
}

/** A row of equally wide controls with the standard gap and side margins. */
@Composable
internal fun ControlRow(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(ControlGap),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp),
        content = content
    )
}

/** One equally wide button per language; the active one is filled. */
@Composable
internal fun LanguageChips(
    locales: List<Locale>,
    selected: Locale?,
    onSelect: (Locale) -> Unit,
) {
    val displayLocale = LocalConfiguration.current.locales[0]
    locales.chunked(2).forEachIndexed { index, pair ->
        ControlRow(Modifier.padding(top = if (index == 0) 0.dp else 8.dp)) {
            pair.forEach { locale ->
                ControlButton(
                    onClick = { onSelect(locale) },
                    selected = locale == selected,
                    text = locale.getDisplayName(displayLocale),
                    modifier = Modifier.weight(1f)
                )
            }
            if (pair.size == 1 && locales.size > 1) Spacer(Modifier.weight(1f))
        }
    }
}

/** Filter field with the same size and shape as the buttons around it. */
@Composable
internal fun FilterField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    clearLabel: String,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = ControlShape,
        color = Color.Transparent,
        border = BorderStroke(1.dp, colors.outline),
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp).height(ControlHeight)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 10.dp)) {
            Icon(Icons.Default.Search, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) {
                    Text(placeholder, style = MaterialTheme.typography.bodyLarge, color = colors.onSurfaceVariant, maxLines = 1)
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.merge(TextStyle(color = colors.onSurface)),
                    cursorBrush = SolidColor(colors.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = placeholder }
                )
            }
            if (value.isNotEmpty()) {
                IconButton(onClick = { onValueChange("") }, modifier = Modifier.size(ControlHeight)) {
                    Icon(Icons.Default.Clear, contentDescription = clearLabel, modifier = Modifier.size(20.dp))
                }
            } else {
                Spacer(Modifier.width(10.dp))
            }
        }
    }
}

/**
 * Where [query] occurs in [word], ignoring case and accents: "u" and "ue" both find "ü", "ss"
 * finds "ß". Returns the matching range of [word], or null.
 */
internal fun findInWord(word: String, query: String): IntRange? {
    if (query.isEmpty()) return IntRange.EMPTY
    for (table in listOf(STRIP_ACCENTS, SPELL_OUT_UMLAUTS)) {
        val (folded, map) = fold(word, table)
        val at = folded.indexOf(fold(query, table).first)
        if (at >= 0) {
            val length = fold(query, table).first.length
            return map[at]..map[at + length - 1]
        }
    }
    return null
}

private val SPELL_OUT_UMLAUTS = mapOf('ä' to "ae", 'ö' to "oe", 'ü' to "ue", 'ß' to "ss")
private val STRIP_ACCENTS = mapOf(
    'ä' to "a", 'ö' to "o", 'ü' to "u", 'ß' to "ss", 'à' to "a", 'á' to "a", 'â' to "a",
    'é' to "e", 'è' to "e", 'ê' to "e", 'ë' to "e", 'î' to "i", 'ï' to "i", 'ô' to "o", 'ù' to "u",
    'û' to "u", 'ç' to "c", 'ñ' to "n",
)

/** Lower-cases and folds [text]; the second value maps each folded char to its source index. */
private fun fold(text: String, table: Map<Char, String>): Pair<String, IntArray> {
    val out = StringBuilder()
    val map = ArrayList<Int>()
    text.forEachIndexed { i, c ->
        val lower = c.lowercaseChar()
        val folded = table[lower] ?: lower.toString()
        folded.forEach { out.append(it); map.add(i) }
    }
    return out.toString() to map.toIntArray()
}

/** [word] with [match] highlighted. */
@Composable
internal fun highlighted(word: String, match: IntRange?): AnnotatedString = buildAnnotatedString {
    if (match == null || match.isEmpty()) {
        append(word)
        return@buildAnnotatedString
    }
    append(word.substring(0, match.first))
    withStyle(SpanStyle(background = MaterialTheme.colorScheme.primaryContainer, color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Medium)) {
        append(word.substring(match.first, match.last + 1))
    }
    append(word.substring(match.last + 1))
}

/**
 * A visible, draggable scrollbar for [state]. While dragging, a bubble shows [label] of the first
 * visible item (e.g. its count or first letter).
 */
@Composable
internal fun BoxScope.FastScrollbar(state: LazyListState, itemCount: Int, label: (Int) -> String) {
    if (itemCount == 0) return
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    var dragging by remember { mutableStateOf(false) }
    var trackHeight by remember { mutableFloatStateOf(0f) }

    val visible = state.layoutInfo.visibleItemsInfo.size.coerceAtLeast(1)
    if (!state.canScrollForward && !state.canScrollBackward) return
    val maxFirst = (itemCount - visible).coerceAtLeast(1)
    val fraction = (state.firstVisibleItemIndex.toFloat() / maxFirst).coerceIn(0f, 1f)
    val thumbHeight = 48.dp
    val thumbPx = with(density) { thumbHeight.toPx() }
    val thumbTop = (trackHeight - thumbPx).coerceAtLeast(0f) * fraction

    Box(
        Modifier
            .align(Alignment.TopEnd)
            .fillMaxHeight()
            .width(24.dp)
            .padding(vertical = 4.dp)
            .pointerInput(itemCount) {
                trackHeight = size.height.toFloat()
                detectVerticalDragGestures(
                    onDragStart = { dragging = true },
                    onDragEnd = { dragging = false },
                    onDragCancel = { dragging = false },
                ) { change, _ ->
                    val f = ((change.position.y - thumbPx / 2) / (size.height - thumbPx)).coerceIn(0f, 1f)
                    scope.launch { state.scrollToItem((f * maxFirst).roundToInt()) }
                }
            }
    ) {
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .offset { IntOffset(0, thumbTop.roundToInt()) }
                .padding(end = 3.dp)
                .size(width = if (dragging) 8.dp else 5.dp, height = thumbHeight)
                .background(
                    if (dragging) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    RoundedCornerShape(4.dp)
                )
        )
    }
    if (dragging) {
        Surface(
            color = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp, bottomStart = 28.dp, bottomEnd = 4.dp),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset { IntOffset(-with(density) { 32.dp.roundToPx() }, thumbTop.roundToInt()) }
                .size(56.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    label(state.firstVisibleItemIndex.coerceIn(0, itemCount - 1)),
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
    }
}
