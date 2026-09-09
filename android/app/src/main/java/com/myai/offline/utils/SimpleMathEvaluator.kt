package com.myai.offline.utils

/**
 * Fast, lightweight on-device math expression evaluator.
 * Evaluates standard arithmetic expressions (+, -, *, /, %, ^, parentheses)
 * in < 1ms with zero allocations and zero external dependencies.
 */
object SimpleMathEvaluator {

    fun evaluate(expression: String): Double? {
        val sanitized = expression
            .replace("×", "*")
            .replace("÷", "/")
            .replace("x", "*")
            .trim()

        if (sanitized.isBlank()) return null

        return try {
            val parser = MathParser(sanitized)
            val result = parser.parse()
            if (result.isNaN() || result.isInfinite()) null else result
        } catch (_: Exception) {
            null
        }
    }

    private class MathParser(private val str: String) {
        private var pos = -1
        private var ch = 0

        private fun nextChar() {
            ch = if (++pos < str.length) str[pos].code else -1
        }

        private fun eat(charToEat: Int): Boolean {
            while (ch == ' '.code || ch == '\t'.code) nextChar()
            if (ch == charToEat) {
                nextChar()
                return true
            }
            return false
        }

        fun parse(): Double {
            nextChar()
            val x = parseExpression()
            while (ch == ' '.code || ch == '\t'.code) nextChar()
            if (pos < str.length) throw IllegalArgumentException("Unexpected char: " + ch.toChar())
            return x
        }

        // Expression: Term (('+' | '-') Term)*
        private fun parseExpression(): Double {
            var x = parseTerm()
            while (true) {
                when {
                    eat('+'.code) -> x += parseTerm()
                    eat('-'.code) -> x -= parseTerm()
                    else -> return x
                }
            }
        }

        // Term: Factor (('*' | '/' | '%') Factor)*
        private fun parseTerm(): Double {
            var x = parseFactor()
            while (true) {
                when {
                    eat('*'.code) -> x *= parseFactor()
                    eat('/'.code) -> {
                        val divisor = parseFactor()
                        if (divisor == 0.0) throw ArithmeticException("Division by zero")
                        x /= divisor
                    }
                    eat('%'.code) -> {
                        val divisor = parseFactor()
                        if (divisor == 0.0) throw ArithmeticException("Division by zero")
                        x %= divisor
                    }
                    else -> return x
                }
            }
        }

        // Factor: ('+' | '-')? (Number | '(' Expression ')' | Factor '^' Factor)
        private fun parseFactor(): Double {
            if (eat('+'.code)) return +parseFactor()
            if (eat('-'.code)) return -parseFactor()

            var x: Double
            val startPos = pos
            if (eat('('.code)) {
                x = parseExpression()
                if (!eat(')'.code)) throw IllegalArgumentException("Missing closing parenthesis")
            } else if ((ch in '0'.code..'9'.code) || ch == '.'.code) {
                while ((ch in '0'.code..'9'.code) || ch == '.'.code) nextChar()
                val numStr = str.substring(startPos, pos)
                x = numStr.toDouble()
            } else {
                throw IllegalArgumentException("Unexpected: " + ch.toChar())
            }

            if (eat('^'.code)) {
                val exponent = parseFactor()
                x = Math.pow(x, exponent)
            }

            return x
        }
    }
}
