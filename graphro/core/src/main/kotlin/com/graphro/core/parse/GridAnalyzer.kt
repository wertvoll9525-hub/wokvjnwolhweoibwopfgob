package com.graphro.core.parse

import com.graphro.core.text.I18n

/**
 * Finds tables inside a raw grid: splits blocks separated by blank rows/columns, peels off
 * title and note lines, finds (possibly multi-line) headers and turns sideways time tables
 * upright.
 */
object GridAnalyzer {

    private val NOTE_PREFIX = Regex("^(출처|자료|주\\s*[:：)]|주\\d|※|\\*|비고|참고|出典|出所|資料|注\\s*[:：)]|注\\d|備考|source|sources|note|notes)", RegexOption.IGNORE_CASE)

    private enum class Kind { E, N, T, S }

    fun analyze(grid: RawGrid, maxRows: Int = 20_000, maxCols: Int = 200): List<TableSource> {
        val rows0 = grid.rows.take(maxRows).map { r -> r.take(maxCols).map { cleanCell(it) } }
        val width = rows0.maxOfOrNull { it.size } ?: 0
        if (width == 0) return emptyList()
        val rows = rows0.map { r -> List(width) { c -> r.getOrElse(c) { "" } } }

        val out = ArrayList<TableSource>()
        var pendingTitle: String? = null
        var sheetUnit: UnitSpec? = null
        for (block in verticalBlocks(rows)) {
            for (b in horizontalBlocks(block)) {
                val nonEmptyCells = b.sumOf { r -> r.count { it.isNotEmpty() } }
                val textLine = b.joinToString(" ") { r -> r.filter { it.isNotEmpty() }.joinToString(" ") }
                if (b.size == 1 && b[0].count { it.isNotEmpty() } <= 2 || nonEmptyCells <= 2) {
                    var newTitle: String? = null
                    for (r in b) {
                        val line = r.filter { it.isNotEmpty() }.joinToString(" ").trim()
                        if (line.isEmpty()) continue
                        val u = UnitSpec.findInText(line)
                        if (u != null) sheetUnit = u
                        val stripped = stripUnit(line)
                        if (NOTE_PREFIX.containsMatchIn(stripped) && out.isNotEmpty()) {
                            val last = out.removeAt(out.size - 1)
                            out.add(last.copy(notes = last.notes + stripped))
                            continue
                        }
                        if (newTitle == null && stripped.isNotEmpty() && !NOTE_PREFIX.containsMatchIn(stripped) && !stripped.startsWith("(") && stripped.length <= 80) newTitle = stripped
                    }
                    if (newTitle != null) pendingTitle = newTitle
                    continue
                }
                val t = buildFromBlock(b, pendingTitle, sheetUnit)
                if (t != null) out.add(t)
                pendingTitle = null
            }
        }
        return out
    }

    private val UNIT_PAREN = Regex("[\\(\\[（［]\\s*(?:단위|単位|[Uu]nits?)\\s*[:：][^\\)\\]）］]*[\\)\\]）］]")

    private fun stripUnit(s: String): String = s.replace(UNIT_PAREN, "").trim()

    private fun cleanCell(s: String): String =
        s.replace(' ', ' ').replace("​", "").replace("﻿", "").trim()

    private fun isBlankRow(r: List<String>) = r.all { it.isEmpty() }

    private fun verticalBlocks(rows: List<List<String>>): List<List<List<String>>> {
        val blocks = ArrayList<List<List<String>>>()
        var cur = ArrayList<List<String>>()
        for (r in rows) {
            if (isBlankRow(r)) {
                if (cur.isNotEmpty()) { blocks.add(cur); cur = ArrayList() }
            } else cur.add(r)
        }
        if (cur.isNotEmpty()) blocks.add(cur)
        return blocks
    }

