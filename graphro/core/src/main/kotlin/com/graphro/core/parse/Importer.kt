package com.graphro.core.parse

import com.graphro.core.json.Json
import com.graphro.core.model.ColumnType
import com.graphro.core.model.DataTable

/** One table found in an input, with where it came from. */
data class Candidate(val sheet: String, val source: TableSource, val table: DataTable, val score: Double) {
    val label: String get() = listOfNotNull(sheet.takeIf { it.isNotBlank() }, source.title).joinToString(" · ").ifEmpty { "표" }
}

data class ImportResult(val candidates: List<Candidate>, val error: String? = null) {
    val best: Candidate? get() = candidates.maxByOrNull { it.score }
}

object Importer {

    const val MAX_BYTES = 30L * 1024 * 1024

    fun fromText(text: String, name: String = ""): ImportResult {
        val t = text.removePrefix("﻿")
        if (t.isBlank()) return ImportResult(emptyList(), "붙여넣은 내용이 비어 있어요")
        val trimmed = t.trim()
        if (trimmed.startsWith("[") || trimmed.startsWith("{")) {
            runCatching { fromJson(trimmed, name) }.getOrNull()?.let { if (it.candidates.isNotEmpty()) return it }
        }
        // Read the text both as a table and as loose sentences, then keep whichever reads better.
        val asGrid = Delimited.sniff(t)?.let { grid -> candidatesFrom(grid.copy(name = name)) }.orEmpty()
            .filter { it.table.rowCount > 0 && it.table.columns.isNotEmpty() }
        val asText = FreeText.parse(t)?.let { grid -> candidatesFrom(grid.copy(name = name)).map { it.copy(score = it.score - 0.02) } }.orEmpty()
            .filter { it.table.rowCount > 0 && it.table.columns.isNotEmpty() }
        val good = if ((asText.maxOfOrNull { it.score } ?: -1.0) > (asGrid.maxOfOrNull { it.score } ?: -1.0)) asText else asGrid
        if (good.isEmpty()) return ImportResult(emptyList(), "표 모양을 찾지 못했어요. 엑셀에서 표를 복사하거나 ‘항목 숫자’처럼 줄마다 적어 주세요")
        return ImportResult(dedupe(good))
    }

    fun fromFile(fileName: String, bytes: ByteArray): ImportResult {
        if (bytes.size > MAX_BYTES) return ImportResult(emptyList(), "파일이 너무 커요 (30MB까지)")
        val lower = fileName.lowercase()
        val isZip = bytes.size > 4 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()
        val isOle = bytes.size > 8 && bytes[0] == 0xD0.toByte() && bytes[1] == 0xCF.toByte() && bytes[2] == 0x11.toByte() && bytes[3] == 0xE0.toByte()
        return try {
            when {
                isOle || lower.endsWith(".xls") && !isZip ->
                    ImportResult(emptyList(), "엑셀 97-2003(.xls) 파일은 아직 열 수 없어요. 엑셀에서 ‘다른 이름으로 저장 → .xlsx’ 후 다시 열어 주세요")
                lower.endsWith(".hwp") || lower.endsWith(".hwpx") ->
                    ImportResult(emptyList(), "한글 문서는 바로 열 수 없어요. 문서에서 표를 복사해 붙여넣어 주세요")
                isZip && (lower.endsWith(".xlsx") || lower.endsWith(".xlsm") || !lower.contains('.') || lower.endsWith(".xltx")) -> fromXlsx(bytes)
                isZip -> runCatching { fromXlsx(bytes) }.getOrElse { ImportResult(emptyList(), "이 압축 파일 형식은 열 수 없어요") }
                lower.endsWith(".json") -> fromJson(TextDecode.decode(bytes), fileName)
                else -> fromText(TextDecode.decode(bytes), fileName.substringBeforeLast('.'))
            }
        } catch (e: XlsxReader.XlsxException) {
            ImportResult(emptyList(), e.message)
        } catch (e: Exception) {
            ImportResult(emptyList(), "파일을 읽는 중 문제가 생겼어요")
        }
    }

