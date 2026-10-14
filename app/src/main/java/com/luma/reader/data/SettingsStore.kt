package com.luma.reader.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode { SYSTEM, LIGHT, DARK }

class SettingsStore(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _theme = MutableStateFlow(loadTheme())
    val theme: StateFlow<ThemeMode> = _theme.asStateFlow()

    private val _fontScale = MutableStateFlow(prefs.getFloat(KEY_FONT_SCALE, DEFAULT_FONT_SCALE))
    val fontScale: StateFlow<Float> = _fontScale.asStateFlow()

    private val _highlightColor = MutableStateFlow(prefs.getInt(KEY_HIGHLIGHT_COLOR, DEFAULT_HIGHLIGHT_RGB))
    val highlightColor: StateFlow<Int> = _highlightColor.asStateFlow()

    private val _highlightOpacity =
        MutableStateFlow(prefs.getFloat(KEY_HIGHLIGHT_OPACITY, DEFAULT_HIGHLIGHT_OPACITY))
    val highlightOpacity: StateFlow<Float> = _highlightOpacity.asStateFlow()

    private val _compatibilityMode = MutableStateFlow(prefs.getBoolean(KEY_COMPATIBILITY, false))
    val compatibilityMode: StateFlow<Boolean> = _compatibilityMode.asStateFlow()

    fun setTheme(mode: ThemeMode) {
        prefs.edit().putString(KEY_THEME, mode.name).apply()
        _theme.value = mode
    }

    fun setFontScale(scale: Float) {
        val clamped = scale.coerceIn(MIN_FONT_SCALE, MAX_FONT_SCALE)
        prefs.edit().putFloat(KEY_FONT_SCALE, clamped).apply()
        _fontScale.value = clamped
    }

    fun setHighlightColor(rgb: Int) {
        prefs.edit().putInt(KEY_HIGHLIGHT_COLOR, rgb).apply()
        _highlightColor.value = rgb
    }

    fun setHighlightOpacity(opacity: Float) {
        val clamped = opacity.coerceIn(MIN_HIGHLIGHT_OPACITY, MAX_HIGHLIGHT_OPACITY)
        prefs.edit().putFloat(KEY_HIGHLIGHT_OPACITY, clamped).apply()
        _highlightOpacity.value = clamped
    }

    fun setCompatibilityMode(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_COMPATIBILITY, enabled).apply()
        _compatibilityMode.value = enabled
    }

    private fun loadTheme(): ThemeMode = runCatching {
        ThemeMode.valueOf(prefs.getString(KEY_THEME, ThemeMode.SYSTEM.name)!!)
    }.getOrDefault(ThemeMode.SYSTEM)

    companion object {
        private const val PREFS = "luma_settings"
        private const val KEY_THEME = "theme_mode"
        private const val KEY_FONT_SCALE = "font_scale"
        private const val KEY_HIGHLIGHT_COLOR = "highlight_color"
        private const val KEY_HIGHLIGHT_OPACITY = "highlight_opacity"
        private const val KEY_COMPATIBILITY = "compatibility_mode"

        const val DEFAULT_FONT_SCALE = 1.0f
        const val MIN_FONT_SCALE = 0.8f
        const val MAX_FONT_SCALE = 1.8f

        const val DEFAULT_HIGHLIGHT_RGB = 0xF5C518
        const val DEFAULT_HIGHLIGHT_OPACITY = 0.4f
        const val MIN_HIGHLIGHT_OPACITY = 0.2f
        const val MAX_HIGHLIGHT_OPACITY = 0.8f
    }
}
