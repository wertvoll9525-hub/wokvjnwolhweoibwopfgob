package com.wertvoll.graphro.chart

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.res.ResourcesCompat
import com.graphro.core.chart.ChartData
import com.graphro.core.chart.ChartSpec
import com.graphro.core.chart.ChartType
import com.graphro.core.chart.Colors
import com.graphro.core.text.Format
import com.wertvoll.graphro.R
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

class ChartFonts(val sans: Typeface, val sansSemi: Typeface, val sansBold: Typeface, val mono: Typeface, val monoSemi: Typeface) {
    companion object {
        @Volatile private var cached: ChartFonts? = null
        fun get(ctx: Context): ChartFonts = cached ?: synchronized(this) {
            cached ?: ChartFonts(
                ResourcesCompat.getFont(ctx, R.font.pretendard_regular) ?: Typeface.DEFAULT,
                ResourcesCompat.getFont(ctx, R.font.pretendard_semibold) ?: Typeface.DEFAULT_BOLD,
                ResourcesCompat.getFont(ctx, R.font.pretendard_bold) ?: Typeface.DEFAULT_BOLD,
                ResourcesCompat.getFont(ctx, R.font.plexmono_regular) ?: Typeface.MONOSPACE,
                ResourcesCompat.getFont(ctx, R.font.plexmono_semibold) ?: Typeface.MONOSPACE,
            ).also { cached = it }
        }
    }
}

/** Geometry shared by drawing and tap handling. */
class ChartLayout(
    val plot: RectF,
    val centers: FloatArray,
    val band: Float,
    val horizontal: Boolean,
    val donutCenterX: Float = 0f,
    val donutCenterY: Float = 0f,
    val donutRadius: Float = 0f,
    val sliceStarts: FloatArray = FloatArray(0),
    val sliceSweeps: FloatArray = FloatArray(0),
)

/**
 * Draws a chart onto an Android canvas. [u] is the size of one dp in pixels for this target
 * (screen density for the app, a fixed scale for exported images).
 */
class ChartRenderer(private val fonts: ChartFonts, private val u: Float) {

    private fun dp(v: Float) = v * u

