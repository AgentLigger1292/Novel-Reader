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

    // ---- tracker ----
    var trackerEnabled: Boolean
        get() = prefs.getBoolean(KEY_TRACKER_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_TRACKER_ENABLED, value).apply()

    var trackerIntervalHours: Int
        get() = prefs.getInt(KEY_TRACKER_INTERVAL, 6).coerceIn(1, 24)
        set(value) = prefs.edit().putInt(KEY_TRACKER_INTERVAL, value.coerceIn(1, 24)).apply()

    companion object {
        private const val KEY_FONT_SP = "reader.font_sp"
        private const val KEY_LINE_MUL = "reader.line_mul"
        private const val KEY_BG = "reader.bg"
        private const val KEY_FONT_TYPE = "reader.font_type"
        private const val KEY_JUSTIFY = "reader.justify"
        private const val KEY_SOURCE = "sources.selected"
        private const val KEY_TRACKER_ENABLED = "tracker.enabled"
        private const val KEY_TRACKER_INTERVAL = "tracker.interval_hours"
    }
}
