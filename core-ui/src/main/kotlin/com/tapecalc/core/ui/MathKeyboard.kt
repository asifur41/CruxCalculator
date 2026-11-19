package com.tapecalc.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The optional built-in keyboard. [enabled] follows the switch in Settings (off by default), and [onKeyPress] gives the
 * app a place to play its key haptics and sound. With the default value nothing changes anywhere.
 */
class MathKeyboardConfig(val enabled: Boolean = false, val onKeyPress: () -> Unit = {})

val LocalMathKeyboard = staticCompositionLocalOf { MathKeyboardConfig() }

private val KeyHeight = 48.dp
private val KeyGap = 4.dp

/**
 * The keyboard panel. It only draws keys and reports presses: pages and SHIFT are handled here, everything that changes
 * the text goes to [onAction] (see [ExpressionField], which edits the box).
 */
@Composable
fun MathKeyboard(
    kind: MathKeyboardKind,
    multiline: Boolean,
    onAction: (KbAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val config = LocalMathKeyboard.current
    val pages = remember(kind) { MathKeyboardLogic.pages(kind) }
    var page by remember(kind) { mutableStateOf(MathKeyboardLogic.defaultPage(kind)) }
    var shift by remember { mutableStateOf(ShiftState.OFF) }

    val press: (KbKey) -> Unit = { key ->
        config.onKeyPress()
        when (val action = key.action) {
            is KbAction.GoTo -> page = action.page
            KbAction.Shift -> shift = shift.next()
            KbAction.None -> Unit
            else -> {
                onAction(action)
                shift = MathKeyboardLogic.nextShift(shift, action, page)
            }
        }
    }

    BoxWithConstraints(modifier.fillMaxWidth()) {
        // dialogs on small phones are narrow: ten letter keys across would be too small to hit
        val compact = maxWidth < 340.dp
        val rows = remember(page, kind, shift, multiline, compact) {
            MathKeyboardLogic.rows(page, kind, shift, multiline, compact)
        }
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(6.dp), verticalArrangement = Arrangement.spacedBy(KeyGap)) {
                // top strip: the pages, and the keys that stay on every page
                val strip = pages.map { p ->
                    KbKey(p.title, KbAction.GoTo(p), p.description, KbStyle.ACTION, weight = 1.2f, active = p == page)
                } + MathKeyboardLogic.stripKeys.map { it.copy(weight = 0.8f) }
                KeyRowView(strip, KeyHeight - 6.dp, press)
                rows.forEach { row -> KeyRowView(row.keys, KeyHeight * row.height, press) }
            }
        }
    }
}

@Composable
private fun KeyRowView(keys: List<KbKey>, height: Dp, onPress: (KbKey) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().height(height),
        horizontalArrangement = Arrangement.spacedBy(KeyGap)
    ) {
        keys.forEach { key ->
            if (key.style == KbStyle.SPACER) {
                Spacer(Modifier.weight(key.weight).fillMaxHeight())
            } else {
                KeyCell(key, onPress, Modifier.weight(key.weight).fillMaxHeight())
            }
        }
    }
}

/**
 * One key. It reacts as the finger goes down (like a phone keyboard), and delete and the arrows repeat while held.
 * It is not focusable, so pressing it never takes the cursor out of the text box.
 */
@Composable
private fun KeyCell(key: KbKey, onPress: (KbKey) -> Unit, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    val (base, content) = when {
        key.active -> colors.primary to colors.onPrimary
        key.style == KbStyle.OPERATOR -> colors.secondaryContainer to colors.onSecondaryContainer
        key.style == KbStyle.FUNCTION -> colors.tertiaryContainer to colors.onTertiaryContainer
        key.style == KbStyle.ACTION -> colors.surfaceVariant to colors.onSurfaceVariant
        else -> colors.surface to colors.onSurface
    }
    var pressed by remember { mutableStateOf(false) }
    val currentKey by rememberUpdatedState(key)
    val currentPress by rememberUpdatedState(onPress)
    val repeats = key.action == KbAction.Backspace || key.action == KbAction.CursorLeft || key.action == KbAction.CursorRight

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (pressed) lerp(base, content, 0.22f) else base)
            // restarted when the key under this spot changes (another page), so a digit never inherits "repeat"
            .pointerInput(repeats) {
                detectTapGestures(onPress = {
                    pressed = true
                    try {
                        currentPress(currentKey)
                        if (repeats) {
                            coroutineScope {
                                val hold = launch {
                                    delay(400)
                                    while (true) {
                                        currentPress(currentKey)
                                        delay(60)
                                    }
                                }
                                tryAwaitRelease()
                                hold.cancel()
                            }
                        } else {
                            tryAwaitRelease()
                        }
                    } finally {
                        // also when the press is interrupted, so a key is never left looking pressed
                        pressed = false
                    }
                })
            }
            .semantics(mergeDescendants = true) {
                contentDescription = key.description
                role = Role.Button
                onClick(label = null) {
                    currentPress(currentKey)
                    true
                }
            },
        contentAlignment = Alignment.Center
    ) {
        val size = when (key.style) {
            KbStyle.OPERATOR -> 20.sp
            KbStyle.FUNCTION -> 13.sp
            KbStyle.ACTION -> if (key.label.length > 2) 13.sp else 17.sp
            else -> if (key.label.length > 2) 14.sp else 18.sp
        }
        Text(
            text = key.label,
            color = content,
            fontSize = size,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            maxLines = 1,
            softWrap = false
        )
    }
}