    /** Splits side-by-side tables separated by an empty column; also drops empty columns. */
    private fun horizontalBlocks(block: List<List<String>>): List<List<List<String>>> {
        val width = block.maxOf { it.size }
        val emptyCol = BooleanArray(width) { c -> block.all { it.getOrElse(c) { "" }.isEmpty() } }
        val groups = ArrayList<IntRange>()
        var start = -1
        for (c in 0 until width) {
            if (!emptyCol[c] && start < 0) start = c
            if (emptyCol[c] && start >= 0) { groups.add(start until c); start = -1 }
        }
        if (start >= 0) groups.add(start until width)
        if (groups.size <= 1) {
            val g = groups.firstOrNull() ?: return emptyList()
            return listOf(block.map { r -> g.map { r.getOrElse(it) { "" } } })
        }
        // Only split if each side has at least 2 columns of real content; otherwise just drop empties.
        val real = groups.filter { it.count() >= 2 }
        if (real.size >= 2) {
            return real.map { g -> block.map { r -> g.map { r.getOrElse(it) { "" } } }.filterNot { isBlankRow(it) } }.filter { it.isNotEmpty() }
        }
        val keep = (0 until width).filter { !emptyCol[it] }
        return listOf(block.map { r -> keep.map { r.getOrElse(it) { "" } } })
    }

    private fun kind(s: String, allowYear: Boolean): Kind = when {
        s.isEmpty() || KoreanNumber.isEmptyToken(s) -> Kind.E
        TimeParser.parse(s, allowBareYear = allowYear) != null -> Kind.T
        KoreanNumber.parse(s) != null -> Kind.N
        else -> Kind.S
    }

    private fun buildFromBlock(block0: List<List<String>>, titleIn: String?, unitIn: UnitSpec?): TableSource? {
        var block = block0
        var title = titleIn
        var unit = unitIn
        val notes = ArrayList<String>()
        val report = ArrayList<String>()
        val width = block.maxOf { it.size }

        // Title rows on top: a single filled cell in a wide block.
        var titleRows = 0
        while (block.size >= 3 && width >= 2 && block[0].count { it.isNotEmpty() } == 1) {
            val line = block[0].first { it.isNotEmpty() }
            if (KoreanNumber.parse(line) != null) break
            val u = UnitSpec.findInText(line)
            if (u != null) unit = u
            if (title == null && stripUnit(line).isNotEmpty() && (u == null || stripUnit(line) != line)) title = stripUnit(line)
            block = block.drop(1)
            titleRows++
        }
        if (titleRows > 0) report.add(I18n.tr("맨 위 제목·단위 줄 ${titleRows}개를 표에서 분리했어요", "Separated $titleRows title/unit line(s) at the top", "上部のタイトル・単位行${titleRows}行を表から分けました"))
        // Note rows at the bottom.
        while (block.size >= 3) {
            val last = block.last()
            val first = last.firstOrNull { it.isNotEmpty() } ?: break
            val filled = last.count { it.isNotEmpty() }
            if (NOTE_PREFIX.containsMatchIn(first) || (filled == 1 && width >= 3 && first.length > 12)) {
                UnitSpec.findInText(first)?.let { unit = it }
                notes.add(0, last.filter { it.isNotEmpty() }.joinToString(" "))
                block = block.dropLast(1)
            } else break
        }
        if (notes.isNotEmpty()) report.add(I18n.tr("출처·메모 줄 ${notes.size}개를 표에서 분리했어요", "Separated ${notes.size} source/note line(s)", "出典・注記${notes.size}行を表から分けました"))
        if (block.isEmpty()) return null

        // A "(단위: 천원)" cell sitting in the top-right corner row.
        if (block.size >= 3) {
            val r0 = block[0]
            val unitCells = r0.filter { it.contains("단위") || it.contains("単位") || it.contains("Unit", true) }
            if (unitCells.size == 1 && r0.count { it.isNotEmpty() } == 1) {
                UnitSpec.findInText(unitCells[0])?.let { unit = it }
                block = block.drop(1)
            }
        }

        val headerCount = detectHeaderRows(block)
        val header: List<String>
        val body: List<List<String>>
        if (headerCount == 0) {
            body = block
            header = List(width) { c ->
                if (c == 0 && block.all { kind(it.getOrElse(0) { "" }, true) != Kind.N }) I18n.item() else if (width == 1) I18n.value() else I18n.valueN(c)
            }.let { h -> if (width >= 2 && h[0] == I18n.item()) h.mapIndexed { i, s -> if (i == 0) s else if (width == 2) I18n.value() else I18n.valueN(i) } else h }
            report.add(I18n.tr("머리글이 없어 열 이름을 붙였어요", "No header row, so columns were named", "見出し行がないため列名を付けました"))
        } else {
            val headRows = block.take(headerCount).map { it.toMutableList() }
            // Upper header rows: forward-fill blanks from the left (merged cells exported as blanks).
            for (h in 0 until headerCount - 1) {
                var last = ""
                for (c in 0 until width) {
                    val v = headRows[h].getOrElse(c) { "" }
                    if (v.isNotEmpty()) last = v else if (c > 0 && last.isNotEmpty()) headRows[h][c] = last
                }
            }
            header = (0 until width).map { c ->
                val parts = ArrayList<String>()
                for (h in 0 until headerCount) {
                    val v = headRows[h].getOrElse(c) { "" }
                    if (v.isNotEmpty() && (parts.isEmpty() || parts.last() != v)) parts.add(v)
                }
                parts.joinToString(" ")
            }
            body = block.drop(headerCount)
            if (headerCount > 1) report.add(I18n.tr("머리글 ${headerCount}줄을 합쳤어요", "Merged $headerCount header rows", "見出し${headerCount}行を結合しました"))
        }
        if (body.isEmpty()) return null

        var src = TableSource(title, header, body, unit, notes, report)
        src = maybeTransposeWideTime(src)
        return src
    }

