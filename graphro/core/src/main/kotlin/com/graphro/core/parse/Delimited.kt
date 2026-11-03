package com.graphro.core.parse

import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CharsetDecoder
import java.nio.charset.CodingErrorAction

/** A rectangular-ish grid of strings straight from a source, before any interpretation. */
data class RawGrid(
    val name: String,
    val rows: List<List<String>>,
    /** Cell styles that mark dates in spreadsheets: (row, col) of cells holding date serials already converted. */
    val note: String? = null,
)

object TextDecode {
    /** Decodes bytes, guessing UTF-8 / UTF-16 / CP949 (EUC-KR) the way Korean files usually come. */
    fun decode(bytes: ByteArray): String {
        if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
            return String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
        }
        if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) {
            return String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE)
        }
        if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) {
            return String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
        }
        // UTF-16 without BOM: many zero bytes.
        if (bytes.size >= 4) {
            val sample = bytes.take(2000)
            val evenZeros = sample.filterIndexed { i, b -> i % 2 == 0 && b == 0.toByte() }.size
            val oddZeros = sample.filterIndexed { i, b -> i % 2 == 1 && b == 0.toByte() }.size
            if (oddZeros > sample.size / 4) return String(bytes, Charsets.UTF_16LE)
            if (evenZeros > sample.size / 4) return String(bytes, Charsets.UTF_16BE)
        }
        strict(Charsets.UTF_8, bytes)?.let { return it }
        for (name in listOf("x-windows-949", "MS949", "windows-949", "EUC-KR")) {
            val cs = runCatching { Charset.forName(name) }.getOrNull() ?: continue
            strict(cs, bytes)?.let { return it }
        }
        return String(bytes, Charsets.UTF_8)
    }

    private fun strict(cs: Charset, bytes: ByteArray): String? {
        val dec: CharsetDecoder = cs.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        return runCatching { dec.decode(ByteBuffer.wrap(bytes)).toString() }.getOrNull()
    }
}

object Delimited {

    /** Splits text into records with RFC 4180 quoting (quotes may span lines). */
    fun parseRecords(text: String, delimiter: Char): List<List<String>> {
        val out = ArrayList<List<String>>()
        var row = ArrayList<String>()
        val field = StringBuilder()
        var inQuotes = false
        var fieldStarted = false
        var i = 0
        val n = text.length
        while (i < n) {
            val c = text[i]
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < n && text[i + 1] == '"') { field.append('"'); i += 2; continue }
                    inQuotes = false; i++; continue
                }
                field.append(c); i++; continue
            }
            when {
                c == '"' && !fieldStarted && field.isEmpty() -> { inQuotes = true; fieldStarted = true; i++ }
                c == delimiter -> { row.add(field.toString()); field.setLength(0); fieldStarted = false; i++ }
                c == '\r' -> { i++ }
                c == '\n' -> {
                    row.add(field.toString()); field.setLength(0); fieldStarted = false
                    out.add(row); row = ArrayList(); i++
                }
                else -> { field.append(c); if (c != ' ') fieldStarted = true; i++ }
            }
        }
        if (field.isNotEmpty() || row.isNotEmpty()) { row.add(field.toString()); out.add(row) }
        return out
    }

    private val MD_SEPARATOR = Regex("^\\s*\\|?\\s*:?-{2,}:?\\s*(\\|\\s*:?-{2,}:?\\s*)*\\|?\\s*$")

    /**
     * Finds the delimiter that gives the most consistent table. Returns null if the text does
     * not look like a table at all.
     */
    fun sniff(text: String): RawGrid? {
        val clean = text.replace("\r\n", "\n").replace('\r', '\n')
        val lines = clean.split('\n').filter { it.isNotBlank() }
        if (lines.isEmpty()) return null

        // Markdown table
        if (lines.count { it.trim().startsWith("|") } >= lines.size * 0.6 && lines.size >= 2) {
            val rows = lines.filterNot { MD_SEPARATOR.matches(it) }.map { line ->
                var t = line.trim()
                if (t.startsWith("|")) t = t.substring(1)
                if (t.endsWith("|")) t = t.dropLast(1)
                t.split('|').map { it.trim() }
            }
            return RawGrid("표", rows)
        }

        val tabLines = lines.count { it.contains('\t') }
        if (tabLines >= lines.size * 0.5) {
            return RawGrid("표", parseRecords(clean.trim('\n'), '\t'))
        }

        val sampleText = lines.take(300).joinToString("\n")
        var best: Pair<Char, Double>? = null
        for (d in listOf(',', ';', '|')) {
            val recs = parseRecords(sampleText, d)
            val score = consistency(recs.map { it.size })
            if (score > 0 && (best == null || score > best.second + 1e-9)) best = d to score
        }
        if (best != null && best.second >= 0.6) {
            return RawGrid("표", parseRecords(clean.trim('\n'), best.first))
        }

        // Columns aligned with 2+ spaces (copied from PDFs or terminals)
        val multi = lines.map { it.trim().split(Regex("\\s{2,}")) }
        if (consistency(multi.map { it.size }) >= 0.7) return RawGrid("표", multi)

        // Single spaces, only if every line splits the same way
        val single = lines.map { it.trim().split(Regex("\\s+")) }
        val counts = single.map { it.size }
        if (counts.isNotEmpty() && counts.min() >= 2 && consistency(counts) >= 0.8 && lines.size >= 2) {
            return RawGrid("표", single)
        }
        return null
    }

    /**
     * Fraction of records whose field count equals the most common count (>1), scaled down
     * slightly when the common count is small.
     */
    private fun consistency(counts: List<Int>): Double {
        val multi = counts.filter { it > 1 }
        if (multi.isEmpty() || counts.isEmpty()) return 0.0
        val mode = multi.groupingBy { it }.eachCount().maxByOrNull { it.value }!!.key
        val same = counts.count { it == mode }
        // Title lines with one field are fine; they do not hurt much.
        val ones = counts.count { it == 1 }
        val frac = same.toDouble() / (counts.size - ones * 0.7)
        return frac.coerceAtMost(1.0) * (if (mode >= 2) 1.0 else 0.0)
    }
}
