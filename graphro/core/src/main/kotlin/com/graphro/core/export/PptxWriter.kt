package com.graphro.core.export

import com.graphro.core.chart.ChartData
import com.graphro.core.chart.ChartSpec
import com.graphro.core.chart.ChartType
import com.graphro.core.chart.Colors
import com.graphro.core.chart.AxisMode
import com.graphro.core.text.Format
import com.graphro.core.text.I18n

/**
 * Builds a 16:9 .pptx whose chart is a real PowerPoint chart (editable data), not a picture.
 */
object PptxWriter {

    data class Options(
        val caption: String? = null,
        val speakerNotes: String? = null,
        val table: List<List<String>>? = null,
        val font: String = I18n.officeFont,
    )

    private const val W = 12192000L
    private const val H = 6858000L
    private const val NS_A = "http://schemas.openxmlformats.org/drawingml/2006/main"
    private const val NS_R = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
    private const val NS_P = "http://schemas.openxmlformats.org/presentationml/2006/main"
    private const val NS_C = "http://schemas.openxmlformats.org/drawingml/2006/chart"
    private const val REL = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"

    private fun hex(argb: Int) = String.format("%06X", argb and 0xFFFFFF)

    fun write(d: ChartData, spec: ChartSpec, opt: Options): ByteArray {
        val z = ZipBuilder()
        val hasTable = !opt.table.isNullOrEmpty()
        val slides = if (hasTable) 2 else 1
        val hasNotes = !opt.speakerNotes.isNullOrBlank()

        z.add("[Content_Types].xml", Xml.HEAD + buildString {
            append("<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">")
            append("<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>")
            append("<Default Extension=\"xml\" ContentType=\"application/xml\"/>")
            append("<Default Extension=\"xlsx\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet\"/>")
            append("<Override PartName=\"/ppt/presentation.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.presentationml.presentation.main+xml\"/>")
            append("<Override PartName=\"/ppt/slideMasters/slideMaster1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.presentationml.slideMaster+xml\"/>")
            append("<Override PartName=\"/ppt/slideLayouts/slideLayout1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.presentationml.slideLayout+xml\"/>")
            for (i in 1..slides) append("<Override PartName=\"/ppt/slides/slide$i.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.presentationml.slide+xml\"/>")
            append("<Override PartName=\"/ppt/theme/theme1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.theme+xml\"/>")
            if (hasNotes) {
                append("<Override PartName=\"/ppt/theme/theme2.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.theme+xml\"/>")
                append("<Override PartName=\"/ppt/notesMasters/notesMaster1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.presentationml.notesMaster+xml\"/>")
                append("<Override PartName=\"/ppt/notesSlides/notesSlide1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.presentationml.notesSlide+xml\"/>")
            }
            append("<Override PartName=\"/ppt/charts/chart1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.drawingml.chart+xml\"/>")
            append("<Override PartName=\"/ppt/presProps.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.presentationml.presProps+xml\"/>")
            append("<Override PartName=\"/ppt/viewProps.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.presentationml.viewProps+xml\"/>")
            append("<Override PartName=\"/ppt/tableStyles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.presentationml.tableStyles+xml\"/>")
            append("<Override PartName=\"/docProps/core.xml\" ContentType=\"application/vnd.openxmlformats-package.core-properties+xml\"/>")
            append("<Override PartName=\"/docProps/app.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.extended-properties+xml\"/>")
            append("</Types>")
        })
        z.add("_rels/.rels", Xml.HEAD + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
            "<Relationship Id=\"rId1\" Type=\"$REL/officeDocument\" Target=\"ppt/presentation.xml\"/>" +
            "<Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/package/2006/relationships/metadata/core-properties\" Target=\"docProps/core.xml\"/>" +
            "<Relationship Id=\"rId3\" Type=\"$REL/extended-properties\" Target=\"docProps/app.xml\"/>" +
            "</Relationships>")
        z.add("docProps/core.xml", Xml.HEAD + "<cp:coreProperties xmlns:cp=\"http://schemas.openxmlformats.org/package/2006/metadata/core-properties\" xmlns:dc=\"http://purl.org/dc/elements/1.1/\" xmlns:dcterms=\"http://purl.org/dc/terms/\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"><dc:title>${Xml.esc(spec.title)}</dc:title><dc:creator>Graphro</dc:creator></cp:coreProperties>")
        z.add("docProps/app.xml", Xml.HEAD + "<Properties xmlns=\"http://schemas.openxmlformats.org/officeDocument/2006/extended-properties\"><Application>Graphro</Application><Slides>$slides</Slides></Properties>")

        // presentation
        z.add("ppt/presentation.xml", Xml.HEAD + buildString {
            append("<p:presentation xmlns:a=\"$NS_A\" xmlns:r=\"$NS_R\" xmlns:p=\"$NS_P\" saveSubsetFonts=\"1\">")
            append("<p:sldMasterIdLst><p:sldMasterId id=\"2147483648\" r:id=\"rId1\"/></p:sldMasterIdLst>")
            if (hasNotes) append("<p:notesMasterIdLst><p:notesMasterId r:id=\"rId20\"/></p:notesMasterIdLst>")
            append("<p:sldIdLst>")
            for (i in 1..slides) append("<p:sldId id=\"${255 + i}\" r:id=\"rId${1 + i}\"/>")
            append("</p:sldIdLst>")
            append("<p:sldSz cx=\"$W\" cy=\"$H\"/><p:notesSz cx=\"6858000\" cy=\"9144000\"/>")
            append("</p:presentation>")
        })
        z.add("ppt/_rels/presentation.xml.rels", Xml.HEAD + buildString {
            append("<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">")
            append("<Relationship Id=\"rId1\" Type=\"$REL/slideMaster\" Target=\"slideMasters/slideMaster1.xml\"/>")
            for (i in 1..slides) append("<Relationship Id=\"rId${1 + i}\" Type=\"$REL/slide\" Target=\"slides/slide$i.xml\"/>")
            append("<Relationship Id=\"rId10\" Type=\"$REL/presProps\" Target=\"presProps.xml\"/>")
            append("<Relationship Id=\"rId11\" Type=\"$REL/viewProps\" Target=\"viewProps.xml\"/>")
            append("<Relationship Id=\"rId12\" Type=\"$REL/theme\" Target=\"theme/theme1.xml\"/>")
            append("<Relationship Id=\"rId13\" Type=\"$REL/tableStyles\" Target=\"tableStyles.xml\"/>")
            if (hasNotes) append("<Relationship Id=\"rId20\" Type=\"$REL/notesMaster\" Target=\"notesMasters/notesMaster1.xml\"/>")
            append("</Relationships>")
        })
        z.add("ppt/presProps.xml", Xml.HEAD + "<p:presentationPr xmlns:a=\"$NS_A\" xmlns:r=\"$NS_R\" xmlns:p=\"$NS_P\"/>")
        z.add("ppt/viewProps.xml", Xml.HEAD + "<p:viewPr xmlns:a=\"$NS_A\" xmlns:r=\"$NS_R\" xmlns:p=\"$NS_P\"><p:normalViewPr><p:restoredLeft sz=\"15620\"/><p:restoredTop sz=\"94660\"/></p:normalViewPr><p:gridSpacing cx=\"76200\" cy=\"76200\"/></p:viewPr>")
        z.add("ppt/tableStyles.xml", Xml.HEAD + "<a:tblStyleLst xmlns:a=\"$NS_A\" def=\"{5C22544A-7EE6-4342-B048-85BDC9FD1C3A}\"/>")
        z.add("ppt/theme/theme1.xml", theme("Graphro", opt.font))
        z.add("ppt/slideMasters/slideMaster1.xml", master(opt.font))
        z.add("ppt/slideMasters/_rels/slideMaster1.xml.rels", rels(listOf("rId1" to ("slideLayout" to "../slideLayouts/slideLayout1.xml"), "rId2" to ("theme" to "../theme/theme1.xml"))))
        z.add("ppt/slideLayouts/slideLayout1.xml", Xml.HEAD + "<p:sldLayout xmlns:a=\"$NS_A\" xmlns:r=\"$NS_R\" xmlns:p=\"$NS_P\" type=\"blank\" preserve=\"1\"><p:cSld name=\"Blank\">${emptyTree()}</p:cSld><p:clrMapOvr><a:masterClrMapping/></p:clrMapOvr></p:sldLayout>")
        z.add("ppt/slideLayouts/_rels/slideLayout1.xml.rels", rels(listOf("rId1" to ("slideMaster" to "../slideMasters/slideMaster1.xml"))))

        // slide 1: chart
        z.add("ppt/slides/slide1.xml", chartSlide(d, spec, opt))
        val s1rels = mutableListOf("rId1" to ("slideLayout" to "../slideLayouts/slideLayout1.xml"), "rId2" to ("chart" to "../charts/chart1.xml"))
        if (hasNotes) s1rels += "rId3" to ("notesSlide" to "../notesSlides/notesSlide1.xml")
        z.add("ppt/slides/_rels/slide1.xml.rels", rels(s1rels))
        z.add("ppt/charts/chart1.xml", chartXml(d, spec, opt.font))
        z.add("ppt/charts/_rels/chart1.xml.rels", rels(listOf("rId1" to ("package" to "../embeddings/Microsoft_Excel_Worksheet1.xlsx")),
            typeOverride = mapOf("package" to "http://schemas.openxmlformats.org/officeDocument/2006/relationships/package")))
        z.add("ppt/embeddings/Microsoft_Excel_Worksheet1.xlsx", XlsxWriter.write(listOf(SheetData("Sheet1", chartSheetRows(d, spec)))))

        if (hasTable) {
            z.add("ppt/slides/slide2.xml", tableSlide(spec.title, opt.table!!, opt.font))
            z.add("ppt/slides/_rels/slide2.xml.rels", rels(listOf("rId1" to ("slideLayout" to "../slideLayouts/slideLayout1.xml"))))
        }
        if (hasNotes) {
            z.add("ppt/theme/theme2.xml", theme("Notes", opt.font))
            z.add("ppt/notesMasters/notesMaster1.xml", notesMaster())
            z.add("ppt/notesMasters/_rels/notesMaster1.xml.rels", rels(listOf("rId1" to ("theme" to "../theme/theme2.xml"))))
            z.add("ppt/notesSlides/notesSlide1.xml", notesSlide(opt.speakerNotes!!, opt.font))
            z.add("ppt/notesSlides/_rels/notesSlide1.xml.rels", rels(listOf("rId1" to ("notesMaster" to "../notesMasters/notesMaster1.xml"), "rId2" to ("slide" to "../slides/slide1.xml"))))
        }
        return z.bytes()
    }

    private fun rels(list: List<Pair<String, Pair<String, String>>>, typeOverride: Map<String, String> = emptyMap()): String = Xml.HEAD + buildString {
        append("<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">")
        for ((id, tt) in list) {
            val type = typeOverride[tt.first] ?: "$REL/${tt.first}"
            append("<Relationship Id=\"$id\" Type=\"$type\" Target=\"${tt.second}\"/>")
        }
        append("</Relationships>")
    }

    private fun emptyTree(extra: String = "") =
        "<p:spTree><p:nvGrpSpPr><p:cNvPr id=\"1\" name=\"\"/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr><p:grpSpPr><a:xfrm><a:off x=\"0\" y=\"0\"/><a:ext cx=\"0\" cy=\"0\"/><a:chOff x=\"0\" y=\"0\"/><a:chExt cx=\"0\" cy=\"0\"/></a:xfrm></p:grpSpPr>$extra</p:spTree>"

    private fun runProps(sz: Int, color: String, bold: Boolean, font: String) =
        "<a:rPr lang=\"ko-KR\" altLang=\"en-US\" sz=\"$sz\"${if (bold) " b=\"1\"" else ""} dirty=\"0\"><a:solidFill><a:srgbClr val=\"$color\"/></a:solidFill><a:latin typeface=\"${Xml.esc(font)}\"/><a:ea typeface=\"${Xml.esc(font)}\"/><a:cs typeface=\"${Xml.esc(font)}\"/></a:rPr>"

    private fun textBox(id: Int, name: String, x: Long, y: Long, cx: Long, cy: Long, text: String, sz: Int, color: String, bold: Boolean, font: String): String =
        "<p:sp><p:nvSpPr><p:cNvPr id=\"$id\" name=\"${Xml.esc(name)}\"/><p:cNvSpPr txBox=\"1\"/><p:nvPr/></p:nvSpPr>" +
            "<p:spPr><a:xfrm><a:off x=\"$x\" y=\"$y\"/><a:ext cx=\"$cx\" cy=\"$cy\"/></a:xfrm><a:prstGeom prst=\"rect\"><a:avLst/></a:prstGeom><a:noFill/></p:spPr>" +
            "<p:txBody><a:bodyPr wrap=\"square\" lIns=\"0\" tIns=\"0\" rIns=\"0\" bIns=\"0\" rtlCol=\"0\" anchor=\"t\"><a:spAutoFit/></a:bodyPr><a:lstStyle/>" +
            text.split('\n').joinToString("") { line -> "<a:p><a:r>${runProps(sz, color, bold, font)}<a:t>${Xml.esc(line)}</a:t></a:r></a:p>" } +
            "</p:txBody></p:sp>"

    private fun chartSlide(d: ChartData, spec: ChartSpec, opt: Options): String {
        val m = 685800L
        val innerW = W - 2 * m
        val sb = StringBuilder()
        var y = 457200L
        sb.append(textBox(2, "제목", m, y, innerW, 600000, spec.title, 2800, "121212", true, opt.font))
        y += 640000
        if (d.unitLabel.isNotEmpty()) sb.append(textBox(3, "단위", m, y, innerW, 300000, "${I18n.tr("단위", "Unit", "単位")}: ${d.unitLabel}", 1300, "6E6E75", false, opt.font))
        y += 380000
        val captionH = if (!opt.caption.isNullOrBlank()) 520000L else 0L
        val sourceH = if (!spec.source.isNullOrBlank()) 300000L else 0L
        val chartH = H - y - 420000 - captionH - sourceH
        sb.append("<p:graphicFrame><p:nvGraphicFramePr><p:cNvPr id=\"4\" name=\"차트\"/><p:cNvGraphicFramePr/><p:nvPr/></p:nvGraphicFramePr>")
        sb.append("<p:xfrm><a:off x=\"$m\" y=\"$y\"/><a:ext cx=\"$innerW\" cy=\"$chartH\"/></p:xfrm>")
        sb.append("<a:graphic><a:graphicData uri=\"$NS_C\"><c:chart xmlns:c=\"$NS_C\" r:id=\"rId2\"/></a:graphicData></a:graphic></p:graphicFrame>")
        y += chartH + 80000
        if (!opt.caption.isNullOrBlank()) { sb.append(textBox(5, "해석", m, y, innerW, 440000, opt.caption, 1600, "4A4A50", false, opt.font)); y += captionH }
        if (!spec.source.isNullOrBlank()) sb.append(textBox(6, "출처", m, y, innerW, 260000, "${I18n.tr("출처", "Source", "出典")}: ${spec.source}", 1100, "9A9AA0", false, opt.font))
        return Xml.HEAD + "<p:sld xmlns:a=\"$NS_A\" xmlns:r=\"$NS_R\" xmlns:p=\"$NS_P\"><p:cSld>${emptyTree(sb.toString())}</p:cSld><p:clrMapOvr><a:masterClrMapping/></p:clrMapOvr></p:sld>"
    }

    private fun tableSlide(title: String, table: List<List<String>>, font: String): String {
        val m = 685800L
        val innerW = W - 2 * m
        val rows = table.take(16)
        val cols = rows.maxOf { it.size }.coerceAtLeast(1)
        val colW = innerW / cols
        val rowH = 340000L
        val sb = StringBuilder()
        sb.append(textBox(2, "제목", m, 457200, innerW, 600000, "$title · ${I18n.tr("원본 표", "Source table", "元の表")}", 2400, "121212", true, font))
        sb.append("<p:graphicFrame><p:nvGraphicFramePr><p:cNvPr id=\"3\" name=\"표\"/><p:cNvGraphicFramePr><a:graphicFrameLocks noGrp=\"1\"/></p:cNvGraphicFramePr><p:nvPr/></p:nvGraphicFramePr>")
        sb.append("<p:xfrm><a:off x=\"$m\" y=\"1280160\"/><a:ext cx=\"${colW * cols}\" cy=\"${rowH * rows.size}\"/></p:xfrm>")
        sb.append("<a:graphic><a:graphicData uri=\"http://schemas.openxmlformats.org/drawingml/2006/table\"><a:tbl><a:tblPr firstRow=\"1\"/><a:tblGrid>")
        repeat(cols) { sb.append("<a:gridCol w=\"$colW\"/>") }
        sb.append("</a:tblGrid>")
        rows.forEachIndexed { r, row ->
            sb.append("<a:tr h=\"$rowH\">")
            for (c in 0 until cols) {
                val t = row.getOrElse(c) { "" }
                val right = r > 0 && c > 0
                sb.append("<a:tc><a:txBody><a:bodyPr/><a:lstStyle/><a:p><a:pPr algn=\"${if (right) "r" else "l"}\"/><a:r>${runProps(1200, if (r == 0) "121212" else "2E2E33", r == 0, font)}<a:t>${Xml.esc(t)}</a:t></a:r></a:p></a:txBody>")
                sb.append("<a:tcPr marL=\"68580\" marR=\"68580\" marT=\"34290\" marB=\"34290\" anchor=\"ctr\">")
                sb.append("<a:lnL w=\"0\"><a:noFill/></a:lnL><a:lnR w=\"0\"><a:noFill/></a:lnR>")
                sb.append("<a:lnT w=\"${if (r == 1) 12700 else 0}\">${if (r == 1) "<a:solidFill><a:srgbClr val=\"121212\"/></a:solidFill>" else "<a:noFill/>"}</a:lnT>")
                sb.append("<a:lnB w=\"6350\"><a:solidFill><a:srgbClr val=\"ECECE9\"/></a:solidFill></a:lnB>")
                sb.append("<a:noFill/></a:tcPr></a:tc>")
            }
            sb.append("</a:tr>")
        }
        sb.append("</a:tbl></a:graphicData></a:graphic></p:graphicFrame>")
        if (table.size > 16) sb.append(textBox(4, "안내", m, 1280160 + rowH * rows.size + 120000, innerW, 260000, I18n.tr("처음 15행만 표시했어요 (전체 ${table.size - 1}행)", "Showing the first 15 rows (of ${table.size - 1})", "最初の15行のみ表示しています（全${table.size - 1}行）"), 1100, "9A9AA0", false, font))
        return Xml.HEAD + "<p:sld xmlns:a=\"$NS_A\" xmlns:r=\"$NS_R\" xmlns:p=\"$NS_P\"><p:cSld>${emptyTree(sb.toString())}</p:cSld><p:clrMapOvr><a:masterClrMapping/></p:clrMapOvr></p:sld>"
    }

    private fun notesMaster(): String = Xml.HEAD +
        "<p:notesMaster xmlns:a=\"$NS_A\" xmlns:r=\"$NS_R\" xmlns:p=\"$NS_P\"><p:cSld><p:bg><p:bgRef idx=\"1001\"><a:schemeClr val=\"bg1\"/></p:bgRef></p:bg>" +
        emptyTree(
            "<p:sp><p:nvSpPr><p:cNvPr id=\"2\" name=\"Slide Image\"/><p:cNvSpPr><a:spLocks noGrp=\"1\" noRot=\"1\" noChangeAspect=\"1\"/></p:cNvSpPr><p:nvPr><p:ph type=\"sldImg\" idx=\"2\"/></p:nvPr></p:nvSpPr><p:spPr><a:xfrm><a:off x=\"381000\" y=\"685800\"/><a:ext cx=\"6096000\" cy=\"3429000\"/></a:xfrm><a:prstGeom prst=\"rect\"><a:avLst/></a:prstGeom><a:noFill/></p:spPr></p:sp>" +
                "<p:sp><p:nvSpPr><p:cNvPr id=\"3\" name=\"Notes\"/><p:cNvSpPr><a:spLocks noGrp=\"1\"/></p:cNvSpPr><p:nvPr><p:ph type=\"body\" sz=\"quarter\" idx=\"3\"/></p:nvPr></p:nvSpPr><p:spPr><a:xfrm><a:off x=\"685800\" y=\"4400550\"/><a:ext cx=\"5486400\" cy=\"3600450\"/></a:xfrm><a:prstGeom prst=\"rect\"><a:avLst/></a:prstGeom></p:spPr><p:txBody><a:bodyPr/><a:lstStyle/><a:p><a:endParaRPr lang=\"ko-KR\"/></a:p></p:txBody></p:sp>"
        ) +
        "</p:cSld><p:clrMap bg1=\"lt1\" tx1=\"dk1\" bg2=\"lt2\" tx2=\"dk2\" accent1=\"accent1\" accent2=\"accent2\" accent3=\"accent3\" accent4=\"accent4\" accent5=\"accent5\" accent6=\"accent6\" hlink=\"hlink\" folHlink=\"folHlink\"/></p:notesMaster>"

    private fun notesSlide(text: String, font: String): String = Xml.HEAD +
        "<p:notes xmlns:a=\"$NS_A\" xmlns:r=\"$NS_R\" xmlns:p=\"$NS_P\"><p:cSld>" +
        emptyTree(
            "<p:sp><p:nvSpPr><p:cNvPr id=\"2\" name=\"Slide Image\"/><p:cNvSpPr><a:spLocks noGrp=\"1\" noRot=\"1\" noChangeAspect=\"1\"/></p:cNvSpPr><p:nvPr><p:ph type=\"sldImg\"/></p:nvPr></p:nvSpPr><p:spPr/></p:sp>" +
                "<p:sp><p:nvSpPr><p:cNvPr id=\"3\" name=\"Notes\"/><p:cNvSpPr><a:spLocks noGrp=\"1\"/></p:cNvSpPr><p:nvPr><p:ph type=\"body\" idx=\"1\"/></p:nvPr></p:nvSpPr><p:spPr/><p:txBody><a:bodyPr/><a:lstStyle/>" +
                text.split('\n').joinToString("") { "<a:p><a:r>${runProps(1200, "121212", false, font)}<a:t>${Xml.esc(it)}</a:t></a:r></a:p>" } +
                "</p:txBody></p:sp>"
        ) +
        "</p:cSld><p:clrMapOvr><a:masterClrMapping/></p:clrMapOvr></p:notes>"

    private fun master(font: String): String = Xml.HEAD +
        "<p:sldMaster xmlns:a=\"$NS_A\" xmlns:r=\"$NS_R\" xmlns:p=\"$NS_P\"><p:cSld><p:bg><p:bgPr><a:solidFill><a:srgbClr val=\"FFFFFF\"/></a:solidFill><a:effectLst/></p:bgPr></p:bg>${emptyTree()}</p:cSld>" +
        "<p:clrMap bg1=\"lt1\" tx1=\"dk1\" bg2=\"lt2\" tx2=\"dk2\" accent1=\"accent1\" accent2=\"accent2\" accent3=\"accent3\" accent4=\"accent4\" accent5=\"accent5\" accent6=\"accent6\" hlink=\"hlink\" folHlink=\"folHlink\"/>" +
        "<p:sldLayoutIdLst><p:sldLayoutId id=\"2147483649\" r:id=\"rId1\"/></p:sldLayoutIdLst>" +
        "<p:txStyles><p:titleStyle><a:lvl1pPr><a:defRPr sz=\"2800\"><a:latin typeface=\"${Xml.esc(font)}\"/><a:ea typeface=\"${Xml.esc(font)}\"/></a:defRPr></a:lvl1pPr></p:titleStyle>" +
        "<p:bodyStyle><a:lvl1pPr><a:defRPr sz=\"1800\"><a:latin typeface=\"${Xml.esc(font)}\"/><a:ea typeface=\"${Xml.esc(font)}\"/></a:defRPr></a:lvl1pPr></p:bodyStyle>" +
        "<p:otherStyle><a:lvl1pPr><a:defRPr sz=\"1800\"><a:latin typeface=\"${Xml.esc(font)}\"/><a:ea typeface=\"${Xml.esc(font)}\"/></a:defRPr></a:lvl1pPr></p:otherStyle></p:txStyles></p:sldMaster>"

    private fun theme(name: String, font: String): String {
        val f = Xml.esc(font)
        return Xml.HEAD + "<a:theme xmlns:a=\"$NS_A\" name=\"$name\"><a:themeElements>" +
            "<a:clrScheme name=\"Graphro\"><a:dk1><a:srgbClr val=\"121212\"/></a:dk1><a:lt1><a:srgbClr val=\"FFFFFF\"/></a:lt1><a:dk2><a:srgbClr val=\"2E2E33\"/></a:dk2><a:lt2><a:srgbClr val=\"F4F4F1\"/></a:lt2>" +
            "<a:accent1><a:srgbClr val=\"2E2E33\"/></a:accent1><a:accent2><a:srgbClr val=\"F04E23\"/></a:accent2><a:accent3><a:srgbClr val=\"2C3E9E\"/></a:accent3><a:accent4><a:srgbClr val=\"9A9AA0\"/></a:accent4><a:accent5><a:srgbClr val=\"E8A33D\"/></a:accent5><a:accent6><a:srgbClr val=\"5AA9A0\"/></a:accent6>" +
            "<a:hlink><a:srgbClr val=\"2C3E9E\"/></a:hlink><a:folHlink><a:srgbClr val=\"6E6E75\"/></a:folHlink></a:clrScheme>" +
            "<a:fontScheme name=\"Graphro\"><a:majorFont><a:latin typeface=\"$f\"/><a:ea typeface=\"$f\"/><a:cs typeface=\"\"/><a:font script=\"Hang\" typeface=\"$f\"/></a:majorFont><a:minorFont><a:latin typeface=\"$f\"/><a:ea typeface=\"$f\"/><a:cs typeface=\"\"/><a:font script=\"Hang\" typeface=\"$f\"/></a:minorFont></a:fontScheme>" +
            "<a:fmtScheme name=\"Graphro\"><a:fillStyleLst><a:solidFill><a:schemeClr val=\"phClr\"/></a:solidFill><a:solidFill><a:schemeClr val=\"phClr\"/></a:solidFill><a:solidFill><a:schemeClr val=\"phClr\"/></a:solidFill></a:fillStyleLst>" +
            "<a:lnStyleLst><a:ln w=\"6350\"><a:solidFill><a:schemeClr val=\"phClr\"/></a:solidFill></a:ln><a:ln w=\"12700\"><a:solidFill><a:schemeClr val=\"phClr\"/></a:solidFill></a:ln><a:ln w=\"19050\"><a:solidFill><a:schemeClr val=\"phClr\"/></a:solidFill></a:ln></a:lnStyleLst>" +
            "<a:effectStyleLst><a:effectStyle><a:effectLst/></a:effectStyle><a:effectStyle><a:effectLst/></a:effectStyle><a:effectStyle><a:effectLst/></a:effectStyle></a:effectStyleLst>" +
            "<a:bgFillStyleLst><a:solidFill><a:schemeClr val=\"phClr\"/></a:solidFill><a:solidFill><a:schemeClr val=\"phClr\"/></a:solidFill><a:solidFill><a:schemeClr val=\"phClr\"/></a:solidFill></a:bgFillStyleLst></a:fmtScheme>" +
            "</a:themeElements><a:objectDefaults/><a:extraClrSchemeLst/></a:theme>"
    }

    // ---- chart part ----

    fun chartSheetRows(d: ChartData, spec: ChartSpec): List<List<Any?>> {
        val header = mutableListOf<Any?>(if (d.isTime) I18n.period() else I18n.item())
        val series = if (spec.type == ChartType.DONUT) d.series.take(1) else d.series
        series.forEach { header.add(it.name) }
        val target = spec.target?.takeIf { spec.type != ChartType.DONUT && spec.type != ChartType.HBAR }
        if (target != null) header.add(I18n.tr("목표", "Target", "目標"))
        val rows = mutableListOf<List<Any?>>(header)
        for (i in d.categories.indices) {
            val r = mutableListOf<Any?>(d.categories[i])
            series.forEach { r.add(it.values.getOrNull(i)) }
            if (target != null) r.add(target)
            rows.add(r)
        }
        return rows
    }

    private fun solid(c: String) = "<a:solidFill><a:srgbClr val=\"$c\"/></a:solidFill>"

    private fun txPr(sz: Int, color: String, font: String, bold: Boolean = false) =
        "<c:txPr><a:bodyPr/><a:lstStyle/><a:p><a:pPr><a:defRPr sz=\"$sz\"${if (bold) " b=\"1\"" else ""}>${solid(color)}<a:latin typeface=\"${Xml.esc(font)}\"/><a:ea typeface=\"${Xml.esc(font)}\"/></a:defRPr></a:pPr><a:endParaRPr lang=\"ko-KR\"/></a:p></c:txPr>"

    private fun strRef(f: String, values: List<String>) = buildString {
        append("<c:strRef><c:f>${Xml.esc(f)}</c:f><c:strCache><c:ptCount val=\"${values.size}\"/>")
        values.forEachIndexed { i, v -> append("<c:pt idx=\"$i\"><c:v>${Xml.esc(v)}</c:v></c:pt>") }
        append("</c:strCache></c:strRef>")
    }

    private fun numRef(f: String, values: List<Double?>, fmt: String) = buildString {
        append("<c:numRef><c:f>${Xml.esc(f)}</c:f><c:numCache><c:formatCode>${Xml.esc(fmt)}</c:formatCode><c:ptCount val=\"${values.size}\"/>")
        values.forEachIndexed { i, v -> if (v != null) append("<c:pt idx=\"$i\"><c:v>${Xml.num(v)}</c:v></c:pt>") }
        append("</c:numCache></c:numRef>")
    }

    private fun dLblsXml(showAll: Boolean, onlyIdx: Int, font: String, pos: String?, labelColor: String = "121212"): String = buildString {
        append("<c:dLbls>")
        if (!showAll && onlyIdx >= 0) {
            append("<c:dLbl><c:idx val=\"$onlyIdx\"/><c:spPr><a:noFill/><a:ln><a:noFill/></a:ln></c:spPr>${txPr(1300, labelColor, font, true)}")
            if (pos != null) append("<c:dLblPos val=\"$pos\"/>")
            append("<c:showLegendKey val=\"0\"/><c:showVal val=\"1\"/><c:showCatName val=\"0\"/><c:showSerName val=\"0\"/><c:showPercent val=\"0\"/><c:showBubbleSize val=\"0\"/></c:dLbl>")
        }
        append("<c:spPr><a:noFill/><a:ln><a:noFill/></a:ln></c:spPr>${txPr(1100, "4A4A50", font)}")
        if (showAll && pos != null) append("<c:dLblPos val=\"$pos\"/>")
        append("<c:showLegendKey val=\"0\"/><c:showVal val=\"${if (showAll) 1 else 0}\"/><c:showCatName val=\"0\"/><c:showSerName val=\"0\"/><c:showPercent val=\"0\"/><c:showBubbleSize val=\"0\"/>")
        append("</c:dLbls>")
    }

    fun chartXml(d: ChartData, spec: ChartSpec, font: String): String {
        val n = d.categories.size
        val catRef = "Sheet1!\$A\$2:\$A\$${n + 1}"
        val fmt = if (d.decimals == 0) "#,##0" else "#,##0." + "0".repeat(d.decimals)
        val hl = d.highlight
        val hlColor = hex(spec.highlightColor)
        val single = d.series.size == 1
        val baseColor = if (single && hl >= 0) hex(Colors.FOG) else hex(Colors.GRAPHITE)
        val sb = StringBuilder()
        sb.append(Xml.HEAD)
        sb.append("<c:chartSpace xmlns:c=\"$NS_C\" xmlns:a=\"$NS_A\" xmlns:r=\"$NS_R\"><c:date1904 val=\"0\"/><c:lang val=\"ko-KR\"/><c:roundedCorners val=\"0\"/>")
        sb.append("<c:chart><c:autoTitleDeleted val=\"1\"/><c:plotArea><c:layout/>")

        fun seriesColor(i: Int): String = if (single) (if (spec.type == ChartType.LINE || spec.type == ChartType.AREA) hex(Colors.GRAPHITE) else baseColor) else hex(Colors.SERIES[i % Colors.SERIES.size])
        fun colRef(i: Int) = XlsxWriter.colName(i + 1)

        val targetIdx = d.series.size
        val target = spec.target?.takeIf { spec.type != ChartType.DONUT && spec.type != ChartType.HBAR }
        fun targetSeries(): String = buildString {
            if (target == null) return@buildString
            append("<c:ser><c:idx val=\"$targetIdx\"/><c:order val=\"$targetIdx\"/><c:tx>${strRef("Sheet1!\$${colRef(targetIdx)}\$1", listOf(I18n.tr("목표", "Target", "目標")))}</c:tx>")
            append("<c:spPr><a:ln w=\"15875\" cap=\"rnd\">${solid("121212")}<a:prstDash val=\"dash\"/></a:ln></c:spPr><c:marker><c:symbol val=\"none\"/></c:marker>")
            append("<c:cat>${strRef(catRef, d.categories)}</c:cat><c:val>${numRef("Sheet1!\$${colRef(targetIdx)}\$2:\$${colRef(targetIdx)}\$${n + 1}", List(n) { target }, fmt)}</c:val><c:smooth val=\"0\"/></c:ser>")
        }

        when (spec.type) {
            ChartType.BAR, ChartType.STACKED, ChartType.HBAR -> {
                val stacked = spec.type == ChartType.STACKED
                sb.append("<c:barChart><c:barDir val=\"${if (spec.type == ChartType.HBAR) "bar" else "col"}\"/><c:grouping val=\"${if (stacked) "stacked" else "clustered"}\"/><c:varyColors val=\"0\"/>")
                d.series.forEachIndexed { i, s ->
                    val c = seriesColor(i)
                    sb.append("<c:ser><c:idx val=\"$i\"/><c:order val=\"$i\"/><c:tx>${strRef("Sheet1!\$${colRef(i)}\$1", listOf(s.name))}</c:tx>")
                    sb.append("<c:spPr>${solid(c)}<a:ln><a:noFill/></a:ln></c:spPr><c:invertIfNegative val=\"0\"/>")
                    if (single && hl >= 0) sb.append("<c:dPt><c:idx val=\"$hl\"/><c:invertIfNegative val=\"0\"/><c:bubble3D val=\"0\"/><c:spPr>${solid(hlColor)}<a:ln><a:noFill/></a:ln></c:spPr></c:dPt>")
                    sb.append(dLblsXml(spec.showValues, if (single) hl else -1, spec.font(font), if (stacked) "ctr" else "outEnd"))
                    sb.append("<c:cat>${strRef(catRef, d.categories)}</c:cat><c:val>${numRef("Sheet1!\$${colRef(i)}\$2:\$${colRef(i)}\$${n + 1}", s.values, fmt)}</c:val></c:ser>")
                }
                sb.append("<c:gapWidth val=\"${if (spec.type == ChartType.HBAR) 50 else 70}\"/>")
                if (stacked) sb.append("<c:overlap val=\"100\"/>") else if (!single) sb.append("<c:overlap val=\"-10\"/>")
                sb.append("<c:axId val=\"111\"/><c:axId val=\"222\"/></c:barChart>")
                if (target != null) sb.append("<c:lineChart><c:grouping val=\"standard\"/><c:varyColors val=\"0\"/>${targetSeries()}<c:marker val=\"1\"/><c:axId val=\"111\"/><c:axId val=\"222\"/></c:lineChart>")
            }
            ChartType.LINE -> {
                sb.append("<c:lineChart><c:grouping val=\"standard\"/><c:varyColors val=\"0\"/>")
                d.series.forEachIndexed { i, s ->
                    val c = seriesColor(i)
                    sb.append("<c:ser><c:idx val=\"$i\"/><c:order val=\"$i\"/><c:tx>${strRef("Sheet1!\$${colRef(i)}\$1", listOf(s.name))}</c:tx>")
                    sb.append("<c:spPr><a:ln w=\"28575\" cap=\"rnd\">${solid(c)}<a:round/></a:ln></c:spPr>")
                    sb.append("<c:marker><c:symbol val=\"circle\"/><c:size val=\"5\"/><c:spPr>${solid("FFFFFF")}<a:ln w=\"19050\">${solid(c)}</a:ln></c:spPr></c:marker>")
                    if (single && hl >= 0) sb.append("<c:dPt><c:idx val=\"$hl\"/><c:marker><c:symbol val=\"circle\"/><c:size val=\"9\"/><c:spPr>${solid(hlColor)}<a:ln w=\"19050\">${solid("FFFFFF")}</a:ln></c:spPr></c:marker><c:bubble3D val=\"0\"/></c:dPt>")
                    sb.append(dLblsXml(spec.showValues, if (single) hl else -1, spec.font(font), "t"))
                    sb.append("<c:cat>${strRef(catRef, d.categories)}</c:cat><c:val>${numRef("Sheet1!\$${colRef(i)}\$2:\$${colRef(i)}\$${n + 1}", s.values, fmt)}</c:val><c:smooth val=\"0\"/></c:ser>")
                }
                sb.append(targetSeries())
                sb.append("<c:marker val=\"1\"/><c:axId val=\"111\"/><c:axId val=\"222\"/></c:lineChart>")
            }
            ChartType.AREA -> {
                val stacked = d.series.size > 1
                sb.append("<c:areaChart><c:grouping val=\"${if (stacked) "stacked" else "standard"}\"/><c:varyColors val=\"0\"/>")
                d.series.forEachIndexed { i, s ->
                    val c = if (single) "E3E3E5" else hex(Colors.SERIES[i % Colors.SERIES.size])
                    sb.append("<c:ser><c:idx val=\"$i\"/><c:order val=\"$i\"/><c:tx>${strRef("Sheet1!\$${colRef(i)}\$1", listOf(s.name))}</c:tx>")
                    sb.append("<c:spPr>${solid(c)}<a:ln w=\"22225\">${solid(if (single) "2E2E33" else c)}</a:ln></c:spPr>")
                    sb.append(dLblsXml(spec.showValues, if (single) hl else -1, spec.font(font), null))
                    sb.append("<c:cat>${strRef(catRef, d.categories)}</c:cat><c:val>${numRef("Sheet1!\$${colRef(i)}\$2:\$${colRef(i)}\$${n + 1}", s.values, fmt)}</c:val></c:ser>")
                }
                sb.append("<c:axId val=\"111\"/><c:axId val=\"222\"/></c:areaChart>")
                if (target != null) sb.append("<c:lineChart><c:grouping val=\"standard\"/><c:varyColors val=\"0\"/>${targetSeries()}<c:marker val=\"1\"/><c:axId val=\"111\"/><c:axId val=\"222\"/></c:lineChart>")
            }
            ChartType.DONUT -> {
                val s = d.series.first()
                sb.append("<c:doughnutChart><c:varyColors val=\"1\"/>")
                sb.append("<c:ser><c:idx val=\"0\"/><c:order val=\"0\"/><c:tx>${strRef("Sheet1!\$B\$1", listOf(s.name))}</c:tx>")
                sb.append("<c:spPr><a:ln w=\"19050\">${solid("FFFFFF")}</a:ln></c:spPr>")
                for (i in 0 until n) {
                    val c = when {
                        hl >= 0 && i == hl -> hlColor
                        hl >= 0 -> hex(Colors.SHADES[(i % (Colors.SHADES.size - 1)) + 1])
                        else -> hex(Colors.SHADES[i % Colors.SHADES.size])
                    }
                    sb.append("<c:dPt><c:idx val=\"$i\"/><c:bubble3D val=\"0\"/><c:spPr>${solid(c)}<a:ln w=\"19050\">${solid("FFFFFF")}</a:ln></c:spPr></c:dPt>")
                }
                sb.append("<c:dLbls><c:spPr><a:noFill/><a:ln><a:noFill/></a:ln></c:spPr>${txPr(1100, "121212", spec.font(font))}<c:showLegendKey val=\"0\"/><c:showVal val=\"0\"/><c:showCatName val=\"1\"/><c:showSerName val=\"0\"/><c:showPercent val=\"1\"/><c:showBubbleSize val=\"0\"/><c:separator>\n</c:separator><c:showLeaderLines val=\"1\"/></c:dLbls>")
                sb.append("<c:cat>${strRef(catRef, d.categories)}</c:cat><c:val>${numRef("Sheet1!\$B\$2:\$B\$${n + 1}", s.values, fmt)}</c:val></c:ser>")
                sb.append("<c:firstSliceAng val=\"0\"/><c:holeSize val=\"62\"/></c:doughnutChart>")
            }
        }

        if (spec.type != ChartType.DONUT) {
            val hbar = spec.type == ChartType.HBAR
            val ax = d.axis
            sb.append("<c:catAx><c:axId val=\"111\"/><c:scaling><c:orientation val=\"${if (hbar) "maxMin" else "minMax"}\"/></c:scaling><c:delete val=\"0\"/><c:axPos val=\"${if (hbar) "l" else "b"}\"/>")
            sb.append("<c:numFmt formatCode=\"General\" sourceLinked=\"0\"/><c:majorTickMark val=\"none\"/><c:minorTickMark val=\"none\"/><c:tickLblPos val=\"low\"/>")
            sb.append("<c:spPr><a:ln w=\"12700\">${solid("121212")}</a:ln></c:spPr>${txPr(1200, "6E6E75", spec.font(font))}")
            sb.append("<c:crossAx val=\"222\"/><c:crosses val=\"autoZero\"/><c:auto val=\"1\"/><c:lblAlgn val=\"ctr\"/><c:lblOffset val=\"100\"/><c:noMultiLvlLbl val=\"0\"/></c:catAx>")
            sb.append("<c:valAx><c:axId val=\"222\"/><c:scaling><c:orientation val=\"minMax\"/>")
            if (ax != null && (spec.axisMode == AxisMode.CUSTOM || ax.max != 0.0)) sb.append("<c:max val=\"${Xml.num(ax.max)}\"/>")
            if (ax != null) sb.append("<c:min val=\"${Xml.num(ax.min)}\"/>")
            sb.append("</c:scaling><c:delete val=\"0\"/><c:axPos val=\"${if (hbar) "b" else "l"}\"/>")
            sb.append("<c:majorGridlines><c:spPr><a:ln w=\"6350\">${solid("ECECE9")}</a:ln></c:spPr></c:majorGridlines>")
            sb.append("<c:numFmt formatCode=\"${Xml.esc(if ((ax?.decimals ?: 0) == 0) "#,##0" else "#,##0." + "0".repeat(ax!!.decimals))}\" sourceLinked=\"0\"/>")
            sb.append("<c:majorTickMark val=\"none\"/><c:minorTickMark val=\"none\"/><c:tickLblPos val=\"nextTo\"/><c:spPr><a:ln><a:noFill/></a:ln></c:spPr>${txPr(1100, "6E6E75", spec.font(font))}")
            sb.append("<c:crossAx val=\"111\"/><c:crosses val=\"${if (hbar) "max" else "autoZero"}\"/><c:crossBetween val=\"between\"/>")
            if (ax != null) sb.append("<c:majorUnit val=\"${Xml.num(ax.step)}\"/>")
            sb.append("</c:valAx>")
        }
        sb.append("<c:spPr><a:noFill/><a:ln><a:noFill/></a:ln></c:spPr></c:plotArea>")
        val legend = (d.series.size > 1 && spec.type != ChartType.DONUT) || target != null
        if (legend) sb.append("<c:legend><c:legendPos val=\"t\"/><c:overlay val=\"0\"/>${txPr(1200, "4A4A50", spec.font(font))}</c:legend>")
        sb.append("<c:plotVisOnly val=\"1\"/><c:dispBlanksAs val=\"gap\"/></c:chart>")
        sb.append("<c:spPr><a:noFill/><a:ln><a:noFill/></a:ln></c:spPr>")
        sb.append(txPr(1200, "6E6E75", spec.font(font)))
        sb.append("<c:externalData r:id=\"rId1\"><c:autoUpdate val=\"0\"/></c:externalData></c:chartSpace>")
        return sb.toString()
    }

    private fun ChartSpec.font(f: String) = f

    /** Short facts for the speaker notes. */
    fun speakerNotes(d: ChartData, caption: String): String {
        val s = d.first ?: return caption
        val idx = s.values.indices.filter { s.values[it] != null }
        if (idx.isEmpty()) return caption
        val mx = idx.maxBy { s.values[it]!! }
        val mn = idx.minBy { s.values[it]!! }
        val avg = idx.sumOf { s.values[it]!! } / idx.size
        return buildString {
            if (caption.isNotBlank()) append(caption).append('\n')
            append("· ${I18n.tr("가장 큼", "Highest", "最大")}: ${d.fullCategories[mx]} ${d.withUnit(s.values[mx]!!)}\n")
            append("· ${I18n.tr("가장 작음", "Lowest", "最小")}: ${d.fullCategories[mn]} ${d.withUnit(s.values[mn]!!)}\n")
            append("· ${I18n.tr("평균", "Average", "平均")}: ${Format.withUnit(avg, d.scale.label, d.unit, d.decimals.coerceAtLeast(if (avg < 100) 1 else 0))}\n")
            append("· ${I18n.tr("항목 수", "Items", "項目数")}: ${idx.size}")
        }
    }
}
