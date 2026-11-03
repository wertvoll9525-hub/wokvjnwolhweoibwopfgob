package com.wertvoll.graphro.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.wertvoll.graphro.R

object G {
    val Ink = Color(0xFF121212)
    val Graphite = Color(0xFF2E2E33)
    val Body = Color(0xFF4A4A50)
    val Sub = Color(0xFF6E6E75)
    val Faint = Color(0xFF9A9AA0)
    val Fog = Color(0xFFDADADC)
    val Fog2 = Color(0xFFCFCFD2)
    val Line = Color(0xFFECECE9)
    val Line2 = Color(0xFFE6E6E2)
    val Mist = Color(0xFFF4F4F1)
    val Paper = Color(0xFFFFFFFF)
    val Signal = Color(0xFFF04E23)
    val SignalText = Color(0xFFC33A15)
    val SignalWash = Color(0xFFFDE7DF)
    val Indigo = Color(0xFF2C3E9E)
    val Pass = Color(0xFF0F7A57)
    val Scrim = Color(0x8C121212)
    val DockText = Color(0xFFCFCFD2)
}

val Pretendard = FontFamily(
    Font(R.font.pretendard_regular, FontWeight.Normal),
    Font(R.font.pretendard_semibold, FontWeight.SemiBold),
    Font(R.font.pretendard_bold, FontWeight.Bold),
)

val PlexMono = FontFamily(
    Font(R.font.plexmono_regular, FontWeight.Normal),
    Font(R.font.plexmono_medium, FontWeight.Medium),
    Font(R.font.plexmono_semibold, FontWeight.SemiBold),
)

object T {
    val display = TextStyle(fontFamily = Pretendard, fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 38.sp, letterSpacing = (-0.045).em, color = G.Ink)
    val title = TextStyle(fontFamily = Pretendard, fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 32.sp, letterSpacing = (-0.045).em, color = G.Ink)
    val heading = TextStyle(fontFamily = Pretendard, fontWeight = FontWeight.Bold, fontSize = 17.sp, lineHeight = 24.sp, letterSpacing = (-0.025).em, color = G.Ink)
    val body = TextStyle(fontFamily = Pretendard, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 23.sp, letterSpacing = (-0.01).em, color = G.Body)
    val bodyStrong = TextStyle(fontFamily = Pretendard, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 22.sp, color = G.Ink)
    val small = TextStyle(fontFamily = Pretendard, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 20.sp, color = G.Body)
    val smallStrong = TextStyle(fontFamily = Pretendard, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, lineHeight = 18.sp, color = G.Ink)
    val button = TextStyle(fontFamily = Pretendard, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 20.sp)
    val meta = TextStyle(fontFamily = PlexMono, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.08.em, color = G.Sub)
    val mono = TextStyle(fontFamily = PlexMono, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp, color = G.Ink)
    val monoBig = TextStyle(fontFamily = PlexMono, fontWeight = FontWeight.Medium, fontSize = 34.sp, lineHeight = 36.sp, color = G.Ink)
}

@Composable
fun GraphroTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = G.Ink, onPrimary = G.Paper, secondary = G.Signal, onSecondary = G.Paper,
            background = G.Paper, onBackground = G.Ink, surface = G.Paper, onSurface = G.Ink,
            surfaceVariant = G.Mist, onSurfaceVariant = G.Body, outline = G.Line2, error = G.SignalText,
        ),
        content = content,
    )
}
