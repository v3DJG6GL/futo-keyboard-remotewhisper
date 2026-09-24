package org.futo.inputmethod.latin.uix.settings.pages.learnedwords

import android.text.format.DateUtils
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
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
import org.futo.inputmethod.latin.uix.settings.SettingSliderForDataStoreItem
import org.futo.inputmethod.latin.uix.settings.SettingToggleDataStore
import org.futo.inputmethod.latin.uix.settings.DataStoreItem
import org.futo.inputmethod.latin.uix.settings.Tip
import java.util.Locale
import kotlin.math.roundToInt

private sealed interface ReviewState {
    data object Loading : ReviewState
    data object Failed : ReviewState
    data class Loaded(val result: LearnedWordsRepository.LoadResult) : ReviewState
}

/** Lists unknown learned words of a language and adds the selected ones to the personal dictionary. */
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
            (state as? ReviewState.Loaded)?.let { repository.candidatesForPersonalDictionary(it.result, minUses) }
                ?: emptyList()
        }
    }

    Column(Modifier.fillMaxSize()) {
        ScreenTitle(stringResource(R.string.learned_words_review), showBack = true, navController = navController)

        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            locales.forEach { option ->
                FilterChip(
                    selected = option == locale,
                    onClick = { locale = option },
                    label = { Text(option.getDisplayName(LocalConfiguration.current.locales[0])) }
                )
            }
        }

        SettingSliderForDataStoreItem(
            title = stringResource(R.string.learned_words_review_min_uses),
            item = DataStoreItem(minUses) { minUses = it },
            default = LearnedWordsAutoAddUses.default,
            range = 1.0f..20.0f,
            transform = { it.roundToInt() },
            steps = 18,
        )
        SettingToggleDataStore(
            title = stringResource(R.string.learned_words_language_only),
            setting = LearnedWordsStoreLanguageOnly,
            subtitle = stringResource(R.string.learned_words_language_only_subtitle)
        )

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
                    SelectAllRow(candidates, selected)
                    LazyColumn(Modifier.weight(1f)) {
                        items(candidates, key = { it.word }) { word ->
                            CandidateRow(word, word.word in selected) { checked ->
                                if (checked) selected.add(word.word) else selected.remove(word.word)
                            }
                        }
                    }
                    Button(
                        enabled = selected.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        onClick = {
                            val target = locale ?: return@Button
                            val words = selected.toList()
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    addToPersonalDictionary(context, words, target)
                                }
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.learned_words_review_added, words.size),
                                    Toast.LENGTH_SHORT
                                ).show()
                                reloadKey++
                            }
                        }
                    ) {
                        Text(stringResource(R.string.learned_words_review_add_selected, selected.size))
                    }
                }
            }
        }
    }
}

private fun addToPersonalDictionary(context: android.content.Context, words: List<String>, locale: Locale) {
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
private fun SelectAllRow(candidates: List<LearnedWord>, selected: SnapshotStateList<String>) {
    val allSelected = candidates.all { it.word in selected }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable {
            if (allSelected) selected.clear() else {
                selected.clear(); selected.addAll(candidates.map { it.word })
            }
        }.padding(horizontal = 8.dp)
    ) {
        Checkbox(checked = allSelected, onCheckedChange = null)
        Text(stringResource(R.string.learned_words_review_select_all, candidates.size))
    }
}

@Composable
private fun CandidateRow(word: LearnedWord, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val lastUsed = DateUtils.getRelativeTimeSpanString(
        word.lastUsed * 1000L, System.currentTimeMillis(), DateUtils.DAY_IN_MILLIS
    ).toString()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable { onCheckedChange(!checked) }.padding(horizontal = 8.dp)
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Column(Modifier.padding(vertical = 6.dp)) {
            Text(word.word, style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(R.string.learned_words_review_word_uses, word.uses, lastUsed),
                style = MaterialTheme.typography.bodySmall
            )
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
