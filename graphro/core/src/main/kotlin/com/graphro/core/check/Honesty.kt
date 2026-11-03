package com.graphro.core.check

import com.graphro.core.chart.AxisMode
import com.graphro.core.chart.ChartData
import com.graphro.core.chart.ChartSpec
import com.graphro.core.chart.ChartType
import com.graphro.core.chart.SortMode
import com.graphro.core.model.ColumnType
import com.graphro.core.model.DataTable
import com.graphro.core.text.Format
import com.graphro.core.text.Josa

enum class Severity { FIX, NOTE }

data class Issue(
    val severity: Severity,
    val code: String,
    val title: String,
    val detail: String,
    val fixLabel: String? = null,
    val fix: ((ChartSpec) -> ChartSpec)? = null,
    /** For the axis issue: values to draw the "now vs fixed" preview. */
    val exaggeration: Pair<Double, Double>? = null,
)

data class CheckResult(val issues: List<Issue>, val passed: List<String>) {
    val fixCount get() = issues.count { it.severity == Severity.FIX }
    val noteCount get() = issues.count { it.severity == Severity.NOTE }
}

/** Rules that catch charts which mislead, plus notes on what the chart can and cannot say. */
object Honesty {

    fun check(table: DataTable, spec: ChartSpec, d: ChartData): CheckResult {
        val issues = ArrayList<Issue>()
        val passed = ArrayList<String>()
        val vals = d.first?.values?.filterNotNull() ?: emptyList()
        val n = d.size
        val catType = table.columns.getOrNull(spec.categoryCol)?.type
        val axisWord = if (spec.type == ChartType.HBAR) "가로축" else "세로축"

        // 1. Bars/areas must start at zero.
        val axis = d.axis
        if (axis != null && (spec.type.barLike || spec.type == ChartType.AREA)) {
            val pos = vals.filter { it > 0 }
            if (axis.min > 0 && pos.size >= 2) {
                val mx = pos.max(); val mn = pos.min()
                val real = mx / mn
                val visual = if (mn > axis.min) (mx - axis.min) / (mn - axis.min) else Double.POSITIVE_INFINITY
                val shown = if (visual.isInfinite()) "끝없이" else "${fmtRatio(visual)}배로"
                issues += Issue(
                    Severity.FIX, "axis_zero",
                    "${Josa.iGa(axisWord)} ${Format.plain(axis.min)}부터 시작해요",
                    "가장 큰 값은 가장 작은 값의 ${fmtRatio(real)}배인데, 막대로는 $shown 보여요.",
                    "0부터 시작", { it.copy(axisMode = AxisMode.ZERO, axisMin = null, axisMax = null) },
                    exaggeration = real to visual,
                )
            } else passed += "막대가 0에서 시작"
            if (axis.max < (vals.maxOrNull() ?: 0.0) - 1e-9) {
                issues += Issue(Severity.FIX, "axis_clip", "일부 값이 축 범위를 넘어서 잘려요", "축의 최댓값을 데이터에 맞게 늘려야 해요.",
                    "자동 범위로", { it.copy(axisMode = AxisMode.AUTO, axisMin = null, axisMax = null) })
            }
        }
        if (axis != null && spec.type == ChartType.LINE && axis.min > 0 && vals.isNotEmpty()) {
            val mx = vals.max(); val mn = vals.min()
            if (mn > 0 && (mx - mn) / mx < 0.15) {
                issues += Issue(Severity.NOTE, "line_zoom", "변화가 실제보다 크게 보여요",
                    "값의 차이는 ${Format.percent((mx - mn) / mn * 100)}%뿐인데, 축을 좁혀서 크게 오르내리는 것처럼 보여요. 선 그래프에선 괜찮지만 해석할 때 주의하세요.",
                    "0부터 보기", { it.copy(axisMode = AxisMode.ZERO) })
            }
        }

        // 2. Donut rules
        if (spec.type == ChartType.DONUT) {
            if (vals.any { it < 0 }) issues += Issue(Severity.FIX, "donut_neg", "음수는 도넛으로 그릴 수 없어요", "음수 값이 빠진 채 그려져서 비율이 틀려져요.",
                "막대로 바꾸기", { it.copy(type = ChartType.BAR) })
            if (n > 6) issues += Issue(Severity.FIX, "donut_many", "조각이 ${n}개라 구분이 어려워요", "도넛은 5~6조각까지가 읽기 좋아요. 가로 막대로 바꾸면 크기를 정확히 비교할 수 있어요.",
                "가로 막대로", { it.copy(type = ChartType.HBAR, sort = SortMode.DESC) })
            else passed += "조각 수 적절"
            if (catType == ColumnType.TIME) issues += Issue(Severity.FIX, "donut_time", "기간 데이터를 도넛으로 그렸어요", "도넛은 전체 중 몫을 보여줘요. 기간의 흐름은 선 그래프가 맞아요.",
                "선 그래프로", { it.copy(type = ChartType.LINE) })
            if (d.unit == "%") {
                val sum = vals.sum()
                if (sum !in 98.0..102.0) issues += Issue(Severity.FIX, "donut_sum", "비율의 합이 100%가 아니에요",
                    "합계가 ${Format.plain(sum)}%예요. 중복 응답이거나 빠진 항목이 있으면 도넛은 오해를 줄 수 있어요.",
                    "가로 막대로", { it.copy(type = ChartType.HBAR, sort = SortMode.DESC) })
            }
            if (spec.seriesCols.size > 1) issues += Issue(Severity.NOTE, "donut_multi", "첫 번째 값만 그렸어요", "도넛은 한 가지 값만 보여줄 수 있어요.")
        }

        // 3. Lines need an order.
        if ((spec.type == ChartType.LINE || spec.type == ChartType.AREA) && catType != ColumnType.TIME && n > 1) {
            issues += Issue(Severity.FIX, "line_unordered", "순서 없는 항목을 선으로 이었어요", "선은 흐름을 뜻해요. 항목끼리 비교할 땐 막대가 맞아요.",
                "막대로 바꾸기", { it.copy(type = ChartType.BAR) })
        }
        if (catType == ColumnType.TIME && spec.sort != SortMode.NONE) {
            issues += Issue(Severity.FIX, "time_sorted", "기간을 값 크기 순으로 정렬했어요", "기간은 시간 순서대로 둬야 흐름이 보여요.",
                "시간 순으로", { it.copy(sort = SortMode.NONE) })
        } else if (catType == ColumnType.TIME) passed += "기간 순서"

        // 4. Stacked with negatives
        if (spec.type == ChartType.STACKED && d.series.any { s -> s.values.any { (it ?: 0.0) < 0 } }) {
            issues += Issue(Severity.FIX, "stack_neg", "음수가 있는 값을 쌓았어요", "음수와 양수를 쌓으면 합계를 읽기 어려워요.",
                "나란히 막대로", { it.copy(type = ChartType.BAR) })
        }

        // 5. Units
        if (d.mixedUnits) {
            issues += Issue(Severity.FIX, "mixed_units", "단위가 다른 값을 한 축에 그렸어요", "단위가 다르면 크기를 비교할 수 없어요. 같은 단위의 값만 남길게요.",
                "첫 번째 값만", { s -> s.copy(seriesCols = s.seriesCols.take(1)) })
        }
        if (d.unitLabel.isEmpty() && spec.type != ChartType.DONUT) {
            issues += Issue(Severity.NOTE, "no_unit", "단위를 알 수 없어요", "표에 단위가 없어요. 제목이나 출처에 단위를 적어 주면 오해가 줄어요.")
        } else if (d.unitLabel.isNotEmpty()) passed += "단위 표시"

        // 6. Wording and data notes
        if (catType == ColumnType.TIME && n in 2..5) {
            issues += Issue(Severity.NOTE, "short_period", "${n}개 시점은 추세라 하기엔 짧아요", "해석 문구에 ‘추세’ 대신 ‘늘었어요/줄었어요’라고 썼어요.")
        }
        val missing = d.series.sumOf { s -> s.values.count { it == null } }
        if (missing > 0) issues += Issue(Severity.NOTE, "missing", "빈 칸 ${missing}개는 비워 두고 그렸어요", "0으로 채우지 않았어요. 0으로 그리면 실제보다 작아 보여요.")
        if (table.excludedRows.isNotEmpty()) issues += Issue(Severity.NOTE, "excluded", "합계 행 ${table.excludedRows.size}개는 빼고 그렸어요", "합계를 함께 그리면 다른 항목이 작아 보여요. 데이터 화면에서 다시 넣을 수 있어요.")
        if (d.series.size > 4) issues += Issue(Severity.NOTE, "many_series", "값이 ${d.series.size}가지라 색 구분이 어려워요", "꼭 필요한 값만 남기거나 하나를 강조해 보세요.",
            "3개만 남기기", { s -> s.copy(seriesCols = s.seriesCols.take(3)) })
        else passed += "색 구분"
        if (spec.type.barLike && n > 30) issues += Issue(Severity.NOTE, "many_bars", "막대가 ${n}개라 빽빽해요",
            if (catType == ColumnType.TIME) "기간이 길면 선 그래프가 읽기 편해요." else "상위 항목만 남기거나 가로 막대로 보세요.",
            if (catType == ColumnType.TIME) "선 그래프로" else null, if (catType == ColumnType.TIME) { s -> s.copy(type = ChartType.LINE) } else null)
        else if (spec.type != ChartType.DONUT) passed += "항목 수 적절"
        passed += "3D 효과 없음"
        passed += "이중 축 없음"

        val order = issues.sortedBy { it.severity.ordinal }
        return CheckResult(order, passed.distinct())
    }

    private fun fmtRatio(r: Double): String = if (r >= 10) Format.fixed(r, 0) else Format.fixed(r, 1, trim = true)
}
