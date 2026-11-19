package com.tapecalc.core.ui

/*
 * Plain logic and key tables for the optional built-in keyboard (drawn by MathKeyboard.kt).
 * No Android or Compose code here, so all of it is unit tested, like SymbolInput.
 */

/** What a text box holds. It decides which pages, and which extra keys, the keyboard offers. */
enum class MathKeyboardKind {
    /** A calculator expression or equation: numbers, operators, letters, functions. */
    EXPRESSION,

    /** A spreadsheet cell: like [EXPRESSION], plus sum / mean / min / max / count and the range colon. */
    FORMULA,

    /** Ordinary words (a label, a list of names): letters and punctuation come first. */
    PLAIN_TEXT
}

/** The pages of the keyboard, switched from the tab strip on top of it. */
enum class KeyboardPage(val title: String, val description: String) {
    NUMBERS("123", "Numbers and operators page"),
    LETTERS("abc", "Letters page"),
    FUNCTIONS("f(x)", "Functions page"),
    MORE("more", "More functions and symbols page")
}

/** Letter case of the letters page: off, next letter only, or caps lock. */
enum class ShiftState {
    OFF, ONCE, LOCK;

    fun next(): ShiftState = when (this) {
        OFF -> ONCE
        ONCE -> LOCK
        LOCK -> OFF
    }
}

enum class KbStyle { NORMAL, OPERATOR, FUNCTION, ACTION, SPACER }

/** What a key does when it is pressed. */
sealed interface KbAction {
    /** Puts [text] into the box at the cursor (or in place of the selection). */
    data class Insert(val text: String) : KbAction

    /** Switches the keyboard to another page. */
    data class GoTo(val page: KeyboardPage) : KbAction

    data object Backspace : KbAction
    data object CursorLeft : KbAction
    data object CursorRight : KbAction
    data object Shift : KbAction
    data object Paste : KbAction
    data object Clear : KbAction
    data object Hide : KbAction

    /** A gap in a row; pressing it does nothing. */
    data object None : KbAction
}

/**
 * One key. [label] is what is drawn, [description] is what a screen reader says, [weight] is the share of the row's width,
 * and [active] draws the key as switched on (the current tab, or SHIFT while it is on).
 */
data class KbKey(
    val label: String,
    val action: KbAction,
    val description: String,
    val style: KbStyle = KbStyle.NORMAL,
    val weight: Float = 1f,
    val active: Boolean = false
)

/** A row of keys; [height] is its share of the height of the page. */
data class KbRow(val keys: List<KbKey>, val height: Float = 1f)

object MathKeyboardLogic {

    /** Result of an edit: the new text and where the cursor goes. */
    data class Edit(val text: String, val caret: Int)

    // ------------------------------------------------------------------------------------------ pages

    fun pages(kind: MathKeyboardKind): List<KeyboardPage> = when (kind) {
        MathKeyboardKind.PLAIN_TEXT -> listOf(KeyboardPage.LETTERS, KeyboardPage.NUMBERS, KeyboardPage.MORE)
        else -> listOf(KeyboardPage.NUMBERS, KeyboardPage.LETTERS, KeyboardPage.FUNCTIONS, KeyboardPage.MORE)
    }

    fun defaultPage(kind: MathKeyboardKind): KeyboardPage =
        if (kind == MathKeyboardKind.PLAIN_TEXT) KeyboardPage.LETTERS else KeyboardPage.NUMBERS

    /** The keys that stay on screen on every page: cursor left and right, delete, hide the keyboard. */
    val stripKeys: List<KbKey> = listOf(
        KbKey("←", KbAction.CursorLeft, "Move cursor left", KbStyle.ACTION),
        KbKey("→", KbAction.CursorRight, "Move cursor right", KbStyle.ACTION),
        KbKey("⌫", KbAction.Backspace, "Delete", KbStyle.ACTION),
        KbKey("▾", KbAction.Hide, "Hide keyboard", KbStyle.ACTION)
    )

    /**
     * The rows of one page. [multiline] adds a new-line key, [compact] is for narrow boxes (dialogs on small phones): the
     * letters page then becomes a wide-key alphabetical grid instead of a ten-column QWERTY.
     */
    fun rows(
        page: KeyboardPage,
        kind: MathKeyboardKind,
        shift: ShiftState = ShiftState.OFF,
        multiline: Boolean = false,
        compact: Boolean = false
    ): List<KbRow> = when (page) {
        KeyboardPage.NUMBERS -> numbersRows(multiline)
        KeyboardPage.LETTERS -> if (compact) alphabeticalRows(shift, multiline) else qwertyRows(shift, multiline)
        KeyboardPage.FUNCTIONS -> functionRows(kind)
        KeyboardPage.MORE -> moreRows(kind)
    }

