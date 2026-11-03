package com.graphro.core.parse

import com.graphro.core.model.Cell
import com.graphro.core.model.Column
import com.graphro.core.model.ColumnType
import com.graphro.core.model.DataTable
import com.graphro.core.model.Scale
import com.graphro.core.model.Value
import com.graphro.core.text.Format
import com.graphro.core.text.I18n
import com.graphro.core.text.Lang
import kotlin.math.abs

/** A unit declaration such as "(단위: 천원)" → factor 1000, unit "원". */
data class UnitSpec(val factor: Double, val unit: String?) {
    companion object {
        private val SCALE_WORDS = listOf(
            "십억" to 1e9, "천억" to 1e11, "백억" to 1e10, "천만" to 1e7, "백만" to 1e6, "십만" to 1e5,
            "조" to 1e12, "억" to 1e8, "만" to 1e4, "천" to 1e3,
            "十億" to 1e9, "百万" to 1e6, "千万" to 1e7, "兆" to 1e12, "億" to 1e8, "万" to 1e4, "千" to 1e3,
        )
        private val KNOWN_UNITS = setOf(
            "원", "달러", "엔", "유로", "위안", "명", "인", "개", "건", "가구", "세대", "호", "대", "회", "곳", "개소",
            "톤", "t", "kg", "g", "km", "m", "㎡", "㎢", "ha", "평", "%", "%p", "‰", "세", "시간", "분", "초", "일",
            "개월", "년", "배", "점", "마리", "두", "주", "표", "석", "kWh", "MWh", "GWh", "TOE", "L", "ℓ", "㎘", "kl",
            "USD", "KRW", "천원", "만원", "백만원", "억원", "명당", "℃", "mm", "cm", "회차", "권", "편", "곡", "채",
            "$", "円", "人", "件", "個", "台", "回", "社", "世帯", "戸", "店", "校", "歳", "時間", "日", "トン", "ドル", "ユーロ",
            "people", "persons", "units", "items", "visits", "users", "orders", "households", "tons", "hours", "days",
        )

        /** Parses "천원", "백만 원", "명", "%", "단위: 천 명". Returns null if it does not look like a unit. */
        fun parse(text: String): UnitSpec? {
            var t = text.trim().removePrefix("단위").removePrefix("単位").removePrefix("Unit").removePrefix("unit").removePrefix("s").trim().removePrefix(":").removePrefix("：").trim()
            // English: "$ millions", "in thousands", "millions of won"
            val en = Regex("^(?:in\\s+)?(\\$|USD|KRW|JPY|EUR)?\\s*(thousands?|millions?|billions?|trillions?|k|mn|bn)?\\s*(?:of\\s+)?([A-Za-z$]+)?$", RegexOption.IGNORE_CASE).matchEntire(t)
            if (en != null && (en.groupValues[2].isNotEmpty() || en.groupValues[1].isNotEmpty()) && t.isNotEmpty()) {
                val f = when (en.groupValues[2].lowercase().removeSuffix("s")) {
                    "thousand", "k" -> 1e3; "million", "mn" -> 1e6; "billion", "bn" -> 1e9; "trillion" -> 1e12; else -> 1.0
                }
                val cur = en.groupValues[1].ifEmpty { en.groupValues[3] }.let { c -> when (c.uppercase()) { "$", "USD", "DOLLAR", "DOLLARS" -> "$"; "KRW", "WON" -> "원"; "JPY", "YEN" -> "円"; "EUR", "EURO", "EUROS" -> "€"; else -> c.ifEmpty { null } } }
                if (f > 1.0 || cur != null) return UnitSpec(f, cur)
            }
            t = t.replace(" ", "")
            if (t.isEmpty() || t.length > 8) return null
            var factor = 1.0
            for ((w, f) in SCALE_WORDS) {
                if (t.startsWith(w) && t.length > w.length) { factor = f; t = t.substring(w.length); break }
                if (t == w) return UnitSpec(f, null)
            }
            if (t == "퍼센트" || t == "パーセント" || t.equals("percent", true)) t = "%"
            if (t in KNOWN_UNITS || (t.length <= 3 && t.all { it in '가'..'힣' } && factor > 1.0)) return UnitSpec(factor, t)
            return null
        }

        private val TRAILING = Regex("[\\(\\[（［]\\s*(?:단위\\s*[:：]?\\s*)?([^\\)\\]）］]{1,12})\\s*[\\)\\]）］]\\s*$")
        private val ANYWHERE = Regex("(?:단위|単位|[Uu]nits?)\\s*[:：]\\s*([^\\)\\]）］,]{1,16})")

        /** Splits "매출액(천원)" into ("매출액", spec). */
        fun fromHeader(name: String): Pair<String, UnitSpec?> {
            val m = TRAILING.find(name) ?: return name to null
            val spec = parse(m.groupValues[1]) ?: return name to null
            return name.substring(0, m.range.first).trim() to spec
        }

        /** Finds "(단위: 백만원)" anywhere in a title or note line. */
        fun findInText(text: String): UnitSpec? {
            ANYWHERE.find(text)?.let { m -> parse(m.groupValues[1].trim())?.let { return it } }
            val t = text.trim()
            TRAILING.find(t)?.let { m -> if (m.groupValues[1].contains("단위") || m.groupValues[1].contains("単位")) parse(m.groupValues[1])?.let { return it } }
            return null
        }
    }
}

