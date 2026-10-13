#!/usr/bin/env python3
"""Held-out scripture typing test: work2/dev_hadith_typed.tsv  (typed phrase <TAB> expected Arabic)
Takes the hadith lines held out from every language-model count (count_ngrams2.py: every 50th line), and "types" 4-8 word windows of them the way
people do: one typing style per phrase, case endings dropped or kept, ' for ayn/hamza, some word pairs glued (the article loses its alif after a
vowel: innamal'amal), و/ف prefixes sometimes typed as a separate word.  Synthetic, so it tests the language model / glued-word decoding, not the
channel on genuinely human spellings (use eval/owner_phrases.tsv and work/dev_real.tsv for that).
    python3 -I make_dev_hadith.py [n=600]"""
import collections, os, random, re, sys
HERE = os.path.dirname(os.path.abspath(__file__)); sys.path.insert(0, HERE)
import romanize as R
from arnorm import strip_marks

ROOT = os.path.join(HERE, '..', '..', 'data', 'Arabic', 'corpus')
random.seed(5)
n = int(sys.argv[1]) if len(sys.argv) > 1 else 600


def style_variants(diac, style):
    units = R.parse(diac)
    if not units or len(units) > 14: return {}
    plain = ''.join(u['c'] for u in units)
    rl = R.roles(units)
    art = 'art' in rl
    sun_art = art and any(R._is_article(units, k) and k + 2 < len(units) and units[k + 2]['c'] in R.SUN and units[k + 2]['sh'] for k in (0, 1))
    arts = ((False, 1.0),) if plain in ('الله', 'اللهم') else ((False, 0.4), (True, 0.4), ('drop', 0.2)) if sun_art else ((False, 0.8), ('drop', 0.2)) if art else ((False, 1.0),)
    dbls = (True, False) if any(u['sh'] for u in units) and plain not in ('الله', 'اللهم') else (True,)
    out = collections.defaultdict(float)
    for mode, ew in enumerate((0.45, 0.35, 0.20)):
        for assim, aw in arts:
            for dbl in dbls:
                dw = (0.85, 0.15)[0 if dbl else 1] if len(dbls) == 2 else 1.0
                r = re.sub(r"(.)\1{2,}", r"\1\1", re.sub(r"'{2,}", "'", R.render(units, style, mode, assim, plain, dbl)))
                if r and re.search(r'[a-z]', r): out[r] += ew * aw * dw
    return out


def pick(d):
    ks = list(d); return random.choices(ks, [d[k] for k in ks])[0]


def main():
    diac = open(os.path.join(ROOT, 'hadith_matn_diac.txt'), encoding='utf-8').read().split('\n')
    lines = [l for i, l in enumerate(diac) if i % 50 == 7 and l.strip()]
    rows = []
    random.shuffle(lines)
    for l in lines:
        toks = l.split()
        if len(toks) < 5: continue
        k = random.randint(4, 8); a = random.randint(0, max(0, len(toks) - k)); win = toks[a:a + k]
        style = random.choices(range(6), [0.2, 0.3, 0.1, 0.0, 0.2, 0.2])[0]
        romans, ok = [], True
        for w in win:
            v = style_variants(w, style)
            if not v: ok = False; break
            romans.append(pick(v))
        if not ok: continue
        typed = romans[0]; cur = romans[0]
        for r, w in zip(romans[1:], win[1:]):
            glue = random.random() < 0.3 and len(cur) + len(r) <= 16
            if glue and strip_marks(w).startswith('ال') and re.search(r'[aeiou]$', typed) and r.startswith('al') and random.random() < 0.8: r = r[1:]
            if not glue and re.match(r'^wa[a-z\']', r) and strip_marks(w)[0] == 'و' and random.random() < 0.3: r = 'wa ' + r[2:]
            typed += ('' if glue else ' ') + r
            cur = cur + r if glue else r
        rows.append((typed, ' '.join(strip_marks(w) for w in win)))
        if len(rows) >= n: break
    os.makedirs(os.path.join(HERE, 'work2'), exist_ok=True)
    with open(os.path.join(HERE, 'work2', 'dev_hadith_typed.tsv'), 'w', encoding='utf-8') as f:
        for t, e in rows: f.write(f'{t}\t{e}\n')
    print(len(rows), 'phrases'); print('\n'.join(f'{t}   ->   {e}' for t, e in rows[:8]))


if __name__ == '__main__': main()