    private fun fromXlsx(bytes: ByteArray): ImportResult {
        val grids = XlsxReader.read(bytes)
        val out = grids.flatMap { candidatesFrom(it) }
        if (out.isEmpty()) return ImportResult(emptyList(), "시트에서 표를 찾지 못했어요")
        return ImportResult(out)
    }

    private fun fromJson(text: String, name: String): ImportResult {
        val root = Json.parse(text)
        val rows: List<Map<*, *>> = when (root) {
            is List<*> -> root.filterIsInstance<Map<*, *>>()
            is Map<*, *> -> {
                val arr = root.values.firstOrNull { it is List<*> && it.firstOrNull() is Map<*, *> } as? List<*>
                if (arr != null) arr.filterIsInstance<Map<*, *>>()
                else if (root.values.all { it is List<*> }) {
                    // { "월": [...], "매출": [...] }
                    val cols = root.entries.map { it.key.toString() to (it.value as List<*>) }
                    val n = cols.maxOf { it.second.size }
                    (0 until n).map { r -> cols.associate { (k, v) -> k to v.getOrNull(r) } }
                } else emptyList()
            }
            else -> emptyList()
        }
        if (rows.isEmpty()) return ImportResult(emptyList(), "JSON에서 표를 찾지 못했어요")
        val keys = LinkedHashSet<String>()
        rows.forEach { r -> r.keys.forEach { keys.add(it.toString()) } }
        val header = keys.toList()
        val body = rows.map { r -> header.map { k -> jsonCell(r[k]) } }
        val grid = RawGrid(name, listOf(header) + body)
        return ImportResult(candidatesFrom(grid))
    }

    private fun jsonCell(v: Any?): String = when (v) {
        null -> ""
        is Double -> XlsxReader.formatNumber(v)
        is Map<*, *>, is List<*> -> Json.stringify(v)
        else -> v.toString()
    }

    fun candidatesFrom(grid: RawGrid): List<Candidate> =
        GridAnalyzer.analyze(grid).map { src ->
            val table = TableBuilder.build(src)
            Candidate(grid.name, src, table, score(table))
        }

    /**
     * How chart-ready a table is: share of cleanly read numeric cells, a label column, and size.
     */
    fun score(t: DataTable): Double {
        if (t.rowCount == 0) return 0.0
        val num = t.columns.filter { it.type == ColumnType.NUMBER }
        if (num.isEmpty()) return 0.05
        val label = t.columns.any { it.type != ColumnType.NUMBER }
        val cells = num.sumOf { it.cells.size }
        val bad = num.sumOf { c -> c.cells.count { it.fix?.startsWith("숫자로 읽을 수 없") == true } }
        val clean = 1.0 - bad.toDouble() / cells
        val emptyNames = t.columns.count { it.name.startsWith("열 ") || it.name.startsWith("값 ") }
        var s = 0.55 * clean + (if (label) 0.25 else 0.0) + 0.1 * minOf(1.0, t.rowCount / 4.0) + 0.1 * minOf(1.0, num.size / 2.0)
        s -= 0.03 * emptyNames
        // Many text columns that look like split sentences are a bad sign.
        val textCols = t.columns.filter { it.type == ColumnType.TEXT }
        if (textCols.size > num.size + 1) s -= 0.2
        // Text cells that still carry numbers ("1월 매출 320만") mean the split went wrong.
        val sentenceLike = textCols.sumOf { c -> c.cells.count { cell -> SENTENCE_NUMBER.containsMatchIn(cell.raw) && cell.raw.length > 5 } }
        val textCells = textCols.sumOf { c -> c.cells.count { it.raw.isNotBlank() } }
        if (textCells > 0) s -= 0.5 * sentenceLike / textCells
        return s.coerceIn(0.0, 1.0)
    }

    private val SENTENCE_NUMBER = Regex("[가-힣A-Za-z]\\s*\\d|\\d\\s*[만억천]|\\d{3,}")

    private fun dedupe(list: List<Candidate>): List<Candidate> {
        val seen = HashSet<String>()
        return list.sortedByDescending { it.score }.filter { c ->
            val key = c.table.columns.joinToString("|") { it.name } + c.table.rowCount
            seen.add(key)
        }
    }
}
