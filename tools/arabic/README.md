# Arabic transliteration model

These tools build the offline Arabic transliteration asset at `app/src/main/assets/arabic/arabic_translit.bin`. The app uses the model to suggest Arabic-script words while the user types Arabic in Latin letters.

## Source data

Training data is kept outside this repository. The build expects an `Arabic/` data directory containing the source material described by `prep_data.py`, including romanized examples, Arabic text and curated vocabulary. Set `DATA` to that directory before running the preparation scripts.

The generated model and channel data are included in the app; the original training bundle is not. Check the terms and attribution requirements of each source before adding or redistributing training material.

## Build

Install the Python dependency and prepare the training files:

```sh
python3 -m pip install wordfreq
export DATA=/path/to/Arabic
python3 prep_data.py
python3 train_channel.py work/train_pairs.tsv 5
python3 build_model.py --channel channel_trained.json --variants work/real_all.tsv
```

`build_model.py` writes the model asset. For local evaluation, pass the held-out variants and an output path as described by `python3 build_model.py --help`, then run the Java helpers under `eval/`.
