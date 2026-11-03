package com.graphro.core.chart

import com.graphro.core.model.ColumnType
import com.graphro.core.model.DataTable
import com.graphro.core.model.Granularity
import com.graphro.core.model.Value
import com.graphro.core.text.I18n
import com.graphro.core.text.Lang

enum class Fit(val tag: String) { BEST("BEST"), OK(""), NOT_FIT("NOT FIT") }

data class Recommendation(val type: ChartType, val fit: Fit, val reason: String)

/** Picks chart types from the shape of the data and explains each choice in plain Korean. */
object Recommender {

    private val SHARE_WORDS = Regex("(비율|비중|점유율|구성비|구성|분포|응답|퍼센트|割合|比率|構成比|シェア|内訳|回答|%|share|percent|breakdown|responses?)", RegexOption.IGNORE_CASE)

    fun categoryColumn(t: DataTable): Int {
        val time = t.columns.indexOfFirst { it.type == ColumnType.TIME }
        if (time >= 0) return time
        return t.columns.indexOfFirst { it.type == ColumnType.TEXT && it.cells.any { c -> c.value is Value.Text } }
    }

    fun defaultSeries(t: DataTable): List<Int> {
        val nums = t.numberColumns.filterNot { t.columns[it].isTotal }.ifEmpty { t.numberColumns }
        if (nums.isEmpty()) return emptyList()
        // Keep series that share the first one's unit so one axis stays honest.
        val first = t.columns[nums[0]]
        return nums.filter { t.columns[it].unit == first.unit && t.columns[it].scale == first.scale }.take(4)
    }

    private fun looksLikeShare(t: DataTable, series: List<Int>, cat: Int): Boolean {
        if (series.size != 1 || cat < 0 || t.columns[cat].type == ColumnType.TIME) return false
        val col = t.columns[series[0]]
        val vals = (0 until t.rowCount).filter { it !in t.excludedRows }.mapNotNull { col.number(it) }
        if (vals.isEmpty() || vals.any { it < 0 }) return false
        val sum = vals.sum()
        val pct = col.unit == "%" && sum in 95.0..105.0
        return pct || SHARE_WORDS.containsMatchIn(col.name) || SHARE_WORDS.containsMatchIn(t.title ?: "")
    }

    fun defaultSpec(t: DataTable): ChartSpec {
        val cat = categoryColumn(t)
        val series = defaultSeries(t)
        val recs = recommend(t, cat, series)
        val type = recs.firstOrNull { it.fit == Fit.BEST }?.type ?: recs.firstOrNull()?.type ?: ChartType.BAR
        val isTime = cat >= 0 && t.columns[cat].type == ColumnType.TIME
        val highlight = when {
            type == ChartType.STACKED -> HighlightMode.NONE
            isTime -> HighlightMode.CHANGE
            else -> HighlightMode.MAX
        }
        val sort = if (!isTime && (type == ChartType.HBAR || type == ChartType.BAR) && rowsIn(t) >= 5 && cat >= 0) SortMode.DESC else SortMode.NONE
        return ChartSpec(
            type = type,
            title = defaultTitle(t, cat, series),
            categoryCol = cat,
            seriesCols = series,
            highlightMode = highlight,
            sort = sort,
            showValues = type == ChartType.HBAR || (type.barLike && rowsIn(t) <= 8),
        )
    }

    private fun rowsIn(t: DataTable) = (0 until t.rowCount).count { it !in t.excludedRows }

    fun defaultTitle(t: DataTable, cat: Int, series: List<Int>): String {
        t.title?.let { if (it.length in 2..40 && !it.contains("단위") && !it.contains("単位")) return it }
        val s = series.firstOrNull()?.let { t.columns[it].name } ?: I18n.value()
        val seriesName = if (series.size > 1) series.joinToString("·") { t.columns[it].name }.take(24) else s
        if (cat < 0) return seriesName
        val c = t.columns[cat]
        if (c.type == ColumnType.TIME) {
            val g = c.cells.mapNotNull { (it.value as? Value.Time)?.t?.granularity }.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
            return when (I18n.lang) {
                Lang.EN -> {
                    val word = when (g) {
                        Granularity.YEAR -> "Yearly"; Granularity.HALF -> "Half-yearly"; Granularity.QUARTER -> "Quarterly"
                        Granularity.MONTH -> "Monthly"; Granularity.DAY -> "Daily"; null -> "Periodic"
                    }
                    "$word $seriesName"
                }
                Lang.JA -> {
                    val word = when (g) {
                        Granularity.YEAR -> "年別"; Granularity.HALF -> "半期別"; Granularity.QUARTER -> "四半期別"
                        Granularity.MONTH -> "月別"; Granularity.DAY -> "日別"; null -> "期間別"
                    }
                    "$word$seriesName"
                }
                Lang.KO -> {
                    val word = when (g) {
                        Granularity.YEAR -> "연도별"; Granularity.HALF -> "반기별"; Granularity.QUARTER -> "분기별"
                        Granularity.MONTH -> "월별"; Granularity.DAY -> "일별"; null -> "기간별"
                    }
                    "$word $seriesName"
                }
            }
        }
        val catName = c.name.takeIf { !I18n.isGenericName(it) }
        if (catName == null) return seriesName
        return I18n.tr("${catName}별 $seriesName", "$seriesName by $catName", "${catName}別$seriesName")
    }

