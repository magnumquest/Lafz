"""v2 (pseudo-letters, several input files): python3 train_channel2.py out.json iterations file1.tsv [file2.tsv ...]
Re-estimate the Arabic-letter -> Roman-chunk channel model with EM (forward-backward over monotone alignments),
starting from the prior in channel.json (channel_prior.py).

    python3 train_channel.py arabizi_pairs.tsv [iterations=5]      # -> channel_trained.json  (copy over channel.json to use it)

arabizi_pairs.tsv: arabic word <TAB> romanization as written by a person <TAB> count (optional, default 1). One line per
(word, spelling); several spellings per word are expected. Use real human-written Arabizi (chat/social media, transliteration
lexicons such as a Dakshina-style Arabic collection). Do NOT generate the file from rules, that only reproduces the prior.

Same algorithm as tools/urdu/train_channel.py, but with a prior instead of a hand-made seed, digits/apostrophe as symbols and
chunks of up to 4 symbols."""
import sys, re, json, collections, os, multiprocessing

MAXC = 4
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import pseudo
PRIOR = pseudo.prior_for(json.load(open(os.path.join(os.path.dirname(os.path.abspath(__file__)), 'channel.json'), encoding='utf-8')))
_DIAC = re.compile('[ً-ٰٟـ]')

def norm_ar(w):
    return _DIAC.sub('', w).replace('ى', 'ى')

def pos_of(i, n):
    return 'I' if i == 0 else ('F' if i == n - 1 else 'M')

def prob(th, key, chunk, unk):
    d = th.get(key)
    if d is None: return 0.0
    return d.get(chunk, unk if len(chunk) <= 2 else unk * 0.1)

def fb(th, u, r, unk, acc, w):
    n, m = len(u), len(r)
    A = [[0.0] * (m + 1) for _ in range(n + 1)]
    A[0][0] = 1.0
    for i in range(n):
        key = u[i] + pos_of(i, n)
        for j in range(m + 1):
            a = A[i][j]
            if a == 0.0: continue
            for l in range(0, MAXC + 1):
                if j + l > m: break
                p = prob(th, key, r[j:j + l], unk)
                if p > 0: A[i + 1][j + l] += a * p
    Z = A[n][m]
    if Z <= 0: return 0
    B = [[0.0] * (m + 1) for _ in range(n + 1)]
    B[n][m] = 1.0
    for i in range(n - 1, -1, -1):
        key = u[i] + pos_of(i, n)
        for j in range(m + 1):
            s = 0.0
            for l in range(0, MAXC + 1):
                if j + l > m: break
                p = prob(th, key, r[j:j + l], unk)
                if p > 0: s += p * B[i + 1][j + l]
            B[i][j] = s
    for i in range(n):
        key = u[i] + pos_of(i, n)
        for j in range(m + 1):
            a = A[i][j]
            if a == 0.0: continue
            for l in range(0, MAXC + 1):
                if j + l > m: break
                ch = r[j:j + l]
                p = prob(th, key, ch, unk)
                if p > 0:
                    g = a * p * B[i + 1][j + l] / Z
                    if g > 1e-6: acc[key][ch] += g * w
    return 1

def normalize(th, floor):
    out = {}
    for k, d in th.items():
        s = sum(d.values())
        d = {c: v / s for c, v in d.items() if v / s > floor}
        s = sum(d.values())
        out[k] = {c: v / s for c, v in d.items()}
    return out

def main():
    outp = sys.argv[1]; iters = int(sys.argv[2]); paths = sys.argv[3:]
    # columns: arabic, roman, count (optional), group weight (optional, default 1: each distinct word of the group counts this much),
    # group name (optional): lets a small set of real spellings outweigh a big set of generated ones
    groups = collections.defaultdict(list)
    gw = {}
    lines = (l for path in paths for l in open(path, encoding='utf-8'))
    for line in lines:
        p = line.rstrip('\n').split('\t')
        if len(p) < 2 or line.startswith('#'): continue
        u, r = norm_ar(p[0].strip()), p[1].strip().lower()
        if re.fullmatch(r"[a-z0-9']+", r) and 1 <= len(u) <= 14 and len(r) <= 20 and ' ' not in u:
            g = p[4] if len(p) > 4 else ''
            gw[(u, g)] = float(p[3]) if len(p) > 3 else 1.0
            groups[(u, g)].append((r, float(p[2]) if len(p) > 2 else 1.0))
    pairs = []
    for key, rs in groups.items():
        tot = sum(a for _, a in rs)
        for r, a in rs: pairs.append((key[0], r, gw[key] * a / tot))
    print('pairs', len(pairs), 'word groups', len(groups), flush=True)
    th = normalize({k: dict(v) for k, v in PRIOR.items()}, 0)
    workers = int(os.environ.get('WORKERS', '4'))
    chunks = [pairs[i::workers] for i in range(workers)]
    for it in range(iters):
        with multiprocessing.Pool(workers) as pool:
            parts = pool.starmap(e_step, [(th, c) for c in chunks])
        acc = collections.defaultdict(lambda: collections.defaultdict(float))
        for part in parts:
            for k, d in part.items():
                for c, v in d.items(): acc[k][c] += v
        new = {k: dict(v) for k, v in acc.items()}
        for k, d in th.items():          # letters/positions never seen in the data keep their prior
            if k not in new: new[k] = d
        th = normalize(new, 0.002)
        print('iter', it, 'keys', len(th), 'chunks', sum(len(d) for d in th.values()), flush=True)
    json.dump(th, open(outp, 'w', encoding='utf-8'), ensure_ascii=False)
    print('wrote', outp)

def e_step(th, pairs):
    acc = collections.defaultdict(lambda: collections.defaultdict(float))
    for u, r, w in pairs:
        if all(u[i] + pos_of(i, len(u)) in th for i in range(len(u))):
            fb(th, u, r, 1e-4, acc, w)
    return {k: dict(v) for k, v in acc.items()}

if __name__ == '__main__':
    main()
