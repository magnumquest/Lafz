"""Shared helpers for the Roman->Urdu transliteration data pipeline."""
import gzip, os, re, unicodedata

DAK = os.environ.get("DAKSHINA", "/tmp/claude-0/-home-user-PersonalBoard/22c441e2-eccf-525f-a05e-87ef534c941a/scratchpad/dak/x/dakshina_dataset_v1.0/ur")

_DIACRITICS = re.compile("[ً-ٰٟۖ-ۭ‌‍‎‏ـ]")
_MAP = str.maketrans({"ي": "ی", "ى": "ی", "ك": "ک", "ۃ": "ہ", "ۂ": "ہ", "ۓ": "ے", "ﷲ": "اللہ"})
URDU_WORD = re.compile("[ء-غف-يٹ-ۓؠ]+")

def norm(w):
    w = unicodedata.normalize("NFC", w)
    w = _DIACRITICS.sub("", w).translate(_MAP)
    return w

def tokens(line):
    return [norm(m) for m in URDU_WORD.findall(line)]

def native_lines():
    p = f"{DAK}/native_script_wikipedia/ur.wiki-filt.train.text.shuf.txt.gz"
    with gzip.open(p, "rt", encoding="utf-8") as f:
        for l in f:
            yield l

def lexicon(split="train"):
    with open(f"{DAK}/lexicons/ur.translit.sampled.{split}.tsv", encoding="utf-8") as f:
        for l in f:
            p = l.rstrip("\n").split("\t")
            if len(p) >= 3:
                yield norm(p[0]), p[1].lower(), int(p[2])

def romanized_sentences():
    with open(f"{DAK}/romanized/ur.romanized.rejoined.tsv", encoding="utf-8") as f:
        for l in f:
            p = l.rstrip("\n").split("\t")
            if len(p) >= 2:
                yield p[0], p[1]
