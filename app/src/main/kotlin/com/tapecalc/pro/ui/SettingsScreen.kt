package com.tapecalc.pro.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.unit.dp
import com.tapecalc.core.data.settings.AppSettings
import com.tapecalc.core.math.AngleMode
import com.tapecalc.core.ui.ButtonShape
import com.tapecalc.core.ui.ColorDot
import com.tapecalc.core.ui.KeypadStyle
import com.tapecalc.core.ui.SectionTitle
import com.tapecalc.core.ui.ThemeSwatch
import com.tapecalc.core.ui.Themes

private val HAPTIC_LABELS = listOf("Off", "Light", "Medium", "Strong")

@Composable
fun SettingsScreen(
    settings: AppSettings,
    onChange: ((AppSettings) -> AppSettings) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val versionName = remember(context) {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        SectionTitle("Theme")
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(Themes.all, key = { it.id }) { theme ->
                ThemeSwatch(
                    theme = theme,
                    selected = theme.id == settings.themeId,
                    onClick = { onChange { it.copy(themeId = theme.id) } }
                )
            }
        }
        SwitchRow(
            title = "Follow system light / dark",
            subtitle = "Switches to Classic Light or Midnight when the phone changes mode. Material You always follows the system.",
            checked = settings.followSystem,
            onCheckedChange = { v -> onChange { it.copy(followSystem = v) } }
        )

        SectionTitle("Accent colour")
        LazyRow(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
            item {
                FilterChip(
                    selected = settings.customAccent == null,
                    onClick = { onChange { it.copy(customAccent = null) } },
                    label = { Text("Theme default") },
                    modifier = Modifier.padding(end = 8.dp)
                )
            }
            items(Themes.accentPresets) { argb ->
                ColorDot(
                    color = Color(argb),
                    selected = settings.customAccent == argb,
                    onClick = { onChange { it.copy(customAccent = argb) } },
                    description = "Accent colour ${java.lang.Long.toHexString(argb and 0xFFFFFF).uppercase().padStart(6, '0')}"
                )
            }
        }

        SectionTitle("Keypad")
        Text("Key style", style = MaterialTheme.typography.labelLarge)
        ChipRow(KeypadStyle.entries, settings.keypadStyle, { it.name }, { it.label }) { v ->
            onChange { it.copy(keypadStyle = v.name) }
        }
        Text("Button shape", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
        ChipRow(ButtonShape.entries, settings.buttonShape, { it.name }, { it.label }) { v ->
            onChange { it.copy(buttonShape = v.name) }
        }
        SwitchRow(
            title = "Left-handed layout",
            subtitle = "Moves the operator column and the = key to the left edge.",
            checked = settings.leftHanded,
            onCheckedChange = { v -> onChange { it.copy(leftHanded = v) } }
        )
        Text(
            "Text size: ${(settings.fontScale * 100).toInt()}%",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(top = 8.dp)
        )
        Slider(
            value = settings.fontScale,
            onValueChange = { v -> onChange { it.copy(fontScale = (Math.round(v * 20f) / 20f)) } },
            valueRange = 0.8f..1.4f,
            modifier = Modifier.semantics { stateDescription = "${(settings.fontScale * 100).toInt()} percent" }
        )

        SectionTitle("Feedback")
        Text("Haptic strength: ${HAPTIC_LABELS[settings.hapticLevel.coerceIn(0, 3)]}", style = MaterialTheme.typography.labelLarge)
        Slider(
            value = settings.hapticLevel.toFloat(),
            onValueChange = { v -> onChange { it.copy(hapticLevel = v.toInt().coerceIn(0, 3)) } },
            valueRange = 0f..3f,
            steps = 2,
            modifier = Modifier.semantics { stateDescription = HAPTIC_LABELS[settings.hapticLevel.coerceIn(0, 3)] }
        )
        SwitchRow(
            title = "Key click sound",
            subtitle = "Uses the system touch sound. Follows your phone's volume and touch-sound settings.",
            checked = settings.soundEnabled,
            onCheckedChange = { v -> onChange { it.copy(soundEnabled = v) } }
        )

        SectionTitle("Calculation")
        Text("Angle unit", style = MaterialTheme.typography.labelLarge)
        ChipRow(AngleMode.entries, settings.angleMode, { it.name }, { it.label }) { v ->
            onChange { it.copy(angleMode = v.name) }
        }
        SwitchRow(
            title = "Update later results automatically",
            subtitle = "When you edit an earlier tape entry, entries that use ans or its variable are recalculated. If off, they are marked stale.",
            checked = settings.autoRecalc,
            onCheckedChange = { v -> onChange { it.copy(autoRecalc = v) } }
        )
        SwitchRow(
            title = "Show fractions by default",
            subtitle = "Exact results such as 1/3 appear as fractions. Tap a result to switch.",
            checked = settings.fractionByDefault,
            onCheckedChange = { v -> onChange { it.copy(fractionByDefault = v) } }
        )
        SwitchRow(
            title = "Group digits (1,234,567)",
            subtitle = "Display only. Copied and exported values are never grouped.",
            checked = settings.groupDigits,
            onCheckedChange = { v -> onChange { it.copy(groupDigits = v) } }
        )

        SectionTitle("Privacy")
        Text(
            "No ads. No subscription. No tracking. No account. Works fully offline.\n" +
                "This app has no internet permission, so your calculations, notes and variables cannot leave this device unless you export or share them yourself.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        SectionTitle("About")
        Text(
            "CruxCalculator $versionName\nThe private, ad-free calculator for people who actually calculate.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun <T> ChipRow(items: List<T>, selectedKey: String, key: (T) -> String, label: (T) -> String, onSelect: (T) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(items) { item ->
            FilterChip(
                selected = key(item) == selectedKey,
                onClick = { onSelect(item) },
                label = { Text(label(item)) }
            )
        }
    }
}
