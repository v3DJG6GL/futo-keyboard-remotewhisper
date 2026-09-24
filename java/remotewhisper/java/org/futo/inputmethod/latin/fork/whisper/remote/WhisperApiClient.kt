package org.futo.inputmethod.latin.fork.whisper.remote

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException
import kotlin.coroutines.resumeWithException

/**
 * Thrown when a remote transcription request fails. [message] is suitable for showing to the user.
 */
class RemoteTranscriptionException(message: String, cause: Throwable? = null) :
    Exception(message, cause)

sealed class TestConnectionResult {
    data class Success(val message: String) : TestConnectionResult()
    data class Failure(val message: String) : TestConnectionResult()
}

/**
 * Thin OkHttp wrapper for OpenAI-compatible audio transcription
 * (`POST {base}/v1/audio/transcriptions`). Used by both [RemoteWhisperModel] for live inference and
 * by the settings "Test connection" button.
 *
 * A single instance owns one OkHttpClient and tracks the in-flight [Call] so [cancelOngoing] can
 * abort it (e.g. when the user cancels voice input). Coroutine cancellation also aborts the call.
 */
class WhisperApiClient {
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    @Volatile
    private var currentCall: Call? = null

    /**
     * Transcribe [wavBytes] via the configured server. Returns the recognized text, which may be
     * an empty string (e.g. for silence) — the caller treats blanks as "no result", matching the
     * on-device backend. Only network/HTTP failures throw.
     * @throws RemoteTranscriptionException on any network/HTTP failure.
     */
    suspend fun transcribe(
        config: RemoteWhisperConfig,
        wavBytes: ByteArray,
        languageCode: String?,
        prompt: String?
    ): String {
        val request = buildTranscriptionRequest(config, wavBytes, languageCode, prompt)
        val response = execute(request)

        response.use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw RemoteTranscriptionException(httpErrorMessage(resp.code, body))
            }

            val text = try {
                JSONObject(body).optString("text", "")
            } catch (e: Exception) {
                // Some servers may return text/plain. Fall back to the raw body.
                body
            }

            // Blank is a valid result (silence). Don't treat it as an error.
            return text.trim()
        }
    }

    /**
     * Validate the server by actually exercising the transcription API with the configured key:
     * POSTs a short silent clip to `/v1/audio/transcriptions`. This is the authoritative check —
     * unlike `GET /v1/models` (which many servers leave unauthenticated), the transcription endpoint
     * enforces auth, so a wrong/missing key on a protected server surfaces as 401/403. Any HTTP 2xx
     * is treated as success regardless of transcribed text (silence yields empty/hallucinated text).
     * Never throws.
     */
    suspend fun testConnection(config: RemoteWhisperConfig): TestConnectionResult {
        if (!config.isValid) {
            return TestConnectionResult.Failure("Server URL is empty")
        }

        return try {
            transcribe(
                config = config,
                wavBytes = WavEncoder.silentWavBytes(),
                languageCode = null,
                prompt = null
            )
            TestConnectionResult.Success("Connection successful")
        } catch (e: RemoteTranscriptionException) {
            TestConnectionResult.Failure(e.message ?: "Connection failed")
        } catch (e: Exception) {
            TestConnectionResult.Failure(friendlyNetworkMessage(e))
        }
    }

    fun cancelOngoing() {
        currentCall?.cancel()
    }

    private fun buildTranscriptionRequest(
        config: RemoteWhisperConfig,
        wavBytes: ByteArray,
        languageCode: String?,
        prompt: String?
    ): Request {
        val multipartBuilder = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "file",
                "audio.wav",
                wavBytes.toRequestBody("audio/wav".toMediaType())
            )
            .addFormDataPart("response_format", "json")

        config.model?.takeIf { it.isNotBlank() }?.let {
            multipartBuilder.addFormDataPart("model", it)
        }
        languageCode?.takeIf { it.isNotBlank() }?.let {
            multipartBuilder.addFormDataPart("language", it)
        }
        prompt?.takeIf { it.isNotBlank() }?.let {
            multipartBuilder.addFormDataPart("prompt", it)
        }

        val requestBuilder = Request.Builder()
            .url(normalizeUrl(config.baseUrl))
            .post(multipartBuilder.build())

        config.apiKey?.takeIf { it.isNotBlank() }?.let {
            requestBuilder.header("Authorization", "Bearer $it")
        }

        return requestBuilder.build()
    }

    private suspend fun execute(request: Request): Response =
        suspendCancellableCoroutine { continuation ->
            val call = client.newCall(request)
            currentCall = call

            continuation.invokeOnCancellation {
                call.cancel()
            }

            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isCancelled) return
                    continuation.resumeWithException(
                        RemoteTranscriptionException(friendlyNetworkMessage(e), e)
                    )
                }

                override fun onResponse(call: Call, response: Response) {
                    continuation.resumeWith(Result.success(response))
                }
            })
        }

    companion object {
        /**
         * Append the OpenAI transcription path unless the user already supplied a full path.
         */
        fun normalizeUrl(baseUrl: String): String {
            var url = baseUrl.trim()
            if (url.contains("/audio/transcriptions")) return url
            url = url.trimEnd('/')
            // Allow the user to provide ".../v1" or just the host.
            return if (url.endsWith("/v1")) {
                "$url/audio/transcriptions"
            } else {
                "$url/v1/audio/transcriptions"
            }
        }

        private fun httpErrorMessage(code: Int, body: String): String {
            val detail = body.take(200).trim()
            return when (code) {
                401, 403 -> "Authentication failed (HTTP $code). Check the API key."
                404 -> "Endpoint not found (HTTP 404). Check the server URL."
                else -> "Server error (HTTP $code)" + if (detail.isNotEmpty()) ": $detail" else ""
            }
        }

        private fun friendlyNetworkMessage(e: Throwable): String = when (e) {
            is UnknownHostException -> "Host not found. Check the server URL."
            is ConnectException -> "Could not connect to the server."
            is SocketTimeoutException -> "Connection timed out."
            is SSLException -> "TLS/SSL error. Check the certificate or use http://."
            else -> e.message ?: "Network error"
        }
    }
}
