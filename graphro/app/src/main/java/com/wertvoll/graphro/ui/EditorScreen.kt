package com.wertvoll.graphro.ui

import com.graphro.core.text.I18n

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.graphro.core.chart.AxisMode
import com.graphro.core.chart.ChartType
import com.graphro.core.chart.Colors
import com.graphro.core.chart.Fit
import com.graphro.core.chart.HighlightMode
import com.graphro.core.chart.Recommender
import com.graphro.core.chart.SortMode
import com.graphro.core.check.Honesty
import com.graphro.core.model.ColumnType
import com.graphro.core.text.Caption
import com.graphro.core.text.Format
import com.wertvoll.graphro.AppViewModel
import com.wertvoll.graphro.chart.ChartView

private val TABS = listOf(I18n.tr("종류", "Type", "種類"), I18n.tr("강조", "Highlight", "強調"), I18n.tr("축", "Axis", "軸"), I18n.tr("라벨", "Labels", "ラベル"), I18n.tr("값", "Values", "値"))

@Composable
fun EditorScreen(vm: AppViewModel) {
    val e = vm.editor ?: run { Box(Modifier.fillMaxSize().background(G.Paper)); return }
    val spec = e.spec
    val d = e.data
    val table = e.table
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var showCheck by remember { mutableStateOf(false) }
    var showExport by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var popover by remember { mutableStateOf(false) }
    var noteEdit by remember { mutableStateOf(false) }
    val check = remember(spec, table) { Honesty.check(table, spec, d) }
    val caption = remember(spec, table) { Caption.make(d, spec) }
    val recs = remember(table, spec.categoryCol, spec.seriesCols) { Recommender.recommend(table, spec.categoryCol, spec.seriesCols) }

    Box(Modifier.fillMaxSize().background(G.Paper)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().height(56.dp).padding(start = 6.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                IconAction(Ic.Back, I18n.tr("뒤로", "Back", "戻る"), { vm.goBack() })
                Spacer(Modifier.weight(1f))
                Text(
                    I18n.tr("표", "Table", "表"), style = T.smallStrong,
                    modifier = Modifier.clip(RoundedCornerShape(50)).clickable(role = Role.Button) { vm.reviewProjectData() }
                        .semantics { contentDescription = I18n.tr("표 확인하고 고치기", "Review and edit the table", "表を確認・修正") }.padding(horizontal = 12.dp, vertical = 10.dp),
                )
                IconAction(Ic.Undo, I18n.tr("되돌리기", "Undo", "元に戻す"), { vm.undo() }, enabled = e.undo.isNotEmpty())
                IconAction(Ic.Redo, I18n.tr("다시 하기", "Redo", "やり直す"), { vm.redo() }, enabled = e.redo.isNotEmpty())
                Spacer(Modifier.width(6.dp))
                Pill(I18n.tr("내보내기", "Export", "書き出し"), { showExport = true }, height = 40.dp, textStyle = T.smallStrong.copy(fontSize = T.button.fontSize * 0.95f))
            }

            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp)) {
                    Meta("${spec.type.code} · ${d.size} ${if (d.isTime) "POINTS" else "ITEMS"}" + (if (d.unitLabel.isNotEmpty()) " · ${d.unitLabel}" else ""))
                    Spacer(Modifier.height(8.dp))
                    Row(
                        Modifier.clip(RoundedCornerShape(8.dp)).clickable { renaming = true }.semantics { contentDescription = I18n.tr("제목: ${spec.title}. 눌러서 고치기", "Title: ${spec.title}. Tap to edit", "タイトル: ${spec.title}。タップして編集") },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(spec.title, style = T.title, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                        Spacer(Modifier.width(6.dp))
                        SvgIcon(Ic.Pencil, color = G.Faint, size = 16.dp)
                    }
                }
                Box(Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp)) {
                    ChartView(
                        d, spec,
                        Modifier.fillMaxWidth().aspectRatio(if (spec.type == ChartType.HBAR) (1.35f).coerceAtMost(350f / (d.size * 34f + 40f).coerceAtLeast(160f)) else 1.5f),
                        description = "${spec.title}. $caption",
                    ) { idx ->
                        if (idx >= 0) {
                            vm.updateSpec { it.copy(highlightMode = HighlightMode.MANUAL, highlightRow = d.rows[idx]) }
                            popover = true
                            tab = 1
                        } else popover = false
                    }
                }
                AnimatedVisibility(popover && d.highlight >= 0, enter = fadeIn(), exit = fadeOut()) {
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.Center) {
                        Row(
                            Modifier.shadow(8.dp, RoundedCornerShape(50)).clip(RoundedCornerShape(50)).background(G.Ink).padding(4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(I18n.tr("강조 해제", "Clear highlight", "強調を解除"), style = T.smallStrong.copy(color = G.Paper), modifier = Modifier.clip(RoundedCornerShape(50)).clickable {
                                vm.updateSpec { it.copy(highlightMode = HighlightMode.NONE) }; popover = false
                            }.padding(horizontal = 14.dp, vertical = 9.dp))
                            Text(I18n.tr("메모 달기", "Add note", "メモを付ける"), style = T.smallStrong, modifier = Modifier.clip(RoundedCornerShape(50)).background(G.Paper).clickable {
                                noteEdit = true; popover = false
                            }.padding(horizontal = 14.dp, vertical = 9.dp))
                        }
                    }
                }
                if (spec.showCaption && caption.isNotBlank()) {
                    Text(caption, style = T.body, modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 8.dp))
                }
                Row(Modifier.padding(start = 24.dp, top = 14.dp, bottom = 16.dp)) {
                    Row(
                        Modifier.height(36.dp).clip(RoundedCornerShape(50)).border(1.dp, G.Line, RoundedCornerShape(50))
                            .clickable { showCheck = true }.padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(7.dp).clip(CircleShape).background(if (check.fixCount > 0) G.Signal else G.Pass))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            when {
                                check.fixCount > 0 -> I18n.tr("검사 · 고칠 것 ${check.fixCount}", "Check · ${check.fixCount} to fix", "チェック · 要修正 ${check.fixCount}")
                                check.noteCount > 0 -> I18n.tr("검사 통과 · 참고 ${check.noteCount}", "Check passed · ${check.noteCount} note(s)", "チェック合格 · 注意 ${check.noteCount}")
                                else -> I18n.tr("검사 통과", "Check passed", "チェック合格")
                            },
                            style = T.smallStrong,
                        )
                        Spacer(Modifier.width(6.dp))
                        SvgIcon(Ic.Chevron, color = G.Sub, size = 13.dp, stroke = 2f)
                    }
                }
            }

            // Panel + dock
            Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(
                    Modifier.fillMaxWidth().heightIn(max = 300.dp).clip(RoundedCornerShape(24.dp)).background(G.Mist)
                        .verticalScroll(rememberScrollState()).padding(16.dp),
                ) {
                    when (tab) {
                        0 -> TypePanel(vm, recs)
                        1 -> HighlightPanel(vm, onNote = { noteEdit = true })
                        2 -> AxisPanel(vm)
                        3 -> LabelPanel(vm)
                        else -> SeriesPanel(vm)
                    }
                }
                Row(
                    Modifier.align(Alignment.CenterHorizontally).clip(RoundedCornerShape(50)).background(G.Ink).padding(4.dp),
                ) {
                    TABS.forEachIndexed { i, label ->
                        val on = i == tab
                        Box(
                            Modifier.height(40.dp).clip(RoundedCornerShape(50)).background(if (on) G.Paper else Color.Transparent)
                                .clickable(role = Role.Tab) { tab = i }.padding(horizontal = 16.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(label, style = T.smallStrong.copy(color = if (on) G.Ink else G.DockText))
                        }
                    }
                }
            }
        }
        CheckSheet(vm, showCheck, check) { showCheck = false }
        ExportSheet(vm, showExport) { showExport = false }
    }

    if (renaming) TextInputDialog(I18n.tr("차트 제목", "Chart title", "グラフのタイトル"), spec.title, { renaming = false }, { t -> vm.updateSpec { it.copy(title = t.trim().ifEmpty { it.title }) }; renaming = false })
    if (noteEdit) TextInputDialog(I18n.tr("강조한 곳에 메모", "Note on the highlight", "強調箇所へのメモ"), spec.note ?: "", { noteEdit = false }, { t -> vm.updateSpec { it.copy(note = t.trim().ifEmpty { null }) }; noteEdit = false }, placeholder = I18n.tr("예: 신메뉴 출시", "e.g. New menu launched", "例: 新メニュー発売"))
}

