package org.futo.inputmethod.latin

import android.util.Log
import org.futo.inputmethod.latin.makedict.WordProperty
import org.futo.inputmethod.latin.utils.AsyncResultHolder
import org.futo.inputmethod.latin.utils.ExecutorUtils

/**
 * Reads the contents of a user history dictionary (or any [ExpandableBinaryDictionary]).
 *
 * Reads run on the dictionary executor, the single thread that performs every write, reload
 * and flush of these dictionaries. Queued behind the pending operations, a read sees every word
 * learned before the call and never races a writer. This is unlike
 * [ExpandableBinaryDictionary.getWordPropertiesForSyncing], which gives up after 100 ms and then
 * silently returns nothing.
 *
 * All functions block; call them from a background thread.
 */
object UserHistoryDictionaryReader {
    private const val TAG = "UserHistoryDictReader"
    private const val COUNT_TIMEOUT_MS = 5_000L
    private const val DUMP_TIMEOUT_MS = 120_000L

    private fun <T> readOnDictionaryThread(
        dictionary: ExpandableBinaryDictionary,
        timeoutMs: Long,
        read: (BinaryDictionary) -> T
    ): T? {
        dictionary.reloadDictionaryIfRequired()
        val result = AsyncResultHolder<T?>(TAG)
        ExecutorUtils.getBackgroundExecutor(ExecutorUtils.KEYBOARD).execute {
            val value = try {
                dictionary.binaryDictionary?.takeIf { it.isValidDictionary }?.let(read)
            } catch (e: Exception) {
                Log.e(TAG, "Reading the dictionary failed", e)
                null
            }
            result.set(value)
        }
        return result.get(null, timeoutMs)
    }

    /**
     * How often [word] has been recorded, or null if the dictionary does not contain it.
     *
     * The first commit of a word that no main dictionary knows only creates an entry with count 0,
     * which the dictionary treats as absent; such words therefore report `uses - 1`.
     */
    fun getCount(dictionary: ExpandableBinaryDictionary, word: String): Int? =
        readOnDictionaryThread(dictionary, COUNT_TIMEOUT_MS) { binaryDictionary ->
            if (!binaryDictionary.isInDictionary(word)) {
                null
            } else {
                binaryDictionary.getWordProperty(word, false /* isBeginningOfSentence */)
                    ?.mProbabilityInfo?.mCount
            }
        }

    /** Every unigram with its n-grams, or null if the dictionary could not be read in time. */
    fun getAllWordProperties(dictionary: ExpandableBinaryDictionary): List<WordProperty>? =
        readOnDictionaryThread(dictionary, DUMP_TIMEOUT_MS) { binaryDictionary ->
            val properties = ArrayList<WordProperty>()
            var token = 0
            do {
                val result = binaryDictionary.getNextWordProperty(token)
                properties.add(result.mWordProperty ?: break)
                token = result.mNextToken
            } while (token != 0)
            properties
        }
}
