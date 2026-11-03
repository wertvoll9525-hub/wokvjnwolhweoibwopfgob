package com.wertvoll.graphro.ui

import com.graphro.core.text.I18n

import android.content.ActivityNotFoundException
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.graphro.core.chart.ChartData
import com.graphro.core.check.CheckResult
import com.graphro.core.check.Severity
import com.graphro.core.text.Caption
import com.wertvoll.graphro.AppViewModel
import com.wertvoll.graphro.chart.ChartView
import com.wertvoll.graphro.data.ExportFormat
import com.wertvoll.graphro.data.ExportOptions
import com.wertvoll.graphro.data.Exporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun CheckSheet(vm: AppViewModel, visible: Boolean, check: CheckResult, onDismiss: () -> Unit) {
    val maxH = (LocalConfiguration.current.screenHeightDp * 0.88f).dp
    Sheet(visible, onDismiss) {
        val e = vm.editor ?: return@Sheet
        Column(Modifier.heightIn(max = maxH).verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 20.dp)) {
                Meta("HONESTY CHECK")
                Spacer(Modifier.height(8.dp))
                Text(I18n.tr("정직성 검사", "Honesty check", "誠実さチェック"), style = T.title)
                Spacer(Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                    Stat(check.fixCount, I18n.tr("고칠 것", "To fix", "要修正"), if (check.fixCount > 0) G.Signal else G.Ink)
                    Stat(check.noteCount, I18n.tr("참고", "Notes", "注意"), G.Ink)
                    Stat(check.passed.size, I18n.tr("통과", "Passed", "合格"), G.Pass)
                }
            }
            Spacer(Modifier.height(20.dp))
            check.issues.forEach { issue ->
                if (issue.severity == Severity.FIX) {
                    var showFixed by remember(issue.code) { mutableIntStateOf(0) }
                    Column(
                        Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(G.Mist).padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Meta("FIX · ${issue.code.uppercase().replace('_', '-')}", color = G.SignalText)
                        Text(issue.title, style = T.heading)
                        Text(issue.detail, style = T.small)
                        val fix = issue.fix
                        if (fix != null && issue.exaggeration != null) {
                            val previewSpec = if (showFixed == 1) fix(e.spec) else e.spec
                            ChartView(ChartData.build(e.table, previewSpec), previewSpec, Modifier.fillMaxWidth().height(150.dp).background(G.Paper, RoundedCornerShape(16.dp)).padding(12.dp), scale = 0.85f)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (fix != null && issue.exaggeration != null) {
                                Segmented(listOf(I18n.tr("지금", "Now", "現在"), I18n.tr("고친 뒤", "Fixed", "修正後")), showFixed, { showFixed = it }, background = G.Paper)
                            }
                            if (fix != null && issue.fixLabel != null) {
                                Pill(issue.fixLabel!!, { vm.updateSpec(f = fix); vm.toast = I18n.tr("고쳤어요", "Fixed", "修正しました") }, Modifier.weight(1f), kind = PillKind.Signal, height = 42.dp, textStyle = T.smallStrong)
                            }
                        }
                    }
                }
            }
            check.issues.filter { it.severity == Severity.NOTE }.forEach { issue ->
                Column(Modifier.padding(horizontal = 24.dp).fillMaxWidth()) {
                    Hairline()
                    Column(Modifier.padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Meta("NOTE")
                        Text(issue.title, style = T.heading.copy(fontSize = T.bodyStrong.fontSize))
                        Text(issue.detail, style = T.small)
                        val fix = issue.fix
                        if (fix != null && issue.fixLabel != null) {
                            Pill(issue.fixLabel!!, { vm.updateSpec(f = fix) }, kind = PillKind.Outline, height = 38.dp, textStyle = T.smallStrong)
                        }
                    }
                }
            }
            Column(Modifier.padding(horizontal = 24.dp)) {
                Hairline()
                Row(Modifier.padding(vertical = 16.dp), verticalAlignment = Alignment.Top) {
                    Box(Modifier.padding(top = 6.dp).size(7.dp).clip(CircleShape).background(G.Pass))
                    Spacer(Modifier.width(10.dp))
                    Text(I18n.tr("통과 ${check.passed.size} · ", "Passed ${check.passed.size} · ", "合格 ${check.passed.size} · ") + check.passed.joinToString(", "), style = T.small)
                }
            }
        }
    }
}

@Composable
private fun Stat(n: Int, label: String, color: androidx.compose.ui.graphics.Color) {
    Column {
        Text(n.toString(), style = T.monoBig.copy(color = color))
        Text(label, style = T.small.copy(fontSize = T.meta.fontSize * 1.1f))
    }
}

