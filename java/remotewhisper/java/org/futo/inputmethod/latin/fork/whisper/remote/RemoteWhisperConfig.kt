package org.futo.inputmethod.latin.fork.whisper.remote

/**
 * Configuration for a custom OpenAI-compatible Whisper transcription server.
 *
 * @param baseUrl    The server base URL (e.g. "http://192.168.1.10:8000" or
 *                   "https://api.openai.com"). The transcription path is appended automatically
 *                   by [WhisperApiClient] if not already present.
 * @param apiKey     Optional bearer token. Sent as "Authorization: Bearer <key>" when non-blank.
 * @param model      Optional model name. Sent as the multipart "model" field when non-blank;
 *                   otherwise omitted so the server uses its own default.
 */
data class RemoteWhisperConfig(
    val baseUrl: String,
    val apiKey: String? = null,
    val model: String? = null
) {
    val isValid: Boolean
        get() = baseUrl.isNotBlank()
}
