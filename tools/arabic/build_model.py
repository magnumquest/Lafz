"""Build app/src/main/assets/arabic/arabic_translit.bin (format "UTRL" v1, read by RomanTransliterator.load).

    pip install wordfreq
    DATA=/path/to/Arabic python3 prep_data.py && python3 train_channel.py work/train_pairs.tsv 5   # -> channel_trained.json
    python3 build_model.py --channel channel_trained.json --variants work/real_all.tsv [--eval]    # --eval: dev-safe model, no leakage

Language model:
  vocabulary  = wordfreq Arabic top 100k  +  Qur'an/Hadith words seen >= 2 times  +  chat words from the real Arabizi set that are
                frequent in wordfreq  +  tools/arabic/chat_curated.tsv  +  names of tools/../honorifics.tsv
  unigram     = 0.35 * wordfreq (general web / subtitles / Twitter / Wikipedia)  +  0.65 * Qur'an + Hadith counts (--religious-mix)
  bigrams     = Qur'an + Hadith running text only (the keyboard's main use is typing scripture words)
Data licences: wordfreq data CC BY-SA 4.0; the Qur'an/Hadith/Arabizi bundle has unclear/restrictive licences (see its README), keep the
bundle itself out of the repo."""
import sys, re, json, math, struct, collections, os, argparse

HERE = os.path.dirname(os.path.abspath(__file__))
ap = argparse.ArgumentParser()
ap.add_argument('out', nargs='?', default=os.path.join(HERE, '../../app/src/main/assets/arabic/arabic_translit.bin'))
ap.add_argument('--vocab', type=int, default=100000)
ap.add_argument('--channel', default=os.path.join(HERE, 'channel.json'))
ap.add_argument('--variants', default='', help='arabic<TAB>roman<TAB>count file of real spellings (observed variants)')
ap.add_argument('--data', default=os.environ.get('DATA', ''), help='Arabic data bundle dir (corpus/, pairs/)')
ap.add_argument('--exclude', default='', help='dev file roman<TAB>arabic: these words get no curated spellings (leak-free evaluation)')
ap.add_argument('--no-bigrams', action='store_true')
ap.add_argument('--religious-mix', type=float, default=0.65, help='share of the unigram probability that comes from Qur\'an/Hadith counts')
A = ap.parse_args()

from wordfreq import top_n_list, word_frequency

AR = re.compile('^[ءآأؤإئابةتثجحخدذرزسشصضطظعغفقكلمنهوىي]+$')
def clean(w):
    return w if AR.match(w) and len(w) <= 20 and not re.search(r'(.)\1\1', w) and (len(w) > 1 or w == 'و') else None

# ---------------------------------------------------------------- vocabulary + unigram
wf = {}
for w in top_n_list('ar', 400000, wordlist='large'):
    c = clean(w)
    if c and c not in wf: wf[c] = word_frequency(c, 'ar', wordlist='large')
vocab_set = dict(list(wf.items())[:A.vocab])

rel = collections.Counter()
if A.data:
    for line in open(f'{A.data}/corpus/vocab_freq.tsv', encoding='utf-8'):
        p = line.rstrip('\n').split('\t')
        if p[0] == 'word' or len(p) < 4: continue
        w = clean(p[0])
        if w: rel[w] = int(p[1]) + int(p[3])      # Qur'an + hadith text without narrator chains
for w, c in rel.items():
    if c >= 2 and w not in vocab_set: vocab_set[w] = wf.get(w, 1e-9)
if A.variants:
    for line in open(A.variants, encoding='utf-8'):                # real chat words that are common enough in general text
        a = line.split('\t')[0]
        if a in wf and a not in vocab_set and wf[a] >= 2e-7: vocab_set[a] = wf[a]

curated = collections.OrderedDict()
for line in open(os.path.join(HERE, 'chat_curated.tsv'), encoding='utf-8'):
    if line.startswith('#') or '\t' not in line: continue
    a, v = line.rstrip('\n').split('\t')
    curated[re.sub(r'\s+', ' ', a.strip())] = [x for x in v.split(',') if x]
if A.data and os.path.exists(f'{A.data}/pairs/religious_terms_curated.tsv'):
    for line in open(f'{A.data}/pairs/religious_terms_curated.tsv', encoding='utf-8'):
        p = line.rstrip('\n').split('\t')
        if len(p) == 2 and p[0] != 'arabic':
            a = re.sub(r'\s+', ' ', p[0].strip())
            curated.setdefault(a, [])
            curated[a] += [x.strip().lower().replace(' ', '') for x in p[1].split(',') if x.strip()]
