package com.tapecalc.core.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
    /** Small label of the SHIFT function, shown top-left. Only drawn when [reserveHintRow] is on (scientific keypad). */
    shiftHint: String? = null,
    /** Small label of the ALPHA function, shown top-right. Only drawn when [reserveHintRow] is on (scientific keypad). */
    alphaHint: String? = null,
    /** Draws the key in the accent colour (SHIFT / ALPHA / HYP while they are switched on). */
    highlighted: Boolean = false,
    minHeight: Dp = 48.dp,
    /**
     * Hides every small label (SHIFT, ALPHA and long-press hints). The scientific keypad turns this on while SHIFT,
     * ALPHA or HYP is active, so only the layer that is in use is drawn, in the large size.
     */
    hideHints: Boolean = false,
    /**
     * Reserves a band at the top of the key for the small corner labels, so they never overlap the main label.
     * The scientific keypad turns this on for every key (also keys without a corner label), which keeps all main
     * labels of a row at the same height.
     */
    reserveHintRow: Boolean = false
) {
    val look = LocalCalcLook.current
    val c = look.colors
    // AC and delete are drawn like "=": filled with the theme colour.
    val filledLikeEquals = spec.kind == KeyKind.EQUALS || spec.action == KeyAction.AllClear || spec.action == KeyAction.Backspace
    val (rawContainer, rawContent) = when {
        filledLikeEquals -> c.equalsKey to c.equalsText
        spec.kind == KeyKind.DIGIT -> c.digitKey to c.digitText
        spec.kind == KeyKind.OPERATOR -> c.operatorKey to c.operatorText
        else -> c.functionKey to c.functionText
    }
    val style = look.keypadStyle
    val isEquals = filledLikeEquals
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
        val labelWeight = if (spec.kind == KeyKind.DIGIT) FontWeight.Medium else FontWeight.SemiBold
        val labelSize = textSize * look.fontScale
        if (reserveHintRow) {
            // Scientific keypad: the key is split into two bands. The top band is only for the small SHIFT (left) and
            // ALPHA (right) labels; the main label is centred in the band below. They can no longer be drawn on top of
            // each other, and the main label sits at the same height on every key of the keypad.
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                HintBand(
                    left = if (hideHints) null else shiftHint,
                    right = if (hideHints) null else (alphaHint ?: spec.hint),
                    leftColor = c.accent,
                    rightColor = if (alphaHint != null) MaterialTheme.colorScheme.tertiary else content.copy(alpha = 0.6f),
                    scale = look.fontScale
                )
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    KeyLabel(spec.label, content, labelSize, labelWeight)
                }
            }
        } else {
            // Basic keypad: the long-press hint is drawn in the corner, as before.
            KeyLabel(spec.label, content, labelSize, labelWeight)
            if (!hideHints && spec.hint != null) {
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
}

/** Width of the longest label that still gets the normal hint size; longer pairs are drawn a little smaller. */
private const val HINT_PAIR_LIMIT = 7

/**
 * The top band of a scientific key: SHIFT label at the left, ALPHA (or long-press) label at the right. The band has a
 * fixed height even when it is empty, so every key keeps the same room for its main label. When both labels are long
 * together (COPY + PASTE) they are drawn smaller instead of running into each other.
 */
@Composable
private fun HintBand(left: String?, right: String?, leftColor: Color, rightColor: Color, scale: Float) {
    val crowded = left != null && right != null && left.length + right.length > HINT_PAIR_LIMIT
    val size = (if (crowded) 7.5f else 9f).sp * scale
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height((12f * scale).dp)
            .padding(horizontal = 4.dp)
            .clearAndSetSemantics { },
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (left != null) {
            Text(
                text = left,
                color = leftColor,
                fontSize = size,
                lineHeight = size,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                softWrap = false
            )
        }
        Spacer(Modifier.weight(1f))
        if (right != null) {
            Text(
                text = right,
                color = rightColor,
                fontSize = size,
                lineHeight = size,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

/**
 * The main label of a key. If the text is wider than the key (ALPHA, or CONST when SHIFT is on) it is made a little
 * smaller until it fits, instead of being cut off at the edge.
 */
@Composable
private fun KeyLabel(text: String, color: Color, size: TextUnit, weight: FontWeight) {
    var shrink by remember(text, size) { mutableFloatStateOf(1f) }
    Text(
        text = text,
        color = color,
        fontSize = size * shrink,
        fontWeight = weight,
        maxLines = 1,
        softWrap = false,
        textAlign = TextAlign.Center,
        onTextLayout = { layout ->
            if (layout.didOverflowWidth && shrink > 0.5f) shrink *= 0.88f
        }
    )
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
