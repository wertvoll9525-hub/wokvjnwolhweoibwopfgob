package com.graphro.core.text

import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.abs

object Format {

    /** "1,240", "12.5", "0.03" with up to [maxDecimals] decimals, trailing zeros removed. */
    fun plain(v: Double, maxDecimals: Int = 2): String {
        if (v.isNaN() || v.isInfinite()) return ""
        val decimals = when {
            abs(v) >= 100 -> minOf(maxDecimals, 1)
            abs(v) >= 1 || v == 0.0 -> maxDecimals
            else -> maxOf(maxDecimals, 3)
        }
        return fixed(v, decimals, trim = true)
    }

    /** Fixed decimals with thousands separators. */
    fun fixed(v: Double, decimals: Int, trim: Boolean = false): String {
        if (v.isNaN() || v.isInfinite()) return ""
        var bd = BigDecimal(v).setScale(decimals, RoundingMode.HALF_UP)
        if (trim) bd = bd.stripTrailingZeros()
        if (bd.scale() < 0) bd = bd.setScale(0)
        val neg = bd.signum() < 0
        val s = bd.abs().toPlainString()
        val intPart = s.substringBefore('.')
        val frac = s.substringAfter('.', "")
        val grouped = intPart.reversed().chunked(3).joinToString(",").reversed()
        val out = if (frac.isEmpty()) grouped else "$grouped.$frac"
        return if (neg && out.any { it in '1'..'9' }) "-$out" else out
    }

    /** Decimals needed so all values show distinctly, at most 2. */
    fun decimalsFor(values: List<Double>): Int {
        for (d in 0..2) {
            if (values.all { v -> abs(v * Math.pow(10.0, d.toDouble()) - Math.rint(v * Math.pow(10.0, d.toDouble()))) < 1e-6 }) return d
        }
        return if (values.isNotEmpty() && values.maxOf { abs(it) } >= 100) 1 else 2
    }

    /** Value with its unit for sentences: "410만 원", "1,240명", "12.5%". */
    fun withUnit(scaledValue: Double, scaleLabel: String, unit: String?, decimals: Int = -1): String {
        val n = if (decimals >= 0) fixed(scaledValue, decimals, trim = true) else plain(scaledValue)
        val u = unit ?: ""
        return when {
            scaleLabel.isEmpty() && u.isEmpty() -> n
            scaleLabel.isEmpty() -> if (u == "%" || u == "%p" || u.length == 1 || isTightUnit(u)) "$n$u" else "$n $u"
            u.isEmpty() -> "$n$scaleLabel"
            else -> "$n$scaleLabel $u"
        }
    }

    private fun isTightUnit(u: String) = u in setOf("명", "개", "건", "원", "회", "대", "곳", "세", "배", "점", "톤", "가구", "세대", "마리", "표")

    fun percent(v: Double): String {
        val a = abs(v)
        return when {
            a >= 10 -> fixed(v, 0)
            a >= 1 -> fixed(v, 1, trim = true)
            else -> fixed(v, 1, trim = true)
        }
    }
}

/** Korean particles that depend on whether the previous syllable has a final consonant. */
object Josa {
    private fun hasBatchim(word: String): Boolean? {
        val last = word.trimEnd().lastOrNull { !it.isWhitespace() && it != ')' && it != '’' && it != '\'' && it != '"' } ?: return null
        if (last in '가'..'힣') return (last.code - 0xAC00) % 28 != 0
        if (last.isDigit()) return last in "013678"
        return when (last.lowercaseChar()) {
            'l', 'm', 'n', 'r' -> true
            '%' -> false
            else -> false
        }
    }

    /** "3월이", "서울이", "부산이" vs "2월이"? → uses the word's ending. */
    fun iGa(w: String) = w + if (hasBatchim(w) == true) "이" else "가"
    fun eunNeun(w: String) = w + if (hasBatchim(w) == true) "은" else "는"
    fun eulReul(w: String) = w + if (hasBatchim(w) == true) "을" else "를"
    fun gwaWa(w: String) = w + if (hasBatchim(w) == true) "과" else "와"
    /** "(으)로": no 으 after a vowel or ㄹ. */
    fun euro(w: String): String {
        val last = w.trimEnd().lastOrNull() ?: return w + "로"
        if (last in '가'..'힣') {
            val jong = (last.code - 0xAC00) % 28
            return w + if (jong == 0 || jong == 8) "로" else "으로"
        }
        if (last.isDigit()) return w + if (last in "036") "으로" else "로"
        return w + "로"
    }
    fun ieyo(w: String) = w + if (hasBatchim(w) == true) "이에요" else "예요"
}
