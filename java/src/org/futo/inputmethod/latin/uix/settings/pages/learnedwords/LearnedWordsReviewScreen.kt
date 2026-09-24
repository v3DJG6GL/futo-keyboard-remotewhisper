package org.futo.inputmethod.latin.uix.settings.pages.learnedwords

import android.content.Context
import android.text.format.DateUtils
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
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
import org.futo.inputmethod.latin.personalization.LearnedWordsStoreLanguageOnly
import org.futo.inputmethod.latin.personalization.personalDictionaryLocale
import org.futo.inputmethod.latin.uix.PersonalWord
import org.futo.inputmethod.latin.uix.UserDictionaryIO
import org.futo.inputmethod.latin.uix.getSetting
import org.futo.inputmethod.latin.uix.settings.ScreenTitle
import org.futo.inputmethod.latin.uix.settings.Tip
import org.futo.inputmethod.latin.uix.settings.useDataStoreValue
import java.util.Locale

private sealed interface ReviewState {
    data object Loading : ReviewState
    data object Failed : ReviewState
    data class Loaded(val result: LearnedWordsRepository.LoadResult) : ReviewState
}

/**
 * Lists the unknown words the keyboard learned for a language. Selected words can be added to the
 * personal dictionary or deleted from the learned words (typos). Optionally also lists the learned
 * words that are already in the personal dictionary, dimmed and not selectable.
 */
@Composable
fun LearnedWordsReviewScreen(navController: NavHostController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val locales = learnedWordsLocales(context)
    val repository = remember { LearnedWordsRepository(context) }
    DisposableEffect(Unit) { onDispose { repository.close() } }

    var locale by remember { mutableStateOf(locales.firstOrNull()) }
    var minUses by remember { mutableIntStateOf(context.getSetting(LearnedWordsAutoAddUses)) }
    var state by remember { mutableStateOf<ReviewState>(ReviewState.Loading) }
    var reloadKey by remember { mutableIntStateOf(0) }
    var confirmDelete by remember { mutableStateOf(false) }
    var showAdded by rememberSaveable { mutableStateOf(false) }
    val selected: SnapshotStateList<String> = remember { emptyList<String>().toMutableStateList() }

    LaunchedEffect(locale, reloadKey) {
        val current = locale ?: return@LaunchedEffect
        state = ReviewState.Loading
        selected.clear()
        state = withContext(Dispatchers.IO) {
            repository.load(current, includeNgrams = false)
        }?.let { ReviewState.Loaded(it) } ?: ReviewState.Failed
    }

    val candidates by remember {
        derivedStateOf {
            (state as? ReviewState.Loaded)?.let {
                repository.candidatesForPersonalDictionary(it.result, minUses, includePersonalDictionary = showAdded)
            } ?: emptyList()
        }
    }
    val selectable by remember { derivedStateOf { candidates.filter { !it.inPersonalDictionary } } }
    // Keep the selection to words that are still selectable after the threshold changes.
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

        CountSlider(
            label = R.plurals.learned_words_review_min_uses_label,
            value = minUses,
            range = 1..20,
            onValueChange = { minUses = it },
        )

        ViewModeSwitch(showAdded) { showAdded = it }

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
                if (candidates.isEmpty()) {
                    CenteredMessage { Text(stringResource(R.string.learned_words_review_none)) }
                } else {
                    SelectAllRow(selectable, candidates.size - selectable.size, selected)
                    HorizontalDivider()
                    LazyColumn(Modifier.weight(1f)) {
                        items(candidates, key = { it.word }) { word ->
                            CandidateRow(word, word.word in selected) { checked ->
                                if (checked) selected.add(word.word) else selected.remove(word.word)
                            }
                        }
                    }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ViewModeSwitch(showAdded: Boolean, onChange: (Boolean) -> Unit) {
    val labels = listOf(R.string.learned_words_review_show_new, R.string.learned_words_review_show_all)
    SingleChoiceSegmentedButtonRow(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        labels.forEachIndexed { index, label ->
            SegmentedButton(
                selected = showAdded == (index == 1),
                onClick = { onChange(index == 1) },
                shape = SegmentedButtonDefaults.itemShape(index, labels.size),
            ) { Text(stringResource(label)) }
        }
    }
}

/** [candidates] are the selectable words; [alreadyAdded] counts the listed words that are not. */
@Composable
private fun SelectAllRow(candidates: List<LearnedWord>, alreadyAdded: Int, selected: SnapshotStateList<String>) {
    val allSelected = candidates.isNotEmpty() && candidates.all { it.word in selected }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = candidates.isNotEmpty()) {
                if (allSelected) {
                    selected.clear()
                } else {
                    selected.clear(); selected.addAll(candidates.map { it.word })
                }
            }
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Checkbox(checked = allSelected, onCheckedChange = null, enabled = candidates.isNotEmpty())
        Spacer(Modifier.width(16.dp))
        Text(
            stringResource(R.string.learned_words_review_select_all),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        Column(horizontalAlignment = Alignment.End) {
            Text(
                pluralStringResource(R.plurals.learned_words_review_word_count, candidates.size, candidates.size),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (alreadyAdded > 0) {
                Text(
                    pluralStringResource(R.plurals.learned_words_review_already_added_count, alreadyAdded, alreadyAdded),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun CandidateRow(word: LearnedWord, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val lastUsed = DateUtils.getRelativeTimeSpanString(
        word.lastUsed * 1000L, System.currentTimeMillis(), DateUtils.DAY_IN_MILLIS
    ).toString()
    val added = word.inPersonalDictionary
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !added) { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // Already added: ticked but greyed out, so it reads as done rather than as selected.
        Checkbox(checked = checked || added, onCheckedChange = null, enabled = !added)
        Spacer(Modifier.width(16.dp))
        Column(if (added) Modifier.alpha(0.6f) else Modifier) {
            Text(
                word.word,
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
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
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
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            content()
        }
    }
}
