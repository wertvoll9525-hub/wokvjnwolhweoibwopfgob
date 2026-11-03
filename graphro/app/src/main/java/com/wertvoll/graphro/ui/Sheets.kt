package com.wertvoll.graphro.ui

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
                Text("정직성 검사", style = T.title)
                Spacer(Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                    Stat(check.fixCount, "고칠 것", if (check.fixCount > 0) G.Signal else G.Ink)
                    Stat(check.noteCount, "참고", G.Ink)
                    Stat(check.passed.size, "통과", G.Pass)
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
                                Segmented(listOf("지금", "고친 뒤"), showFixed, { showFixed = it }, background = G.Paper)
                            }
                            if (fix != null && issue.fixLabel != null) {
                                Pill(issue.fixLabel!!, { vm.updateSpec(f = fix); vm.toast = "고쳤어요" }, Modifier.weight(1f), kind = PillKind.Signal, height = 42.dp, textStyle = T.smallStrong)
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
                    Text("통과 ${check.passed.size} · " + check.passed.joinToString(", "), style = T.small)
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
                vm.toast = if (ok) "저장했어요" else "저장하지 못했어요"
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
                    Text("내보내기", style = T.title)
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
                                it.asImageBitmap(), "내보낼 이미지 미리보기",
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
                            if (e.data.unitLabel.isNotEmpty()) Text("단위: ${e.data.unitLabel}", style = T.meta.copy(fontSize = T.meta.fontSize * 0.8f))
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
                        OptionRow("해석을 제목으로", "“3월 매출, 전월보다 46% 늘었어요”처럼", opt.headline, { opt = opt.copy(headline = it) })
                        OptionRow("세로형 4:5", "카톡·인스타에 꽉 차게. 끄면 16:9", opt.vertical, { opt = opt.copy(vertical = it) })
                        OptionRow("출처 표기", if (e.spec.source.isNullOrBlank()) "라벨 탭에서 출처를 적을 수 있어요" else "아래에 작게", opt.showSource, { opt = opt.copy(showSource = it) }, divider = false)
                    }
                    ExportFormat.PPT -> {
                        OptionRow("해석 문구", "차트 아래 한 줄", opt.caption, { opt = opt.copy(caption = it) })
                        OptionRow("발표자 메모", "핵심 숫자를 메모 칸에 자동으로", opt.notes, { opt = opt.copy(notes = it) })
                        OptionRow("원본 표 슬라이드", "다음 장에 표를 붙여요", opt.tableSlide, { opt = opt.copy(tableSlide = it) }, divider = false)
                    }
                    ExportFormat.SHEET -> {
                        OptionRow("고친 곳 시트", "앱이 바꾼 칸과 이유를 함께", opt.fixesSheet, { opt = opt.copy(fixesSheet = it) })
                        OptionRow("원문 시트", "붙여넣은 내용을 그대로", opt.rawSheet, { opt = opt.copy(rawSheet = it) }, divider = false)
                    }
                }
                if (format == ExportFormat.PPT) Text("차트는 파워포인트에서 데이터와 색을 바로 고칠 수 있는 차트로 들어가요. 서체는 맑은 고딕이에요.", style = T.small.copy(color = G.Sub), modifier = Modifier.padding(vertical = 8.dp))
            }
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill("저장", { savers[format]?.launch(Exporter.fileName(e.spec, format)) }, kind = PillKind.Outline, enabled = !working)
                Pill(
                    when (format) { ExportFormat.IMAGE -> "카톡 등으로 공유"; ExportFormat.PPT -> "PPT 공유"; ExportFormat.SHEET -> "엑셀 공유" },
                    {
                        working = true
                        scope.launch {
                            val file = withContext(Dispatchers.IO) { runCatching { bytes()?.let { Exporter.writeToCache(ctx, Exporter.fileName(e.spec, format), it) } }.getOrNull() }
                            working = false
                            if (file == null) { vm.toast = "파일을 만들지 못했어요"; return@launch }
                            try { ctx.startActivity(Exporter.shareIntent(ctx, file, format)) } catch (_: ActivityNotFoundException) { vm.toast = "공유할 앱이 없어요" }
                        }
                    },
                    Modifier.weight(1f), kind = PillKind.Ink, enabled = !working,
                )
            }
        }
    }
}
