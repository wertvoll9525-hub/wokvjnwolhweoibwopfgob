#!/usr/bin/env bash
# Drives the debug app on an emulator: taps through the main flow, saves screenshots and
# fails if the app crashes. Taps are found by visible text through uiautomator.
set -u
PKG=com.wertvoll.graphro.debug
OUT=${1:-screens}
UI_LANG=${2:-ko}

# Visible labels the script taps, per app language.
case "$UI_LANG" in
  en) L_SAMPLE="Sample"; L_DATA="read your table"; L_RAW="Original"; L_TABLE="Table"; L_MAKE="Make chart"; L_HL="Highlight"
      L_CHANGE="Biggest change"; L_AXIS="Axis"; L_TYPE="Type"; L_BAR="Bar"; L_MANUAL="Manual"; L_OK="OK"; L_CHECK="Check"
      L_EXPORT="Export"; L_SHEET="Excel"; L_SETTINGS="Settings"; L_PRIVACY="Privacy policy"; LOCALE=en-US
      SHARE='City\tPopulation\nNew York\t8,258,000\nLos Angeles\t3,821,000\nChicago\t2,665,000\nHouston\t2,314,000\nPhoenix\t1,650,000\nTotal\t18,708,000\n' ;;
  ja) L_SAMPLE="サンプル"; L_DATA="読み取りました"; L_RAW="原文"; L_TABLE="表"; L_MAKE="グラフを作成"; L_HL="強調"
      L_CHANGE="大きな変化"; L_AXIS="軸"; L_TYPE="種類"; L_BAR="棒"; L_MANUAL="手動"; L_OK="OK"; L_CHECK="チェック"
      L_EXPORT="書き出し"; L_SHEET="Excel"; L_SETTINGS="設定"; L_PRIVACY="プライバシーポリシー"; LOCALE=ja-JP
      SHARE='都市\t人口(人)\n東京\t14,047,594\n横浜\t3,777,491\n大阪\t2,752,412\n名古屋\t2,332,176\n札幌\t1,973,395\n合計\t24,883,068\n' ;;
  *)  L_SAMPLE="예제"; L_DATA="표를 이렇게"; L_RAW="원문"; L_TABLE="표"; L_MAKE="차트 만들기"; L_HL="강조"
      L_CHANGE="큰 변화"; L_AXIS="축"; L_TYPE="종류"; L_BAR="막대"; L_MANUAL="직접"; L_OK="확인"; L_CHECK="검사"
      L_EXPORT="내보내기"; L_SHEET="엑셀"; L_SETTINGS="설정"; L_PRIVACY="개인정보처리방침"; LOCALE=ko-KR
      SHARE='지역\t인구(명)\n서울\t9411000\n부산\t3293000\n대구\t2374000\n인천\t2997000\n광주\t1419000\n합계\t19494000\n' ;;
esac
mkdir -p "$OUT"
adb logcat -c
fail=0

shot() { sleep "${2:-2}"; adb exec-out screencap -p > "$OUT/$1.png"; echo "shot $1"; }

