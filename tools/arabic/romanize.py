"""Rule-based Arabic (diacritized) -> *typed* Roman spellings, several typing styles per word.

Why: no open dataset has typed romanization of Qur'an/Hadith vocabulary (Tanzil's scheme is scholarly, the Arabizi set is chat).
Fully diacritized text tells us every vowel, so we can render each word the ways people really type it:
    style 1 'eng'    english-religious   rasool, raheem, alaikum, kareem, quran
    style 2 'simple' plain letters       rasul, rahim, alaykum, karim, quran / qur'an
    style 3 'digits' chat arabizi        ra7eem, 3alaykum, 5alid, mo9tafa
    style 4 'skel'   vowel-dropping      rsl, rhmn  (short vowels dropped, initial kept)
    style 5 'schol'  scholarly-lite      rasuul->rasool, aa/ee/oo, ' for hamza/ayn
Each style also has a pausal form (final case vowel/tanwin dropped: rahman) and a full form (rahmanu); sun-letter articles come as
al+X and a+XX (alrahman / arrahman).  variants() returns {roman: weight}, weights sum to 1.
"""
import re, unicodedata
from collections import defaultdict

FATHA, DAMMA, KASRA, SHADDA, SUKUN = 'َ', 'ُ', 'ِ', 'ّ', 'ْ'
TANF, TAND, TANK, DAGGER = 'ً', 'ٌ', 'ٍ', 'ٰ'
VOW = {FATHA: 'a', DAMMA: 'u', KASRA: 'i'}
TAN = {TANF: 'an', TAND: 'un', TANK: 'in'}
SUN = set('تثدذرزسشصضطظلن')
HAMZA = set('ءأؤئإ')
CONS = set('بتثجحخدذرزسشصضطظعغفقكلمنهـ') | HAMZA
PREFIX = set('وفبكل')

# consonant -> roman per style index (0..4): eng, simple, digits, skel, schol ; None = silent
C = {
    'ب': 'b', 'ت': 't', 'ج': 'j', 'د': 'd', 'ر': 'r', 'ز': 'z', 'س': 's', 'ش': 'sh', 'ف': 'f', 'ك': 'k', 'ل': 'l', 'م': 'm', 'ن': 'n',
    'ه': 'h', 'و': 'w', 'ي': 'y', 'غ': 'gh', 'ق': 'q',
    'ث': 'th',
    'ح': ['h', 'h', '7', 'h', 'h', 'h'],
    'خ': ['kh', 'kh', '5', 'kh', 'kh', 'kh'],
    'ذ': ['dh', 'th', 'z', 'dh', 'dh', 'z'],
    'ص': ['s', 's', '9', 's', 's', 's'],
    'ض': ['d', 'd', 'd', 'd', 'dh', 'd'],
    'ط': ['t', 't', '6', 't', 't', 't'],
    'ظ': ['z', 'dh', 'z', 'z', 'dh', 'z'],
    'ع': ['', "'", '3', '', "'", ''],
}
HAMZA_MID = ['', "'", '2', '', '', '']
SHORT = [{'a': 'a', 'i': 'i', 'u': 'u'}, {'a': 'a', 'i': 'i', 'u': 'u'}, {'a': 'a', 'i': 'e', 'u': 'o'}, {'a': 'a', 'i': 'i', 'u': 'u'},
         {'a': 'a', 'i': 'i', 'u': 'u'}, {'a': 'a', 'i': 'e', 'u': 'o'}]
LONG_A, LONG_I, LONG_U = ['a', 'a', 'a', 'a', 'aa', 'a'], ['ee', 'i', 'ee', 'i', 'ee', 'i'], ['oo', 'u', 'oo', 'u', 'oo', 'ou']
AY, AW = ['i', 'y', 'y', 'y', 'y', 'ey'], ['u', 'w', 'w', 'w', 'w', 'o']   # glide part only: the preceding fatha supplies the 'a'
GLIDE_REPLACES_A = [False, False, False, False, False, True]   # casual: yawm -> yom, khayr -> kheyr (the 'a' is absorbed)
DROP_SHORT = [False, False, False, True, False, False]
STYLE_W = [0.26, 0.22, 0.10, 0.12, 0.15, 0.15]
DBL_W = (0.85, 0.15)               # shadda -> doubled letter is the norm (kull, rabb, muhammad); single (kul) is a minority habit
END_W = (0.65, 0.15, 0.20)      # pausal (rahmah), full (rahmatu), pausal with silent ta marbuta (rahma)


