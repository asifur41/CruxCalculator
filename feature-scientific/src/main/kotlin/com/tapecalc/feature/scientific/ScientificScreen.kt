package com.tapecalc.feature.scientific

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tapecalc.core.math.editor.Mk
import com.tapecalc.core.ui.CasioKey
import com.tapecalc.core.ui.Dir
import com.tapecalc.core.ui.KeyAction
import com.tapecalc.core.ui.KeyButton
import com.tapecalc.core.ui.KeyKind
import com.tapecalc.core.ui.KeySpec
import com.tapecalc.core.ui.LocalCalcLook
import com.tapecalc.feature.basic.CalculatorDisplay
import com.tapecalc.feature.basic.DisplayActions
import com.tapecalc.feature.basic.DisplayState
import com.tapecalc.feature.basic.MiniTapeLine
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * The scientific keypad, laid out like a physical Casio so it can be learnt once and used everywhere:
 *
 *     SHIFT  ALPHA  [ cursor pad ]  MODE   Σ
 *     CALC   ∫dx    [ cursor pad ]  x⁻¹   logₓy
 *     a/b    √      x²    xʸ    log   ln
 *     (−)    °′″    hyp   sin   cos   tan
 *     RCL    ENG    ( )   x     S⇔D   M+
 *     7  8  9  ⌫  AC     4  5  6  ×  ÷     1  2  3  +  −     0  .  Exp  Ans  =
 *
 * Every key has up to three layers: plain, SHIFT (small label top-left) and ALPHA (small label top-right).
 * Pressing SHIFT or ALPHA arms the next key only. The single "( )" key types "(" when tapped and ")" when held.
 */
object SciKeys {

    private fun fn(label: String, insert: String, description: String) =
        KeySpec(label, KeyAction.Insert(insert), KeyKind.FUNCTION, description)

    private fun act(label: String, action: KeyAction, description: String, kind: KeyKind = KeyKind.FUNCTION) =
        KeySpec(label, action, kind, description)

    private fun digit(d: String, shift: KeySpec? = null, alpha: KeySpec? = null) =
        CasioKey(KeySpec(d, KeyAction.Insert(d), KeyKind.DIGIT, d), shift, alpha)

    private fun op(label: String, insert: String, description: String, shift: KeySpec? = null, alpha: KeySpec? = null) =
        CasioKey(KeySpec(label, KeyAction.Insert(insert), KeyKind.OPERATOR, description), shift, alpha)

    private fun tool(label: String, id: String, description: String) =
        act(label, KeyAction.OpenTool(id), description)

    // ---- top block -----------------------------------------------------------------------------------
    val shift = CasioKey(act("SHIFT", KeyAction.Shift, "Shift: the next key uses its small top-left label", KeyKind.OPERATOR))
    val alpha = CasioKey(act("ALPHA", KeyAction.Alpha, "Alpha: the next key types the letter shown top-right", KeyKind.OPERATOR))
    val calc = CasioKey(
        act("CALC", KeyAction.Calc, "Calculate, asking for the value of each unknown letter"),
        shift = fn("SOLVE", "solve(", "Solve an equation for x"),
        alpha = fn("=", "=", "Equals sign, for assigning a variable")
    )
    val integral = CasioKey(
        act("∫dx", KeyAction.Template(Mk.INTEG), "Integral with limits"),
        shift = act("d/dx", KeyAction.Template(Mk.DERIV), "Derivative at a point"),
        alpha = fn(":", ":", "Colon, to join several calculations on one line")
    )
    val mode = CasioKey(act("MODE", KeyAction.OpenMode, "Mode: angle unit, number format and fractions"))
    val sigma = CasioKey(
        act("Σ", KeyAction.Template(Mk.SUM), "Sum of an expression over a range"),
        shift = act("Π", KeyAction.Template(Mk.PROD), "Product of an expression over a range")
    )
    val reciprocal = CasioKey(
        fn("x⁻¹", "^(-1)", "Reciprocal"),
        shift = fn("x!", "!", "Factorial")
    )
    val logBase = CasioKey(
        act("logₓy", KeyAction.Template(Mk.LOGB), "Logarithm with a base you type"),
        shift = fn("log₂", "log2(", "Logarithm base 2")
    )

