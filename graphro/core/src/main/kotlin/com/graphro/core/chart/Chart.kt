package com.graphro.core.chart

import com.graphro.core.model.ColumnType
import com.graphro.core.model.DataTable
import com.graphro.core.model.Granularity
import com.graphro.core.model.Scale
import com.graphro.core.model.TimePoint
import com.graphro.core.model.Value
import com.graphro.core.text.Format
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

enum class ChartType(val label: String, val short: String, val code: String) {
    LINE("선 그래프", "선", "LINE"),
    BAR("막대 그래프", "막대", "BAR"),
    STACKED("누적 막대", "누적", "STACK"),
    HBAR("가로 막대", "가로", "H-BAR"),
    AREA("영역 그래프", "영역", "AREA"),
    DONUT("도넛 그래프", "도넛", "DONUT");

    val hasValueAxis: Boolean get() = this != DONUT
    val barLike: Boolean get() = this == BAR || this == STACKED || this == HBAR
}

enum class AxisMode { AUTO, ZERO, CUSTOM }
enum class HighlightMode { NONE, MAX, CHANGE, MANUAL }
enum class SortMode { NONE, DESC, ASC }

object Colors {
    const val INK = 0xFF121212.toInt()
    const val GRAPHITE = 0xFF2E2E33.toInt()
    const val FOG = 0xFFDADADC.toInt()
    const val MIST = 0xFFF4F4F1.toInt()
    const val LINE = 0xFFECECE9.toInt()
    const val SUB = 0xFF6E6E75.toInt()
    const val BODY = 0xFF4A4A50.toInt()
    const val SIGNAL = 0xFFF04E23.toInt()
    const val SIGNAL_TEXT = 0xFFC33A15.toInt()
    const val INDIGO = 0xFF2C3E9E.toInt()
    const val PASS = 0xFF0F7A57.toInt()

    val HIGHLIGHTS = listOf(SIGNAL, INDIGO, INK)

    /** Series colors for multi-series charts: distinct in lightness as well as hue. */
    val SERIES = listOf(GRAPHITE, SIGNAL, INDIGO, 0xFF9A9AA0.toInt(), 0xFFE8A33D.toInt(), 0xFF5AA9A0.toInt())

    /** Shades for donut slices when nothing is highlighted. */
    val SHADES = listOf(GRAPHITE, 0xFF55555C.toInt(), 0xFF7D7D84.toInt(), 0xFFA5A5AB.toInt(), 0xFFC4C4C8.toInt(), 0xFFDADADC.toInt())
}

data class ChartSpec(
    val type: ChartType,
    val title: String,
    /** Column used for categories; -1 means row numbers. */
    val categoryCol: Int,
    val seriesCols: List<Int>,
    val highlightMode: HighlightMode = HighlightMode.MAX,
    /** Source row index used when [highlightMode] is MANUAL. */
    val highlightRow: Int = -1,
    val highlightColor: Int = Colors.SIGNAL,
    val axisMode: AxisMode = AxisMode.AUTO,
    val axisMin: Double? = null,
    val axisMax: Double? = null,
    val showValues: Boolean = false,
    val showCaption: Boolean = true,
    val customCaption: String? = null,
    val sort: SortMode = SortMode.NONE,
    /** Target line in display units. */
    val target: Double? = null,
    val showAverage: Boolean = false,
    val note: String? = null,
    val source: String? = null,
)

data class SeriesData(val name: String, val column: Int, val values: List<Double?>)

data class Axis(val min: Double, val max: Double, val step: Double, val ticks: List<Double>, val decimals: Int)

