package com.tapecalc.core.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** What a keypad key does. The app's view model interprets these. */
sealed interface KeyAction {
    data class Insert(val text: String) : KeyAction
    data object Backspace : KeyAction
    data object ClearEntry : KeyAction
    data object AllClear : KeyAction
    data object Equals : KeyAction
    data object ToggleSign : KeyAction
    data object Paste : KeyAction
    data object Ans : KeyAction
    data object SmartParen : KeyAction
    data object CycleAngle : KeyAction
    data object ToggleFraction : KeyAction
}

enum class KeyKind { DIGIT, OPERATOR, FUNCTION, EQUALS }

/**
 * One key. [hint] is the small label of the long-press function, [description] is what TalkBack reads.
 */
data class KeySpec(
    val label: String,
    val action: KeyAction,
    val kind: KeyKind,
    val description: String,
    val hint: String? = null,
    val longAction: KeyAction? = null,
    val longDescription: String? = null
)

fun digitKey(d: String) = KeySpec(d, KeyAction.Insert(d), KeyKind.DIGIT, d)

fun opKey(label: String, insert: String, description: String, hint: String? = null, longInsert: String? = null, longDescription: String? = null) =
    KeySpec(
        label, KeyAction.Insert(insert), KeyKind.OPERATOR, description, hint,
        longInsert?.let { KeyAction.Insert(it) }, longDescription
    )

fun fnKey(label: String, insert: String, description: String, hint: String? = null, longInsert: String? = null, longDescription: String? = null) =
    KeySpec(
        label, KeyAction.Insert(insert), KeyKind.FUNCTION, description, hint,
        longInsert?.let { KeyAction.Insert(it) }, longDescription
    )

/**
 * A calculator key with a minimum 48dp touch target, ripple, long-press support, TalkBack text
 * and keyboard focus (Enter / Space activate it).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun KeyButton(
    spec: KeySpec,
    onAction: (KeyAction) -> Unit,
    modifier: Modifier = Modifier,
    textSize: TextUnit = 22.sp
) {
    val look = LocalCalcLook.current
    val c = look.colors
    val (rawContainer, rawContent) = when (spec.kind) {
        KeyKind.DIGIT -> c.digitKey to c.digitText
        KeyKind.OPERATOR -> c.operatorKey to c.operatorText
        KeyKind.FUNCTION -> c.functionKey to c.functionText
        KeyKind.EQUALS -> c.equalsKey to c.equalsText
    }
    val style = look.keypadStyle
    val isEquals = spec.kind == KeyKind.EQUALS
    val container: Color = when {
        isEquals || style == KeypadStyle.FILLED -> rawContainer
        style == KeypadStyle.TONAL -> rawContainer.copy(alpha = 0.55f)
        else -> Color.Transparent
    }
    val content = if (style == KeypadStyle.FLAT && spec.kind == KeyKind.OPERATOR) c.accent else rawContent
    val borderColor: Color? = if (style == KeypadStyle.OUTLINED && !isEquals) c.muted.copy(alpha = 0.55f) else null
    val shape = look.buttonShape.toShape()

    var chain = modifier
        .padding(3.dp)
        .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
        .clip(shape)
        .background(container)
    if (borderColor != null) chain = chain.border(1.5.dp, borderColor, shape)

    Box(
        modifier = chain
            .combinedClickable(
                role = Role.Button,
                onClickLabel = spec.description,
                onLongClickLabel = spec.longDescription,
                onLongClick = spec.longAction?.let { long -> { onAction(long) } },
                onClick = { onAction(spec.action) }
            )
            .semantics { contentDescription = spec.description },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = spec.label,
            color = content,
            fontSize = textSize * look.fontScale,
            fontWeight = if (spec.kind == KeyKind.DIGIT) FontWeight.Medium else FontWeight.SemiBold,
            maxLines = 1,
            softWrap = false,
            textAlign = TextAlign.Center
        )
        if (spec.hint != null) {
            Text(
                text = spec.hint,
                color = content.copy(alpha = 0.6f),
                fontSize = 10.sp * look.fontScale,
                maxLines = 1,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 4.dp, end = 8.dp)
                    .clearAndSetSemantics { }
            )
        }
    }
}

/** Lays out [keys] as equal-width cells of one keypad row (the operator column moves to the left for left-handed layouts). */
@Composable
fun KeyRow(
    keys: List<KeySpec>,
    onAction: (KeyAction) -> Unit,
    modifier: Modifier = Modifier,
    lastKeyFirst: Boolean = false,
    textSize: TextUnit = 22.sp
) {
    Row(modifier = modifier) {
        val ordered = if (lastKeyFirst && keys.size > 1) listOf(keys.last()) + keys.dropLast(1) else keys
        ordered.forEach { spec ->
            KeyButton(spec, onAction, Modifier.weight(1f).fillMaxHeight(), textSize)
        }
    }
}