    // ---- six-column rows ----------------------------------------------------------------------------
    val row2 = listOf(
        CasioKey(
            act("a⁄b", KeyAction.Template(Mk.FRAC), "Fraction"),
            shift = act("a b⁄c", KeyAction.Template(Mk.MIXED), "Mixed number")
        ),
        CasioKey(
            fn("√", "√(", "Square root"),
            shift = fn("∛", "cbrt(", "Cube root"),
            alpha = fn("mod", " mod ", "Modulo")
        ),
        CasioKey(fn("x²", "^2", "Square"), shift = fn("x³", "^3", "Cube")),
        CasioKey(fn("xʸ", "^", "Power"), shift = act("ˣ√", KeyAction.Template(Mk.NROOT), "Root with an index you type")),
        CasioKey(fn("log", "log(", "Logarithm base 10"), shift = fn("10ˣ", "10^(", "Ten to the power")),
        CasioKey(fn("ln", "ln(", "Natural logarithm"), shift = fn("eˣ", "exp(", "e to the power"))
    )

    val row3 = listOf(
        CasioKey(fn("(−)", "−", "Negative sign"), alpha = fn("A", "A", "Variable A")),
        CasioKey(fn("°′″", "°", "Degrees, minutes, seconds"), shift = fn("′", "'", "Minutes mark"), alpha = fn("B", "B", "Variable B")),
        CasioKey(
            act("hyp", KeyAction.Hyp, "Hyperbolic: the next sin, cos or tan becomes sinh, cosh or tanh"),
            shift = fn("|x|", "abs(", "Absolute value"),
            alpha = fn("C", "C", "Variable C")
        ),
        CasioKey(
            fn("sin", "sin(", "Sine"),
            shift = fn("sin⁻¹", "asin(", "Inverse sine"),
            alpha = fn("D", "D", "Variable D"),
            hyp = fn("sinh", "sinh(", "Hyperbolic sine"),
            shiftHyp = fn("sinh⁻¹", "asinh(", "Inverse hyperbolic sine")
        ),
        CasioKey(
            fn("cos", "cos(", "Cosine"),
            shift = fn("cos⁻¹", "acos(", "Inverse cosine"),
            alpha = fn("y", "y", "Variable y"),
            hyp = fn("cosh", "cosh(", "Hyperbolic cosine"),
            shiftHyp = fn("cosh⁻¹", "acosh(", "Inverse hyperbolic cosine")
        ),
        CasioKey(
            fn("tan", "tan(", "Tangent"),
            shift = fn("tan⁻¹", "atan(", "Inverse tangent"),
            alpha = fn("F", "F", "Variable F"),
            hyp = fn("tanh", "tanh(", "Hyperbolic tangent"),
            shiftHyp = fn("tanh⁻¹", "atanh(", "Inverse hyperbolic tangent")
        )
    )

    /** The single bracket key: tap for "(", hold for ")". It replaces the two bracket keys of the basic keypad. */
    val bracket = CasioKey(
        KeySpec(
            label = "( )",
            action = KeyAction.Insert("("),
            kind = KeyKind.FUNCTION,
            description = "Open parenthesis. Hold for close parenthesis",
            hint = ")",
            longAction = KeyAction.Insert(")"),
            longDescription = "Close parenthesis"
        ),
        shift = fn("%", "%", "Percent")
    )

    val row4 = listOf(
        CasioKey(act("RCL", KeyAction.Recall, "Recall a variable"), shift = act("STO", KeyAction.Store, "Store the value in a variable")),
        CasioKey(act("ENG", KeyAction.CycleNotation, "Change the number format: normal, scientific, engineering")),
        bracket,
        CasioKey(fn("x", "x", "The letter x"), shift = fn(",", ",", "Comma, between function arguments")),
        CasioKey(act("S⇔D", KeyAction.ToggleFraction, "Switch the result between fraction and decimal")),
        CasioKey(
            act("M+", KeyAction.MemoryPlus, "Add to memory M"),
            shift = act("M−", KeyAction.MemoryMinus, "Subtract from memory M"),
            alpha = fn("M", "M", "Memory M")
        )
    )

