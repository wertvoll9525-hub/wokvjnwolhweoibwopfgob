package com.graphro.core

import com.graphro.core.model.ColumnType
import com.graphro.core.model.Granularity
import com.graphro.core.model.Scale
import com.graphro.core.model.Value
import com.graphro.core.parse.Delimited
import com.graphro.core.parse.Importer
import com.graphro.core.parse.KoreanNumber
import com.graphro.core.parse.TextDecode
import com.graphro.core.parse.TimeParser
import com.graphro.core.parse.UnitSpec
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KoreanNumberTest {
    private fun v(s: String) = KoreanNumber.parse(s)?.value

    @Test fun plain() {
        assertEquals(1240.0, v("1,240"))
        assertEquals(1240.0, v("1240"))
        assertEquals(12.5, v("12.5"))
        assertEquals(0.5, v(".5"))
        assertEquals(-3.0, v("-3"))
        assertEquals(120000.0, v("1.2E+05"))
    }

    @Test fun units() {
        assertEquals("명", KoreanNumber.parse("1,240명")?.unit)
        assertEquals(1240.0, v("1,240명"))
        assertEquals("%", KoreanNumber.parse("12.5%")?.unit)
        assertEquals("원", KoreanNumber.parse("₩1,000")?.unit)
        assertEquals(1000.0, v("₩1,000"))
        assertEquals("원", KoreanNumber.parse("320만 원")?.unit)
    }

    @Test fun koreanMagnitudes() {
        assertEquals(3_200_000.0, v("320만"))
        assertEquals(3_200_000.0, v("320만원"))
        assertEquals(320_000_000.0, v("3억 2천만"))
        assertEquals(320_000_000.0, v("3억2천만원"))
        assertEquals(1.2e12, v("1조 2,000억"))
        assertEquals(2000.0, v("2천"))
        assertEquals(1200.0, v("1천2백"))
        assertEquals(50_000_000.0, v("0.5억"))
        assertEquals(1e8, KoreanNumber.parse("320만")!!.bigFactor?.times(1e4))
    }

    @Test fun negatives() {
        assertEquals(-5.0, v("△5"))
        assertEquals(-5.0, v("▽5"))
        assertEquals(-5.0, v("(5)"))
        assertEquals(3.0, v("▲3"))
        assertEquals(-1200.0, v("(1,200)"))
        assertTrue(KoreanNumber.parse("△5")!!.negativeMarker)
    }

    @Test fun english() {
        assertEquals(3_500_000.0, v("$3.5M"))
        assertEquals(1200.0, v("1.2k"))
    }

    @Test fun rejects() {
        assertNull(v("010-1234-5678"))
        assertNull(v("A-12"))
        assertNull(v("서울"))
        assertNull(v("2 3"))
        assertNull(v("12,5"))
        assertNull(v("100/200"))
        assertNull(v("-"))
        assertNull(v("#N/A"))
        assertNull(v(""))
    }

    @Test fun approx() {
        val p = KoreanNumber.parse("약 300")!!
        assertEquals(300.0, p.value)
        assertTrue(p.approx)
    }
}

class TimeParserTest {
    @Test fun days() {
        val t = TimeParser.parse("2024-03-01")!!
        assertEquals(Granularity.DAY, t.granularity); assertEquals(2024, t.year); assertEquals(3, t.sub); assertEquals(1, t.day)
        assertNotNull(TimeParser.parse("2024.3.1"))
        assertNotNull(TimeParser.parse("2024. 3. 1."))
        assertNotNull(TimeParser.parse("2024년 3월 1일"))
        assertNotNull(TimeParser.parse("2024-03-01 10:00:00"))
        assertNotNull(TimeParser.parse("2024년 3월 1일 (금)"))
        assertNull(TimeParser.parse("2023-02-30"))
    }