tap_text() {
  # tap the first node whose text or content-desc contains $1
  if [ "$1" != "Wait" ] && type dismiss_system >/dev/null 2>&1; then dismiss_system; fi
  adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
  adb pull /sdcard/ui.xml /tmp/ui.xml >/dev/null 2>&1
  local xy
  xy=$(python3 - "$1" <<'PY'
import sys, re, xml.etree.ElementTree as ET
want = sys.argv[1]
root = ET.parse('/tmp/ui.xml').getroot()
nodes = list(root.iter('node'))
def pick(test):
    for n in nodes:
        if test((n.get('text') or '').strip()) or test((n.get('content-desc') or '').strip()):
            return n
n = pick(lambda t: t == want)
if n is None:
    n = pick(lambda t: want in t)
if n is not None:
    x1, y1, x2, y2 = map(int, re.findall(r'\d+', n.get('bounds')))
    print((x1 + x2) // 2, (y1 + y2) // 2)
PY
)
  if [ -z "$xy" ]; then echo "NOT FOUND: $1"; return 1; fi
  adb shell input tap $xy
  echo "tap $1 at $xy"
}

wait_text() {
  for i in $(seq 1 20); do
    adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
    adb pull /sdcard/ui.xml /tmp/ui.xml >/dev/null 2>&1
    grep -q "$1" /tmp/ui.xml && return 0
    sleep 1
  done
  echo "TIMEOUT waiting for: $1"; return 1
}

crashed() {
  if adb logcat -d | grep -E "FATAL EXCEPTION|ANR in $PKG" >/dev/null; then
    echo "!!! CRASH"; adb logcat -d | grep -A 40 "FATAL EXCEPTION" | head -80; return 0
  fi
  return 1
}

# Keep slow-emulator system dialogs ("Pixel Launcher isn't responding") from covering the app.
adb shell settings put global hide_error_dialogs 1
adb shell settings put global anr_show_background 0
sleep 20
dismiss_system() {
  adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
  adb pull /sdcard/ui.xml /tmp/ui.xml >/dev/null 2>&1
  if grep -q "isn't responding" /tmp/ui.xml; then tap_text "Wait"; sleep 1; fi
}
adb shell cmd locale set-app-locales $PKG --locales $LOCALE || true
sleep 2
adb shell am start -W -n $PKG/com.wertvoll.graphro.MainActivity
shot 00-splash 0.6
shot 01-home 3
tap_text "$L_SAMPLE" || fail=1
wait_text "$L_DATA" || fail=1
shot 02-data 1.5
tap_text "$L_RAW" && shot 02b-raw 1.5
tap_text "$L_TABLE" ; sleep 1
tap_text "$L_MAKE" || fail=1
shot 03-editor 3
tap_text "$L_HL" && shot 04-highlight 2
tap_text "$L_CHANGE" && shot 04b-change 2
tap_text "$L_AXIS" && shot 05-axis 2
tap_text "$L_TYPE" && sleep 1 && tap_text "$L_BAR" && shot 05b-bar 2
# Make an exaggerated axis so the honesty check has something to catch
tap_text "$L_AXIS" && sleep 1 && tap_text "$L_MANUAL" && sleep 1.5
for i in $(seq 1 24); do adb shell input keyevent 67; done
adb shell input text "250,500"; sleep 0.5
tap_text "$L_OK"; sleep 1.5
shot 05c-axis-250 1.5
tap_text "$L_CHECK" && shot 06-check 2.5
adb shell input keyevent KEYCODE_BACK; sleep 1
tap_text "$L_EXPORT" && shot 07-export-image 3
tap_text "PPT" && shot 08-export-ppt 2
tap_text "$L_SHEET" && shot 09-export-sheet 2
adb shell input keyevent KEYCODE_BACK; sleep 1
adb shell input keyevent KEYCODE_BACK; sleep 1
shot 10-home-recent 2
# Share text into the app like another app would
printf "$SHARE" > /tmp/share.txt
adb push /tmp/share.txt /data/local/tmp/share.txt >/dev/null
adb shell 'am start -a android.intent.action.SEND -t text/plain --es android.intent.extra.TEXT "$(cat /data/local/tmp/share.txt)" -n '$PKG'/com.wertvoll.graphro.MainActivity'
wait_text "$L_DATA" || fail=1
shot 11-shared-data 1.5
tap_text "$L_MAKE" && shot 12-shared-chart 3
adb shell input keyevent KEYCODE_BACK; sleep 1
tap_text "$L_SETTINGS" && shot 13-settings 2
tap_text "$L_PRIVACY" && shot 14-privacy 2

if crashed; then fail=1; fi
adb logcat -d -s AndroidRuntime:E > "$OUT/crash-log.txt" || true
echo "fail=$fail"
exit $fail
