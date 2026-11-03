package com.graphro.core

import com.graphro.core.chart.AxisMode
import com.graphro.core.chart.ChartData
import com.graphro.core.chart.ChartType
import com.graphro.core.chart.Fit
import com.graphro.core.chart.HighlightMode
import com.graphro.core.chart.Recommender
import com.graphro.core.chart.Ticks
import com.graphro.core.check.Honesty
import com.graphro.core.check.Severity
import com.graphro.core.export.CsvWriter
import com.graphro.core.export.PptxWriter
import com.graphro.core.export.SheetData
import com.graphro.core.export.XlsxWriter
import com.graphro.core.parse.Importer
import com.graphro.core.parse.XlsxReader
import com.graphro.core.project.Project
import com.graphro.core.project.ProjectCodec
import com.graphro.core.text.Caption
import com.graphro.core.text.Format
import com.graphro.core.text.Josa
import org.junit.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private const val SALES = "월\t매출(만원)\t방문자\n1월\t320\t1,240\n2월\t280\t1,105\n3월\t410\t1,630\n4월\t395\t1,580\n5월\t452\t1,712\n6월\t438\t1,690"
private const val REGIONS = "지역\t인구(명)\n서울\t9411000\n부산\t3293000\n대구\t2374000\n인천\t2997000\n광주\t1419000\n대전\t1442000\n울산\t1103000\n세종\t386000"
private const val SURVEY = "응답\t비율\n매우 만족\t32.5%\n만족\t41%\n보통\t18%\n불만\t8.5%"

class FormatTest {
    @Test fun numbers() {
        assertEquals("1,240", Format.plain(1240.0))
        assertEquals("410", Format.plain(410.0))
        assertEquals("12.5", Format.plain(12.5))
        assertEquals("-1,200", Format.fixed(-1200.0, 0))
        assertEquals("410만 원", Format.withUnit(410.0, "만", "원"))
        assertEquals("1,240명", Format.withUnit(1240.0, "", "명"))
        assertEquals("12.5%", Format.withUnit(12.5, "", "%"))
    }

    @Test fun particles() {
        assertEquals("3월이", Josa.iGa("3월"))
        assertEquals("부산이", Josa.iGa("부산"))
        assertEquals("서울이", Josa.iGa("서울"))
        assertEquals("대구가", Josa.iGa("대구"))
        assertEquals("매출은", Josa.eunNeun("매출"))
        assertEquals("410만 원으로", Josa.euro("410만 원"))
        assertEquals("1,240명으로", Josa.euro("1,240명"))
        assertEquals("12%로", Josa.euro("12%"))
        assertEquals("2로", Josa.euro("2"))
        assertEquals("1로", Josa.euro("1"))
        assertEquals("3으로", Josa.euro("3"))
    }

    @Test fun ticks() {
        val a = Ticks.nice(0.0, 452.0)
        assertEquals(0.0, a.min); assertTrue(a.max >= 452.0)
        assertTrue(a.ticks.size in 4..8)
    }
}

class ChartTest {
    private fun table(s: String) = Importer.fromText(s).best!!.table

    @Test fun salesRecommendLine() {
        val t = table(SALES)
        val spec = Recommender.defaultSpec(t)
        assertEquals(ChartType.LINE, spec.type)
        assertEquals("월별 매출", spec.title)
        assertEquals(listOf(1), spec.seriesCols, "visitors have another unit so they are not mixed in")
        val recs = Recommender.recommend(t, spec.categoryCol, spec.seriesCols)
        assertEquals(Fit.BEST, recs.first().fit)
        assertTrue(recs.first().reason.contains("3월"))
        assertEquals(Fit.NOT_FIT, recs.first { it.type == ChartType.DONUT }.fit)
    }

    @Test fun salesCaption() {
        val t = table(SALES)
        val spec = Recommender.defaultSpec(t)
        val d = ChartData.build(t, spec)
        assertEquals(2, d.highlight, "biggest change is March")
        val cap = Caption.make(d, spec)
        assertEquals("3월 매출이 전월보다 46% 늘었어요. 280만 원에서 410만 원으로.", cap)
        val axis = d.axis!!
        assertEquals(0.0, axis.min)
    }

    @Test fun regionsCompare() {
        val t = table(REGIONS)
        val spec = Recommender.defaultSpec(t)
        assertEquals(ChartType.BAR, spec.type, "8 short labels fit vertical bars")
        val d = ChartData.build(t, spec)
        assertEquals("서울", d.categories.first(), "sorted descending")
        val cap = Caption.make(d, spec)
        assertTrue(cap.startsWith("서울의 인구가 941.1만 명으로 가장 많아요."), cap)
        assertTrue(cap.contains("세종의 24배"), cap)
    }