def parse(word):
    w = unicodedata.normalize('NFKC', word)
    units = []
    for ch in w:
        if ch == 'ٱ':
            ch = 'ا'
        if ch in VOW or ch in TAN or ch in (SHADDA, SUKUN, DAGGER):
            if not units: continue
            u = units[-1]
            if ch in VOW: u['v'] = VOW[ch]
            elif ch in TAN: u['tan'] = TAN[ch]
            elif ch == SHADDA: u['sh'] = True
            elif ch == SUKUN: u['suk'] = True
            else: u['dag'] = True
        elif 'ء' <= ch <= 'ي' or ch in 'ٱ':
            units.append({'c': ch, 'v': None, 'tan': None, 'sh': False, 'suk': False, 'dag': False})
        # everything else (Quranic marks, tatweel, punctuation) is ignored
    if units and units[0]['sh']: units[0]['sh'] = False        # initial shadda = Qur'anic idgham mark (مِّن), not typed
    # sunnah.com quirk: "لاَ" is encoded lam, alef, fatha  -> move the vowel back onto the lam
    for i in range(1, len(units)):
        a, c = units[i], units[i - 1]
        if a['c'] == 'ا' and a['v'] and c['c'] in CONS and c['v'] is None and not c['suk'] and not c['tan']:
            c['v'], a['v'] = a['v'], None
    return units


def _is_article(units, i):
    """alif at i followed by a vowel-less lam, at word start or after a one-letter prefix (wa-, bi-, li-, fa-, ka-)."""
    if i >= len(units) - 1 or units[i]['c'] != 'ا': return False
    lam = units[i + 1]
    if lam['c'] != 'ل' or lam['v'] or lam['tan']: return False
    return i == 0 or (i == 1 and units[0]['c'] in PREFIX)


def roles(units, nisba_single=False):
    """Per unit: cons | art | artlam | long_a | long_u | long_i | diph_y | diph_w | silent | init."""
    n, r = len(units), []
    for i, u in enumerate(units):
        c, v, prev = u['c'], u['v'], (units[i - 1] if i else None)
        pv = prev['v'] if prev else None
        if _is_article(units, i): r.append('art')
        elif i and r[i - 1] == 'art': r.append('artlam')
        elif c == 'ا':
            nx = units[i + 1] if i + 1 < n else None
            if nx and nx['c'] == 'ل' and nx['sh'] and (i == 0 or (i == 1 and units[0]['c'] in PREFIX)):
                r.append('init_a' if i == 0 else 'silent')      # الَّذِي / اللَّه : article alif + shadda-lam
            elif u['tan']: r.append('tan_alif')
            elif i == 0: r.append('init')
            elif prev['tan']: r.append('silent')
            elif prev['c'] == 'و' and r[i - 1] == 'long_u' and i == n - 1: r.append('silent')
            else: r.append('long_a')
        elif c == 'ى': r.append('long_a')
        elif c in 'وي':
            if c == 'ي' and nisba_single and u['sh'] and i == n - 1 and pv == 'i': r.append('long_i')    # النبيّ -> nabi / nabee
            elif v is not None or u['sh'] or u['tan'] or i == 0: r.append('cons')
            elif c == 'و':
                r.append('long_u' if pv == 'u' else 'diph_w' if pv == 'a' else 'long_u' if (pv is None and not prev['suk']) else 'cons')
            else:
                r.append('long_i' if pv == 'i' else 'diph_y' if pv == 'a' else 'long_i' if (pv is None and not prev['suk']) else 'cons')
        else: r.append('cons')
    return r