    @Test fun months() {
        assertEquals(Granularity.MONTH, TimeParser.parse("2024.03")!!.granularity)
        assertEquals(Granularity.MONTH, TimeParser.parse("2024년 3월")!!.granularity)
        assertEquals(2024, TimeParser.parse("24년 3월")!!.year)
        val m = TimeParser.parse("3월")!!
        assertEquals(null, m.year); assertEquals(3, m.sub)
        assertEquals(3, TimeParser.parse("Mar 2024")!!.sub)
        assertNull(TimeParser.parse("13월"))
    }

    @Test fun quartersAndHalves() {
        val q = TimeParser.parse("2024년 1분기")!!
        assertEquals(Granularity.QUARTER, q.granularity); assertEquals(1, q.sub)
        assertEquals(2, TimeParser.parse("2024 2/4분기")!!.sub)
        assertEquals(3, TimeParser.parse("2024 3/4")!!.sub)
        assertEquals(4, TimeParser.parse("Q4 2023")!!.sub)
        assertEquals(4, TimeParser.parse("2023Q4")!!.sub)
        assertNull(TimeParser.parse("1/4"))
        assertEquals(2, TimeParser.parse("2024 하반기")!!.sub)
        assertEquals(Granularity.HALF, TimeParser.parse("상반기")!!.granularity)
    }

    @Test fun years() {
        assertEquals(Granularity.YEAR, TimeParser.parse("2024년")!!.granularity)
        assertNull(TimeParser.parse("2024"))
        assertNotNull(TimeParser.parse("2024", allowBareYear = true))
    }

    @Test fun ordering() {
        val a = TimeParser.parse("2023년 4분기")!!
        val b = TimeParser.parse("2024년 1월")!!
        assertTrue(a < b)
    }

    @Test fun excelSerial() {
        val t = TimeParser.fromExcelSerial(45352.0)!!
        assertEquals(2024, t.year); assertEquals(3, t.sub); assertEquals(1, t.day)
    }
}

class UnitSpecTest {
    @Test fun parses() {
        assertEquals(UnitSpec(1e3, "원"), UnitSpec.parse("천원"))
        assertEquals(UnitSpec(1e6, "원"), UnitSpec.parse("백만 원"))
        assertEquals(UnitSpec(1.0, "명"), UnitSpec.parse("명"))
        assertEquals(UnitSpec(1.0, "%"), UnitSpec.parse("%"))
        assertNull(UnitSpec.parse("서울특별시"))
        val (name, spec) = UnitSpec.fromHeader("매출액(천원)")
        assertEquals("매출액", name); assertEquals(1e3, spec!!.factor)
        assertEquals(1e6, UnitSpec.findInText("(단위: 백만원)")!!.factor)
    }
}

class DelimitedTest {
    @Test fun excelPaste() {
        val g = Delimited.sniff("월\t매출\t방문자\n1월\t320\t1,240\n2월\t280\t1,105\n")!!
        assertEquals(3, g.rows.size); assertEquals(listOf("월", "매출", "방문자"), g.rows[0])
    }

    @Test fun csvQuoted() {
        val g = Delimited.sniff("지역,인구\n서울,\"9,411,000\"\n부산,\"3,293,000\"\n\"대구, 경북\",\"5,000\"")!!
        assertEquals("9,411,000", g.rows[1][1])
        assertEquals("대구, 경북", g.rows[3][0])
    }

    @Test fun markdown() {
        val g = Delimited.sniff("| 항목 | 값 |\n|---|---|\n| A | 1 |\n| B | 2 |")!!
        assertEquals(3, g.rows.size); assertEquals("A", g.rows[1][0])
    }

    @Test fun multiSpace() {
        val g = Delimited.sniff("지역      2022     2023\n서울특별시  100   120\n부산광역시  80   90")!!
        assertEquals(3, g.rows[0].size)
    }

    @Test fun decode() {
        val s = "월,매출\n1월,320"
        assertEquals(s, TextDecode.decode(s.toByteArray(Charsets.UTF_8)))
        assertEquals(s, TextDecode.decode(s.toByteArray(charset("EUC-KR"))))
        assertEquals(s, TextDecode.decode(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + s.toByteArray()))
    }
}

