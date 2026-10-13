#!/usr/bin/env python3
"""Generate typed-style roman spellings for the Qur'an/Hadith vocabulary (see romanize.py) -> data/Arabic/pairs.
   python3 -I tools/arabic/build_synthetic.py [vocab_size=30000]
Outputs
  pairs_synthetic.tsv  roman, arabic, weight (per-word weights sum to 1), word_count, arabic_diac, style_note
  pairs_train.tsv      merged training table: synthetic + curated + arabizi (+ optional Tanzil), columns roman, arabic, weight, source
"""
import collections, os, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from arnorm import strip_marks
from romanize import variants

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..', 'data', 'Arabic')
CORPUS, PAIRS = os.path.join(ROOT, 'corpus'), os.path.join(ROOT, 'pairs')
MAX_VARIANTS = 14
MIN_W = 0.004          # drop spellings rarer than this share of a word's mass, then renormalise
W_SYNTH, W_CURATED, W_ARABIZI = 1.0, 5.0, 1.0   # source weights used in pairs_train.tsv (per word / per curated pair / per chat pair)


def diac_forms():
    forms = collections.defaultdict(collections.Counter)         # plain -> {diac form: count}
    for fn in ('quran_diac.txt', 'hadith_matn_diac.txt'):
        for line in open(os.path.join(CORPUS, fn), encoding='utf-8'):
            for w in line.split():
                forms[strip_marks(w)][w] += 1
    return forms


def main():
    size = int(sys.argv[1]) if len(sys.argv) > 1 else 30000
    forms = diac_forms()
    ranked = sorted(forms, key=lambda p: -sum(forms[p].values()))
    tot = sum(sum(c.values()) for c in forms.values())
    keep = ranked[:size]
    cov = sum(sum(forms[p].values()) for p in keep) / tot
    rows, nvar = [], 0
    for p in keep:
        c = forms[p]; best = c.most_common(1)[0]
        use = [(f, n) for f, n in c.most_common(3) if n >= 0.25 * best[1] and any(ch in f for ch in 'َُِّْٰ')]
        if not use: continue
        mix, total = collections.defaultdict(float), sum(n for _, n in use)
        for f, n in use:
            for r, w in variants(f).items(): mix[r] += w * n / total
        mix = dict(sorted(((r, w) for r, w in mix.items() if w >= MIN_W), key=lambda kv: -kv[1])[:MAX_VARIANTS])
        z = sum(mix.values())
        if not z: continue
        for r, w in sorted(mix.items(), key=lambda kv: -kv[1]):
            rows.append((r, p, w / z, sum(c.values()), best[0])); nvar += 1
    with open(os.path.join(PAIRS, 'pairs_synthetic.tsv'), 'w', encoding='utf-8') as f:
        f.write('roman\tarabic\tweight\tword_count\tarabic_diac\n')
        for r, p, w, n, d in rows: f.write(f'{r}\t{p}\t{w:.4f}\t{n}\t{d}\n')
    print(f'synthetic: {len({r[1] for r in rows})} words (top {size} cover {cov:.1%} of tokens) -> {len(rows)} spellings, {len(rows) / len({r[1] for r in rows}):.1f}/word')
    # ---- merged training table
    with open(os.path.join(PAIRS, 'pairs_train.tsv'), 'w', encoding='utf-8') as out:
        out.write('roman\tarabic\tweight\tsource\n')
        for r, p, w, n, d in rows: out.write(f'{r}\t{p}\t{w * W_SYNTH:.4f}\tsynthetic\n')
        cur = 0
        for line in open(os.path.join(PAIRS, 'pairs_all.tsv'), encoding='utf-8').read().splitlines()[1:]:
            r, a, q, z, c = line.split('\t')
            if int(c): out.write(f'{r}\t{a}\t{W_CURATED}\tcurated\n'); cur += 1
            if int(z): out.write(f'{r}\t{a}\t{W_ARABIZI}\tarabizi\n')
    print('pairs_train.tsv written (synthetic + curated', cur, '+ arabizi). Tanzil romanization intentionally excluded (scholarly scheme).')


if __name__ == '__main__':
    main()
