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
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.Dp
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

    // ---- scientific keypad (SHIFT / ALPHA layers, cursor, templates, memory, navigation) ----
    data object Shift : KeyAction
    data object Alpha : KeyAction
    data object Hyp : KeyAction
    /** Clears a pending SHIFT / ALPHA / HYP after a key that the app root handled itself. */
    data object ClearModifiers : KeyAction
    data class Move(val direction: Dir) : KeyAction
    /** Inserts a template (fraction, log with base, integral...); [open] is the template's marker character. */
    data class Template(val open: Char) : KeyAction
    data object Store : KeyAction
    data object Recall : KeyAction
    data object MemoryPlus : KeyAction
    data object MemoryMinus : KeyAction
    data object CycleNotation : KeyAction
    data object CopyResult : KeyAction
    /** CALC: asks for the values of unknown letters, then calculates. */
    data object Calc : KeyAction
    data object OpenMode : KeyAction
    data object OpenTape : KeyAction
    data class OpenTool(val id: String) : KeyAction
    /** Puts an earlier line (tape entry) back into the input line for editing. */
    data class LoadExpression(val text: String) : KeyAction
}

enum class Dir { LEFT, RIGHT, UP, DOWN }

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
 * A key of the scientific keypad with its layers: what it does plain, after SHIFT, after ALPHA and while HYP is on.
 * [resolve] picks the layer; a layer that is not defined falls back to the plain key.
 */
data class CasioKey(
    val main: KeySpec,
    val shift: KeySpec? = null,
    val alpha: KeySpec? = null,
    val hyp: KeySpec? = null,
    val shiftHyp: KeySpec? = null
) {
    fun resolve(shiftOn: Boolean, alphaOn: Boolean, hypOn: Boolean): KeySpec = when {
        hypOn && shiftOn && shiftHyp != null -> shiftHyp
        hypOn && !shiftOn && hyp != null -> hyp
        shiftOn -> shift ?: main
        alphaOn -> alpha ?: main
        else -> main
    }
}

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
    textSize: TextUnit = 22.sp,
    /** Small label of the SHIFT function, shown top-left (scientific keypad). */
    shiftHint: String? = null,
    /** Small label of the ALPHA function, shown top-right (scientific keypad). */
    alphaHint: String? = null,
    /** Draws the key in the accent colour (SHIFT / ALPHA / HYP while they are switched on). */
    highlighted: Boolean = false,
    minHeight: Dp = 48.dp
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
        highlighted -> MaterialTheme.colorScheme.primary
        isEquals || style == KeypadStyle.FILLED -> rawContainer
        style == KeypadStyle.TONAL -> rawContainer.copy(alpha = 0.55f)
        else -> Color.Transparent
    }
    val content = when {
        highlighted -> MaterialTheme.colorScheme.onPrimary
        style == KeypadStyle.FLAT && spec.kind == KeyKind.OPERATOR -> c.accent
        else -> rawContent
    }
    val borderColor: Color? = if (style == KeypadStyle.OUTLINED && !isEquals) c.muted.copy(alpha = 0.55f) else null
    val shape = look.buttonShape.toShape()

    var chain = modifier
        .padding(3.dp)
        .defaultMinSize(minWidth = 48.dp, minHeight = minHeight)
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
        if (spec.hint != null && alphaHint == null) {
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
        if (shiftHint != null) {
            Text(
                text = shiftHint,
                color = c.accent,
                fontSize = 9.sp * look.fontScale,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(top = 2.dp, start = 5.dp)
                    .clearAndSetSemantics { }
            )
        }
        if (alphaHint != null) {
            Text(
                text = alphaHint,
                color = MaterialTheme.colorScheme.tertiary,
                fontSize = 9.sp * look.fontScale,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 2.dp, end = 5.dp)
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