    fun recommend(t: DataTable, cat: Int, series: List<Int>): List<Recommendation> {
        val n = rowsIn(t)
        val isTime = cat >= 0 && t.columns[cat].type == ColumnType.TIME
        val multi = series.size > 1
        val share = looksLikeShare(t, series, cat)
        val labels = if (cat >= 0) (0 until t.rowCount).filter { it !in t.excludedRows }.map { t.columns[cat].cells[it].raw } else emptyList()
        val longLabels = labels.isNotEmpty() && labels.map { it.length }.average() > (if (I18n.lang == Lang.EN) 9.0 else 5.5)
        val col = series.firstOrNull()?.let { t.columns[it] }
        val vals = if (col != null) (0 until t.rowCount).filter { it !in t.excludedRows }.mapNotNull { col.number(it) } else emptyList()
        val hasNeg = vals.any { it < 0 }
        val seriesName = col?.name ?: I18n.value()
        val k = series.size

        // A short story for the reason text: the biggest jump in a time series.
        var jumpText: String? = null
        if (isTime && vals.size >= 3) {
            val rows = (0 until t.rowCount).filter { it !in t.excludedRows && col!!.number(it) != null }
                .sortedBy { ((t.columns[cat].cells[it].value as? Value.Time)?.t?.sortKey) ?: Long.MAX_VALUE }
            var best = -1; var bestAbs = -1.0
            for (i in 1 until rows.size) {
                val d = kotlin.math.abs(col!!.number(rows[i])!! - col.number(rows[i - 1])!!)
                if (d > bestAbs) { bestAbs = d; best = i }
            }
            if (best > 0) {
                val tp = (t.columns[cat].cells[rows[best]].value as? Value.Time)?.t
                val short = tp?.axisLabel(false) ?: ""
                val up = col!!.number(rows[best])!! >= col.number(rows[best - 1])!!
                jumpText = if (up) I18n.tr("${short}의 큰 증가가 잘 드러나요.", "The big jump in $short stands out.", "${short}の大きな増加がよく分かります。")
                else I18n.tr("${short}의 큰 감소가 잘 드러나요.", "The big drop in $short stands out.", "${short}の大きな減少がよく分かります。")
            }
        }

        val out = ArrayList<Recommendation>()
        if (isTime) {
            out += Recommendation(ChartType.LINE, if (n >= 4) Fit.BEST else Fit.OK,
                I18n.tr("기간에 따른 흐름을 보여주기에 가장 좋아요.", "Best for showing how values move over time.", "時間の流れに沿った変化を見せるのに最適です。") + (jumpText?.let { " $it" } ?: ""))
            out += Recommendation(ChartType.BAR, if (n < 4) Fit.BEST else if (n <= 24) Fit.OK else Fit.NOT_FIT,
                if (n <= 24) I18n.tr("기간마다 크기를 비교하기 좋아요. 값 라벨을 함께 쓰기 편해요.", "Good for comparing each period. Easy to label values.", "期間ごとの大きさを比べやすく、値ラベルも付けやすいです。")
                else I18n.tr("기간이 ${n}개라 막대가 너무 가늘어져요.", "With $n periods the bars get too thin.", "期間が${n}個あり、棒が細くなりすぎます。"))
            if (multi && !hasNeg) out += Recommendation(ChartType.STACKED, Fit.OK, I18n.tr("${k}개 항목의 합과 구성을 함께 보여줘요.", "Shows the total and the mix of $k series together.", "${k}項目の合計と内訳を同時に示します。"))
            out += Recommendation(ChartType.AREA, if (hasNeg) Fit.NOT_FIT else Fit.OK,
                when {
                    hasNeg -> I18n.tr("음수가 있으면 영역이 헷갈려 보여요.", "Areas get confusing with negative values.", "マイナス値があると面が分かりにくくなります。")
                    multi -> I18n.tr("항목을 쌓아서 전체 양의 변화를 보여줘요.", "Stacks the series to show how the total changes.", "項目を積み上げて全体量の変化を示します。")
                    else -> I18n.tr("양이 쌓이는 느낌을 줘요. 계열이 하나면 선 그래프와 거의 같아요.", "Emphasises volume. With one series it is close to a line chart.", "量の積み重なりを感じさせます。系列が1つなら折れ線とほぼ同じです。")
                })
            out += Recommendation(ChartType.HBAR, Fit.OK, I18n.tr("항목 이름이 길 때 좋아요. 기간 데이터는 세로 방향이 더 자연스러워요.", "Good for long labels, though periods read more naturally left to right.", "項目名が長いときに便利です。期間データは縦棒の方が自然です。"))
            out += Recommendation(ChartType.DONUT, Fit.NOT_FIT, I18n.tr("전체 중 비율을 보여주는 차트예요. 기간 흐름엔 맞지 않아요.", "Donuts show parts of a whole, not change over time.", "全体に占める割合を示すグラフで、時間の流れには向きません。"))
        } else if (share) {
            val few = n in 2..6
            out += Recommendation(ChartType.DONUT, if (few) Fit.BEST else Fit.NOT_FIT,
                if (few) I18n.tr("전체 중 각 항목이 차지하는 몫을 보여주기 좋아요.", "Good for showing each item's share of the whole.", "全体に占める各項目の割合を示すのに向いています。")
                else I18n.tr("조각이 ${n}개면 크기를 구분하기 어려워요.", "With $n slices the sizes are hard to tell apart.", "${n}切れでは大きさを見分けにくいです。"))
            out += Recommendation(ChartType.HBAR, if (few) Fit.OK else Fit.BEST, I18n.tr("항목끼리 크기를 정확히 비교할 수 있어요. 큰 순서로 정렬해요.", "Compares items precisely, sorted largest first.", "項目同士を正確に比べられます。大きい順に並べます。"))
            out += Recommendation(ChartType.BAR, Fit.OK, I18n.tr("항목이 적고 이름이 짧을 때 좋아요.", "Good for a few items with short names.", "項目が少なく名前が短いときに向いています。"))
            out += Recommendation(ChartType.LINE, Fit.NOT_FIT, unorderedLine())
        } else {
            val hbarBest = longLabels || n > 8
            out += Recommendation(ChartType.BAR, if (hbarBest) Fit.OK else Fit.BEST,
                if (multi) I18n.tr("${k}개 항목을 나란히 비교해요.", "Compares $k series side by side.", "${k}項目を並べて比べます。")
                else I18n.tr("$seriesName 크기를 항목끼리 비교하기 좋아요.", "Good for comparing $seriesName across items.", "${seriesName}を項目同士で比べるのに向いています。"))
            out += Recommendation(ChartType.HBAR, if (hbarBest) Fit.BEST else Fit.OK,
                if (longLabels) I18n.tr("항목 이름이 길어서 가로 막대가 읽기 편해요.", "Labels are long, so horizontal bars read better.", "項目名が長いので横棒が読みやすいです。")
                else I18n.tr("항목이 많을 때 읽기 편해요. 큰 순서로 정렬해요.", "Easy to read with many items, sorted largest first.", "項目が多いときに読みやすく、大きい順に並べます。"))
            if (multi && !hasNeg) out += Recommendation(ChartType.STACKED, Fit.OK, I18n.tr("항목별 합계와 구성을 함께 보여줘요.", "Shows each item's total and mix together.", "項目ごとの合計と内訳を同時に示します。"))
            if (!multi && !hasNeg && n in 2..6) out += Recommendation(ChartType.DONUT, Fit.OK, I18n.tr("전체 중 몫을 보여줄 때만 써요. 합계가 의미 있을 때 좋아요.", "Use only for parts of a meaningful whole.", "合計に意味があるとき、全体に占める割合を示す場合だけ使います。"))
            else out += Recommendation(ChartType.DONUT, Fit.NOT_FIT, when {
                hasNeg -> I18n.tr("음수는 원 그래프로 그릴 수 없어요.", "Negative values can't be drawn as a donut.", "マイナス値は円グラフで描けません。")
                multi -> I18n.tr("원 그래프는 한 가지 값만 보여줄 수 있어요.", "A donut can show only one series.", "円グラフは1系列しか示せません。")
                else -> I18n.tr("조각이 ${n}개면 구분하기 어려워요.", "With $n slices it is hard to tell them apart.", "${n}切れでは見分けにくいです。")
            })
            out += Recommendation(ChartType.LINE, Fit.NOT_FIT, unorderedLine())
            out += Recommendation(ChartType.AREA, Fit.NOT_FIT, I18n.tr("영역 그래프는 시간 흐름에 맞는 차트예요.", "Area charts are meant for change over time.", "面グラフは時間の流れ向けのグラフです。"))
        }
        // Order: best first, then ok, then not fit. Stable within groups.
        return out.sortedBy { it.fit.ordinal }.distinctBy { it.type }
    }

    private fun unorderedLine() = I18n.tr("순서가 없는 항목을 선으로 이으면 흐름처럼 보여요.", "Joining unordered items with a line suggests a trend that isn't there.", "順序のない項目を線で結ぶと、流れがあるように見えてしまいます。")
}
