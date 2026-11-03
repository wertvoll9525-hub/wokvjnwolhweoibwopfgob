package com.graphro.core.text

import com.graphro.core.chart.ChartData
import com.graphro.core.chart.ChartSpec
import com.graphro.core.chart.ChartType
import com.graphro.core.model.Granularity
import kotlin.math.abs

/** English and Japanese captions. Same facts as the Korean ones, written in each language's order. */
internal object ForeignCaption {
    private val en get() = I18n.lang == Lang.EN

    fun make(d: ChartData, spec: ChartSpec): String {
        val s = d.first ?: return ""
        val vals = s.values
        if (vals.none { it != null }) return ""
        val name = s.name
        val parts = ArrayList<String>()
        parts += when {
            spec.type == ChartType.DONUT -> share(d)
            d.isTime -> time(d, name)
            else -> compare(d, name)
        }
        spec.target?.let { t ->
            val k = vals.count { it != null && it >= t }
            val tgt = d.withUnit(t)
            parts += if (en) {
                val word = when (d.timeGranularity) {
                    Granularity.MONTH -> "month"; Granularity.YEAR -> "year"; Granularity.QUARTER -> "quarter"
                    Granularity.DAY -> "day"; Granularity.HALF -> "half"; null -> "item"
                }
                "$k ${word}${if (k == 1) "" else "s"} reached the $tgt target."
            } else {
                val word = when (d.timeGranularity) {
                    Granularity.MONTH -> "か月"; Granularity.YEAR -> "年"; Granularity.QUARTER -> "四半期"
                    Granularity.DAY -> "日"; Granularity.HALF -> "期"; null -> "項目"
                }
                "目標の${tgt}に達したのは${k}${word}です。"
            }
        }
        return parts.filter { it.isNotBlank() }.joinToString(" ")
    }

    private fun pct(from: Double, to: Double) = (to - from) / abs(from) * 100

    private fun change(subject: String, from: Double, to: Double, d: ChartData, prev: String): String {
        val f = d.withUnit(from); val t = d.withUnit(to)
        if (from == 0.0) return if (en) "$subject went from $f to $t." else "${subject}は${f}から${t}になりました。"
        if (d.unit == "%" || d.unit == "%p") {
            val diff = to - from
            val pts = Format.percent(abs(diff))
            return if (en) "$subject ${if (diff > 0) "rose" else "fell"} $pts percentage points from $prev, from $f to $t."
            else "${subject}は${prev}より${pts}ポイント${if (diff > 0) "上がりました" else "下がりました"}（$f→$t）。"
        }
        val p = pct(from, to)
        if (abs(p) < 0.5) return if (en) "$subject was about the same as $prev ($t)." else "${subject}は${prev}とほぼ同じです（$t）。"
        val ps = Format.percent(abs(p))
        return if (en) "$subject ${if (p > 0) "rose" else "fell"} $ps% from $prev, from $f to $t."
        else "${subject}は${prev}より${ps}%${if (p > 0) "増えました" else "減りました"}（$f→$t）。"
    }

    private fun prevWord(d: ChartData, i: Int, adjacent: Boolean): String {
        val a = d.timePoints.getOrNull(i - 1); val b = d.timePoints.getOrNull(i)
        if (!adjacent || a == null || b == null || !Caption.consecutive(a, b)) return if (en) "the previous point" else "前回"
        return b.previousWord()
    }

    private fun subject(cat: String, name: String) = if (en) "$cat $name" else "${cat}の$name"

