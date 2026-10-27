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
    val canToggleFraction: Boolean = false
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
    modifier: Modifier = Modifier
) {
    val look = LocalCalcLook.current
    val scale = look.fontScale
    val onBg = MaterialTheme.colorScheme.onBackground
    val muted = look.colors.muted

    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        DisplayHeader(state, actions)

        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.BottomEnd) {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState(), reverseScrolling = true),
                horizontalAlignment = Alignment.End
            ) {
                miniTape.takeLast(2).forEach { line ->
                    Text(
                        text = "${line.expression} = ${line.result}",
                        color = muted,
                        fontSize = 14.sp * scale,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .clickable(onClickLabel = "Open tape", onClick = actions.onOpenTape)
                            .padding(vertical = 2.dp)
                    )
                }
                Spacer(Modifier.height(8.dp))

                Column(
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    horizontalAlignment = Alignment.End
                ) {
                    if (state.result != null) {
                        if (!state.resultExpression.isNullOrBlank()) {
                            Text(
                                text = state.resultExpression,
                                color = muted,
                                fontSize = 20.sp * scale,
                                textAlign = TextAlign.End
                            )
                        }
                        Text(
                            text = state.result,
                            color = onBg,
                            textAlign = TextAlign.End,
                            style = TextStyle(
                                fontSize = expressionFontSize(state.result.length).sp * scale,
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
                        Text(
                            text = state.expression.ifEmpty { "0" },
                            color = if (state.expression.isEmpty()) muted else onBg,
                            textAlign = TextAlign.End,
                            style = TextStyle(
                                fontSize = expressionFontSize(state.expression.length).sp * scale,
                                fontWeight = FontWeight.Medium,
                                fontFeatureSettings = "tnum"
                            )
                        )
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
        }
    }
}

@Composable
private fun DisplayHeader(state: DisplayState, actions: DisplayActions) {
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
