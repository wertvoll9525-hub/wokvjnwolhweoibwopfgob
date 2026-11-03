package com.graphro.core.export

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

internal object Xml {
    fun esc(s: String): String {
        val sb = StringBuilder(s.length + 8)
        for (c in s) {
            when (c) {
                '&' -> sb.append("&amp;"); '<' -> sb.append("&lt;"); '>' -> sb.append("&gt;")
                '"' -> sb.append("&quot;"); '\'' -> sb.append("&apos;")
                else -> if (c == '\t' || c == '\n' || c == '\r' || c >= ' ') sb.append(c)
            }
        }
        return sb.toString()
    }

    const val HEAD = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"

    fun num(v: Double): String {
        if (v.isNaN() || v.isInfinite()) return "0"
        val l = v.toLong()
        return if (v == l.toDouble() && kotlin.math.abs(v) < 1e15) l.toString() else java.math.BigDecimal(v).round(java.math.MathContext(15)).stripTrailingZeros().toPlainString()
    }
}

/** Writes a zip with fixed timestamps so output is reproducible. */
internal class ZipBuilder {
    private val bos = ByteArrayOutputStream()
    private val zip = ZipOutputStream(bos)
    fun add(name: String, text: String) = add(name, text.toByteArray(Charsets.UTF_8))
    fun add(name: String, bytes: ByteArray) {
        val e = ZipEntry(name)
        e.time = 1_700_000_000_000L
        zip.putNextEntry(e)
        zip.write(bytes)
        zip.closeEntry()
    }
    fun bytes(): ByteArray { zip.close(); return bos.toByteArray() }
}

/** A sheet of cells: String, Double/Number, or null. Row 0 is bold when [headerRow] is set. */
data class SheetData(val name: String, val rows: List<List<Any?>>, val headerRow: Boolean = true, val colWidths: List<Double> = emptyList())

object XlsxWriter {

