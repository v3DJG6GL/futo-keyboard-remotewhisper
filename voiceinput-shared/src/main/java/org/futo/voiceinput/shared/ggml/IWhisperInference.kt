package org.futo.voiceinput.shared.ggml

/**
 * Common contract for a Whisper transcription backend.
 *
 * Implemented by [WhisperGGML] (on-device, native GGML) and by remote HTTP backends supplied
 * through a custom [org.futo.voiceinput.shared.types.ModelLoader]. This lets the rest of the voice input
 * pipeline ([org.futo.voiceinput.shared.whisper.MultiModelRunner],
 * [org.futo.voiceinput.shared.whisper.ModelManager]) treat any backend uniformly.
 *
 * The signatures intentionally mirror the original [WhisperGGML] methods so existing callers do
 * not change.
 */
interface IWhisperInference {
    // empty languages = autodetect any language
    // 1 language = will force that language
    // 2 or more languages = autodetect between those languages
    @Throws(BailLanguageException::class, InferenceCancelledException::class)
    suspend fun infer(
        samples: FloatArray,
        prompt: String,
        languages: Array<String>,
        bailLanguages: Array<String>,
        decodingMode: DecodingMode,
        suppressNonSpeechTokens: Boolean,
        partialResultCallback: (String) -> Unit
    ): String

    fun cancel()

    suspend fun close()
}
