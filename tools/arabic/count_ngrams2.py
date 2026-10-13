#!/usr/bin/env python3
"""Count unigrams / bigrams / trigrams per source over a shared candidate vocabulary -> data/Arabic/ngrams/

    python3 -I tools/arabic/count_ngrams2.py [vocab=400000]

Sources: scrip (Qur'an + hadith without narrator chains; every 50th hadith line is held out for tuning: work2/dev_hadith.txt),
         tash (Tashkeela books), subs (OpenSubtitles), wiki, cc (whichever exist).
Vocabulary = every Qur'an/hadith word  +  the most frequent words over all sources (max of per-source relative frequencies).
Output per source: uni_<src>.npy (count per vocab id), bi_<src>.npz (keys, counts), tri_<src>.npz (keys, counts; scripture + tashkeela only).
Ids: 0..V-1 words, V = sentence start.  bigram key = id1 * (V+1) + id2, trigram key = (id1 * (V+1) + id2) * (V+1) + id3.
"""
import collections, multiprocessing as mp, os, sys
import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.join(HERE, '..', '..', 'data', 'Arabic')
C1, C2, OUT = os.path.join(ROOT, 'corpus'), os.path.join(ROOT, 'corpus2'), os.path.join(ROOT, 'ngrams')
W2 = os.path.join(HERE, 'work2')
V = 0
IDS = {}


def scripture_lines():
    lines = [l.split() for l in open(os.path.join(C1, 'quran_plain.txt'), encoding='utf-8')]
    hadith = [l.split() for l in open(os.path.join(C1, 'hadith_matn_plain.txt'), encoding='utf-8')]
    dev = [h for i, h in enumerate(hadith) if i % 50 == 7]
    train = [h for i, h in enumerate(hadith) if i % 50 != 7]
    return lines + train, dev


def counts_file(src):
    p = os.path.join(C2, f'counts_{src}.tsv')
    c = {}
    if os.path.exists(p):
        for line in open(p, encoding='utf-8'):
            w, n = line.rstrip('\n').split('\t'); c[w] = int(n)
    return c


def init(ids, v):
    global IDS, V
    IDS, V = ids, v


def file_ranges(path, size=48 << 20):
    n = os.path.getsize(path)
    return [(path, a, min(a + size, n)) for a in range(0, n, size)]


def read_range(path, a, b):
    """the lines that START inside [a, b): workers read the file themselves, so the main process never touches the text"""
    with open(path, 'rb') as f:
        if a > 0:
            f.seek(a - 1)
            if f.read(1) != b'\n': f.readline()           # partial line belongs to the previous range
        else:
            f.seek(0)
        out = []
        while f.tell() < b:
            l = f.readline()
            if not l: break
            out.append(l.decode('utf-8', 'replace'))
    return out


def count_chunk(args):
    lines, tri = args
    if isinstance(lines, tuple): lines = read_range(*lines)
    uni = collections.Counter(); bi = []; tr = []
    B = V + 1
    for line in lines:
        ids = [IDS.get(w, -1) for w in (line.split() if isinstance(line, str) else line)]
        prev2, prev1 = -1, V
        for i in ids:
            if i >= 0:
                uni[i] += 1
                bi.append(prev1 * B + i)
                if tri and prev1 != -1 and prev2 != -1: tr.append((prev2 * B + prev1) * B + i)
                prev2, prev1 = prev1, i
            else:
                prev2, prev1 = -1, -1             # unknown word breaks the context
            if prev1 == -1: prev1 = -1
    def uniq(a):
        if not a: return np.zeros(0, np.int64), np.zeros(0, np.int64)
        return np.unique(np.array(a, dtype=np.int64), return_counts=True)
    return uni, uniq(bi), uniq(tr)


def merge(parts):
    keys = np.concatenate([p[0] for p in parts]) if parts else np.zeros(0, np.int64)
    cnt = np.concatenate([p[1] for p in parts]) if parts else np.zeros(0, np.int64)
    if not len(keys): return keys, cnt
    order = np.argsort(keys, kind='stable'); keys, cnt = keys[order], cnt[order]
    first = np.concatenate(([True], keys[1:] != keys[:-1]))
    idx = np.flatnonzero(first)
    return keys[idx], np.add.reduceat(cnt, idx)


def chunks(it, n):
    b = []
    for x in it:
        b.append(x)
        if len(b) >= n: yield b; b = []
    if b: yield b


def run_source(src, line_iter, tri, pool, minbi):
    uni = np.zeros(V, np.int64); bis = []; tris = []
    work = ((r, tri) for r in line_iter) if isinstance(line_iter, list) and line_iter and isinstance(line_iter[0], tuple) \
        else ((c, tri) for c in chunks(line_iter, 20000))
    for u, b, t in pool.imap_unordered(count_chunk, work, chunksize=1):
        for k, v in u.items(): uni[k] += v
        bis.append(b); tris.append(t)
        if len(bis) >= 96: bis = [merge(bis)]; tris = [merge(tris)] if tri else []
    bk, bc = merge(bis); m = bc >= minbi; bk, bc = bk[m], bc[m]
    np.save(os.path.join(OUT, f'uni_{src}.npy'), uni)
    np.savez(os.path.join(OUT, f'bi_{src}.npz'), keys=bk, counts=bc)
    if tri:
        tk, tc = merge(tris); np.savez(os.path.join(OUT, f'tri_{src}.npz'), keys=tk, counts=tc)
    print(f'{src}: tokens {uni.sum()}, bigrams {len(bk)}' + (f', trigrams {len(tk)}' if tri else ''), flush=True)


def main():
    global V, IDS
    size = int(sys.argv[1]) if len(sys.argv) > 1 else 400000
    os.makedirs(OUT, exist_ok=True)
    scrip, dev = scripture_lines()
    with open(os.path.join(W2, 'dev_hadith.txt'), 'w', encoding='utf-8') as f:
        for d in dev: f.write(' '.join(d) + '\n')
    sc = collections.Counter(w for l in scrip for w in l)
    srcs = {s: counts_file(s) for s in ('tash', 'subs', 'wiki', 'cc')}
    srcs['tash'] = counts_file('tashkeela')
    score = collections.defaultdict(float)
    for w, n in sc.items(): score[w] = max(score[w], n / sum(sc.values()) * 4)
    for s, c in srcs.items():
        tot = sum(c.values()) or 1
        for w, n in c.items():
            if n >= 3: score[w] = max(score[w], n / tot)
    words = [w for w in sc]                                              # every scripture word
    have = set(words)
    for w, _ in sorted(score.items(), key=lambda kv: -kv[1]):
        if len(words) >= size: break
        if w not in have: words.append(w); have.add(w)
    V = len(words); IDS = {w: i for i, w in enumerate(words)}
    with open(os.path.join(OUT, 'vocab.txt'), 'w', encoding='utf-8') as f: f.write('\n'.join(words) + '\n')
    print('vocab', V, 'scripture words', len(sc), flush=True)
    with mp.Pool(28, initializer=init, initargs=(IDS, V)) as pool:
        run_source('scrip', scrip, True, pool, 1)
        run_source('tash', file_ranges(os.path.join(C2, 'tashkeela_plain.txt')), True, pool, 2)
        for s, fn in (('subs', 'subs_plain.txt'), ('wiki', 'wiki_plain.txt'), ('cc', 'cc_plain.txt')):
            p = os.path.join(C2, fn)
            if os.path.exists(p): run_source(s, file_ranges(p), False, pool, 3)


if __name__ == '__main__':
    main()
