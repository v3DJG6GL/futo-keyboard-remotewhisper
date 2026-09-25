package org.futo.inputmethod.latin.uix.settings.pages.learnedwords

import android.content.Context
import android.text.format.DateUtils
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.personalization.LEARNED_WORDS_PERSONAL_DICTIONARY_FREQUENCY
import org.futo.inputmethod.latin.personalization.LearnedWord
import org.futo.inputmethod.latin.personalization.LearnedWordsAutoAddUses
import org.futo.inputmethod.latin.personalization.LearnedWordsRepository
import org.futo.inputmethod.latin.personalization.LearnedWordsReviewMinUses
import org.futo.inputmethod.latin.personalization.LearnedWordsReviewMinUsesBase
import org.futo.inputmethod.latin.personalization.LearnedWordsReviewSort
import org.futo.inputmethod.latin.personalization.LearnedWordsSort
import org.futo.inputmethod.latin.personalization.LearnedWordsStoreLanguageOnly
import org.futo.inputmethod.latin.personalization.personalDictionaryLocale
import org.futo.inputmethod.latin.personalization.reviewMinUses
import org.futo.inputmethod.latin.uix.PersonalWord
import org.futo.inputmethod.latin.uix.UserDictionaryIO
import org.futo.inputmethod.latin.uix.getSetting
import org.futo.inputmethod.latin.uix.settings.ScreenTitle
import org.futo.inputmethod.latin.uix.settings.Tip
import org.futo.inputmethod.latin.uix.settings.useDataStore
import org.futo.inputmethod.latin.uix.settings.useDataStoreValue
import java.text.Collator
import java.util.Locale

private sealed interface ReviewState {
    data object Loading : ReviewState
    data object Failed : ReviewState
    data class Loaded(val result: LearnedWordsRepository.LoadResult) : ReviewState
}

private val MIN_USES_RANGE = 1..20

/**
 * Lists the unknown words the keyboard learned for a language. Selected words can be added to the
 * personal dictionary or deleted from the learned words (typos). Alternatively lists the learned
 * words that are already in the personal dictionary, dimmed and not selectable. The list can be
 * filtered by text and by how often a word was typed, and sorted.
 */
