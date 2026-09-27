package org.futo.inputmethod.latin.personalization

import android.content.Context
import android.provider.UserDictionary
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.futo.inputmethod.latin.DictionaryFacilitator
import org.futo.inputmethod.latin.UserHistoryDictionaryReader
import org.futo.inputmethod.latin.common.StringUtils
import org.futo.inputmethod.latin.uix.getSetting
import java.util.Locale

/**
 * Adds words that no dictionary knows to the Android personal dictionary once the keyboard has
 * learned them often enough, or right away when the user picks them verbatim from the
 * suggestion strip. Called for every word the keyboard learns (GeneralIME.addToHistory).
 */
class PersonalDictionaryAutoAdd(
    private val context: Context,
    private val dictionaryFacilitator: DictionaryFacilitator,
    private val scope: CoroutineScope,
) {
    // Words added recently, so a word is not added twice while the personal dictionary reloads.
    private val recentlyAdded = object : LinkedHashMap<String, Unit>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Unit>?) = size > 64
    }

    /**
     * @param wasAutoCapitalized the first letter was capitalised automatically (sentence start);
     *   the keyboard learns such words in lower case, so they are left alone here.
     * @param importance learning weight; [MANUAL_PICK_IMPORTANCE] or more when the typed word was
     *   picked from the suggestion strip.
     */
    fun onWordLearned(word: String, wasAutoCapitalized: Boolean, importance: Int) {
        if (!context.getSetting(LearnedWordsAutoAddSetting)) return
        if (wasAutoCapitalized || !isPlausiblePersonalDictionaryWord(word)) return

        // The keyboard learns into the history of the most confident language only.
        val locale = dictionaryFacilitator.mostConfidentLocale
        if (locale == Locale.ROOT) return
        if (dictionaryFacilitator.isValidSuggestionWord(word)) return
        if (dictionaryFacilitator.isValidSuggestionWord(word.lowercase(locale))) return

        val manualPick = importance >= MANUAL_PICK_IMPORTANCE &&
                context.getSetting(LearnedWordsAddOnManualPick)
        val minUses = context.getSetting(LearnedWordsAutoAddUses)
        val languageOnly = context.getSetting(LearnedWordsStoreLanguageOnly)
        val singleLanguage = dictionaryFacilitator.locales.size <= 1

        scope.launch(Dispatchers.IO) {
            val key = "$locale/$word"
            synchronized(recentlyAdded) { if (recentlyAdded.containsKey(key)) return@launch }

            // Queued behind the learning of this very commit, so the count includes it.
            val count = UserHistoryDictionaryReader.getCount(userHistoryDictionaryFor(context, locale), word)
            if (!shouldAdd(count, minUses, manualPick, singleLanguage)) return@launch
            if (isInPersonalDictionary(word)) return@launch

            val storedLocale = personalDictionaryLocale(locale, languageOnly)
            runCatching {
                UserDictionary.Words.addWord(
                    context, word, LEARNED_WORDS_PERSONAL_DICTIONARY_FREQUENCY, null, storedLocale
                )
            }.onSuccess {
                synchronized(recentlyAdded) { recentlyAdded[key] = Unit }
                Log.i(TAG, "Added \"$word\" to the personal dictionary ($storedLocale, count=$count)")
            }.onFailure {
                Log.w(TAG, "Could not add \"$word\" to the personal dictionary", it)
            }
        }
    }

    /**
     * Deletes the capitalized form of the lower-case [word] from the personal dictionary ("Ich"
     * when "ich" was picked), for the current language only, if the main dictionary knows [word]
     * but not that form. Such an entry, often added by another keyboard, otherwise keeps beating
     * the lower-case word.
     */
    fun forgetCapitalizedForm(word: String) {
        val locale = dictionaryFacilitator.mostConfidentLocale
        if (locale == Locale.ROOT) return
        val capitalized = StringUtils.capitalizeFirstCodePoint(word, locale)
        if (capitalized == word) return
        if (!dictionaryFacilitator.isValidMainDictionaryWord(word)) return
        if (dictionaryFacilitator.isValidMainDictionaryWord(capitalized)) return

        val language = locale.language
        scope.launch(Dispatchers.IO) {
            runCatching {
                context.contentResolver.delete(
                    UserDictionary.Words.CONTENT_URI,
                    "${UserDictionary.Words.WORD}=? AND (${UserDictionary.Words.LOCALE} IS NULL OR " +
                        "${UserDictionary.Words.LOCALE}=? OR ${UserDictionary.Words.LOCALE} LIKE ?)",
                    arrayOf(capitalized, language, "${language}_%")
                )
            }.onSuccess {
                if (it > 0) Log.i(TAG, "Deleted \"$capitalized\" from the personal dictionary ($language)")
            }.onFailure {
                Log.w(TAG, "Could not delete \"$capitalized\" from the personal dictionary", it)
            }
        }
    }

    private fun isInPersonalDictionary(word: String): Boolean = runCatching {
        context.contentResolver.query(
            UserDictionary.Words.CONTENT_URI,
            arrayOf(UserDictionary.Words._ID),
            "${UserDictionary.Words.WORD}=?",
            arrayOf(word),
            null
        )?.use { it.count > 0 } ?: false
    }.getOrDefault(false)

    companion object {
        private const val TAG = "PersonalDictAutoAdd"

        /** Lowest importance InputLogic passes for a word picked from the suggestion strip. */
        const val STRIP_PICK_IMPORTANCE = 1

        /** Importance InputLogic passes when the typed word itself was picked from the strip. */
        const val MANUAL_PICK_IMPORTANCE = 3

        /**
         * @param count recorded count of an unknown word (uses - 1), or null if not recorded yet.
         * @param singleLanguage with several languages, a word must have been recorded in the
         *   language's history before a manual pick adds it, since the keyboard may not have
         *   learned it into that language at all.
         */
        fun shouldAdd(count: Int?, minUses: Int, manualPick: Boolean, singleLanguage: Boolean): Boolean {
            if (manualPick && (singleLanguage || count != null)) return true
            return count != null && count + 1 >= minUses
        }
    }
}
