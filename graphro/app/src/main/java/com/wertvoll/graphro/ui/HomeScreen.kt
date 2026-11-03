package com.wertvoll.graphro.ui

import com.graphro.core.text.I18n

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.graphro.core.chart.ChartData
import com.graphro.core.chart.ChartType
import com.wertvoll.graphro.AppViewModel
import com.wertvoll.graphro.ProjectItem
import com.wertvoll.graphro.SAMPLE
import com.wertvoll.graphro.Screen
import com.wertvoll.graphro.chart.ChartView
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

val OPEN_TYPES = arrayOf(
    "text/*", "application/csv", "application/json", "application/octet-stream",
    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "application/vnd.ms-excel",
)

fun relativeDate(t: Long): String {
    val now = Calendar.getInstance()
    val c = Calendar.getInstance().apply { timeInMillis = t }
    val sameDay = now.get(Calendar.YEAR) == c.get(Calendar.YEAR) && now.get(Calendar.DAY_OF_YEAR) == c.get(Calendar.DAY_OF_YEAR)
    if (sameDay) return I18n.tr("오늘", "Today", "今日")
    now.add(Calendar.DAY_OF_YEAR, -1)
    if (now.get(Calendar.YEAR) == c.get(Calendar.YEAR) && now.get(Calendar.DAY_OF_YEAR) == c.get(Calendar.DAY_OF_YEAR)) return I18n.tr("어제", "Yesterday", "昨日")
    return SimpleDateFormat("MM.dd", Locale.KOREA).format(Date(t))
}

@Composable
fun HomeScreen(vm: AppViewModel) {
    val ctx = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) vm.importUri(ctx, uri) }
    val today = remember { SimpleDateFormat("EEE · MM.dd", Locale.ENGLISH).format(Date()).uppercase() }
    val recent = vm.projects.take(6)

    LazyColumn(
        Modifier.fillMaxSize().background(G.Paper).statusBarsPadding(),
        contentPadding = PaddingValues(bottom = 40.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth().height(56.dp).padding(start = 24.dp, end = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Logo()
                Spacer(Modifier.width(8.dp))
                Text(I18n.tr("그래프로", "Graphro", "グラフロ"), style = T.heading.copy(letterSpacing = androidx.compose.ui.unit.TextUnit.Unspecified))
                Spacer(Modifier.weight(1f))
                IconAction(Ic.Menu, I18n.tr("설정", "Settings", "設定"), { vm.go(Screen.SETTINGS) })
            }
        }
        item {
            Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 24.dp)) {
                Meta(today)
                Spacer(Modifier.height(10.dp))
                Text(I18n.tr("무엇을\n그려볼까요", "What shall\nwe chart?", "何を\nグラフにしますか"), style = T.display)
            }
        }
        item {
            StartCard(vm)
        }
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill(I18n.tr("붙여넣기", "Paste", "貼り付け"), { vm.go(Screen.PASTE) }, Modifier.weight(1f), kind = PillKind.Outline, height = 48.dp, textStyle = T.smallStrong)
                Pill(I18n.tr("파일 열기", "Open file", "ファイルを開く"), { picker.launch(OPEN_TYPES) }, Modifier.weight(1f), kind = PillKind.Outline, height = 48.dp, textStyle = T.smallStrong)
                Pill(I18n.tr("예제", "Sample", "サンプル"), { vm.sample() }, Modifier.weight(1f), kind = PillKind.Outline, height = 48.dp, textStyle = T.smallStrong)
            }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Meta("RECENT")
                Spacer(Modifier.weight(1f))
                if (vm.projects.size > recent.size) {
                    Text(I18n.tr("모두 보기", "See all", "すべて見る"), style = T.small, modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { vm.go(Screen.PROJECTS) }.padding(8.dp))
                }
            }
        }
        if (recent.isEmpty()) {
            item {
                Column(Modifier.padding(horizontal = 24.dp, vertical = 12.dp)) {
                    Hairline()
                    Spacer(Modifier.height(16.dp))
                    Text(I18n.tr("아직 만든 차트가 없어요. 엑셀에서 표를 복사하거나 파일을 열어 시작해 보세요.", "No charts yet. Copy a table from a spreadsheet or open a file to start.", "まだグラフがありません。表をコピーするかファイルを開いて始めましょう。"), style = T.small)
                    Spacer(Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SvgIcon(Ic.Lock, color = G.Sub, size = 14.dp, stroke = 1.8f)
                        Spacer(Modifier.width(6.dp))
                        Text(I18n.tr("모든 데이터는 이 폰 안에서만 처리돼요", "All data stays on this phone", "データはすべてこの端末内で処理されます"), style = T.small.copy(color = G.Sub))
                    }
                }
            }
        }
        itemsIndexed(recent, key = { _, it -> it.project.id }) { i, item ->
            ProjectRow(vm, item, i + 1, Modifier.padding(horizontal = 24.dp))
        }
    }
}