    // ------------------------------------------------------------------------------------------ key makers

    private fun digit(c: Char) = KbKey(c.toString(), KbAction.Insert(c.toString()), c.toString())

    private fun op(label: String, text: String, description: String, weight: Float = 1f) =
        KbKey(label, KbAction.Insert(text), description, KbStyle.OPERATOR, weight)

    private fun sym(label: String, text: String, description: String, weight: Float = 1f) =
        KbKey(label, KbAction.Insert(text), description, KbStyle.NORMAL, weight)

    private fun fn(label: String, text: String, description: String) =
        KbKey(label, KbAction.Insert(text), description, KbStyle.FUNCTION)

    private fun spacer(weight: Float) = KbKey("", KbAction.None, "", KbStyle.SPACER, weight)

    private fun space(weight: Float) = KbKey("space", KbAction.Insert(" "), "Space", KbStyle.NORMAL, weight)

    private fun newLine() = KbKey("↵", KbAction.Insert("\n"), "New line", KbStyle.ACTION)

    private fun letter(c: Char, shift: ShiftState): KbKey {
        val shown = if (shift == ShiftState.OFF) c.toString() else c.uppercaseChar().toString()
        return KbKey(shown, KbAction.Insert(shown), shown)
    }

    private fun shiftKey(shift: ShiftState, weight: Float) = KbKey(
        label = if (shift == ShiftState.LOCK) "⇪" else "⇧",
        action = KbAction.Shift,
        description = when (shift) {
            ShiftState.OFF -> "Shift, off"
            ShiftState.ONCE -> "Shift, next letter is a capital"
            ShiftState.LOCK -> "Caps lock, on"
        },
        style = KbStyle.ACTION,
        weight = weight,
        active = shift != ShiftState.OFF
    )

    private fun goTo(page: KeyboardPage, label: String, weight: Float) =
        KbKey(label, KbAction.GoTo(page), page.description, KbStyle.ACTION, weight)

    private fun pasteKey(weight: Float) = KbKey("Paste", KbAction.Paste, "Paste from the clipboard", KbStyle.ACTION, weight)

    private fun clearKey(weight: Float) = KbKey("Clear", KbAction.Clear, "Clear the whole box", KbStyle.ACTION, weight)

    // ------------------------------------------------------------------------------------------ pages

    /** 6 columns: digits and the signs used most. */
    private fun numbersRows(multiline: Boolean): List<KbRow> = listOf(
        KbRow(listOf(digit('7'), digit('8'), digit('9'), op("÷", "/", "divide"), sym("(", "(", "open bracket"), sym(")", ")", "close bracket"))),
        KbRow(listOf(digit('4'), digit('5'), digit('6'), op("×", "*", "times"), op("^", "^", "power"), op("%", "%", "percent"))),
        KbRow(listOf(digit('1'), digit('2'), digit('3'), op("−", "-", "minus"), sym("=", "=", "equals"), sym("π", "pi", "pi"))),
        KbRow(
            listOf(digit('0'), sym(".", ".", "decimal point"), sym(",", ",", "comma"), op("+", "+", "plus")) +
                if (multiline) listOf(space(1f), newLine()) else listOf(space(2f))
        )
    )

    /** 10 columns: a digit row (for names like x1 or A1) and QWERTY. */
    private fun qwertyRows(shift: ShiftState, multiline: Boolean): List<KbRow> = listOf(
        KbRow("1234567890".map { digit(it) }, height = 0.8f),
        KbRow("qwertyuiop".map { letter(it, shift) }),
        KbRow(listOf(spacer(0.5f)) + "asdfghjkl".map { letter(it, shift) } + spacer(0.5f)),
        KbRow(
            listOf(shiftKey(shift, 1.5f)) + "zxcvbnm".map { letter(it, shift) } + sym(".", ".", "decimal point", 1.5f)
        ),
        KbRow(
            listOf(goTo(KeyboardPage.NUMBERS, "123", 1.5f), sym("=", "=", "equals"), sym("(", "(", "open bracket")) +
                (if (multiline) listOf(space(3.5f), newLine()) else listOf(space(4.5f))) +
                listOf(sym(")", ")", "close bracket"), sym(",", ",", "comma"))
        )
    )

    /** 7 columns, no digit row: wide keys for narrow boxes. */
    private fun alphabeticalRows(shift: ShiftState, multiline: Boolean): List<KbRow> = listOf(
        KbRow("abcdefg".map { letter(it, shift) }),
        KbRow("hijklmn".map { letter(it, shift) }),
        KbRow("opqrstu".map { letter(it, shift) }),
        KbRow("vwxyz".map { letter(it, shift) } + shiftKey(shift, 1f) + sym(".", ".", "decimal point")),
        KbRow(
            listOf(goTo(KeyboardPage.NUMBERS, "123", 1.5f)) +
                (if (multiline) listOf(space(2.5f), newLine()) else listOf(space(3.5f))) +
                listOf(sym("=", "=", "equals"), sym(",", ",", "comma"))
        )
    )

