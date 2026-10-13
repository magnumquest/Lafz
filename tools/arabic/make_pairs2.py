#!/usr/bin/env python3
"""v2 training data for the Arabic channel model -> tools/arabic/work2/

    python3 -I tools/arabic/make_pairs2.py [vocab=150000]

Needs data/Arabic/corpus (Qur'an, hadith, from build_corpus.py) and corpus2 (counts_*.tsv + tashkeela_diac.txt from build_corpus2.py).

Phonetic spellings are *generated from vowelled text* (so every vowel is known), not looked up: romanize.variants() renders each word in six
human typing styles; on top of that this script adds what people do when they type scripture quickly:
  * case endings are free: the final vowel is dropped, kept, or replaced by another one (rahmati / rahmatu / rahmata / rahmat)
  * the article's alif disappears when words are typed glued together (innamal'amal)
  * article forms are annotated with context pseudo-letters (pseudo.py) so the channel learns al- / an- / ash- / -l'
Outputs
  work2/train_pairs.tsv   arabic(annotated)  roman  share  groupweight  group     (train_channel2.py)
  work2/words.tsv         plain word, weighted count, source mix, chosen vowelled form  (vocabulary + LM builder)
"""
import collections, math, multiprocessing as mp, os, random, re, sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from arnorm import strip_marks
from romanize import variants
import pseudo
import romanize as R
R.END_W = (0.42, 0.38, 0.20)    # scripture is typed with its case endings far more often than the dictionary forms of romanize.py assume (hawla, illa, khayran)

ROOT = os.path.join(HERE, '..', '..', 'data', 'Arabic')
C1, C2 = os.path.join(ROOT, 'corpus'), os.path.join(ROOT, 'corpus2')
OUT = os.path.join(HERE, 'work2')
HARAKA = re.compile('[ًٌٍَُِّْٰ]')
AR = re.compile('^[ءآأؤإئابةتثجحخدذرزسشصضطظعغفقكلمنهوىي]+$')
SCRIPTURE_ONLY = os.environ.get('SCRIPTURE_ONLY', '1') == '1'   # the Tashkeela corpus has (almost) no shadda marks: its vowelled forms cannot be used for phonetics
SCRIPTURE_BOOST = 3.0       # a Qur'an/hadith occurrence counts this much more than one in other vowelled books


def read_counts(path):
    c = {}
    if os.path.exists(path):
        for line in open(path, encoding='utf-8'):
            w, n = line.rstrip('\n').split('\t'); c[w] = int(n)
    return c


def vowelled_forms():
    forms = collections.defaultdict(collections.Counter)      # plain -> {vowelled form: weighted count}
    src = collections.defaultdict(lambda: [0, 0])               # plain -> [scripture count, other vowelled count]
    for fn, boost, k in (('quran_diac.txt', SCRIPTURE_BOOST, 0), ('hadith_matn_diac.txt', SCRIPTURE_BOOST, 0)):
        for line in open(os.path.join(C1, fn), encoding='utf-8'):
            for w in line.split():
                if HARAKA.search(w):
                    p = strip_marks(w)
                    if AR.match(p): forms[p][w] += boost; src[p][k] += 1
    tk = os.path.join(C2, 'tashkeela_diac.txt')
    if os.path.exists(tk) and not SCRIPTURE_ONLY:
        for line in open(tk, encoding='utf-8'):
            for w in line.split():
                if HARAKA.search(w):
                    p = strip_marks(w)
                    if AR.match(p) and len(p) <= 14: forms[p][w] += 1; src[p][1] += 1
    return forms, src


def case_noise(d):
    """final case vowel is frequently written differently or not at all (rahmati / rahmatu / rahmata / rahmat)"""
    out = collections.defaultdict(float)
    for r, w in d.items():
        m = re.search(r"[bcdfghjklmnpqrstvwxyz'0-9]([aiu])$", r)
        if m and len(r) > 3:
            base = r[:-1]
            out[r] += w * 0.7
            for v in 'aiu':
                if v != r[-1]: out[base + v] += w * 0.1
            out[base] += w * 0.1
        else:
            out[r] += w
    return out


def elided(d, art):
    """words that begin with the article: typed after a vowel the alif is not pronounced (and often not written)"""
    if not art: return d
    out = collections.defaultdict(float)
    for r, w in d.items():
        out[r] += w * 0.72
        if r.startswith('a') and len(r) > 2: out[r[1:]] += w * 0.2
        elif r.startswith('e') and len(r) > 2: out[r[1:]] += w * 0.05
        else: out[r] += w * 0.25
        if r.startswith('al'): out['el' + r[2:]] += w * 0.03
    return out


def gen(item):
    plain, forms = item
    use = [(f, n) for f, n in forms if n >= 0.2 * forms[0][1]][:3]
    tot = sum(n for _, n in use)
    mix = collections.defaultdict(float)
    for f, n in use:
        for r, w in variants(f).items(): mix[r] += w * n / tot
    if not mix: return plain, []
    mix = case_noise(mix)
    return plain, mix


def main():
    size = int(sys.argv[1]) if len(sys.argv) > 1 else 150000
    random.seed(11)
    os.makedirs(OUT, exist_ok=True)
    forms, src = vowelled_forms()
    allc = collections.Counter()
    for s in ('subs', 'wiki', 'cc', 'tashkeela'):
        for w, n in read_counts(os.path.join(C2, f'counts_{s}.tsv')).items(): allc[w] += n
    for w in forms: allc[w] += 0
    known = {w for w, n in allc.items() if n >= 5} | set(forms)
    ranked = sorted(forms, key=lambda p: -sum(forms[p].values()))[:size]
    print('vowelled words', len(forms), 'using', len(ranked), 'known words', len(known), flush=True)
    items = [(p, sorted(forms[p].items(), key=lambda kv: -kv[1])) for p in ranked]
    med = sorted(sum(forms[p].values()) for p in ranked)[len(ranked) // 2]
    n_rows = 0
    with mp.Pool(28) as pool, open(os.path.join(OUT, 'train_pairs.tsv'), 'w', encoding='utf-8') as f, \
            open(os.path.join(OUT, 'words.tsv'), 'w', encoding='utf-8') as g:
        g.write('plain\tweighted_count\tscripture_count\tother_vowelled\tvowelled_form\n')
        for plain, mix in pool.imap(gen, items, chunksize=200):
            if not mix: continue
            ann = pseudo.annotate(plain, known)
            art = ann != plain
            mix = elided(mix, art)
            z = sum(mix.values())
            c = sum(forms[plain].values())
            gw = max(0.3, min(3.0, math.log1p(c) / math.log1p(max(med, 2))))
            top = sorted(mix.items(), key=lambda kv: -kv[1])[:16]
            zt = sum(v for _, v in top)
            for r, v in top:
                if v / zt >= 0.004 and re.fullmatch(r"[a-z0-9']+", r): f.write(f'{ann}\t{r}\t{v / zt:.4f}\t{gw:.3f}\tsyn\n'); n_rows += 1
            g.write(f'{plain}\t{c}\t{src[plain][0]}\t{src[plain][1]}\t{max(forms[plain], key=forms[plain].get)}\n')
    print('train rows', n_rows, '->', OUT)


if __name__ == '__main__':
    main()
