package org.futo.inputmethod.latin.personalization

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import kotlinx.serialization.Serializable
import org.futo.inputmethod.latin.localeFromString
import org.futo.inputmethod.latin.uix.SettingsKey
import java.util.Locale

// "Learned words" are the words the keyboard records in its per-language user history
// dictionaries (UserHistoryDictionary.<locale>.dict) while you type. They drive predictions but,
// unlike the Android personal dictionary, cannot be viewed or edited. These features make them
// visible (export) and let unknown words graduate into the personal dictionary (auto-add,
// review screen).

/** Add unknown words to the personal dictionary once they are used often enough. */
val LearnedWordsAutoAddSetting = SettingsKey(
    key = booleanPreferencesKey("learned_words_auto_add"),
    default = false
)

/** Number of uses after which an unknown word is added. */
val LearnedWordsAutoAddUses = SettingsKey(
    key = intPreferencesKey("learned_words_auto_add_uses"),
    default = 4
)

/** Also add an unknown word right away when it is picked verbatim from the suggestion strip. */
val LearnedWordsAddOnManualPick = SettingsKey(
    key = booleanPreferencesKey("learned_words_add_on_manual_pick"),
    default = true
)

/** Store added words for the language only (e.g. de instead of de_CH). */
val LearnedWordsStoreLanguageOnly = SettingsKey(
    key = booleanPreferencesKey("learned_words_store_language_only"),
    default = false
)

const val LEARNED_WORDS_PERSONAL_DICTIONARY_FREQUENCY = 250

/** A recorded word sequence: [context] (in reading order) was followed by [next]. */
@Serializable
data class LearnedNgram(
    val context: List<String>,
    val next: String,
    val count: Int,
    val probability: Int,
)

@Serializable
data class LearnedWord(
    val word: String,
    val locale: String,
    /** Recorded uses. For words no main dictionary knows this is `uses - 1`. */
    val count: Int,
    /** Last use, in seconds since the epoch. */
    val lastUsed: Int,
    val probability: Int,
    /** Known to the main, personal or contacts dictionary; null when that could not be checked. */
    val known: Boolean?,
    val inPersonalDictionary: Boolean,
    /** Recorded sequences whose context ends with this word. */
    val ngrams: List<LearnedNgram> = emptyList(),
) {
    /** Estimated number of times the word was typed. */
    val uses: Int get() = if (known == true) count else count + 1
}

/** Whether a word should be offered for the personal dictionary at all. */
fun isPlausiblePersonalDictionaryWord(word: String): Boolean {
    if (word.length <= 1 || word.length > 48) return false
    if (word.none { it.isLetter() }) return false
    return word.all { it.isLetter() || it == '\'' || it == '’' || it == '-' }
}

/** Locales that have a user history dictionary file, including ones no longer enabled. */
fun userHistoryDictionaryLocales(context: Context): List<Locale> {
    val prefix = UserHistoryDictionary.NAME + "."
    return context.filesDir.listFiles()
        .orEmpty()
        .map { it.name }
        .filter { it.startsWith(prefix) && it.endsWith(".dict") }
        .map { it.removePrefix(prefix).removeSuffix(".dict") }
        .filter { it.isNotEmpty() }
        .map { localeFromString(it) }
        .distinct()
}

/** The user history dictionary of [locale]; the same instance the keyboard uses while running. */
fun userHistoryDictionaryFor(context: Context, locale: Locale): UserHistoryDictionary =
    PersonalizationHelper.getUserHistoryDictionary(context, locale, null)

/** The locale under which a word is stored in the personal dictionary. */
fun personalDictionaryLocale(locale: Locale, languageOnly: Boolean): Locale =
    if (languageOnly && locale.language.isNotEmpty()) Locale(locale.language) else locale
