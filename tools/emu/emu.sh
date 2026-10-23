#!/usr/bin/env bash
# Private-emulator helpers for Lafz (port 5580; never touches emulator-5554).  usage: tools/emu/emu.sh <cmd> [args]
#   settings | tap x y | swipe x1 y1 x2 y2 [ms] | back | night <yes|no> | boot | install [apk] | subtype <en-US|ur-Latn|ar-Latn> | field | field2 | clear | switch <ur|ar|en> | ui [regex] | uitap <regex> | type "<text>" [delay] | key <name> | shot <png> | logcat | kill
# Key coordinates are for the 1080x2400 Pixel_API36 AVD with the Lafz alphabet layout (qwerty rows at y=1842/1980/2114, space y=2252).
. ~/.android-env.sh
S=emulator-5580; A="adb -s $S"; P=app.lafz.keyboard.debug; IME=$P/helium314.keyboard.latin.LatinIME
HERE="$(cd "$(dirname "$0")/../.." && pwd)"
case "$1" in
settings) $A shell am start -n "$P/helium314.keyboard.settings.SettingsActivity" ;;
tap) $A shell input tap "$2" "$3" ;;
swipe) $A shell input swipe "$2" "$3" "$4" "$5" "${6:-300}" ;;
back) $A shell input keyevent KEYCODE_BACK ;;
night) $A shell cmd uimode night "$2" ;;

boot)
  adb devices | grep -q $S || nohup emulator -avd Pixel_API36 -port 5580 -read-only -no-snapshot -no-audio -no-window -gpu swiftshader_indirect > /tmp/emu.log 2>&1 &
  for i in $(seq 1 60); do $A shell getprop sys.boot_completed 2>/dev/null | grep -q 1 && break; sleep 5; done; $A shell getprop sys.boot_completed ;;
install) $A install -r "${2:-$HERE/app/build/outputs/apk/debug/Lafz_0.4.2-debug.apk}" | tail -1
  $A shell ime enable $IME >/dev/null; $A shell ime set $IME ;;
subtype) # writes enabled/selected subtype into the debug app's device-protected prefs (app is force-stopped first)
  F=/data/user_de/0/$P/shared_prefs/${P}_preferences.xml
  $A shell am force-stop $P; $A shell ime set $IME >/dev/null
  $A shell run-as $P cat $F > /tmp/emu_prefs.xml 2>/dev/null
  python3 -I - "$2" <<'PY'
import re,sys
tag=sys.argv[1]
try: s=open('/tmp/emu_prefs.xml',encoding='utf-8').read()
except Exception: s=''
if '<map>' not in s: s="<?xml version='1.0' encoding='utf-8' standalone='yes' ?>\n<map>\n</map>\n"
s=re.sub(r'\s*<string name="(selected_subtype|enabled_subtypes)">.*?</string>','',s)
s=s.replace('</map>','    <string name="selected_subtype">%s§</string>\n    <string name="enabled_subtypes">en-US§SupportTouchPositionCorrection,TrySuppressingImeSwitcher;ur-Latn§;ar-Latn§</string>\n</map>'%tag)
open('/tmp/emu_prefs2.xml','w',encoding='utf-8').write(s)
PY
  cat /tmp/emu_prefs2.xml | $A shell "run-as $P sh -c 'cat > $F'" ;;
field) $A shell input keyevent KEYCODE_HOME; $A shell ime set $IME >/dev/null
  $A shell am start -a android.settings.APP_SEARCH_SETTINGS >/dev/null 2>&1; sleep 3; $A shell input tap 540 156; sleep 2 ;;
type) shift; T="$1"; D="${2:-0.2}"
  declare -A X=([q]=54 [w]=162 [e]=270 [r]=378 [t]=486 [y]=594 [u]=702 [i]=810 [o]=918 [p]=1026 [a]=108 [s]=216 [d]=324 [f]=432 [g]=540 [h]=648 [j]=756 [k]=864 [l]=972 [z]=216 [x]=324 [c]=432 [v]=540 [b]=648 [n]=756 [m]=864)
  for ((i=0;i<${#T};i++)); do c=${T:$i:1}; if [ "$c" = " " ]; then $A shell input tap 540 2252; else
    case "qwertyuiop" in *$c*) y=1842;; esac; case "asdfghjkl" in *$c*) y=1980;; esac; case "zxcvbnm" in *$c*) y=2114;; esac
    $A shell input tap ${X[$c]} $y; fi; sleep $D; done ;;
field2) # Google Messages compose box (multi-line, normal suggestions; the settings search field has NO_SUGGESTIONS)
  $A shell input keyevent KEYCODE_HOME; sleep 1
  $A shell am start -a android.intent.action.SENDTO -d sms:5551234 >/dev/null 2>&1; sleep 3
  "$0" uitap 'class="android.widget.EditText"' || $A shell input tap 540 2000; sleep 1 ;;
clear) # empties the focused field (a text field must be focused): select all, then delete the selection
  $A shell input keycombination KEYCODE_CTRL_LEFT KEYCODE_A
  $A shell input keyevent KEYCODE_DEL ;;
ui) # dump the current UI, print nodes matching the optional regex
  $A shell uiautomator dump /sdcard/emu_ui.xml >/dev/null 2>&1; $A shell cat /sdcard/emu_ui.xml | tr -d '\r' > /tmp/emu_ui.xml
  python3 -I "$HERE/tools/emu/uinode.py" /tmp/emu_ui.xml list "${2:-.}" ;;
uitap) # tap the centre of the first node whose attributes match the regex
  $A shell uiautomator dump /sdcard/emu_ui.xml >/dev/null 2>&1; $A shell cat /sdcard/emu_ui.xml | tr -d '\r' > /tmp/emu_ui.xml
  B=$(python3 -I "$HERE/tools/emu/uinode.py" /tmp/emu_ui.xml center "$2") || { echo "uitap: no match for $2"; exit 1; }
  echo "tap $B"; $A shell input tap $B ;;
switch) # long-press space (language chooser), then tap the entry; verify with: emu.sh ui
  case "$2" in ur) L='text="Urdu[^"]*Lafz';; ar) L='text="Arabic[^"]*Lafz';; en) L='text="English[^"]*Lafz';; *) echo "usage: switch ur|ar|en"; exit 1;; esac
  $A shell input swipe 540 2252 540 2252 800; sleep 1.5
  "$0" uitap "$L" || { echo "switch: '$L' not visible; see: $0 ui"; exit 1; }; sleep 1.5 ;;
key) case "$2" in backspace) $A shell input tap 1000 2114;; symbols) $A shell input tap 80 2252;; enter) $A shell input tap 1000 2252;; esac ;;
shot) $A exec-out screencap -p > "${2:-/tmp/shot.png}" ;;
logcat) $A logcat -d -s LatinIME:* LafzPerf:* AndroidRuntime:E ;;
kill) $A emu kill ;;
*) echo "usage: $0 settings|tap|swipe|back|night|boot|install|subtype|field|field2|clear|switch|ui|uitap|type|key|shot|logcat|kill"; exit 1 ;;
esac
