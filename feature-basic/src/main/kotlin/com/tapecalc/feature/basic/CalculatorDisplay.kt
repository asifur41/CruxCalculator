package com.tapecalc.feature.basic

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tapecalc.core.math.editor.Mk
import com.tapecalc.core.ui.KeyAction
import com.tapecalc.core.ui.LocalCalcLook
import com.tapecalc.core.ui.StatusChip

data class MiniTapeLine(val expression: String, val result: String)

/** Everything the display needs to render; produced by the app's view model. */
data class DisplayState(
    val expression: String = "",
    /** Set after "=": the result as shown (decimal or fraction view). */
    val result: String? = null,
    val resultExpression: String? = null,
    /** Live "= 12.5" preview while typing. */
    val preview: String? = null,
    val error: String? = null,
    val warning: String? = null,
    /** Extra line under a result, for example "0.(3)" or "1 3/4". */
    val secondaryResult: String? = null,
    val angleLabel: String = "DEG",
    val workspaceName: String = "",
    val canToggleFraction: Boolean = false,
    /** Cursor position in [expression]; -1 means at the end. */
    val cursor: Int = -1,
    val shift: Boolean = false,
    val alpha: Boolean = false,
    val hyp: Boolean = false,
    /** "STO: press a letter" while waiting for the letter after STO / RCL. */
    val pendingLabel: String? = null
)

class DisplayActions(
    val onWorkspaceClick: () -> Unit,
    val onTypeExpression: () -> Unit,
    val onCopyResult: () -> Unit,
    val onCopyExpression: () -> Unit,
    val onPaste: () -> Unit,
    val onOpenTape: () -> Unit,
    val onSettings: () -> Unit,
    val onAction: (KeyAction) -> Unit
)

/**
 * Font size that makes a line of [length] characters fit the width [availableDp] on one line, shrinking down to
 * [minSp]; below that the line wraps instead. Used by the scientific screen, where lines get long.
 */
internal fun fitFontSize(availableDp: Float, length: Int, maxSp: Float, minSp: Float): Float {
    if (length <= 0) return maxSp
    // an average digit is about 0.56 of the font size wide
    val fits = availableDp / (length * 0.56f)
    return fits.coerceIn(minSp, maxSp)
}

private fun expressionFontSize(length: Int): Int = when {
    length <= 9 -> 56
    length <= 14 -> 44
    length <= 22 -> 34
    else -> 26
}

@Composable
fun CalculatorDisplay(
    state: DisplayState,
    miniTape: List<MiniTapeLine>,
    actions: DisplayActions,
    modifier: Modifier = Modifier,
    /** How many earlier calculations to show above the current line. */
    maxTapeLines: Int = 2,
    /** Scientific screen: shrink long lines to fit, always show the cursor, tapping an earlier line edits it. */
    scientific: Boolean = false
) {
    val look = LocalCalcLook.current
    val scale = look.fontScale

    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        DisplayHeader(state, actions)

        BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
            val widthDp = maxWidth.value
            val displayMaxHeight = maxHeight
            if (scientific) {
                // Two zones. The earlier calculations start right under the top buttons; the line being worked on sits
                // at the bottom and always has room for at least two lines of the large text.
                val workMin = (44f * scale * 2.4f).dp
                Column(Modifier.fillMaxSize()) {
                    BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().clipToBounds()) {
                        val lineHeightDp = 21f * scale
                        val fit = (maxHeight.value / lineHeightDp).toInt().coerceAtLeast(0)
                        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
                            HistoryLines(miniTape.takeLast(minOf(maxTapeLines, fit)), true, actions)
                        }
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = workMin, max = displayMaxHeight * 0.8f)
                            .verticalScroll(rememberScrollState(), reverseScrolling = true),
                        contentAlignment = Alignment.BottomEnd
                    ) {
                        WorkingArea(state, true, widthDp, actions)
                    }
                }
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomEnd) {
                    Column(
                        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState(), reverseScrolling = true),
                        horizontalAlignment = Alignment.End
                    ) {
                        HistoryLines(miniTape.takeLast(maxTapeLines), false, actions)
                        Spacer(Modifier.height(8.dp))
                        WorkingArea(state, false, widthDp, actions)
                    }
                }
            }
        }
    }
}

