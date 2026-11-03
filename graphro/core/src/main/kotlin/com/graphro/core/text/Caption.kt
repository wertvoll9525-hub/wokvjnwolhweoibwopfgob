package com.graphro.core.text

import com.graphro.core.chart.ChartData
import com.graphro.core.chart.ChartSpec
import com.graphro.core.chart.ChartType
import com.graphro.core.model.Granularity
import com.graphro.core.model.TimePoint
import kotlin.math.abs

/**
 * One- or two-sentence explanation of a chart. All numbers are computed here; the wording stays
 * modest (no "추세" for short periods, no causal language).
 */
object Caption {

    fun make(d: ChartData, spec: ChartSpec): String {
        spec.customCaption?.takeIf { it.isNotBlank() }?.let { return it }
        val s = d.first ?: return ""
        val vals = s.values
        if (vals.none { it != null }) return ""
        val name = s.name
        val parts = ArrayList<String>()

        if (spec.type == ChartType.DONUT) {
            parts += shareSentence(d, name)
        } else if (d.isTime) {
            parts += timeSentence(d, name)
        } else {
            parts += compareSentence(d, name)
        }
        spec.target?.let { t ->
            val k = vals.count { it != null && it >= t }
            val word = when (d.timeGranularity) {
                Granularity.MONTH -> "달"; Granularity.YEAR -> "해"; Granularity.QUARTER -> "분기"
                Granularity.DAY -> "날"; Granularity.HALF -> "반기"; null -> "항목"
            }
            parts += "목표 ${d.withUnit(t)}에 닿은 ${Josa.eunNeun(word)} ${k}개예요."
        }
        return parts.filter { it.isNotBlank() }.joinToString(" ")
    }

    private fun bigWord(d: ChartData, up: Boolean): String {
        val high = d.unit in setOf("%", "%p", "℃", "점", "배", "°")
        return if (high) (if (up) "높아요" else "낮아요") else (if (up) "많아요" else "적어요")
    }

    private fun prevWord(d: ChartData, i: Int): String {
        val a = d.timePoints.getOrNull(i - 1); val b = d.timePoints.getOrNull(i)
        if (a == null || b == null) return "이전"
        return if (consecutive(a, b)) b.previousWord() else "이전 시점"
    }

    private fun consecutive(a: TimePoint, b: TimePoint): Boolean {
        if (a.granularity != b.granularity) return false
        val ya = a.year ?: 0; val yb = b.year ?: 0
        return when (a.granularity) {
            Granularity.YEAR -> yb - ya == 1
            Granularity.HALF -> (yb * 2 + b.sub) - (ya * 2 + a.sub) == 1
            Granularity.QUARTER -> (yb * 4 + b.sub) - (ya * 4 + a.sub) == 1
            Granularity.MONTH -> (yb * 12 + b.sub) - (ya * 12 + a.sub) == 1
            Granularity.DAY -> runCatching {
                val da = java.time.LocalDate.of(a.year ?: 2000, a.sub, a.day)
                val db = java.time.LocalDate.of(b.year ?: 2000, b.sub, b.day)
                java.time.temporal.ChronoUnit.DAYS.between(da, db) == 1L
            }.getOrDefault(false)
        }
    }

    private fun changeSentence(subject: String, from: Double, to: Double, d: ChartData, compareWord: String): String {
        val fromS = d.withUnit(from); val toS = d.withUnit(to)
        if (from == 0.0) return "${Josa.iGa(subject)} ${fromS}에서 ${Josa.euro(toS)} 바뀌었어요."
        val pct = (to - from) / abs(from) * 100
        if (abs(pct) < 0.5) return "${Josa.eunNeun(subject)} ${compareWord}와 거의 같아요 (${toS})."
        if (d.unit == "%" || d.unit == "%p") {
            val diff = to - from
            return "${Josa.iGa(subject)} ${compareWord}보다 ${Format.percent(abs(diff))}%p ${if (diff > 0) "올랐어요" else "내렸어요"}. ${fromS}에서 ${Josa.euro(toS)}."
        }
        return "${Josa.iGa(subject)} ${compareWord}보다 ${Format.percent(abs(pct))}% ${if (pct > 0) "늘었어요" else "줄었어요"}. ${fromS}에서 ${Josa.euro(toS)}."
    }

