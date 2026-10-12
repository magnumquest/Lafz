"""Pack channel model + vocab + LM + observed romanizations into one compact binary (big-endian).
usage: export_model.py OUT.bin [--ship]   (--ship also uses dev/test lexicon splits)"""
import sys, json, pickle, struct, math, collections
sys.path.insert(0, '.')
from common import *
out = sys.argv[1]; ship = '--ship' in sys.argv
import os
extra = json.load(open('extra.json')) if '--extra' in sys.argv and os.path.exists('extra.json') else {'words': {}, 'variants': {}}
lm = pickle.load(open('lm.pkl', 'rb')); ch = json.load(open('channel.json'))
vocab = list(lm['vocab']); wid = {w: i for i, w in enumerate(vocab)}
tot = sum(lm['uni'].values())
extra_uni = {}
for w, c in extra['words'].items():
    if w not in wid: wid[w] = len(vocab); vocab.append(w); extra_uni[w] = c
var = collections.defaultdict(collections.Counter)
for sp in (['train', 'dev', 'test'] if ship else ['train']):
    for u, r, a in lexicon(sp):
        if u in wid and r.isascii() and r.isalpha(): var[wid[u]][r] += a
b = bytearray()
def u16(x): b.extend(struct.pack('>H', x))
def i32(x): b.extend(struct.pack('>i', x))
def f32(x): b.extend(struct.pack('>f', x))
def s(x): d = x.encode('utf-8'); b.append(len(d)); b.extend(d)
b.extend(b'UTRL'); i32(1)
i32(len(ch))
for k, d in ch.items():
    u16(ord(k[0])); b.append(ord(k[1])); b.append(len(d))
    for c, p in d.items(): s(c); f32(math.log(p))
i32(len(vocab))
for i, w in enumerate(vocab):
    d = w.encode('utf-16-be'); b.append(len(d) // 2); b.extend(d)
    f32(math.log((lm['uni'].get(w) or extra_uni[w]) / tot))
    v = var.get(i) or collections.Counter()
    top = v.most_common(8); T = sum(v.values())
    outv = [(r, max(1, min(255, round(255 * a / T)))) for r, a in top]
    for e, wgt in extra['variants'].get(w, {}).items():
        if all(e != r for r, _ in outv) and e.isascii() and e.replace('-', '').isalpha():
            outv.append((e.replace('-', ''), max(1, round(255 * wgt))))
    b.append(len(outv))
    for r, q in outv: s(r); b.append(q)
# bigrams grouped by w1 (index 0 = <s>, words shifted by +1)
by = collections.defaultdict(list)
tot1 = collections.Counter()
for (a, c), n in lm['big'].items(): tot1[a] += n
for (a, c), n in lm['big'].items():
    by[-1 if a == '<s>' else wid[a]].append((wid[c], -math.log(n / tot1[a])))
keys = sorted(by)
i32(len(keys))
for k in keys:
    lst = sorted(by[k]); i32(k); i32(len(lst)); prev = 0
    for w2, nl in lst:
        d = w2 - prev; prev = w2
        while d >= 128: b.append((d & 127) | 128); d >>= 7
        b.append(d); b.append(min(255, round(nl * 12)))
open(out, 'wb').write(b); print('wrote', out, len(b) // 1024, 'KB')
