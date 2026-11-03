package com.graphro.core.model

/** What kind of values a column holds. */
enum class ColumnType { TEXT, NUMBER, TIME }

/** How fine a time value is. */
enum class Granularity { YEAR, HALF, QUARTER, MONTH, DAY }

/**
 * A point in time as people write it in tables: "2024", "2024년 1분기", "3월", "2024-03-01".
 * [year] is null when the source only gave a month ("3월").
 * [sub] is the half (1-2), quarter (1-4) or month (1-12) depending on [granularity].
 */
data class TimePoint(
    val granularity: Granularity,
    val year: Int?,
    val sub: Int = 0,
    val day: Int = 0,
) : Comparable<TimePoint> {

    /** First month covered by this point, used for ordering. */
    val startMonth: Int
        get() = when (granularity) {
            Granularity.YEAR -> 1
            Granularity.HALF -> (sub - 1) * 6 + 1
            Granularity.QUARTER -> (sub - 1) * 3 + 1
            Granularity.MONTH, Granularity.DAY -> sub
        }

    val sortKey: Long
        get() = (year ?: 0).toLong() * 10_000L + startMonth * 100L + day

    override fun compareTo(other: TimePoint): Int = sortKey.compareTo(other.sortKey)

    /** Label for tables and long text, e.g. "2024년 3월". */
    fun fullLabel(): String {
        val y = year
        return when (granularity) {
            Granularity.YEAR -> "${y}년"
            Granularity.HALF -> (if (y != null) "${y}년 " else "") + (if (sub == 1) "상반기" else "하반기")
            Granularity.QUARTER -> (if (y != null) "${y}년 " else "") + "${sub}분기"
            Granularity.MONTH -> (if (y != null) "${y}년 " else "") + "${sub}월"
            Granularity.DAY -> (if (y != null) "${y}년 " else "") + "${sub}월 ${day}일"
        }
    }

    /** Short label for chart axes. [withYear] adds a compact year. */
    fun axisLabel(withYear: Boolean): String {
        val y = year
        val yy = if (y != null) (y % 100).toString().padStart(2, '0') else ""
        return when (granularity) {
            Granularity.YEAR -> "$y"
            Granularity.HALF -> (if (withYear && y != null) "'$yy " else "") + (if (sub == 1) "상반기" else "하반기")
            Granularity.QUARTER -> if (withYear && y != null) "'$yy Q$sub" else "${sub}분기"
            Granularity.MONTH -> if (withYear && y != null) "$yy.$sub" else "${sub}월"
            Granularity.DAY -> if (withYear && y != null) "$yy.$sub.$day" else "$sub.$day"
        }
    }

    /** Word used for "compared to the previous ..." in captions. */
    fun previousWord(): String = when (granularity) {
        Granularity.YEAR -> "전년"
        Granularity.HALF -> "직전 반기"
        Granularity.QUARTER -> "직전 분기"
        Granularity.MONTH -> "전월"
        Granularity.DAY -> "전날"
    }
}

sealed interface Value {
    data object Empty : Value
    data class Num(val v: Double) : Value
    data class Text(val s: String) : Value
    data class Time(val t: TimePoint) : Value
}

/**
 * One cell after reading. [raw] is exactly what the source said. [fix] explains, in plain
 * Korean, how the app changed the meaning of the raw text (null when nothing notable happened).
 */
data class Cell(val raw: String, val value: Value, val fix: String? = null)

/** A display scale for big numbers. */
enum class Scale(val factor: Double, val label: String) {
    ONE(1.0, ""),
    THOUSAND(1e3, "천"),
    TEN_THOUSAND(1e4, "만"),
    MILLION(1e6, "백만"),
    HUNDRED_MILLION(1e8, "억"),
    BILLION(1e9, "십억"),
    TRILLION(1e12, "조");

    companion object {
        fun ofFactor(f: Double): Scale? = entries.firstOrNull { kotlin.math.abs(it.factor - f) < 1e-6 * it.factor }
    }
}

data class Column(
    val name: String,
    val type: ColumnType,
    val cells: List<Cell>,
    /** Base unit such as "원", "명", "%". Numbers in cells are stored in this base unit. */
    val unit: String? = null,
    /** Scale used to show numbers, e.g. TEN_THOUSAND shows 3,200,000 as 320 (만 원). */
    val scale: Scale = Scale.ONE,
    /** True when the column looks like a total of the others ("합계"). */
    val isTotal: Boolean = false,
) {
    /** "만 원", "명", "%" or "" */
    val unitLabel: String
        get() {
            val u = unit ?: ""
            val s = scale.label
            return when {
                s.isEmpty() -> u
                u.isEmpty() -> s
                else -> "$s $u"
            }
        }

    val displayName: String get() = if (unitLabel.isEmpty()) name else "$name ($unitLabel)"

    fun number(row: Int): Double? = (cells.getOrNull(row)?.value as? Value.Num)?.v

    /** Number in display scale. */
    fun scaled(row: Int): Double? = number(row)?.div(scale.factor)
}

/**
 * The cleaned table. [excludedRows] maps row index to the reason it is left out of charts
 * (for example a "합계" row). [report] lists what the reader did, for the review screen.
 */
data class DataTable(
    val title: String?,
    val columns: List<Column>,
    val excludedRows: Map<Int, String> = emptyMap(),
    val notes: List<String> = emptyList(),
    val report: List<String> = emptyList(),
) {
    val rowCount: Int get() = columns.firstOrNull()?.cells?.size ?: 0
    val numberColumns: List<Int> get() = columns.indices.filter { columns[it].type == ColumnType.NUMBER }
    val fixCount: Int get() = columns.sumOf { c -> c.cells.count { it.fix != null } }

    fun fixes(): List<FixRef> = buildList {
        for (r in 0 until rowCount) for ((ci, c) in columns.withIndex()) {
            val f = c.cells[r].fix ?: continue
            add(FixRef(r, ci, f))
        }
    }
}

data class FixRef(val row: Int, val column: Int, val message: String)
