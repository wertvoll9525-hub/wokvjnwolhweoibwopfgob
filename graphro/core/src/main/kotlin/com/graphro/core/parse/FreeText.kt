package com.graphro.core.parse

/**
 * Turns loosely written lines into a table:
 *   "1월 매출 320만, 방문 1,240명 / 2월 280만원 1105명 ..."
 * Each record starts with a label (a period such as "3월" or the first word of a line) and is
 * followed by numbers. Words right before the numbers become column names.
 */
object FreeText {

    private val NUMBER_TOKEN = Regex("[△▽▼+\\-−]?\\(?[₩$]?\\d[\\d,]*(?:\\.\\d+)?\\)?(?:\\s*[천백십])?(?:\\s*[조억만])?(?:(?<=[조억만])\\s*\\d[\\d,]*(?:\\.\\d+)?\\s*(?:[천백십]\\s*[조억만]?|[조억만]))*(?:%p|%|\\s?원|[가-힣A-Za-z㎡]{0,4})")
    private val PERIOD_TOKEN = Regex("(\\d{4}\\s*년\\s*\\d{1,2}\\s*월|\\d{2,4}\\s*년\\s*[1-4]\\s*분기|\\d{4}\\s*년(?:도)?|\\d{1,2}\\s*월|[1-4]\\s*분기|상반기|하반기|\\d{4}[-./]\\d{1,2}(?:[-./]\\d{1,2})?)")

    data class Record(val label: String, val pairs: List<Pair<String?, String>>)

    fun parse(text: String): RawGrid? {
        val lines = text.replace("\r", "").split('\n').map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.isEmpty()) return null

        // Split a line into several records when it contains several periods: "2월 280 3월 410".
        val segments = ArrayList<String>()
        for (line in lines) {
            val periods = PERIOD_TOKEN.findAll(line).toList()
            if (periods.size >= 2 && periods.first().range.first <= 2) {
                for ((i, m) in periods.withIndex()) {
                    val end = if (i + 1 < periods.size) periods[i + 1].range.first else line.length
                    segments.add(line.substring(m.range.first, end).trim().trimEnd(',', '/', ';', '·'))
                }
            } else segments.add(line)
        }

        val records = segments.mapNotNull { parseSegment(it) }
        if (records.size < 2) return null
        val counts = records.map { it.pairs.size }
        val mode = counts.groupingBy { it }.eachCount().maxByOrNull { it.value }!!.key
        if (mode == 0) return null
        if (counts.count { it == mode } < records.size * 0.6) return null
        if (records.map { it.label }.toSet().size < records.size * 0.8) return null

        // Column names: the most common word in each position, else the unit, else "값 n".
        val names = (0 until mode).map { p ->
            val words = records.mapNotNull { it.pairs.getOrNull(p)?.first }.filter { it.isNotBlank() }
            words.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
                ?: records.mapNotNull { r -> r.pairs.getOrNull(p)?.second?.let { KoreanNumber.parse(it)?.unit } }
                    .groupingBy { it }.eachCount().maxByOrNull { it.value }?.key?.let { unitName(it) }
                ?: if (mode == 1) "값" else "값 ${p + 1}"
        }.let { n -> n.mapIndexed { i, s -> if (n.count { it == s } > 1) "$s ${i + 1}" else s } }

        val labelIsPeriod = records.all { TimeParser.parse(it.label) != null }
        val header = listOf(if (labelIsPeriod) "기간" else "항목") + names
        val rows = records.map { r -> listOf(r.label) + (0 until mode).map { p -> r.pairs.getOrNull(p)?.second ?: "" } }
        return RawGrid("문장", rows.let { listOf(header) + it })
    }

    private fun unitName(u: String) = when (u) {
        "원", "만원", "천원" -> "금액"
        "명" -> "인원"
        "%" -> "비율"
        "개" -> "개수"
        "건" -> "건수"
        else -> u
    }

    private fun parseSegment(seg: String): Record? {
        var s = seg.trim().trimStart('-', '*', '•', '·').trim()
        if (s.isEmpty()) return null
        val period = PERIOD_TOKEN.find(s)
        val label: String
        if (period != null && period.range.first <= 1) {
            label = period.value.trim()
            s = s.substring(period.range.last + 1)
        } else {
            // First word(s) before the first digit
            val firstDigit = s.indexOfFirst { it.isDigit() }
            if (firstDigit <= 0) return null
            val head = s.substring(0, firstDigit).trim().trimEnd(':', '：', '-', '=')
            val words = head.split(Regex("\\s+")).filter { it.isNotEmpty() }
            if (words.isEmpty()) return null
            label = words.first().trimEnd(':', '：', ',')
            s = s.substring(s.indexOf(words.first()) + words.first().length)
        }
        val pairs = ArrayList<Pair<String?, String>>()
        var pos = 0
        for (m in NUMBER_TOKEN.findAll(s)) {
            val token = m.value.trim().trimEnd(',', '/', ';')
            if (KoreanNumber.parse(token) == null) continue
            val before = s.substring(pos, m.range.first)
                .replace(Regex("[,/;:：=·()]"), " ").trim()
                .split(Regex("\\s+")).lastOrNull { it.isNotEmpty() && it.any { ch -> ch.isLetter() } }
                ?.removeSuffix("은")?.removeSuffix("는")?.removeSuffix("이")?.removeSuffix("가")
            pairs.add(before to token)
            pos = m.range.last + 1
        }
        if (pairs.isEmpty()) return null
        return Record(label, pairs)
    }
}
