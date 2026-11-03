package com.wertvoll.graphro.ui

import com.graphro.core.text.I18n

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
            Text(I18n.tr("설정", "Settings", "設定"), style = T.title)
            Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Logo(28)
                Spacer(Modifier.padding(start = 12.dp))
                Column {
                    Text(I18n.tr("그래프로", "Graphro", "グラフロ"), style = T.heading)
                    Meta("VERSION ${BuildConfig.VERSION_NAME}")
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(I18n.tr("숫자는 지어내지 않고, 데이터는 이 폰 밖으로 나가지 않아요. 인터넷 권한도 쓰지 않아요.", "Never makes up numbers, and your data never leaves this phone. It doesn't even use the internet permission.", "数値を作り出すことはなく、データがこの端末の外に出ることもありません。インターネット権限も使いません。"), style = T.small)
            Spacer(Modifier.height(20.dp))
        }
        Hairline(Modifier.padding(horizontal = 24.dp))
        SettingRow(I18n.tr("언어", "Language", "言語"), I18n.tr("한국어 · English · 日本語", "한국어 · English · 日本語", "한국어 · English · 日本語")) {
            // Android 13+ lets each app have its own language; older versions use the phone's language.
            val i = if (android.os.Build.VERSION.SDK_INT >= 33)
                Intent(android.provider.Settings.ACTION_APP_LOCALE_SETTINGS, Uri.parse("package:" + ctx.packageName))
            else Intent(android.provider.Settings.ACTION_LOCALE_SETTINGS)
            try { ctx.startActivity(i) } catch (_: ActivityNotFoundException) {
                try { ctx.startActivity(Intent(android.provider.Settings.ACTION_LOCALE_SETTINGS)) } catch (_: ActivityNotFoundException) {}
            }
        }
        SettingRow(I18n.tr("개인정보처리방침", "Privacy policy", "プライバシーポリシー"), I18n.tr("수집하는 정보가 없어요", "We collect no data", "収集する情報はありません")) { vm.go(Screen.PRIVACY) }
        SettingRow(I18n.tr("오픈소스 라이선스", "Open-source licenses", "オープンソースライセンス"), "Pretendard, IBM Plex Mono") { vm.go(Screen.LICENSES) }
        SettingRow(I18n.tr("문의하기", "Contact", "お問い合わせ"), CONTACT_EMAIL) {
            val i = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$CONTACT_EMAIL")).apply {
                putExtra(Intent.EXTRA_SUBJECT, "[Graphro ${BuildConfig.VERSION_NAME}] " + I18n.tr("문의", "Feedback", "お問い合わせ"))
            }
            try { ctx.startActivity(i) } catch (_: ActivityNotFoundException) { vm.toast = CONTACT_EMAIL }
        }
        SettingRow(I18n.tr("모든 차트 지우기", "Delete all charts", "すべてのグラフを削除"), I18n.tr("저장된 작업을 모두 지워요", "Deletes all saved work", "保存した作業をすべて削除します"), danger = true) { confirm = true }
        Spacer(Modifier.height(40.dp))
    }
    if (confirm) ConfirmDialog(I18n.tr("모든 차트를 지울까요?", "Delete all charts?", "すべてのグラフを削除しますか？"), I18n.tr("이 폰에 저장된 차트 ${vm.projects.size}개를 모두 지워요. 되돌릴 수 없어요.", "This deletes all ${vm.projects.size} charts on this phone. It can't be undone.", "この端末に保存された${vm.projects.size}件のグラフをすべて削除します。元に戻せません。"), I18n.tr("모두 지우기", "Delete all", "すべて削除"), { confirm = false }, { confirm = false; vm.deleteAll() }, danger = true)
}

val PRIVACY_TEXT: String get() = I18n.tr(PRIVACY_KO, PRIVACY_EN, PRIVACY_JA)

private val PRIVACY_KO = """
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

private val PRIVACY_EN = """
Graphro (“the app”) does not collect any personal information.

1. Information we collect
The app collects no personal information of any kind, including names, email addresses, phone numbers, location or device identifiers. There is no sign-up or log-in.

2. How your data is handled
Tables and files you paste or open are processed only on your device. The app does not request the internet permission, so it cannot send your data to any server. Charts you make are stored only in the app's private storage on your device and are excluded from device backups.

3. Clipboard
On the home screen the app only checks whether the clipboard contains text. It reads the content only when you tap “Create” or “From clipboard”, and never sends it off the device.

4. Sharing and saving
When you tap “Share” or “Save”, the file is passed to the app or location you choose. What happens next is governed by that app's policy.

5. Ads and analytics
The app contains no advertising, analytics or tracking tools.

6. Children
The app does not collect information from users of any age.

7. Deleting data
You can delete all stored data with “Delete all charts” in Settings, or by uninstalling the app.

8. Contact
wertvoll9525@gmail.com

Effective date: November 3, 2026
""".trimIndent()

private val PRIVACY_JA = """
グラフロ（以下「本アプリ」）は、利用者の個人情報を収集しません。

1. 収集する情報
本アプリは、氏名、メールアドレス、電話番号、位置情報、端末識別子など、いかなる個人情報も収集しません。会員登録やログインはありません。

2. データの取り扱い
利用者が貼り付けたり開いたりした表やファイルは、端末内でのみ処理されます。本アプリはインターネット権限を要求しないため、データを外部サーバーへ送信することはできません。作成したグラフは端末内のアプリ専用領域にのみ保存され、端末のバックアップにも含まれません。

3. クリップボード
ホーム画面では、クリップボードに文字があるかどうかだけを確認します。内容は利用者が「作成」または「クリップボードから」をタップしたときにのみ読み取り、端末の外へ送ることはありません。

4. 共有と保存
利用者が「共有」または「保存」をタップすると、選択したアプリや場所にファイルが渡されます。その後の取り扱いは当該アプリのポリシーに従います。

5. 広告と分析
本アプリには広告、分析、トラッキングのツールはありません。

6. 児童
本アプリは、いかなる年齢の利用者からも情報を収集しません。

7. データの削除
設定の「すべてのグラフを削除」またはアプリのアンインストールにより、保存されたデータをすべて削除できます。

8. お問い合わせ
wertvoll9525@gmail.com

施行日: 2026年11月3日
""".trimIndent()

@Composable
fun PrivacyScreen(vm: AppViewModel) {
    Column(Modifier.fillMaxSize().background(G.Paper).statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState())) {
        TopBar(onBack = { vm.goBack() })
        Column(Modifier.padding(horizontal = 24.dp)) {
            Meta("PRIVACY")
            Spacer(Modifier.height(8.dp))
            Text(I18n.tr("개인정보처리방침", "Privacy policy", "プライバシーポリシー"), style = T.title)
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
            Text(I18n.tr("오픈소스 라이선스", "Open-source licenses", "オープンソースライセンス"), style = T.title)
            Spacer(Modifier.height(12.dp))
            Text(I18n.tr("앱의 차트 엔진(표 읽기, 추천, 검사, 파일 만들기)은 직접 작성했어요. 아래 글꼴은 SIL Open Font License 1.1로 제공돼요. 화면은 Android Jetpack(Apache 2.0)으로 만들었어요.", "The chart engine (reading tables, recommending, checking, creating files) is our own code. The fonts below are provided under the SIL Open Font License 1.1. The UI is built with Android Jetpack (Apache 2.0).", "グラフエンジン（表の読み取り、推薦、チェック、ファイル作成）は独自に作成しました。以下のフォントは SIL Open Font License 1.1 で提供されています。画面は Android Jetpack（Apache 2.0）で作りました。"), style = T.small)
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
