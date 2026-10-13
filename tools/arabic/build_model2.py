#!/usr/bin/env python3
"""Build the v2 Arabic model ("UTRL" version 2: pseudo-letter map + trigram table) from the counted corpora.

    python3 -I tools/arabic/build_model2.py out.bin [--channel work2/channel2.json] [--vocab 220000]
        [--lam-uni scrip=.35,tash=.1,subs=.25,wiki=.15,cc=.15] [--lam-bi ...] [--lam-tri scrip=.7,tash=.3]
        [--variants work/real_all.tsv] [--exclude-variants work/dev_real.tsv]

Run count_ngrams2.py first.  The language model is a per-source mixture (a scripture-heavy one: the keyboard's main use is typing Qur'an / hadith):
  unigram   P(w)      = sum_s lam_s * P_s(w)
  bigram    P(w2|w1)  = sum_s lam_s(w1) * P_s(w2|w1)    (lam_s(w1) shrinks for sources that hardly saw w1)
  trigram   P(w3|w1,w2) likewise, scripture + Tashkeela only (the "phrase memory")
Vocabulary: every Qur'an/hadith word seen at least --scripture-min times plus the most probable words of the mixture up to --vocab.
"""
import argparse, collections, json, math, os, re, struct, sys
import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import pseudo

ROOT = os.path.join(HERE, '..', '..', 'data', 'Arabic')
NG = os.path.join(ROOT, 'ngrams')
ap = argparse.ArgumentParser()
ap.add_argument('out')
ap.add_argument('--channel', default=os.path.join(HERE, 'work2', 'channel2.json'))
ap.add_argument('--vocab', type=int, default=220000)
ap.add_argument('--scripture-min', type=int, default=1)
ap.add_argument('--lam-uni', default='scrip=.35,tash=.10,subs=.25,wiki=.15,cc=.15')
ap.add_argument('--lam-bi', default='scrip=.45,tash=.15,subs=.2,wiki=.1,cc=.1')
ap.add_argument('--lam-tri', default='scrip=.7,tash=.3')
ap.add_argument('--bi-keep', type=int, default=40, help='followers kept per context')
ap.add_argument('--tri-keep', type=int, default=24)
ap.add_argument('--tri-min-tash', type=int, default=4)
ap.add_argument('--variants', default=os.path.join(HERE, 'work', 'real_all.tsv'))
ap.add_argument('--exclude-variants', default='', help='dev file roman<TAB>arabic: no observed spellings for these words (leak-free evaluation)')
ap.add_argument('--syn-variants', default=os.path.join(HERE, 'work2', 'train_pairs.tsv'), help='synthetic human-style spellings (arabic, roman, p, ..., syn) stored as exact-spelling variants')
ap.add_argument('--syn-top', type=int, default=6)
ap.add_argument('--syn-min-p', type=float, default=0.03)
ap.add_argument('--no-tri', action='store_true')
A = ap.parse_args()


def lam(s):
    return {k: float(v) for k, v in (x.split('=') for x in s.split(','))}


LU, LB, LT = lam(A.lam_uni), lam(A.lam_bi), lam(A.lam_tri)
words = open(os.path.join(NG, 'vocab.txt'), encoding='utf-8').read().split('\n')
words = [w for w in words if w]
V = len(words)
srcs = [s for s in LU if os.path.exists(os.path.join(NG, f'uni_{s}.npy'))]
uni = {s: np.load(os.path.join(NG, f'uni_{s}.npy')).astype(np.float64) for s in srcs}
print('sources', srcs, 'candidate vocab', V, flush=True)

# ------------------------------------------------------------ vocabulary
P = {s: (uni[s] + 0.1) / (uni[s].sum() + 0.1 * V) for s in srcs}
mix = sum(LU[s] * P[s] for s in srcs) / sum(LU[s] for s in srcs)
scrip_cnt = uni['scrip']
CLITICS = ['و', 'ف', 'ب', 'ل', 'ك', 'ال', 'وال', 'فال', 'بال', 'كال', 'لل', 'ولل']
ONE_LETTER_OK = set('وفبلك')
sel = [i for i in range(V) if scrip_cnt[i] >= A.scripture_min]
chosen = set(sel)
for i in np.argsort(-mix):
    if len(chosen) >= A.vocab: break
    i = int(i)
    if i in chosen: continue
    w = words[i]
    if len(w) == 1 and w not in ONE_LETTER_OK: continue
    chosen.add(i); sel.append(i)
