package com.tapecalc.core.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * A text box for expressions and equations. While a letter is being typed (x, y, a, b...), a row of the signs an
 * expression needs (+ − × ÷ ^ ( ) = ...) appears under the box, so there is no need to switch the phone keyboard to its
 * symbol page. The row goes away when something other than a letter is typed, or when the box loses focus, and comes
 * back with the next letter.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ExpressionField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    singleLine: Boolean = true,
    minLines: Int = 1,
    isError: Boolean = false,
    supportingText: String? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
    /** False for boxes that hold ordinary words (a list of names), where the row of maths signs would only get in the way. */
    symbolRow: Boolean = true,
    /**
     * Which pages the optional built-in keyboard offers for this box. Null picks [MathKeyboardKind.EXPRESSION], or
     * [MathKeyboardKind.PLAIN_TEXT] when [symbolRow] is false. Only used while the keyboard is switched on in Settings.
     */
    keyboardKind: MathKeyboardKind? = null
) {
    var field by remember { mutableStateOf(TextFieldValue(value, TextRange(value.length))) }
    var rowWanted by remember { mutableStateOf(false) }
    var focused by remember { mutableStateOf(false) }
    // Follow changes made from outside (a Clear button, restored text, a length limit applied by the caller).
    val shown = if (field.text == value) field else TextFieldValue(value, TextRange(value.length))

    // The optional built-in keyboard. With the switch off (the default) none of this is used.
    val builtIn = LocalMathKeyboard.current.enabled
    val focusManager = LocalFocusManager.current
    val clipboard = LocalClipboardManager.current
    val kind = keyboardKind ?: if (symbolRow) MathKeyboardKind.EXPRESSION else MathKeyboardKind.PLAIN_TEXT

    fun accept(next: TextFieldValue) {
        field = next
        onValueChange(next.text)
    }

    fun edit(result: MathKeyboardLogic.Edit) = accept(TextFieldValue(result.text, TextRange(result.caret)))

    fun onBuiltInKey(action: KbAction) {
        val sel = shown.selection
        when (action) {
            is KbAction.Insert -> edit(MathKeyboardLogic.insert(shown.text, sel.start, sel.end, action.text))
            KbAction.Backspace -> edit(MathKeyboardLogic.backspace(shown.text, sel.start, sel.end))
            KbAction.CursorLeft -> accept(shown.copy(selection = TextRange(MathKeyboardLogic.caretLeft(shown.text, sel.start, sel.end))))
            KbAction.CursorRight -> accept(shown.copy(selection = TextRange(MathKeyboardLogic.caretRight(shown.text, sel.start, sel.end))))
            KbAction.Paste -> MathKeyboardLogic.cleanPaste(clipboard.getText()?.text, multiline = !singleLine)?.let {
                edit(MathKeyboardLogic.insert(shown.text, sel.start, sel.end, it))
            }
            KbAction.Clear -> accept(TextFieldValue("", TextRange(0)))
            KbAction.Hide -> focusManager.clearFocus()
            else -> Unit
        }
    }

    val textField: @Composable () -> Unit = {
        OutlinedTextField(
            value = shown,
            onValueChange = { next ->
                if (next.selection.collapsed) {
                    SymbolInput.reaction(shown.text, next.text, next.selection.start)?.let { rowWanted = it }
                }
                accept(next)
            },
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged {
                    focused = it.isFocused
                    if (!it.isFocused) rowWanted = false
                },
            label = if (label != null) {
                { Text(label) }
            } else null,
            placeholder = if (placeholder != null) {
                { Text(placeholder) }
            } else null,
            singleLine = singleLine,
            minLines = minLines,
            isError = isError,
            supportingText = if (supportingText != null) {
                { Text(supportingText) }
            } else null,
            keyboardOptions = keyboardOptions
        )
    }

    // Scroll the keyboard into view when it opens, so it is never below the visible part of a scrolling screen.
    val keyboardInView = remember { BringIntoViewRequester() }
    val keyboardShown = builtIn && focused
    LaunchedEffect(keyboardShown) {
        if (keyboardShown) {
            // wait until the keyboard has been laid out, otherwise there is nothing yet to scroll to
            withFrameNanos { }
            withFrameNanos { }
            keyboardInView.bringIntoView()
        }
    }

    Column(modifier) {
        if (builtIn) WithoutSystemKeyboard(textField) else textField()
        if (builtIn) {
            if (focused) {
                // the Back button hides the keyboard first, like it does with the phone's own keyboard
                BackHandler { focusManager.clearFocus() }
                MathKeyboard(
                    kind = kind,
                    multiline = !singleLine,
                    onAction = { onBuiltInKey(it) },
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .bringIntoViewRequester(keyboardInView)
                )
            }
        } else if (symbolRow && rowWanted && focused) {
            SymbolRow(
                onSymbol = { symbol ->
                    val sel = shown.selection
                    val (text, caret) = SymbolInput.insert(shown.text, sel.start, sel.end, symbol)
                    accept(TextFieldValue(text, TextRange(caret)))
                }
            )
        }
    }
}

/** The row of signs itself: scrolls sideways when the screen is narrow. */
@Composable
fun SymbolRow(onSymbol: (String) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(top = 4.dp, bottom = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        SymbolInput.symbols.forEach { symbol ->
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                onClick = { onSymbol(symbol.insert) },
                modifier = Modifier.semantics { contentDescription = symbol.description }
            ) {
                Box(
                    modifier = Modifier.defaultMinSize(minWidth = 40.dp, minHeight = 40.dp).padding(horizontal = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(symbol.label, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