    private fun timeSentence(d: ChartData, name: String): String {
        val v = d.first!!.values
        val h = d.highlight
        if (h > 0 && v[h] != null) {
            var p = h - 1
            while (p >= 0 && v[p] == null) p--
            if (p >= 0) return changeSentence("${d.categories[h]} $name", v[p]!!, v[h]!!, d, if (p == h - 1) prevWord(d, h) else "이전")
        }
        val idx = v.indices.filter { v[it] != null }
        if (idx.size < 2) return "${d.fullCategories[idx.first()]} ${Josa.eunNeun(name)} ${Josa.ieyo(d.withUnit(v[idx.first()]!!))}."
        val a = idx.first(); val b = idx.last()
        val head = "${d.categories[a]}부터 ${d.categories[b]}까지 $name"
        val from = v[a]!!; val to = v[b]!!
        val first = if (from == 0.0) "${Josa.iGa(head)} ${d.withUnit(from)}에서 ${Josa.euro(d.withUnit(to))} 바뀌었어요."
        else {
            val pct = (to - from) / abs(from) * 100
            if (abs(pct) < 0.5) "${Josa.eunNeun(head)} 거의 그대로예요 (${d.withUnit(to)})."
            else "${Josa.iGa(head)} ${Format.percent(abs(pct))}% ${if (pct > 0) "늘었어요" else "줄었어요"}. ${d.withUnit(from)}에서 ${Josa.euro(d.withUnit(to))}."
        }
        if (idx.size >= 4) {
            val mx = idx.maxBy { v[it]!! }
            if (mx != a && mx != b) return "$first 가장 높았던 때는 ${Josa.ieyo(d.categories[mx] + "(" + d.withUnit(v[mx]!!) + ")")}."
        }
        return first
    }

    private fun compareSentence(d: ChartData, name: String): String {
        val v = d.first!!.values
        val idx = v.indices.filter { v[it] != null }
        if (idx.isEmpty()) return ""
        val mx = idx.maxBy { v[it]!! }
        val mn = idx.minBy { v[it]!! }
        val h = d.highlight
        if (h >= 0 && v[h] != null && h != mx) {
            val rank = idx.sortedByDescending { v[it]!! }.indexOf(h) + 1
            return "${d.categories[h]}의 ${Josa.eunNeun(name)} ${Josa.ieyo(d.withUnit(v[h]!!))}. ${idx.size}개 중 ${rank}번째예요."
        }
        val first = "${Josa.iGa(d.categories[mx] + "의 " + name)} ${Josa.euro(d.withUnit(v[mx]!!))} 가장 ${bigWord(d, true)}."
        if (idx.size >= 3 && mn != mx) {
            val ratio = if (v[mn]!! > 0) v[mx]!! / v[mn]!! else 0.0
            val high = d.unit in setOf("%", "%p", "℃", "점", "배", "°")
            val ratioText = if (ratio >= 10) Format.fixed(ratio, 0) else Format.fixed(ratio, 1, trim = true)
            val tail = if (ratio >= 1.5) " 가장 ${if (high) "낮은" else "적은"} ${d.categories[mn]}의 ${ratioText}배예요." else ""
            return first + tail
        }
        return first
    }

    private fun shareSentence(d: ChartData, name: String): String {
        val v = d.first!!.values
        val idx = v.indices.filter { v[it] != null && v[it]!! > 0 }
        if (idx.isEmpty()) return ""
        val total = idx.sumOf { v[it]!! }
        val mx = idx.maxBy { v[it]!! }
        val h = if (d.highlight >= 0 && d.highlight in idx) d.highlight else mx
        val pct = v[h]!! / total * 100
        return if (h == mx) "${Josa.iGa(d.categories[h])} 전체의 ${Format.percent(pct)}%로 가장 커요."
        else "${Josa.eunNeun(d.categories[h])} 전체의 ${Format.percent(pct)}%예요."
    }
}
