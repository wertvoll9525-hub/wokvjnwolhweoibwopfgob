package com.graphro.core.parse

/**
 * Reads numbers the way they appear in Korean tables and sentences:
 * "1,240명", "320만", "3억 2천만 원", "△5", "(5)", "12.5%", "₩1,000", "1.2E+05", "$3.5M".
 */
object KoreanNumber {

    data class Parsed(
        val value: Double,
        /** Trailing unit word such as "원", "명", "%". Null when there was none. */
        val unit: String?,
        /** The largest Korean magnitude word used ("만", "억", "조"), as its factor. */
        val bigFactor: Double?,
        /** True if any magnitude word or suffix (천/만/억/조/k/M/B) changed the plain digits. */
        val hadMagnitude: Boolean,
        val approx: Boolean,
        val negativeMarker: Boolean,
    )

    private val EMPTY_TOKENS = setOf(
        "-", "–", "—", "−", "…", "...", "·", "x", "X", "n/a", "N/A", "NA", "na", "null", "NULL",
        "해당없음", "해당 없음", "없음", "미상", "자료없음", "비공개", "#N/A", "#DIV/0!", "#VALUE!", "#REF!", "#NAME?", "#NUM!",
    )

    fun isEmptyToken(s: String): Boolean {
        val t = s.trim()
        return t.isEmpty() || t in EMPTY_TOKENS
    }

    private const val BIG_UNITS = "조억만"
    private const val SMALL_UNITS = "천백십"

    private fun bigOf(c: Char): Double = when (c) {
        '조' -> 1e12
        '억' -> 1e8
        '만' -> 1e4
        else -> 1.0
    }

    private fun smallOf(c: Char): Double = when (c) {
        '천' -> 1e3
        '백' -> 1e2
        '십' -> 1e1
        else -> 1.0
    }

    private val UNIT_RE = Regex("^[A-Za-z가-힣㎡㎢㎏㎞㎜㎝㎖㎗ℓ%‰℃°/·]{1,8}$")
    private val THOUSANDS_RE = Regex("^\\d{1,3}(,\\d{3})+(\\.\\d+)?$")

    private val UNIT_ALIASES = mapOf(
        "퍼센트" to "%", "프로" to "%", "pct" to "%",
        "원정" to "원", "원화" to "원", "krw" to "원", "KRW" to "원",
        "usd" to "달러", "USD" to "달러",
    )

