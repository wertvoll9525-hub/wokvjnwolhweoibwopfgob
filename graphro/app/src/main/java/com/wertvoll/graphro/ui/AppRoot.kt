package com.wertvoll.graphro.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wertvoll.graphro.AppViewModel
import com.wertvoll.graphro.Screen
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun AppRoot(vm: AppViewModel, playIntro: Boolean) {
    var showIntro by rememberSaveable { mutableStateOf(playIntro) }
    Box(Modifier.fillMaxSize().background(G.Paper)) {
        BackHandler(enabled = vm.screen != Screen.HOME) {
            if (vm.screen == Screen.DATA) vm.cancelImport() else vm.goBack()
        }
        AnimatedContent(
            targetState = vm.screen,
            transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
            label = "screen",
        ) { s ->
            when (s) {
                Screen.HOME -> HomeScreen(vm)
                Screen.PASTE -> PasteScreen(vm)
                Screen.DATA -> DataScreen(vm)
                Screen.EDITOR -> EditorScreen(vm)
                Screen.PROJECTS -> ProjectsScreen(vm)
                Screen.SETTINGS -> SettingsScreen(vm)
                Screen.PRIVACY -> PrivacyScreen(vm)
                Screen.LICENSES -> LicensesScreen(vm)
            }
        }
        if (vm.busy) {
            Box(Modifier.fillMaxSize().background(Color(0x33FFFFFF)), contentAlignment = Alignment.Center) {
                Row(
                    Modifier.clip(RoundedCornerShape(50)).background(G.Ink).padding(horizontal = 18.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(Modifier.size(16.dp), color = G.Paper, strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text("표를 읽고 있어요", style = T.smallStrong.copy(color = G.Paper))
                }
            }
        }
        Toast(vm.toast) { vm.toast = null }
        AnimatedVisibility(showIntro, enter = fadeIn(), exit = fadeOut(tween(260))) {
            IntroSplash { showIntro = false }
        }
    }
}

/** Bars rise one by one; the last, colored bar overshoots and settles with a spring. */
@Composable
fun IntroSplash(onDone: () -> Unit) {
    val b1 = remember { Animatable(0f) }
    val b2 = remember { Animatable(0f) }
    val b3 = remember { Animatable(0f) }
    val word = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        val a = async { b1.animateTo(1f, tween(300, easing = FastOutSlowInEasing)) }
        delay(110)
        val b = async { b2.animateTo(1f, tween(300, easing = FastOutSlowInEasing)) }
        delay(160)
        val c = async { b3.animateTo(1f, spring(dampingRatio = 0.32f, stiffness = Spring.StiffnessMediumLow)) }
        delay(260)
        val w = async { word.animateTo(1f, tween(360, easing = FastOutSlowInEasing)) }
        a.await(); b.await(); w.await()
        delay(420)
        onDone()
    }
    Box(Modifier.fillMaxSize().background(G.Paper).semantics { contentDescription = "그래프로" }, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.height(104.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Bar(52, G.Ink, b1.value)
                Bar(80, G.Ink, b2.value)
                Bar(100, G.Signal, b3.value)
            }
            Spacer(Modifier.height(28.dp))
            Column(
                Modifier.graphicsLayer { alpha = word.value; translationY = (1f - word.value) * 10.dp.toPx() },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("그래프로", style = T.title.copy(fontSize = 26.sp))
                Spacer(Modifier.height(6.dp))
                Text("틀리지 않는 차트", style = T.small.copy(fontSize = 14.sp))
            }
        }
        Box(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 40.dp).graphicsLayer { alpha = word.value }) {
            Meta("OFFLINE · ON-DEVICE")
        }
    }
}

@Composable
private fun Bar(heightDp: Int, color: Color, progress: Float) {
    Box(
        Modifier
            .size(width = 26.dp, height = heightDp.dp)
            .graphicsLayer { scaleY = progress.coerceAtLeast(0f); transformOrigin = TransformOrigin(0.5f, 1f) }
            .clip(RoundedCornerShape(5.dp))
            .background(color),
    )
}