    /** 7 columns: trigonometry, logarithms, roots, powers. In a spreadsheet cell the last row is the range functions. */
    private fun functionRows(kind: MathKeyboardKind): List<KbRow> {
        val last = if (kind == MathKeyboardKind.FORMULA) {
            listOf(
                fn("sum", "sum(", "sum of the numbers"),
                fn("mean", "mean(", "mean of the numbers"),
                fn("min", "min(", "smallest number"),
                fn("max", "max(", "largest number"),
                fn("count", "count(", "how many numbers"),
                sym(":", ":", "range, as in A1 to A9"),
                sym(",", ",", "comma")
            )
        } else {
            listOf(
                fn("x²", "^2", "squared"),
                fn("x³", "^3", "cubed"),
                fn("xʸ", "^", "to the power"),
                fn("x⁻¹", "^(-1)", "reciprocal"),
                sym("(", "(", "open bracket"),
                sym(")", ")", "close bracket"),
                sym("°", "°", "degree sign")
            )
        }
        return listOf(
            KbRow(
                listOf(
                    fn("sin", "sin(", "sine"), fn("cos", "cos(", "cosine"), fn("tan", "tan(", "tangent"),
                    fn("asin", "asin(", "inverse sine"), fn("acos", "acos(", "inverse cosine"), fn("atan", "atan(", "inverse tangent"),
                    fn("|x|", "abs(", "absolute value")
                )
            ),
            KbRow(
                listOf(
                    fn("sinh", "sinh(", "hyperbolic sine"), fn("cosh", "cosh(", "hyperbolic cosine"), fn("tanh", "tanh(", "hyperbolic tangent"),
                    fn("asinh", "asinh(", "inverse hyperbolic sine"), fn("acosh", "acosh(", "inverse hyperbolic cosine"),
                    fn("atanh", "atanh(", "inverse hyperbolic tangent"),
                    fn("n!", "!", "factorial")
                )
            ),
            KbRow(
                listOf(
                    fn("log", "log(", "logarithm base 10"), fn("ln", "ln(", "natural logarithm"), fn("log2", "log2(", "logarithm base 2"),
                    fn("e^x", "exp(", "e to the power"), fn("√", "sqrt(", "square root"), fn("³√", "cbrt(", "cube root"),
                    fn("n√", "root(", "nth root")
                )
            ),
            KbRow(last)
        )
    }

    /** 7 columns: the less common functions, constants and signs; for plain text, punctuation. */
    private fun moreRows(kind: MathKeyboardKind): List<KbRow> {
        if (kind == MathKeyboardKind.PLAIN_TEXT) {
            return listOf(
                KbRow(listOf(sym(".", ".", "full stop"), sym(",", ",", "comma"), sym("!", "!", "exclamation mark"), sym("?", "?", "question mark"),
                    sym("'", "'", "apostrophe"), sym("\"", "\"", "quotation mark"), sym(":", ":", "colon"))),
                KbRow(listOf(sym(";", ";", "semicolon"), sym("-", "-", "hyphen"), sym("_", "_", "underscore"), sym("(", "(", "open bracket"),
                    sym(")", ")", "close bracket"), sym("/", "/", "slash"), sym("@", "@", "at sign"))),
                KbRow(listOf(sym("#", "#", "hash"), sym("$", "$", "dollar sign"), sym("%", "%", "percent"), sym("&", "&", "ampersand"),
                    sym("+", "+", "plus"), sym("=", "=", "equals"), sym("*", "*", "star"))),
                KbRow(listOf(sym("<", "<", "less than"), sym(">", ">", "greater than"), sym("[", "[", "open square bracket"),
                    sym("]", "]", "close square bracket"), pasteKey(1.5f), clearKey(1.5f)))
            )
        }
        return listOf(
            KbRow(
                listOf(
                    fn("nPr", "npr(", "permutations"), fn("nCr", "ncr(", "combinations"), fn("mod", "mod(", "remainder"),
                    fn("gcd", "gcd(", "greatest common divisor"), fn("lcm", "lcm(", "least common multiple"),
                    fn("hypot", "hypot(", "length of the hypotenuse"), fn("atan2", "atan2(", "angle of a point")
                )
            ),
            KbRow(
                listOf(
                    fn("floor", "floor(", "round down"), fn("ceil", "ceil(", "round up"), fn("round", "round(", "round"),
                    fn("sign", "sign(", "sign of a number"), fn("Pol", "pol(", "rectangular to polar"), fn("Rec", "rec(", "polar to rectangular"),
                    sym("Ans", "ans", "previous answer")
                )
            ),
            KbRow(
                listOf(
                    sym("e", "e", "Euler's number"), sym("φ", "phi", "golden ratio"), sym("°", "°", "degree sign"),
                    sym("′", "'", "minutes"), sym("″", "\"", "seconds"), sym(":", ":", "colon"), sym(";", ";", "semicolon")
                )
            ),
            KbRow(listOf(sym("_", "_", "underscore"), sym("?", "?", "question mark"), sym("#", "#", "hash"), sym("@", "@", "at sign"),
                pasteKey(1.5f), clearKey(1.5f)))
        )
    }

