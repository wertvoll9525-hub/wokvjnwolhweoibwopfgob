package com.graphro.core.parse

import com.graphro.core.model.Granularity
import com.graphro.core.model.TimePoint

/**
 * Reads period labels: "2024-03-01", "2024.3", "2024년 3월", "24년 3월", "3월", "2024 1/4",
 * "2024년 1분기", "Q1 2024", "2024 상반기", "2024년", "Mar 2024".
 */
object TimeParser {

    private val WEEKDAY_SUFFIX = Regex("\\s*\\((월|화|수|목|금|토|일)(요일)?\\)$")

    private val DAY_ISO = Regex("^(\\d{4})\\s*[-./]\\s*(\\d{1,2})\\s*[-./]\\s*(\\d{1,2})\\.?(?:[ T]\\d{1,2}:\\d{2}(?::\\d{2}(?:\\.\\d+)?)?(?:Z|[+-]\\d{2}:?\\d{2})?)?$")
    private val DAY_KO = Regex("^(\\d{4})\\s*년\\s*(\\d{1,2})\\s*월\\s*(\\d{1,2})\\s*일$")
    private val DAY_SHORT = Regex("^'?(\\d{2})\\s*[-./]\\s*(\\d{1,2})\\s*[-./]\\s*(\\d{1,2})\\.?$")
    private val DAY_COMPACT = Regex("^(\\d{4})(0[1-9]|1[0-2])(0[1-9]|[12]\\d|3[01])$")
    private val MONTH_ISO = Regex("^(\\d{4})\\s*[-./]\\s*(\\d{1,2})\\.?$")
    private val MONTH_KO = Regex("^(\\d{4})\\s*년\\s*(\\d{1,2})\\s*월$")
    private val MONTH_KO_SHORT = Regex("^'?(\\d{2})\\s*년\\s*(\\d{1,2})\\s*월$")
    private val MONTH_APOS = Regex("^'(\\d{2})\\s*[-./]\\s*(\\d{1,2})$")
    private val MONTH_ONLY = Regex("^(\\d{1,2})\\s*월$")
    private val YEAR_KO = Regex("^(\\d{4})\\s*년(?:도)?$")
    private val YEAR_BARE = Regex("^(\\d{4})$")
    private val QUARTER_A = Regex("^(?:(\\d{4})\\s*년?\\s*[-./]?\\s*)?([1-4])\\s*(/\\s*4\\s*분기|/\\s*4|분기|Q|q)$")
    private val QUARTER_B = Regex("^(?:(\\d{4})\\s*[-./]?\\s*)?[Qq]([1-4])$")
    private val QUARTER_C = Regex("^[Qq]([1-4])\\s*[-./,']?\\s*(\\d{4})$")
    private val QUARTER_KO_SHORT = Regex("^'?(\\d{2})\\s*년\\s*([1-4])\\s*분기$")
    private val HALF = Regex("^(?:(\\d{4})\\s*년?\\s*)?(상반기|하반기|상반|하반|전반기|후반기|[12]\\s*H|H\\s*[12])$")
    private val MONTH_EN = Regex("^(jan|feb|mar|apr|may|jun|jul|aug|sep|sept|oct|nov|dec)[a-z]*\\.?(?:\\s*[-',]?\\s*(\\d{4}|\\d{2}))?$", RegexOption.IGNORE_CASE)
    private val MONTH_EN_REV = Regex("^(\\d{4})\\s*[-/]?\\s*(jan|feb|mar|apr|may|jun|jul|aug|sep|sept|oct|nov|dec)[a-z]*\\.?$", RegexOption.IGNORE_CASE)

    private val EN_MONTHS = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")