if A.exclude:
    for line in open(A.exclude, encoding='utf-8'): curated.pop(line.rstrip('\n').split('\t')[1], None)
FLOOR_CHAT, FLOOR_PHRASE = 4e-6, 1.5e-5
for a in curated:
    vocab_set[a] = max(vocab_set.get(a, 0), FLOOR_PHRASE if ' ' in a else FLOOR_CHAT)
hon = os.path.join(HERE, '../../app/src/main/assets/honorifics/honorifics.tsv')
for line in open(hon, encoding='utf-8'):
    p = line.rstrip('\n').split('\t')
    if line.startswith('#') or len(p) < 6 or p[0] != 'N': continue
    for tok in p[5].split():
        tok = clean(tok)
        if tok and tok not in vocab_set: vocab_set[tok] = 3e-6

vocab = list(vocab_set)
tw = sum(vocab_set[w] for w in vocab) or 1
tr = sum(rel[w] for w in vocab) or 1
uni = {w: (1 - A.religious_mix) * vocab_set[w] / tw + A.religious_mix * rel.get(w, 0) / tr + 1e-12 for w in vocab}
wid = {w: i for i, w in enumerate(vocab)}
print('vocabulary', len(vocab), 'curated', len(curated), 'religious types', len(rel))

# ---------------------------------------------------------------- bigrams (Qur'an + Hadith running text)
big = collections.Counter()
if A.data and not A.no_bigrams:
    for fn in ('quran_plain.txt', 'hadith_matn_plain.txt'):
        for line in open(f'{A.data}/corpus/{fn}', encoding='utf-8'):
            prev = '<s>'
            for tok in line.split():
                if tok in ('.', '،', '؛', '؟'): prev = '<s>'; continue
                if tok not in wid: prev = None; continue
                if prev is not None: big[(prev, tok)] += 1
                prev = tok
    big = {k: c for k, c in big.items() if c >= 2}
    by = collections.defaultdict(list); tot1 = collections.Counter()
    for (a, c), n in big.items(): tot1[a] += n
    for (a, c), n in big.items(): by[-1 if a == '<s>' else wid[a]].append((wid[c], -math.log(n / tot1[a])))
    for k in by: by[k] = sorted(sorted(by[k], key=lambda x: x[1])[:40])   # best 40 followers per word, stored sorted by id
else:
    by = {}
print('bigram contexts', len(by), 'bigrams', sum(len(v) for v in by.values()))

# ---------------------------------------------------------------- observed spellings
variants = collections.defaultdict(dict)
for a, vs in curated.items():
    for i, v in enumerate(vs):
        if re.fullmatch(r"[a-z0-9']+", v) and 1 <= len(v) <= 30:
            variants[a].setdefault(v, 0.8 if i == 0 else 0.6)
if A.variants:
    real = collections.defaultdict(collections.Counter)
    for line in open(A.variants, encoding='utf-8'):
        p = line.rstrip('\n').split('\t')
        if len(p) >= 2 and p[0] in wid and re.fullmatch(r"[a-z0-9']+", p[1]): real[p[0]][p[1]] += int(p[2]) if len(p) > 2 else 1
    for a, c in real.items():
        t = sum(c.values())
        for r, n in c.most_common(8): variants[a].setdefault(r, min(0.7, 0.35 + n / t * 0.35))

# ---------------------------------------------------------------- export
ch = json.load(open(A.channel, encoding='utf-8'))
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
for w in vocab:
    d = w.encode('utf-16-be'); b.append(len(d) // 2); b.extend(d)
    f32(math.log(uni[w]))
    outv = sorted(variants.get(w, {}).items(), key=lambda x: -x[1])[:12]
    b.append(len(outv))
    for r, q in outv: s(r); b.append(max(1, min(255, round(255 * q))))
keys = sorted(by)
i32(len(keys))
for k in keys:
    i32(k); i32(len(by[k])); prev = 0
    for w2, nl in by[k]:
        d = w2 - prev; prev = w2
        while d >= 128: b.append((d & 127) | 128); d >>= 7
        b.append(d); b.append(min(255, round(nl * 12)))
os.makedirs(os.path.dirname(os.path.abspath(A.out)), exist_ok=True)
open(A.out, 'wb').write(b)
print('wrote', A.out, len(b) // 1024, 'KB')
