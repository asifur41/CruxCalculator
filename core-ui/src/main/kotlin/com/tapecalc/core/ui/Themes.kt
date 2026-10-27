package com.tapecalc.core.ui

import androidx.compose.ui.graphics.Color

/**
 * A built-in colour theme. [key] is the colour of digit keys, [keyAlt] of function keys.
 * The Material You theme uses these values only as a fallback on Android < 12.
 */
data class ThemeDef(
    val id: String,
    val name: String,
    val isDark: Boolean,
    val background: Color,
    val surface: Color,
    val text: Color,
    val muted: Color,
    val accent: Color,
    val onAccent: Color,
    val key: Color,
    val keyAlt: Color,
    val isDynamic: Boolean = false
)

private fun c(rgb: Long): Color = Color(0xFF000000L or rgb)

private fun theme(
    id: String, name: String, dark: Boolean,
    bg: Long, surface: Long, text: Long, muted: Long, accent: Long, onAccent: Long, key: Long, keyAlt: Long,
    dynamic: Boolean = false
) = ThemeDef(id, name, dark, c(bg), c(surface), c(text), c(muted), c(accent), c(onAccent), c(key), c(keyAlt), dynamic)

object Themes {
    val ClassicLight = theme("classic_light", "Classic Light", false, 0xF6F7F9, 0xFFFFFF, 0x14171C, 0x5F6672, 0x3B5BDB, 0xFFFFFF, 0xEDEFF3, 0xDDE2EA)
    val Midnight = theme("midnight", "Midnight", true, 0x0B1020, 0x141B30, 0xE8ECF8, 0x9AA6C4, 0x7C9CFF, 0x0B1020, 0x1B2440, 0x26325A)
    val AmoledBlack = theme("amoled_black", "AMOLED Black", true, 0x000000, 0x0A0A0A, 0xFFFFFF, 0xA3A3A3, 0x00E5C3, 0x000000, 0x141414, 0x222222)
    val Nord = theme("nord", "Nord", true, 0x2E3440, 0x3B4252, 0xECEFF4, 0xA9B4C6, 0x88C0D0, 0x2E3440, 0x434C5E, 0x4C566A)
    val Solarized = theme("solarized", "Solarized", false, 0xFDF6E3, 0xEEE8D5, 0x073642, 0x586E75, 0x268BD2, 0xFDF6E3, 0xEEE8D5, 0xE2DAC0)
    val Dracula = theme("dracula", "Dracula", true, 0x282A36, 0x343746, 0xF8F8F2, 0xA9B0D3, 0xBD93F9, 0x282A36, 0x44475A, 0x565A74)
    val MaterialYou = theme("dynamic", "Material You", false, 0xF4FBFA, 0xFFFFFF, 0x161D1D, 0x4A6363, 0x006A6A, 0xFFFFFF, 0xE3EFEE, 0xCFE5E4, dynamic = true)
    val WarmPaper = theme("warm_paper", "Warm Paper", false, 0xF4ECD8, 0xFBF6E9, 0x3B2F20, 0x6B5B44, 0xB5532F, 0xFFFFFF, 0xEADFC4, 0xDCCFAF)
    val Ocean = theme("ocean", "Ocean", true, 0x06202B, 0x0B2F3F, 0xE3F4FA, 0x8FBBC9, 0x2EC4D6, 0x06202B, 0x0F3A4D, 0x164B63)
    val Forest = theme("forest", "Forest", true, 0x0F1F17, 0x17301F, 0xE6F2E8, 0x93B99E, 0x5FD38D, 0x0F1F17, 0x1F3D2A, 0x2A5239)
    val Sunset = theme("sunset", "Sunset", false, 0xFFF1E8, 0xFFFFFF, 0x3A1F1A, 0x86554A, 0xD6401C, 0xFFFFFF, 0xFFE0CF, 0xFFCDB3)
    val MonoPro = theme("mono_pro", "Mono Pro", false, 0xFFFFFF, 0xF2F2F2, 0x000000, 0x595959, 0x000000, 0xFFFFFF, 0xF2F2F2, 0xDEDEDE)

    val all: List<ThemeDef> = listOf(
        ClassicLight, Midnight, AmoledBlack, Nord, Solarized, Dracula,
        MaterialYou, WarmPaper, Ocean, Forest, Sunset, MonoPro
    )

    fun byId(id: String): ThemeDef = all.firstOrNull { it.id == id } ?: ClassicLight

    /** Preset accent colours offered in settings (ARGB). */
    val accentPresets: List<Long> = listOf(
        0xFF3B5BDB, 0xFF7C4DFF, 0xFFD6336C, 0xFFE8590C, 0xFF2F9E44, 0xFF0C8599, 0xFFF59F00, 0xFF495057
    )
}