    /**
     * How many top rows are header rows (0-3). A row is header-like when, in columns whose body
     * is numeric or time, the row holds text instead.
     */
    private fun detectHeaderRows(block: List<List<String>>): Int {
        val width = block.maxOf { it.size }
        if (block.size < 2) return 0
        var count = 0
        for (r in 0 until minOf(3, block.size - 1)) {
            val below = block.drop(r + 1)
            var pos = 0
            var neg = 0
            var anyTyped = false
            for (c in 0 until width) {
                val firstColYears = c == 0
                val bodyKinds = below.map { kind(it.getOrElse(c) { "" }, firstColYears) }.filter { it != Kind.E }
                if (bodyKinds.isEmpty()) continue
                val bodyKind = bodyKinds.groupingBy { it }.eachCount().maxByOrNull { it.value }!!.key
                val k = kind(block[r].getOrElse(c) { "" }, true)
                if (bodyKind == Kind.N || bodyKind == Kind.T) {
                    anyTyped = true
                    when {
                        k == Kind.S -> pos++
                        k == Kind.T && bodyKind == Kind.N -> pos++
                        k == Kind.E -> {}
                        k == bodyKind -> neg++
                        else -> {}
                    }
                }
            }
            if (!anyTyped) {
                // Pure text table: the first row is a header if its cells are filled and unique.
                if (r == 0) {
                    val row = block[0].take(width)
                    if (row.all { it.isNotEmpty() } && row.toSet().size == row.size) count = 1
                }
                break
            }
            if (pos > 0 && pos >= neg) count = r + 1 else break
        }
        return count
    }

    /** "구분 | 2020 | 2021 | 2022" with regions as rows → years as rows, regions as columns. */
    private fun maybeTransposeWideTime(src: TableSource): TableSource {
        val header = src.header
        if (header.size < 4) return src
        val timeCols = (1 until header.size).filter { TimeParser.parse(header[it], allowBareYear = true) != null }
        if (timeCols.size < 3 || timeCols.size < (header.size - 1) * 0.7) return src
        // The first column must be labels, not times.
        val firstColTimes = src.rows.count { TimeParser.parse(it.getOrElse(0) { "" }, allowBareYear = true) != null }
        if (firstColTimes > src.rows.size / 2) return src
        val numericBody = src.rows.sumOf { r -> timeCols.count { c -> KoreanNumber.parse(r.getOrElse(c) { "" }) != null } }
        if (numericBody < src.rows.size * timeCols.size * 0.6) return src
        val t = src.transposed()
        return t.copy(
            header = listOf(I18n.period()) + t.header.drop(1),
            report = src.report + I18n.tr("기간이 가로로 놓여 있어서 행과 열을 바꿨어요", "Periods ran across the top, so rows and columns were swapped", "期間が横に並んでいたため、行と列を入れ替えました"),
        )
    }
}
