package com.novelreader.core.prefs

import android.content.Context
import android.content.SharedPreferences
import com.novelreader.ui.ReaderBg
import com.novelreader.ui.ReaderFontType

/**
 * Typed SharedPreferences wrapper — Kotatsu-style AppSettings.
 * Persists reader preferences (previously ephemeral remember{} state),
 * active source, and tracker configuration.
 */
class AppSettings(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("novel_reader_settings", Context.MODE_PRIVATE)

    // ---- reader prefs ----
    var readerFontSp: Float
        get() = prefs.getFloat(KEY_FONT_SP, 18f)
        set(value) = prefs.edit().putFloat(KEY_FONT_SP, value).apply()

    var readerLineMul: Float
        get() = prefs.getFloat(KEY_LINE_MUL, 1.7f)
        set(value) = prefs.edit().putFloat(KEY_LINE_MUL, value).apply()

    var readerBg: ReaderBg
        get() = runCatching { ReaderBg.valueOf(prefs.getString(KEY_BG, null) ?: "") }
            .getOrDefault(ReaderBg.Dark)
        set(value) = prefs.edit().putString(KEY_BG, value.name).apply()

    var readerFontType: ReaderFontType
        get() = runCatching { ReaderFontType.valueOf(prefs.getString(KEY_FONT_TYPE, null) ?: "") }
            .getOrDefault(ReaderFontType.Serif)
        set(value) = prefs.edit().putString(KEY_FONT_TYPE, value.name).apply()

    var readerJustify: Boolean
        get() = prefs.getBoolean(KEY_JUSTIFY, true)
        set(value) = prefs.edit().putBoolean(KEY_JUSTIFY, value).apply()

    // ---- sources ----
    var selectedSourceId: String
        get() = prefs.getString(KEY_SOURCE, null) ?: "bacalightnovel"
        set(value) = prefs.edit().putString(KEY_SOURCE, value).apply()

    // ---- AI translation ----
    /** "gemini" (Google AI) or "openai" (any OpenAI-compatible endpoint). */
    var aiProvider: String
        get() = prefs.getString(KEY_AI_PROVIDER, null) ?: "gemini"
        set(value) = prefs.edit().putString(KEY_AI_PROVIDER, value).apply()

    /** Base URL for the OpenAI-compatible provider, e.g. https://api.openai.com/v1 or a LAN LLM server. */
    var aiBaseUrl: String
        get() = prefs.getString(KEY_AI_BASE_URL, null) ?: "https://api.openai.com/v1"
        set(value) = prefs.edit().putString(KEY_AI_BASE_URL, value.trim().trimEnd('/')).apply()

    /** Secret entered by the user at runtime; stored in app-private prefs (not backed up to VCS). */
    var aiKey: String
        get() = prefs.getString(KEY_AI_KEY, null).orEmpty()
        set(value) = prefs.edit().putString(KEY_AI_KEY, value.trim()).apply()

    var aiModel: String
        get() = prefs.getString(KEY_AI_MODEL, null) ?: "gemini-2.0-flash"
        set(value) = prefs.edit().putString(KEY_AI_MODEL, value.trim()).apply()

    var aiTargetLang: String
        get() = prefs.getString(KEY_AI_LANG, null) ?: "Indonesian"
        set(value) = prefs.edit().putString(KEY_AI_LANG, value.trim()).apply()

    // ---- app update check ----
    /** Epoch millis of the last GitHub update check (throttle). */
    var lastUpdateCheck: Long
        get() = prefs.getLong(KEY_UPDATE_CHECK, 0L)
        set(value) = prefs.edit().putLong(KEY_UPDATE_CHECK, value).apply()

    /** Version the user dismissed so the banner stops nagging until a newer one appears. */
    var dismissedUpdateVersion: String
        get() = prefs.getString(KEY_UPDATE_DISMISS, null).orEmpty()
        set(value) = prefs.edit().putString(KEY_UPDATE_DISMISS, value).apply()

    companion object {
        private const val KEY_FONT_SP = "reader.font_sp"
        private const val KEY_LINE_MUL = "reader.line_mul"
        private const val KEY_BG = "reader.bg"
        private const val KEY_FONT_TYPE = "reader.font_type"
        private const val KEY_JUSTIFY = "reader.justify"
        private const val KEY_SOURCE = "sources.selected"
        private const val KEY_AI_PROVIDER = "ai.provider"
        private const val KEY_AI_BASE_URL = "ai.base_url"
        private const val KEY_AI_KEY = "ai.key"
        private const val KEY_AI_MODEL = "ai.model"
        private const val KEY_AI_LANG = "ai.target_lang"
        private const val KEY_UPDATE_CHECK = "update.last_check"
        private const val KEY_UPDATE_DISMISS = "update.dismissed_version"
    }
}
