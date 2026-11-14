package com.tapecalc.feature.basic

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.tapecalc.core.math.editor.LineBreaks
import com.tapecalc.core.math.editor.MathNode
import com.tapecalc.core.math.editor.MathTree
import com.tapecalc.core.math.editor.Mk
import com.tapecalc.core.math.editor.RunNode
import com.tapecalc.core.math.editor.SlotNode
import com.tapecalc.core.math.editor.TemplateNode

private val AlwaysVisible: State<Float> = mutableStateOf(1f)
private val LocalCursorBlink = compositionLocalOf { AlwaysVisible }

/**
 * Draws an input line the way a natural-display calculator does: ordinary text, with fractions stacked over a
 * line, log with a small base, integrals with limits and so on, and an empty box wherever something still has
 * to be typed. [cursor] is the text position of the blinking cursor, or null for a read-only line.
 * Long lines wrap onto further lines.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MathText(
    text: String,
    cursor: Int?,
    fontSize: TextUnit,
    color: Color,
    modifier: Modifier = Modifier,
    weight: FontWeight = FontWeight.Medium
) {
    val nodes = remember(text) { MathTree.parse(text) }
    val blink: State<Float> = if (cursor != null) {
        val transition = rememberInfiniteTransition(label = "cursor")
        transition.animateFloat(
            initialValue = 1f,
            targetValue = 0f,
            animationSpec = infiniteRepeatable(tween(520, easing = LinearEasing), RepeatMode.Reverse),
            label = "cursorAlpha"
        )
    } else AlwaysVisible
    CompositionLocalProvider(LocalCursorBlink provides blink) {
        FlowRow(
            modifier = modifier,
            horizontalArrangement = Arrangement.End,
            verticalArrangement = Arrangement.Center
        ) {
            // wrap = true: only the top level of the line may break onto a further row. The boxes inside a fraction,
            // integral and so on stay in one piece.
            Nodes(nodes, cursor, fontSize, color, weight, wrap = true)
        }
    }
}

@Composable
private fun Nodes(nodes: List<MathNode>, cursor: Int?, fs: TextUnit, color: Color, weight: FontWeight, wrap: Boolean = false) {
    for (node in nodes) {
        when (node) {
            is RunNode -> Run(node, cursor, fs, color, weight, wrap)
            is TemplateNode -> Template(node, cursor, fs, color, weight)
        }
    }
}

/**
 * One piece of text. With [wrap] the text is cut into small chunks ([LineBreaks]) that are separate children of the
 * FlowRow, so a long run such as `78999999999999999999` or `ans+ans+ans+ans+ans` can continue on the next row. Before,
 * the whole run was a single piece that could not break and was cut off at the edge of the screen.
 */
@Composable
private fun Piece(text: String, fs: TextUnit, color: Color, weight: FontWeight, wrap: Boolean = false) {
    if (text.isEmpty()) return
    val style = TextStyle(fontSize = fs, fontWeight = weight, fontFeatureSettings = "tnum")
    if (!wrap) {
        Text(text = text, color = color, style = style, softWrap = false)
        return
    }
    val chunks = remember(text) { LineBreaks.chunks(text) }
    for (chunk in chunks) {
        Text(text = chunk, color = color, style = style, softWrap = false)
    }
}

@Composable
private fun Run(run: RunNode, cursor: Int?, fs: TextUnit, color: Color, weight: FontWeight, wrap: Boolean = false) {
    if (cursor != null && cursor >= run.start && cursor <= run.end) {
        val cut = cursor - run.start
        Piece(run.text.substring(0, cut), fs, color, weight, wrap)
        Caret(fs)
        Piece(run.text.substring(cut), fs, color, weight, wrap)
    } else {
        Piece(run.text, fs, color, weight, wrap)
    }
}

@Composable
private fun Caret(fs: TextUnit) {
    val blink = LocalCursorBlink.current
    val accent = MaterialTheme.colorScheme.primary
    val height = with(LocalDensity.current) { (fs * 1.1f).toDp() }
    Box(
        Modifier
            .width(2.dp)
            .height(height)
            .graphicsLayer { alpha = blink.value }
            .background(accent)
    )
}

