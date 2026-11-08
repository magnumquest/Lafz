# Bundled dictionaries

## Urdu

`app/src/main/assets/dicts/main_ur.dict` is a native-script Urdu dictionary bundled separately from Lafz's Roman Urdu decoder. It is based on the [AOSP dictionaries copy](https://codeberg.org/Helium314/aosp-dictionaries/raw/branch/main/dictionaries/main_ur.dict), sourced from the [IndicProject Urdu word list](https://gitlab.com/indicproject/dictionaries/-/blob/master/ur_wordlist.combined). The source word list and its license are retained here as `ur_wordlist.combined.gz` and `IndicProject-GPL-2.0.txt`.

The dictionary is licensed under GPLv2, as indicated by the [AOSP source notice](https://codeberg.org/Helium314/aosp-dictionaries/raw/branch/main/wordlists/main_ur.source). SHA-256 of the bundled binary:

```
11c43687913663c4f3bb5915085067358a18c9b8f0fd8759a191bd55028983c7
```

The existing native Urdu (`ur-PK`) layout can use this dictionary. It does not replace or retrain the separate Roman Urdu (`ur-Latn`) decoder or its model, and no improvement to Roman Urdu transliteration accuracy is claimed. The English (US) dictionary, `app/src/main/assets/dicts/main_en-US.dict`, is already bundled.

## STEM completions

Lafz also ships a small low-priority technical completion layer in `app/src/main/assets/technical_words/`. It supplements native English and Urdu typing without altering the bundled conversational dictionaries, their n-grams, or autocorrection. At most one matching technical term is shown after the typed word and the best ordinary suggestion. English entries are letter-starting preferred MeSH descriptors from the 2026 NLM dataset, plus the project-curated engineering and physics list. The generator excludes numbered chemical names such as `1-2-dichloro…`. Regenerate the English file with `python tools/technical-terms/build_english.py`; NLM requests clear source attribution and identification of the MeSH year in redistributed data.

The Urdu file is a project-curated starter list of common scientific, clinical, and engineering spellings, including frequently used Urdu transliterations. It is intentionally kept separate from the Roman Urdu transliteration model. Expand it based on usage and native-speaker review rather than adding large unreviewed glossaries.

Sources: [NLM MeSH data download](https://www.nlm.nih.gov/databases/download/mesh.html), [MeSH terms and conditions](https://www.nlm.nih.gov/databases/download/terms_and_conditions_mesh.html).
