package com.graphro.core.parse

import com.graphro.core.text.I18n

import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler
import java.io.ByteArrayInputStream
import java.math.BigDecimal
import java.math.MathContext
import java.util.zip.ZipInputStream
import javax.xml.parsers.SAXParserFactory

/**
 * Minimal, dependency-free .xlsx reader. Produces one [RawGrid] per visible sheet with cell text
 * as Excel would show it for the formats that matter for charts (dates, percents).
 */
object XlsxReader {

    class XlsxException(message: String) : Exception(message)

    private const val MAX_ENTRY = 64L * 1024 * 1024

    fun read(bytes: ByteArray, maxRows: Int = 20_000, maxCols: Int = 200): List<RawGrid> {
        val entries = unzip(bytes)
        val workbook = entries["xl/workbook.xml"] ?: throw XlsxException(I18n.tr("엑셀 파일 구조를 찾을 수 없어요", "This doesn't look like an Excel file", "Excelファイルの構造が見つかりません"))
        val rels = entries["xl/_rels/workbook.xml.rels"]?.let { parseRels(it) } ?: emptyMap()
        val shared = entries["xl/sharedStrings.xml"]?.let { parseShared(it) } ?: emptyList()
        val styles = entries["xl/styles.xml"]?.let { parseStyles(it) } ?: Styles(emptyList(), emptyMap())
        val wb = parseWorkbook(workbook)

        val grids = ArrayList<RawGrid>()
        for (sheet in wb.sheets) {
            if (sheet.hidden) continue
            var target = rels[sheet.rid] ?: continue
            target = target.removePrefix("/")
            if (!target.startsWith("xl/")) target = "xl/$target"
            val data = entries[target] ?: continue
            val grid = parseSheet(data, shared, styles, wb.date1904, maxRows, maxCols)
            if (grid.isNotEmpty()) grids.add(RawGrid(sheet.name, grid))
        }
        if (grids.isEmpty()) throw XlsxException(I18n.tr("내용이 있는 시트가 없어요", "No sheet has any content", "内容のあるシートがありません"))
        return grids
    }