/**
 * Everything needed to rebuild a table deterministically: the user can transpose, edit a cell,
 * or include an excluded row, and we simply rebuild from this.
 */
data class TableSource(
    val title: String?,
    val header: List<String>,
    val rows: List<List<String>>,
    val unitHint: UnitSpec? = null,
    val notes: List<String> = emptyList(),
    val report: List<String> = emptyList(),
    /** Rows the user chose to keep even though they look like totals. */
    val forceInclude: Set<Int> = emptySet(),
    /** Cells (row, col) where the user turned off the app's reinterpretation. */
    val keepRaw: Set<Pair<Int, Int>> = emptySet(),
) {
    fun transposed(): TableSource {
        val width = header.size
        val newHeader = ArrayList<String>()
        newHeader.add(I18n.item())
        rows.forEach { newHeader.add(it.firstOrNull() ?: "") }
        val newRows = (1 until width).map { c ->
            val r = ArrayList<String>()
            r.add(header.getOrElse(c) { "" })
            rows.forEach { r.add(it.getOrElse(c) { "" }) }
            r
        }
        return copy(header = newHeader, rows = newRows, forceInclude = emptySet(), keepRaw = emptySet(),
            report = report + I18n.tr("행과 열을 바꿨어요", "Swapped rows and columns", "行と列を入れ替えました"))
    }

    fun withCell(row: Int, col: Int, text: String): TableSource {
        val newRows = rows.mapIndexed { r, cells ->
            if (r != row) cells else List(maxOf(cells.size, col + 1)) { c -> if (c == col) text else cells.getOrElse(c) { "" } }
        }
        return copy(rows = newRows, keepRaw = keepRaw - (row to col))
    }
}

object TableBuilder {

    private val TOTAL_RE = Regex("^(합계|총계|총합|총액|총|계|소계|합|누계|전체|총수|合計|総計|小計|計|累計|全体|総数|total|totals|sum|grandtotal|subtotal|all|overall)$", RegexOption.IGNORE_CASE)
    private val YEAR_HEADER_RE = Regex("(연도|년도|년|year|年度|年)", RegexOption.IGNORE_CASE)

    fun isTotalLabel(s: String): Boolean = TOTAL_RE.matches(s.replace(" ", "").replace("　", "").trim())