    fun write(sheets: List<SheetData>): ByteArray {
        val z = ZipBuilder()
        z.add("[Content_Types].xml", Xml.HEAD + buildString {
            append("<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">")
            append("<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>")
            append("<Default Extension=\"xml\" ContentType=\"application/xml\"/>")
            append("<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>")
            append("<Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>")
            sheets.indices.forEach { append("<Override PartName=\"/xl/worksheets/sheet${it + 1}.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>") }
            append("<Override PartName=\"/docProps/core.xml\" ContentType=\"application/vnd.openxmlformats-package.core-properties+xml\"/>")
            append("<Override PartName=\"/docProps/app.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.extended-properties+xml\"/>")
            append("</Types>")
        })
        z.add("_rels/.rels", Xml.HEAD + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
            "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/>" +
            "<Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/package/2006/relationships/metadata/core-properties\" Target=\"docProps/core.xml\"/>" +
            "<Relationship Id=\"rId3\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/extended-properties\" Target=\"docProps/app.xml\"/>" +
            "</Relationships>")
        z.add("docProps/core.xml", Xml.HEAD + "<cp:coreProperties xmlns:cp=\"http://schemas.openxmlformats.org/package/2006/metadata/core-properties\" xmlns:dc=\"http://purl.org/dc/elements/1.1/\" xmlns:dcterms=\"http://purl.org/dc/terms/\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"><dc:creator>그래프로</dc:creator></cp:coreProperties>")
        z.add("docProps/app.xml", Xml.HEAD + "<Properties xmlns=\"http://schemas.openxmlformats.org/officeDocument/2006/extended-properties\"><Application>Graphro</Application></Properties>")
        z.add("xl/workbook.xml", Xml.HEAD + buildString {
            append("<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"><sheets>")
            sheets.forEachIndexed { i, s -> append("<sheet name=\"${Xml.esc(safeSheetName(s.name, i))}\" sheetId=\"${i + 1}\" r:id=\"rId${i + 1}\"/>") }
            append("</sheets></workbook>")
        })
        z.add("xl/_rels/workbook.xml.rels", Xml.HEAD + buildString {
            append("<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">")
            sheets.indices.forEach { append("<Relationship Id=\"rId${it + 1}\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet${it + 1}.xml\"/>") }
            append("<Relationship Id=\"rId${sheets.size + 1}\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/>")
            append("</Relationships>")
        })
        z.add("xl/styles.xml", Xml.HEAD + "<styleSheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">" +
            "<numFmts count=\"1\"><numFmt numFmtId=\"164\" formatCode=\"#,##0.##\"/></numFmts>" +
            "<fonts count=\"2\"><font><sz val=\"11\"/><name val=\"맑은 고딕\"/><family val=\"2\"/></font><font><b/><sz val=\"11\"/><name val=\"맑은 고딕\"/><family val=\"2\"/></font></fonts>" +
            "<fills count=\"2\"><fill><patternFill patternType=\"none\"/></fill><fill><patternFill patternType=\"gray125\"/></fill></fills>" +
            "<borders count=\"1\"><border><left/><right/><top/><bottom/><diagonal/></border></borders>" +
            "<cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs>" +
            "<cellXfs count=\"3\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/>" +
            "<xf numFmtId=\"0\" fontId=\"1\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyFont=\"1\"/>" +
            "<xf numFmtId=\"164\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyNumberFormat=\"1\"/></cellXfs>" +
            "<cellStyles count=\"1\"><cellStyle name=\"Normal\" xfId=\"0\" builtinId=\"0\"/></cellStyles></styleSheet>")
        sheets.forEachIndexed { i, s -> z.add("xl/worksheets/sheet${i + 1}.xml", sheetXml(s)) }
        return z.bytes()
    }

    private fun safeSheetName(name: String, i: Int): String {
        val n = name.replace(Regex("[\\[\\]:*?/\\\\]"), " ").trim().take(31)
        return n.ifEmpty { "Sheet${i + 1}" }
    }

    fun colName(c: Int): String {
        var n = c + 1
        val sb = StringBuilder()
        while (n > 0) { val m = (n - 1) % 26; sb.append('A' + m); n = (n - 1) / 26 }
        return sb.reverse().toString()
    }

    private fun sheetXml(s: SheetData): String = Xml.HEAD + buildString {
        append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">")
        val width = s.rows.maxOfOrNull { it.size } ?: 0
        if (width > 0) {
            append("<cols>")
            for (c in 0 until width) {
                val w = s.colWidths.getOrNull(c) ?: run {
                    val maxLen = s.rows.maxOfOrNull { r -> displayLen(r.getOrNull(c)) } ?: 8
                    (maxLen + 2).coerceIn(8, 40).toDouble()
                }
                append("<col min=\"${c + 1}\" max=\"${c + 1}\" width=\"$w\" customWidth=\"1\"/>")
            }
            append("</cols>")
        }
        append("<sheetData>")
        s.rows.forEachIndexed { r, row ->
            append("<row r=\"${r + 1}\">")
            row.forEachIndexed { c, v ->
                val ref = "${colName(c)}${r + 1}"
                val bold = s.headerRow && r == 0
                when (v) {
                    null -> {}
                    is Number -> {
                        val d = v.toDouble()
                        if (!d.isNaN() && !d.isInfinite()) append("<c r=\"$ref\"${if (bold) " s=\"1\"" else " s=\"2\""}><v>${Xml.num(d)}</v></c>")
                    }
                    else -> {
                        val t = v.toString()
                        if (t.isNotEmpty()) append("<c r=\"$ref\" t=\"inlineStr\"${if (bold) " s=\"1\"" else ""}><is><t xml:space=\"preserve\">${Xml.esc(t)}</t></is></c>")
                    }
                }
            }
            append("</row>")
        }
        append("</sheetData></worksheet>")
    }

    private fun displayLen(v: Any?): Int = when (v) {
        null -> 0
        is Number -> Xml.num(v.toDouble()).length + 2
        else -> v.toString().sumOf { ch -> if (ch.code > 0x2E80) 2 else 1 }.toInt()
    }
}

object CsvWriter {
    /** UTF-8 with BOM so Excel opens Korean text correctly. */
    fun write(rows: List<List<Any?>>): ByteArray {
        val sb = StringBuilder("﻿")
        for (row in rows) {
            sb.append(row.joinToString(",") { cell(it) }).append("\r\n")
        }
        return sb.toString().toByteArray(Charsets.UTF_8)
    }

    private fun cell(v: Any?): String {
        val s = when (v) {
            null -> ""
            is Number -> Xml.num(v.toDouble())
            else -> v.toString()
        }
        return if (s.any { it == ',' || it == '"' || it == '\n' || it == '\r' } || s.startsWith(" ") || s.endsWith(" ")) "\"" + s.replace("\"", "\"\"") + "\"" else s
    }
}
