"""Arabic letter -> Roman chunk channel model P(roman | arabic) written as channel.json (same format as tools/urdu).

IMPORTANT: this is a *prior written from the documented Arabizi / Franco-Arab chat conventions*, NOT a model learned from
human-written romanizations (no such corpus was reachable when this was built). It is only the starting point for
`train_channel.py`, which re-estimates it with EM as soon as an `arabizi_pairs.tsv` (arabic <TAB> roman <TAB> count) exists.

Conventions encoded (see e.g. Darwish 2014 "Arabizi detection and conversion to Arabic"; Yaghan 2008 "Arabizi"):
  digits that stand for letters with no Latin equivalent:  2 = ء/أ/ق   3 = ع   5 = خ   6 = ط   7 = ح   8 = غ   9 = ص/ق  (4 = ذ/ظ rare)
  digraphs:  sh = ش   kh = خ   gh = غ   th = ث/ذ   dh = ذ/ض/ظ   ch = ش (Maghreb) / چ
  short vowels are not written in Arabic script, so a consonant may or may not be followed by a/e/i/o/u in Roman text;
  long vowels ا و ي are written as a/aa, o/u/oo/ou/w, i/ee/e/y; ة and final ه are a/ah/e/eh/h/t; doubled letters (shadda) are often doubled.
"""
import json, collections, sys

POS = 'IMF'

# vowel add-ons that may follow a consonant (short vowel that Arabic script leaves out), weight by word position
VOWELS = {
    'I': {'': 5.0, 'a': 2.4, 'i': 1.6, 'e': 1.6, 'o': 1.0, 'u': 1.0, 'aa': 0.35, 'ee': 0.25, 'oo': 0.25, 'ou': 0.25, 'ai': 0.2, 'ay': 0.2, 'ei': 0.15, 'ea': 0.1, 'ia': 0.1, 'io': 0.1},
    'M': {'': 6.0, 'a': 2.2, 'i': 1.5, 'e': 1.3, 'o': 0.8, 'u': 0.8, 'aa': 0.2, 'ee': 0.2, 'oo': 0.2, 'ou': 0.15, 'ai': 0.15, 'ay': 0.15, 'ei': 0.1, 'ea': 0.1, 'ia': 0.08, 'io': 0.08},
    'F': {'': 14.0, 'a': 0.7, 'i': 0.6, 'e': 0.6, 'o': 0.3, 'u': 0.3, 'ah': 0.2, 'eh': 0.2},
}
DOUBLE = 0.45     # weight of a doubled consonant (shadda written as "bb")

# consonants: base chunk -> weight (before vowel add-ons)
CONS = {
    'ب': {'b': 10, 'p': 1.0},
    'ت': {'t': 10},
    'ث': {'th': 7, 's': 3, 't': 2.5},
    'ج': {'j': 10, 'g': 2.5, 'dj': 1.5, 'zh': 0.2},
    'ح': {'7': 10, 'h': 8, 'hh': 0.8},
    'خ': {'kh': 9, '5': 8, 'x': 1.0, 'h': 0.4},
    'د': {'d': 10},
    'ذ': {'th': 4, 'dh': 4, 'z': 4, 'd': 2.2},
    'ر': {'r': 10},
    'ز': {'z': 10, 's': 0.3},
    'س': {'s': 10, 'c': 0.5},
    'ش': {'sh': 10, 'ch': 2.0, 'sch': 0.2},
    'ص': {'s': 6, '9': 5, 'c': 0.2},
    'ض': {'d': 6, 'dh': 2, 'z': 1.5, '9': 0.8},
    'ط': {'6': 8, 't': 8, 'd': 0.2},
    'ظ': {'z': 6, 'dh': 2, 'th': 1.5, '6': 1.0, 'd': 0.8},
    'غ': {'gh': 9, '8': 6, 'g': 3, 'r': 0.6},
    'ف': {'f': 10, 'v': 1.0, 'ph': 0.3},
    'ق': {'q': 8, '2': 4, 'g': 3, 'k': 2, '9': 2.5},
    'ك': {'k': 10, 'c': 1.5, 'ch': 0.8},
    'ل': {'l': 10},
    'م': {'m': 10},
    'ن': {'n': 10},
}

def add_vowels(bases, pos, vscale=1.0):
    out = collections.defaultdict(float)
    for b, w in bases.items():
        for v, vw in VOWELS[pos].items():
            out[b + v] += w * vw * (vscale if v else 1.0)
        # doubled (shadda): "bb", "bba"
        for v, vw in VOWELS[pos].items():
            if v in ('', 'a', 'i', 'e', 'o', 'u'):
                out[b + b + v] += w * vw * DOUBLE * 0.35 if len(b) == 1 else 0
    return out

