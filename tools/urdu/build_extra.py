"""Builds extra.json: dictionary entries that are not in the Wikipedia vocabulary (curated loanwords, generated
verb phrases such as 'آؤں گا') and English-spelling variants for loanwords ('computer' -> کمپیوٹر)."""
import sys, json, pickle, collections
sys.path.insert(0, '.')
from common import *
from wordfreq import zipf_frequency
lm = pickle.load(open('lm.pkl', 'rb')); uni = lm['uni']; V = set(lm['vocab'])
total = sum(uni.values())
words = {}                                   # urdu entry -> prior count (only used if not already in vocab)
variants = collections.defaultdict(dict)     # urdu entry -> {english: weight 0..1}
def addv(u, e, w):
    variants[u][e] = max(w, variants[u].get(e, 0))
# 1. curated (most trusted)
for l in open('loanwords_curated.tsv', encoding='utf-8'):
    e, u = l.rstrip('\n').split('\t')
    if u not in V: words[u] = max(words.get(u, 0), 300)
    addv(u, e.lower(), 0.6)
# 2. mined pairs confirmed by the decoder
for l in open('mined_ok.tsv', encoding='utf-8'):
    u, e, n = l.rstrip('\n').split('\t')
    if u in V and zipf_frequency(e, 'en') >= 2.5: addv(u, e, 0.3)
# 3. decoder-voted pairs for common English words
for l in open('loanwords_raw.tsv', encoding='utf-8'):
    e, u, v = l.rstrip('\n').split('\t')
    if int(v) >= 3 and zipf_frequency(e, 'en') >= 3.5 and u in V: addv(u, e, 0.15)
# 4. verb phrases (future tense), from infinitives attested in the corpus
def forms(stem):
    last = stem[-1]
    if stem == 'ہو': return ('ہوں', 'ہو', 'ہوں', 'ہو')
    if stem == 'لی': return ('لوں', 'لے', 'لیں', 'لو')
    if stem == 'دی': return ('دوں', 'دے', 'دیں', 'دو')
    if last in 'اآ': return (stem + 'ؤں', stem + 'ئے', stem + 'ئیں', stem + 'ؤ')
    if last == 'و': return (stem + 'ؤں', stem + 'ئے', stem + 'ئیں', stem)
    if last in 'یے': return None
    return (stem + 'وں', stem + 'ے', stem + 'یں', stem + 'و')
nverb = 0
SEED_VERBS = """کرنا ہونا جانا آنا دینا لینا کہنا رہنا پینا کھانا سونا رونا بولنا چلنا سننا ملنا پوچھنا بتانا بلانا دیکھنا لکھنا پڑھنا
اٹھنا بیٹھنا کھیلنا ہنسنا خریدنا بھیجنا بھولنا سمجھنا سوچنا کھولنا رکنا بنانا لانا لگنا ڈالنا رکھنا اٹھانا سکھانا ملانا چھوڑنا
پہنچنا گھومنا نہانا بچنا جیتنا ہارنا سیکھنا نکلنا مانگنا چاہنا لڑنا ڈرنا مارنا سنانا دکھانا بھجوانا کھلانا پکانا چھپانا دوڑنا
اڑنا گرنا اترنا چڑھنا بدلنا بیچنا توڑنا جوڑنا روکنا ٹھہرنا لوٹنا پکڑنا سنبھالنا سمجھانا بھگانا جگانا سلانا ہنسانا""".split()
def regular_infinitive(w, c):
    if not (w.endswith('نا') and len(w) >= 4 and c >= 6): return False
    stem = w[:-2]
    return all(uni.get(stem + x, 0) >= 3 for x in ('تا', 'تی', 'تے'))
cand = {w: uni.get(w, 0) for w in SEED_VERBS}
for w, c in uni.items():
    if regular_infinitive(w, c): cand[w] = c
cand.update({w: max(c, 300) for w, c in cand.items() if w in SEED_VERBS})
for w, c in cand.items():
    stem = w[:-2]
    f = forms(stem)
    if not f: continue
    s1, s3, pl, s2 = f
    prior = max(40, int(c * 0.03))
    for base, auxs in ((s1, ('گا', 'گی')), (s3, ('گا', 'گی')), (pl, ('گے', 'گی')), (s2, ('گے', 'گی'))):
        for aux in auxs:
            p = base + ' ' + aux
            if p not in words: words[p] = prior; nverb += 1
print('curated/extra words', len(words), '(verb phrases', nverb, ') variant entries', len(variants), 'variants', sum(len(v) for v in variants.values()))
json.dump({'words': words, 'variants': variants}, open('extra.json', 'w'), ensure_ascii=False)
