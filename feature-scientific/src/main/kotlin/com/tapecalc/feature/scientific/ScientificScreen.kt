package com.tapecalc.feature.scientific

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tapecalc.core.ui.KeyAction
import com.tapecalc.core.ui.KeyKind
import com.tapecalc.core.ui.KeyRow
import com.tapecalc.core.ui.KeySpec
import com.tapecalc.core.ui.fnKey
import com.tapecalc.feature.basic.BasicKeypad
import com.tapecalc.feature.basic.CalculatorDisplay
import com.tapecalc.feature.basic.DisplayActions
import com.tapecalc.feature.basic.DisplayState
import com.tapecalc.feature.basic.MiniTapeLine

/**
 * Scientific key grid: 5 columns x 4 rows. Every key has a long-press secondary function
 * (shown as a small hint) so the grid stays uncluttered.
 */
object ScientificKeys {

    fun rows(angleLabel: String): List<List<KeySpec>> = listOf(
        listOf(
            fnKey("sin", "sin(", "Sine", "sin⁻¹", "asin(", "Inverse sine"),
            fnKey("cos", "cos(", "Cosine", "cos⁻¹", "acos(", "Inverse cosine"),
            fnKey("tan", "tan(", "Tangent", "tan⁻¹", "atan(", "Inverse tangent"),
            fnKey("π", "pi", "Pi", "φ", "phi", "Golden ratio phi"),
            fnKey("e", "e", "Euler's number", "exp", "exp(", "Exponential function")
        ),
        listOf(
            fnKey("sinh", "sinh(", "Hyperbolic sine", "sinh⁻¹", "asinh(", "Inverse hyperbolic sine"),
            fnKey("cosh", "cosh(", "Hyperbolic cosine", "cosh⁻¹", "acosh(", "Inverse hyperbolic cosine"),
            fnKey("tanh", "tanh(", "Hyperbolic tangent", "tanh⁻¹", "atanh(", "Inverse hyperbolic tangent"),
            fnKey("log", "log(", "Logarithm base 10", "log₂", "log2(", "Logarithm base 2"),
            fnKey("ln", "ln(", "Natural logarithm", "logₙ", "log(", "Logarithm with custom base: log of x comma base")
        ),
        listOf(
            fnKey("x²", "^2", "Square", "x³", "^3", "Cube"),
            fnKey("xʸ", "^", "Power", "x⁻¹", "^(-1)", "Reciprocal"),
            fnKey("√", "√(", "Square root", "∛", "cbrt(", "Cube root"),
            fnKey("n!", "!", "Factorial", "root", "root(", "Nth root: root of x comma n"),
            fnKey("|x|", "abs(", "Absolute value", "round", "round(", "Round")
        ),
        listOf(
            fnKey("mod", " mod ", "Modulo", "floor", "floor(", "Floor"),
            fnKey("nPr", "nPr(", "Permutations", "ceil", "ceil(", "Ceiling"),
            fnKey("nCr", "nCr(", "Combinations", "min", "min(", "Minimum"),
            KeySpec("F⇄D", KeyAction.ToggleFraction, KeyKind.OPERATOR, "Toggle fraction or decimal result"),
            KeySpec(angleLabel, KeyAction.CycleAngle, KeyKind.OPERATOR, "Angle mode $angleLabel. Tap to change between degrees, radians and gradians")
        )
    )
}

@Composable
fun ScientificPanel(
    onAction: (KeyAction) -> Unit,
    angleLabel: String,
    modifier: Modifier = Modifier,
    textSize: TextUnit = 17.sp
) {
    Column(modifier.padding(horizontal = 6.dp, vertical = 2.dp)) {
        ScientificKeys.rows(angleLabel).forEach { row ->
            KeyRow(row, onAction, Modifier.weight(1f).fillMaxWidth(), textSize = textSize)
        }
    }
}

/**
 * Scientific calculator. Portrait: display, scientific grid, basic keypad stacked.
 * Landscape / wide: display and scientific grid on the left, basic keypad on the right.
 */
@Composable
fun ScientificScreen(
    state: DisplayState,
    miniTape: List<MiniTapeLine>,
    actions: DisplayActions,
    leftHanded: Boolean,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        if (maxWidth > maxHeight) {
            Row(Modifier.fillMaxSize()) {
                Column(Modifier.weight(1.3f)) {
                    CalculatorDisplay(state, miniTape, actions, Modifier.weight(1f))
                    ScientificPanel(actions.onAction, state.angleLabel, Modifier.weight(1.3f))
                }
                BasicKeypad(actions.onAction, Modifier.weight(1f), leftHanded)
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                CalculatorDisplay(state, miniTape, actions, Modifier.weight(0.75f))
                ScientificPanel(actions.onAction, state.angleLabel, Modifier.weight(0.95f).fillMaxWidth())
                BasicKeypad(actions.onAction, Modifier.weight(1.45f), leftHanded, textSize = 22.sp)
            }
        }
    }
}
