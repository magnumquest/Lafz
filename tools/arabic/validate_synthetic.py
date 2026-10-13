#!/usr/bin/env python3
"""How often does the generator reproduce spellings that real people typed?  python3 -I tools/arabic/validate_synthetic.py
Compares data/Arabic/pairs/pairs_synthetic.tsv against (a) the hand-written religious terms (b) the real chat-Arabizi pairs."""
import collections, itertools, os, re
ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..', 'data', 'Arabic', 'pairs')
syn = collections.defaultdict(dict)
for line in open(os.path.join(ROOT, 'pairs_synthetic.tsv'), encoding='utf-8').read().splitlines()[1:]:
    r, a, w, *_ = line.split('\t'); syn[a][r] = float(w)


def hit(pairs, label, show=0):
    n = ok = top3 = covered = 0; miss = []
    for r, a in pairs:
        if a not in syn: continue
        covered += 1
        ranked = sorted(syn[a], key=lambda x: -syn[a][x])
        ok += r in syn[a]; top3 += r in ranked[:3]
        if r not in syn[a]: miss.append((r, a, ranked[:3]))
    print(f'{label}: {covered} in-vocab pairs; exact spelling produced {ok / max(covered, 1):.1%}, within top-3 {top3 / max(covered, 1):.1%}')
    for m in miss[:show]: print('   miss', m)


cur = []
for line in open(os.path.join(ROOT, 'religious_terms_curated.tsv'), encoding='utf-8').read().splitlines()[1:]:
    a, rs = line.split('\t')
    for r in rs.split(','):
        cur.append((r.strip(), re.sub('[ً-ْ]', '', a)))
single = [(r, a) for r, a in cur if ' ' not in a]
hit(single, 'curated single-word terms', show=12)
# phrases: every typed token must be producible by the matching Arabic word, in order (typed with or without spaces)
ph = [(r, a) for r, a in cur if ' ' in a]; n = ok = 0
for r, a in ph:
    ws = a.split()
    if not all(w in syn for w in ws) or len(ws) > 4: continue
    n += 1
    pools = [sorted(syn[w], key=lambda x: -syn[w][x])[:6] for w in ws]
    gen = {sep.join(c) for c in itertools.product(*pools) for sep in ('', ' ')}
    ok += r in gen
print(f'curated phrases: {n} in-vocab; reproduced (top-6 per word, with/without spaces) {ok / max(n, 1):.1%}')
az = [tuple(l.split('\t')[:2]) for l in open(os.path.join(ROOT, 'arabizi_pairs.tsv'), encoding='utf-8').read().splitlines()[1:]]
az1 = [(r, a) for r, a in az if ' ' not in a]
hit([p for p in az1 if not re.search(r'\d', p[0])], 'chat Arabizi, no digits')
hit([p for p in az1 if re.search(r'\d', p[0])], 'chat Arabizi, with digits (3/7/5…)')