    private val grid = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Colors.LINE; strokeWidth = dp(1f) }
    private val base = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Colors.INK; strokeWidth = dp(1f) }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    private val tick = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = fonts.mono; textSize = dp(10f); color = Colors.SUB }
    private val catText = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = fonts.mono; textSize = dp(11f); color = Colors.SUB }
    private val catSans = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = fonts.sans; textSize = dp(12f); color = Colors.BODY }
    private val valueText = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = fonts.monoSemi; textSize = dp(12f); color = Colors.INK }
    private val smallValue = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = fonts.mono; textSize = dp(10.5f); color = Colors.BODY }

    private fun isMono(s: String) = s.all { it.code < 0x1100 }
    private fun catPaint(s: String): TextPaint = if (isMono(s)) catText else catSans

    fun layout(d: ChartData, spec: ChartSpec, w: Float, h: Float): ChartLayout {
        val n = max(1, d.size)
        if (spec.type == ChartType.DONUT) return donutLayout(d, w, h)
        val legendH = if (legendItems(d, spec).isNotEmpty()) dp(26f) else 0f
        if (spec.type == ChartType.HBAR) {
            val labelW = min(w * 0.34f, (d.categories.maxOfOrNull { catSans.measureText(it) } ?: 0f) + dp(10f))
            val valueW = dp(48f)
            val plot = RectF(labelW, legendH + dp(4f), w - valueW, h - dp(22f))
            val band = plot.height() / n
            return ChartLayout(plot, FloatArray(n) { plot.top + band * (it + 0.5f) }, band, true)
        }
        val ax = d.axis
        val tickW = ax?.ticks?.maxOfOrNull { tick.measureText(Format.fixed(it, ax.decimals)) } ?: dp(20f)
        val top = legendH + dp(if (spec.showValues || d.highlight >= 0) 26f else 12f)
        val plot = RectF(0f, top, w, h - dp(24f))
        // Tick labels sit above their grid lines on the left, so the plot keeps the full width;
        // leave a gutter only so the first bar/point does not collide with the labels.
        val gutter = min(tickW + dp(8f), w * 0.18f)
        val left = plot.left + gutter
        val band = (plot.right - left) / n
        return ChartLayout(RectF(left, plot.top, plot.right, plot.bottom), FloatArray(n) { left + band * (it + 0.5f) }, band, false)
    }

    private fun donutLayout(d: ChartData, w: Float, h: Float): ChartLayout {
        val vals = d.first?.values ?: emptyList()
        val total = vals.sumOf { max(0.0, it ?: 0.0) }.toFloat().takeIf { it > 0 } ?: 1f
        val side = w > h * 1.25f
        val r = if (side) min(h * 0.46f, w * 0.26f) else min(w * 0.36f, h * 0.30f)
        val cx = if (side) r + dp(8f) else w / 2f
        val cy = if (side) h / 2f else r + dp(8f)
        val starts = FloatArray(vals.size); val sweeps = FloatArray(vals.size)
        var a = -90f
        vals.forEachIndexed { i, v ->
            val sw = (max(0.0, v ?: 0.0).toFloat() / total) * 360f
            starts[i] = a; sweeps[i] = sw; a += sw
        }
        return ChartLayout(RectF(0f, 0f, w, h), FloatArray(0), 0f, false, cx, cy, r, starts, sweeps)
    }

    fun hitTest(l: ChartLayout, d: ChartData, spec: ChartSpec, x: Float, y: Float): Int {
        if (spec.type == ChartType.DONUT) {
            val dx = x - l.donutCenterX; val dy = y - l.donutCenterY
            val dist = kotlin.math.sqrt(dx * dx + dy * dy)
            if (dist > l.donutRadius * 1.15f || dist < l.donutRadius * 0.45f) return -1
            var ang = Math.toDegrees(kotlin.math.atan2(dy.toDouble(), dx.toDouble())).toFloat()
            if (ang < -90f) ang += 360f
            for (i in l.sliceStarts.indices) if (ang >= l.sliceStarts[i] && ang < l.sliceStarts[i] + l.sliceSweeps[i]) return i
            return -1
        }
        if (l.centers.isEmpty()) return -1
        val p = if (l.horizontal) y else x
        var best = -1; var bestD = Float.MAX_VALUE
        l.centers.forEachIndexed { i, c -> val dd = abs(c - p); if (dd < bestD) { bestD = dd; best = i } }
        return if (bestD <= l.band * 0.75f + dp(8f)) best else -1
    }

    private fun legendItems(d: ChartData, spec: ChartSpec): List<Pair<String, Int>> {
        val out = ArrayList<Pair<String, Int>>()
        if (d.series.size > 1 && spec.type != ChartType.DONUT && spec.type != ChartType.LINE) {
            d.series.forEachIndexed { i, s -> out += s.name to seriesColor(i, d, spec) }
        }
        return out
    }

    private fun seriesColor(i: Int, d: ChartData, spec: ChartSpec): Int =
        if (d.series.size == 1) Colors.GRAPHITE else Colors.SERIES[i % Colors.SERIES.size]

    fun draw(c: Canvas, d: ChartData, spec: ChartSpec, w: Float, h: Float) {
        if (d.size == 0 || d.series.isEmpty()) { drawEmpty(c, w, h); return }
        val l = layout(d, spec, w, h)
        when (spec.type) {
            ChartType.DONUT -> drawDonut(c, d, spec, l, w, h)
            ChartType.HBAR -> drawHBar(c, d, spec, l)
            else -> drawVertical(c, d, spec, l)
        }
        drawLegend(c, d, spec, w)
    }

    private fun drawEmpty(c: Canvas, w: Float, h: Float) {
        val p = TextPaint(catSans).apply { textAlign = Paint.Align.CENTER; color = Colors.SUB }
        c.drawText("그릴 숫자가 없어요", w / 2, h / 2, p)
    }

    private fun drawLegend(c: Canvas, d: ChartData, spec: ChartSpec, w: Float) {
        val items = legendItems(d, spec)
        if (items.isEmpty()) return
        var x = 0f
        val y = dp(12f)
        for ((name, col) in items) {
            fill.color = col
            c.drawRoundRect(RectF(x, y - dp(5f), x + dp(10f), y + dp(5f)), dp(2f), dp(2f), fill)
            x += dp(14f)
            c.drawText(name, x, y + dp(4f), catSans)
            x += catSans.measureText(name) + dp(14f)
            if (x > w - dp(40f)) break
        }
    }

    // Values outside a user-set axis range are pinned to the plot edge so nothing is drawn
    // over the labels; the honesty check tells the user about cut or exaggerated bars.
    private fun yOf(v: Double, l: ChartLayout, d: ChartData): Float {
        val ax = d.axis!!
        val t = ((v - ax.min) / (ax.max - ax.min)).toFloat().coerceIn(0f, 1.04f)
        return l.plot.bottom - t * l.plot.height()
    }

    private fun xOfValue(v: Double, l: ChartLayout, d: ChartData): Float {
        val ax = d.axis!!
        val t = ((v - ax.min) / (ax.max - ax.min)).toFloat().coerceIn(0f, 1.02f)
        return l.plot.left + t * l.plot.width()
    }

    private fun labelStep(d: ChartData, l: ChartLayout): Int {
        val widest = d.categories.maxOfOrNull { catPaint(it).measureText(it) } ?: 0f
        return max(1, ceil((widest + dp(8f)) / l.band).toInt())
    }

    private fun drawValueGrid(c: Canvas, d: ChartData, l: ChartLayout) {
        val ax = d.axis ?: return
        val ticks = ax.ticks
        // Show at most 3 tick labels (top, middle, bottom) for a calm chart.
        val labelIdx = if (ticks.size <= 3) ticks.indices.toSet() else setOf(0, ticks.size / 2, ticks.size - 1)
        ticks.forEachIndexed { i, t ->
            val y = yOf(t, l, d)
            if (abs(t) > 1e-12) c.drawLine(l.plot.left - (if (i in labelIdx) 0f else 0f), y, l.plot.right, y, grid)
            if (i in labelIdx && i != 0 || (i == 0 && ax.min != 0.0)) {
                c.drawText(Format.fixed(t, ax.decimals), 0f, y - dp(4f), tick)
            }
        }
        // Zero baseline in ink
        val zeroY = if (ax.min <= 0 && ax.max >= 0) yOf(0.0, l, d) else l.plot.bottom
        c.drawLine(0f, zeroY, l.plot.right, zeroY, base)
        if (ax.min <= 0 && ax.max >= 0) c.drawText(Format.fixed(0.0, 0), 0f, zeroY - dp(4f), tick)
    }

    private fun drawCategoryLabels(c: Canvas, d: ChartData, l: ChartLayout) {
        val step = labelStep(d, l)
        d.categories.forEachIndexed { i, s ->
            val hl = i == d.highlight
            if (i % step != 0 && !hl && i != d.size - 1) return@forEachIndexed
            if (!hl && step > 1 && i == d.size - 1 && (d.size - 1) % step != 0 && (d.size - 1) % step < step / 2 + 1) return@forEachIndexed
            val p = TextPaint(catPaint(s)).apply {
                textAlign = Paint.Align.CENTER
                if (hl) { color = Colors.INK; typeface = if (isMono(s)) fonts.monoSemi else fonts.sansSemi }
            }
            val text = ellipsize(s, p, max(l.band * step - dp(4f), dp(24f)))
            c.drawText(text, l.centers[i], l.plot.bottom + dp(17f), p)
        }
    }

    private fun ellipsize(s: String, p: TextPaint, maxW: Float): String {
        if (p.measureText(s) <= maxW) return s
        var t = s
        while (t.length > 1 && p.measureText("$t…") > maxW) t = t.dropLast(1)
        return "$t…"
    }

    private fun highlightAnnotation(d: ChartData, i: Int): Pair<String, String?> {
        val v = d.first!!.values[i] ?: return "" to null
        val main = d.format(v)
        if (d.isTime && i > 0) {
            val p = d.first!!.values[i - 1]
            if (p != null && p != 0.0) {
                if (d.unit == "%" || d.unit == "%p") {
                    val diff = v - p
                    return main to ((if (diff >= 0) "+" else "−") + Format.percent(abs(diff)) + "%p")
                }
                val pct = (v - p) / abs(p) * 100
                if (abs(pct) >= 0.5) return main to ((if (pct >= 0) "+" else "−") + Format.percent(abs(pct)) + "%")
            }
        }
        return main to null
    }

    private fun drawAnnotation(c: Canvas, x: Float, y: Float, d: ChartData, i: Int, plotTop: Float, maxRight: Float) {
        val (main, delta) = highlightAnnotation(d, i)
        if (main.isEmpty()) return
        val p1 = TextPaint(valueText)
        val p2 = TextPaint(valueText).apply { color = Colors.SIGNAL_TEXT }
        val gap = dp(4f)
        val w1 = p1.measureText(main); val w2 = delta?.let { p2.measureText(it) + gap } ?: 0f
        var left = x - (w1 + w2) / 2f
        left = left.coerceIn(0f, max(0f, maxRight - (w1 + w2)))
        val baseY = max(y - dp(12f), plotTop - dp(8f) + p1.textSize)
        c.drawText(main, left, baseY, p1)
        if (delta != null) c.drawText(delta, left + w1 + gap, baseY, p2)
    }

    private fun drawRefLines(c: Canvas, d: ChartData, spec: ChartSpec, l: ChartLayout, horizontal: Boolean) {
        val dash = DashPathEffect(floatArrayOf(dp(4f), dp(3f)), 0f)
        fun line(v: Double, label: String, color: Int) {
            val p = Paint(stroke).apply { this.color = color; strokeWidth = dp(1.2f); pathEffect = dash; strokeCap = Paint.Cap.BUTT }
            val t = TextPaint(smallValue).apply { typeface = fonts.monoSemi; this.color = 0xFFFFFFFF.toInt() }
            val tw = t.measureText(label) + dp(12f)
            if (!horizontal) {
                val y = yOf(v, l, d)
                c.drawLine(l.plot.left, y, l.plot.right, y, p)
                val r = RectF(l.plot.right - tw, y - dp(9f), l.plot.right, y + dp(9f))
                fill.color = color
                c.drawRoundRect(r, dp(9f), dp(9f), fill)
                c.drawText(label, r.left + dp(6f), y + dp(3.6f), t)
            } else {
                val x = xOfValue(v, l, d)
                c.drawLine(x, l.plot.top, x, l.plot.bottom, p)
                val r = RectF(x - tw / 2, l.plot.bottom + dp(2f), x + tw / 2, l.plot.bottom + dp(20f))
                fill.color = color
                c.drawRoundRect(r, dp(9f), dp(9f), fill)
                c.drawText(label, r.left + dp(6f), r.centerY() + dp(3.6f), t)
            }
        }
        spec.target?.let { line(it, "목표 ${Format.plain(it)}", Colors.INK) }
        if (spec.showAverage) {
            val vals = d.first?.values?.filterNotNull() ?: emptyList()
            if (vals.isNotEmpty()) line(vals.average(), "평균 ${Format.plain(vals.average())}", Colors.GRAPHITE)
        }
    }

    private fun drawVertical(c: Canvas, d: ChartData, spec: ChartSpec, l: ChartLayout) {
        drawValueGrid(c, d, l)
        val hl = d.highlight
        val single = d.series.size == 1
        val zeroY = yOf(0.0.coerceIn(d.axis!!.min, d.axis!!.max), l, d)
        when (spec.type) {
            ChartType.BAR, ChartType.STACKED -> {
                val k = if (spec.type == ChartType.STACKED) 1 else d.series.size
                val groupW = l.band * (if (single) 0.5f else 0.72f)
                val barW = min(groupW / k, dp(44f))
                val radius = min(dp(3f), barW / 3f)
                for (i in 0 until d.size) {
                    var posAcc = 0.0; var negAcc = 0.0
                    d.series.forEachIndexed { si, s ->
                        val v = s.values[i] ?: return@forEachIndexed
                        val barColor = when {
                            single && hl >= 0 -> if (i == hl) spec.highlightColor else Colors.FOG
                            else -> seriesColor(si, d, spec)
                        }
                        fill.color = barColor
                        val x0 = if (k == 1) l.centers[i] - barW / 2 else l.centers[i] - groupW / 2 + si * (groupW / k) + (groupW / k - barW) / 2
                        val (from, to) = if (spec.type == ChartType.STACKED) {
                            if (v >= 0) { val a = posAcc; posAcc += v; a to posAcc } else { val a = negAcc; negAcc += v; a to negAcc }
                        } else 0.0 to v
                        val yA = yOf(from, l, d); val yB = yOf(to, l, d)
                        val r = RectF(x0, min(yA, yB), x0 + barW, max(yA, yB))
                        if (r.height() < 0.5f) r.bottom = r.top + 0.5f
                        c.drawRoundRect(r, radius, radius, fill)
                        if (spec.type == ChartType.STACKED && spec.showValues && r.height() > dp(14f)) {
                            val t = TextPaint(smallValue).apply { textAlign = Paint.Align.CENTER; color = if (si == 0) 0xFFFFFFFF.toInt() else Colors.INK }
                            c.drawText(d.format(v), r.centerX(), r.centerY() + dp(3.5f), t)
                        } else if (spec.type == ChartType.BAR && spec.showValues && !(single && i == hl)) {
                            val t = TextPaint(smallValue).apply { textAlign = Paint.Align.CENTER }
                            val ty = if (v >= 0) r.top - dp(4f) else r.bottom + dp(12f)
                            if (k <= 2 || barW > dp(18f)) c.drawText(d.format(v), r.centerX(), ty, t)
                        }
                    }
                    if (spec.type == ChartType.STACKED && spec.showValues) {
                        val t = TextPaint(smallValue).apply { textAlign = Paint.Align.CENTER; typeface = fonts.monoSemi; color = Colors.INK }
                        c.drawText(d.format(posAcc + negAcc), l.centers[i], yOf(posAcc, l, d) - dp(4f), t)
                    }
                }
                if (single && hl >= 0 && d.first!!.values[hl] != null) {
                    val v = d.first!!.values[hl]!!
                    drawAnnotation(c, l.centers[hl], if (v >= 0) yOf(v, l, d) else zeroY, d, hl, l.plot.top, l.plot.right)
                }
            }
            ChartType.LINE, ChartType.AREA -> {
                val stackedArea = spec.type == ChartType.AREA && d.series.size > 1
                val acc = DoubleArray(d.size)
                d.series.forEachIndexed { si, s ->
                    val color = if (single) Colors.GRAPHITE else seriesColor(si, d, spec)
                    val path = Path()
                    val area = Path()
                    var started = false
                    val pts = ArrayList<Pair<Float, Float>>()
                    for (i in 0 until d.size) {
                        val raw = s.values[i]
                        if (raw == null) { started = false; continue }
                        val v = if (stackedArea) acc[i] + raw else raw
                        val x = l.centers[i]; val y = yOf(v, l, d)
                        if (!started) { path.moveTo(x, y); started = true } else path.lineTo(x, y)
                        pts += x to y
                    }
                    if (spec.type == ChartType.AREA && pts.isNotEmpty()) {
                        area.moveTo(pts.first().first, if (stackedArea) yOf(acc[0], l, d) else zeroY)
                        pts.forEach { (x, y) -> area.lineTo(x, y) }
                        if (stackedArea) {
                            for (i in d.size - 1 downTo 0) area.lineTo(l.centers[i], yOf(acc[i], l, d))
                        } else area.lineTo(pts.last().first, zeroY)
                        area.close()
                        fill.color = if (single) 0xFFE9E9EC.toInt() else (color and 0x00FFFFFF) or 0x55000000
                        c.drawPath(area, fill)
                    }
                    stroke.color = color; stroke.strokeWidth = dp(2f); stroke.pathEffect = null
                    c.drawPath(path, stroke)
                    if (spec.type == ChartType.LINE && d.size <= 24) {
                        for ((x, y) in pts) {
                            fill.color = 0xFFFFFFFF.toInt(); c.drawCircle(x, y, dp(3.2f), fill)
                            stroke.strokeWidth = dp(1.6f); c.drawCircle(x, y, dp(3.2f), stroke)
                        }
                    }
                    if (spec.showValues && spec.type == ChartType.LINE) {
                        val t = TextPaint(smallValue).apply { textAlign = Paint.Align.CENTER }
                        for (i in 0 until d.size) {
                            val v = s.values[i] ?: continue
                            if (single && i == hl) continue
                            c.drawText(d.format(v), l.centers[i], yOf(v, l, d) - dp(8f), t)
                        }
                    }
                    // Direct label at the line end instead of a legend.
                    if (!single && spec.type == ChartType.LINE && pts.isNotEmpty()) {
                        val (x, y) = pts.last()
                        val t = TextPaint(catSans).apply { this.color = color; typeface = fonts.sansSemi; textAlign = Paint.Align.RIGHT; textSize = dp(11f) }
                        c.drawText(s.name, x, y - dp(8f), t)
                    }
                    if (stackedArea) for (i in 0 until d.size) acc[i] += s.values[i] ?: 0.0
                }
                if (single && hl >= 0 && d.first!!.values[hl] != null) {
                    val v = d.first!!.values[hl]!!
                    val x = l.centers[hl]; val y = yOf(v, l, d)
                    val dash = Paint(stroke).apply { color = spec.highlightColor; strokeWidth = dp(1f); pathEffect = DashPathEffect(floatArrayOf(dp(2f), dp(3f)), 0f); strokeCap = Paint.Cap.BUTT }
                    c.drawLine(x, y + dp(7f), x, zeroY, dash)
                    fill.color = 0xFFFFFFFF.toInt(); c.drawCircle(x, y, dp(7.5f), fill)
                    fill.color = spec.highlightColor; c.drawCircle(x, y, dp(5.5f), fill)
                    drawAnnotation(c, x, y, d, hl, l.plot.top, l.plot.right)
                }
            }
            else -> {}
        }
        drawRefLines(c, d, spec, l, false)
        drawCategoryLabels(c, d, l)
        spec.note?.takeIf { it.isNotBlank() && hl >= 0 }?.let { note ->
            val t = TextPaint(catSans).apply { textSize = dp(11f); color = Colors.BODY }
            val x = l.centers[hl]
            val w = min(t.measureText(note), l.plot.width() * 0.6f)
            val left = (x - w / 2).coerceIn(l.plot.left, l.plot.right - w)
            c.drawText(ellipsize(note, t, w), left, l.plot.top - dp(2f) + t.textSize, t)
        }
    }

    private fun drawHBar(c: Canvas, d: ChartData, spec: ChartSpec, l: ChartLayout) {
        val ax = d.axis ?: return
        val hl = d.highlight
        val single = d.series.size == 1
        // vertical grid at ticks
        val labelIdx = if (ax.ticks.size <= 3) ax.ticks.indices.toSet() else setOf(0, ax.ticks.size / 2, ax.ticks.size - 1)
        val tp = TextPaint(tick).apply { textAlign = Paint.Align.CENTER }
        ax.ticks.forEachIndexed { i, t ->
            val x = xOfValue(t, l, d)
            c.drawLine(x, l.plot.top, x, l.plot.bottom, grid)
            if (i in labelIdx && spec.target == null) c.drawText(Format.fixed(t, ax.decimals), x, l.plot.bottom + dp(14f), tp)
        }
        val zeroX = xOfValue(0.0.coerceIn(ax.min, ax.max), l, d)
        val k = d.series.size
        val groupH = l.band * (if (single) 0.6f else 0.78f)
        val barH = min(groupH / k, dp(28f))
        for (i in 0 until d.size) {
            val label = d.categories[i]
            val lp = TextPaint(catSans).apply { textAlign = Paint.Align.RIGHT; if (i == hl) { color = Colors.INK; typeface = fonts.sansSemi } }
            c.drawText(ellipsize(label, lp, l.plot.left - dp(10f)), l.plot.left - dp(8f), l.centers[i] + dp(4f), lp)
            d.series.forEachIndexed { si, s ->
                val v = s.values[i] ?: return@forEachIndexed
                fill.color = if (single && hl >= 0) (if (i == hl) spec.highlightColor else Colors.FOG) else seriesColor(si, d, spec)
                val y0 = l.centers[i] - groupH / 2 + si * (groupH / k) + (groupH / k - barH) / 2
                val xv = xOfValue(v, l, d)
                val r = RectF(min(zeroX, xv), y0, max(zeroX, xv), y0 + barH)
                c.drawRoundRect(r, min(dp(3f), barH / 3), min(dp(3f), barH / 3), fill)
                if (spec.showValues || (single && i == hl)) {
                    val t = TextPaint(if (single && i == hl) valueText else smallValue)
                    c.drawText(d.format(v), r.right + dp(5f), r.centerY() + dp(4f), t)
                }
            }
        }
        c.drawLine(zeroX, l.plot.top, zeroX, l.plot.bottom, base)
        drawRefLines(c, d, spec, l, true)
    }

    private fun drawDonut(c: Canvas, d: ChartData, spec: ChartSpec, l: ChartLayout, w: Float, h: Float) {
        val vals = d.first!!.values
        val hl = d.highlight
        val r = l.donutRadius
        val thick = r * 0.34f
        val oval = RectF(l.donutCenterX - r + thick / 2, l.donutCenterY - r + thick / 2, l.donutCenterX + r - thick / 2, l.donutCenterY + r - thick / 2)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = thick; strokeCap = Paint.Cap.BUTT }
        val gapDeg = if (vals.size > 1) 0.9f else 0f
        vals.indices.forEach { i ->
            val sw = l.sliceSweeps[i]
            if (sw <= 0f) return@forEach
            p.color = when {
                hl >= 0 && i == hl -> spec.highlightColor
                hl >= 0 -> Colors.SHADES[1 + (i % (Colors.SHADES.size - 1))]
                else -> Colors.SHADES[i % Colors.SHADES.size]
            }
            c.drawArc(oval, l.sliceStarts[i] + gapDeg / 2, max(0.1f, sw - gapDeg), false, p)
        }
        val total = vals.sumOf { max(0.0, it ?: 0.0) }
        val focus = if (hl >= 0) hl else vals.indices.maxByOrNull { vals[it] ?: Double.MIN_VALUE } ?: 0
        val pct = if (total > 0) (vals[focus] ?: 0.0) / total * 100 else 0.0
        val big = TextPaint(valueText).apply { textAlign = Paint.Align.CENTER; textSize = r * 0.30f }
        c.drawText(Format.percent(pct) + "%", l.donutCenterX, l.donutCenterY + big.textSize * 0.2f, big)
        val sub = TextPaint(catSans).apply { textAlign = Paint.Align.CENTER; textSize = max(dp(11f), r * 0.12f) }
        c.drawText(ellipsize(d.categories[focus], sub, r * 1.1f), l.donutCenterX, l.donutCenterY + big.textSize * 0.2f + sub.textSize * 1.5f, sub)

        // Legend with percentages
        val side = w > h * 1.25f
        val lx = if (side) l.donutCenterX + r + dp(24f) else dp(4f)
        val ly = if (side) l.donutCenterY - (vals.size * dp(24f)) / 2f + dp(12f) else l.donutCenterY + r + dp(26f)
        val colW = if (side) w - lx else w
        val name = TextPaint(catSans).apply { textSize = dp(12f) }
        val num = TextPaint(smallValue).apply { textAlign = Paint.Align.RIGHT; typeface = fonts.monoSemi; color = Colors.INK }
        val twoCols = !side && vals.size > 4
        vals.indices.forEach { i ->
            val col = if (twoCols) i % 2 else 0
            val row = if (twoCols) i / 2 else i
            val cw = if (twoCols) (colW - dp(16f)) / 2 else colW - dp(8f)
            val x = lx + col * (cw + dp(16f))
            val y = ly + row * dp(24f)
            if (y > h) return@forEach
            fill.color = when {
                hl >= 0 && i == hl -> spec.highlightColor
                hl >= 0 -> Colors.SHADES[1 + (i % (Colors.SHADES.size - 1))]
                else -> Colors.SHADES[i % Colors.SHADES.size]
            }
            c.drawCircle(x + dp(5f), y - dp(4f), dp(4.5f), fill)
            val p100 = if (total > 0) max(0.0, vals[i] ?: 0.0) / total * 100 else 0.0
            val numText = Format.percent(p100) + "%"
            c.drawText(numText, x + cw, y, num)
            val nm = TextPaint(name).apply { if (i == hl) { typeface = fonts.sansSemi; color = Colors.INK } }
            c.drawText(ellipsize(d.categories[i], nm, cw - num.measureText(numText) - dp(24f)), x + dp(16f), y, nm)
        }
    }
}

/** Simple wrapped text drawing for exports. */
object TextBlock {
    fun draw(c: Canvas, text: String, paint: TextPaint, x: Float, y: Float, width: Int, maxLines: Int = 4, spacing: Float = 1.25f): Float {
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, spacing)
            .setIncludePad(false)
            .setMaxLines(maxLines)
            .setEllipsize(android.text.TextUtils.TruncateAt.END)
            .build()
        c.save(); c.translate(x, y); layout.draw(c); c.restore()
        return layout.height.toFloat()
    }

    fun height(text: String, paint: TextPaint, width: Int, maxLines: Int = 4, spacing: Float = 1.25f): Float =
        StaticLayout.Builder.obtain(text, 0, text.length, paint, width).setLineSpacing(0f, spacing).setIncludePad(false).setMaxLines(maxLines).build().height.toFloat()
}
