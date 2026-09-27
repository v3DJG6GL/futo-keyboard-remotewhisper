package org.futo.inputmethod.latin.personalization

import android.content.Context
import org.futo.inputmethod.latin.DictionaryFacilitator
import org.futo.inputmethod.latin.DictionaryFacilitatorLruCache
import org.futo.inputmethod.latin.SuggestionBlacklist
import org.futo.inputmethod.latin.UserHistoryDictionaryReader
import org.futo.inputmethod.latin.makedict.WordProperty
import org.futo.inputmethod.latin.uix.SUGGESTION_BLACKLIST
import org.futo.inputmethod.latin.uix.UserDictionaryIO
import org.futo.inputmethod.latin.uix.getSetting
import java.io.Closeable
import java.util.Locale

/**
 * Loads the learned words of a language and classifies them against the other dictionaries.
 * Blocking; use from a background thread and [close] when done.
 */
class LearnedWordsRepository(private val context: Context) : Closeable {
    // Main + personal dictionaries (no user history), used to tell known words from unknown ones.
    private val knownWordsLookup = DictionaryFacilitatorLruCache(context, "learnedwords_")

    data class LoadResult(
        val locale: Locale,
        val words: List<LearnedWord>,
        /** False if no main dictionary is available; then every word looks unknown. */
        val canTellKnownWords: Boolean,
    )

    /** Returns null when the dictionary could not be read. */
    fun load(locale: Locale, includeNgrams: Boolean): LoadResult? {
        val properties = UserHistoryDictionaryReader.getAllWordProperties(
            userHistoryDictionaryFor(context, locale)
        ) ?: return null

        val personalWords = UserDictionaryIO(context).get().mapTo(HashSet()) { it.word }
        val lookup: DictionaryFacilitator? = runCatching { knownWordsLookup.get(listOf(locale)) }.getOrNull()
        val canTellKnownWords = lookup?.hasAtLeastOneInitializedMainDictionary() == true

        val words = properties
            .filter { !it.mIsBeginningOfSentence && !it.mIsNotAWord }
            .map { property ->
                val word = property.mWord
                LearnedWord(
                    word = word,
                    locale = locale.toString(),
                    count = property.mProbabilityInfo.mCount,
                    lastUsed = property.mProbabilityInfo.mTimestamp,
                    probability = property.probability,
                    known = if (canTellKnownWords) isKnown(lookup!!, word, locale) else null,
                    inPersonalDictionary = word in personalWords,
                    ngrams = if (includeNgrams) ngramsOf(property) else emptyList(),
                )
            }

        return LoadResult(locale, words, canTellKnownWords)
    }

    /**
     * Unknown words that are worth offering for the personal dictionary, most used first. With
     * [inPersonalDictionary], the learned words that are already in it instead.
     */
    fun candidatesForPersonalDictionary(
        result: LoadResult,
        minUses: Int,
        inPersonalDictionary: Boolean = false,
    ): List<LearnedWord> {
        val blacklist = context.getSetting(SUGGESTION_BLACKLIST)
        val locale = result.locale
        return result.words
            // The lookup includes the personal dictionary, so its words count as known.
            .filter {
                if (inPersonalDictionary) it.inPersonalDictionary
                else !it.inPersonalDictionary && (it.known == false || (it.known == null && !result.canTellKnownWords))
            }
            .filter { it.uses >= minUses }
            .filter { isPlausiblePersonalDictionaryWord(it.word) }
            .filter { word -> SuggestionBlacklist.getCapitalVariants(word.word, locale).none { it in blacklist } }
            .sortedWith(compareByDescending<LearnedWord> { it.uses }.thenBy { it.word })
    }

    /**
     * Makes the keyboard forget [words] of [locale] (typos, for example). Removal and flush are
     * queued on the dictionary thread, so a following [load] no longer sees them.
     */
    fun forget(locale: Locale, words: Collection<String>) {
        val dictionary = userHistoryDictionaryFor(context, locale)
        words.forEach { dictionary.removeUnigramEntryDynamically(it) }
        dictionary.asyncFlushBinaryDictionary()
    }

    override fun close() {
        knownWordsLookup.closeDictionaries()
    }

    companion object {
        fun isKnown(lookup: DictionaryFacilitator, word: String, locale: Locale): Boolean =
            lookup.isValidSuggestionWord(word) || lookup.isValidSuggestionWord(word.lowercase(locale))

        private fun ngramsOf(property: WordProperty): List<LearnedNgram> =
            property.mNgrams.orEmpty().map { ngram ->
                val context = ngram.mNgramContext
                LearnedNgram(
                    // NgramContext numbers the previous words nearest first.
                    context = (context.prevWordCount downTo 1).map { n ->
                        if (context.isNthPrevWordBeginningOfSentence(n)) "<s>" else context.getNthPrevWord(n).toString()
                    },
                    next = ngram.mTargetWord.mWord,
                    count = ngram.mTargetWord.mProbabilityInfo.mCount,
                    probability = ngram.mTargetWord.probability,
                )
            }
    }
}
