"""Shared Arabic text normalisation for the Lafz Arabic data pipeline."""
import re, unicodedata

# harakat, tanwin, shadda, sukun, dagger alif, Quranic annotation signs, small high marks
_MARKS = re.compile('[ؐ-ًؚ-ٰٟۖ-ۜ۟-۪ۨ-ۭ࣓-ࣿ]')
_TATWEEL = 'ـ'
_ARABIC_WORD = re.compile('^[ء-غف-يٱىة]+$')


def strip_marks(s: str) -> str:
    """Remove diacritics and tatweel; alef wasla -> bare alef; keeps hamza forms (أ إ آ ؤ ئ) as written."""
    s = unicodedata.normalize('NFKC', s)          # also fixes presentation forms such as the lam-alef ligature
    s = _MARKS.sub('', s.replace(_TATWEEL, ''))
    return s.replace('ٱ', 'ا')


def clean_ws(s: str) -> str:
    return re.sub(r'\s+', ' ', s).strip()


def is_arabic_word(w: str) -> bool:
    return bool(_ARABIC_WORD.match(strip_marks(w)))