    private fun time(d: ChartData, name: String): String {
        val v = d.first!!.values
        val h = d.highlight
        if (h > 0 && v[h] != null) {
            var p = h - 1
            while (p >= 0 && v[p] == null) p--
            if (p >= 0) return change(subject(d.categories[h], name), v[p]!!, v[h]!!, d, prevWord(d, h, p == h - 1))
        }
        val idx = v.indices.filter { v[it] != null }
        if (idx.size < 2) {
            val i = idx.first()
            return if (en) "$name in ${d.fullCategories[i]} was ${d.withUnit(v[i]!!)}." else "${d.fullCategories[i]}の${name}は${d.withUnit(v[i]!!)}です。"
        }
        val a = idx.first(); val b = idx.last()
        val from = v[a]!!; val to = v[b]!!
        val fa = d.categories[a]; val fb = d.categories[b]
        val first = when {
            from == 0.0 -> if (en) "From $fa to $fb, $name went from ${d.withUnit(from)} to ${d.withUnit(to)}." else "${fa}から${fb}までで、${name}は${d.withUnit(from)}から${d.withUnit(to)}になりました。"
            abs(pct(from, to)) < 0.5 -> if (en) "From $fa to $fb, $name stayed about the same (${d.withUnit(to)})." else "${fa}から${fb}まで、${name}はほぼ横ばいです（${d.withUnit(to)}）。"
            else -> {
                val p = pct(from, to); val ps = Format.percent(abs(p))
                if (en) "From $fa to $fb, $name ${if (p > 0) "rose" else "fell"} $ps%, from ${d.withUnit(from)} to ${d.withUnit(to)}."
                else "${fa}から${fb}までで、${name}は${ps}%${if (p > 0) "増えました" else "減りました"}（${d.withUnit(from)}→${d.withUnit(to)}）。"
            }
        }
        if (idx.size >= 4) {
            val mx = idx.maxBy { v[it]!! }
            if (mx != a && mx != b) return first + " " + (if (en) "It peaked in ${d.categories[mx]} (${d.withUnit(v[mx]!!)})." else "最も高かったのは${d.categories[mx]}（${d.withUnit(v[mx]!!)}）です。")
        }
        return first
    }

    private fun compare(d: ChartData, name: String): String {
        val v = d.first!!.values
        val idx = v.indices.filter { v[it] != null }
        if (idx.isEmpty()) return ""
        val mx = idx.maxBy { v[it]!! }
        val mn = idx.minBy { v[it]!! }
        val h = d.highlight
        if (h >= 0 && v[h] != null && h != mx) {
            val rank = idx.sortedByDescending { v[it]!! }.indexOf(h) + 1
            return if (en) "${d.categories[h]}: $name ${d.withUnit(v[h]!!)}, #$rank of ${idx.size}."
            else "${d.categories[h]}の${name}は${d.withUnit(v[h]!!)}で、${idx.size}件中${rank}位です。"
        }
        val high = d.unit in setOf("%", "%p", "℃", "点", "倍", "°")
        val first = if (en) "${d.categories[mx]} has the highest $name at ${d.withUnit(v[mx]!!)}."
        else "${name}が最も${if (high) "高い" else "多い"}のは${d.categories[mx]}で、${d.withUnit(v[mx]!!)}です。"
        if (idx.size >= 3 && mn != mx && v[mn]!! > 0) {
            val ratio = v[mx]!! / v[mn]!!
            if (ratio >= 1.5) {
                val r = if (ratio >= 10) Format.fixed(ratio, 0) else Format.fixed(ratio, 1, trim = true)
                return first + " " + (if (en) "That is ${r}× the lowest, ${d.categories[mn]}." else "最も${if (high) "低い" else "少ない"}${d.categories[mn]}の${r}倍です。")
            }
        }
        return first
    }

    private fun share(d: ChartData): String {
        val v = d.first!!.values
        val idx = v.indices.filter { v[it] != null && v[it]!! > 0 }
        if (idx.isEmpty()) return ""
        val total = idx.sumOf { v[it]!! }
        val mx = idx.maxBy { v[it]!! }
        val h = if (d.highlight >= 0 && d.highlight in idx) d.highlight else mx
        val p = Format.percent(v[h]!! / total * 100)
        return if (h == mx) {
            if (en) "${d.categories[h]} is the largest share at $p%." else "${d.categories[h]}が全体の${p}%で最大です。"
        } else {
            if (en) "${d.categories[h]} is $p% of the total." else "${d.categories[h]}は全体の${p}%です。"
        }
    }
}