    // ------------------------------------------------------------------------------------------ editing

    /** Puts [text] in place of the selection [selStart, selEnd) and returns the new text with the new cursor position. */
    fun insert(text: String, selStart: Int, selEnd: Int, put: String): Edit {
        val (newText, caret) = SymbolInput.insert(text, selStart, selEnd, put)
        return Edit(newText, caret)
    }

    /**
     * What the delete key does: removes the selection; or, with the cursor right after a function the keyboard typed
     * (`sin(`, `sqrt(`), the whole function; otherwise the one character before the cursor.
     */
    fun backspace(text: String, selStart: Int, selEnd: Int): Edit {
        val start = minOf(selStart, selEnd).coerceIn(0, text.length)
        val end = maxOf(selStart, selEnd).coerceIn(0, text.length)
        if (start != end) return Edit(text.removeRange(start, end), start)
        if (start == 0) return Edit(text, 0)
        val before = text.substring(0, start)
        var cut = functionLengthBefore(before)
        if (cut == 0) cut = if (start >= 2 && before[start - 1].isLowSurrogate() && before[start - 2].isHighSurrogate()) 2 else 1
        return Edit(text.removeRange(start - cut, start), start - cut)
    }

    /** Where the cursor goes for the left arrow: the start of a selection, else one character back. */
    fun caretLeft(text: String, selStart: Int, selEnd: Int): Int {
        val start = minOf(selStart, selEnd).coerceIn(0, text.length)
        val end = maxOf(selStart, selEnd).coerceIn(0, text.length)
        return if (start != end) start else (start - 1).coerceAtLeast(0)
    }

    /** Where the cursor goes for the right arrow: the end of a selection, else one character forward. */
    fun caretRight(text: String, selStart: Int, selEnd: Int): Int {
        val start = minOf(selStart, selEnd).coerceIn(0, text.length)
        val end = maxOf(selStart, selEnd).coerceIn(0, text.length)
        return if (start != end) end else (end + 1).coerceAtMost(text.length)
    }

    /** The shift state after [action] was pressed on [page]: the SHIFT key cycles it, one capital letter uses it up. */
    fun nextShift(shift: ShiftState, action: KbAction, page: KeyboardPage): ShiftState = when {
        action is KbAction.Shift -> shift.next()
        shift == ShiftState.ONCE && page == KeyboardPage.LETTERS &&
            action is KbAction.Insert && action.text.length == 1 && action.text[0].isLetter() -> ShiftState.OFF
        else -> shift
    }

    /** The text to paste: new lines become spaces in a one-line box; null when there is nothing to paste. */
    fun cleanPaste(raw: CharSequence?, multiline: Boolean): String? {
        val text = raw?.toString() ?: return null
        val cleaned = if (multiline) text.replace("\r\n", "\n").replace('\r', '\n') else text.replace("\r\n", " ").replace('\n', ' ').replace('\r', ' ')
        return cleaned.ifEmpty { null }
    }

    // The functions the keyboard types, longest first, so that asin( is found before sin(.
    private val functionTokens: List<String> by lazy {
        val kinds = listOf(MathKeyboardKind.EXPRESSION, MathKeyboardKind.FORMULA)
        listOf(KeyboardPage.FUNCTIONS, KeyboardPage.MORE)
            .flatMap { page -> kinds.flatMap { kind -> rows(page, kind).flatMap { it.keys } } }
            .mapNotNull { (it.action as? KbAction.Insert)?.text }
            .filter { it.length > 1 && it.endsWith("(") && it[0].isLetter() }
            .distinct()
            .sortedByDescending { it.length }
    }

    /** Length of the keyboard-typed function that ends [before], or 0. It must not be the tail of a longer word. */
    private fun functionLengthBefore(before: String): Int {
        for (token in functionTokens) {
            if (!before.endsWith(token)) continue
            val at = before.length - token.length
            if (at == 0 || !before[at - 1].isLetter()) return token.length
        }
        return 0
    }

    /** Every text the function keys insert; used by the tests to check them against the calculator. */
    fun functionInsertTexts(): List<String> = functionTokens
}
