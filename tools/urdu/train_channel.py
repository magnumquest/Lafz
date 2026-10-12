"""Learn the Urdu-char -> Roman-chunk channel model theta[(char,pos)][chunk] with EM
(forward-backward over monotone alignments) on the Dakshina Urdu romanization lexicon."""
import sys, math, json, collections, itertools
sys.path.insert(0, '.')
from common import *

MAXC = 3
SEED = {
 'ا': ['a','aa','','i','e','u','o'], 'آ': ['aa','a'], 'ب': ['b'], 'پ': ['p'], 'ت': ['t'], 'ٹ': ['t'], 'ث': ['s'],
 'ج': ['j'], 'چ': ['ch'], 'ح': ['h'], 'خ': ['kh'], 'د': ['d'], 'ڈ': ['d'], 'ذ': ['z'], 'ر': ['r'], 'ڑ': ['r'],
 'ز': ['z'], 'ژ': ['zh'], 'س': ['s'], 'ش': ['sh'], 'ص': ['s'], 'ض': ['z'], 'ط': ['t'], 'ظ': ['z'], 'ع': ['a','','e','i'],
 'غ': ['gh'], 'ف': ['f'], 'ق': ['q','k'], 'ک': ['k'], 'گ': ['g'], 'ل': ['l'], 'م': ['m'], 'ن': ['n'], 'ں': ['n',''],
 'و': ['o','w','u','oo','v','au'], 'ہ': ['h','a','',"ah"], 'ھ': ['h',''], 'ء': ['','a'], 'ی': ['i','y','e','ee','ai'],
 'ے': ['e','ay','ai','ey'], 'ئ': ['y','i','e',''], 'ؤ': ['o','u'], 'ۓ': ['e'],
}
VOW = ['', 'a', 'i', 'u', 'e', 'o', 'aa', 'ee', 'oo', 'ai', 'au']
CONS = set('بپتٹثجچحخدڈذرڑزژسشصضطظعغفقکگلمنںہھ')

def pos_of(i, n):
    return 'I' if i == 0 else ('F' if i == n - 1 else 'M')

def init_theta():
    th = collections.defaultdict(dict)
    for c, bases in SEED.items():
        for p in 'IMF':
            for b in bases:
                if c in CONS:
                    for v in VOW:
                        if len(b+v) <= MAXC: th[(c,p)][b+v] = th[(c,p)].get(b+v, 0) + (3.0 if v == '' else 1.0) / (1 + len(v))
                else:
                    th[(c,p)][b] = th[(c,p)].get(b, 0) + 2.0
    return th

def normalize(th, floor):
    out = {}
    for k, d in th.items():
        s = sum(d.values())
        out[k] = {ch: v / s for ch, v in d.items() if v / s > floor}
        s = sum(out[k].values())
        out[k] = {ch: v / s for ch, v in out[k].items()}
    return out

def prob(th, key, chunk, unk):
    d = th.get(key)
    if d is None: return 0.0
    return d.get(chunk, unk if len(chunk) <= 2 else unk * 0.1)

def fb(th, u, r, unk, acc, w):
    n, m = len(u), len(r)
    # forward
    A = [[0.0] * (m + 1) for _ in range(n + 1)]
    A[0][0] = 1.0
    P = [[None] * (m + 1) for _ in range(n)]
    for i in range(n):
        key = (u[i], pos_of(i, n))
        for j in range(m + 1):
            a = A[i][j]
            if a == 0.0: continue
            for l in range(0, MAXC + 1):
                if j + l > m: break
                p = prob(th, key, r[j:j+l], unk)
                if p > 0: A[i+1][j+l] += a * p
    Z = A[n][m]
    if Z <= 0: return 0
    B = [[0.0] * (m + 1) for _ in range(n + 1)]
    B[n][m] = 1.0
    for i in range(n - 1, -1, -1):
        key = (u[i], pos_of(i, n))
        for j in range(m + 1):
            s = 0.0
            for l in range(0, MAXC + 1):
                if j + l > m: break
                p = prob(th, key, r[j:j+l], unk)
                if p > 0: s += p * B[i+1][j+l]
            B[i][j] = s
    for i in range(n):
        key = (u[i], pos_of(i, n))
        for j in range(m + 1):
            a = A[i][j]
            if a == 0.0: continue
            for l in range(0, MAXC + 1):
                if j + l > m: break
                ch = r[j:j+l]
                p = prob(th, key, ch, unk)
                if p > 0:
                    g = a * p * B[i+1][j+l] / Z
                    if g > 1e-6: acc[key][ch] += g * w
    return 1

def main():
    # per-word weights: each word counts once, spread over its variants by attestation
    byw = collections.defaultdict(list)
    for u, r, a in lexicon('train'):
        if re.fullmatch('[a-z]+', r) and len(u) <= 12 and len(r) <= 18:
            byw[u].append((r, a))
    pairs = []
    for u, rs in byw.items():
        tot = sum(a for _, a in rs)
        for r, a in rs: pairs.append((u, r, a / tot))
    print('pairs', len(pairs), 'words', len(byw))
    th = normalize(init_theta(), 0)
    iters = int(sys.argv[1]) if len(sys.argv) > 1 else 5
    for it in range(iters):
        acc = collections.defaultdict(lambda: collections.defaultdict(float))
        ll = 0
        for u, r, w in pairs:
            fb(th, u, r, 1e-4, acc, w)
        # allow new chunks to appear through the unk path: M-step
        newth = collections.defaultdict(dict)
        for k, d in acc.items():
            for ch, v in d.items(): newth[k][ch] = v
        th = normalize(newth, 0.002)
        print('iter', it, 'keys', len(th), 'chunks', sum(len(d) for d in th.values()), flush=True)
    json.dump({f'{k[0]}{k[1]}': d for k, d in th.items()}, open('channel.json', 'w'), ensure_ascii=False)
    for c in 'کلمینوہاے':
        print(c, sorted(th[(c,'M')].items(), key=lambda x:-x[1])[:8])
main()
