package com.tapecalc.feature.basic

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tapecalc.core.ui.KeyAction
import com.tapecalc.core.ui.KeyButton
import com.tapecalc.core.ui.KeyKind
import com.tapecalc.core.ui.KeyRow
import com.tapecalc.core.ui.KeySpec
import com.tapecalc.core.ui.digitKey
import com.tapecalc.core.ui.opKey

/** Key definitions of the basic keypad. Long-press functions are shown as small hints. */
object BasicKeys {
    val allClear = KeySpec("AC", KeyAction.AllClear, KeyKind.FUNCTION, "All clear")
    val backspace = KeySpec(
        "⌫", KeyAction.Backspace, KeyKind.FUNCTION, "Backspace",
        hint = "CE", longAction = KeyAction.ClearEntry, longDescription = "Clear entry"
    )
    val percent = KeySpec(
        "%", KeyAction.Insert("%"), KeyKind.FUNCTION, "Percent",
        hint = "mod", longAction = KeyAction.Insert(" mod "), longDescription = "Modulo"
    )
    val divide = opKey("÷", "÷", "Divide")
    val multiply = opKey("×", "×", "Multiply", hint = "xʸ", longInsert = "^", longDescription = "Power")
    val minus = opKey("−", "−", "Subtract")
    val plus = opKey("+", "+", "Add")
    val openParen = KeySpec("(", KeyAction.Insert("("), KeyKind.FUNCTION, "Open parenthesis")
    val closeParen = KeySpec(")", KeyAction.Insert(")"), KeyKind.FUNCTION, "Close parenthesis")
    val sign = KeySpec("±", KeyAction.ToggleSign, KeyKind.FUNCTION, "Change sign")
    val ans = KeySpec(
        "ans", KeyAction.Ans, KeyKind.FUNCTION, "Previous answer",
        hint = "paste", longAction = KeyAction.Paste, longDescription = "Paste from clipboard"
    )
    val zero = KeySpec(
        "0", KeyAction.Insert("0"), KeyKind.DIGIT, "0",
        hint = "00", longAction = KeyAction.Insert("00"), longDescription = "Double zero"
    )
    val decimal = KeySpec(
        ".", KeyAction.Insert("."), KeyKind.DIGIT, "Decimal point",
        hint = ",", longAction = KeyAction.Insert(","), longDescription = "Comma, to separate function arguments"
    )
    val equals = KeySpec("=", KeyAction.Equals, KeyKind.EQUALS, "Equals")

    val rows: List<List<KeySpec>> = listOf(
        listOf(allClear, backspace, percent, divide),
        listOf(openParen, closeParen, sign, multiply),
        listOf(digitKey("7"), digitKey("8"), digitKey("9"), minus),
        listOf(digitKey("4"), digitKey("5"), digitKey("6"), plus)
    )
    val lowerBlock: List<List<KeySpec>> = listOf(
        listOf(digitKey("1"), digitKey("2"), digitKey("3")),
        listOf(ans, zero, decimal)
    )
}

/**
 * Compact 4-column keypad (6 rows). The "=" key spans the last two rows.
 * With [leftHanded] the operator column and "=" move to the left edge.
 */
@Composable
fun BasicKeypad(
    onAction: (KeyAction) -> Unit,
    modifier: Modifier = Modifier,
    leftHanded: Boolean = false,
    textSize: TextUnit = 24.sp
) {
    Column(modifier = modifier.padding(horizontal = 6.dp, vertical = 2.dp)) {
        BasicKeys.rows.forEach { row ->
            KeyRow(
                keys = row,
                onAction = onAction,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                lastKeyFirst = leftHanded,
                textSize = textSize
            )
        }
        Row(Modifier.weight(2f).fillMaxWidth()) {
            if (leftHanded) {
                EqualsKey(onAction, textSize)
                LowerBlock(onAction, textSize)
            } else {
                LowerBlock(onAction, textSize)
                EqualsKey(onAction, textSize)
            }
        }
    }
}

@Composable
private fun RowScope.EqualsKey(onAction: (KeyAction) -> Unit, textSize: TextUnit) {
    KeyButton(BasicKeys.equals, onAction, Modifier.weight(1f).fillMaxHeight(), textSize * 1.15f)
}

@Composable
private fun RowScope.LowerBlock(onAction: (KeyAction) -> Unit, textSize: TextUnit) {
    Column(Modifier.weight(3f).fillMaxHeight()) {
        BasicKeys.lowerBlock.forEach { row ->
            KeyRow(row, onAction, Modifier.weight(1f).fillMaxWidth(), textSize = textSize)
        }
    }
}
