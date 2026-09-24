package org.futo.inputmethod.latin.fork.whisper.remote

import android.content.Context
import org.futo.voiceinput.shared.ggml.IWhisperInference
import org.futo.voiceinput.shared.types.ModelLoader

/**
 * [ModelLoader] for a remote OpenAI-compatible Whisper server. Unlike the on-device loaders, this
 * "loads" instantly (no file mapping / no native model) — it just wraps the server config in a
 * [RemoteWhisperModel].
 *
 * @param name           display name resource (shown in the Languages screen).
 * @param config         the server configuration.
 * @param forcedLanguage optional Whisper language code to force on the server.
 */
class ModelRemoteApi(
    override val name: Int,
    private val config: RemoteWhisperConfig,
    private val forcedLanguage: String? = null
) : ModelLoader {
    override fun exists(context: Context): Boolean = config.isValid

    override fun getRequiredDownloadList(context: Context): List<String> = listOf()

    override fun loadGGML(context: Context): IWhisperInference =
        RemoteWhisperModel(config, forcedLanguage)

    override fun key(context: Context): Any =
        "Remote:${config.baseUrl}:${config.model}:$forcedLanguage"
}