sel = [i for i in sel if len(words[i]) > 1 or words[words.index(words[i])] in ONE_LETTER_OK]
vocab = [words[i] for i in sel]
extra = [c for c in CLITICS if c not in set(vocab)]
vocab += extra
M = len(vocab)
mid = np.full(V + 1, -1, np.int64)
for j, i in enumerate(sel): mid[i] = j
widx = {w: j for j, w in enumerate(vocab)}
for c in extra: widx[c] = widx[c]
print('model vocabulary', M, '(scripture words:', int((scrip_cnt >= A.scripture_min).sum()), ')', flush=True)

# unigram of the model words, renormalised over the model vocabulary
u_model = np.zeros(M)
for j, i in enumerate(sel): u_model[j] = mix[i]
CLITIC_P = {'و': 3e-2, 'ف': 1e-2, 'ب': 5e-3, 'ل': 5e-3, 'ك': 2e-3, 'ال': 1e-2, 'وال': 3e-3, 'فال': 1e-3, 'بال': 2e-3, 'كال': 3e-4, 'لل': 2e-3, 'ولل': 3e-4}
for c in extra: u_model[widx[c]] = CLITIC_P.get(c, 5e-5)
for c in CLITICS: u_model[widx[c]] = max(u_model[widx[c]], CLITIC_P.get(c, 5e-5))
u_model /= u_model.sum()

# ------------------------------------------------------------ bigrams
B = V + 1
def load_ng(kind, s):
    p = os.path.join(NG, f'{kind}_{s}.npz')
    if not os.path.exists(p): return None
    z = np.load(p); return z['keys'], z['counts'].astype(np.float64)


def group_top(ctx, nxt, prob, keep):
    """rows (ctx, next, prob) -> for each ctx the `keep` most probable nexts"""
    order = np.lexsort((-prob, ctx))
    ctx, nxt, prob = ctx[order], nxt[order], prob[order]
    first = np.concatenate(([True], ctx[1:] != ctx[:-1]))
    start = np.flatnonzero(first)
    rank = np.arange(len(ctx)) - np.repeat(start, np.diff(np.concatenate((start, [len(ctx)]))))
    m = rank < keep
    return ctx[m], nxt[m], prob[m]


def sum_dups(keys, vals):
    order = np.argsort(keys, kind='stable'); keys, vals = keys[order], vals[order]
    first = np.concatenate(([True], keys[1:] != keys[:-1])); idx = np.flatnonzero(first)
    return keys[idx], np.add.reduceat(vals, idx)


# per-source weight of a context: lam_s * n_s / (n_s + 5), normalised over sources
nctx = {s: np.concatenate((uni[s][sel], np.zeros(len(extra)))) for s in srcs}
wctx = {s: LB.get(s, 0) * nctx[s] / (nctx[s] + 5.0) for s in srcs}
tot = sum(wctx.values()) + 1e-12
bk_all, bv_all = [], []
for s in srcs:
    kc = load_ng('bi', s)
    if kc is None: continue
    keys, cnt = kc
    i1, i2 = keys // B, keys % B
    ok = (mid[i2] >= 0) & ((mid[i1] >= 0) | (i1 == V))
    i1, i2, cnt = i1[ok], i2[ok], cnt[ok]
    m1, m2 = np.where(i1 == V, -1, mid[i1]), mid[i2]
    if s == 'scrip': denom_start = cnt[m1 == -1].sum()
    # P_s(w2 | w1) = count / occurrences of w1 as a context (sentence starts: total starts seen)
    st = cnt[m1 == -1].sum() or 1.0
    den = np.where(m1 == -1, st, nctx[s][np.maximum(m1, 0)] + 1e-9)
    p = np.minimum(cnt / den, 1.0)
    wgt = np.where(m1 == -1, LB.get(s, 0) / sum(LB.get(x, 0) for x in srcs), (wctx[s] / tot)[np.maximum(m1, 0)])
    bk_all.append((m1 + 1) * (M + 1) + m2); bv_all.append(wgt * p)
    print('  bigrams', s, len(keys), '->', int(ok.sum()), flush=True)
bk, bv = sum_dups(np.concatenate(bk_all), np.concatenate(bv_all))
bctx, bnxt = bk // (M + 1) - 1, bk % (M + 1)
bctx, bnxt, bv = group_top(bctx, bnxt, bv, A.bi_keep)
print('bigram contexts', len(np.unique(bctx)), 'bigrams', len(bctx), flush=True)