    fun build(src: TableSource): DataTable {
        val width = maxOf(src.header.size, src.rows.maxOfOrNull { it.size } ?: 0)
        val rowCount = src.rows.size
        val report = src.report.toMutableList()
        val columns = ArrayList<Column>()

        val headerNames = (0 until width).map { c ->
            val h = src.header.getOrElse(c) { "" }.trim()
            h.ifEmpty { if (c == 0) I18n.item() else I18n.columnN(c + 1) }
        }.let { dedupe(it) }

        for (c in 0 until width) {
            val raws = (0 until rowCount).map { r -> src.rows[r].getOrElse(c) { "" }.trim() }
            val (cleanName, headerUnit) = UnitSpec.fromHeader(headerNames[c])
            columns.add(buildColumn(cleanName.ifEmpty { headerNames[c] }, c, raws, headerUnit ?: src.unitHint.takeIf { c > 0 }, src.keepRaw))
        }

        // Fill down grouped labels in the first text column ("서울" then blanks).
        if (columns.isNotEmpty() && columns[0].type == ColumnType.TEXT && rowCount > 2) {
            val col = columns[0]
            val cells = col.cells.toMutableList()
            var last: String? = null
            var filled = 0
            for (r in cells.indices) {
                val v = cells[r].value
                val others = columns.drop(1).any { it.cells[r].value !is Value.Empty }
                if (v is Value.Text) last = v.s
                else if (v is Value.Empty && last != null && others && r > 0) {
                    cells[r] = Cell(cells[r].raw, Value.Text(last), I18n.tr("빈 칸이라 위와 같은 ‘$last’(으)로 채웠어요", "Empty, so filled with “$last” from above", "空欄なので上と同じ「$last」で埋めました"), revertible = true)
                    filled++
                }
            }
            if (filled > 0 && filled < rowCount / 2 + 1) columns[0] = col.copy(cells = cells)
        }

        // Excluded rows: totals by label or by arithmetic.
        val excluded = LinkedHashMap<Int, String>()
        val labelCol = columns.indexOfFirst { it.type != ColumnType.NUMBER }
        val numCols = columns.indices.filter { columns[it].type == ColumnType.NUMBER }
        for (r in 0 until rowCount) {
            if (r in src.forceInclude) continue
            val labels = columns.filter { it.type != ColumnType.NUMBER }.map { it.cells[r].raw }
            if (labels.any { isTotalLabel(it) }) { excluded[r] = I18n.tr("합계 행이라 그래프에서 뺐어요", "Left out of the chart: it is a total row", "合計行なのでグラフから除きました"); continue }
        }
        if (numCols.isNotEmpty() && rowCount >= 4) {
            for (r in 0 until rowCount) {
                if (r in excluded || r in src.forceInclude) continue
                if (r != 0 && r != rowCount - 1) continue
                var allMatch = true
                var checked = 0
                for (c in numCols) {
                    val v = columns[c].number(r) ?: continue
                    val others = (0 until rowCount).filter { it != r && it !in excluded }.mapNotNull { columns[c].number(it) }
                    if (others.size < 2) { allMatch = false; break }
                    val sum = others.sum()
                    if (sum == 0.0 || abs(v - sum) > abs(sum) * 0.005 + 1e-9) { allMatch = false; break }
                    checked++
                }
                if (allMatch && checked > 0) {
                    val label = if (labelCol >= 0) columns[labelCol].cells[r].raw.ifEmpty { I18n.tr("이 행", "This row", "この行") } else I18n.tr("이 행", "This row", "この行")
                    excluded[r] = I18n.tr("‘$label’ 값이 나머지의 합과 같아서 합계로 보고 뺐어요", "“$label” equals the sum of the other rows, so it was treated as a total", "「$label」は他の行の合計と同じなので、合計として除きました")
                }
            }
        }
        if (excluded.isNotEmpty()) report.add(I18n.tr("합계로 보이는 행 ${excluded.size}개를 그래프에서 뺐어요", "Left ${excluded.size} total row(s) out of the chart", "合計と思われる${excluded.size}行をグラフから除きました"))

        // Total columns
        val finalCols = columns.map { col -> if (col.type == ColumnType.NUMBER && isTotalLabel(col.name)) col.copy(isTotal = true) else col }

        // Time order check
        val timeCol = finalCols.indexOfFirst { it.type == ColumnType.TIME }
        if (timeCol >= 0) {
            val pts = finalCols[timeCol].cells.mapNotNull { (it.value as? Value.Time)?.t }
            if (pts.size >= 2 && pts.zipWithNext().any { (a, b) -> a > b }) {
                val desc = pts.zipWithNext().all { (a, b) -> a >= b }
                report.add(if (desc) I18n.tr("기간이 최근 순으로 되어 있어 차트에서는 오래된 순으로 그려요", "Periods were newest first; the chart draws them oldest first", "期間が新しい順だったため、グラフでは古い順に描きます") else I18n.tr("기간 순서가 섞여 있어 차트에서는 시간 순으로 그려요", "Periods were out of order; the chart draws them in time order", "期間の順序がばらばらだったため、グラフでは時系列順に描きます"))
            }
        }

        return DataTable(
            title = src.title,
            columns = finalCols,
            excludedRows = excluded,
            notes = src.notes,
            report = report,
        )
    }

    private fun dedupe(names: List<String>): List<String> {
        val seen = HashMap<String, Int>()
        return names.map { n ->
            val k = seen.getOrDefault(n, 0)
            seen[n] = k + 1
            if (k == 0) n else "$n (${k + 1})"
        }
    }

    private class NumRead(val raw: String, val p: KoreanNumber.Parsed)

