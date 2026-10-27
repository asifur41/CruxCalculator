package com.tapecalc.core.math

import com.tapecalc.core.precision.Rational

enum class BinOp { ADD, SUB, MUL, DIV, POW, MOD }

sealed class Node
class NumNode(val value: Rational) : Node()
class NameNode(val name: String, val pos: Int) : Node()
class UnaryNode(val negate: Boolean, val operand: Node) : Node()
class BinaryNode(val op: BinOp, val left: Node, val right: Node, val implicit: Boolean = false) : Node()
class FactorialNode(val operand: Node) : Node()
class PercentNode(val operand: Node) : Node()
class CallNode(val name: String, val args: List<Node>, val pos: Int) : Node()
class ParenNode(val inner: Node) : Node()

sealed class Statement {
    class Expr(val node: Node) : Statement()
    class Assign(val name: String, val node: Node) : Statement()
}

/**
 * Pratt (top-down operator precedence) parser.
 *
 * Binding powers (higher binds tighter):
 *  + -  10 | * / mod 20 | implicit multiplication 22 | unary minus 25 | ^ 30 (right assoc.) | ! % 40
 *
 * Consequences worth knowing:
 *  - -2^2 = -4 and 2^3^2 = 512
 *  - implicit multiplication binds tighter than division: 1/2x is read as 1/(2x) (a warning is added)
 */
class Parser(private val tokens: List<Token>) {
    private var pos = 0
    private var depth = 0
    val warnings = mutableListOf<String>()

    private class Infix(val op: BinOp, val lbp: Int, val rbp: Int)

    companion object {
        private const val ADD_BP = 10
        private const val MUL_BP = 20
        private const val IMPLICIT_BP = 22
        private const val UNARY_BP = 25
        private const val POW_BP = 30
        private const val POSTFIX_BP = 40
        private const val MAX_DEPTH = 150

        fun parse(tokens: List<Token>): Pair<Statement, List<String>> {
            val p = Parser(tokens)
            val st = p.parseStatement()
            return st to p.warnings
        }
    }

    private fun peek(): Token = tokens[pos]
    private fun peekAt(offset: Int): Token = tokens[minOf(pos + offset, tokens.size - 1)]
    private fun advance(): Token {
        val t = tokens[pos]
        if (t.type != T.EOF) pos++
        return t
    }

    private fun fail(message: String, at: Token): Nothing =
        throw CalcException(ErrorKind.SYNTAX, message, at.pos)

    fun parseStatement(): Statement {
        if (peek().type == T.EOF) throw CalcException(ErrorKind.EMPTY, "Nothing to calculate")
        val statement: Statement =
            if (peek().type == T.IDENT && peekAt(1).type == T.EQUALS) {
                val nameTok = advance()
                advance()
                if (Functions.isReserved(nameTok.text)) {
                    throw CalcException(ErrorKind.SYNTAX, "'${nameTok.text}' is a reserved name and cannot be a variable", nameTok.pos)
                }
                Statement.Assign(nameTok.text, parseExpression(0))
            } else {
                Statement.Expr(parseExpression(0))
            }
        val t = peek()
        when (t.type) {
            T.EOF -> Unit
            T.RPAREN -> throw CalcException(ErrorKind.UNBALANCED_PARENTHESES, "Unmatched closing parenthesis", t.pos)
            T.EQUALS -> fail("Unexpected '='. Variables are set like: x = 5", t)
            else -> fail("Unexpected '${t.text}'", t)
        }
        return statement
    }

    private fun infixFor(t: Token): Infix? = when (t.type) {
        T.PLUS -> Infix(BinOp.ADD, ADD_BP, ADD_BP + 1)
        T.MINUS -> Infix(BinOp.SUB, ADD_BP, ADD_BP + 1)
        T.STAR -> Infix(BinOp.MUL, MUL_BP, MUL_BP + 1)
        T.SLASH -> Infix(BinOp.DIV, MUL_BP, MUL_BP + 1)
        T.CARET -> Infix(BinOp.POW, POW_BP, POW_BP - 1)
        T.IDENT -> if (t.text.equals("mod", ignoreCase = true)) Infix(BinOp.MOD, MUL_BP, MUL_BP + 1) else null
        else -> null
    }