@Composable
private fun PanelLabel(text: String) {
    Meta(text, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
}

@Composable
private fun TypePanel(vm: AppViewModel, recs: List<com.graphro.core.chart.Recommendation>) {
    val spec = vm.editor!!.spec
    val order = ChartType.entries.sortedBy { t -> recs.indexOfFirst { it.type == t }.let { if (it < 0) 99 else it } }
    val available = order.filter { t -> recs.any { it.type == t } || t == spec.type }
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        available.forEach { t ->
            val rec = recs.firstOrNull { it.type == t }
            val on = t == spec.type
            Column(
                Modifier.width(62.dp).height(64.dp).clip(RoundedCornerShape(16.dp))
                    .background(if (on) G.Paper else Color.Transparent)
                    .clickable(role = Role.RadioButton) { vm.updateSpec { s -> s.copy(type = t, showValues = if (t == ChartType.HBAR) true else s.showValues) } }
                    .alpha(if (rec?.fit == Fit.NOT_FIT && !on) 0.45f else 1f)
                    .semantics { contentDescription = t.label },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                TypeGlyph(t, Modifier.size(width = 28.dp, height = 20.dp))
                Spacer(Modifier.height(5.dp))
                Text(t.short, style = T.smallStrong.copy(fontSize = T.meta.fontSize * 1.0f))
            }
        }
    }
    val rec = recs.firstOrNull { it.type == spec.type }
    Column(Modifier.padding(start = 4.dp, end = 4.dp, top = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(spec.type.label, style = T.bodyStrong)
            if (rec != null && rec.fit != Fit.OK) {
                Spacer(Modifier.width(8.dp))
                Meta(rec.fit.tag, color = if (rec.fit == Fit.BEST) G.SignalText else G.Sub)
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(rec?.reason ?: I18n.tr("이 데이터에는 잘 맞지 않을 수 있어요.", "May not suit this data.", "このデータには合わないかもしれません。"), style = T.small)
    }
}

@Composable
fun TypeGlyph(t: ChartType, modifier: Modifier, color: Color = G.Ink) {
    Canvas(modifier) {
        val w = size.width; val h = size.height
        val sx = w / 28f; val sy = h / 20f
        when (t) {
            ChartType.LINE -> {
                val p = Path().apply { moveTo(2 * sx, 14 * sy); lineTo(9 * sx, 16 * sy); lineTo(14 * sx, 6 * sy); lineTo(20 * sx, 8 * sy); lineTo(26 * sx, 3 * sy) }
                drawPath(p, color, style = Stroke(1.75.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            ChartType.BAR -> listOf(3f to 9f, 11.5f to 3f, 20f to 6f).forEach { (x, y) -> drawRoundRect(color, Offset(x * sx, y * sy), Size(5 * sx, (20 - y) * sy), CornerRadius(sx)) }
            ChartType.STACKED -> listOf(3f, 11.5f, 20f).forEachIndexed { i, x ->
                val top = listOf(8f, 3f, 6f)[i]; val mid = top + (20 - top) * 0.45f
                drawRoundRect(color, Offset(x * sx, mid * sy), Size(5 * sx, (20 - mid) * sy), CornerRadius(sx))
                drawRoundRect(color.copy(alpha = 0.35f), Offset(x * sx, top * sy), Size(5 * sx, (mid - top - 1) * sy), CornerRadius(sx))
            }
            ChartType.HBAR -> listOf(1f to 17f, 8f to 24f, 15f to 11f).forEach { (y, len) -> drawRoundRect(color, Offset(2 * sx, y * sy), Size(len * sx, 4.5f * sy), CornerRadius(sx)) }
            ChartType.AREA -> {
                val p = Path().apply { moveTo(2 * sx, 14 * sy); lineTo(9 * sx, 16 * sy); lineTo(14 * sx, 6 * sy); lineTo(20 * sx, 8 * sy); lineTo(26 * sx, 3 * sy); lineTo(26 * sx, 20 * sy); lineTo(2 * sx, 20 * sy); close() }
                drawPath(p, color)
            }
            ChartType.DONUT -> drawCircle(color, 7.5f * sy, Offset(14 * sx, 10 * sy), style = Stroke(3.5f * sy))
        }
    }
}

@Composable
private fun HighlightPanel(vm: AppViewModel, onNote: () -> Unit) {
    val e = vm.editor!!
    val spec = e.spec
    var targetEdit by remember { mutableStateOf(false) }
    val modes = listOf(HighlightMode.MAX to I18n.tr("최댓값", "Maximum", "最大値"), HighlightMode.CHANGE to I18n.tr("큰 변화", "Biggest change", "大きな変化"), HighlightMode.MANUAL to I18n.tr("직접", "Manual", "手動"), HighlightMode.NONE to I18n.tr("없음", "None", "なし"))
    val visible = if (e.data.isTime) modes else modes.filter { it.first != HighlightMode.CHANGE }
    PanelLabel("AUTO HIGHLIGHT")
    Segmented(visible.map { it.second }, visible.indexOfFirst { it.first == spec.highlightMode }.coerceAtLeast(0), { i ->
        val m = visible[i].first
        vm.updateSpec { s ->
            if (m == HighlightMode.MANUAL) s.copy(highlightMode = m, highlightRow = if (e.data.highlight >= 0) e.data.rows[e.data.highlight] else e.data.rows.firstOrNull() ?: -1)
            else s.copy(highlightMode = m)
        }
    }, Modifier.fillMaxWidth(), dark = false, fill = true, itemHeight = 40.dp, background = G.Line2)
    if (spec.highlightMode == HighlightMode.MANUAL) {
        Text(I18n.tr("차트에서 막대나 점을 눌러 고를 수 있어요.", "Tap a bar or point on the chart to pick it.", "グラフの棒や点をタップして選べます。"), style = T.small.copy(color = G.Sub), modifier = Modifier.padding(start = 4.dp, top = 8.dp))
    }
    Spacer(Modifier.height(8.dp))
    Hairline(color = G.Line2)
    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).clickable { targetEdit = true }) {
            Text(I18n.tr("목표선", "Target line", "目標線"), style = T.smallStrong)
            Text(spec.target?.let { "${Format.plain(it)} ${e.data.unitLabel}".trim() + I18n.tr(" · 눌러서 바꾸기", " · tap to change", " · タップで変更") } ?: I18n.tr("기준이 되는 값을 선으로", "Draw a reference value as a line", "基準となる値を線で表示"), style = T.small.copy(color = G.Sub))
        }
        Toggle(spec.target != null, { on ->
            if (on) targetEdit = true else vm.updateSpec { it.copy(target = null) }
        }, I18n.tr("목표선", "Target line", "目標線"))
    }
    Hairline(color = G.Line2)
    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(I18n.tr("평균선", "Average line", "平均線"), style = T.smallStrong, modifier = Modifier.weight(1f))
        Toggle(spec.showAverage, { on -> vm.updateSpec { it.copy(showAverage = on) } }, I18n.tr("평균선", "Average line", "平均線"))
    }
    Hairline(color = G.Line2)
    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(I18n.tr("강조 색", "Highlight color", "強調色"), style = T.smallStrong, modifier = Modifier.weight(1f))
        listOf(Colors.SIGNAL to I18n.tr("주홍", "Vermilion", "朱"), Colors.INDIGO to I18n.tr("남색", "Indigo", "藍"), Colors.INK to I18n.tr("먹색", "Ink", "墨")).forEach { (c, name) ->
            val on = spec.highlightColor == c
            Box(
                Modifier.size(40.dp).clip(CircleShape).clickable(role = Role.RadioButton) { vm.updateSpec { it.copy(highlightColor = c) } }
                    .semantics { contentDescription = "$name${if (on) I18n.tr(", 선택됨", ", selected", "、選択中") else ""}" },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier.size(22.dp).then(if (on) Modifier.border(2.dp, G.Ink, CircleShape).padding(3.dp) else Modifier)
                        .clip(CircleShape).background(Color(c)),
                )
            }
        }
    }
    Hairline(color = G.Line2)
    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable(onClick = onNote).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(I18n.tr("메모", "Note", "メモ"), style = T.smallStrong)
            Text(spec.note ?: I18n.tr("강조한 곳 위에 짧은 설명을 달아요", "Add a short note above the highlight", "強調した所に短い説明を付けます"), style = T.small.copy(color = G.Sub), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        SvgIcon(Ic.Chevron, color = G.Sub, size = 14.dp)
    }
    if (targetEdit) {
        TextInputDialog(
            I18n.tr("목표선 값", "Target value", "目標値") + " (${e.data.unitLabel.ifEmpty { I18n.tr("표의 단위", "table units", "表の単位") }})",
            spec.target?.let { Format.plain(it) } ?: "",
            { targetEdit = false },
            { t ->
                val v = t.replace(",", "").trim().toDoubleOrNull()
                if (v == null) vm.toast = I18n.tr("숫자를 입력해 주세요", "Please enter a number", "数値を入力してください") else vm.updateSpec { it.copy(target = v) }
                targetEdit = false
            },
            keyboard = KeyboardType.Decimal, placeholder = I18n.tr("예: 400", "e.g. 400", "例: 400"),
        )
    }
}