/** A box of a template: its content, or an empty dashed-looking box that still needs something typed. */
@Composable
private fun Slot(slot: SlotNode, cursor: Int?, fs: TextUnit, color: Color, weight: FontWeight) {
    if (slot.isEmpty) {
        val box = with(LocalDensity.current) { (fs * 0.75f).toDp() }
        val tall = with(LocalDensity.current) { (fs * 1.0f).toDp() }
        Box(
            modifier = Modifier
                .size(width = box, height = tall)
                .border(1.dp, color.copy(alpha = 0.55f), RoundedCornerShape(3.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (cursor != null && cursor == slot.start) Caret(fs)
        }
    } else {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Nodes(slot.nodes, cursor, fs, color, weight)
        }
    }
}

@Composable
private fun Rule(color: Color) {
    Box(Modifier.fillMaxWidth().height(1.5.dp).background(color))
}

@Composable
private fun Template(t: TemplateNode, cursor: Int?, fs: TextUnit, color: Color, weight: FontWeight) {
    val small = fs * 0.68f
    val s = t.slots
    @Composable
    fun S(i: Int, size: TextUnit = fs) = Slot(s[i], cursor, size, color, weight)

    when (t.open) {
        Mk.FRAC -> Column(
            modifier = Modifier.padding(horizontal = 3.dp).width(IntrinsicSize.Max),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            S(0, fs * 0.9f)
            Rule(color)
            S(1, fs * 0.9f)
        }

        Mk.MIXED -> Row(verticalAlignment = Alignment.CenterVertically) {
            S(0)
            Column(
                modifier = Modifier.padding(horizontal = 3.dp).width(IntrinsicSize.Max),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                S(1, fs * 0.9f)
                Rule(color)
                S(2, fs * 0.9f)
            }
        }

        Mk.LOGB -> Row(verticalAlignment = Alignment.Top) {
            Piece("log", fs, color, weight)
            Box(Modifier.padding(top = with(LocalDensity.current) { (fs * 0.55f).toDp() })) { S(0, small) }
            Piece("(", fs, color, weight)
            S(1)
            Piece(")", fs, color, weight)
        }

        Mk.NROOT -> Row(verticalAlignment = Alignment.Bottom) {
            Box(Modifier.padding(bottom = with(LocalDensity.current) { (fs * 0.5f).toDp() })) { S(0, small) }
            Piece("√", fs, color, weight)
            Box(
                Modifier
                    .drawBehind { drawLine(color, Offset(0f, 1f), Offset(size.width, 1f), strokeWidth = 3f) }
                    .padding(top = 3.dp)
            ) { S(1) }
        }

        Mk.INTEG -> Row(verticalAlignment = Alignment.CenterVertically) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                S(2, small)
                Piece("∫", fs * 1.25f, color, weight)
                S(1, small)
            }
            S(0)
            Piece("dx", fs, color, weight)
        }

        Mk.SUM, Mk.PROD -> Row(verticalAlignment = Alignment.CenterVertically) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                S(2, small)
                Piece(if (t.open == Mk.SUM) "Σ" else "Π", fs * 1.2f, color, weight)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Piece("x=", small, color, weight)
                    S(1, small)
                }
            }
            Piece("(", fs, color, weight)
            S(0)
            Piece(")", fs, color, weight)
        }

        Mk.DERIV -> Row(verticalAlignment = Alignment.CenterVertically) {
            Column(
                modifier = Modifier.width(IntrinsicSize.Max),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Piece("d", fs * 0.9f, color, weight)
                Rule(color)
                Piece("dx", fs * 0.9f, color, weight)
            }
            Piece("(", fs, color, weight)
            S(0)
            Piece(")", fs, color, weight)
            Piece("|x=", small, color, weight)
            S(1, small)
        }

        else -> Unit
    }
}