class ImportTest {
    private fun best(text: String) = Importer.fromText(text).best!!.table

    @Test fun simpleTab() {
        val t = best("월\t매출(만원)\t방문자\n1월\t320\t1,240\n2월\t280\t1,105\n3월\t410\t1,630\n4월\t395\t1,580")
        assertEquals(ColumnType.TIME, t.columns[0].type)
        assertEquals(ColumnType.NUMBER, t.columns[1].type)
        assertEquals("매출", t.columns[1].name)
        assertEquals(3_200_000.0, t.columns[1].number(0))
        assertEquals(Scale.TEN_THOUSAND, t.columns[1].scale)
        assertEquals("만 원", t.columns[1].unitLabel)
        assertEquals(0, t.fixCount)
    }

    @Test fun mixedUnitsInColumn() {
        val t = best("월\t매출\n1월\t320만\n2월\t280만원\n3월\t410만\n4월\t395\n5월\t452만\n6월\t4,380,000원")
        val c = t.columns[1]
        assertEquals(ColumnType.NUMBER, c.type)
        assertEquals(Scale.TEN_THOUSAND, c.scale)
        assertEquals(3_950_000.0, c.number(3), "395 without 만 should be read as 395만")
        assertNotNull(c.cells[3].fix)
        assertEquals(4_380_000.0, c.number(5))
        assertEquals(438.0, c.scaled(5))
    }

    @Test fun totalRowsExcluded() {
        val t = best("지역\t인구\n서울\t100\n부산\t50\n대구\t30\n합계\t180")
        assertEquals(setOf(3), t.excludedRows.keys)
    }

    @Test fun totalByArithmetic() {
        val t = best("지역\t인구\n전국\t180\n서울\t100\n부산\t50\n대구\t30")
        assertEquals(setOf(0), t.excludedRows.keys)
    }

    @Test fun titleUnitAndNotes() {
        val text = "시도별 인구 현황\n(단위: 천명)\n\n시도\t2022\t2023\n서울\t9,428\t9,386\n부산\t3,317\t3,293\n대구\t2,363\t2,374\n\n출처: 통계청"
        val r = Importer.fromText(text)
        val t = r.best!!.table
        assertEquals("시도별 인구 현황", t.title)
        assertEquals(listOf("출처: 통계청"), t.notes)
        // Only two years: keep regions as rows so the chart compares regions across two years.
        assertEquals(ColumnType.TEXT, t.columns[0].type)
        assertEquals(listOf("시도", "2022", "2023"), t.columns.map { it.name })
        assertEquals(9_428_000.0, t.columns[1].number(0))
        assertEquals("천 명", t.columns[1].unitLabel)
    }

    @Test fun wideTimeTransposed() {
        val t = best("구분\t2019\t2020\t2021\t2022\nA\t1\t2\t3\t4\nB\t5\t6\t7\t8")
        assertEquals(ColumnType.TIME, t.columns[0].type)
        assertEquals(4, t.rowCount)
        assertEquals(listOf("기간", "A", "B"), t.columns.map { it.name })
    }

    @Test fun twoRowHeader() {
        val t = best("구분\t2023\t\t2024\t\n\t남\t여\t남\t여\n서울\t10\t11\t12\t13\n부산\t5\t6\t7\t8\n대구\t1\t2\t3\t4")
        assertEquals(listOf("구분", "2023 남", "2023 여", "2024 남", "2024 여"), t.columns.map { it.name })
        assertEquals(3, t.rowCount)
    }

    @Test fun freeText() {
        val t = best("1월 매출 320만, 방문 1,240명\n2월 매출 280만원, 방문 1,105명\n3월 매출 410만, 방문 1,630명\n4월 매출 395만, 방문 1,580명")
        assertEquals(ColumnType.TIME, t.columns[0].type)
        assertEquals(3, t.columns.size)
        assertEquals("매출", t.columns[1].name)
        assertEquals("방문", t.columns[2].name)
        assertEquals(4_100_000.0, t.columns[1].number(2))
        assertEquals(1630.0, t.columns[2].number(2))
    }

