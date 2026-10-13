#!/bin/bash
# usage: eval2.sh model.bin   -> prints the four headline numbers
M=$1; shift; X="$@"; H="$(dirname "$0")"; cd "$H"
echo -n "owner phrases : "; java -Dstdout.encoding=UTF-8 -cp eval/out Phrase $M eval/owner_phrases.tsv $X | tail -1
echo -n "hadith typed  : "; java -Dstdout.encoding=UTF-8 -cp eval/out Phrase $M work2/dev_hadith_typed.tsv $X | tail -1
echo -n "real chat words: "; java -Dstdout.encoding=UTF-8 -cp eval/out Smoke $M work/dev_real.tsv $X | tail -1
echo -n "religious terms: "; java -Dstdout.encoding=UTF-8 -cp eval/out Smoke $M work/dev_religious.tsv $X | tail -1
