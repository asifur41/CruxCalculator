package com.tapecalc.core.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

enum class KeypadStyle(val label: String) {
    FILLED("Filled"), TONAL("Soft"), OUTLINED("Outlined"), FLAT("Flat");

    companion object {
        fun fromName(name: String?) = entries.firstOrNull { it.name == name } ?: FILLED
    }
}

enum class ButtonShape(val label: String) {
    ROUNDED("Rounded"), PILL("Pill"), SQUARE("Square"), LEAF("Leaf");

    fun toShape(): Shape = when (this) {
        ROUNDED -> RoundedCornerShape(18.dp)
        PILL -> RoundedCornerShape(50)
        SQUARE -> RoundedCornerShape(6.dp)
        LEAF -> RoundedCornerShape(topStart = 26.dp, topEnd = 8.dp, bottomEnd = 26.dp, bottomStart = 8.dp)
    }

    companion object {
        fun fromName(name: String?) = entries.firstOrNull { it.name == name } ?: ROUNDED
    }
}

/** Everything the theme needs from the user's settings. */
data class ThemeSettings(
    val themeId: String = Themes.ClassicLight.id,
    val followSystem: Boolean = true,
    val customAccent: Long? = null,
    val keypadStyle: KeypadStyle = KeypadStyle.FILLED,
    val buttonShape: ButtonShape = ButtonShape.ROUNDED,
    val fontScale: Float = 1f
)

@Immutable
data class CalcColors(
    val accent: Color,
    val onAccent: Color,
    val digitKey: Color,
    val digitText: Color,
    val operatorKey: Color,
    val operatorText: Color,
    val functionKey: Color,
    val functionText: Color,
    val equalsKey: Color,
    val equalsText: Color,
    val muted: Color,
    val error: Color,
    val warning: Color
)

@Immutable
data class CalcLook(
    val colors: CalcColors,
    val keypadStyle: KeypadStyle,
    val buttonShape: ButtonShape,
    val fontScale: Float,
    val isDark: Boolean
)

private fun calcColors(theme: ThemeDef, accent: Color, onAccent: Color): CalcColors = CalcColors(
    accent = accent,
    onAccent = onAccent,
    digitKey = theme.key,
    digitText = theme.text,
    operatorKey = lerp(theme.keyAlt, accent, 0.28f),
    operatorText = theme.text,
    functionKey = theme.keyAlt,
    functionText = theme.text,
    equalsKey = accent,
    equalsText = onAccent,
    muted = theme.muted,
    error = if (theme.isDark) Color(0xFFFF8A80) else Color(0xFFB3261E),
    warning = if (theme.isDark) Color(0xFFFFCC80) else Color(0xFF8A5100)
)

private fun readableOn(color: Color): Color = if (color.luminance() > 0.45f) Color.Black else Color.White

/** Applies "follow system": a theme of the wrong brightness is swapped for Classic Light / Midnight. */
fun resolveTheme(settings: ThemeSettings, systemDark: Boolean): ThemeDef {
    val selected = Themes.byId(settings.themeId)
    if (selected.isDynamic) return selected.copy(isDark = systemDark)
    if (!settings.followSystem || selected.isDark == systemDark) return selected
    return if (systemDark) Themes.Midnight else Themes.ClassicLight
}

private fun schemeFrom(theme: ThemeDef, accent: Color, onAccent: Color): ColorScheme {
    val error = if (theme.isDark) Color(0xFFFF8A80) else Color(0xFFB3261E)
    return if (theme.isDark) {
        darkColorScheme(
            primary = accent, onPrimary = onAccent,
            primaryContainer = theme.keyAlt, onPrimaryContainer = theme.text,
            secondary = accent, onSecondary = onAccent,
            secondaryContainer = theme.keyAlt, onSecondaryContainer = theme.text,
            tertiary = accent, onTertiary = onAccent,
            background = theme.background, onBackground = theme.text,
            surface = theme.surface, onSurface = theme.text,
            surfaceVariant = theme.key, onSurfaceVariant = theme.muted,
            outline = theme.muted, outlineVariant = theme.keyAlt,
            error = error, onError = Color.Black
        )
    } else {
        lightColorScheme(
            primary = accent, onPrimary = onAccent,
            primaryContainer = theme.keyAlt, onPrimaryContainer = theme.text,
            secondary = accent, onSecondary = onAccent,
            secondaryContainer = theme.keyAlt, onSecondaryContainer = theme.text,
            tertiary = accent, onTertiary = onAccent,
            background = theme.background, onBackground = theme.text,
            surface = theme.surface, onSurface = theme.text,
            surfaceVariant = theme.key, onSurfaceVariant = theme.muted,
            outline = theme.muted, outlineVariant = theme.keyAlt,
            error = error, onError = Color.White
        )
    }
}

val LocalCalcLook = staticCompositionLocalOf {
    val t = Themes.ClassicLight
    CalcLook(calcColors(t, t.accent, t.onAccent), KeypadStyle.FILLED, ButtonShape.ROUNDED, 1f, false)
}

/** App-wide theme: Material 3 colours plus the calculator-specific [CalcLook]. */
@Composable
fun TapeTheme(
    settings: ThemeSettings,
    systemDark: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val theme = resolveTheme(settings, systemDark)
    val useDynamic = theme.isDynamic && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    val scheme: ColorScheme = if (useDynamic) {
        if (theme.isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        val accent = settings.customAccent?.let { Color(it) } ?: theme.accent
        val onAccent = if (settings.customAccent != null) readableOn(accent) else theme.onAccent
        schemeFrom(theme, accent, onAccent)
    }

    val look = remember(scheme, theme, settings.customAccent, settings.keypadStyle, settings.buttonShape, settings.fontScale) {
        val accent = if (useDynamic) scheme.primary else settings.customAccent?.let { Color(it) } ?: theme.accent
        val onAccent = if (useDynamic) scheme.onPrimary else if (settings.customAccent != null) readableOn(accent) else theme.onAccent
        val base = if (useDynamic) {
            theme.copy(
                background = scheme.background, surface = scheme.surface, text = scheme.onSurface,
                muted = scheme.onSurfaceVariant, key = scheme.surfaceVariant, keyAlt = scheme.secondaryContainer
            )
        } else theme
        CalcLook(calcColors(base, accent, onAccent), settings.keypadStyle, settings.buttonShape, settings.fontScale, theme.isDark)
    }

    CompositionLocalProvider(LocalCalcLook provides look) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
