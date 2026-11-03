package com.graphro.core.parse

import com.graphro.core.model.Cell
import com.graphro.core.model.Column
import com.graphro.core.model.ColumnType
import com.graphro.core.model.DataTable
import com.graphro.core.model.Scale
import com.graphro.core.model.Value
import com.graphro.core.text.Format
import kotlin.math.abs

/** A unit declaration such as "(단위: 천원)" → factor 1000, unit "원". */
data class UnitSpec(val factor: Double, val unit: String?) {
    companion object {
        private val SCALE_WORDS = listOf(
            "십억" to 1e9, "천억" to 1e11, "백억" to 1e10, "천만" to 1e7, "백만" to 1e6, "십만" to 1e5,
            "조" to 1e12, "억" to 1e8, "만" to 1e4, "천" to 1e3,
        )
        private val KNOWN_UNITS = setOf(
            "원", "달러", "엔", "유로", "위안", "명", "인", "개", "건", "가구", "세대", "호", "대", "회", "곳", "개소",
            "톤", "t", "kg", "g", "km", "m", "㎡", "㎢", "ha", "평", "%", "%p", "‰", "세", "시간", "분", "초", "일",
            "개월", "년", "배", "점", "마리", "두", "주", "표", "석", "kWh", "MWh", "GWh", "TOE", "L", "ℓ", "㎘", "kl",
            "USD", "KRW", "천원", "만원", "백만원", "억원", "명당", "℃", "mm", "cm", "회차", "권", "편", "곡", "채",
        )

        /** Parses "천원", "백만 원", "명", "%", "단위: 천 명". Returns null if it does not look like a unit. */
        fun parse(text: String): UnitSpec? {
            var t = text.trim().removePrefix("단위").trim().removePrefix(":").removePrefix("：").trim()
            t = t.replace(" ", "")
            if (t.isEmpty() || t.length > 8) return null
            var factor = 1.0
            for ((w, f) in SCALE_WORDS) {
                if (t.startsWith(w) && t.length > w.length) { factor = f; t = t.substring(w.length); break }
                if (t == w) return UnitSpec(f, null)
            }
            if (t == "퍼센트") t = "%"
            if (t in KNOWN_UNITS || (t.length <= 3 && t.all { it in '가'..'힣' } && factor > 1.0)) return UnitSpec(factor, t)
            return null
        }

        private val TRAILING = Regex("[\\(\\[（［]\\s*(?:단위\\s*[:：]?\\s*)?([^\\)\\]）］]{1,12})\\s*[\\)\\]）］]\\s*$")
        private val ANYWHERE = Regex("단위\\s*[:：]\\s*([^\\)\\]）］,]{1,12})")

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
            TRAILING.find(t)?.let { m -> if (m.groupValues[1].contains("단위")) parse(m.groupValues[1])?.let { return it } }
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
        newHeader.add(header.firstOrNull()?.takeIf { it.isNotBlank() }?.let { "항목" } ?: "항목")
        rows.forEach { newHeader.add(it.firstOrNull() ?: "") }
        val newRows = (1 until width).map { c ->
            val r = ArrayList<String>()
            r.add(header.getOrElse(c) { "" })
            rows.forEach { r.add(it.getOrElse(c) { "" }) }
            r
        }
        return copy(header = newHeader, rows = newRows, forceInclude = emptySet(), keepRaw = emptySet(),
            report = report + "행과 열을 바꿨어요")
    }

    fun withCell(row: Int, col: Int, text: String): TableSource {
        val newRows = rows.mapIndexed { r, cells ->
            if (r != row) cells else List(maxOf(cells.size, col + 1)) { c -> if (c == col) text else cells.getOrElse(c) { "" } }
        }
        return copy(rows = newRows, keepRaw = keepRaw - (row to col))
    }
}

object TableBuilder {