# ------------------------------------------------------------ trigrams (phrase memory)
tctx, tnxt, tv = np.zeros(0, np.int64), np.zeros(0, np.int64), np.zeros(0)
if not A.no_tri:
    tk_all, tv_all = [], []
    for s in ('scrip', 'tash'):
        if s not in srcs: continue
        tr, bi = load_ng('tri', s), load_ng('bi', s)
        if tr is None: continue
        keys, cnt = tr
        if s == 'tash': m = cnt >= A.tri_min_tash; keys, cnt = keys[m], cnt[m]
        i3 = keys % B; r = keys // B; i2 = r % B; i1 = r // B
        ok = (mid[i1] >= 0) & (mid[i2] >= 0) & (mid[i3] >= 0)
        keys, cnt, i1, i2, i3 = keys[ok], cnt[ok], i1[ok], i2[ok], i3[ok]
        # context count of (w1, w2) in that source: from the bigram table
        bkeys, bcnt = bi
        pos = np.searchsorted(bkeys, i1 * B + i2)
        pos = np.minimum(pos, len(bkeys) - 1)
        c2 = np.where(bkeys[pos] == i1 * B + i2, bcnt[pos], np.maximum(cnt, 1))
        p = np.minimum(cnt / c2, 1.0)
        tk_all.append(((mid[i1] * (M + 1) + mid[i2]) * (M + 1) + mid[i3], LT[s] * p, LT[s]))
        print('  trigrams', s, len(ok), '->', len(keys), flush=True)
    keys = np.concatenate([k for k, _, _ in tk_all]); vals = np.concatenate([v for _, v, _ in tk_all])
    keys, vals = sum_dups(keys, vals)
    vals = np.minimum(vals / sum(LT.values()), 1.0)
    tn = keys % (M + 1); tc = keys // (M + 1)
    tc, tn, vals = group_top(tc, tn, vals, A.tri_keep)
    tctx, tnxt, tv = tc, tn, vals
    print('trigram contexts', len(np.unique(tctx)), 'trigrams', len(tctx), flush=True)

# ------------------------------------------------------------ observed spellings
CUR = collections.OrderedDict()
for line in open(os.path.join(HERE, 'chat_curated.tsv'), encoding='utf-8'):
    if line.startswith('#') or '\t' not in line: continue
    a, v = line.rstrip('\n').split('\t'); CUR[re.sub(r'\s+', ' ', a.strip())] = [x for x in v.split(',') if x]
CLITIC_VARIANTS = {'ال': 'al,el,il,l', 'وال': 'wal,wel,wl,walal', 'بال': 'bil,bal,bel,bl', 'فال': 'fal,fel,fil', 'كال': 'kal,kel', 'لل': 'lil,lel,lal',
                   'ولل': 'walil,wlil', 'و': 'w,wa,wo,wu,o,u', 'ف': 'f,fa,fe,fi', 'ب': 'b,bi,be,ba', 'ل': 'l,li,la,le,lo', 'ك': 'k,ka,ki,ke'}
for a, v in CLITIC_VARIANTS.items(): CUR[a] = v.split(',')
rel = os.path.join(ROOT, 'pairs', 'religious_terms_curated.tsv')
if os.path.exists(rel):
    for line in open(rel, encoding='utf-8'):
        p = line.rstrip('\n').split('\t')
        if len(p) == 2 and p[0] != 'arabic':
            a = re.sub(r'\s+', ' ', p[0].strip()); CUR.setdefault(a, [])
            CUR[a] += [x.strip().lower().replace(' ', '') for x in p[1].split(',') if x.strip()]
excl = set()
if A.exclude_variants:
    for line in open(A.exclude_variants, encoding='utf-8'): excl.add(line.rstrip('\n').split('\t')[1])
for a in excl: CUR.pop(a, None)
variants = collections.defaultdict(dict)
for a, vs in CUR.items():
    if a not in widx:                                    # curated phrases / words become dictionary entries
        widx[a] = len(vocab); vocab.append(a); u_model = np.append(u_model, 1.5e-5 if ' ' in a else 4e-6)
    for i, v in enumerate(vs):
        if re.fullmatch(r"[a-z0-9']+", v) and 1 <= len(v) <= 30: variants[a].setdefault(v, 0.8 if i == 0 else 0.6)
if A.variants and os.path.exists(A.variants):
    real = collections.defaultdict(collections.Counter)
    for line in open(A.variants, encoding='utf-8'):
        p = line.rstrip('\n').split('\t')
        if len(p) >= 2 and p[0] in widx and p[0] not in excl and re.fullmatch(r"[a-z0-9']+", p[1]): real[p[0]][p[1]] += int(p[2]) if len(p) > 2 else 1
    for a, c in real.items():
        t = sum(c.values())
        for r, n in c.most_common(8): variants[a].setdefault(r, min(0.7, 0.35 + n / t * 0.35))
