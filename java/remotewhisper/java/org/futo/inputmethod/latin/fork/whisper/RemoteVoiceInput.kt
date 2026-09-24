package org.futo.inputmethod.latin.fork.whisper

import android.content.Context
import kotlinx.coroutines.runBlocking
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.fork.whisper.remote.ModelRemoteApi
import org.futo.inputmethod.latin.fork.whisper.remote.RemoteWhisperConfig
import org.futo.inputmethod.latin.uix.FileKind
import org.futo.inputmethod.engine.GlobalIMEMessage
import org.futo.inputmethod.engine.IMEMessage
import org.futo.inputmethod.latin.uix.getSetting
import org.futo.inputmethod.latin.uix.setSetting
import org.futo.voiceinput.shared.types.ModelLoader
import java.util.Locale

/**
 * Entry points of the custom remote Whisper server feature, called from hooks in upstream code
 * (ResourceHelper in ImportResourceActivity.kt).
 */
object RemoteVoiceInput {
    // True if the user opted this locale into the custom (remote) Whisper server. Mirrors the
    // multi-variant key lookup used by ResourceHelper.findKeyForLocaleAndKind so reads match
    // the UI's writes.
    fun isEnabledForLocale(context: Context, locale: Locale): Boolean {
        val keysToTry = listOf(locale.toString(), locale.language)
        return keysToTry.any { context.getSetting(voiceInputRemoteKeyFor(it)) }
    }

    fun isServerConfigured(context: Context): Boolean =
        context.getSetting(CUSTOM_WHISPER_SERVER_URL).isNotBlank()

    fun configFromSettings(context: Context) = RemoteWhisperConfig(
        baseUrl = context.getSetting(CUSTOM_WHISPER_SERVER_URL),
        apiKey = context.getSetting(CUSTOM_WHISPER_API_KEY).ifBlank { null },
        model = context.getSetting(CUSTOM_WHISPER_MODEL).ifBlank { null }
    )

    /**
     * The model to use for [locale] when the user opted it into the custom server, else null so
     * the regular lookup (imported model file, then built-in fallback) runs.
     */
    fun modelFor(context: Context, locale: Locale): ModelLoader? {
        if (!isEnabledForLocale(context, locale) || !isServerConfigured(context)) return null

        return ModelRemoteApi(
            name = R.string.fork_voice_input_custom_server_model_name,
            config = configFromSettings(context),
            forcedLanguage = locale.language.ifBlank { null }
        )
    }

    // Opt this locale into (or out of) the custom remote Whisper server.
    fun setEnabledForLocale(context: Context, locale: Locale, enabled: Boolean) {
        runBlocking { context.setSetting(voiceInputRemoteKeyFor(locale.toString()), enabled) }
        if (!enabled) {
            runBlocking { context.setSetting(voiceInputRemoteKeyFor(locale.language), false) }
        }
        GlobalIMEMessage.tryEmit(IMEMessage.ReloadResources)
    }

    // Removing/reverting a voice input model also turns off the custom remote server opt-in.
    fun onResourceDeleted(context: Context, kind: FileKind, locale: Locale) {
        if (kind != FileKind.VoiceInput) return
        runBlocking { context.setSetting(voiceInputRemoteKeyFor(locale.toString()), false) }
        runBlocking { context.setSetting(voiceInputRemoteKeyFor(locale.language), false) }
    }
}
