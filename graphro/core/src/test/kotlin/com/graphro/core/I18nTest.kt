package com.graphro.core

import com.graphro.core.chart.ChartData
import com.graphro.core.chart.ChartType
import com.graphro.core.chart.AxisMode
import com.graphro.core.chart.Recommender
import com.graphro.core.check.Honesty
import com.graphro.core.export.PptxWriter
import com.graphro.core.model.ColumnType
import com.graphro.core.model.Scale
import com.graphro.core.parse.Importer
import com.graphro.core.parse.KoreanNumber
import com.graphro.core.parse.TimeParser
import com.graphro.core.text.Caption
import com.graphro.core.text.I18n
import com.graphro.core.text.Lang
import org.junit.After
import org.junit.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class EnglishTest {
    @After fun reset() { I18n.lang = Lang.KO }

    private val sales = "Month\tRevenue\tVisitors\nJan\t$320K\t1,240\nFeb\t$280,000\t1,105\nMar\t$410K\t1,630\nApr\t395\t1,580\nMay\t$452K\t1,712\nJun\t$438K\t1,690\nTotal\t$2,295K\t8,957"

    @Test fun parsesEnglish() {
        I18n.lang = Lang.EN
        assertEquals(3_500_000.0, KoreanNumber.parse("$3.5M")!!.value)
        assertEquals(3, TimeParser.parse("Q3 2024")!!.sub)
        assertEquals(2024, TimeParser.parse("FY2024")!!.year)
        val t = Importer.fromText(sales).best!!.table
        assertEquals(ColumnType.TIME, t.columns[0].type)
        val rev = t.columns[1]
        assertEquals("$", rev.unit)
        assertEquals(395_000.0, rev.number(3), "395 among K values is read as 395K")
        assertEquals(setOf(6), t.excludedRows.keys)
        assertEquals(Scale.THOUSAND, rev.scale)
        assertEquals("$ thousands", rev.unitLabel)
    }

    @Test fun englishCaptionAndTitle() {
        I18n.lang = Lang.EN
        val t = Importer.fromText(sales).best!!.table
        val spec = Recommender.defaultSpec(t)
        assertEquals("Monthly Revenue", spec.title)
        val d = ChartData.build(t, spec)
        assertEquals("Mar Revenue rose 46% from the previous month, from $280K to $410K.", Caption.make(d, spec))
        val recs = Recommender.recommend(t, spec.categoryCol, spec.seriesCols)
        assertTrue(recs.first().reason.contains("Mar"), recs.first().reason)
    }

    @Test fun englishHonesty() {
        I18n.lang = Lang.EN
        val t = Importer.fromText(sales).best!!.table
        val spec = Recommender.defaultSpec(t).copy(type = ChartType.BAR, axisMode = AxisMode.CUSTOM, axisMin = 250.0, axisMax = 500.0)
        val r = Honesty.check(t, spec, ChartData.build(t, spec))
        val issue = r.issues.first { it.code == "axis_zero" }
        assertEquals("The axis starts at 250, not 0", issue.title)
    }

    @Test fun englishCompare() {
        I18n.lang = Lang.EN
        val t = Importer.fromText("City\tPopulation\nSeoul\t9,411,000\nBusan\t3,293,000\nDaegu\t2,374,000\nIncheon\t2,997,000\nSejong\t386,000").best!!.table
        val spec = Recommender.defaultSpec(t)
        val cap = Caption.make(ChartData.build(t, spec), spec)
        assertEquals("Seoul has the highest Population at 9.41M. That is 24× the lowest, Sejong.", cap)
    }
}

class JapaneseTest {
    @After fun reset() { I18n.lang = Lang.KO }

    private val sales = "月\t売上\t来店数(人)\n1月\t320万\t1,240\n2月\t280万円\t1,105\n3月\t410万\t1,630\n4月\t395\t1,580\n5月\t452万\t1,712\n6月\t4,380,000円\t1,690\n合計\t2,295万\t8,957"

    @Test fun parsesJapanese() {
        I18n.lang = Lang.JA
        assertEquals(3_200_000.0, KoreanNumber.parse("320万円")!!.value)
        assertEquals(1.2e8, KoreanNumber.parse("1.2億")!!.value)
        val q = TimeParser.parse("2024年第1四半期")!!
        assertEquals(1, q.sub); assertEquals(2024, q.year)
        assertEquals(3, TimeParser.parse("2024年3月")!!.sub)
        assertNotNull(TimeParser.parse("2024年3月1日(金)"))
        assertEquals(2, TimeParser.parse("2024年下期")!!.sub)
        val t = Importer.fromText(sales).best!!.table
        assertEquals(ColumnType.TIME, t.columns[0].type)
        val c = t.columns[1]
        assertEquals(3_950_000.0, c.number(3))
        assertEquals(4_380_000.0, c.number(5))
        assertEquals("万円", c.unitLabel)
        assertEquals(setOf(6), t.excludedRows.keys)
    }

    @Test fun japaneseCaption() {
        I18n.lang = Lang.JA
        val t = Importer.fromText(sales).best!!.table
        val spec = Recommender.defaultSpec(t)
        assertEquals("月別売上", spec.title)
        val d = ChartData.build(t, spec)
        assertEquals("3月の売上は前月より46%増えました（280万円→410万円）。", Caption.make(d, spec))
    }

    @Test fun japaneseFreeText() {
        I18n.lang = Lang.JA
        val t = Importer.fromText("1月 売上 320万、来店 1,240人\n2月 売上 280万円、来店 1,105人\n3月 売上 410万、来店 1,630人\n4月 売上 395万、来店 1,580人").best!!.table
        assertEquals(ColumnType.TIME, t.columns[0].type)
        assertEquals(4_100_000.0, t.columns[1].number(2))
    }

    @Test fun pptInEveryLanguage() {
        val out = File("build/test-out").apply { mkdirs() }
        for ((lang, src) in listOf(Lang.EN to "Month\tRevenue\nJan\t$320K\nFeb\t$280K\nMar\t$410K\nApr\t$395K", Lang.JA to sales)) {
            I18n.lang = lang
            val t = Importer.fromText(src).best!!.table
            val spec = Recommender.defaultSpec(t).copy(target = 400.0)
            val d = ChartData.build(t, spec)
            val cap = Caption.make(d, spec)
            File(out, "lang-${lang.name.lowercase()}.pptx").writeBytes(PptxWriter.write(d, spec, PptxWriter.Options(cap, PptxWriter.speakerNotes(d, cap))))
        }
    }
}