data class ChartData(
    val categories: List<String>,
    val fullCategories: List<String>,
    /** Source row for each category, so highlights survive sorting. */
    val rows: List<Int>,
    val series: List<SeriesData>,
    val unit: String?,
    val scale: Scale,
    val unitLabel: String,
    val timeGranularity: Granularity?,
    val timePoints: List<TimePoint?>,
    val decimals: Int,
    /** Index into [categories] of the highlighted item, or -1. */
    val highlight: Int,
    val axis: Axis?,
    val mixedUnits: Boolean,
) {
    val isTime: Boolean get() = timeGranularity != null
    val size: Int get() = categories.size
    val first: SeriesData? get() = series.firstOrNull()

    fun format(v: Double): String = Format.fixed(v, decimals, trim = false)
    fun withUnit(v: Double): String = Format.withUnit(v, scale.label, unit, decimals)

    companion object {
        fun build(table: DataTable, spec: ChartSpec): ChartData {
            val catCol = table.columns.getOrNull(spec.categoryCol)
            val seriesCols = spec.seriesCols.filter { table.columns.getOrNull(it)?.type == ColumnType.NUMBER }
                .ifEmpty { table.numberColumns.filterNot { table.columns[it].isTotal }.take(1) }
            val firstCol = seriesCols.firstOrNull()?.let { table.columns[it] }
            val scale = firstCol?.scale ?: Scale.ONE
            val unit = firstCol?.unit
            val mixedUnits = seriesCols.map { table.columns[it].unit to table.columns[it].scale }.toSet().size > 1

            var rows = (0 until table.rowCount).filter { it !in table.excludedRows }
                .filter { r -> seriesCols.any { table.columns[it].cells[r].value is Value.Num } }

            val isTime = catCol?.type == ColumnType.TIME
            val tp: (Int) -> TimePoint? = { r -> (catCol?.cells?.getOrNull(r)?.value as? Value.Time)?.t }
            if (isTime && spec.sort == SortMode.NONE) rows = rows.sortedWith(compareBy(nullsLast()) { tp(it) })
            val sortKey: (Int) -> Double = { r -> seriesCols.firstOrNull()?.let { table.columns[it].number(r) } ?: Double.NaN }
            when (spec.sort) {
                SortMode.DESC -> rows = rows.sortedByDescending { sortKey(it) }
                SortMode.ASC -> rows = rows.sortedBy { sortKey(it) }
                SortMode.NONE -> {}
            }

            val points = rows.map { tp(it) }
            val gran = if (isTime) points.filterNotNull().map { it.granularity }.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key else null
            val years = points.mapNotNull { it?.year }.toSet()
            val withYear = years.size > 1
            val categories = rows.map { r ->
                when {
                    catCol == null -> "${r + 1}"
                    isTime -> tp(r)?.axisLabel(withYear) ?: catCol.cells[r].raw
                    else -> catCol.cells[r].raw.ifEmpty { "${r + 1}" }
                }
            }
            val full = rows.map { r ->
                when {
                    catCol == null -> "${r + 1}번째"
                    isTime -> tp(r)?.fullLabel() ?: catCol.cells[r].raw
                    else -> catCol.cells[r].raw.ifEmpty { "${r + 1}번째" }
                }
            }
            val series = seriesCols.map { c ->
                val col = table.columns[c]
                SeriesData(col.name, c, rows.map { r -> col.number(r)?.div(scale.factor) })
            }
            val all = series.flatMap { it.values }.filterNotNull()
            val decimals = Format.decimalsFor(all)

            val first = series.firstOrNull()?.values ?: emptyList()
            val highlight = when (spec.highlightMode) {
                HighlightMode.NONE -> -1
                HighlightMode.MAX -> first.withIndex().filter { it.value != null }.maxByOrNull { it.value!! }?.index ?: -1
                HighlightMode.CHANGE -> {
                    if (first.size < 2) -1 else {
                        var best = -1; var bestAbs = -1.0
                        for (i in 1 until first.size) {
                            val a = first[i - 1]; val b = first[i]
                            if (a == null || b == null) continue
                            val d = abs(b - a)
                            if (d > bestAbs) { bestAbs = d; best = i }
                        }
                        best
                    }
                }
                HighlightMode.MANUAL -> rows.indexOf(spec.highlightRow)
            }

            val axis = if (spec.type.hasValueAxis) axisFor(spec, series) else null

            return ChartData(
                categories = categories, fullCategories = full, rows = rows, series = series, unit = unit,
                scale = scale, unitLabel = firstCol?.unitLabel ?: "", timeGranularity = gran, timePoints = points,
                decimals = decimals, highlight = highlight, axis = axis, mixedUnits = mixedUnits,
            )
        }

        fun dataRange(spec: ChartSpec, series: List<SeriesData>): Pair<Double, Double> {
            if (series.isEmpty()) return 0.0 to 1.0
            if (spec.type == ChartType.STACKED || (spec.type == ChartType.AREA && series.size > 1)) {
                val n = series.maxOf { it.values.size }
                var lo = 0.0; var hi = 0.0
                for (i in 0 until n) {
                    var pos = 0.0; var neg = 0.0
                    series.forEach { s -> val v = s.values.getOrNull(i) ?: 0.0; if (v >= 0) pos += v else neg += v }
                    hi = maxOf(hi, pos); lo = minOf(lo, neg)
                }
                return lo to hi
            }
            val all = series.flatMap { it.values }.filterNotNull()
            if (all.isEmpty()) return 0.0 to 1.0
            return all.min() to all.max()
        }

        fun axisFor(spec: ChartSpec, series: List<SeriesData>): Axis {
            var (lo, hi) = dataRange(spec, series)
            spec.target?.let { lo = minOf(lo, it); hi = maxOf(hi, it) }
            val mustZero = spec.type.barLike || spec.type == ChartType.AREA
            when (spec.axisMode) {
                AxisMode.ZERO -> { lo = minOf(lo, 0.0); hi = maxOf(hi, 0.0) }
                AxisMode.CUSTOM -> {
                    spec.axisMin?.let { lo = it }
                    spec.axisMax?.let { hi = it }
                    if (hi <= lo) hi = lo + 1
                    return Ticks.fixed(lo, hi)
                }
                AxisMode.AUTO -> {
                    if (mustZero) { lo = minOf(lo, 0.0); hi = maxOf(hi, 0.0) }
                    else if (lo >= 0 && hi > 0) {
                        // Lines start at zero unless the changes are tiny relative to the level.
                        if ((hi - lo) / hi >= 0.15) lo = 0.0
                    } else if (hi <= 0 && lo < 0) {
                        if ((hi - lo) / abs(lo) >= 0.15) hi = 0.0
                    }
                }
            }
            return Ticks.nice(lo, hi)
        }
    }
}