    private fun unzip(bytes: ByteArray): Map<String, ByteArray> {
        val out = HashMap<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zin ->
            while (true) {
                val e = zin.nextEntry ?: break
                val name = e.name
                if (e.isDirectory) continue
                if (!(name.endsWith(".xml") || name.endsWith(".rels"))) continue
                if (!name.startsWith("xl/") && name != "[Content_Types].xml") continue
                val buf = java.io.ByteArrayOutputStream()
                val chunk = ByteArray(16 * 1024)
                var total = 0L
                while (true) {
                    val n = zin.read(chunk)
                    if (n < 0) break
                    total += n
                    if (total > MAX_ENTRY) throw XlsxException(I18n.tr("엑셀 파일이 너무 커요", "The Excel file is too large", "Excelファイルが大きすぎます"))
                    buf.write(chunk, 0, n)
                }
                out[name] = buf.toByteArray()
            }
        }
        return out
    }

    private fun sax(data: ByteArray, handler: DefaultHandler) {
        val f = SAXParserFactory.newInstance()
        f.isNamespaceAware = false
        runCatching { f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        runCatching { f.setFeature("http://xml.org/sax/features/external-general-entities", false) }
        runCatching { f.setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
        f.newSAXParser().parse(ByteArrayInputStream(data), handler)
    }

    private fun local(qName: String) = qName.substringAfter(':')

    private data class SheetRef(val name: String, val rid: String, val hidden: Boolean)
    private data class Workbook(val sheets: List<SheetRef>, val date1904: Boolean)

    private fun parseWorkbook(data: ByteArray): Workbook {
        val sheets = ArrayList<SheetRef>()
        var d1904 = false
        sax(data, object : DefaultHandler() {
            override fun startElement(uri: String?, localName: String?, qName: String, a: Attributes) {
                when (local(qName)) {
                    "sheet" -> {
                        var rid = ""
                        for (i in 0 until a.length) if (local(a.getQName(i)) == "id") rid = a.getValue(i)
                        val state = a.getValue("state") ?: ""
                        sheets.add(SheetRef(a.getValue("name") ?: "Sheet", rid, state == "hidden" || state == "veryHidden"))
                    }
                    "workbookPr" -> d1904 = a.getValue("date1904").let { it == "1" || it == "true" }
                }
            }
        })
        return Workbook(sheets, d1904)
    }

    private fun parseRels(data: ByteArray): Map<String, String> {
        val m = HashMap<String, String>()
        sax(data, object : DefaultHandler() {
            override fun startElement(uri: String?, localName: String?, qName: String, a: Attributes) {
                if (local(qName) == "Relationship") {
                    val id = a.getValue("Id"); val t = a.getValue("Target")
                    if (id != null && t != null) m[id] = t
                }
            }
        })
        return m
    }

    private fun parseShared(data: ByteArray): List<String> {
        val out = ArrayList<String>()
        sax(data, object : DefaultHandler() {
            val sb = StringBuilder()
            var inT = false
            var inPh = false
            override fun startElement(uri: String?, localName: String?, qName: String, a: Attributes) {
                when (local(qName)) {
                    "si" -> sb.setLength(0)
                    "t" -> inT = true
                    "rPh" -> inPh = true
                }
            }
            override fun endElement(uri: String?, localName: String?, qName: String) {
                when (local(qName)) {
                    "si" -> out.add(sb.toString())
                    "t" -> inT = false
                    "rPh" -> inPh = false
                }
            }
            override fun characters(ch: CharArray, start: Int, length: Int) {
                if (inT && !inPh) sb.appendRange(ch, start, start + length)
            }
        })
        return out
    }

    private enum class Fmt { GENERAL, DATE, DATE_MONTH, PERCENT, TEXT }
    private data class Styles(val xfFormats: List<Fmt>, val decimals: Map<Int, Int>)

    private val BUILTIN_DATE = setOf(14, 15, 16, 17, 22, 27, 28, 29, 30, 31, 34, 35, 36, 50, 51, 52, 53, 54, 57, 58)
    private val BUILTIN_MONTH = setOf(17, 55, 56)

    private fun classify(code: String): Fmt {
        // Drop quoted text, escapes and [color]/[locale] blocks before looking for date tokens.
        val c = code.replace(Regex("\"[^\"]*\""), "").replace(Regex("\\\\."), "").replace(Regex("\\[[^\\]]*\\]"), "").lowercase()
        if (c == "@") return Fmt.TEXT
        if (c.contains('%')) return Fmt.PERCENT
        if (c == "general") return Fmt.GENERAL
        val hasY = c.contains('y'); val hasD = c.contains('d'); val hasM = c.contains('m')
        return when {
            hasY && hasM && !hasD -> Fmt.DATE_MONTH
            hasY || hasD -> Fmt.DATE
            else -> Fmt.GENERAL
        }
    }

    private fun parseStyles(data: ByteArray): Styles {
        val custom = HashMap<Int, String>()
        val xf = ArrayList<Fmt>()
        sax(data, object : DefaultHandler() {
            var inCellXfs = false
            override fun startElement(uri: String?, localName: String?, qName: String, a: Attributes) {
                when (local(qName)) {
                    "numFmt" -> {
                        val id = a.getValue("numFmtId")?.toIntOrNull(); val code = a.getValue("formatCode")
                        if (id != null && code != null) custom[id] = code
                    }
                    "cellXfs" -> inCellXfs = true
                    "xf" -> if (inCellXfs) {
                        val id = a.getValue("numFmtId")?.toIntOrNull() ?: 0
                        val fmt = when {
                            custom.containsKey(id) -> classify(custom[id]!!)
                            id in BUILTIN_MONTH -> Fmt.DATE_MONTH
                            id in BUILTIN_DATE -> Fmt.DATE
                            id == 9 || id == 10 -> Fmt.PERCENT
                            id == 49 -> Fmt.TEXT
                            else -> Fmt.GENERAL
                        }
                        xf.add(fmt)
                    }
                }
            }
            override fun endElement(uri: String?, localName: String?, qName: String) {
                if (local(qName) == "cellXfs") inCellXfs = false
            }
        })
        return Styles(xf, emptyMap())
    }

    private fun colIndex(ref: String): Int {
        var n = 0
        for (ch in ref) {
            if (ch in 'A'..'Z') n = n * 26 + (ch - 'A' + 1)
            else if (ch in 'a'..'z') n = n * 26 + (ch - 'a' + 1)
            else break
        }
        return n - 1
    }

    private fun rowIndex(ref: String): Int = ref.dropWhile { it.isLetter() }.toIntOrNull()?.minus(1) ?: -1

    fun formatNumber(v: Double): String {
        if (v.isNaN() || v.isInfinite()) return ""
        val bd = BigDecimal(v).round(MathContext(15)).stripTrailingZeros()
        val s = bd.toPlainString()
        return if (s == "-0") "0" else s
    }

    private fun parseSheet(
        data: ByteArray, shared: List<String>, styles: Styles, date1904: Boolean, maxRows: Int, maxCols: Int,
    ): List<List<String>> {
        val cells = HashMap<Long, String>()
        val merges = ArrayList<IntArray>()
        var maxR = -1
        var maxC = -1
        sax(data, object : DefaultHandler() {
            var ref = ""
            var type = ""
            var style = 0
            val v = StringBuilder()
            var inV = false
            var inIsT = false
            var inIs = false
            var autoCol = 0
            var curRow = 0
            override fun startElement(uri: String?, localName: String?, qName: String, a: Attributes) {
                when (local(qName)) {
                    "row" -> { curRow = (a.getValue("r")?.toIntOrNull() ?: (curRow + 1)) - 1; autoCol = 0 }
                    "c" -> {
                        ref = a.getValue("r") ?: ""
                        type = a.getValue("t") ?: ""
                        style = a.getValue("s")?.toIntOrNull() ?: 0
                        v.setLength(0)
                    }
                    "v" -> inV = true
                    "is" -> inIs = true
                    "t" -> if (inIs) inIsT = true
                    "mergeCell" -> a.getValue("ref")?.let { r ->
                        val parts = r.split(':')
                        if (parts.size == 2) merges.add(intArrayOf(rowIndex(parts[0]), colIndex(parts[0]), rowIndex(parts[1]), colIndex(parts[1])))
                    }
                }
            }
            override fun endElement(uri: String?, localName: String?, qName: String) {
                when (local(qName)) {
                    "v" -> inV = false
                    "t" -> inIsT = false
                    "is" -> inIs = false
                    "c" -> {
                        val r = if (ref.isNotEmpty()) rowIndex(ref) else curRow
                        val c = if (ref.isNotEmpty()) colIndex(ref) else autoCol
                        autoCol = c + 1
                        if (r < 0 || c < 0 || r >= maxRows || c >= maxCols) return
                        val raw = v.toString()
                        val text = when (type) {
                            "s" -> raw.trim().toIntOrNull()?.let { shared.getOrNull(it) } ?: ""
                            "inlineStr", "str" -> raw
                            "b" -> if (raw == "1") "TRUE" else "FALSE"
                            "e" -> ""
                            else -> {
                                val d = raw.trim().toDoubleOrNull()
                                if (d == null) raw else when (styles.xfFormats.getOrElse(style) { Fmt.GENERAL }) {
                                    Fmt.DATE -> TimeParser.fromExcelSerial(d, date1904)?.let { "%04d-%02d-%02d".format(it.year, it.sub, it.day) } ?: formatNumber(d)
                                    Fmt.DATE_MONTH -> TimeParser.fromExcelSerial(d, date1904)?.let { "%04d-%02d".format(it.year, it.sub) } ?: formatNumber(d)
                                    Fmt.PERCENT -> formatNumber(d * 100) + "%"
                                    else -> formatNumber(d)
                                }
                            }
                        }
                        if (text.isNotEmpty()) {
                            cells[(r.toLong() shl 20) or c.toLong()] = text
                            if (r > maxR) maxR = r
                            if (c > maxC) maxC = c
                        }
                    }
                }
            }
            override fun characters(ch: CharArray, start: Int, length: Int) {
                if (inV || inIsT) v.appendRange(ch, start, start + length)
            }
        })
        if (maxR < 0) return emptyList()
        // Fill merged ranges with the top-left value.
        for (m in merges) {
            val (r1, c1, r2, c2) = m.toList()
            if (r1 < 0 || c1 < 0) continue
            val top = cells[(r1.toLong() shl 20) or c1.toLong()] ?: continue
            for (r in r1..minOf(r2, maxRows - 1)) for (c in c1..minOf(c2, maxCols - 1)) {
                val k = (r.toLong() shl 20) or c.toLong()
                if (!cells.containsKey(k)) cells[k] = top
                if (r > maxR) maxR = r
                if (c > maxC) maxC = c
            }
        }
        return (0..maxR).map { r -> (0..maxC).map { c -> cells[(r.toLong() shl 20) or c.toLong()] ?: "" } }
    }
}