    // ---- five-column rows ---------------------------------------------------------------------------
    val row5 = listOf(
        digit("7", shift = tool("CONST", "constants", "Physical constants")),
        digit("8", shift = tool("CONV", "convert", "Unit converter")),
        digit("9", shift = fn("∞", "inf", "Infinity, for integral limits")),
        CasioKey(act("⌫", KeyAction.Backspace, "Delete before the cursor")),
        CasioKey(act("AC", KeyAction.AllClear, "All clear"), shift = act("CLR", KeyAction.AllClear, "Clear everything"))
    )
    val row6 = listOf(
        digit("4", shift = tool("MAT", "matrix", "Matrices")),
        digit("5", shift = tool("VCT", "vector", "Vectors")),
        digit("6", shift = tool("ALG", "algebra", "Algebra")),
        op("×", "×", "Multiply", shift = fn("nPr", "nPr(", "Permutations"), alpha = fn("GCD", "gcd(", "Greatest common divisor")),
        op("÷", "÷", "Divide", shift = fn("nCr", "nCr(", "Combinations"), alpha = fn("LCM", "lcm(", "Least common multiple"))
    )
    val row7 = listOf(
        digit("1", shift = tool("STAT", "stats1", "Statistics")),
        digit("2", shift = tool("CMPLX", "complex", "Complex numbers")),
        digit("3", shift = tool("DISTR", "dist", "Distributions")),
        op("+", "+", "Add", shift = fn("Pol", "pol(", "Rectangular to polar"), alpha = fn("Ceil", "ceil(", "Round up")),
        op("−", "−", "Subtract", shift = fn("Rec", "rec(", "Polar to rectangular"), alpha = fn("Floor", "floor(", "Round down"))
    )
    val row8 = listOf(
        CasioKey(
            KeySpec("0", KeyAction.Insert("0"), KeyKind.DIGIT, "0"),
            shift = act("COPY", KeyAction.CopyResult, "Copy the result"),
            alpha = act("PASTE", KeyAction.Paste, "Paste from the clipboard")
        ),
        CasioKey(KeySpec(".", KeyAction.Insert("."), KeyKind.DIGIT, "Decimal point")),
        CasioKey(
            fn("Exp", "×10^(", "Times ten to the power"),
            shift = fn("π", "pi", "Pi"),
            alpha = fn("e", "e", "Euler's number")
        ),
        CasioKey(KeySpec("Ans", KeyAction.Ans, KeyKind.FUNCTION, "Previous answer")),
        CasioKey(
            KeySpec("=", KeyAction.Equals, KeyKind.EQUALS, "Equals"),
            shift = act("History", KeyAction.OpenTape, "Open the tape")
        )
    )
}

@Composable
private fun CasioRow(
    keys: List<CasioKey>,
    shift: Boolean,
    alpha: Boolean,
    hyp: Boolean,
    onAction: (KeyAction) -> Unit,
    textSize: TextUnit,
    minKeyHeight: Dp,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier) {
        keys.forEach { key ->
            val spec = key.resolve(shift, alpha, hyp)
            val armed = when (key.main.action) {
                KeyAction.Shift -> shift
                KeyAction.Alpha -> alpha
                KeyAction.Hyp -> hyp
                else -> false
            }
            KeyButton(
                spec = spec,
                onAction = onAction,
                modifier = Modifier.weight(1f).fillMaxHeight(),
                textSize = textSize,
                shiftHint = key.shift?.label,
                alphaHint = key.alpha?.label,
                highlighted = armed,
                minHeight = minKeyHeight,
                // While SHIFT, ALPHA or HYP is on, only the active layer is drawn; all small labels come back when it is off.
                hideHints = shift || alpha || hyp
            )
        }
    }
}

/**
 * Four arrows in one pad. A press moves the cursor once; holding repeats. Up and down move between the
 * parts of a fraction or integral, or, outside those, through earlier lines of the tape.
 */
