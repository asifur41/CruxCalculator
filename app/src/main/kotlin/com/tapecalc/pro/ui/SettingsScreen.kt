package com.tapecalc.pro.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.unit.dp
import com.tapecalc.core.data.settings.AppSettings
import com.tapecalc.pro.AppInfo
import com.tapecalc.core.math.AngleMode
import com.tapecalc.core.math.NumberNotation
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
    onReplayTutorial: () -> Unit,
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
        Text("Number notation", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
        Text(
            "How the main result is written: Auto (12345), Scientific (1.2345E4) or Engineering (12.345E3). Display only; tape entries, copying and exports keep the normal form.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        ChipRow(NumberNotation.entries, settings.numberNotation, { it.name }, { it.label }) { v ->
            onChange { it.copy(numberNotation = v.name) }
        }
        if (settings.numberNotation == NumberNotation.FIXED.name) {
            Text("Fix: decimal places", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 4.dp))
            ChipRow((0..9).toList(), settings.fixDigits.toString(), { it.toString() }, { it.toString() }) { v ->
                onChange { it.copy(fixDigits = v) }
            }
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

        GroupTitle("Developer")
        InfoRow("Name", AppInfo.DEVELOPER_NAME)
        InfoRow("Email", AppInfo.DEVELOPER_EMAIL, onClick = { sendEmail(context) })
        InfoRow("Country", AppInfo.DEVELOPER_COUNTRY)
        LinkRow("Visit Developer Website") { openLink(context, AppInfo.DEVELOPER_WEBSITE) }

        GroupTitle("Support")
        LinkRow("Join Community", "Chat with other users on WhatsApp") {
            if (AppInfo.COMMUNITY_URL.isBlank()) {
                Toast.makeText(context, "The community link is coming soon", Toast.LENGTH_SHORT).show()
            } else {
                openLink(context, AppInfo.COMMUNITY_URL)
            }
        }
        LinkRow("Download the latest APK", "Direct download of the newest version") { openLink(context, AppInfo.LATEST_APK_URL) }
        LinkRow("Visit GitHub releases", "Installed: $versionName. See what is new.") { openLink(context, AppInfo.GITHUB_URL) }
        LinkRow("Leave a review", "Tell others what you think of the app") { openLink(context, AppInfo.REVIEW_URL) }
        LinkRow("Report a Bug") { openLink(context, AppInfo.BUG_REPORT_URL) }
        LinkRow("Donate") { openLink(context, AppInfo.DONATE_URL) }
        LinkRow("Replay tutorial", onClick = onReplayTutorial)
        InfoRow("Version", versionName)
        Text(
            "Crux Calculator. The private, ad-free calculator for people who actually calculate.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 12.dp)
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

private fun openLink(context: android.content.Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: Exception) {
        Toast.makeText(context, "No app available to open this link", Toast.LENGTH_SHORT).show()
    }
}

private fun sendEmail(context: android.content.Context) {
    val intent = Intent(Intent.ACTION_SENDTO).apply {
        data = Uri.parse("mailto:")
        putExtra(Intent.EXTRA_EMAIL, arrayOf(AppInfo.DEVELOPER_EMAIL))
        putExtra(Intent.EXTRA_SUBJECT, AppInfo.EMAIL_SUBJECT)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    try {
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(context, "No email app available", Toast.LENGTH_SHORT).show()
    }
}

/** Small upper-case group heading (DEVELOPER, SUPPORT). */
@Composable
private fun GroupTitle(text: String) {
    Text(
        text = text.uppercase(),
        modifier = Modifier.padding(top = 24.dp, bottom = 4.dp),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold
    )
}

/** Label on the left, value on the right. Tappable when [onClick] is given. */
@Composable
private fun InfoRow(label: String, value: String, onClick: (() -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClickLabel = "Open $label", onClick = onClick) else Modifier)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
    }
}

/** Title (and optional subtitle) on the left, a chevron on the right. */
@Composable
private fun LinkRow(title: String, subtitle: String? = null, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
    }
}
