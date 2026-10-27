package com.tapecalc.core.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * All user preferences. Enum-like options are stored as plain strings so this module stays
 * independent of the UI modules.
 */
data class AppSettings(
    val themeId: String = "classic_light",
    /** Swap to a light/dark theme automatically when the system switches (default on). */
    val followSystem: Boolean = true,
    /** ARGB colour chosen by the user, or null to use the theme accent. */
    val customAccent: Long? = null,
    val keypadStyle: String = "FILLED",
    val buttonShape: String = "ROUNDED",
    val fontScale: Float = 1f,
    /** 0 = off, 1 = light, 2 = medium, 3 = strong. */
    val hapticLevel: Int = 2,
    val soundEnabled: Boolean = false,
    val leftHanded: Boolean = false,
    val angleMode: String = "DEG",
    val autoRecalc: Boolean = true,
    val groupDigits: Boolean = false,
    val fractionByDefault: Boolean = false,
    val workspaceId: Long = 0
)

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "tape_settings")

class SettingsRepository(private val context: Context) {

    private object Keys {
        val THEME = stringPreferencesKey("theme")
        val FOLLOW_SYSTEM = booleanPreferencesKey("follow_system")
        val ACCENT = longPreferencesKey("accent")
        val KEYPAD_STYLE = stringPreferencesKey("keypad_style")
        val BUTTON_SHAPE = stringPreferencesKey("button_shape")
        val FONT_SCALE = floatPreferencesKey("font_scale")
        val HAPTIC = intPreferencesKey("haptic_level")
        val SOUND = booleanPreferencesKey("sound")
        val LEFT_HANDED = booleanPreferencesKey("left_handed")
        val ANGLE = stringPreferencesKey("angle_mode")
        val AUTO_RECALC = booleanPreferencesKey("auto_recalc")
        val GROUP = booleanPreferencesKey("group_digits")
        val FRACTION = booleanPreferencesKey("fraction_default")
        val WORKSPACE = longPreferencesKey("workspace_id")
    }

    private fun Preferences.toSettings(): AppSettings {
        val d = AppSettings()
        return AppSettings(
            themeId = this[Keys.THEME] ?: d.themeId,
            followSystem = this[Keys.FOLLOW_SYSTEM] ?: d.followSystem,
            customAccent = this[Keys.ACCENT],
            keypadStyle = this[Keys.KEYPAD_STYLE] ?: d.keypadStyle,
            buttonShape = this[Keys.BUTTON_SHAPE] ?: d.buttonShape,
            fontScale = this[Keys.FONT_SCALE] ?: d.fontScale,
            hapticLevel = this[Keys.HAPTIC] ?: d.hapticLevel,
            soundEnabled = this[Keys.SOUND] ?: d.soundEnabled,
            leftHanded = this[Keys.LEFT_HANDED] ?: d.leftHanded,
            angleMode = this[Keys.ANGLE] ?: d.angleMode,
            autoRecalc = this[Keys.AUTO_RECALC] ?: d.autoRecalc,
            groupDigits = this[Keys.GROUP] ?: d.groupDigits,
            fractionByDefault = this[Keys.FRACTION] ?: d.fractionByDefault,
            workspaceId = this[Keys.WORKSPACE] ?: d.workspaceId
        )
    }

    val settings: Flow<AppSettings> = context.settingsDataStore.data
        .catch { emit(androidx.datastore.preferences.core.emptyPreferences()) }
        .map { it.toSettings() }

    /** Reads the current settings, applies [transform] and writes everything back. */
    suspend fun update(transform: (AppSettings) -> AppSettings) {
        context.settingsDataStore.edit { prefs ->
            val s = transform(prefs.toSettings())
            prefs[Keys.THEME] = s.themeId
            prefs[Keys.FOLLOW_SYSTEM] = s.followSystem
            if (s.customAccent != null) prefs[Keys.ACCENT] = s.customAccent else prefs.remove(Keys.ACCENT)
            prefs[Keys.KEYPAD_STYLE] = s.keypadStyle
            prefs[Keys.BUTTON_SHAPE] = s.buttonShape
            prefs[Keys.FONT_SCALE] = s.fontScale.coerceIn(0.8f, 1.4f)
            prefs[Keys.HAPTIC] = s.hapticLevel.coerceIn(0, 3)
            prefs[Keys.SOUND] = s.soundEnabled
            prefs[Keys.LEFT_HANDED] = s.leftHanded
            prefs[Keys.ANGLE] = s.angleMode
            prefs[Keys.AUTO_RECALC] = s.autoRecalc
            prefs[Keys.GROUP] = s.groupDigits
            prefs[Keys.FRACTION] = s.fractionByDefault
            prefs[Keys.WORKSPACE] = s.workspaceId
        }
    }

    suspend fun current(): AppSettings = settings.first()
}