    @Test fun freeTextOneLine() {
        val t = best("1월 320 2월 280 3월 410 4월 395")
        assertEquals(4, t.rowCount)
        assertEquals(410.0, t.columns[1].number(2))
    }

    @Test fun labelValueLines() {
        val t = best("서울 9411\n부산 3293\n대구 2374\n인천 2997")
        assertEquals(4, t.rowCount)
        assertEquals(ColumnType.TEXT, t.columns[0].type)
        assertEquals(9411.0, t.columns[1].number(0))
    }

    @Test fun percentColumn() {
        val t = best("응답\t비율\n매우 만족\t32.5%\n만족\t41%\n보통\t18%\n불만\t8.5%")
        assertEquals("%", t.columns[1].unit)
        assertEquals(Scale.ONE, t.columns[1].scale)
        assertEquals(41.0, t.columns[1].number(1))
    }

    @Test fun negativeMarkers() {
        val t = best("항목\t증감\nA\t△5\nB\t3\nC\t(2)")
        assertEquals(-5.0, t.columns[1].number(0))
        assertEquals(-2.0, t.columns[1].number(2))
        assertNotNull(t.columns[1].cells[0].fix)
    }

    @Test fun yearColumn() {
        val t = best("연도\t출생아\n2019\t302,676\n2020\t272,337\n2021\t260,562\n2022\t249,186")
        assertEquals(ColumnType.TIME, t.columns[0].type)
        assertEquals(Granularity.YEAR, (t.columns[0].cells[0].value as Value.Time).t.granularity)
        assertEquals(Scale.TEN_THOUSAND, t.columns[1].scale)
    }

    @Test fun jsonArray() {
        val r = Importer.fromText("""[{"지역":"서울","인구":9411},{"지역":"부산","인구":3293},{"지역":"대구","인구":2374}]""")
        val t = r.best!!.table
        assertEquals(3, t.rowCount)
        assertEquals(9411.0, t.columns[1].number(0))
    }

    @Test fun garbage() {
        val r = Importer.fromText("안녕하세요 반갑습니다")
        assertTrue(r.candidates.isEmpty())
        assertNotNull(r.error)
    }

    @Test fun xlsFriendlyError() {
        val ole = byteArrayOf(0xD0.toByte(), 0xCF.toByte(), 0x11, 0xE0.toByte(), 0, 0, 0, 0, 0, 0)
        val r = Importer.fromFile("a.xls", ole)
        assertTrue(r.error!!.contains(".xlsx"))
    }
}

class FixNoiseTest {
    @Test fun onlyOddCellsAreFlagged() {
        val t = Importer.fromText("월\t매출\n1월\t320만\n2월\t280만원\n3월\t410만\n4월\t395\n5월\t452만\n6월\t4,380,000원").best!!.table
        val flagged = t.fixes().map { it.row }.toSet()
        assertEquals(setOf(3, 5), flagged)
    }

    @Test fun plainScaleIsNotAFix() {
        val t = Importer.fromText("지역\t인구\n서울\t9411000\n부산\t3293000\n대구\t2374000").best!!.table
        assertEquals(0, t.fixCount)
    }

    @Test fun onlyOneReadingIsOffered() {
        val r = Importer.fromText("월\t매출\n1월\t320만\n2월\t280만\n3월\t410만")
        assertEquals(1, r.candidates.size)
    }
}

class TotalInTimeColumnTest {
    @Test fun totalLabelKept() {
        val t = Importer.fromText("월\t매출\n1월\t320\n2월\t280\n3월\t410\n합계\t1010").best!!.table
        assertEquals("합계", t.columns[0].cells[3].raw)
        assertNull(t.columns[0].cells[3].fix)
        assertEquals("합계 행이라 그래프에서 뺐어요", t.excludedRows[3])
    }
}
