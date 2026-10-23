# Bundled dictionaries

## Urdu

`app/src/main/assets/dicts/main_ur.dict` is a native-script Urdu dictionary bundled separately from Lafz's Roman Urdu decoder. It is based on the [AOSP dictionaries copy](https://codeberg.org/Helium314/aosp-dictionaries/raw/branch/main/dictionaries/main_ur.dict), sourced from the [IndicProject Urdu word list](https://gitlab.com/indicproject/dictionaries/-/blob/master/ur_wordlist.combined). The source word list and its license are retained here as `ur_wordlist.combined.gz` and `IndicProject-GPL-2.0.txt`.

The dictionary is licensed under GPLv2, as indicated by the [AOSP source notice](https://codeberg.org/Helium314/aosp-dictionaries/raw/branch/main/wordlists/main_ur.source). SHA-256 of the bundled binary:

```
11c43687913663c4f3bb5915085067358a18c9b8f0fd8759a191bd55028983c7
```

The existing native Urdu (`ur-PK`) layout can use this dictionary. It does not replace or retrain the separate Roman Urdu (`ur-Latn`) decoder or its model, and no improvement to Roman Urdu transliteration accuracy is claimed. The English (US) dictionary, `app/src/main/assets/dicts/main_en-US.dict`, is already bundled.
