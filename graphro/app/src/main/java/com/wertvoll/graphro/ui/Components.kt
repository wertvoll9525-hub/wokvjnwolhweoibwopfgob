package com.wertvoll.graphro.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.material3.Text

/** Stroke icons drawn from SVG path data on a 24×24 grid. */
object Ic {
    const val Back = "M15 5l-7 7 7 7"
    const val Close = "M6 6l12 12M18 6L6 18"
    const val Undo = "M9 14L4 9l5-5M4 9h10.5a5.5 5.5 0 0 1 0 11H11"
    const val Redo = "M15 14l5-5-5-5M20 9H9.5a5.5 5.5 0 0 0 0 11H13"
    const val Menu = "M4 8h16M4 16h16"
    const val Arrow = "M5 12h14M13 6l6 6-6 6"
    const val Chevron = "M9 6l6 6-6 6"
    const val ChevronDown = "M6 9l6 6 6-6"
    const val Swap = "M7 4v14l-3-3M17 20V6l3 3"
    const val Plus = "M12 5v14M5 12h14"
    const val Pencil = "M4 20h4L19 9l-4-4L4 16z"
    const val Trash = "M5 7h14M10 7V4h4v3M7 7l1 13h8l1-13"
    const val More = "M5 12h.01M12 12h.01M19 12h.01"
    const val Check = "M5 12l5 5 9-10"
    const val Lock = "M6 11h12v9H6zM8.5 11V8a3.5 3.5 0 0 1 7 0v3"
}