@Composable
fun Logo(size: Int = 18) {
    Canvas(Modifier.size(size.dp)) {
        val s = this.size.width / 18f
        fun bar(x: Float, top: Float, c: androidx.compose.ui.graphics.Color) =
            drawRoundRect(c, Offset(x * s, top * s), androidx.compose.ui.geometry.Size(4.5f * s, (18f - top) * s), androidx.compose.ui.geometry.CornerRadius(1f * s))
        bar(0.5f, 9f, G.Ink); bar(6.75f, 4f, G.Ink); bar(13f, 0.5f, G.Signal)
    }
}

@Composable
private fun StartCard(vm: AppViewModel) {
    val ctx = LocalContext.current
    val hasClip = vm.clipboardHasText
    Column(
        Modifier.padding(start = 16.dp, end = 16.dp, top = 28.dp).fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(G.Mist)
            .clickable {
                if (hasClip) {
                    val text = vm.readClipboard(ctx)
                    if (text.isNullOrBlank()) vm.toast = I18n.tr("복사한 내용이 비어 있어요", "The clipboard is empty", "コピーした内容が空です") else vm.importText(text, I18n.tr("복사한 내용", "Copied content", "コピーした内容"))
                } else vm.sample()
            }
            .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(Modifier.fillMaxWidth()) {
            Meta(if (hasClip) "CLIPBOARD" else "TRY A SAMPLE", color = G.SignalText)
            Spacer(Modifier.weight(1f))
            Meta(if (hasClip) "TEXT" else "6 × 3")
        }
        SampleSpark(Modifier.fillMaxWidth().height(88.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(if (hasClip) I18n.tr("방금 복사한 내용", "What you just copied", "コピーした内容") else I18n.tr("예제로 먼저 해보기", "Try a sample first", "まずはサンプルで試す"), style = T.heading)
                Spacer(Modifier.height(3.dp))
                Text(if (hasClip) I18n.tr("표·숫자를 읽어서 차트로 만들어요", "Turns tables and numbers into a chart", "表や数値を読み取ってグラフにします") else I18n.tr("섞인 단위와 합계 행을 어떻게 읽는지 보세요", "See how mixed units and totals are handled", "混在した単位や合計行の扱いを見てみましょう"), style = T.small, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(12.dp))
            Row(
                Modifier.height(44.dp).clip(RoundedCornerShape(50)).background(G.Ink).padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(I18n.tr("만들기", "Create", "作成"), style = T.button.copy(color = G.Paper))
                Spacer(Modifier.width(6.dp))
                SvgIcon(Ic.Arrow, color = G.Paper, size = 16.dp, stroke = 2f)
            }
        }
    }
}

@Composable
private fun SampleSpark(modifier: Modifier) {
    Canvas(modifier) {
        val vals = listOf(320f, 280f, 410f, 395f, 452f, 438f)
        val minV = 250f; val maxV = 470f
        val w = size.width; val h = size.height
        val pts = vals.mapIndexed { i, v -> Offset(w * (0.02f + 0.96f * i / (vals.size - 1)), h * (1f - (v - minV) / (maxV - minV)) * 0.9f + h * 0.05f) }
        drawLine(G.Fog2, Offset(0f, h - 1f), Offset(w, h - 1f), 1.dp.toPx())
        val p = Path().apply { pts.forEachIndexed { i, o -> if (i == 0) moveTo(o.x, o.y) else lineTo(o.x, o.y) } }
        drawPath(p, G.Ink, style = Stroke(1.75.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawCircle(G.Signal, 4.5.dp.toPx(), pts[2])
    }
}

@Composable
fun MiniSpark(data: ChartData, modifier: Modifier) {
    Canvas(modifier) {
        val s = data.first ?: return@Canvas
        val vals = s.values
        val present = vals.filterNotNull()
        if (present.isEmpty()) return@Canvas
        val mn = minOf(present.min(), 0.0); val mx = present.max().let { if (it == mn) mn + 1 else it }
        val w = size.width; val h = size.height
        fun y(v: Double) = (h - 2f) - ((v - mn) / (mx - mn)).toFloat() * (h - 4f)
        val n = vals.size
        if (data.categories.isNotEmpty() && (data.isTime)) {
            val p = Path(); var started = false
            vals.forEachIndexed { i, v -> if (v != null) { val x = if (n == 1) w / 2 else w * i / (n - 1); if (!started) { p.moveTo(x, y(v)); started = true } else p.lineTo(x, y(v)) } }
            drawPath(p, G.Graphite, style = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            val hl = data.highlight
            if (hl >= 0 && vals[hl] != null) drawCircle(G.Signal, 2.5.dp.toPx(), Offset(if (n == 1) w / 2 else w * hl / (n - 1), y(vals[hl]!!)))
        } else {
            val bw = w / (n * 1.6f)
            vals.forEachIndexed { i, v ->
                if (v == null) return@forEachIndexed
                val x = (i + 0.5f) * w / n - bw / 2
                drawRect(if (i == data.highlight) G.Signal else G.Fog, Offset(x, y(v)), androidx.compose.ui.geometry.Size(bw, (h - 2f) - y(v)))
            }
        }
    }
}

@Composable
fun ProjectRow(vm: AppViewModel, item: ProjectItem, index: Int, modifier: Modifier = Modifier) {
    var menu by remember { mutableStateOf(false) }
    var rename by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    Column(modifier) {
        Hairline()
        Row(
            Modifier.fillMaxWidth().height(64.dp).clickable { vm.open(item) },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(index.toString().padStart(2, '0'), style = T.mono.copy(fontSize = androidx.compose.ui.unit.TextUnit.Unspecified, color = G.Sub), modifier = Modifier.width(32.dp))
            Column(Modifier.weight(1f)) {
                Text(item.project.spec.title, style = T.bodyStrong, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(2.dp))
                Meta("${item.project.spec.type.code} · ${relativeDate(item.project.updatedAt)}")
            }
            Spacer(Modifier.width(8.dp))
            MiniSpark(item.data, Modifier.size(width = 64.dp, height = 28.dp))
            Box {
                IconAction(Ic.More, I18n.tr("더 보기", "More", "その他"), { menu = true }, color = G.Sub)
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text(I18n.tr("이름 바꾸기", "Rename", "名前を変更"), style = T.bodyStrong) }, onClick = { menu = false; rename = true })
                    DropdownMenuItem(text = { Text(I18n.tr("사본 만들기", "Duplicate", "複製"), style = T.bodyStrong) }, onClick = { menu = false; vm.duplicate(item) })
                    DropdownMenuItem(text = { Text(I18n.tr("삭제", "Delete", "削除"), style = T.bodyStrong.copy(color = G.SignalText)) }, onClick = { menu = false; confirmDelete = true })
                }
            }
        }
    }
    if (rename) TextInputDialog(I18n.tr("이름 바꾸기", "Rename", "名前を変更"), item.project.spec.title, { rename = false }, { vm.rename(item, it.trim().ifEmpty { item.project.spec.title }); rename = false })
    if (confirmDelete) ConfirmDialog(I18n.tr("차트를 삭제할까요?", "Delete this chart?", "グラフを削除しますか？"), I18n.tr("‘${item.project.spec.title}’을(를) 지우면 되돌릴 수 없어요.", "Deleting “${item.project.spec.title}” can't be undone.", "「${item.project.spec.title}」を削除すると元に戻せません。"), I18n.tr("삭제", "Delete", "削除"), { confirmDelete = false }, { confirmDelete = false; vm.delete(item) }, danger = true)
}

@Composable
fun ProjectsScreen(vm: AppViewModel) {
    Column(Modifier.fillMaxSize().background(G.Paper).statusBarsPadding()) {
        TopBar(onBack = { vm.goBack() })
        Column(Modifier.padding(horizontal = 24.dp)) {
            Meta("ALL CHARTS · ${vm.projects.size}")
            Spacer(Modifier.height(8.dp))
            Text(I18n.tr("모든 차트", "All charts", "すべてのグラフ"), style = T.title)
            Spacer(Modifier.height(12.dp))
        }
        LazyColumn(contentPadding = PaddingValues(bottom = 40.dp)) {
            itemsIndexed(vm.projects, key = { _, it -> it.project.id }) { i, item ->
                ProjectRow(vm, item, i + 1, Modifier.padding(horizontal = 24.dp))
            }
        }
    }
}

@Composable
fun TopBar(onBack: () -> Unit, close: Boolean = false, actions: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().height(56.dp).padding(start = 6.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        IconAction(if (close) Ic.Close else Ic.Back, if (close) I18n.tr("닫기", "Close", "閉じる") else I18n.tr("뒤로", "Back", "戻る"), onBack)
        Spacer(Modifier.weight(1f))
        actions()
    }
}

@Composable
fun PasteScreen(vm: AppViewModel) {
    val ctx = LocalContext.current
    Column(Modifier.fillMaxSize().background(G.Paper).statusBarsPadding().navigationBarsPadding().imePadding()) {
        TopBar(onBack = { vm.goBack() }, close = true)
        Column(Modifier.padding(horizontal = 24.dp)) {
            Meta("PASTE")
            Spacer(Modifier.height(8.dp))
            Text(I18n.tr("표나 숫자를\n붙여넣으세요", "Paste a table\nor numbers", "表や数値を\n貼り付けてください"), style = T.title)
        }
        Box(
            Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp).fillMaxWidth().weight(1f)
                .clip(RoundedCornerShape(24.dp)).background(G.Mist).padding(18.dp),
        ) {
            if (vm.pasteDraft.isEmpty()) {
                Text(
                    I18n.tr("엑셀·한글·웹에서 복사한 표, CSV, 또는\n\n1월 매출 320만, 방문 1,240명\n2월 매출 280만원, 방문 1,105명\n\n처럼 줄마다 적어도 괜찮아요.", "A table copied from a spreadsheet or the web, CSV, or lines like\n\nJan revenue $320K, visitors 1,240\nFeb revenue $280,000, visitors 1,105\n\nall work.", "表計算ソフトやWebからコピーした表、CSV、または\n\n1月 売上 320万、来店 1,240人\n2月 売上 280万円、来店 1,105人\n\nのように1行ずつ書いてもOKです。"),
                    style = T.mono.copy(color = G.Faint, fontSize = androidx.compose.ui.unit.TextUnit.Unspecified),
                )
            }
            BasicTextField(
                value = vm.pasteDraft,
                onValueChange = { vm.pasteDraft = it },
                textStyle = T.mono.copy(color = G.Ink),
                cursorBrush = SolidColor(G.Signal),
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            )
        }
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill(I18n.tr("클립보드에서", "From clipboard", "クリップボードから"), {
                val t = vm.readClipboard(ctx)
                if (t.isNullOrBlank()) vm.toast = I18n.tr("복사한 내용이 없어요", "Nothing has been copied", "コピーした内容がありません") else vm.pasteDraft = t
            }, Modifier.weight(1f), kind = PillKind.Outline, height = 44.dp, textStyle = T.smallStrong)
            Pill(I18n.tr("예제 넣기", "Insert sample", "サンプルを入れる"), { vm.pasteDraft = SAMPLE }, Modifier.weight(1f), kind = PillKind.Outline, height = 44.dp, textStyle = T.smallStrong)
            if (vm.pasteDraft.isNotEmpty()) Pill(I18n.tr("지우기", "Clear", "消去"), { vm.pasteDraft = "" }, kind = PillKind.Outline, height = 44.dp, textStyle = T.smallStrong)
        }
        Pill(
            I18n.tr("표로 읽기", "Read as table", "表として読む"), { vm.importText(vm.pasteDraft, I18n.tr("붙여넣기", "Paste", "貼り付け")) },
            Modifier.fillMaxWidth().padding(16.dp).widthIn(max = 640.dp),
            kind = PillKind.Signal, enabled = vm.pasteDraft.isNotBlank(), trailingArrow = true,
        )
    }
}