object Ticks {
    private fun niceNum(range: Double, round: Boolean): Double {
        if (range <= 0) return 1.0
        val exp = floor(log10(range))
        val f = range / 10.0.pow(exp)
        val nf = if (round) {
            when { f < 1.5 -> 1.0; f < 3 -> 2.0; f < 7 -> 5.0; else -> 10.0 }
        } else {
            when { f <= 1 -> 1.0; f <= 2 -> 2.0; f <= 5 -> 5.0; else -> 10.0 }
        }
        return nf * 10.0.pow(exp)
    }

    fun nice(lo0: Double, hi0: Double, maxTicks: Int = 5): Axis {
        var lo = lo0; var hi = hi0
        if (lo == hi) { if (lo == 0.0) hi = 1.0 else { val pad = abs(lo) * 0.1; lo -= pad; hi += pad } }
        val range = niceNum(hi - lo, false)
        val step = niceNum(range / (maxTicks - 1), true)
        val nMin = floor(lo / step) * step
        val nMax = ceil(hi / step) * step
        val ticks = ArrayList<Double>()
        var v = nMin
        var guard = 0
        while (v <= nMax + step * 0.5 && guard++ < 50) { ticks.add(if (abs(v) < step * 1e-9) 0.0 else v); v += step }
        return Axis(nMin, nMax, step, ticks, decimalsForStep(step))
    }

    fun fixed(lo: Double, hi: Double): Axis {
        val base = nice(lo, hi)
        val ticks = base.ticks.filter { it >= lo - 1e-9 && it <= hi + 1e-9 }.toMutableList()
        if (ticks.isEmpty() || ticks.first() > lo + 1e-9) ticks.add(0, lo)
        if (ticks.last() < hi - 1e-9) ticks.add(hi)
        return Axis(lo, hi, base.step, ticks, maxOf(base.decimals, Format.decimalsFor(listOf(lo, hi))))
    }

    fun decimalsForStep(step: Double): Int = when {
        step >= 1 -> 0
        step >= 0.1 -> 1
        step >= 0.01 -> 2
        else -> 3
    }
}
