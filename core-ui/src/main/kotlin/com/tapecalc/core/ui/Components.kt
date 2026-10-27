package com.tapecalc.core.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Small coloured label such as "STALE", "DEG" or a tag. */
@Composable
fun StatusChip(
    text: String,
    modifier: Modifier = Modifier,
    container: Color = MaterialTheme.colorScheme.secondaryContainer,
    content: Color = MaterialTheme.colorScheme.onSecondaryContainer
) {
    Surface(
        modifier = modifier.defaultMinSize(minHeight = 24.dp),
        shape = RoundedCornerShape(50),
        color = container,
        contentColor = content
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier.padding(top = 20.dp, bottom = 8.dp),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold
    )
}

/** Preview card for choosing a theme in settings. */
@Composable
fun ThemeSwatch(theme: ThemeDef, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val borderColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    Surface(
        modifier = modifier
            .width(104.dp)
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton),
        shape = RoundedCornerShape(16.dp),
        color = theme.background,
        border = BorderStroke(if (selected) 3.dp else 1.dp, borderColor)
    ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(theme.key, theme.keyAlt, theme.accent).forEach { color ->
                    Box(Modifier.size(18.dp).clip(CircleShape).background(color))
                }
            }
            Text(
                text = theme.name,
                color = theme.text,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2
            )
        }
    }
}

/** A round colour swatch used for the custom accent picker. */
@Composable
fun ColorDot(color: Color, selected: Boolean, onClick: () -> Unit, description: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(48.dp)
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .size(if (selected) 36.dp else 30.dp)
                .clip(CircleShape)
                .background(color)
        )
        if (selected) {
            Text("✓", color = if (color.red * 0.299f + color.green * 0.587f + color.blue * 0.114f > 0.6f) Color.Black else Color.White, fontWeight = FontWeight.Bold)
        }
    }
}
