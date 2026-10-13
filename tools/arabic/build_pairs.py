#!/usr/bin/env python3
"""Build roman<->Arabic pair datasets in data/Arabic/pairs (run fetch_data.py first).
   python3 -I tools/arabic/build_pairs.py
Outputs (TSV, UTF-8, header row):
  quran_word_tokens.tsv   every aligned Quran word occurrence (ref, roman_raw, roman, arabic, arabic_diac)
  quran_verse_pairs.tsv   verse-level roman/Arabic (for sentence-level eval, like Dakshina sentences)
  arabizi_pairs.tsv       cleaned chat-Arabizi word/phrase pairs (data/ source dataset)
  pairs_all.tsv           merged unique (roman, arabic) with counts and per-source columns
"""
import collections, os, re, sys, unicodedata
import pandas as pd
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from arnorm import strip_marks, clean_ws, is_arabic_word

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..', 'data')
RAW, OUT = os.path.join(ROOT, 'Arabic', 'raw'), os.path.join(ROOT, 'Arabic', 'pairs')
ARABIZI = os.path.join(ROOT, 'train-00000-of-00001-600f00d132ab2179.parquet')
BASMALA = 'بسم الله الرحمن الرحيم'


def load_tanzil(fn, strip_html):
    d = {}
    for line in open(os.path.join(RAW, fn), encoding='utf-8'):
        p = line.rstrip('\n').split('|')
        if len(p) == 3 and p[0].isdigit():
            t = re.sub(r'<[^>]+>', '', p[2]) if strip_html else p[2]
            d[(int(p[0]), int(p[1]))] = clean_ws(t)
    return d


def strip_basmala(sura, aya, words):
    """Tanzil prefixes the basmala to ayah 1 of every sura except 1 and 9 in the Arabic file only."""
    if aya == 1 and sura not in (1, 9) and len(words) > 4 and strip_marks(' '.join(words[:4])) == BASMALA:
        return words[4:]
    return words


def quran():
    ar = load_tanzil('tanzil_quran_simple.txt', False)
    tr = load_tanzil('tanzil_transliteration_en.txt', True)
    toks, verses, skipped = [], [], []
    for (s, a), text in sorted(ar.items()):
        aw, rw = strip_basmala(s, a, text.split()), tr[(s, a)].split()
        if len(aw) != len(rw):
            skipped.append((s, a, len(aw), len(rw))); continue
        for i, (x, r) in enumerate(zip(aw, rw), 1):
            toks.append((f'{s}:{a}:{i}', r, r.lower(), strip_marks(x), x))
        verses.append((f'{s}:{a}', ' '.join(rw).lower(), strip_marks(' '.join(aw)), ' '.join(aw)))
    with open(os.path.join(OUT, 'quran_word_tokens.tsv'), 'w', encoding='utf-8') as f:
        f.write('ref\troman_raw\troman\tarabic\tarabic_diac\n')
        for t in toks: f.write('\t'.join(t) + '\n')
    with open(os.path.join(OUT, 'quran_verse_pairs.tsv'), 'w', encoding='utf-8') as f:
        f.write('ref\troman\tarabic\tarabic_diac\n')
        for t in verses: f.write('\t'.join(t) + '\n')
    print(f'quran: {len(toks)} word tokens, {len(verses)} verses aligned, {len(skipped)} verses skipped (word-count mismatch): {skipped[:6]}')
    return collections.Counter((t[2], t[3]) for t in toks), {}


def arabizi():
    df = pd.read_parquet(ARABIZI)
    rows, drop = [], collections.Counter()
    seen = set()
    for rom, ara, chk in zip(df.Arabize, df.Arabic, df.checked_word):
        if not isinstance(rom, str) or not isinstance(ara, str): drop['empty'] += 1; continue
        r = clean_ws(rom.lower().replace('′', "'").replace('’', "'").replace('`', "'"))
        a_diac = clean_ws(ara)
        a = strip_marks(a_diac)
        if re.search(r'\d{4}-\d\d-\d\d', r): drop['excel date'] += 1; continue
        if not r or not re.fullmatch(r"[a-z0-9'$ ]+", r): drop['bad roman chars'] += 1; continue
        if not a or not all(is_arabic_word(w) for w in a.split()): drop['bad arabic chars'] += 1; continue
        if re.fullmatch(r'[0-9 ]+', r): drop['digits only'] += 1; continue
        if (r, a) in seen: drop['duplicate'] += 1; continue
        seen.add((r, a))
        rows.append((r, a, a_diac, str(len(a.split())), '1' if str(chk).strip() == '1' else '0'))
    with open(os.path.join(OUT, 'arabizi_pairs.tsv'), 'w', encoding='utf-8') as f:
        f.write('roman\tarabic\tarabic_diac\tn_words\tchecked\n')
        for t in rows: f.write('\t'.join(t) + '\n')
    print(f'arabizi: {len(df)} raw -> {len(rows)} clean; dropped {dict(drop)}; multi-word {sum(1 for r in rows if r[3] != "1")}')
    return rows


def curated():
    rows = []
    for line in open(os.path.join(OUT, 'religious_terms_curated.tsv'), encoding='utf-8').read().splitlines()[1:]:
        a, rs = line.split('\t')
        for r in dict.fromkeys(x.strip() for x in rs.split(',') if x.strip()):
            rows.append((r, strip_marks(a)))
    return rows


def main():
    os.makedirs(OUT, exist_ok=True)
    q, _ = quran()
    az = arabizi()
    allp = collections.defaultdict(lambda: [0, 0, 0])   # (roman, arabic) -> [quran, arabizi, curated]
    for k, n in q.items(): allp[k][0] += n
    for r, a, *_ in az: allp[(r, a)][1] += 1
    cur = curated()
    for r, a in cur: allp[(r, a)][2] += 1
    print('curated religious terms:', len(cur), 'pairs')
    with open(os.path.join(OUT, 'pairs_all.tsv'), 'w', encoding='utf-8') as f:
        f.write('roman\tarabic\tquran_count\tarabizi_count\tcurated_count\n')
        for (r, a), (c1, c2, c3) in sorted(allp.items(), key=lambda kv: -sum(kv[1])):
            f.write(f'{r}\t{a}\t{c1}\t{c2}\t{c3}\n')
    print('pairs_all:', len(allp), 'unique (roman, arabic) pairs;',
          'arabic types:', len({a for _, a in allp}), 'roman types:', len({r for r, _ in allp}))


if __name__ == '__main__':
    main()
