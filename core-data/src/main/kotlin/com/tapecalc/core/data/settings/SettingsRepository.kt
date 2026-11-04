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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.runBlocking

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
    /** AUTO, SCIENTIFIC or ENGINEERING (see NumberNotation in core-math). Display only. */
    val numberNotation: String = "AUTO",
    /** Decimal places used by the Fix notation (0 to 9). */
    val fixDigits: Int = 2,
    val workspaceId: Long = 0,
    /** True once the first-run tutorial has been finished or skipped. */
    val onboardingDone: Boolean = false
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
        val NOTATION = stringPreferencesKey("number_notation")
        val FIX_DIGITS = intPreferencesKey("fix_digits")
        val WORKSPACE = longPreferencesKey("workspace_id")
        val ONBOARDING = booleanPreferencesKey("onboarding_done")
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
            numberNotation = this[Keys.NOTATION] ?: d.numberNotation,
            fixDigits = (this[Keys.FIX_DIGITS] ?: d.fixDigits).coerceIn(0, 9),
            workspaceId = this[Keys.WORKSPACE] ?: d.workspaceId,
            onboardingDone = this[Keys.ONBOARDING] ?: d.onboardingDone
        )
    }

    val settings: Flow<AppSettings> = context.settingsDataStore.data
        .catch { emit(androidx.datastore.preferences.core.emptyPreferences()) }
        .map { it.toSettings() }
        .distinctUntilChanged()
        .onEach { saveBootSnapshot(it) }

    // ------------------------------------------------------------------ instant start-up copy
    //
    // DataStore loads asynchronously, so the first frame used to be drawn with the default theme
    // and then repainted with the saved one (a visible flash). A tiny SharedPreferences copy of the
    // settings is read synchronously (a few milliseconds) so the very first frame is already right.
    // DataStore stays the single source of truth; this copy is rewritten on every change.

    private val bootPrefs by lazy { context.getSharedPreferences("tape_boot_settings", Context.MODE_PRIVATE) }

    private fun saveBootSnapshot(s: AppSettings) {
        try {
            bootPrefs.edit().apply {
                putBoolean("valid", true)
                putString("theme", s.themeId)
                putBoolean("follow_system", s.followSystem)
                if (s.customAccent != null) putLong("accent", s.customAccent) else remove("accent")
                putString("keypad_style", s.keypadStyle)
                putString("button_shape", s.buttonShape)
                putFloat("font_scale", s.fontScale)
                putInt("haptic", s.hapticLevel)
                putBoolean("sound", s.soundEnabled)
                putBoolean("left_handed", s.leftHanded)
                putString("angle", s.angleMode)
                putBoolean("auto_recalc", s.autoRecalc)
                putBoolean("group", s.groupDigits)
                putBoolean("fraction", s.fractionByDefault)
                putString("notation", s.numberNotation)
                putInt("fix_digits", s.fixDigits)
                putLong("workspace", s.workspaceId)
                putBoolean("onboarding", s.onboardingDone)
            }.apply()
        } catch (e: Exception) {
            // The copy is only a start-up optimisation; never let it break the app.
        }
    }

    private fun readBootSnapshot(): AppSettings? {
        return try {
            val p = bootPrefs
            if (!p.getBoolean("valid", false)) return null
            val d = AppSettings()
            AppSettings(
                themeId = p.getString("theme", null) ?: d.themeId,
                followSystem = p.getBoolean("follow_system", d.followSystem),
                customAccent = if (p.contains("accent")) p.getLong("accent", 0L) else null,
                keypadStyle = p.getString("keypad_style", null) ?: d.keypadStyle,
                buttonShape = p.getString("button_shape", null) ?: d.buttonShape,
                fontScale = p.getFloat("font_scale", d.fontScale),
                hapticLevel = p.getInt("haptic", d.hapticLevel),
                soundEnabled = p.getBoolean("sound", d.soundEnabled),
                leftHanded = p.getBoolean("left_handed", d.leftHanded),
                angleMode = p.getString("angle", null) ?: d.angleMode,
                autoRecalc = p.getBoolean("auto_recalc", d.autoRecalc),
                groupDigits = p.getBoolean("group", d.groupDigits),
                fractionByDefault = p.getBoolean("fraction", d.fractionByDefault),
                numberNotation = p.getString("notation", null) ?: d.numberNotation,
                fixDigits = p.getInt("fix_digits", d.fixDigits).coerceIn(0, 9),
                workspaceId = p.getLong("workspace", d.workspaceId),
                onboardingDone = p.getBoolean("onboarding", d.onboardingDone)
            )
        } catch (e: Exception) {
            null
        }
    }

    /**
     * The settings to draw the first frame with. Fast path: the SharedPreferences copy. Only on the
     * very first launch after installing or updating (no copy yet) is DataStore read once, blocking.
     */
    fun initialSettings(): AppSettings {
        readBootSnapshot()?.let { return it }
        return try {
            runBlocking { settings.first() }
        } catch (e: Exception) {
            AppSettings()
        }
    }

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
            prefs[Keys.NOTATION] = s.numberNotation
            prefs[Keys.FIX_DIGITS] = s.fixDigits.coerceIn(0, 9)
            prefs[Keys.WORKSPACE] = s.workspaceId
            prefs[Keys.ONBOARDING] = s.onboardingDone
        }
    }

    suspend fun current(): AppSettings = settings.first()
}