def render(units, style, mode, assim, word_plain, dbl=True):
    """mode: 0 pausal, 1 full, 2 pausal + silent ta marbuta."""
    out, n = [], len(units)
    sm, rl = SHORT[style], roles(units, nisba_single=not dbl)
    skip, force_double, no_double = set(), set(), set()
    last = max((i for i, u in enumerate(units) if rl[i] in ('cons', 'long_a', 'long_u', 'long_i') or u['c'] == 'ة'), default=-1)
    pausal = mode != 1
    for i, u in enumerate(units):
        if i in skip: continue
        c, v, role = u['c'], u['v'], rl[i]
        nxt_role = rl[i + 1] if i + 1 < n else None
        drop_final = pausal and i == last and (c != 'ه' or word_plain == 'الله')
        if role == 'art':
            skip.add(i + 1)
            nxt = units[i + 2] if i + 2 < n else None
            if assim == 'drop':                                        # rahman for الرحمن, quran for القرآن
                if nxt: no_double.add(i + 2)
                continue
            out.append('a' if i == 0 else '')
            if nxt and nxt['c'] in SUN and nxt['sh']:
                if assim: force_double.add(i + 2)
                else: out.append('l'); no_double.add(i + 2)
            else: out.append('l')
            continue
        if role == 'silent': continue
        if role == 'tan_alif': out.append('an'); continue
        if role == 'init_a': out.append('a'); continue
        if role == 'init': out.append(sm['a'] if v == 'a' else sm['u'] if v == 'u' else sm['i']); continue
        if role == 'long_a': out.append(LONG_A[style]); continue
        if role == 'long_u': out.append(LONG_U[style]); continue
        if role == 'long_i': out.append('y' if style == 5 and i == n - 1 else LONG_I[style]); continue
        if role == 'diph_y': out.append(AY[style]); continue
        if role == 'diph_w': out.append(AW[style]); continue
        if c == 'آ': out.append(('' if i == 0 else HAMZA_MID[style]) + LONG_A[style]); continue
        if c == 'ة':
            out.append('t' if mode == 1 else '' if mode == 2 else 'h')
            if v and not drop_final: out.append(sm[v])
            continue
        first = i == 0
        if c in HAMZA:
            ch = '' if first else HAMZA_MID[style]
            vw = v if v else ('i' if c == 'إ' else 'a') if first else None
        elif c in C:
            r = C[c]; ch = r[style] if isinstance(r, list) else r
            vw = v
        elif c in 'وي': ch, vw = ('w' if c == 'و' else 'y'), (v or ('a' if first else None))
        else: continue
        dd = (u['sh'] or i in force_double) and i not in no_double and dbl
        out.append(ch * 2 if dd and ch else ch)
        if u['dag']: out.append(LONG_A[style])
        elif vw and vw != 'x':
            long_next = (vw == 'a' and nxt_role == 'long_a') or (vw == 'u' and nxt_role == 'long_u') or (vw == 'i' and nxt_role == 'long_i') \
                or (GLIDE_REPLACES_A[style] and vw == 'a' and nxt_role in ('diph_y', 'diph_w'))
            glide_next = nxt_role in ('diph_y', 'diph_w')
            if not long_next and not drop_final and (glide_next or not (DROP_SHORT[style] and not first)):
                out.append(sm[vw])
        if u['tan'] and not drop_final: out.append(u['tan'])
        elif u['tan'] == 'an' and drop_final and style in (0, 1, 4): out.append('an')
    return ''.join(out)


def variants(diac):
    """-> {roman: weight} with weights summing to 1 (identical spellings from different styles are merged)."""
    units = parse(diac)
    if not units or len(units) > 14: return {}
    plain = ''.join(u['c'] for u in units)
    rl = roles(units)
    art = 'art' in rl
    sun_art = art and any(_is_article(units, k) and k + 2 < len(units) and units[k + 2]['c'] in SUN and units[k + 2]['sh'] for k in (0, 1))
    arts = ((False, 1.0),) if plain in ('الله', 'اللهم') else ((False, 0.4), (True, 0.4), ('drop', 0.2)) if sun_art else ((False, 0.8), ('drop', 0.2)) if art else ((False, 1.0),)
    dbls = (True, False) if any(u['sh'] for u in units) and plain not in ('الله', 'اللهم') else (True,)
    nisba = units[-1]['c'] == 'ي' and units[-1]['sh'] and len(units) > 1 and units[-2]['v'] == 'i'
    dw2 = (0.35, 0.65) if nisba else DBL_W              # nabiyy is rare; nabi / nabee is what people type
    out = defaultdict(float)
    for s in range(len(STYLE_W)):
        for mode, ew in enumerate(END_W):
            for assim, aw in arts:
                for dbl in dbls:
                    dw = dw2[0 if dbl else 1] if len(dbls) == 2 else 1.0
                    r = re.sub(r"(.)\1{2,}", r"\1\1", re.sub(r"'{2,}", "'", render(units, s, mode, assim, plain, dbl)))
                    if r and re.search(r'[a-z]', r): out[r] += STYLE_W[s] * ew * aw * dw
    t = sum(out.values())
    return {k: v / t for k, v in out.items()} if t else {}