    private val TOTAL_RE = Regex("^(합계|총계|총합|총액|총|계|소계|합|누계|전체|총수|total|sum|grandtotal|subtotal|all|overall)$", RegexOption.IGNORE_CASE)
    private val YEAR_HEADER_RE = Regex("(연도|년도|년|year)", RegexOption.IGNORE_CASE)

    fun isTotalLabel(s: String): Boolean = TOTAL_RE.matches(s.replace(" ", "").replace("　", "").trim())

    fun build(src: TableSource): DataTable {
        val width = maxOf(src.header.size, src.rows.maxOfOrNull { it.size } ?: 0)
        val rowCount = src.rows.size
        val report = src.report.toMutableList()
        val columns = ArrayList<Column>()

        val headerNames = (0 until width).map { c ->
            val h = src.header.getOrElse(c) { "" }.trim()
            h.ifEmpty { if (c == 0) "항목" else "열 ${c + 1}" }
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
                    cells[r] = Cell(cells[r].raw, Value.Text(last), "빈 칸이라 위와 같은 ‘$last’(으)로 채웠어요")
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
            val labels = columns.filter { it.type == ColumnType.TEXT }.mapNotNull { (it.cells[r].value as? Value.Text)?.s }
            if (labels.any { isTotalLabel(it) }) { excluded[r] = "합계 행이라 그래프에서 뺐어요"; continue }
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
                    val label = if (labelCol >= 0) columns[labelCol].cells[r].raw.ifEmpty { "이 행" } else "이 행"
                    excluded[r] = "‘$label’ 값이 나머지의 합과 같아서 합계로 보고 뺐어요"
                }
            }
        }
        if (excluded.isNotEmpty()) report.add("합계로 보이는 행 ${excluded.size}개를 그래프에서 뺐어요")

        // Total columns
        val finalCols = columns.map { col -> if (col.type == ColumnType.NUMBER && isTotalLabel(col.name)) col.copy(isTotal = true) else col }

        // Time order check
        val timeCol = finalCols.indexOfFirst { it.type == ColumnType.TIME }
        if (timeCol >= 0) {
            val pts = finalCols[timeCol].cells.mapNotNull { (it.value as? Value.Time)?.t }
            if (pts.size >= 2 && pts.zipWithNext().any { (a, b) -> a > b }) {
                val desc = pts.zipWithNext().all { (a, b) -> a >= b }
                report.add(if (desc) "기간이 최근 순으로 되어 있어 차트에서는 오래된 순으로 그려요" else "기간 순서가 섞여 있어 차트에서는 시간 순으로 그려요")
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
        val headerSaysYear = YEAR_HEADER_RE.containsMatchIn(name) && !name.contains("년간") && headerUnit == null
        val allowBareYear = allYearLike && (headerSaysYear || index == 0)
        val allCompact = nonEmpty.all { Regex("^\\d{8}$").matches(it.value.trim()) }
        val allowCompact = allCompact && (index == 0 || YEAR_HEADER_RE.containsMatchIn(name) || name.contains("일자") || name.contains("날짜"))

        val times = nonEmpty.map { TimeParser.parse(it.value, allowBareYear, allowCompact) }
        val timeCount = times.count { it != null }
        // "2024.5" style values that are really decimals: only trust when the whole column agrees.
        val timeShare = timeCount.toDouble() / nonEmpty.size
        if (timeShare >= 0.8 && (index == 0 || timeShare == 1.0 || name.contains("기간") || name.contains("월") || name.contains("일자"))) {
            val cells = raws.mapIndexed { r, raw ->
                if (KoreanNumber.isEmptyToken(raw)) Cell(raw, Value.Empty)
                else {
                    val t = TimeParser.parse(raw, allowBareYear, allowCompact)
                    if (t != null) Cell(raw, Value.Time(t)) else Cell(raw, Value.Empty, "기간으로 읽을 수 없어서 비웠어요")
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
        for (r in raws.indices) {
            val nr = nums[r] ?: continue
            var v = nr.p.value
            if (dominantBig != null && nr.p.bigFactor == null && !nr.p.hadMagnitude && minBig != null && maxBig != null && (r to index) !in keepRaw) {
                val scaled = v * dominantBig
                if (abs(v) > 0 && abs(v) < minBig / 100.0 && abs(scaled) >= minBig / 10.0 && abs(scaled) <= maxBig * 10.0) {
                    v = scaled
                    fixes[r] = "단위가 빠져 있어서, 다른 칸처럼 ${magnitudeWord(dominantBig)} 단위로 읽었어요"
                }
            }
            values[r] = v * headerFactor
        }

        // Cells written in a different currency/unit scale than the header ("4,380,000원" in a 만 원 column)
        // are already in base units after parsing; nothing else to do.

        val present = values.filterNotNull()
        val scale = chooseScale(present, unit, headerFactor)

        val cells = raws.mapIndexed { r, raw ->
            val v = values[r]
            when {
                KoreanNumber.isEmptyToken(raw) -> Cell(raw, Value.Empty)
                v == null -> Cell(raw, Value.Empty, "숫자로 읽을 수 없어서 비웠어요")
                else -> {
                    val shown = Format.plain(v / scale.factor)
                    val fix = fixes[r] ?: describeChange(raw, nums[r]!!.p, shown, unit, scale, isPercent)
                    Cell(raw, Value.Num(v), fix)
                }
            }
        }
        return Column(name, ColumnType.NUMBER, cells, unit, scale)
    }

    private fun magnitudeWord(f: Double) = when (f) { 1e12 -> "조"; 1e8 -> "억"; 1e4 -> "만"; else -> "" }

    /** Picks a readable scale. Declared header scales win so the chart matches the source table. */
    fun chooseScale(values: List<Double>, unit: String?, headerFactor: Double): Scale {
        if (unit == "%" || unit == "%p" || unit == "‰") return Scale.ONE
        if (headerFactor > 1.0) Scale.ofFactor(headerFactor)?.let { return it }
        val countable = unit == null || unit in setOf("원", "달러", "명", "개", "건", "가구", "세대", "톤", "대", "회", "인", "엔", "유로", "위안", "마리", "표", "kWh")
        if (!countable) return Scale.ONE
        val abs = values.map { kotlin.math.abs(it) }.filter { it > 0 }.sorted()
        if (abs.isEmpty()) return Scale.ONE
        val median = abs[abs.size / 2]
        return when {
            median >= 1e13 -> Scale.TRILLION
            median >= 1e9 -> Scale.HUNDRED_MILLION
            median >= 1e5 -> Scale.TEN_THOUSAND
            else -> Scale.ONE
        }
    }

    /** Explains a reinterpretation only when the shown number differs from what the cell literally said. */
    private fun describeChange(raw: String, p: KoreanNumber.Parsed, shown: String, unit: String?, scale: Scale, isPercent: Boolean): String? {
        val literal = raw.replace(",", "").replace(" ", "").replace(" ", "")
            .let { s -> unit?.let { s.removeSuffix(it) } ?: s }
            .removeSuffix("%").removePrefix("+").removePrefix("₩").removePrefix("$")
        val shownCmp = shown.replace(",", "")
        if (literal == shownCmp) return null
        if (literal.toDoubleOrNull() != null && shownCmp.toDoubleOrNull() != null && literal.toDouble() == shownCmp.toDouble()) return null
        val unitText = listOf(scale.label, unit ?: "").filter { it.isNotEmpty() }.joinToString(" ")
        return when {
            p.negativeMarker -> "‘${raw.trim()}’는 음수 표시라서 $shown(으)로 읽었어요"
            p.approx -> "‘약’을 빼고 $shown(으)로 읽었어요"
            p.hadMagnitude || scale != Scale.ONE -> "‘${raw.trim()}’ → $shown" + (if (unitText.isNotEmpty()) " ($unitText)" else "") + ". 표의 단위에 맞췄어요"
            else -> null
        }
    }
}
