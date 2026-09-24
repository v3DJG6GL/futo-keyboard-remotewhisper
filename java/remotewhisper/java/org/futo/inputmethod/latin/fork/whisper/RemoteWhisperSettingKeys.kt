package org.futo.inputmethod.latin.fork.whisper

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import org.futo.inputmethod.latin.uix.SettingsKey

// Custom (remote) OpenAI-compatible Whisper API server. A single global server config; whether it
// is actually used is decided per-language via [voiceInputRemoteKeyFor].
//
// The preference key names must not change: they hold the settings of existing installs.

val CUSTOM_WHISPER_SERVER_URL = SettingsKey(
    key = stringPreferencesKey("custom_whisper_url"),
    default = ""
)

val CUSTOM_WHISPER_API_KEY = SettingsKey(
    key = stringPreferencesKey("custom_whisper_key"),
    default = ""
)

val CUSTOM_WHISPER_MODEL = SettingsKey(
    key = stringPreferencesKey("custom_whisper_model"),
    default = ""
)

// Per-language opt-in: when true (and a server URL is configured), voice input for [localeTag]
// routes to the custom Whisper server instead of an on-device model.
fun voiceInputRemoteKeyFor(localeTag: String) = SettingsKey(
    key = booleanPreferencesKey("voiceinput_remote_$localeTag"),
    default = false
)