    fun parse(input: String): Parsed? {
        var s = input.trim()
            .replace(' ', ' ')
            .replace("​", "")
            .replace('，', ',')
            .replace('．', '.')
        if (s.isEmpty() || isEmptyToken(s)) return null

        var approx = false
        for (p in listOf("약 ", "약", "대략 ", "대략", "≒", "~")) {
            if (s.startsWith(p) && s.length > p.length && (s[p.length].isDigit() || s[p.length] == ' ' || s[p.length] in "-△▽▼(")) {
                approx = true
                s = s.substring(p.length).trim()
                break
            }
        }

        var negative = false
        var negMarker = false
        if (s.startsWith("(") && s.endsWith(")") && s.length > 2) {
            negative = true; negMarker = true
            s = s.substring(1, s.length - 1).trim()
        }
        if (s.isNotEmpty()) {
            when (s[0]) {
                '△', '▽', '▼' -> { negative = !negative; negMarker = true; s = s.substring(1).trim() }
                '-', '−', '–' -> { negative = !negative; s = s.substring(1).trim() }
                '+', '▲' -> s = s.substring(1).trim()
            }
        }

        var unit: String? = null
        if (s.isNotEmpty()) {
            val cur = when (s[0]) {
                '₩', '￦' -> "원"
                '$' -> "달러"
                '€' -> "유로"
                '£' -> "파운드"
                '¥' -> "엔"
                else -> null
            }
            if (cur != null) { unit = cur; s = s.substring(1).trim() }
        }
        // "-" after a currency sign: "$-5"
        if (s.startsWith("-") || s.startsWith("−")) { negative = !negative; s = s.substring(1).trim() }

        if (s.isEmpty() || !(s[0].isDigit() || (s[0] == '.' && s.length > 1 && s[1].isDigit()))) return null

        var i = 0
        var total = 0.0
        var section = 0.0
        var sawNumber = false
        var lastHadUnit = true
        var hadMagnitude = false
        var bigFactor: Double? = null
        var suffixMult = 1.0

        fun skipSpaces() { while (i < s.length && s[i] == ' ') i++ }

        while (i < s.length) {
            skipSpaces()
            if (i >= s.length) break
            val c = s[i]
            if (c.isDigit() || (c == '.' && i + 1 < s.length && s[i + 1].isDigit())) {
                if (sawNumber && !lastHadUnit) return null
                val start = i
                while (i < s.length && (s[i].isDigit() || s[i] == ',' || s[i] == '.')) i++
                // exponent
                if (i < s.length && (s[i] == 'e' || s[i] == 'E') && i + 1 < s.length &&
                    (s[i + 1].isDigit() || ((s[i + 1] == '+' || s[i + 1] == '-') && i + 2 < s.length && s[i + 2].isDigit()))
                ) {
                    i++
                    if (s[i] == '+' || s[i] == '-') i++
                    while (i < s.length && s[i].isDigit()) i++
                }
                val token = s.substring(start, i)
                val n = toNumber(token) ?: return null
                sawNumber = true
                // English suffixes directly after the number
                if (i < s.length && (s[i] == 'k' || s[i] == 'K' || s[i] == 'M' || s[i] == 'B') &&
                    (i + 1 == s.length || !s[i + 1].isLetter())
                ) {
                    suffixMult = when (s[i]) { 'k', 'K' -> 1e3; 'M' -> 1e6; else -> 1e9 }
                    hadMagnitude = true
                    i++
                }
                skipSpaces()
                var mult = 1.0
                var hadUnitHere = false
                if (i < s.length && s[i] in SMALL_UNITS && isMagnitudeContext(s, i)) {
                    mult = smallOf(s[i]); i++; hadMagnitude = true; hadUnitHere = true
                    skipSpaces()
                }
                if (i < s.length && s[i] in BIG_UNITS && isMagnitudeContext(s, i)) {
                    val big = bigOf(s[i]); i++
                    section += n * mult
                    total += section * big
                    section = 0.0
                    hadMagnitude = true
                    hadUnitHere = true
                    if (bigFactor == null || big > bigFactor) bigFactor = big
                } else {
                    section += n * mult
                }
                lastHadUnit = hadUnitHere
                continue
            }
            // Anything else must be a trailing unit word.
            val rest = s.substring(i).trim()
            if (rest.isEmpty()) break
            val u = UNIT_ALIASES[rest] ?: rest
            if (!UNIT_RE.matches(u)) return null
            if (unit == null) unit = u
            break
        }
        if (!sawNumber) return null
        var value = (total + section) * suffixMult
        if (negative) value = -value
        return Parsed(value, unit, bigFactor, hadMagnitude, approx, negMarker)
    }

    /**
     * A magnitude character counts as a number word only when it is followed by the end,
     * a space, another magnitude word or a short unit. "3만원" yes, "3천원" yes, "5백화점" no.
     */
    private fun isMagnitudeContext(s: String, i: Int): Boolean {
        val next = s.getOrNull(i + 1) ?: return true
        if (next == ' ' || next.isDigit() || next in BIG_UNITS || next in SMALL_UNITS) return true
        val rest = s.substring(i + 1).trim()
        return rest.length <= 4 && UNIT_RE.matches(rest)
    }

    private fun toNumber(token: String): Double? {
        if (token.count { it == '.' } > 1) return null
        if (token.contains(',')) {
            val mantissa = token.substringBefore('e').substringBefore('E')
            if (!THOUSANDS_RE.matches(mantissa)) return null
            return token.replace(",", "").toDoubleOrNull()
        }
        return token.toDoubleOrNull()
    }

    /** True when the text is just digits with optional grouping and decimal point. */
    fun isPlainNumber(s: String): Boolean {
        val t = s.trim()
        return t.isNotEmpty() && (t.toDoubleOrNull() != null || THOUSANDS_RE.matches(t.removePrefix("-")))
    }
}
