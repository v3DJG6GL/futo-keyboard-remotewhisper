package org.futo.inputmethod.latin.personalization

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.OutputStream
import java.util.Locale

/** Writes learned words as JSON or CSV. */
object LearnedWordsExporter {
    enum class Format(val mimeType: String, val extension: String) {
        Json("application/json", "json"),
        Csv("text/csv", "csv"),
    }

    @Serializable
    data class ExportFile(
        val version: Int = 1,
        val exportedAt: Long,
        val languages: Map<String, List<LearnedWord>>,
    )

    private val json = Json { prettyPrint = true; encodeDefaults = true }

    fun fileName(format: Format, timestampMillis: Long): String {
        val date = java.text.SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(java.util.Date(timestampMillis))
        return "learned-words-$date.${format.extension}"
    }

    fun write(results: List<LearnedWordsRepository.LoadResult>, format: Format, out: OutputStream) {
        val text = when (format) {
            Format.Json -> toJson(results, System.currentTimeMillis())
            Format.Csv -> toCsv(results)
        }
        out.write(text.toByteArray(Charsets.UTF_8))
    }

    fun toJson(results: List<LearnedWordsRepository.LoadResult>, exportedAtMillis: Long): String =
        json.encodeToString(
            ExportFile(
                exportedAt = exportedAtMillis / 1000,
                languages = results.associate { it.locale.toString() to it.words.sortedBy { w -> w.word } },
            )
        )

    fun toCsv(results: List<LearnedWordsRepository.LoadResult>): String {
        val header = listOf(
            "locale", "word", "uses", "count", "last_used", "probability",
            "known", "in_personal_dictionary", "ngrams",
        )
        val rows = results.flatMap { result ->
            result.words.sortedBy { it.word }.map { word ->
                listOf(
                    word.locale,
                    word.word,
                    word.uses.toString(),
                    word.count.toString(),
                    word.lastUsed.toString(),
                    word.probability.toString(),
                    word.known?.toString() ?: "",
                    word.inPersonalDictionary.toString(),
                    word.ngrams.joinToString("|") { ngram ->
                        (ngram.context + ngram.next).joinToString(" ") + ":" + ngram.count
                    },
                )
            }
        }
        return (listOf(header) + rows).joinToString("\n", postfix = "\n") { row ->
            row.joinToString(",") { csvField(it) }
        }
    }

    fun csvField(value: String): String =
        if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else {
            value
        }
}
