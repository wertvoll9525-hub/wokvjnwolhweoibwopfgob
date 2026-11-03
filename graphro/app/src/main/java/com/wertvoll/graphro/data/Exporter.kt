package com.wertvoll.graphro.data

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.text.TextPaint
import androidx.core.content.FileProvider
import com.graphro.core.chart.ChartData
import com.graphro.core.chart.ChartSpec
import com.graphro.core.chart.Colors
import com.graphro.core.export.PptxWriter
import com.graphro.core.export.SheetData
import com.graphro.core.export.XlsxWriter
import com.graphro.core.model.ColumnType
import com.graphro.core.model.DataTable
import com.graphro.core.model.Value
import com.graphro.core.text.Caption
import com.wertvoll.graphro.chart.ChartFonts
import com.wertvoll.graphro.chart.ChartRenderer
import com.wertvoll.graphro.chart.TextBlock
import java.io.ByteArrayOutputStream
import java.io.File

enum class ExportFormat(val ext: String, val mime: String, val label: String) {
    IMAGE("png", "image/png", "이미지"),
    PPT("pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation", "PPT"),
    SHEET("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "엑셀"),
}

data class ExportOptions(
    // image
    val headline: Boolean = true,
    val vertical: Boolean = true,
    val showSource: Boolean = true,
    // ppt
    val caption: Boolean = true,
    val notes: Boolean = true,
    val tableSlide: Boolean = false,
    // sheet
    val fixesSheet: Boolean = true,
    val rawSheet: Boolean = false,
)

object Exporter {

    fun fileName(spec: ChartSpec, f: ExportFormat): String {
        val base = spec.title.replace(Regex("[\\\\/:*?\"<>|\\n\\r\\t]"), " ").trim().take(60).ifEmpty { "그래프로 차트" }
        return "$base.${f.ext}"
    }

    fun build(ctx: Context, f: ExportFormat, table: DataTable, spec: ChartSpec, opt: ExportOptions, rawText: String?): ByteArray {
        val d = ChartData.build(table, spec)
        val caption = if (spec.showCaption) Caption.make(d, spec) else ""
        return when (f) {
            ExportFormat.IMAGE -> {
                val bmp = renderImage(ctx, d, spec, caption, opt)
                val bos = ByteArrayOutputStream()
                bmp.compress(Bitmap.CompressFormat.PNG, 100, bos)
                bmp.recycle()
                bos.toByteArray()
            }
            ExportFormat.PPT -> PptxWriter.write(
                d, spec,
                PptxWriter.Options(
                    caption = caption.takeIf { opt.caption && it.isNotBlank() },
                    speakerNotes = if (opt.notes) PptxWriter.speakerNotes(d, caption) else null,
                    table = if (opt.tableSlide) tableRows(table, display = true) else null,
                ),
            )
            ExportFormat.SHEET -> {
                val sheets = ArrayList<SheetData>()
                sheets += SheetData("데이터", tableValues(table))
                if (opt.fixesSheet) {
                    val fixes = table.fixes()
                    val rows = mutableListOf<List<Any?>>(listOf("행", "열", "원문", "앱이 읽은 값", "설명"))
                    fixes.forEach { f -> val c = table.columns[f.column]; rows += listOf(f.row + 2.0, c.name, c.cells[f.row].raw, c.scaled(f.row), f.message) }
                    table.excludedRows.forEach { (r, why) -> rows += listOf(r + 2.0, "(행 전체)", table.columns.firstOrNull()?.cells?.getOrNull(r)?.raw, null, why) }
                    if (rows.size > 1) sheets += SheetData("고친 곳", rows)
                }
                if (opt.rawSheet && !rawText.isNullOrBlank()) {
                    sheets += SheetData("원문", rawText.lines().map { listOf(it) }, headerRow = false, colWidths = listOf(80.0))
                }
                XlsxWriter.write(sheets)
            }
        }
    }

    /** Table as text, values shown in the column's display scale. */
    fun tableRows(t: DataTable, display: Boolean): List<List<String>> {
        val header = t.columns.map { it.displayName }
        val rows = (0 until t.rowCount).map { r ->
            t.columns.map { c ->
                when (val v = c.cells[r].value) {
                    is Value.Num -> com.graphro.core.text.Format.plain(v.v / c.scale.factor)
                    is Value.Time -> v.t.fullLabel()
                    is Value.Text -> v.s
                    Value.Empty -> ""
                }
            }
        }
        return listOf(header) + rows
    }