    @Test fun surveyDonut() {
        val t = table(SURVEY)
        val spec = Recommender.defaultSpec(t)
        assertEquals(ChartType.DONUT, spec.type)
        val d = ChartData.build(t, spec)
        assertEquals("만족이 전체의 41%로 가장 커요.", Caption.make(d, spec))
        val check = Honesty.check(t, spec, d)
        assertEquals(0, check.fixCount)
    }

    @Test fun honestyAxis() {
        val t = table(SALES)
        val spec = Recommender.defaultSpec(t).copy(type = ChartType.BAR, axisMode = AxisMode.CUSTOM, axisMin = 250.0, axisMax = 500.0)
        val d = ChartData.build(t, spec)
        val r = Honesty.check(t, spec, d)
        val axis = r.issues.first { it.code == "axis_zero" }
        assertEquals(Severity.FIX, axis.severity)
        val fixed = axis.fix!!(spec)
        assertEquals(AxisMode.ZERO, fixed.axisMode)
        val r2 = Honesty.check(t, fixed, ChartData.build(t, fixed))
        assertFalse(r2.issues.any { it.code == "axis_zero" })
    }

    @Test fun honestyLineForCategories() {
        val t = table(REGIONS)
        val spec = Recommender.defaultSpec(t).copy(type = ChartType.LINE)
        val r = Honesty.check(t, spec, ChartData.build(t, spec))
        assertTrue(r.issues.any { it.code == "line_unordered" })
    }

    @Test fun highlightModes() {
        val t = table(SALES)
        val base = Recommender.defaultSpec(t)
        assertEquals(4, ChartData.build(t, base.copy(highlightMode = HighlightMode.MAX)).highlight)
        assertEquals(1, ChartData.build(t, base.copy(highlightMode = HighlightMode.MANUAL, highlightRow = 1)).highlight)
        assertEquals(-1, ChartData.build(t, base.copy(highlightMode = HighlightMode.NONE)).highlight)
        val withTarget = base.copy(target = 400.0)
        val cap = Caption.make(ChartData.build(t, withTarget), withTarget)
        assertTrue(cap.endsWith("목표 400만 원에 닿은 달은 3개예요."), cap)
    }

    @Test fun projectRoundTrip() {
        val r = Importer.fromText(SALES).best!!
        val spec = Recommender.defaultSpec(r.table).copy(target = 400.0, note = "메모 \"따옴표\"\n줄바꿈")
        val p = Project("id1", "이름", 1L, 2L, r.source, spec, rawText = SALES)
        val back = ProjectCodec.decode(ProjectCodec.encode(p))
        assertEquals(p, back)
    }
}

class ExportTest {
    private val out = File("build/test-out").apply { mkdirs() }

    @Test fun xlsxRoundTrip() {
        val bytes = XlsxWriter.write(listOf(SheetData("데이터", listOf(listOf("월", "매출"), listOf("1월", 320.0), listOf("2월", 280.5)))))
        File(out, "roundtrip.xlsx").writeBytes(bytes)
        val grids = XlsxReader.read(bytes)
        assertEquals("데이터", grids[0].name)
        assertEquals(listOf("월", "매출"), grids[0].rows[0])
        assertEquals("280.5", grids[0].rows[2][1])
        val r = Importer.fromFile("x.xlsx", bytes)
        assertEquals(280.5, r.best!!.table.columns[1].number(1))
    }

    @Test fun csv() {
        val s = String(CsvWriter.write(listOf(listOf("a,b", 1.0), listOf("x\"y", null))), Charsets.UTF_8)
        assertEquals("﻿\"a,b\",1\r\n\"x\"\"y\",\r\n", s)
    }

    @Test fun pptxAllTypes() {
        for ((name, src) in listOf("sales" to SALES, "regions" to REGIONS, "survey" to SURVEY)) {
            val t = Importer.fromText(src).best!!.table
            val base = Recommender.defaultSpec(t)
            for (type in ChartType.entries) {
                val spec = base.copy(type = type, target = if (name == "sales") 400.0 else null, source = "테스트 자료")
                val d = ChartData.build(t, spec)
                val cap = Caption.make(d, spec)
                val table = listOf(t.columns.map { it.displayName }) + (0 until t.rowCount).map { r -> t.columns.map { it.cells[r].raw } }
                val bytes = PptxWriter.write(d, spec, PptxWriter.Options(cap, PptxWriter.speakerNotes(d, cap), table))
                File(out, "$name-${type.name.lowercase()}.pptx").writeBytes(bytes)
                assertTrue(bytes.size > 3000)
            }
        }
    }
}