if A.syn_top > 0 and os.path.exists(A.syn_variants):
    syn = collections.defaultdict(list)
    for line in open(A.syn_variants, encoding='utf-8'):
        p = line.rstrip('\n').split('\t')
        if len(p) >= 5 and p[4] == 'syn' and p[0] in widx and p[0] not in excl and re.fullmatch(r"[a-z0-9']+", p[1]) and float(p[2]) >= A.syn_min_p:
            syn[p[0]].append((float(p[2]), p[1]))
    for a, l in syn.items():
        for q, r in sorted(l, reverse=True)[:A.syn_top]: variants[a].setdefault(r, min(0.45, 0.15 + q))
    print('synthetic variants for', len(syn), 'words', flush=True)
M2 = len(vocab)
u_model /= u_model.sum()
print('vocabulary incl. curated', M2, flush=True)

# ------------------------------------------------------------ export
known = set(vocab)
ch = json.load(open(A.channel, encoding='utf-8'))
b = bytearray()
def u16(x): b.extend(struct.pack('>H', x))
def i32(x): b.extend(struct.pack('>i', x))
def i64(x): b.extend(struct.pack('>q', int(x)))
def f32(x): b.extend(struct.pack('>f', x))
def s(x): d = x.encode('utf-8'); b.append(len(d)); b.extend(d)
b.extend(b'UTRL'); i32(2)
i32(len(ch))
for k, d in ch.items():
    u16(ord(k[0])); b.append(ord(k[1])); b.append(len(d))
    for c, p in d.items(): s(c); f32(math.log(p))
b.append(len(pseudo.MAP))
for a, r in pseudo.MAP: u16(ord(a)); u16(ord(r))
i32(M2)
for j, w in enumerate(vocab):
    ann = pseudo.annotate(w, known) if ' ' not in w else ' '.join(pseudo.annotate(x, known) for x in w.split(' '))
    d = ann.encode('utf-16-be'); b.append(min(255, len(d) // 2)); b.extend(d[:2 * min(255, len(d) // 2)])
    f32(math.log(max(u_model[j], 1e-12)))
    outv = sorted(variants.get(w, {}).items(), key=lambda x: -x[1])[:12]
    b.append(len(outv))
    for r, q in outv: s(r); b.append(max(1, min(255, round(255 * q))))


def write_table(ctx, nxt, val):
    keys = np.unique(ctx)
    return keys


# bigrams: per context, followers sorted by id (delta + varint), q = round(12 * -ln p)
def write_followers(ctxs, nxts, vals, keyfun, keysize):
    order = np.lexsort((nxts, ctxs)); ctxs, nxts, vals = ctxs[order], nxts[order], vals[order]
    first = np.concatenate(([True], ctxs[1:] != ctxs[:-1])); st = np.flatnonzero(first); en = np.concatenate((st[1:], [len(ctxs)]))
    i32(len(st))
    for a, z in zip(st, en):
        keyfun(ctxs[a]); i32(z - a); prev = 0
        for w2, p in zip(nxts[a:z], vals[a:z]):
            d = int(w2) - prev; prev = int(w2)
            while d >= 128: b.append((d & 127) | 128); d >>= 7
            b.append(d); b.append(min(255, round(-math.log(max(p, 1e-9)) * 12)))


write_followers(bctx, bnxt, bv, lambda c: i32(int(c)), 4)
if A.no_tri:
    i32(0)
else:
    tc1 = tctx // (M + 1); tc2 = tctx % (M + 1)                      # context = (w1, w2) pair: sorted by the engine's key ((w1+1)<<32)|(w2+1) = w1 first
    # re-key for the engine: both ids shifted by 1 (so -1 = sentence start would be 0); our ids are >= 0 here
    keyed = (tc1 + 1) * (M + 1) + (tc2 + 1)
    order = np.lexsort((tnxt, keyed)); keyed, tn2, tv2 = keyed[order], tnxt[order], tv[order]
    first = np.concatenate(([True], keyed[1:] != keyed[:-1])); st = np.flatnonzero(first); en = np.concatenate((st[1:], [len(keyed)]))
    i32(len(st))
    for a, z in zip(st, en):
        k1, k2 = keyed[a] // (M + 1), keyed[a] % (M + 1)
        i64((int(k1) << 32) | int(k2)); i32(z - a); prev = 0
        for w3, p in zip(tn2[a:z], tv2[a:z]):
            d = int(w3) - prev; prev = int(w3)
            while d >= 128: b.append((d & 127) | 128); d >>= 7
            b.append(d); b.append(min(255, round(-math.log(max(p, 1e-9)) * 12)))
os.makedirs(os.path.dirname(os.path.abspath(A.out)), exist_ok=True)
open(A.out, 'wb').write(b)
print('wrote', A.out, len(b) // 1024, 'KB')