    private fun tableValues(t: DataTable): List<List<Any?>> {
        val header: List<Any?> = t.columns.map { it.displayName }
        val rows = (0 until t.rowCount).map { r ->
            t.columns.map { c ->
                when (val v = c.cells[r].value) {
                    is Value.Num -> v.v / c.scale.factor
                    is Value.Time -> v.t.fullLabel()
                    is Value.Text -> v.s
                    Value.Empty -> null
                }
            }
        }
        return listOf(header) + rows
    }

    /** The shareable image: meta line, headline, chart, caption and source on white. */
    fun renderImage(ctx: Context, d: ChartData, spec: ChartSpec, caption: String, opt: ExportOptions, widthPx: Int = 1080): Bitmap {
        val w = widthPx
        val h = if (opt.vertical) (w * 5 / 4) else (w * 9 / 16)
        val u = w / 390f
        val fonts = ChartFonts.get(ctx)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.WHITE)
        val pad = 24f * u
        val inner = (w - 2 * pad).toInt()
        var y = pad + 4 * u

        val meta = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = fonts.mono; textSize = 9.5f * u; color = Colors.SUB; letterSpacing = 0.08f }
        c.drawText(metaLine(d).uppercase(), pad, y + meta.textSize, meta)
        y += meta.textSize + 10 * u

        val headlineText = if (opt.headline && caption.isNotBlank()) caption.substringBefore(". ").removeSuffix(".").let { if (it.length > 44) spec.title else it } else spec.title
        val title = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = fonts.sansBold; textSize = (if (opt.vertical) 21f else 19f) * u; color = Colors.INK; letterSpacing = -0.03f }
        y += TextBlock.draw(c, headlineText, title, pad, y, inner, maxLines = 2, spacing = 1.18f)
        val sub = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = fonts.sans; textSize = 11.5f * u; color = Colors.BODY }
        val subText = buildString {
            if (headlineText != spec.title) append(spec.title)
            if (d.unitLabel.isNotEmpty()) { if (isNotEmpty()) append(" · "); append("단위: ${d.unitLabel}") }
        }
        if (subText.isNotEmpty()) { y += 6 * u; y += TextBlock.draw(c, subText, sub, pad, y, inner, maxLines = 1) }
        y += 14 * u

        val captionPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = fonts.sans; textSize = 12.5f * u; color = Colors.BODY }
        val bodyCaption = if (opt.headline && headlineText != spec.title) caption.substringAfter(". ", "").trim() else caption
        val capH = if (bodyCaption.isNotBlank()) TextBlock.height(bodyCaption, captionPaint, inner, maxLines = 3) + 14 * u else 0f
        val source = spec.source?.takeIf { opt.showSource && it.isNotBlank() }
        val footPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = fonts.mono; textSize = 8.5f * u; color = 0xFF9A9AA0.toInt(); letterSpacing = 0.04f }
        val footH = if (source != null) footPaint.textSize + 10 * u else 0f
        val chartH = h - y - pad - capH - footH
        val renderer = ChartRenderer(fonts, u)
        c.save(); c.translate(pad, y)
        renderer.draw(c, d, spec, inner.toFloat(), chartH)
        c.restore()
        y += chartH + 14 * u
        if (bodyCaption.isNotBlank()) { TextBlock.draw(c, bodyCaption, captionPaint, pad, y, inner, maxLines = 3); y += capH }
        if (source != null) c.drawText("출처: $source", pad, h - pad, footPaint)
        return bmp
    }

    fun metaLine(d: ChartData): String {
        val range = if (d.isTime && d.size >= 2) "${d.fullCategories.first()} – ${d.fullCategories.last()}" else "${d.size} ITEMS"
        return listOf(range, d.unitLabel).filter { it.isNotEmpty() }.joinToString(" · ")
    }

    fun writeToCache(ctx: Context, name: String, bytes: ByteArray): File {
        val dir = File(ctx.cacheDir, "exports").apply { mkdirs() }
        dir.listFiles()?.forEach { if (System.currentTimeMillis() - it.lastModified() > 24 * 3600_000L) it.delete() }
        val f = File(dir, name)
        f.writeBytes(bytes)
        return f
    }

    fun shareIntent(ctx: Context, file: File, f: ExportFormat): Intent {
        val uri: Uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = f.mime
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri(file.name, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(send, "공유").apply { addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }
    }
}
