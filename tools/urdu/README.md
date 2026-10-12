# Roman → Urdu transliteration model (offline)

Pipeline that builds `app/src/main/assets/urdu/urdu_translit.bin`, consumed by
`helium314.keyboard.latin.urdu.UrduTransliterator`.

Data: [Google Dakshina](https://github.com/google-research-datasets/dakshina) (CC BY-SA 4.0): Urdu romanization
lexicons + Urdu Wikipedia text. Download `dakshina_dataset_v1.0.tar`, extract `ur/`, and set `DAKSHINA=.../ur`.

    python3 train_channel.py 5            # EM: Urdu letter -> Roman chunk model  -> channel.json
    python3 build_lm.py 60000 --holdout   # vocab + unigram/bigram counts          -> lm.pkl
    python3 export_model.py out.bin       # eval model (train lexicon only)
    python3 make_eval.py && javac -d eval/out -sourcepath ../../app/src/main/java eval/Eval.java
    java -cp eval/out Eval out.bin 1500   # word accuracy on held-out romanized sentences

Shipping model: `build_lm.py 60000` (no holdout) then `export_model.py ../../app/src/main/assets/urdu/urdu_translit.bin --ship`.
