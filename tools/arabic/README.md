# Roman → Arabic transliteration model (offline), scripture-focused

Builds `app/src/main/assets/arabic/arabic_translit.bin`, read by `RomanTransliterator` (same engine and file format as Urdu).
Main use: typing Qur'an / Hadith words (and everyday Arabizi) in Roman letters and getting Arabic script.

## Data (the bundle is NOT in the repo; licences are unclear/restrictive, keep this repo private)
Bundle `lafz_arabic_data.zip` → `Arabic/` (README inside): real chat Arabizi pairs (arbml/Arabizi_Transliteration), generated typed-style
spellings of 30k Qur'an+Hadith words, hand-written religious terms, Qur'an + Hadith running text, vocabulary counts.
Plus `wordfreq` (pip, Arabic frequencies, CC BY-SA 4.0 data). Derived files in the repo: `channel_trained.json`, the model asset.

## Build
    pip install wordfreq
    export DATA=/path/to/Arabic
    python3 prep_data.py                                   # work/: train pairs (real 1.0, curated 1.0, synthetic 0.3 per word), dev sets
    python3 train_channel.py work/train_pairs.tsv 5        # EM from the prior in channel.json -> channel_trained.json (~2 min, 4 cores)
    python3 build_model.py --channel channel_trained.json --variants work/real_all.tsv     # writes the asset
    # leak-free evaluation model: add  --variants work/real_train.tsv --exclude work/dev_religious.tsv  and an output path, then
    javac -d eval/out -sourcepath ../../app/src/main/java eval/Smoke.java
    java -cp eval/out Smoke model.bin work/dev_real.tsv     # also work/dev_religious.tsv, eval/arabizi_smoke.tsv

Model: vocabulary 117k (wordfreq top 100k + scripture words + chat words + `chat_curated.tsv`); unigram = 35 % wordfreq + 65 % Qur'an/Hadith
counts (`--religious-mix`); 76k bigrams from Qur'an + Hadith text only; channel model = EM-trained letter→Roman chunks (digits 3 7 5 6 8 9 2 included).

## Held-out results (words/terms never used for training or spellings)
| set | top-1 | top-3 |
|---|---|---|
| 1494 real chat spellings (47 % of the words are not in the dictionary at all, ceiling ≈ 52 %) | 34.9 % | 44.6 % |
| 41 hand-written religious spellings (small!) | 56.1 % | 65.9 % |
| `eval/arabizi_smoke.tsv` (my own general-vocabulary list, written around the old prior, least trustworthy) | 72.8 % | 94.4 % |
Prior-only channel (no training) scored 22.3 / 53.7 / 77.8 % top-1 on the same sets. ~8 ms/query on a desktop JVM (Urdu ~3 ms).

Known gaps: dialect words missing from the dictionary; "rabbil alameen"-style linking forms only where listed in `chat_curated.tsv`;
bigrams are scripture-only, so everyday sentences get weak context; no sentence-level Arabizi data was available for evaluation.
`train_channel.py` also accepts any further `arabic<TAB>roman<TAB>count<TAB>groupweight<TAB>group` files.