    private fun startsOperand(t: Token): Boolean = when (t.type) {
        T.NUMBER, T.LPAREN, T.SQRT -> true
        T.IDENT -> !t.text.equals("mod", ignoreCase = true)
        else -> false
    }

    private fun parseExpression(minBp: Int): Node {
        if (++depth > MAX_DEPTH) throw CalcException(ErrorKind.TOO_COMPLEX, "Expression is too complex")
        try {
            var left = parsePrefix()
            while (true) {
                val t = peek()
                if (t.type == T.BANG || t.type == T.PERCENT) {
                    if (POSTFIX_BP < minBp) break
                    advance()
                    left = if (t.type == T.BANG) FactorialNode(left) else PercentNode(left)
                    continue
                }
                val infix = infixFor(t)
                if (infix != null) {
                    if (infix.lbp < minBp) break
                    advance()
                    val right = parseExpression(infix.rbp)
                    if (infix.op == BinOp.DIV && right is BinaryNode && right.implicit) {
                        warnings.add("Read as a/(b·c): implicit multiplication binds tighter than division. Add parentheses to be explicit.")
                    }
                    left = BinaryNode(infix.op, left, right)
                    continue
                }
                if (startsOperand(t) && IMPLICIT_BP >= minBp) {
                    if (left is NumNode && t.type == T.NUMBER) fail("Missing operator between numbers", t)
                    val right = parseExpression(IMPLICIT_BP + 1)
                    left = BinaryNode(BinOp.MUL, left, right, implicit = true)
                    continue
                }
                break
            }
            return left
        } finally {
            depth--
        }
    }

    private fun parsePrefix(): Node {
        val t = advance()
        return when (t.type) {
            T.NUMBER -> {
                val value = try {
                    Rational.parse(t.text)
                } catch (e: NumberFormatException) {
                    fail("Invalid number '${t.text}'", t)
                }
                NumNode(value)
            }
            T.IDENT -> parseIdent(t)
            T.LPAREN -> {
                val inner = parseExpression(0)
                expectClose()
                ParenNode(inner)
            }
            T.MINUS -> UnaryNode(true, parseExpression(UNARY_BP))
            T.PLUS -> UnaryNode(false, parseExpression(UNARY_BP))
            T.SQRT -> CallNode("sqrt", listOf(parseExpression(35)), t.pos)
            T.EOF -> throw CalcException(ErrorKind.SYNTAX, "Unexpected end of expression", t.pos)
            else -> fail("Unexpected '${t.text}'", t)
        }
    }

    private fun parseIdent(t: Token): Node {
        val canonical = Functions.canonicalName(t.text)
        if (canonical == null) return NameNode(t.text, t.pos)
        if (peek().type == T.LPAREN) {
            advance()
            val args = ArrayList<Node>()
            if (peek().type == T.RPAREN) {
                advance()
            } else {
                while (true) {
                    args.add(parseExpression(0))
                    val sep = peek()
                    if (sep.type == T.COMMA) {
                        advance()
                        continue
                    }
                    expectClose()
                    break
                }
            }
            return CallNode(canonical, args, t.pos)
        }
        // Function applied without parentheses: "sin 30", "sqrt 16"
        if (!startsOperand(peek())) fail("'${t.text}' needs an argument", peek())
        return CallNode(canonical, listOf(parseExpression(IMPLICIT_BP)), t.pos)
    }

    private fun expectClose() {
        val t = peek()
        if (t.type != T.RPAREN) {
            throw CalcException(ErrorKind.UNBALANCED_PARENTHESES, "Missing closing parenthesis", t.pos)
        }
        advance()
    }
}
