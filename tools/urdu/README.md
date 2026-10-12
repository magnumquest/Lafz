# Roman → Urdu transliteration model (offline)

Pipeline that builds `app/src/main/assets/urdu/urdu_translit.bin`, consumed by
`helium314.keyboard.latin.urdu.UrduTransliterator`.

Data: [Google Dakshina](https://github.com/google-research-datasets/dakshina) (CC BY-SA 4.0): Urdu romanization
lexicons + Urdu Wikipedia text. Download `dakshina_dataset_v1.0.tar`, extract `ur/`, and set `DAKSHINA=.../ur`.
Extra deps: `pip install cmudict wordfreq cairosvg pillow` (cmudict/wordfreq only for the loanword step).

## 1. Core model
    python3 train_channel.py 5            # EM: Urdu letter -> Roman chunk model  -> channel.json
    python3 build_lm.py 60000 --holdout   # vocab + unigram/bigram counts          -> lm.pkl
    python3 export_model.py out.bin       # eval model (train lexicon only)
    python3 make_eval.py && javac -d eval/out -sourcepath ../../app/src/main/java eval/Eval.java
    java -cp eval/out Eval out.bin 1500   # word accuracy on held-out romanized sentences

## 2. Extra dictionary entries (verbs, English loanwords) -> extra.json
`extra.json` is committed, so this step only needs re-running when changing the lists.

    python3 loan_guesses.py                       # english -> phonetic roman guesses (CMUdict)
    javac -d eval/out -sourcepath ../../app/src/main/java eval/Loan.java eval/MineCheck.java
    java -cp eval/out Loan out.bin eng_guesses.tsv loanwords_raw.tsv      # decoder votes (6 min on 4 cores)
    python3 mine_pairs.py && java -cp eval/out MineCheck out.bin eng_guesses.tsv mined_pairs.tsv mined_ok.tsv
    python3 build_extra.py                        # curated (loanwords_curated.tsv) + mined + voted + verb phrases -> extra.json

Sources by trust: hand-curated `loanwords_curated.tsv` (edit this to add words you care about!) > mined "اردو (english)" pairs confirmed
by the decoder > decoder-voted pairs for common English words (wordfreq zipf >= 3.5). Verb phrases ("آؤں گا") are generated for
~150 seed + attested infinitives.

## 3. Shipping model
    python3 build_lm.py 60000          # no --holdout
    python3 export_model.py ../../app/src/main/assets/urdu/urdu_translit.bin --ship --extra
