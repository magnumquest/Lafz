# Roman → Arabic transliteration model (offline)

Builds `app/src/main/assets/arabic/arabic_translit.bin`, read by `RomanTransliterator` (same engine and file format as Urdu).

**Status: NOT trained on human-written Arabizi.** No such corpus was reachable when this was built.
* Vocabulary + unigram frequencies: `wordfreq` Arabic list (data CC BY-SA 4.0, keep attribution in the app credits), 100k words.
* No bigrams (no running Arabic text available): next-word prediction only offers honorific suffixes.
* Channel model `channel.json` is a documented prior (`channel_prior.py`: 3=ع 7=ح 5=خ 6=ط 8=غ 9=ص/ق 2=ء, sh/kh/gh, optional vowels).
* `chat_curated.tsv`: hand-written dialect words, loanwords, fused religious phrases and spellings (edit freely).
* `eval/arabizi_smoke.tsv` is a hand-written smoke test, not a benchmark (~87% top-1 / 97% top-3 on it, ~6 ms/query on a desktop JVM).

## Build
    pip install wordfreq
    python3 channel_prior.py && python3 build_model.py     # writes the asset
    javac -d eval/out -sourcepath ../../app/src/main/java eval/Smoke.java && java -cp eval/out Smoke ../../app/src/main/assets/arabic/arabic_translit.bin eval/arabizi_smoke.tsv

## When real data exists
    python3 train_channel.py arabizi_pairs.tsv 5     # EM from the prior -> channel_trained.json; copy over channel.json, rebuild
Bigrams: extend `build_model.py` to count bigrams from Arabic text (see tools/urdu/build_lm.py / export_model.py for the format).