@Composable
private fun AxisPanel(vm: AppViewModel) {
    val e = vm.editor!!
    val spec = e.spec
    var custom by remember { mutableStateOf(false) }
    if (spec.type == ChartType.DONUT) {
        Text(I18n.tr("도넛 그래프에는 축이 없어요.", "Donut charts have no axis.", "ドーナツグラフには軸がありません。"), style = T.small, modifier = Modifier.padding(4.dp))
        return
    }
    PanelLabel("VALUE AXIS")
    val modes = listOf(AxisMode.AUTO to I18n.tr("자동", "Auto", "自動"), AxisMode.ZERO to I18n.tr("0부터", "From 0", "0から"), AxisMode.CUSTOM to I18n.tr("직접", "Manual", "手動"))
    Segmented(modes.map { it.second }, modes.indexOfFirst { it.first == spec.axisMode }, { i ->
        if (modes[i].first == AxisMode.CUSTOM) custom = true else vm.updateSpec { it.copy(axisMode = modes[i].first) }
    }, Modifier.fillMaxWidth(), dark = false, fill = true, itemHeight = 40.dp, background = G.Line2)
    e.data.axis?.let { ax ->
        Text(I18n.tr("지금 범위: ${Format.plain(ax.min)} ~ ${Format.plain(ax.max)}", "Current range: ${Format.plain(ax.min)} – ${Format.plain(ax.max)}", "現在の範囲: ${Format.plain(ax.min)} 〜 ${Format.plain(ax.max)}"), style = T.small.copy(color = G.Sub), modifier = Modifier.padding(start = 4.dp, top = 8.dp))
    }
    val isTime = e.table.columns.getOrNull(spec.categoryCol)?.type == ColumnType.TIME
    if (!isTime) {
        Spacer(Modifier.height(14.dp))
        PanelLabel("ORDER")
        val sorts = listOf(SortMode.NONE to I18n.tr("원래 순서", "Original", "元の順"), SortMode.DESC to I18n.tr("큰 순", "Largest", "大きい順"), SortMode.ASC to I18n.tr("작은 순", "Smallest", "小さい順"))
        Segmented(sorts.map { it.second }, sorts.indexOfFirst { it.first == spec.sort }, { i -> vm.updateSpec { it.copy(sort = sorts[i].first) } },
            Modifier.fillMaxWidth(), dark = false, fill = true, itemHeight = 40.dp, background = G.Line2)
    }
    if (custom) {
        val ax = e.data.axis
        TextInputDialog(
            I18n.tr("축 범위 (최솟값, 최댓값)", "Axis range (min, max)", "軸の範囲（最小, 最大）"),
            "${Format.plain(spec.axisMin ?: ax?.min ?: 0.0).replace(",", "")}, ${Format.plain(spec.axisMax ?: ax?.max ?: 100.0).replace(",", "")}",
            { custom = false },
            { t ->
                val parts = Regex("-?\\d+(?:\\.\\d+)?").findAll(t.replace(",", " ").replace("~", " ")).mapNotNull { it.value.toDoubleOrNull() }.toList().take(2)
                if (parts.size == 2 && parts[1] > parts[0]) vm.updateSpec { it.copy(axisMode = AxisMode.CUSTOM, axisMin = parts[0], axisMax = parts[1]) }
                else vm.toast = I18n.tr("예: 0, 500 처럼 두 숫자를 적어 주세요", "Enter two numbers, like 0, 500", "0, 500 のように2つの数値を入力してください")
                custom = false
            },
            placeholder = "0, 500",
        )
    }
}