@Composable
private fun CursorPad(onAction: (KeyAction) -> Unit, modifier: Modifier = Modifier) {
    val look = LocalCalcLook.current
    val colors = look.colors
    val latest by rememberUpdatedState(onAction)
    val scope = rememberCoroutineScope()
    val line = colors.muted.copy(alpha = 0.45f)
    val glyph = colors.functionText

    Box(
        modifier = modifier
            .padding(3.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(colors.functionKey)
            .drawBehind {
                drawLine(line, Offset(0f, 0f), Offset(size.width, size.height), strokeWidth = 2f)
                drawLine(line, Offset(size.width, 0f), Offset(0f, size.height), strokeWidth = 2f)
                drawCircle(colors.accent, radius = size.minDimension * 0.13f, center = Offset(size.width / 2f, size.height / 2f))
            }
            .pointerInput(Unit) {
                detectTapGestures(onPress = { p ->
                    val w = size.width.toFloat().coerceAtLeast(1f)
                    val h = size.height.toFloat().coerceAtLeast(1f)
                    val dx = (p.x - w / 2f) / w
                    val dy = (p.y - h / 2f) / h
                    val dir = if (abs(dx) > abs(dy)) (if (dx < 0) Dir.LEFT else Dir.RIGHT) else (if (dy < 0) Dir.UP else Dir.DOWN)
                    latest(KeyAction.Move(dir))
                    val repeat = scope.launch {
                        delay(380)
                        while (true) {
                            latest(KeyAction.Move(dir))
                            delay(70)
                        }
                    }
                    try {
                        tryAwaitRelease()
                    } finally {
                        repeat.cancel()
                    }
                })
            }
            .semantics {
                contentDescription = "Cursor pad: left, right, up and down"
                customActions = listOf(
                    CustomAccessibilityAction("Cursor left") { latest(KeyAction.Move(Dir.LEFT)); true },
                    CustomAccessibilityAction("Cursor right") { latest(KeyAction.Move(Dir.RIGHT)); true },
                    CustomAccessibilityAction("Cursor up") { latest(KeyAction.Move(Dir.UP)); true },
                    CustomAccessibilityAction("Cursor down") { latest(KeyAction.Move(Dir.DOWN)); true }
                )
            }
    ) {
        Text("▲", color = glyph, fontSize = 15.sp, modifier = Modifier.align(Alignment.TopCenter).padding(top = 5.dp))
        Text("▼", color = glyph, fontSize = 15.sp, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 5.dp))
        Text("◀", color = glyph, fontSize = 15.sp, modifier = Modifier.align(Alignment.CenterStart).padding(start = 9.dp))
        Text("▶", color = glyph, fontSize = 15.sp, modifier = Modifier.align(Alignment.CenterEnd).padding(end = 9.dp))
    }
}

@Composable
fun ScientificKeypad(
    shift: Boolean,
    alpha: Boolean,
    hyp: Boolean,
    onAction: (KeyAction) -> Unit,
    modifier: Modifier = Modifier,
    minKeyHeight: Dp = 38.dp
) {
    val small = 15.sp
    val big = 21.sp
    Column(modifier.padding(horizontal = 6.dp, vertical = 2.dp)) {
        // top block: SHIFT ALPHA, cursor pad in the middle, MODE and friends on the right
        Row(Modifier.weight(2f).fillMaxWidth()) {
            Column(Modifier.weight(2f)) {
                CasioRow(listOf(SciKeys.shift, SciKeys.alpha), shift, alpha, hyp, onAction, small, minKeyHeight, Modifier.weight(1f))
                CasioRow(listOf(SciKeys.calc, SciKeys.integral), shift, alpha, hyp, onAction, small, minKeyHeight, Modifier.weight(1f))
            }
            CursorPad(onAction, Modifier.weight(2f).fillMaxHeight())
            Column(Modifier.weight(2f)) {
                CasioRow(listOf(SciKeys.mode, SciKeys.sigma), shift, alpha, hyp, onAction, small, minKeyHeight, Modifier.weight(1f))
                CasioRow(listOf(SciKeys.reciprocal, SciKeys.logBase), shift, alpha, hyp, onAction, small, minKeyHeight, Modifier.weight(1f))
            }
        }
        listOf(SciKeys.row2, SciKeys.row3, SciKeys.row4).forEach { row ->
            CasioRow(row, shift, alpha, hyp, onAction, small, minKeyHeight, Modifier.weight(1f).fillMaxWidth())
        }
        listOf(SciKeys.row5, SciKeys.row6, SciKeys.row7, SciKeys.row8).forEach { row ->
            CasioRow(row, shift, alpha, hyp, onAction, big, minKeyHeight, Modifier.weight(1f).fillMaxWidth())
        }
    }
}

/**
 * Scientific calculator. Portrait: a tall display, then the keypad. Landscape: display on the left, keypad on
 * the right. The keypad needs no scrolling: its keys share the height that is left.
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
                CalculatorDisplay(state, miniTape, actions, Modifier.weight(0.9f), maxTapeLines = 3, scientific = true)
                ScientificKeypad(
                    state.shift, state.alpha, state.hyp, actions.onAction,
                    Modifier.weight(1.3f), minKeyHeight = 28.dp
                )
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                CalculatorDisplay(state, miniTape, actions, Modifier.weight(0.9f), maxTapeLines = 4, scientific = true)
                ScientificKeypad(state.shift, state.alpha, state.hyp, actions.onAction, Modifier.weight(1.7f).fillMaxWidth())
            }
        }
    }
}
