package com.graphro.core.check

import com.graphro.core.chart.AxisMode
import com.graphro.core.chart.ChartData
import com.graphro.core.chart.ChartSpec
import com.graphro.core.chart.ChartType
import com.graphro.core.chart.SortMode
import com.graphro.core.model.ColumnType
import com.graphro.core.model.DataTable
import com.graphro.core.text.Format
import com.graphro.core.text.I18n

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
        val hbar = spec.type == ChartType.HBAR

        // 1. Bars/areas must start at zero.
        val axis = d.axis
        if (axis != null && (spec.type.barLike || spec.type == ChartType.AREA)) {
            val pos = vals.filter { it > 0 }
            if (axis.min > 0 && pos.size >= 2) {
                val mx = pos.max(); val mn = pos.min()
                val real = mx / mn
                val visual = if (mn > axis.min) (mx - axis.min) / (mn - axis.min) else Double.POSITIVE_INFINITY
                val start = Format.plain(axis.min)
                val r = fmtRatio(real)
                val v = if (visual.isInfinite()) null else fmtRatio(visual)
                issues += Issue(
                    Severity.FIX, "axis_zero",
                    if (hbar) I18n.tr("가로축이 ${start}부터 시작해요", "The axis starts at $start, not 0", "横軸が${start}から始まっています")
                    else I18n.tr("세로축이 ${start}부터 시작해요", "The axis starts at $start, not 0", "縦軸が${start}から始まっています"),
                    if (v == null) I18n.tr("가장 큰 값은 가장 작은 값의 ${r}배인데, 가장 작은 막대가 보이지 않아요.", "The largest value is ${r}× the smallest, but the smallest bar disappears.", "最大値は最小値の${r}倍ですが、最も小さい棒が見えなくなっています。")
                    else I18n.tr("가장 큰 값은 가장 작은 값의 ${r}배인데, 막대로는 ${v}배로 보여요.", "The largest value is ${r}× the smallest, but the bars make it look ${v}×.", "最大値は最小値の${r}倍ですが、棒では${v}倍に見えます。"),
                    I18n.tr("0부터 시작", "Start at 0", "0から始める"), { it.copy(axisMode = AxisMode.ZERO, axisMin = null, axisMax = null) },
                    exaggeration = real to visual,
                )
            } else passed += I18n.tr("막대가 0에서 시작", "Bars start at 0", "棒が0から始まる")
            if (axis.max < (vals.maxOrNull() ?: 0.0) - 1e-9) {
                issues += Issue(Severity.FIX, "axis_clip",
                    I18n.tr("일부 값이 축 범위를 넘어서 잘려요", "Some values are cut off by the axis range", "一部の値が軸の範囲を超えて切れています"),
                    I18n.tr("축의 최댓값을 데이터에 맞게 늘려야 해요.", "Raise the axis maximum to fit the data.", "軸の最大値をデータに合わせて広げる必要があります。"),
                    I18n.tr("자동 범위로", "Auto range", "自動範囲にする"), { it.copy(axisMode = AxisMode.AUTO, axisMin = null, axisMax = null) })
            }
        }
        if (axis != null && spec.type == ChartType.LINE && axis.min > 0 && vals.isNotEmpty()) {
            val mx = vals.max(); val mn = vals.min()
            if (mn > 0 && (mx - mn) / mx < 0.15) {
                val pct = Format.percent((mx - mn) / mn * 100)
                issues += Issue(Severity.NOTE, "line_zoom",
                    I18n.tr("변화가 실제보다 크게 보여요", "Changes look bigger than they are", "変化が実際より大きく見えます"),
                    I18n.tr("값의 차이는 ${pct}%뿐인데, 축을 좁혀서 크게 오르내리는 것처럼 보여요. 선 그래프에선 괜찮지만 해석할 때 주의하세요.",
                        "Values differ by only $pct%, but the narrow axis makes them swing widely. Fine for a line chart, but keep it in mind.",
                        "値の差は${pct}%だけですが、軸を狭めているため大きく上下して見えます。折れ線では問題ありませんが、解釈に注意してください。"),
                    I18n.tr("0부터 보기", "Show from 0", "0から表示"), { it.copy(axisMode = AxisMode.ZERO) })
            }
        }

        // 2. Donut rules
        if (spec.type == ChartType.DONUT) {
            if (vals.any { it < 0 }) issues += Issue(Severity.FIX, "donut_neg",
                I18n.tr("음수는 도넛으로 그릴 수 없어요", "Negative values can't be drawn as a donut", "マイナス値はドーナツグラフで描けません"),
                I18n.tr("음수 값이 빠진 채 그려져서 비율이 틀려져요.", "Negative values get dropped, so the shares are wrong.", "マイナス値が抜けて描かれるため、割合が正しくなくなります。"),
                I18n.tr("막대로 바꾸기", "Switch to bars", "棒グラフにする"), { it.copy(type = ChartType.BAR) })
            if (n > 6) issues += Issue(Severity.FIX, "donut_many",
                I18n.tr("조각이 ${n}개라 구분이 어려워요", "$n slices are hard to tell apart", "${n}切れでは見分けにくいです"),
                I18n.tr("도넛은 5~6조각까지가 읽기 좋아요. 가로 막대로 바꾸면 크기를 정확히 비교할 수 있어요.", "Donuts read well up to 5–6 slices. Horizontal bars compare sizes precisely.", "ドーナツは5〜6切れまでが読みやすいです。横棒にすると大きさを正確に比べられます。"),
                I18n.tr("가로 막대로", "Use horizontal bars", "横棒にする"), { it.copy(type = ChartType.HBAR, sort = SortMode.DESC) })
            else passed += I18n.tr("조각 수 적절", "Slice count OK", "切れ数が適切")
            if (catType == ColumnType.TIME) issues += Issue(Severity.FIX, "donut_time",
                I18n.tr("기간 데이터를 도넛으로 그렸어요", "Periods are drawn as a donut", "期間データをドーナツで描いています"),
                I18n.tr("도넛은 전체 중 몫을 보여줘요. 기간의 흐름은 선 그래프가 맞아요.", "Donuts show parts of a whole. Change over time belongs in a line chart.", "ドーナツは全体に占める割合を示します。期間の流れは折れ線グラフが適しています。"),
                I18n.tr("선 그래프로", "Use a line chart", "折れ線にする"), { it.copy(type = ChartType.LINE) })
            if (d.unit == "%") {
                val sum = vals.sum()
                if (sum !in 98.0..102.0) issues += Issue(Severity.FIX, "donut_sum",
                    I18n.tr("비율의 합이 100%가 아니에요", "Shares don't add up to 100%", "割合の合計が100%ではありません"),
                    I18n.tr("합계가 ${Format.plain(sum)}%예요. 중복 응답이거나 빠진 항목이 있으면 도넛은 오해를 줄 수 있어요.",
                        "They add up to ${Format.plain(sum)}%. With multiple answers or missing items, a donut misleads.",
                        "合計は${Format.plain(sum)}%です。複数回答や抜けている項目があると、ドーナツは誤解を招きます。"),
                    I18n.tr("가로 막대로", "Use horizontal bars", "横棒にする"), { it.copy(type = ChartType.HBAR, sort = SortMode.DESC) })
            }
            if (spec.seriesCols.size > 1) issues += Issue(Severity.NOTE, "donut_multi",
                I18n.tr("첫 번째 값만 그렸어요", "Only the first series is drawn", "最初の系列だけを描いています"),
                I18n.tr("도넛은 한 가지 값만 보여줄 수 있어요.", "A donut can show only one series.", "ドーナツは1系列しか示せません。"))
        }

        // 3. Lines need an order.
        if ((spec.type == ChartType.LINE || spec.type == ChartType.AREA) && catType != ColumnType.TIME && n > 1) {
            issues += Issue(Severity.FIX, "line_unordered",
                I18n.tr("순서 없는 항목을 선으로 이었어요", "Unordered items are joined by a line", "順序のない項目を線で結んでいます"),
                I18n.tr("선은 흐름을 뜻해요. 항목끼리 비교할 땐 막대가 맞아요.", "A line implies a trend. Bars are right for comparing items.", "線は流れを意味します。項目同士の比較には棒グラフが適しています。"),
                I18n.tr("막대로 바꾸기", "Switch to bars", "棒グラフにする"), { it.copy(type = ChartType.BAR) })
        }
        if (catType == ColumnType.TIME && spec.sort != SortMode.NONE) {
            issues += Issue(Severity.FIX, "time_sorted",
                I18n.tr("기간을 값 크기 순으로 정렬했어요", "Periods are sorted by value", "期間を値の大きさ順に並べています"),
                I18n.tr("기간은 시간 순서대로 둬야 흐름이 보여요.", "Keep periods in time order so the trend shows.", "期間は時間順に並べると流れが見えます。"),
                I18n.tr("시간 순으로", "Time order", "時間順にする"), { it.copy(sort = SortMode.NONE) })
        } else if (catType == ColumnType.TIME) passed += I18n.tr("기간 순서", "Time order", "時間順")

        // 4. Stacked with negatives
        if (spec.type == ChartType.STACKED && d.series.any { s -> s.values.any { (it ?: 0.0) < 0 } }) {
            issues += Issue(Severity.FIX, "stack_neg",
                I18n.tr("음수가 있는 값을 쌓았어요", "Negative values are stacked", "マイナス値を積み上げています"),
                I18n.tr("음수와 양수를 쌓으면 합계를 읽기 어려워요.", "Stacking negatives with positives makes totals hard to read.", "正と負の値を積み上げると合計が読みにくくなります。"),
                I18n.tr("나란히 막대로", "Side-by-side bars", "並べた棒にする"), { it.copy(type = ChartType.BAR) })
        }

        // 5. Units
        if (d.mixedUnits) {
            issues += Issue(Severity.FIX, "mixed_units",
                I18n.tr("단위가 다른 값을 한 축에 그렸어요", "Values with different units share one axis", "単位の異なる値を1つの軸に描いています"),
                I18n.tr("단위가 다르면 크기를 비교할 수 없어요. 같은 단위의 값만 남길게요.", "Different units can't be compared by size. Keep one unit only.", "単位が違うと大きさを比べられません。同じ単位の値だけを残します。"),
                I18n.tr("첫 번째 값만", "Keep the first", "最初の値だけ"), { s -> s.copy(seriesCols = s.seriesCols.take(1)) })
        }
        if (d.unitLabel.isEmpty() && spec.type != ChartType.DONUT) {
            issues += Issue(Severity.NOTE, "no_unit",
                I18n.tr("단위를 알 수 없어요", "The unit is unknown", "単位が分かりません"),
                I18n.tr("표에 단위가 없어요. 제목이나 출처에 단위를 적어 주면 오해가 줄어요.", "The table has no unit. Adding one to the title or source avoids confusion.", "表に単位がありません。タイトルや出典に単位を書くと誤解が減ります。"))
        } else if (d.unitLabel.isNotEmpty()) passed += I18n.tr("단위 표시", "Unit shown", "単位の表示")

        // 6. Wording and data notes
        if (catType == ColumnType.TIME && n in 2..5) {
            issues += Issue(Severity.NOTE, "short_period",
                I18n.tr("${n}개 시점은 추세라 하기엔 짧아요", "$n points are too few to call it a trend", "${n}時点ではトレンドと呼ぶには短いです"),
                I18n.tr("해석 문구에 ‘추세’ 대신 ‘늘었어요/줄었어요’라고 썼어요.", "The caption says “rose/fell” rather than “trend”.", "説明文では「傾向」ではなく「増えた／減った」と書いています。"))
        }
        val missing = d.series.sumOf { s -> s.values.count { it == null } }
        if (missing > 0) issues += Issue(Severity.NOTE, "missing",
            I18n.tr("빈 칸 ${missing}개는 비워 두고 그렸어요", "$missing empty cell(s) are left as gaps", "空欄${missing}個は空けたまま描いています"),
            I18n.tr("0으로 채우지 않았어요. 0으로 그리면 실제보다 작아 보여요.", "They are not drawn as 0, which would look smaller than reality.", "0で埋めていません。0で描くと実際より小さく見えます。"))
        if (table.excludedRows.isNotEmpty()) issues += Issue(Severity.NOTE, "excluded",
            I18n.tr("합계 행 ${table.excludedRows.size}개는 빼고 그렸어요", "${table.excludedRows.size} total row(s) are left out", "合計行${table.excludedRows.size}行を除いて描いています"),
            I18n.tr("합계를 함께 그리면 다른 항목이 작아 보여요. 데이터 화면에서 다시 넣을 수 있어요.", "Drawing the total would make every other item look small. You can add it back on the data screen.", "合計を一緒に描くと他の項目が小さく見えます。データ画面で戻せます。"))
        if (d.series.size > 4) issues += Issue(Severity.NOTE, "many_series",
            I18n.tr("값이 ${d.series.size}가지라 색 구분이 어려워요", "${d.series.size} series are hard to tell apart by colour", "${d.series.size}系列あり、色の区別が難しいです"),
            I18n.tr("꼭 필요한 값만 남기거나 하나를 강조해 보세요.", "Keep only what matters, or highlight one.", "必要な値だけ残すか、1つを強調してみてください。"),
            I18n.tr("3개만 남기기", "Keep 3", "3つだけ残す"), { s -> s.copy(seriesCols = s.seriesCols.take(3)) })
        else passed += I18n.tr("색 구분", "Distinct colours", "色の区別")
        if (spec.type.barLike && n > 30) issues += Issue(Severity.NOTE, "many_bars",
            I18n.tr("막대가 ${n}개라 빽빽해요", "$n bars are crowded", "棒が${n}本あり、混み合っています"),
            if (catType == ColumnType.TIME) I18n.tr("기간이 길면 선 그래프가 읽기 편해요.", "For long periods a line chart is easier to read.", "期間が長いときは折れ線グラフが読みやすいです。")
            else I18n.tr("상위 항목만 남기거나 가로 막대로 보세요.", "Keep the top items or use horizontal bars.", "上位の項目だけ残すか、横棒で見てください。"),
            if (catType == ColumnType.TIME) I18n.tr("선 그래프로", "Use a line chart", "折れ線にする") else null,
            if (catType == ColumnType.TIME) { s -> s.copy(type = ChartType.LINE) } else null)
        else if (spec.type != ChartType.DONUT) passed += I18n.tr("항목 수 적절", "Item count OK", "項目数が適切")
        passed += I18n.tr("3D 효과 없음", "No 3D effects", "3D効果なし")
        passed += I18n.tr("이중 축 없음", "No dual axis", "二重軸なし")

        val order = issues.sortedBy { it.severity.ordinal }
        return CheckResult(order, passed.distinct())
    }

    private fun fmtRatio(r: Double): String = if (r >= 10) Format.fixed(r, 0) else Format.fixed(r, 1, trim = true)
}