@Composable
private fun LabelPanel(vm: AppViewModel) {
    val spec = vm.editor!!.spec
    var captionEdit by remember { mutableStateOf(false) }
    var sourceEdit by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(I18n.tr("모든 값 표시", "Show all values", "すべての値を表示"), style = T.smallStrong, modifier = Modifier.weight(1f))
        Toggle(spec.showValues, { on -> vm.updateSpec { it.copy(showValues = on) } }, I18n.tr("모든 값 표시", "Show all values", "すべての値を表示"))
    }
    Hairline(color = G.Line2)
    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(I18n.tr("해석 문구", "Caption", "説明文"), style = T.smallStrong, modifier = Modifier.weight(1f))
        Toggle(spec.showCaption, { on -> vm.updateSpec { it.copy(showCaption = on) } }, I18n.tr("해석 문구", "Caption", "説明文"))
    }
    Hairline(color = G.Line2)
    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable { captionEdit = true }.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(I18n.tr("문구 직접 쓰기", "Write your own caption", "説明文を自分で書く"), style = T.smallStrong)
            Text(if (spec.customCaption != null) I18n.tr("직접 쓴 문구 사용 중", "Using your own caption", "自分で書いた説明文を使用中") else I18n.tr("자동 문구 사용 중", "Using the automatic caption", "自動の説明文を使用中"), style = T.small.copy(color = G.Sub))
        }
        if (spec.customCaption != null) Text(I18n.tr("자동으로", "Back to auto", "自動に戻す"), style = T.smallStrong.copy(color = G.SignalText), modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { vm.updateSpec { it.copy(customCaption = null) } }.padding(8.dp))
        SvgIcon(Ic.Chevron, color = G.Sub, size = 14.dp)
    }
    Hairline(color = G.Line2)
    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable { sourceEdit = true }.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(I18n.tr("출처", "Source", "出典"), style = T.smallStrong)
            Text(spec.source ?: I18n.tr("예: 통계청, 매장 판매 기록", "e.g. Statistics office, store sales records", "例: 統計局、店舗の売上記録"), style = T.small.copy(color = G.Sub), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        SvgIcon(Ic.Chevron, color = G.Sub, size = 14.dp)
    }
    if (captionEdit) {
        val e = vm.editor!!
        TextInputDialog(I18n.tr("해석 문구", "Caption", "説明文"), spec.customCaption ?: Caption.make(e.data, spec.copy(customCaption = null)), { captionEdit = false },
            { t -> vm.updateSpec { it.copy(customCaption = t.trim().ifEmpty { null }) }; captionEdit = false }, singleLine = false)
    }
    if (sourceEdit) {
        TextInputDialog(I18n.tr("출처", "Source", "出典"), spec.source ?: "", { sourceEdit = false }, { t -> vm.updateSpec { it.copy(source = t.trim().ifEmpty { null }) }; sourceEdit = false }, placeholder = I18n.tr("예: 통계청", "e.g. Statistics Korea", "例: 総務省統計局"))
    }
}

