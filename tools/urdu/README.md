# Urdu transliteration model

These tools build `app/src/main/assets/urdu/urdu_translit.bin`, used by Lafz's offline Roman Urdu transliteration engine.

## Source data

The pipeline uses the [Google Dakshina dataset](https://github.com/google-research-datasets/dakshina), licensed CC BY-SA 4.0. Download `dakshina_dataset_v1.0.tar`, extract its `ur/` directory, and set `DAKSHINA` to that directory. Optional loanword-generation steps use `cmudict`, `wordfreq`, CairoSVG and Pillow.

## Build

Train the channel model and language model, then export a model for local evaluation:

```sh
python3 train_channel.py 5
python3 build_lm.py 60000 --holdout
python3 export_model.py out.bin
python3 make_eval.py
javac -d eval/out -sourcepath ../../app/src/main/java eval/Eval.java
java -cp eval/out Eval out.bin 1500
```

Curated loanwords and generated verb phrases are assembled into `extra.json` by `build_extra.py`. To create the app asset, build the full language model and export it with `--ship --extra`:

```sh
python3 build_lm.py 60000
python3 export_model.py ../../app/src/main/assets/urdu/urdu_translit.bin --ship --extra
```