/** The earlier calculations, oldest first, as small muted lines. */
@Composable
private fun HistoryLines(lines: List<MiniTapeLine>, scientific: Boolean, actions: DisplayActions) {
    val look = LocalCalcLook.current
    val scale = look.fontScale
    val muted = look.colors.muted
    lines.forEach { line ->
        Text(
            text = "${Mk.stripMarkers(line.expression)} = ${line.result}",
            color = muted,
            fontSize = 14.sp * scale,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .clickable(
                    onClickLabel = if (scientific) "Edit this line" else "Open tape",
                    onClick = if (scientific) { { actions.onAction(KeyAction.LoadExpression(line.expression)) } } else actions.onOpenTape
                )
                .padding(vertical = 2.dp)
        )
    }
}

/** The line being typed, or the result just calculated, with its preview, error and warnings. */
@Composable
private fun WorkingArea(state: DisplayState, scientific: Boolean, widthDp: Float, actions: DisplayActions) {
    val look = LocalCalcLook.current
    val scale = look.fontScale
    val onBg = MaterialTheme.colorScheme.onBackground
    val muted = look.colors.muted

    Column(
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        horizontalAlignment = Alignment.End
    ) {
        if (state.result != null) {
            if (!state.resultExpression.isNullOrBlank()) {
                MathText(
                    text = state.resultExpression,
                    cursor = null,
                    fontSize = (if (scientific) fitFontSize(widthDp, Mk.stripMarkers(state.resultExpression).length, 22f, 15f) else 20f).sp * scale,
                    color = muted,
                    weight = FontWeight.Normal
                )
            }
            Text(
                text = state.result,
                color = onBg,
                textAlign = TextAlign.End,
                style = TextStyle(
                    fontSize = (if (scientific) fitFontSize(widthDp, state.result.length, 48f, 22f) else expressionFontSize(state.result.length).toFloat()).sp * scale,
                    fontWeight = FontWeight.SemiBold,
                    fontFeatureSettings = "tnum"
                ),
                modifier = if (state.canToggleFraction) {
                    Modifier.clickable(role = Role.Button, onClickLabel = "Switch between fraction and decimal") {
                        actions.onAction(KeyAction.ToggleFraction)
                    }.defaultMinSize(minHeight = 48.dp)
                } else Modifier
            )
            if (state.secondaryResult != null) {
                Text(
                    text = state.secondaryResult,
                    color = muted,
                    fontSize = 16.sp * scale,
                    textAlign = TextAlign.End
                )
            }
        } else {
            val shownLength = Mk.stripMarkers(state.expression).length
            val fontSp = (if (scientific) fitFontSize(widthDp, shownLength, 44f, 20f) else expressionFontSize(shownLength).toFloat()).sp * scale
            val cursor = if (state.cursor < 0 || state.cursor > state.expression.length) state.expression.length else state.cursor
            // the basic screen only shows the cursor when it is somewhere other than the end of the line
            val showCursor = scientific || cursor < state.expression.length
            if (state.expression.isEmpty() && !showCursor) {
                Text(text = "0", color = muted, textAlign = TextAlign.End, fontSize = fontSp, fontWeight = FontWeight.Medium)
            } else {
                MathText(
                    text = state.expression,
                    cursor = if (showCursor) cursor else null,
                    fontSize = fontSp,
                    color = onBg
                )
            }
            if (state.error != null) {
                Text(
                    text = state.error,
                    color = look.colors.error,
                    fontSize = 18.sp * scale,
                    textAlign = TextAlign.End
                )
            } else if (state.preview != null) {
                Text(
                    text = state.preview,
                    color = muted,
                    fontSize = 24.sp * scale,
                    textAlign = TextAlign.End,
                    style = TextStyle(fontFeatureSettings = "tnum")
                )
            }
        }
        if (state.pendingLabel != null) {
            Text(
                text = state.pendingLabel,
                color = MaterialTheme.colorScheme.tertiary,
                fontSize = 13.sp * scale,
                textAlign = TextAlign.End
            )
        }
        if (state.warning != null) {
            Text(
                text = state.warning,
                color = look.colors.warning,
                fontSize = 12.sp * scale,
                textAlign = TextAlign.End,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
private fun DisplayHeader(state: DisplayState, actions: DisplayActions) {
    val look = LocalCalcLook.current
    var copyMenu by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Box(
            modifier = Modifier
                .defaultMinSize(minHeight = 48.dp, minWidth = 48.dp)
                .clickable(role = Role.Button, onClickLabel = "Change workspace", onClick = actions.onWorkspaceClick),
            contentAlignment = Alignment.Center
        ) {
            StatusChip(
                text = state.workspaceName.ifEmpty { "Tape" },
                container = MaterialTheme.colorScheme.surfaceVariant,
                content = MaterialTheme.colorScheme.onSurface
            )
        }
        Box(
            modifier = Modifier
                .defaultMinSize(minHeight = 48.dp, minWidth = 48.dp)
                .clickable(role = Role.Button, onClickLabel = "Change angle mode") { actions.onAction(KeyAction.CycleAngle) },
            contentAlignment = Alignment.Center
        ) {
            StatusChip(
                text = state.angleLabel,
                container = MaterialTheme.colorScheme.primary,
                content = MaterialTheme.colorScheme.onPrimary
            )
        }
        if (state.shift) StatusChip("SHIFT", container = look.colors.accent, content = look.colors.onAccent)
        if (state.alpha) StatusChip("ALPHA", container = MaterialTheme.colorScheme.tertiary, content = MaterialTheme.colorScheme.onTertiary)
        if (state.hyp) StatusChip("hyp", container = MaterialTheme.colorScheme.secondary, content = MaterialTheme.colorScheme.onSecondary)
        Spacer(Modifier.weight(1f))
        IconButton(onClick = actions.onTypeExpression) {
            Icon(Icons.Outlined.Keyboard, contentDescription = "Type an expression, for example 12 km to mi")
        }
        Box {
            IconButton(onClick = { copyMenu = true }) {
                Icon(Icons.Outlined.ContentCopy, contentDescription = "Copy")
            }
            DropdownMenu(expanded = copyMenu, onDismissRequest = { copyMenu = false }) {
                DropdownMenuItem(text = { Text("Copy result") }, onClick = { copyMenu = false; actions.onCopyResult() })
                DropdownMenuItem(text = { Text("Copy expression") }, onClick = { copyMenu = false; actions.onCopyExpression() })
            }
        }
        IconButton(onClick = actions.onPaste) {
            Icon(Icons.Outlined.ContentPaste, contentDescription = "Smart paste from clipboard")
        }
        IconButton(onClick = actions.onSettings) {
            Icon(Icons.Outlined.Settings, contentDescription = "Settings")
        }
    }
}

/**
 * Basic calculator screen: display on top, keypad below (side by side in landscape).
 */
@Composable
fun BasicScreen(
    state: DisplayState,
    miniTape: List<MiniTapeLine>,
    actions: DisplayActions,
    leftHanded: Boolean,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        if (maxWidth > maxHeight) {
            Row(Modifier.fillMaxSize()) {
                CalculatorDisplay(state, miniTape, actions, Modifier.weight(1f))
                BasicKeypad(actions.onAction, Modifier.weight(1f), leftHanded)
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                CalculatorDisplay(state, miniTape, actions, Modifier.weight(0.85f))
                BasicKeypad(actions.onAction, Modifier.weight(1.45f), leftHanded)
            }
        }
    }
}