# letters that are (also) vowels: explicit chunk weights per position  I / M / F
# where "{v}" entries marked in CONSLIKE are expanded as consonants with vowel add-ons.
VOWELISH = {
    'ا': {
        'I': {'a': 5, 'e': 2, 'i': 1.5, 'o': 1.0, 'u': 1.0, 'aa': 2, 'ei': 0.2, 'ou': 0.2, '': 0.8},
        'M': {'a': 6, 'aa': 3, '': 1.6, 'e': 0.5, 'o': 0.3, 'i': 0.3, 'ah': 0.3, 'ae': 0.2},
        'F': {'a': 6, 'aa': 2, '': 1.6, 'ah': 0.4, 'e': 0.3, 'ae': 0.1},
    },
    'أ': {
        'I': {'a': 6, 'e': 1.5, 'i': 1.0, 'o': 1.0, 'u': 1.0, '2a': 0.3, 'aa': 0.6, '2': 0.2, 'ou': 0.2, '': 0.3},
        'M': {'a': 3, '2a': 0.4, '2': 0.6, '': 1.8, 'e': 0.3, "'": 0.3, 'aa': 0.4, 'o': 0.2, 'u': 0.2},
        'F': {'a': 3, '': 1.6, '2': 0.3, "'": 0.2, 'e': 0.1},
    },
    'إ': {
        'I': {'i': 5, 'e': 4, 'a': 1.2, '2i': 0.25, '2e': 0.15, 'u': 0.3, '': 0.3, 'ee': 0.4},
        'M': {'i': 3, 'e': 2, '': 1.5, '2i': 0.3, 'a': 0.5},
        'F': {'i': 2, 'e': 1, '': 1.0},
    },
    'آ': {
        'I': {'aa': 5, 'a': 4, '2a': 0.4, '2aa': 0.2, 'e': 0.3, '': 0.1},
        'M': {'aa': 4, 'a': 3, '2a': 0.5, '': 0.3, '2aa': 0.2},
        'F': {'aa': 3, 'a': 3, '': 0.3},
    },
    'ء': {p: {'': 5, '2': 2, 'a': 1, 'e': 0.8, "'": 0.8, 'i': 0.3, 'u': 0.3, 'o': 0.2, '2a': 0.2} for p in POS},
    'ئ': {p: {'e': 3, 'i': 3, 'y': 2, '2': 1, '': 2, 'a': 1, "'": 0.5, 'ei': 0.4, 'ye': 0.3, 'yi': 0.3} for p in POS},
    'ؤ': {p: {'o': 3, 'u': 3, 'w': 2, '2': 1, '': 2, 'oo': 1, "'": 0.3, 'ou': 0.3} for p in POS},
    'ى': {
        'I': {'a': 3, 'e': 2, 'i': 2, 'y': 1},
        'M': {'a': 5, 'aa': 2, 'y': 2, 'i': 2, 'e': 2, 'ee': 1, '': 1, 'ay': 0.8, 'ai': 0.5},
        'F': {'a': 6, 'aa': 2, 'y': 2, 'i': 2, 'e': 2, 'ee': 1, '': 1, 'ay': 0.8, 'ai': 0.5, 'ah': 0.3},
    },
    'ي': {
        'I': {'y': 6, 'ya': 3, 'ye': 1.5, 'yi': 1.0, 'yu': 0.5, 'yo': 0.5, 'i': 1.5, 'e': 1.0, 'ee': 1.0, 'ia': 0.2, 'ii': 0.2, 'yaa': 0.3, 'ey': 0.1},
        'M': {'i': 6, 'ee': 4, 'e': 3, 'y': 3, 'ay': 1.5, 'ai': 1.5, 'ei': 1.0, 'ie': 0.7, 'ii': 0.7, 'ya': 1.0, 'yi': 0.3, 'ye': 0.3, '': 1.0, 'ia': 0.3, 'iya': 0.3, 'aya': 0.2, 'iy': 0.5, 'ey': 0.5, 'yy': 1.5, 'yya': 1.4, 'yyi': 0.4, 'yye': 0.3, 'iyy': 0.3, 'iyya': 0.5},
        'F': {'i': 5, 'y': 5, 'ee': 3, 'e': 2, 'ie': 0.5, 'iy': 0.5, 'a': 1.0, 'ya': 0.3, '': 0.3, 'ei': 0.2, 'ey': 0.5, 'ay': 1.0, 'ii': 0.5, 'ai': 0.4, 'yi': 0.1},
    },
    'و': {
        'I': {'w': 6, 'wa': 4, 'wi': 2, 'we': 2, 'wo': 1.5, 'wu': 1.0, 'o': 1.0, 'u': 1.0, 'oo': 0.5, 'v': 0.6, 'ou': 0.3, 'woo': 0.5, 'wee': 0.3},
        'M': {'o': 5, 'u': 4, 'oo': 4, 'w': 4, 'ou': 3, 'aw': 1.5, 'au': 1.5, 'wa': 2, 'wi': 0.5, 'we': 0.5, 'wo': 0.5, 'v': 0.8, 'ow': 1.0, 'ua': 0.5, '': 0.8, 'uu': 0.5, 'wu': 0.3, 'oa': 0.2, 'ew': 0.2, 'iw': 0.2, 'ww': 0.8, 'wwa': 0.5, 'wwi': 0.2},
        'F': {'o': 5, 'u': 5, 'oo': 3, 'w': 3, 'ou': 1.0, 'aw': 1.0, 'au': 0.5, 'ow': 0.7, 'wa': 0.3, 'wo': 0.3, '': 0.3, 'v': 0.2, 'uh': 0.2, 'oh': 0.2, 'ew': 0.3},
    },
    'ه': {
        'I': {'h': 6, 'ha': 2, 'hi': 1.5, 'he': 1.5, 'ho': 1.0, 'hu': 1.0, 'haa': 0.4, 'hee': 0.3},
        'M': {'h': 6, 'ha': 2, 'hi': 1.5, 'he': 1.2, 'hu': 0.5, 'ho': 0.5, '': 0.3, 'haa': 0.2, 'hee': 0.2, 'hh': 0.3},
        'F': {'h': 4, 'a': 4, 'ah': 3, 'e': 3, 'eh': 2, 'o': 1.2, 'oh': 1.5, 'u': 0.8, 'uh': 0.5, 'i': 0.5, '': 3, 'hu': 0.3, 'ha': 0.3, 'hi': 0.2, 'hh': 0.1},
    },
    'ة': {
        'I': {'t': 6, 'ta': 3, 'ti': 1.5, 'te': 1.5, 'tu': 0.7, 'a': 1.0, 'ah': 0.3, 'h': 0.2},
        'M': {'t': 6, 'ta': 3, 'ti': 1.5, 'te': 1.5, 'tu': 0.7, 'a': 1.0, 'ah': 0.3, 'h': 0.2},
        'F': {'a': 6, 'ah': 3, 'e': 3, 'eh': 2, 'at': 3, 't': 2, 'et': 1.5, 'h': 2, 'i': 0.5, 'it': 0.5, '': 2, 'ta': 0.5, 'te': 0.3, 'ati': 0.2, 'o': 0.2},
    },
    # ʿayn: 3 and a/e/i/o/u, optionally apostrophe, optionally silent
    'ع': {
        'I': {'3': 9, '3a': 6, '3e': 3, '3i': 3, '3o': 2.5, '3u': 2.5, '3aa': 1.5, '3ee': 0.8, '3oo': 0.8, 'a': 5, 'e': 3, 'i': 2.5, 'o': 2.5, 'u': 2, 'aa': 1.0, "'": 2, "'a": 1.2, "'e": 0.6, "'i": 0.6, "'o": 0.5, "'u": 0.5, 'ae': 0.3, 'ai': 0.3, 'ou': 0.3, 'ee': 0.3, 'oo': 0.3, '': 1.0},
        'M': {'3': 9, '3a': 6, '3e': 3, '3i': 3, '3o': 2.5, '3u': 2.5, '3aa': 1.5, '3ee': 0.8, '3oo': 0.8, 'a': 5, 'e': 3, 'i': 2.5, 'o': 2.5, 'u': 2, 'aa': 1.0, "'": 2, "'a": 1.2, "'e": 0.6, "'i": 0.6, "'o": 0.5, "'u": 0.5, '': 2.0, 'ae': 0.3, 'ai': 0.3},
        'F': {'3': 9, '3a': 1.0, '3e': 0.5, '3i': 0.5, 'a': 3, 'e': 2, 'i': 1.5, 'o': 1.0, 'u': 1.0, "'": 2, '': 2.0},
    },
}

def build():
    th = {}
    for c, bases in CONS.items():
        for p in POS:
            th[c + p] = dict(add_vowels(bases, p))
    for c, byp in VOWELISH.items():
        for p in POS:
            th[c + p] = {k: float(v) for k, v in byp[p].items() if v > 0}
    # normalise, prune tiny chunks, cap the number of chunks per key (decoder speed)
    out = {}
    for k, d in th.items():
        s = sum(d.values())
        d = {ch: v / s for ch, v in d.items() if v / s >= 0.0015}
        top = sorted(d.items(), key=lambda x: -x[1])[:48]
        s = sum(v for _, v in top)
        out[k] = {ch: v / s for ch, v in top}
    return out

if __name__ == '__main__':
    out = build()
    json.dump(out, open('channel.json', 'w'), ensure_ascii=False)
    print('keys', len(out), 'chunks', sum(len(d) for d in out.values()), 'max/key', max(len(d) for d in out.values()))