    private fun buildColumn(name: String, index: Int, raws: List<String>, headerUnit: UnitSpec?, keepRaw: Set<Pair<Int, Int>>): Column {
        val nonEmpty = raws.withIndex().filter { !KoreanNumber.isEmptyToken(it.value) }
        if (nonEmpty.isEmpty()) {
            return Column(name, ColumnType.TEXT, raws.map { Cell(it, Value.Empty) })
        }

        // Years as bare numbers are only years when the header says so or the column is the first.
        val allYearLike = nonEmpty.all { Regex("^\\d{4}$").matches(it.value.trim()) && TimeParser.validYear(it.value.trim().toInt()) }
        val headerSaysYear = YEAR_HEADER_RE.containsMatchIn(name) && !name.contains("년간") && !name.contains("年間") && headerUnit == null
        val allowBareYear = allYearLike && (headerSaysYear || index == 0)
        val allCompact = nonEmpty.all { Regex("^\\d{8}$").matches(it.value.trim()) }
        val allowCompact = allCompact && (index == 0 || YEAR_HEADER_RE.containsMatchIn(name) || name.contains("일자") || name.contains("날짜"))

        val times = nonEmpty.map { TimeParser.parse(it.value, allowBareYear, allowCompact) }
        val timeCount = times.count { it != null }
        // "2024.5" style values that are really decimals: only trust when the whole column agrees.
        val timeShare = timeCount.toDouble() / nonEmpty.size
        if (timeShare >= 0.8 && (index == 0 || timeShare == 1.0 || name.contains("기간") || name.contains("월") || name.contains("일자") || name.contains("期間") || name.contains("月") || name.contains("日付") || name.contains("date", true) || name.contains("month", true))) {
            val cells = raws.mapIndexed { r, raw ->
                if (KoreanNumber.isEmptyToken(raw)) Cell(raw, Value.Empty)
                else {
                    val t = TimeParser.parse(raw, allowBareYear, allowCompact)
                    when {
                        t != null -> Cell(raw, Value.Time(t))
                        isTotalLabel(raw) -> Cell(raw, Value.Empty)
                        else -> Cell(raw, Value.Empty, I18n.tr("기간으로 읽을 수 없어서 비웠어요", "Not a readable period, left empty", "期間として読めないため空欄にしました"))
                    }
                }
            }
            return Column(name, ColumnType.TIME, cells)
        }

        val nums = raws.map { raw -> if (KoreanNumber.isEmptyToken(raw)) null else KoreanNumber.parse(raw)?.let { NumRead(raw, it) } }
        val numCount = nums.count { it != null }
        if (numCount < 0.8 * nonEmpty.size || numCount == 0) {
            return Column(name, ColumnType.TEXT, raws.map { if (KoreanNumber.isEmptyToken(it)) Cell(it, Value.Empty) else Cell(it, Value.Text(it)) })
        }

        // Unit for the column: header first, then the most common cell unit.
        val cellUnits = nums.mapNotNull { it?.p?.unit }
        val majorityUnit = cellUnits.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
        val unit: String? = headerUnit?.unit ?: majorityUnit
        val headerFactor = headerUnit?.factor ?: 1.0
        val isPercent = unit == "%" || unit == "%p"

        // Implicit magnitude: most cells say "320만" but one says "280".
        val withBig = nums.filterNotNull().filter { it.p.bigFactor != null }
        val dominantBig: Double? = if (withBig.size * 2 >= numCount && withBig.isNotEmpty()) {
            withBig.groupingBy { it.p.bigFactor!! }.eachCount().maxByOrNull { it.value }!!.key
        } else null
        val bigValues = withBig.map { abs(it.p.value) }.filter { it > 0 }
        val minBig = bigValues.minOrNull()
        val maxBig = bigValues.maxOrNull()

        val values = arrayOfNulls<Double>(raws.size)
        val fixes = arrayOfNulls<String>(raws.size)
        val revert = BooleanArray(raws.size)
        for (r in raws.indices) {
            val nr = nums[r] ?: continue
            var v = nr.p.value
            if (dominantBig != null && nr.p.bigFactor == null && !nr.p.hadMagnitude && minBig != null && maxBig != null && (r to index) !in keepRaw) {
                val scaled = v * dominantBig
                if (abs(v) > 0 && abs(v) < minBig / 100.0 && abs(scaled) >= minBig / 10.0 && abs(scaled) <= maxBig * 10.0) {
                    v = scaled
                    fixes[r] = I18n.tr("단위가 빠져 있어서, 다른 칸처럼 ${magnitudeWord(dominantBig)} 단위로 읽었어요", "No unit was written, so it was read in ${magnitudeWord(dominantBig)} like the other cells", "単位がなかったため、他のセルと同じく${magnitudeWord(dominantBig)}単位で読みました")
                    revert[r] = true
                }
            }
            values[r] = v * headerFactor
        }

        // Cells written in a different currency/unit scale than the header ("4,380,000원" in a 만 원 column)
        // are already in base units after parsing; nothing else to do.

        val present = values.filterNotNull()
        val scale = chooseScale(present, unit, headerFactor)

        // How each cell wrote its number: with 만/억/조, with k/M, or plain digits. A cell written
        // differently from most of its column ("4,380,000원" among "320만") is worth pointing out.
        fun form(p: KoreanNumber.Parsed): Double = p.bigFactor ?: if (p.hadMagnitude) -1.0 else 1.0
        val forms = nums.filterNotNull().map { form(it.p) }
        val majorityForm = forms.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
        val cells = raws.mapIndexed { r, raw ->
            val v = values[r]
            when {
                KoreanNumber.isEmptyToken(raw) -> Cell(raw, Value.Empty)
                v == null -> Cell(raw, Value.Empty, I18n.tr("숫자로 읽을 수 없어서 비웠어요", "Not a readable number, left empty", "数値として読めないため空欄にしました"))
                else -> {
                    val p = nums[r]!!.p
                    val shown = Format.plain(v / scale.factor)
                    val unitText = com.graphro.core.model.unitLabelOf(scale, unit)
                    val fix = fixes[r] ?: when {
                        p.negativeMarker -> I18n.tr("‘${raw.trim()}’는 음수 표시라서 $shown(으)로 읽었어요", "“${raw.trim()}” marks a negative number, read as $shown", "「${raw.trim()}」はマイナス表記なので${shown}と読みました")
                        p.approx -> I18n.tr("‘약’을 빼고 $shown(으)로 읽었어요", "Read as $shown, ignoring “about”", "「約」を除いて${shown}と読みました")
                        forms.size >= 3 && majorityForm != null && form(p) != majorityForm && shown.replace(",", "") != raw.filter { it.isDigit() || it == '.' } ->
                            I18n.tr("‘${raw.trim()}’를 다른 칸과 같은 단위로 맞췄어요 → $shown", "Converted “${raw.trim()}” to the column's unit → $shown", "「${raw.trim()}」を他のセルと同じ単位に揃えました → $shown") + (if (unitText.isNotEmpty()) " ($unitText)" else "")
                        else -> null
                    }
                    Cell(raw, Value.Num(v), fix, revertible = revert[r])
                }
            }
        }
        return Column(name, ColumnType.NUMBER, cells, unit, scale)
    }