@Composable
fun SvgIcon(d: String, modifier: Modifier = Modifier, color: Color = G.Ink, size: Dp = 20.dp, stroke: Float = 1.6f) {
    val path = remember(d) { PathParser().parsePathString(d).toPath() }
    Canvas(modifier.size(size)) {
        val s = this.size.minDimension / 24f
        scale(s, s, pivot = androidx.compose.ui.geometry.Offset.Zero) {
            drawPath(path, color, style = Stroke(width = stroke * 1.0f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

@Composable
fun IconAction(d: String, label: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, color: Color = G.Ink) {
    Box(
        modifier
            .size(44.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = label, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        SvgIcon(d, color = if (enabled) color else Color(0xFFBDBDC2), size = 20.dp)
    }
}

enum class PillKind { Ink, Signal, Outline, Light, Ghost }

@Composable
fun Pill(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: PillKind = PillKind.Ink,
    height: Dp = 54.dp,
    enabled: Boolean = true,
    trailingArrow: Boolean = false,
    textStyle: TextStyle = T.button,
) {
    val bg = when (kind) {
        PillKind.Ink -> G.Ink; PillKind.Signal -> G.Signal; PillKind.Outline -> G.Paper; PillKind.Light -> G.Paper; PillKind.Ghost -> Color.Transparent
    }
    val fg = when (kind) { PillKind.Ink, PillKind.Signal -> G.Paper; else -> G.Ink }
    var m = modifier.height(height).clip(RoundedCornerShape(50)).background(bg)
    if (kind == PillKind.Outline) m = m.border(1.dp, G.Fog, RoundedCornerShape(50))
    Row(
        m.alpha(if (enabled) 1f else 0.4f)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(text, style = textStyle.copy(color = fg, fontWeight = if (kind == PillKind.Signal) androidx.compose.ui.text.font.FontWeight.Bold else textStyle.fontWeight), maxLines = 1)
        if (trailingArrow) { Spacer(Modifier.width(6.dp)); SvgIcon(Ic.Arrow, color = fg, size = 16.dp, stroke = 2f) }
    }
}

/** Pill-shaped segmented control. [dark] = selected item in ink (otherwise white on mist). */
@Composable
fun Segmented(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    dark: Boolean = true,
    fill: Boolean = false,
    itemHeight: Dp = 34.dp,
    background: Color = G.Mist,
) {
    Row(
        modifier.clip(RoundedCornerShape(50)).background(background).padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        options.forEachIndexed { i, label ->
            val on = i == selected
            val bg = when { on && dark -> G.Ink; on -> G.Paper; else -> Color.Transparent }
            val fg = if (on && dark) G.Paper else G.Ink
            var m = Modifier.height(itemHeight).clip(RoundedCornerShape(50)).background(bg)
            if (on && !dark) m = m.shadow(1.dp, RoundedCornerShape(50))
            if (fill) m = Modifier.weight(1f).then(m)
            Box(
                m.clickable(role = Role.Tab) { onSelect(i) }
                    .semantics { stateDescription = if (on) "선택됨" else "" }
                    .padding(horizontal = 13.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, style = T.smallStrong.copy(color = fg), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
fun Toggle(checked: Boolean, onChange: (Boolean) -> Unit, label: String, modifier: Modifier = Modifier) {
    val x by animateDpAsState(if (checked) 20.dp else 0.dp, tween(160), label = "toggle")
    Box(
        modifier
            .size(width = 48.dp, height = 28.dp)
            .clip(RoundedCornerShape(50))
            .background(if (checked) G.Ink else G.Fog2)
            .clickable(role = Role.Switch) { onChange(!checked) }
            .semantics { contentDescription = label; stateDescription = if (checked) "켜짐" else "꺼짐" }
            .padding(3.dp),
    ) {
        Box(Modifier.offset(x = x).size(22.dp).clip(CircleShape).background(G.Paper))
    }
}

@Composable
fun OptionRow(title: String, sub: String?, checked: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier, divider: Boolean = true) {
    Column(modifier) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 54.dp).clickable { onChange(!checked) }.padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = T.smallStrong.copy(fontSize = 14.sp))
                if (sub != null) Text(sub, style = T.small.copy(color = G.Sub, fontSize = 12.sp))
            }
            Spacer(Modifier.width(12.dp))
            Toggle(checked, onChange, title)
        }
        if (divider) Box(Modifier.fillMaxWidth().height(1.dp).background(G.Line))
    }
}


@Composable
fun Meta(text: String, modifier: Modifier = Modifier, color: Color = G.Sub) {
    Text(text, modifier = modifier, style = T.meta.copy(color = color), maxLines = 1, overflow = TextOverflow.Ellipsis)
}

@Composable
fun Hairline(modifier: Modifier = Modifier, color: Color = G.Line) {
    Box(modifier.fillMaxWidth().height(1.dp).background(color))
}

/** Bottom sheet with scrim, drag handle and slide-in. */
@Composable
fun Sheet(visible: Boolean, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    if (visible) BackHandler(onBack = onDismiss)
    AnimatedVisibility(visible, enter = fadeIn(tween(180)), exit = fadeOut(tween(180))) {
        Box(Modifier.fillMaxSize().background(G.Scrim).clickable(remember { MutableInteractionSource() }, null, onClick = onDismiss))
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(
            visible,
            enter = slideInVertically(tween(260)) { it } + fadeIn(tween(120)),
            exit = slideOutVertically(tween(200)) { it } + fadeOut(tween(160)),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .widthIn(max = 640.dp)
                    .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                    .background(G.Paper)
                    .clickable(remember { MutableInteractionSource() }, null) {}
                    .navigationBarsPadding(),
            ) {
                Box(Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(width = 36.dp, height = 4.dp).clip(RoundedCornerShape(4.dp)).background(G.Fog))
                }
                content()
            }
        }
    }
}

@Composable
fun TextInputDialog(
    title: String,
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    placeholder: String = "",
    keyboard: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true,
    confirmLabel: String = "확인",
) {
    // Start with the old value selected so typing replaces it.
    var text by remember { mutableStateOf(androidx.compose.ui.text.input.TextFieldValue(initial, androidx.compose.ui.text.TextRange(0, initial.length))) }
    val focus = remember { FocusRequester() }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(G.Paper).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(title, style = T.heading)
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(G.Mist).padding(14.dp)) {
                if (text.text.isEmpty()) Text(placeholder, style = T.body.copy(color = G.Faint))
                BasicTextField(
                    value = text,
                    onValueChange = { text = it },
                    textStyle = T.body.copy(color = G.Ink),
                    singleLine = singleLine,
                    keyboardOptions = KeyboardOptions(keyboardType = keyboard),
                    cursorBrush = SolidColor(G.Signal),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill("취소", onDismiss, Modifier.weight(1f), kind = PillKind.Outline, height = 48.dp)
                Pill(confirmLabel, { onConfirm(text.text) }, Modifier.weight(1f), kind = PillKind.Ink, height = 48.dp)
            }
        }
    }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
}

@Composable
fun ConfirmDialog(title: String, body: String, confirm: String, onDismiss: () -> Unit, onConfirm: () -> Unit, danger: Boolean = false) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(G.Paper).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(title, style = T.heading)
            Text(body, style = T.body)
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill("취소", onDismiss, Modifier.weight(1f), kind = PillKind.Outline, height = 48.dp)
                Pill(confirm, onConfirm, Modifier.weight(1f), kind = if (danger) PillKind.Signal else PillKind.Ink, height = 48.dp)
            }
        }
    }
}

/** Dark pill message at the bottom of the screen. */
@Composable
fun BoxScope.Toast(message: String?, onDone: () -> Unit) {
    AnimatedVisibility(
        message != null,
        modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 96.dp, start = 24.dp, end = 24.dp),
        enter = fadeIn() + slideInVertically { it / 2 },
        exit = fadeOut(),
    ) {
        Box(Modifier.clip(RoundedCornerShape(50)).background(G.Ink).padding(horizontal = 18.dp, vertical = 12.dp)) {
            Text(message ?: "", style = T.smallStrong.copy(color = G.Paper), textAlign = TextAlign.Center)
        }
    }
    LaunchedEffect(message) {
        if (message != null) { kotlinx.coroutines.delay(2600); onDone() }
    }
}

@Composable
fun Chip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .height(36.dp)
            .clip(RoundedCornerShape(50))
            .background(if (selected) G.Ink else G.Paper)
            .border(BorderStroke(1.dp, if (selected) G.Ink else G.Fog), RoundedCornerShape(50))
            .clickable(role = Role.Checkbox, onClick = onClick)
            .semantics { stateDescription = if (selected) "선택됨" else "선택 안 됨" }
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = T.smallStrong.copy(color = if (selected) G.Paper else G.Ink), maxLines = 1)
    }
}

val ScreenPadding = PaddingValues(horizontal = 24.dp)
