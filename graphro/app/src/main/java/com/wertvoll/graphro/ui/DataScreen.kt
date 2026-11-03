package com.wertvoll.graphro.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.graphro.core.model.Column as DataColumn
import com.graphro.core.model.ColumnType
import com.graphro.core.model.DataTable
import com.graphro.core.model.Value
import com.graphro.core.text.Format
import com.wertvoll.graphro.AppViewModel

private const val MAX_ROWS = 200

private fun shown(c: DataColumn, r: Int): String = when (val v = c.cells[r].value) {
    is Value.Num -> Format.plain(v.v / c.scale.factor)
    is Value.Time -> v.t.fullLabel()
    is Value.Text -> v.s
    Value.Empty -> c.cells[r].raw
}

private fun typeLabel(c: DataColumn): String = when (c.type) {
    ColumnType.TIME -> "기간"
    ColumnType.NUMBER -> if (c.unitLabel.isNotEmpty()) "숫자 · ${c.unitLabel}" else "숫자"
    ColumnType.TEXT -> "항목"
}

@Composable
fun DataScreen(vm: AppViewModel) {
    val s = vm.importSession
    if (s == null) { Box(Modifier.fillMaxSize().background(G.Paper)); return }
    val t = s.table
    var view by remember { mutableIntStateOf(0) }
    var sel by remember(t) { mutableIntStateOf(0) }
    var editing by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var renaming by remember { mutableStateOf<Int?>(null) }
    val fixes = remember(t) { t.fixes() }
    val selFix = fixes.getOrNull(sel)
    val numCols = t.columns.count { it.type == ColumnType.NUMBER }

    Column(Modifier.fillMaxSize().background(G.Paper).statusBarsPadding().navigationBarsPadding()) {
        TopBar(onBack = { vm.cancelImport() }, close = s.editingProjectId == null) {
            IconAction(Ic.Swap, "행과 열 바꾸기", { vm.transpose(); vm.toast = "행과 열을 바꿨어요" })
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Column(Modifier.padding(horizontal = 24.dp)) {
                Meta(if (s.editingProjectId != null) "DATA · ${s.origin.ifBlank { "표" }}" else "STEP 1 / 2 · DATA")
                Spacer(Modifier.height(8.dp))
                Text("표를 이렇게\n읽었어요", style = T.title)
            }
            if (s.candidates.size > 1) {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(start = 24.dp, end = 24.dp, top = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    s.candidates.forEachIndexed { i, c ->
                        Chip("표 ${i + 1} · ${c.label}".take(24), i == s.selected, { vm.selectCandidate(i) })
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("${t.rowCount} × ${t.columns.size}", style = T.mono.copy(fontSize = T.meta.fontSize * 1.1f, color = G.Body))
                    if (fixes.isNotEmpty()) Text("● 고침 ${fixes.size}", style = T.mono.copy(fontSize = T.meta.fontSize * 1.1f, color = G.SignalText))
                    if (t.excludedRows.isNotEmpty()) Text("— 제외 ${t.excludedRows.size}", style = T.mono.copy(fontSize = T.meta.fontSize * 1.1f, color = G.Body))
                }
                Segmented(listOf("표", "원문"), view, { view = it })
            }
            Spacer(Modifier.height(14.dp))
            if (view == 0) {
                DataGrid(t, selFix?.let { it.row to it.column }, onCell = { r, c ->
                    val fi = fixes.indexOfFirst { it.row == r && it.column == c }
                    if (fi >= 0) sel = fi else editing = r to c
                }, onHeader = { renaming = it }, onInclude = { vm.includeRow(it) })
                if (t.rowCount > MAX_ROWS) Text("처음 ${MAX_ROWS}행만 보여요. 차트에는 모두 들어가요.", style = T.small.copy(color = G.Sub), modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
                Text("칸을 누르면 고칠 수 있고, 열 이름을 누르면 이름을 바꿀 수 있어요.", style = T.small.copy(color = G.Sub), modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp))
            } else {
                RawView(s.rawText, s.source.header, s.source.rows)
            }
            if (t.report.isNotEmpty() || t.title != null || t.notes.isNotEmpty()) {
                Column(Modifier.padding(horizontal = 24.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Meta("WHAT THE APP DID")
                    t.title?.let { Text("제목: $it", style = T.small) }
                    t.report.distinct().forEach { Text("· $it", style = T.small) }
                    t.notes.forEach { Text("· 메모: $it", style = T.small) }
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        if (selFix != null) {
            val col = t.columns[selFix.column]
            val revertible = selFix.message.startsWith("단위가 빠져") || selFix.message.startsWith("빈 칸이라")
            Column(
                Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(G.Ink)
                    .padding(start = 20.dp, end = 16.dp, top = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row {
                    Meta("${shownLabel(t, selFix.row)} · ${col.name}", color = G.Fog2, modifier = Modifier.weight(1f))
                    Meta("${sel + 1} / ${fixes.size}", color = G.Fog2)
                }
                Text("“${col.cells[selFix.row].raw}” → ${shown(col, selFix.row).ifEmpty { "빈 칸" }}", style = T.heading.copy(color = G.Paper), maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(selFix.message, style = T.small.copy(color = G.Fog2))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (revertible) DarkPill("원래대로", outline = true) { vm.keepRaw(selFix.row, selFix.column) }
                    DarkPill("직접 고치기", outline = true) { editing = selFix.row to selFix.column }
                    if (fixes.size > 1) DarkPill("확인, 다음", outline = false) { sel = (sel + 1) % fixes.size }
                }
            }
        }
        Pill(
            if (s.editingProjectId != null) "이 표로 다시 그리기" else "차트 만들기",
            { vm.confirmData() },
            Modifier.fillMaxWidth().padding(16.dp),
            kind = PillKind.Signal, trailingArrow = true, enabled = numCols > 0,
        )
    }

    editing?.let { (r, c) ->
        TextInputDialog(
            "${shownLabel(t, r)} · ${t.columns.getOrNull(c)?.name ?: ""}",
            t.columns.getOrNull(c)?.cells?.getOrNull(r)?.raw ?: "",
            { editing = null },
            { vm.editCell(r, c, it); editing = null },
            placeholder = "값을 입력하세요",
        )
    }
    renaming?.let { c ->
        TextInputDialog("열 이름", s.source.header.getOrElse(c) { t.columns[c].name }, { renaming = null }, { vm.renameColumn(c, it); renaming = null })
    }
}

private fun shownLabel(t: DataTable, r: Int): String {
    val label = t.columns.firstOrNull { it.type != ColumnType.NUMBER }?.let { c -> shown(c, r).ifEmpty { c.cells[r].raw } }
    return label?.takeIf { it.isNotBlank() } ?: "${r + 1}행"
}

@Composable
private fun DarkPill(text: String, outline: Boolean, onClick: () -> Unit) {
    var m = Modifier.height(40.dp).clip(RoundedCornerShape(50))
    m = if (outline) m.border(1.dp, G.Body, RoundedCornerShape(50)) else m.background(G.Paper)
    Box(m.clickable(onClick = onClick).padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
        Text(text, style = T.smallStrong.copy(color = if (outline) G.Paper else G.Ink))
    }
}

@Composable
private fun DataGrid(t: DataTable, selected: Pair<Int, Int>?, onCell: (Int, Int) -> Unit, onHeader: (Int) -> Unit, onInclude: (Int) -> Unit) {
    val many = t.columns.size > 3
    val widths: List<Dp> = t.columns.mapIndexed { i, c -> if (i == 0 && c.type != ColumnType.NUMBER) 84.dp else if (many) 112.dp else 0.dp }
    val h = rememberScrollState()
    val outer = if (many) Modifier.fillMaxWidth().horizontalScroll(h) else Modifier.fillMaxWidth()
    Column(outer.padding(horizontal = 24.dp).then(if (many) Modifier.width(androidx.compose.foundation.layout.IntrinsicSize.Max) else Modifier)) {
        Row(Modifier.border(0.dp, G.Paper).padding(bottom = 8.dp), verticalAlignment = Alignment.Bottom) {
            t.columns.forEachIndexed { ci, c ->
                val right = c.type == ColumnType.NUMBER
                Column(
                    cellModifier(widths[ci], many, this).clip(RoundedCornerShape(6.dp)).clickable { onHeader(ci) }.padding(vertical = 2.dp),
                    horizontalAlignment = if (right) Alignment.End else Alignment.Start,
                ) {
                    Text(c.name, style = T.smallStrong.copy(fontSize = T.bodyStrong.fontSize * 0.93f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Meta(typeLabel(c))
                }
            }
        }
        Hairline(color = G.Ink)
        for (r in 0 until minOf(t.rowCount, MAX_ROWS)) {
            val excluded = r in t.excludedRows
            Row(Modifier.height(44.dp), verticalAlignment = Alignment.CenterVertically) {
                t.columns.forEachIndexed { ci, c ->
                    val cell = c.cells[r]
                    val fixed = cell.fix != null
                    val on = selected == (r to ci)
                    val right = c.type == ColumnType.NUMBER
                    val text = shown(c, r)
                    Box(cellModifier(widths[ci], many, this), contentAlignment = if (right) Alignment.CenterEnd else Alignment.CenterStart) {
                        Row(
                            Modifier.clip(RoundedCornerShape(50)).background(if (on) G.Signal else androidx.compose.ui.graphics.Color.Transparent)
                                .clickable { onCell(r, ci) }.padding(horizontal = if (fixed) 8.dp else 4.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (fixed) {
                                Box(Modifier.size(5.dp).clip(CircleShape).background(if (on) G.Paper else G.Signal))
                                Spacer(Modifier.width(6.dp))
                            }
                            val style = if (right) T.mono else T.bodyStrong.copy(fontWeight = if (ci == 0) FontWeight.SemiBold else FontWeight.Normal)
                            Text(
                                text.ifEmpty { " " },
                                style = style.copy(
                                    color = when { excluded -> G.Faint; on -> G.Paper; fixed -> G.SignalText; else -> G.Ink },
                                    textDecoration = if (excluded) TextDecoration.LineThrough else null,
                                    fontWeight = if (fixed) FontWeight.SemiBold else style.fontWeight,
                                ),
                                maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = if (right) TextAlign.End else TextAlign.Start,
                            )
                        }
                    }
                }
            }
            if (excluded) {
                Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(t.excludedRows[r] ?: "", style = T.small.copy(color = G.Sub), modifier = Modifier.weight(1f, fill = false))
                    Spacer(Modifier.width(8.dp))
                    Text("포함하기", style = T.smallStrong.copy(textDecoration = TextDecoration.Underline), modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { onInclude(r) }.padding(6.dp))
                }
            }
            Hairline()
        }
    }
}

private fun cellModifier(w: Dp, many: Boolean, scope: androidx.compose.foundation.layout.RowScope): Modifier =
    if (many || w > 0.dp) Modifier.width(if (w > 0.dp) w else 112.dp).padding(end = 8.dp) else with(scope) { Modifier.weight(1f).padding(end = 8.dp) }

@Composable
private fun RawView(raw: String?, header: List<String>, rows: List<List<String>>) {
    val text = raw?.take(20_000) ?: (listOf(header) + rows.take(200)).joinToString("\n") { it.joinToString("   ") }
    Box(
        Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(G.Mist)
            .horizontalScroll(rememberScrollState()).padding(18.dp),
    ) {
        Text(text, style = T.mono.copy(fontSize = T.small.fontSize, lineHeight = T.small.lineHeight * 1.2f, color = G.Body))
    }
}