    private fun magnitudeWord(f: Double) = when (f) {
        1e12 -> I18n.tr("조", "trillions", "兆"); 1e9 -> I18n.tr("십억", "billions (B)", "十億"); 1e8 -> I18n.tr("억", "hundred millions", "億")
        1e6 -> I18n.tr("백만", "millions (M)", "百万"); 1e4 -> I18n.tr("만", "ten thousands", "万"); 1e3 -> I18n.tr("천", "thousands (K)", "千"); else -> ""
    }

    /** Picks a readable scale. Declared header scales win so the chart matches the source table. */
    fun chooseScale(values: List<Double>, unit: String?, headerFactor: Double): Scale {
        if (unit == "%" || unit == "%p" || unit == "‰") return Scale.ONE
        val en = I18n.lang == Lang.EN
        if (headerFactor > 1.0) Scale.ofFactor(headerFactor)?.let { declared ->
            val fitsLang = if (en) declared in setOf(Scale.THOUSAND, Scale.MILLION, Scale.BILLION, Scale.TRILLION) else true
            if (fitsLang) return declared
        }
        val countable = unit == null || unit in COUNTABLE
        if (!countable) return Scale.ONE
        val abs = values.map { kotlin.math.abs(it) }.filter { it > 0 }.sorted()
        if (abs.isEmpty()) return Scale.ONE
        val median = abs[abs.size / 2]
        return if (en) when {
            median >= 1e12 -> Scale.TRILLION
            median >= 1e9 -> Scale.BILLION
            median >= 1e6 -> Scale.MILLION
            median >= 1e5 -> Scale.THOUSAND
            else -> Scale.ONE
        } else when {
            median >= 1e13 -> Scale.TRILLION
            median >= 1e9 -> Scale.HUNDRED_MILLION
            median >= 1e5 -> Scale.TEN_THOUSAND
            else -> Scale.ONE
        }
    }

    private val COUNTABLE = setOf(
        "원", "달러", "명", "개", "건", "가구", "세대", "톤", "대", "회", "인", "엔", "유로", "위안", "마리", "표", "kWh",
        "$", "€", "£", "円", "人", "件", "個", "台", "回", "世帯", "トン", "people", "persons", "units", "items", "visits", "users", "orders",
    )

}
