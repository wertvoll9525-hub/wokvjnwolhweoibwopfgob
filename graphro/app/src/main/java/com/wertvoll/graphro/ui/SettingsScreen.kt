package com.wertvoll.graphro.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.wertvoll.graphro.AppViewModel
import com.wertvoll.graphro.BuildConfig
import com.wertvoll.graphro.Screen

const val CONTACT_EMAIL = "wertvoll9525@gmail.com"

@Composable
private fun SettingRow(title: String, sub: String? = null, danger: Boolean = false, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 60.dp).padding(horizontal = 24.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = T.bodyStrong.copy(color = if (danger) G.SignalText else G.Ink))
                if (sub != null) Text(sub, style = T.small.copy(color = G.Sub))
            }
            SvgIcon(Ic.Chevron, color = G.Sub, size = 14.dp)
        }
        Hairline(Modifier.padding(horizontal = 24.dp))
    }
}

@Composable
fun SettingsScreen(vm: AppViewModel) {
    val ctx = LocalContext.current
    var confirm by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().background(G.Paper).statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState())) {
        TopBar(onBack = { vm.goBack() })
        Column(Modifier.padding(horizontal = 24.dp)) {
            Meta("SETTINGS")
            Spacer(Modifier.height(8.dp))
            Text("설정", style = T.title)
            Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Logo(28)
                Spacer(Modifier.padding(start = 12.dp))
                Column {
                    Text("그래프로", style = T.heading)
                    Meta("VERSION ${BuildConfig.VERSION_NAME}")
                }
            }
            Spacer(Modifier.height(16.dp))
            Text("숫자는 지어내지 않고, 데이터는 이 폰 밖으로 나가지 않아요. 인터넷 권한도 쓰지 않아요.", style = T.small)
            Spacer(Modifier.height(20.dp))
        }
        Hairline(Modifier.padding(horizontal = 24.dp))
        SettingRow("개인정보처리방침", "수집하는 정보가 없어요") { vm.go(Screen.PRIVACY) }
        SettingRow("오픈소스 라이선스", "Pretendard, IBM Plex Mono") { vm.go(Screen.LICENSES) }
        SettingRow("문의하기", CONTACT_EMAIL) {
            val i = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$CONTACT_EMAIL")).apply {
                putExtra(Intent.EXTRA_SUBJECT, "[그래프로 ${BuildConfig.VERSION_NAME}] 문의")
            }
            try { ctx.startActivity(i) } catch (_: ActivityNotFoundException) { vm.toast = CONTACT_EMAIL }
        }
        SettingRow("모든 차트 지우기", "저장된 작업을 모두 지워요", danger = true) { confirm = true }
        Spacer(Modifier.height(40.dp))
    }
    if (confirm) ConfirmDialog("모든 차트를 지울까요?", "이 폰에 저장된 차트 ${vm.projects.size}개를 모두 지워요. 되돌릴 수 없어요.", "모두 지우기", { confirm = false }, { confirm = false; vm.deleteAll() }, danger = true)
}

val PRIVACY_TEXT = """
그래프로(이하 ‘앱’)는 이용자의 개인정보를 수집하지 않습니다.

1. 수집하는 정보
앱은 이름, 이메일, 전화번호, 위치, 기기 식별자 등 어떤 개인정보도 수집하지 않습니다. 회원가입이나 로그인이 없습니다.

2. 데이터 처리 방식
이용자가 붙여넣거나 연 표와 파일은 기기 안에서만 처리됩니다. 앱은 인터넷 권한을 요청하지 않으므로 데이터를 외부 서버로 보낼 수 없습니다. 만든 차트는 기기의 앱 전용 저장공간에만 저장되며, 기기 백업에도 포함되지 않습니다.

3. 클립보드
홈 화면에서는 클립보드에 글자가 있는지만 확인합니다. 내용은 이용자가 ‘만들기’나 ‘클립보드에서’를 누를 때만 읽으며, 기기 밖으로 보내지 않습니다.

4. 공유와 저장
이용자가 ‘공유’ 또는 ‘저장’을 누르면, 선택한 앱이나 위치로 파일이 전달됩니다. 이후의 처리는 해당 앱의 정책을 따릅니다.

5. 광고와 분석
앱에는 광고, 분석, 추적 도구가 없습니다.

6. 아동
앱은 어떤 연령의 이용자에게서도 정보를 수집하지 않습니다.

7. 데이터 삭제
설정의 ‘모든 차트 지우기’ 또는 앱 삭제로 저장된 데이터를 모두 지울 수 있습니다.

8. 문의
wertvoll9525@gmail.com

시행일: 2026년 11월 3일
""".trimIndent()

@Composable
fun PrivacyScreen(vm: AppViewModel) {
    Column(Modifier.fillMaxSize().background(G.Paper).statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState())) {
        TopBar(onBack = { vm.goBack() })
        Column(Modifier.padding(horizontal = 24.dp)) {
            Meta("PRIVACY")
            Spacer(Modifier.height(8.dp))
            Text("개인정보처리방침", style = T.title)
            Spacer(Modifier.height(20.dp))
            Text(PRIVACY_TEXT, style = T.body)
            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
fun LicensesScreen(vm: AppViewModel) {
    val ctx = LocalContext.current
    val texts = remember {
        listOf("Pretendard" to "licenses/pretendard_ofl.txt", "IBM Plex Mono" to "licenses/ibmplexmono_ofl.txt").map { (n, p) ->
            n to runCatching { ctx.assets.open(p).bufferedReader().readText() }.getOrDefault("")
        }
    }
    Column(Modifier.fillMaxSize().background(G.Paper).statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState())) {
        TopBar(onBack = { vm.goBack() })
        Column(Modifier.padding(horizontal = 24.dp)) {
            Meta("LICENSES")
            Spacer(Modifier.height(8.dp))
            Text("오픈소스 라이선스", style = T.title)
            Spacer(Modifier.height(12.dp))
            Text("앱의 차트 엔진(표 읽기, 추천, 검사, 파일 만들기)은 직접 작성했어요. 아래 글꼴은 SIL Open Font License 1.1로 제공돼요. 화면은 Android Jetpack(Apache 2.0)으로 만들었어요.", style = T.small)
            texts.forEach { (name, text) ->
                Spacer(Modifier.height(24.dp))
                Text(name, style = T.heading)
                Spacer(Modifier.height(8.dp))
                Text(text, style = T.mono.copy(fontSize = T.meta.fontSize, lineHeight = T.meta.lineHeight * 1.4f, color = G.Body))
            }
            Spacer(Modifier.height(40.dp))
        }
    }
}
