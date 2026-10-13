#!/usr/bin/env python3
"""Build running-text corpora for next-word modelling in data/Arabic/corpus (run fetch_data.py first).
   python3 -I tools/arabic/build_corpus.py [--full]     (--full also writes hadith_{diac,plain}.txt incl. narrator chains, ~70 MB)
One sentence/verse per line.  *_diac = with harakat as published, *_plain = marks stripped (what a keyboard user types).
  quran_*          Tanzil simple spelling, basmala prefix removed from ayah 1 (except Fatiha)
  hadith_*         (only with --full) all 17 books incl. narrator chains, exact-duplicate hadith removed
  hadith_matn_*    only the quoted words of the Prophet / main text where a quotation exists (drops most isnad chains)
  vocab_freq.tsv   plain word counts per source
"""
import collections, glob, json, os, re, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from arnorm import strip_marks, clean_ws, is_arabic_word
from build_pairs import load_tanzil, strip_basmala

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..', 'data', 'Arabic')
RAW, OUT = os.path.join(ROOT, 'raw'), os.path.join(ROOT, 'corpus')
SPLIT = re.compile(r'[.؟!]+')                          # sentence-ish boundaries inside a hadith
PUNCT = re.compile(r'[،؛:,;"“”«»()\[\]{}*<>/\\|…\-–—_=+~^ـ]|‏|‎')


def tidy(s):
    return clean_ws(PUNCT.sub(' ', s))


# End-of-chain markers (plain spelling). The matn starts after the LAST one found in the first MAX_ISNAD words.
CHAIN_END = [re.compile(x) for x in (
    r'(?:ان|انه) (?:رسول الله|النبي)', r'(?:قال|يقول|قالت) (?:رسول الله|النبي)', r'(?:سمعت|سمع) (?:رسول الله|النبي)',
    r'عن (?:رسول الله|النبي)', r'(?:رايت|راي|ان) (?:رسول الله|النبي)', r'(?:قال|يقول|قالت)(?= )')]
RESALA = ('صلى الله عليه وسلم', 'ﷺ')
ISNAD_START = ('حدثنا', 'اخبرنا', 'حدثني', 'اخبرني', 'عن', 'انبانا', 'حدثناه', 'اخبرناه', 'وحدثنا', 'وحدثني', 'واخبرنا', 'وحدثناه')
MAX_ISNAD = 45


def fold(w):
    return strip_marks(w).translate(str.maketrans('أإآ', 'ااا'))


def strip_isnad(text):
    """Return the text after the narrator chain (or the whole text if it has no chain); None if unsure."""
    dw = text.split(); pw = [fold(w) for w in dw]
    if pw[0] not in ISNAD_START: return text
    head = ' '.join(pw[:MAX_ISNAD]); cut = None
    for rx in CHAIN_END[:5]:
        for m in rx.finditer(head): cut = max(cut or 0, head[:m.end()].count(' ') + 1)
    if cut is None: return None
    rest = pw[cut:]
    if ' '.join(rest[:4]) == RESALA[0]: cut += 4
    elif rest and rest[0] in ('صلى',) : cut = None
    if cut is None or len(dw) - cut < 3: return None
    return ' '.join(dw[cut:])


def write(name, lines):
    with open(os.path.join(OUT, name), 'w', encoding='utf-8') as f:
        f.write('\n'.join(lines) + '\n')


def pair_files(prefix, diac_lines):
    plain = [strip_marks(l) for l in diac_lines]
    write(f'{prefix}_diac.txt', diac_lines); write(f'{prefix}_plain.txt', plain)
    return plain


FULL = '--full' in sys.argv


def main():
    os.makedirs(OUT, exist_ok=True)
    vocab = collections.defaultdict(collections.Counter)
    # ---- Quran
    ar = load_tanzil('tanzil_quran_simple.txt', False)
    q = []
    for (s, a), t in sorted(ar.items()):
        w = strip_basmala(s, a, t.split())
        q.append(' '.join(w))
    qp = pair_files('quran', q)
    for l in qp: vocab['quran'].update(l.split())
    # ---- Hadith
    seen, had, matn, n_in, n_dup, n_bad = set(), [], [], 0, 0, 0
    for fn in sorted(glob.glob(os.path.join(RAW, 'hadith_json', '*.json'))):
        for h in json.load(open(fn, encoding='utf-8'))['hadiths']:
            n_in += 1
            text = (h.get('arabic') or '').replace('\n', ' ')   # source wraps lines mid-sentence
            plain = strip_marks(tidy(text))
            words = plain.split()
            if len(words) < 3 or sum(is_arabic_word(w) for w in words) < 0.9 * len(words): n_bad += 1; continue
            if plain in seen: n_dup += 1; continue
            seen.add(plain)
            quoted = re.findall(r'"([^"]+)"', text)       # sunnah.com puts the Prophetic text in straight quotes
            for part in SPLIT.split(text):
                part = tidy(part)
                if len(part.split()) >= 2: had.append(part)
            body = ' '.join(quoted) if quoted else strip_isnad(tidy(text))
            if body is None: continue
            for part in SPLIT.split(body) if quoted else [body]:
                part = tidy(part)
                if len(part.split()) >= 2: matn.append(part)
    hp = pair_files('hadith', had) if FULL else [strip_marks(l) for l in had]     # full text (with isnad chains) only on request
    mp = pair_files('hadith_matn', matn)
    for l in hp: vocab['hadith'].update(l.split())
    for l in mp: vocab['hadith_matn'].update(l.split())
    allw = set(vocab['quran']) | set(vocab['hadith'])
    with open(os.path.join(OUT, 'vocab_freq.tsv'), 'w', encoding='utf-8') as f:
        f.write('word\tquran\thadith\thadith_matn\n')
        for w in sorted(allw, key=lambda w: -(vocab['quran'][w] + vocab['hadith'][w])):
            f.write(f"{w}\t{vocab['quran'][w]}\t{vocab['hadith'][w]}\t{vocab['hadith_matn'][w]}\n")
    tq, th, tm = (sum(vocab[k].values()) for k in ('quran', 'hadith', 'hadith_matn'))
    print(f'hadith in={n_in} bad={n_bad} duplicates={n_dup} kept={len(seen)}')
    print(f'quran: {len(q)} lines, {tq} tokens, {len(vocab["quran"])} types')
    print(f'hadith: {len(had)} lines, {th} tokens, {len(vocab["hadith"])} types; matn-only: {len(matn)} lines, {tm} tokens')
    print(f'combined vocab: {len(allw)} types; quran types also in hadith: {len(set(vocab["quran"]) & set(vocab["hadith"]))}/{len(vocab["quran"])}')
    top = sum(c for w, c in vocab['hadith'].most_common(60000)); print('hadith coverage by top-60k:', round(top / th, 4))


if __name__ == '__main__':
    main()