    /**
     * @param allowBareYear accept "2024" alone as a year (only when the column clearly holds years).
     * @param allowCompact accept "20240301" as a date.
     */
    fun parse(input: String, allowBareYear: Boolean = false, allowCompact: Boolean = false): TimePoint? {
        var s = input.trim().replace(' ', ' ')
        if (s.isEmpty() || s.length > 40) return null
        s = s.replace(WEEKDAY_SUFFIX, "").trim()
        s = s.replace(Regex("\\s+"), " ")

        DAY_ISO.matchEntire(s)?.let { m -> return day(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt()) }
        DAY_KO.matchEntire(s)?.let { m -> return day(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt()) }
        DAY_SHORT.matchEntire(s)?.let { m ->
            val y = 2000 + m.groupValues[1].toInt()
            return day(y, m.groupValues[2].toInt(), m.groupValues[3].toInt())
        }
        if (allowCompact) DAY_COMPACT.matchEntire(s)?.let { m -> return day(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt()) }

        MONTH_KO.matchEntire(s)?.let { m -> return month(m.groupValues[1].toInt(), m.groupValues[2].toInt()) }
        MONTH_KO_SHORT.matchEntire(s)?.let { m -> return month(2000 + m.groupValues[1].toInt(), m.groupValues[2].toInt()) }
        MONTH_APOS.matchEntire(s)?.let { m -> return month(2000 + m.groupValues[1].toInt(), m.groupValues[2].toInt()) }
        MONTH_ISO.matchEntire(s)?.let { m ->
            val y = m.groupValues[1].toInt()
            val mo = m.groupValues[2]
            // "2024.10" is a month; plain "2024.5" could also be a decimal number, so the
            // column-level reader double-checks before trusting it.
            return month(y, mo.toInt())
        }
        MONTH_ONLY.matchEntire(s)?.let { m ->
            val mo = m.groupValues[1].toInt()
            return if (mo in 1..12) TimePoint(Granularity.MONTH, null, mo) else null
        }

        QUARTER_KO_SHORT.matchEntire(s)?.let { m -> return TimePoint(Granularity.QUARTER, 2000 + m.groupValues[1].toInt(), m.groupValues[2].toInt()) }
        QUARTER_A.matchEntire(s)?.let { m ->
            val y = m.groupValues[1].takeIf { it.isNotEmpty() }?.toInt()
            if (y != null && !validYear(y)) return null
            // A bare "1/4" without a year is more likely a date or a fraction.
            if (y == null && m.groupValues[3].replace(" ", "") == "/4") return null
            return TimePoint(Granularity.QUARTER, y, m.groupValues[2].toInt())
        }
        QUARTER_B.matchEntire(s)?.let { m ->
            val y = m.groupValues[1].takeIf { it.isNotEmpty() }?.toInt()
            if (y != null && !validYear(y)) return null
            return TimePoint(Granularity.QUARTER, y, m.groupValues[2].toInt())
        }
        QUARTER_C.matchEntire(s)?.let { m ->
            val y = m.groupValues[2].toInt()
            if (!validYear(y)) return null
            return TimePoint(Granularity.QUARTER, y, m.groupValues[1].toInt())
        }
        HALF.matchEntire(s)?.let { m ->
            val y = m.groupValues[1].takeIf { it.isNotEmpty() }?.toInt()
            if (y != null && !validYear(y)) return null
            val w = m.groupValues[2].replace(" ", "")
            val h = if (w.startsWith("상") || w.startsWith("전") || w == "1H" || w == "H1") 1 else 2
            return TimePoint(Granularity.HALF, y, h)
        }
        MONTH_EN.matchEntire(s)?.let { m ->
            val mo = EN_MONTHS.indexOf(m.groupValues[1].lowercase().take(3)) + 1
            val ys = m.groupValues[2]
            val y = when {
                ys.isEmpty() -> null
                ys.length == 2 -> 2000 + ys.toInt()
                else -> ys.toInt()
            }
            if (y != null && !validYear(y)) return null
            return TimePoint(Granularity.MONTH, y, mo)
        }
        MONTH_EN_REV.matchEntire(s)?.let { m ->
            val y = m.groupValues[1].toInt()
            if (!validYear(y)) return null
            val mo = EN_MONTHS.indexOf(m.groupValues[2].lowercase().take(3)) + 1
            return TimePoint(Granularity.MONTH, y, mo)
        }
        YEAR_KO.matchEntire(s)?.let { m ->
            val y = m.groupValues[1].toInt()
            return if (validYear(y)) TimePoint(Granularity.YEAR, y) else null
        }
        if (allowBareYear) YEAR_BARE.matchEntire(s)?.let { m ->
            val y = m.groupValues[1].toInt()
            return if (validYear(y)) TimePoint(Granularity.YEAR, y) else null
        }
        return null
    }

    fun validYear(y: Int) = y in 1900..2100

    private fun month(y: Int, m: Int): TimePoint? =
        if (validYear(y) && m in 1..12) TimePoint(Granularity.MONTH, y, m) else null

    private fun day(y: Int, m: Int, d: Int): TimePoint? {
        if (!validYear(y) || m !in 1..12 || d !in 1..31) return null
        val maxDay = when (m) {
            2 -> if ((y % 4 == 0 && y % 100 != 0) || y % 400 == 0) 29 else 28
            4, 6, 9, 11 -> 30
            else -> 31
        }
        if (d > maxDay) return null
        return TimePoint(Granularity.DAY, y, m, d)
    }

    /** Excel serial date (1900 system) to a day. */
    fun fromExcelSerial(serial: Double, date1904: Boolean = false): TimePoint? {
        if (serial.isNaN() || serial < 1 || serial > 2958465) return null
        var days = kotlin.math.floor(serial).toLong()
        if (date1904) days += 1462
        // Excel thinks 1900 was a leap year; serial 60 is the fake 1900-02-29.
        if (days >= 60) days -= 1
        val epoch = java.time.LocalDate.of(1899, 12, 31)
        val d = epoch.plusDays(days)
        return TimePoint(Granularity.DAY, d.year, d.monthValue, d.dayOfMonth)
    }
}