@Composable
private fun SeriesPanel(vm: AppViewModel) {
    val e = vm.editor!!
    val spec = e.spec
    val t = e.table
    val nums = t.numberColumns
    PanelLabel("VALUES")
    if (nums.size <= 1) {
        Text(I18n.tr("이 표에는 숫자 열이 하나예요", "This table has one number column", "この表の数値列は1つです") + ": ${nums.firstOrNull()?.let { t.columns[it].displayName } ?: "-"}", style = T.small, modifier = Modifier.padding(horizontal = 4.dp))
    } else {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            nums.forEach { c ->
                val on = c in spec.seriesCols
                Chip(t.columns[c].displayName, on, {
                    vm.updateSpec { s ->
                        val next = if (on) s.seriesCols - c else (s.seriesCols + c).sortedBy { nums.indexOf(it) }
                        if (next.isEmpty()) s else s.copy(seriesCols = next, highlightMode = if (next.size > 1 && s.highlightMode != HighlightMode.NONE) HighlightMode.NONE else s.highlightMode)
                    }
                })
            }
        }
        Text(I18n.tr("단위가 다른 값을 함께 고르면 검사에서 알려 드려요.", "If you mix units, the check will tell you.", "単位の違う値を一緒に選ぶと、チェックでお知らせします。"), style = T.small.copy(color = G.Sub), modifier = Modifier.padding(start = 4.dp, top = 10.dp))
    }
    val cats = t.columns.indices.filter { t.columns[it].type != ColumnType.NUMBER }
    if (cats.size > 1) {
        Spacer(Modifier.height(14.dp))
        PanelLabel("CATEGORY")
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            cats.forEach { c -> Chip(t.columns[c].name, c == spec.categoryCol, { vm.updateSpec { it.copy(categoryCol = c) } }) }
        }
    }
}
