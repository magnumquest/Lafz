#!/usr/bin/env bash
# Runs a SuggestionEval set on the private emulator (debug build installed, keyboard selected, a text field reachable).
# usage: tools/eval/run_eval.sh SET.tsv OUT.tsv
set -e
A="adb -s emulator-5580"; P=app.lafz.keyboard.debug; HERE="$(cd "$(dirname "$0")/../.." && pwd)"
$A shell run-as $P rm -f files/sugg_eval_out.tsv
cat "$1" | $A shell "run-as $P sh -c 'cat > files/sugg_eval_in.tsv'"
"$HERE/tools/emu/emu.sh" field2 >/dev/null   # (re)opening the field starts the input view, which starts the run
for i in $(seq 1 120); do
  $A shell run-as $P ls files/sugg_eval_out.tsv >/dev/null 2>&1 && break; sleep 2
done
$A shell run-as $P cat files/sugg_eval_out.tsv > "$2"
wc -l < "$2"
