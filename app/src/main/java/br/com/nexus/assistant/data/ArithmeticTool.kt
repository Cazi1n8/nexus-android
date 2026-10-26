package br.com.nexus.assistant.data

import java.math.BigDecimal
import java.math.MathContext
import java.text.Normalizer
import java.util.Locale

/** Small, bounded arithmetic parser. No scripting engine, network, or language-model guessing. */
object ArithmeticTool {
    private val precision = MathContext.DECIMAL128
    private val prefix = Regex("^(?:(?:nexus[, :]+)?(?:quanto (?:e|da)|qual (?:e )?o resultado(?: de)?|calcule|calcular|resolva|resolve)(?:\\s*[:=])?\\s+)")
    private val alphabet = Regex("[0-9\\s.,+\\-*/^()=]+")

    /** Returns null for ordinary conversation, including questions that merely contain numbers. */
    fun answerFor(input: String): String? {
        var expression = Normalizer.normalize(input.trim().lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .replace('×', '*').replace('÷', '/').replace('−', '-')
        expression = expression.replace(prefix, "").trim().trimEnd('?', '!', '=').trim()
        expression = expression.replace(Regex("\\bvezes\\b"), "*")
            .replace(Regex("\\bdividido por\\b"), "/").replace(Regex("\\bmais\\b"), "+")
            .replace(Regex("\\bmenos\\b"), "-")
        // Support x as a multiplication symbol only between numbers/parentheses.
        expression = expression.replace(Regex("(?<=[0-9)])\\s*x\\s*(?=[0-9(])"), "*")
        if (expression.isEmpty() || !alphabet.matches(expression) || expression.none { it.isDigit() }) return null
        if (expression.none { it in "+-*/^()" }) return null
        if (expression.length > 256) return "O cálculo é longo demais. Use uma expressão com até 256 caracteres."
        return try {
            val result = Parser(expression).parse().stripTrailingZeros().toPlainString()
            "${expression.replace('*', '×').replace('/', '÷')} = ${result.replace('.', ',')}"
        } catch (failure: ArithmeticFailure) {
            failure.message
        } catch (_: ArithmeticException) {
            "Não foi possível calcular essa expressão dentro dos limites de precisão."
        }
    }

    private class ArithmeticFailure(message: String) : IllegalArgumentException(message)

    private class Parser(private val input: String) {
        private var position = 0
        private var depth = 0
        private var operations = 0

        fun parse(): BigDecimal {
            val value = sum()
            spaces()
            if (position != input.length) invalid()
            return bounded(value)
        }

        private fun sum(): BigDecimal {
            var value = product()
            while (true) {
                value = when {
                    take('+') -> bounded(value.add(product(), precision))
                    take('-') -> bounded(value.subtract(product(), precision))
                    else -> return value
                }
                count()
            }
        }

        private fun product(): BigDecimal {
            var value = unary()
            while (true) {
                value = when {
                    take('*') -> bounded(value.multiply(unary(), precision))
                    take('/') -> {
                        val divisor = unary()
                        if (divisor.signum() == 0) throw ArithmeticFailure("Divisão por zero não é definida.")
                        bounded(value.divide(divisor, precision))
                    }
                    else -> return value
                }
                count()
            }
        }

        private fun unary(): BigDecimal {
            enter()
            return try {
                when {
                    take('+') -> unary()
                    take('-') -> unary().negate(precision)
                    else -> power()
                }
            } finally { depth-- }
        }

        private fun power(): BigDecimal {
            val base = primary()
            if (!take('^')) return base
            count()
            val exponent = unary()
            val integer = try { exponent.intValueExact() } catch (_: ArithmeticException) {
                throw ArithmeticFailure("Neste calculador, use expoentes inteiros entre -100 e 100.")
            }
            if (integer !in -100..100) throw ArithmeticFailure("Neste calculador, use expoentes inteiros entre -100 e 100.")
            if (base.signum() == 0 && integer <= 0) throw ArithmeticFailure("Zero elevado a zero ou a um expoente negativo não é definido neste calculador.")
            return bounded(if (integer < 0) BigDecimal.ONE.divide(base.pow(-integer, precision), precision) else base.pow(integer, precision))
        }

        private fun primary(): BigDecimal {
            if (take('(')) {
                val value = sum()
                if (!take(')')) invalid()
                return value
            }
            spaces()
            val start = position
            var decimal = false
            var digits = 0
            while (position < input.length) {
                val symbol = input[position]
                if (symbol.isDigit()) {
                    digits++
                    position++
                } else if (symbol == '.' || symbol == ',') {
                    if (decimal) invalid()
                    decimal = true
                    position++
                } else break
            }
            if (digits == 0) invalid()
            if (digits > 80) throw ArithmeticFailure("Use números com até 80 algarismos.")
            return input.substring(start, position).replace(',', '.').toBigDecimalOrNull()?.let(::bounded) ?: invalid()
        }

        private fun take(symbol: Char): Boolean {
            spaces()
            if (position >= input.length || input[position] != symbol) return false
            position++
            return true
        }
        private fun spaces() { while (position < input.length && input[position].isWhitespace()) position++ }
        private fun enter() { if (++depth > 24) throw ArithmeticFailure("Há parênteses ou sinais encadeados demais. Simplifique a expressão.") }
        private fun count() { if (++operations > 100) throw ArithmeticFailure("Use no máximo 100 operações por cálculo.") }
        private fun bounded(value: BigDecimal): BigDecimal {
            if (value.precision() - value.scale() > 200 || value.scale() > 200) throw ArithmeticFailure("O resultado ultrapassa o limite de 200 casas deste calculador.")
            return value
        }
        private fun invalid(): Nothing = throw ArithmeticFailure("Não entendi a expressão. Use números, +, −, ×, ÷, ^ e parênteses; vírgula ou ponto indicam decimais.")
    }
}