@Composable
fun ExportSheet(vm: AppViewModel, visible: Boolean, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var fmt by remember { mutableIntStateOf(0) }
    var opt by remember { mutableStateOf(ExportOptions()) }
    var working by remember { mutableStateOf(false) }
    val format = ExportFormat.entries[fmt]
    val maxH = (LocalConfiguration.current.screenHeightDp * 0.9f).dp

    fun bytes(): ByteArray? {
        val e = vm.editor ?: return null
        return Exporter.build(ctx, format, e.table, e.spec, opt, e.project.rawText)
    }

    val savers = ExportFormat.entries.associateWith { f ->
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(f.mime)) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            working = true
            scope.launch {
                val ok = withContext(Dispatchers.IO) {
                    runCatching {
                        val e = vm.editor ?: return@runCatching false
                        val b = Exporter.build(ctx, f, e.table, e.spec, opt, e.project.rawText)
                        ctx.contentResolver.openOutputStream(uri)?.use { it.write(b) } != null
                    }.getOrDefault(false)
                }
                working = false
                vm.toast = if (ok) I18n.tr("저장했어요", "Saved", "保存しました") else I18n.tr("저장하지 못했어요", "Couldn't save", "保存できませんでした")
            }
        }
    }

    Sheet(visible, onDismiss) {
        val e = vm.editor ?: return@Sheet
        Column(Modifier.heightIn(max = maxH).verticalScroll(rememberScrollState())) {
            Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 20.dp), verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Meta("EXPORT")
                    Spacer(Modifier.height(8.dp))
                    Text(I18n.tr("내보내기", "Export", "書き出し"), style = T.title)
                }
                Segmented(ExportFormat.entries.map { it.label }, fmt, { fmt = it })
            }
            Box(
                Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp).fillMaxWidth().height(300.dp).clip(RoundedCornerShape(24.dp)).background(G.Mist),
                contentAlignment = Alignment.Center,
            ) {
                when (format) {
                    ExportFormat.IMAGE -> {
                        var bmp by remember { mutableStateOf<Bitmap?>(null) }
                        LaunchedEffect(e.spec, e.table, opt.vertical, opt.headline, opt.showSource, visible) {
                            if (!visible) return@LaunchedEffect
                            bmp = withContext(Dispatchers.Default) {
                                val d = ChartData.build(e.table, e.spec)
                                Exporter.renderImage(ctx, d, e.spec, if (e.spec.showCaption) Caption.make(d, e.spec) else "", opt, widthPx = 720)
                            }
                        }
                        bmp?.let {
                            Image(
                                it.asImageBitmap(), I18n.tr("내보낼 이미지 미리보기", "Preview of the image to export", "書き出す画像のプレビュー"),
                                Modifier.fillMaxHeight().padding(vertical = 18.dp).aspectRatio(it.width.toFloat() / it.height).shadow(12.dp, RoundedCornerShape(4.dp)).clip(RoundedCornerShape(4.dp)),
                                contentScale = ContentScale.Fit,
                            )
                        }
                    }
                    ExportFormat.PPT -> {
                        Column(
                            Modifier.padding(18.dp).fillMaxWidth().aspectRatio(16f / 9f).shadow(12.dp, RoundedCornerShape(4.dp)).background(G.Paper).padding(horizontal = 16.dp, vertical = 12.dp),
                        ) {
                            Text(e.spec.title, style = T.smallStrong)
                            if (e.data.unitLabel.isNotEmpty()) Text(I18n.tr("단위: ${e.data.unitLabel}", "Unit: ${e.data.unitLabel}", "単位: ${e.data.unitLabel}"), style = T.meta.copy(fontSize = T.meta.fontSize * 0.8f))
                            ChartView(e.data, e.spec, Modifier.fillMaxWidth().weight(1f).padding(top = 4.dp), scale = 0.6f)
                            if (opt.caption && e.spec.showCaption) Text(Caption.make(e.data, e.spec), style = T.small.copy(fontSize = T.meta.fontSize * 0.85f), maxLines = 1)
                        }
                    }
                    ExportFormat.SHEET -> {
                        val rows = remember(e.table) { Exporter.tableRows(e.table, true).take(8) }
                        Column(Modifier.padding(18.dp).fillMaxWidth().shadow(12.dp, RoundedCornerShape(4.dp)).background(G.Paper)) {
                            rows.forEachIndexed { r, row ->
                                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
                                    row.take(4).forEachIndexed { c, v ->
                                        Text(v, style = if (r == 0) T.smallStrong.copy(fontSize = T.meta.fontSize) else T.mono.copy(fontSize = T.meta.fontSize), maxLines = 1, modifier = Modifier.weight(1f))
                                    }
                                }
                                Hairline(color = if (r == 0) G.Ink else G.Line)
                            }
                        }
                    }
                }
            }
            Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 8.dp)) {
                when (format) {
                    ExportFormat.IMAGE -> {
                        OptionRow(I18n.tr("해석을 제목으로", "Use the finding as the title", "読み取り結果をタイトルに"), I18n.tr("“3월 매출, 전월보다 46% 늘었어요”처럼", "e.g. “March sales up 46% from February”", "例:「3月の売上、前月より46%増」"), opt.headline, { opt = opt.copy(headline = it) })
                        OptionRow(I18n.tr("세로형 4:5", "Portrait 4:5", "縦長 4:5"), I18n.tr("카톡·인스타에 꽉 차게. 끄면 16:9", "Fills chat and Instagram screens. Off = 16:9", "LINEやインスタで画面いっぱいに。オフで16:9"), opt.vertical, { opt = opt.copy(vertical = it) })
                        OptionRow(I18n.tr("출처 표기", "Show source", "出典を表示"), if (e.spec.source.isNullOrBlank()) I18n.tr("라벨 탭에서 출처를 적을 수 있어요", "Add a source in the Labels tab", "ラベルタブで出典を入力できます") else I18n.tr("아래에 작게", "Small, at the bottom", "下に小さく"), opt.showSource, { opt = opt.copy(showSource = it) }, divider = false)
                    }
                    ExportFormat.PPT -> {
                        OptionRow(I18n.tr("해석 문구", "Caption", "説明文"), I18n.tr("차트 아래 한 줄", "One line under the chart", "グラフの下に1行"), opt.caption, { opt = opt.copy(caption = it) })
                        OptionRow(I18n.tr("발표자 메모", "Speaker notes", "発表者メモ"), I18n.tr("핵심 숫자를 메모 칸에 자동으로", "Key numbers go into the notes automatically", "主要な数値をメモ欄に自動で記入"), opt.notes, { opt = opt.copy(notes = it) })
                        OptionRow(I18n.tr("원본 표 슬라이드", "Source table slide", "元の表のスライド"), I18n.tr("다음 장에 표를 붙여요", "Adds the table on the next slide", "次のスライドに表を追加"), opt.tableSlide, { opt = opt.copy(tableSlide = it) }, divider = false)
                    }
                    ExportFormat.SHEET -> {
                        OptionRow(I18n.tr("고친 곳 시트", "Fixes sheet", "修正箇所シート"), I18n.tr("앱이 바꾼 칸과 이유를 함께", "Cells the app changed, with reasons", "アプリが変更したセルと理由"), opt.fixesSheet, { opt = opt.copy(fixesSheet = it) })
                        OptionRow(I18n.tr("원문 시트", "Original text sheet", "原文シート"), I18n.tr("붙여넣은 내용을 그대로", "The pasted text as is", "貼り付けた内容をそのまま"), opt.rawSheet, { opt = opt.copy(rawSheet = it) }, divider = false)
                    }
                }
                if (format == ExportFormat.PPT) Text(I18n.tr("차트는 파워포인트에서 데이터와 색을 바로 고칠 수 있는 차트로 들어가요. 서체는 맑은 고딕이에요.", "The chart goes in as a real PowerPoint chart you can edit. Font: Calibri.", "グラフはPowerPointでデータや色を直接編集できるグラフとして入ります。フォントは游ゴシックです。"), style = T.small.copy(color = G.Sub), modifier = Modifier.padding(vertical = 8.dp))
            }
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill(I18n.tr("저장", "Save", "保存"), { savers[format]?.launch(Exporter.fileName(e.spec, format)) }, kind = PillKind.Outline, enabled = !working)
                Pill(
                    when (format) { ExportFormat.IMAGE -> I18n.tr("카톡 등으로 공유", "Share", "LINEなどで共有"); ExportFormat.PPT -> I18n.tr("PPT 공유", "Share PPT", "PPTを共有"); ExportFormat.SHEET -> I18n.tr("엑셀 공유", "Share Excel", "Excelを共有") },
                    {
                        working = true
                        scope.launch {
                            val file = withContext(Dispatchers.IO) { runCatching { bytes()?.let { Exporter.writeToCache(ctx, Exporter.fileName(e.spec, format), it) } }.getOrNull() }
                            working = false
                            if (file == null) { vm.toast = I18n.tr("파일을 만들지 못했어요", "Couldn't create the file", "ファイルを作成できませんでした"); return@launch }
                            try { ctx.startActivity(Exporter.shareIntent(ctx, file, format)) } catch (_: ActivityNotFoundException) { vm.toast = I18n.tr("공유할 앱이 없어요", "No app to share with", "共有できるアプリがありません") }
                        }
                    },
                    Modifier.weight(1f), kind = PillKind.Ink, enabled = !working,
                )
            }
        }
    }
}