@Composable
fun LearnedWordsReviewScreen(navController: NavHostController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val locales = learnedWordsLocales(context)
    val repository = remember { LearnedWordsRepository(context) }
    DisposableEffect(Unit) { onDispose { repository.close() } }

    var locale by remember { mutableStateOf(locales.firstOrNull()) }
    var state by remember { mutableStateOf<ReviewState>(ReviewState.Loading) }
    var reloadKey by remember { mutableIntStateOf(0) }
    var confirmDelete by remember { mutableStateOf(false) }
    var showAdded by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var countPanelOpen by rememberSaveable { mutableStateOf(false) }
    val selected: SnapshotStateList<String> = remember { emptyList<String>().toMutableStateList() }

    // The count filter is remembered on its own, but follows the auto-add setting when that changes.
    val autoAddUses = useDataStoreValue(LearnedWordsAutoAddUses)
    val storedMinUses = useDataStore(LearnedWordsReviewMinUses)
    val storedMinUsesBase = useDataStore(LearnedWordsReviewMinUsesBase)
    val minUses = reviewMinUses(storedMinUses.value, storedMinUsesBase.value, autoAddUses)
        .coerceIn(MIN_USES_RANGE)
    val sortSetting = useDataStore(LearnedWordsReviewSort)
    val sort = LearnedWordsSort.entries.firstOrNull { it.name == sortSetting.value } ?: LearnedWordsSort.MostTyped

    LaunchedEffect(locale, reloadKey) {
        val current = locale ?: return@LaunchedEffect
        state = ReviewState.Loading
        selected.clear()
        state = withContext(Dispatchers.IO) {
            repository.load(current, includeNgrams = false)
        }?.let { ReviewState.Loaded(it) } ?: ReviewState.Failed
    }

    // Plain remember with explicit keys: a derivedStateOf keyed on only some inputs would keep reading
    // the State object of the first composition and ignore later sort or filter changes.
    val loaded = (state as? ReviewState.Loaded)?.result
    val candidates = remember(loaded, minUses, showAdded, sort) {
        if (loaded == null) emptyList()
        else sorted(
            repository.candidatesForPersonalDictionary(loaded, minUses, inPersonalDictionary = showAdded),
            sort, loaded.locale
        )
    }
    val rows = remember(candidates, query) {
        candidates.mapNotNull { word -> findInWord(word.word, query.trim())?.let { word to it } }
    }
    val selectable = remember(rows) { rows.map { it.first }.filter { !it.inPersonalDictionary } }
    // Keep the selection to words that are still shown after a filter changes.
    LaunchedEffect(selectable) {
        val listed = selectable.mapTo(HashSet()) { it.word }
        selected.retainAll { it in listed }
    }

    val languageOnly = useDataStoreValue(LearnedWordsStoreLanguageOnly)
    val targetDictionary = locale?.let {
        personalDictionaryLocale(it, languageOnly).getDisplayName(LocalConfiguration.current.locales[0])
    } ?: ""

    Column(Modifier.fillMaxSize()) {
        ScreenTitle(stringResource(R.string.learned_words_review), showBack = true, navController = navController)

        LanguageChips(locales, locale) { locale = it }

        FilterField(
            value = query,
            onValueChange = { query = it },
            placeholder = stringResource(R.string.learned_words_review_filter),
            clearLabel = stringResource(R.string.learned_words_review_filter_clear),
            modifier = Modifier.padding(top = 10.dp)
        )

        ControlRow(Modifier.padding(top = 10.dp)) {
            ControlButton(
                onClick = { showAdded = false },
                selected = !showAdded,
                text = stringResource(R.string.learned_words_review_show_new),
                modifier = Modifier.weight(1f)
            )
            ControlButton(
                onClick = { showAdded = true },
                selected = showAdded,
                text = stringResource(R.string.learned_words_review_show_added),
                modifier = Modifier.weight(1f)
            )
        }

        ControlRow(Modifier.padding(top = 10.dp)) {
            ControlButton(
                onClick = { countPanelOpen = !countPanelOpen },
                style = ControlStyle.Menu,
                leadingIcon = R.drawable.learned_words_filter,
                trailing = { DropdownArrow() },
                text = pluralStringResource(R.plurals.learned_words_review_min_uses_chip, minUses, minUses),
                modifier = Modifier.weight(1f)
            )
            SortButton(sort, onSort = { sortSetting.setValue(it.name) }, modifier = Modifier.weight(1f))
        }

        AnimatedVisibility(countPanelOpen) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 10.dp)
            ) {
                Column(Modifier.padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 12.dp)) {
                    CountSlider(
                        label = R.plurals.learned_words_review_min_uses_label,
                        value = minUses,
                        range = MIN_USES_RANGE,
                        onValueChange = {
                            storedMinUses.setValue(it)
                            storedMinUsesBase.setValue(autoAddUses)
                        },
                        marker = autoAddUses,
                    )
                    Text(
                        stringResource(R.string.learned_words_review_min_uses_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        when (val current = state) {
            ReviewState.Loading -> CenteredMessage {
                CircularProgressIndicator()
                Text(stringResource(R.string.learned_words_review_loading))
            }
            ReviewState.Failed -> CenteredMessage {
                Text(stringResource(R.string.learned_words_review_read_failed))
            }
            is ReviewState.Loaded -> {
                if (!current.result.canTellKnownWords) {
                    Tip(stringResource(R.string.learned_words_review_no_main_dictionary))
                }
                when {
                    candidates.isEmpty() -> CenteredMessage {
                        Text(stringResource(
                            if (showAdded) R.string.learned_words_review_none_in_dictionary
                            else R.string.learned_words_review_none
                        ))
                    }
                    rows.isEmpty() -> CenteredMessage {
                        Text(
                            stringResource(R.string.learned_words_review_no_match, query.trim()),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            stringResource(R.string.learned_words_review_no_match_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(ControlGap)) {
                            ControlButton(onClick = { query = "" }, text = stringResource(R.string.learned_words_review_filter_clear))
                            ControlButton(onClick = { countPanelOpen = true }, text = stringResource(R.string.learned_words_review_change_count))
                        }
                    }
                    else -> {
                        if (showAdded) {
                            ListHeader {
                                Text(
                                    stringResource(R.string.learned_words_review_in_dictionary_count),
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.weight(1f)
                                )
                                WordCount(rows.size)
                            }
                        } else {
                            SelectAllRow(selectable, selected)
                        }
                        HorizontalDivider()
                        val listState = rememberLazyListState()
                        Box(Modifier.weight(1f)) {
                            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                                items(rows, key = { it.first.word }) { (word, match) ->
                                    CandidateRow(word, match, word.word in selected) { checked ->
                                        if (checked) selected.add(word.word) else selected.remove(word.word)
                                    }
                                }
                            }
                            FastScrollbar(listState, rows.size) { index ->
                                scrollLabel(rows[index].first, sort, current.result.locale)
                            }
                        }
                        if (!showAdded) {
                            ActionBar(
                                selectedCount = selected.size,
                                targetDictionary = targetDictionary,
                                onDelete = { confirmDelete = true },
                                onAdd = {
                                    val target = locale ?: return@ActionBar
                                    val words = selected.toList()
                                    scope.launch {
                                        withContext(Dispatchers.IO) { addToPersonalDictionary(context, words, target) }
                                        toast(context, context.resources.getQuantityString(
                                            R.plurals.learned_words_review_added, words.size, words.size))
                                        reloadKey++
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    if (confirmDelete) {
        val count = selected.size
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(pluralStringResource(R.plurals.learned_words_review_delete_title, count, count)) },
            text = { Text(stringResource(R.string.learned_words_review_delete_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        val target = locale ?: return@TextButton
                        val words = selected.toList()
                        scope.launch {
                            withContext(Dispatchers.IO) { repository.forget(target, words) }
                            toast(context, context.resources.getQuantityString(
                                R.plurals.learned_words_review_deleted, words.size, words.size))
                            reloadKey++
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text(stringResource(R.string.learned_words_review_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text(stringResource(R.string.learned_words_review_delete_cancel))
                }
            }
        )
    }
}

private fun sorted(words: List<LearnedWord>, sort: LearnedWordsSort, locale: Locale): List<LearnedWord> {
    val collator = Collator.getInstance(locale)
    val alphabetical = Comparator<LearnedWord> { a, b -> collator.compare(a.word, b.word) }
    return words.sortedWith(when (sort) {
        LearnedWordsSort.MostTyped -> compareByDescending<LearnedWord> { it.uses }.then(alphabetical)
        LearnedWordsSort.LeastTyped -> compareBy<LearnedWord> { it.uses }.then(alphabetical)
        LearnedWordsSort.RecentlyTyped -> compareByDescending<LearnedWord> { it.lastUsed }.then(alphabetical)
        LearnedWordsSort.LongestAgo -> compareBy<LearnedWord> { it.lastUsed }.then(alphabetical)
        LearnedWordsSort.AToZ -> alphabetical
        LearnedWordsSort.ZToA -> alphabetical.reversed()
    })
}

/** What the scrollbar bubble shows for [word]: the value the list is sorted by. */
private fun scrollLabel(word: LearnedWord, sort: LearnedWordsSort, locale: Locale): String = when (sort) {
    LearnedWordsSort.MostTyped, LearnedWordsSort.LeastTyped -> "${word.uses}×"
    LearnedWordsSort.RecentlyTyped, LearnedWordsSort.LongestAgo -> {
        val days = ((System.currentTimeMillis() / 1000 - word.lastUsed) / 86400).coerceAtLeast(0)
        if (days < 60) "${days}d" else "${days / 30}m"
    }
    LearnedWordsSort.AToZ, LearnedWordsSort.ZToA -> word.word.take(1).uppercase(locale)
}

/** Each sort key with its default direction first. */
private val SORT_PAIRS = listOf(
    LearnedWordsSort.MostTyped to LearnedWordsSort.LeastTyped,
    LearnedWordsSort.RecentlyTyped to LearnedWordsSort.LongestAgo,
    LearnedWordsSort.AToZ to LearnedWordsSort.ZToA,
)

private fun sortLabel(sort: LearnedWordsSort) = when (sort) {
    LearnedWordsSort.MostTyped -> R.string.learned_words_review_sort_most_typed
    LearnedWordsSort.LeastTyped -> R.string.learned_words_review_sort_least_typed
    LearnedWordsSort.RecentlyTyped -> R.string.learned_words_review_sort_recently_typed
    LearnedWordsSort.LongestAgo -> R.string.learned_words_review_sort_longest_ago
    LearnedWordsSort.AToZ -> R.string.learned_words_review_sort_a_to_z
    LearnedWordsSort.ZToA -> R.string.learned_words_review_sort_z_to_a
}

/** Whether [sort] lists small values (few uses, old, A) first; drawn as an up arrow. */
private fun ascending(sort: LearnedWordsSort) =
    sort == LearnedWordsSort.LeastTyped || sort == LearnedWordsSort.LongestAgo || sort == LearnedWordsSort.AToZ

@Composable
private fun DropdownArrow() {
    Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(18.dp))
}

/** One button with a menu of sort keys; picking the active key again reverses its direction. */
@Composable
private fun SortButton(sort: LearnedWordsSort, onSort: (LearnedWordsSort) -> Unit, modifier: Modifier) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        ControlButton(
            onClick = { open = true },
            style = ControlStyle.Menu,
            leadingIcon = if (ascending(sort)) R.drawable.arrow_up else R.drawable.arrow_down,
            trailing = { DropdownArrow() },
            text = stringResource(sortLabel(sort)),
            modifier = Modifier.fillMaxWidth()
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            SORT_PAIRS.forEach { (first, second) ->
                val active = sort == first || sort == second
                val shown = if (active) sort else first
                DropdownMenuItem(
                    text = { Text(stringResource(sortLabel(shown))) },
                    leadingIcon = {
                        Box(Modifier.size(24.dp)) {
                            if (active) Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        }
                    },
                    trailingIcon = {
                        Icon(
                            painterResource(if (ascending(shown)) R.drawable.arrow_up else R.drawable.arrow_down),
                            contentDescription = null, modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    onClick = {
                        onSort(if (!active) first else if (sort == first) second else first)
                        open = false
                    }
                )
            }
            Text(
                stringResource(R.string.learned_words_review_sort_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            )
        }
    }
}

private fun toast(context: Context, text: String) =
    Toast.makeText(context, text, Toast.LENGTH_SHORT).show()

private fun addToPersonalDictionary(context: Context, words: List<String>, locale: Locale) {
    val storedLocale = personalDictionaryLocale(locale, context.getSetting(LearnedWordsStoreLanguageOnly))
    UserDictionaryIO(context).put(words.map {
        PersonalWord(
            word = it,
            frequency = LEARNED_WORDS_PERSONAL_DICTIONARY_FREQUENCY,
            locale = storedLocale.toString(),
            appId = 0,
            shortcut = null
        )
    })
}

@Composable
private fun ListHeader(content: @Composable RowScope.() -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).height(48.dp),
        content = content
    )
}

@Composable
private fun WordCount(count: Int) {
    Text(
        pluralStringResource(R.plurals.learned_words_review_word_count, count, count),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/** Selects or clears exactly the words the filters show. */
@Composable
private fun SelectAllRow(candidates: List<LearnedWord>, selected: SnapshotStateList<String>) {
    val count = candidates.count { it.word in selected }
    val toggleState = when (count) {
        0 -> ToggleableState.Off
        candidates.size -> ToggleableState.On
        else -> ToggleableState.Indeterminate
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(
                value = toggleState == ToggleableState.On,
                enabled = candidates.isNotEmpty(),
                role = Role.Checkbox,
                onValueChange = {
                    selected.clear()
                    if (toggleState != ToggleableState.On) selected.addAll(candidates.map { it.word })
                }
            )
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .height(48.dp)
    ) {
        TriStateCheckbox(state = toggleState, onClick = null, enabled = candidates.isNotEmpty())
        Spacer(Modifier.width(16.dp))
        Text(
            stringResource(R.string.learned_words_review_select_all),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        WordCount(candidates.size)
    }
}

@Composable
private fun CandidateRow(word: LearnedWord, match: IntRange, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val lastUsed = DateUtils.getRelativeTimeSpanString(
        word.lastUsed * 1000L, System.currentTimeMillis(), DateUtils.DAY_IN_MILLIS
    ).toString()
    val added = word.inPersonalDictionary
    val addedLabel = stringResource(R.string.learned_words_review_in_dictionary_count)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked || added, enabled = !added, role = Role.Checkbox, onValueChange = onCheckedChange)
            .semantics(mergeDescendants = true) { if (added) stateDescription = addedLabel }
            // Room on the right for the scrollbar.
            .padding(start = 16.dp, end = 28.dp, top = 8.dp, bottom = 8.dp)
    ) {
        // Already added: ticked but greyed out, so it reads as done rather than as selected.
        Checkbox(checked = checked || added, onCheckedChange = null, enabled = !added)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f).alpha(if (added) 0.6f else 1f)) {
            Text(
                highlighted(word.word, match),
                style = MaterialTheme.typography.bodyLarge,
                color = if (added) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
            )
            Text(
                if (added) stringResource(R.string.learned_words_review_word_in_dictionary, word.uses, lastUsed)
                else stringResource(R.string.learned_words_review_word_uses, word.uses, lastUsed),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            stringResource(R.string.learned_words_review_times, word.uses),
            fontFamily = FontFamily.Monospace,
            color = if (added) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 12.dp).alpha(if (added) 0.6f else 1f)
        )
    }
}

@Composable
private fun ActionBar(
    selectedCount: Int,
    targetDictionary: String,
    onDelete: () -> Unit,
    onAdd: () -> Unit,
) {
    Surface(tonalElevation = 3.dp, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                if (selectedCount == 0) {
                    stringResource(R.string.learned_words_review_nothing_selected)
                } else {
                    pluralStringResource(R.plurals.learned_words_review_selection, selectedCount, selectedCount, targetDictionary)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
            )
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedButton(
                    enabled = selectedCount > 0,
                    onClick = onDelete,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.weight(1f)
                ) { Text(stringResource(R.string.learned_words_review_delete)) }
                Button(
                    enabled = selectedCount > 0,
                    onClick = onAdd,
                    modifier = Modifier.weight(1.4f)
                ) { Text(stringResource(R.string.learned_words_review_add)) }
            }
        }
    }
}

@Composable
private fun CenteredMessage(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            content()
        }
    }
}
