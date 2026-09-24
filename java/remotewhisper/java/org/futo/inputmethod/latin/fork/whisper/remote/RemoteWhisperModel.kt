package org.futo.inputmethod.latin.fork.whisper.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.futo.voiceinput.shared.ggml.DecodingMode
import org.futo.voiceinput.shared.ggml.IWhisperInference

/**
 * [IWhisperInference] backend that transcribes audio via a remote OpenAI-compatible Whisper server
 * instead of the on-device GGML model.
 *
 * @param config          the server configuration (URL / key / model).
 * @param forcedLanguage  optional Whisper language code (e.g. "en") to force on the server; when
 *                        null the language is inferred from the [infer] `languages` argument.
 */
class RemoteWhisperModel(
    private val config: RemoteWhisperConfig,
    private val forcedLanguage: String? = null
) : IWhisperInference {
    private val client = WhisperApiClient()

    override suspend fun infer(
        samples: FloatArray,
        prompt: String,
        languages: Array<String>,
        bailLanguages: Array<String>,
        decodingMode: DecodingMode,
        suppressNonSpeechTokens: Boolean,
        partialResultCallback: (String) -> Unit
    ): String = withContext(Dispatchers.IO) {
        // Remote backends transcribe in a single request: bailLanguages / decodingMode /
        // suppressNonSpeechTokens are local-decoding concerns and don't apply. No partial results.
        val languageCode = forcedLanguage?.takeIf { it.isNotBlank() }
            ?: languages.singleOrNull()

        val wavBytes = WavEncoder.floatArrayToWavBytes(samples)

        client.transcribe(
            config = config,
            wavBytes = wavBytes,
            languageCode = languageCode,
            prompt = prompt
        )
    }

    override fun cancel() {
        client.cancelOngoing()
    }

    override suspend fun close() {
        client.cancelOngoing()
    }
}
