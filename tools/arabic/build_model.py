"""Build app/src/main/assets/arabic/arabic_translit.bin (format "UTRL" v1, read by RomanTransliterator.load).

    pip install wordfreq
    python3 channel_prior.py            # (or train_channel.py once Arabizi pairs exist) -> channel.json
    python3 build_model.py [OUT.bin] [--vocab 100000]

Data: unigram frequencies and vocabulary come from `wordfreq` (Arabic; compiled from Wikipedia, subtitles, news, Twitter,
web text; data licensed CC BY-SA 4.0 -> keep the attribution in the app's credits). There is NO bigram table (no running
Arabic text was available when this was built), so the language model is a pure unigram and next-word prediction is
only offered for honorific suffixes. Spellings in chat_curated.tsv add dictionary entries (dialect words, loanwords,
fused religious phrases) and "observed" Roman spellings. Names from the honorifics file are guaranteed to be in the dictionary."""
import sys, re, json, math, struct, collections, os

HERE = os.path.dirname(os.path.abspath(__file__))
args = [a for a in sys.argv[1:] if not a.startswith('--')]
OUT = args[0] if args else os.path.join(HERE, '../../app/src/main/assets/arabic/arabic_translit.bin')
V = int(sys.argv[sys.argv.index('--vocab') + 1]) if '--vocab' in sys.argv else 100000
if '--vocab' in sys.argv and args and args[-1] == str(V): args.remove(str(V)); OUT = args[0] if args else OUT

from wordfreq import top_n_list, word_frequency

AR = re.compile('^[ءآأؤإئابةتثجحخدذرزسشصضطظعغفقكلمنهوىي]+$')

def clean(w):
    return w if AR.match(w) and len(w) <= 20 and not re.search(r'(.)\1\1', w) and (len(w) > 1 or w == 'و') else None

# ---------------------------------------------------------------- vocabulary + unigram
words = {}
for w in top_n_list('ar', 400000, wordlist='large'):
    w = clean(w)
    if w and w not in words:
        words[w] = word_frequency(w, 'ar', wordlist='large')
        if len(words) >= V: break

def fold(w):  # spelling-insensitive key for "is this word already in the dictionary"
    return w

# chat / dialect / loanword entries
curated = collections.OrderedDict()
for line in open(os.path.join(HERE, 'chat_curated.tsv'), encoding='utf-8'):
    if line.startswith('#') or '\t' not in line: continue
    a, v = line.rstrip('\n').split('\t')
    a = re.sub(r'\s+', ' ', a.strip())
    curated[a] = [x for x in v.split(',') if x]
FLOOR_CHAT = 4e-6    # roughly zipf 3.6
FLOOR_PHRASE = 1.5e-5
for a, vs in curated.items():
    f = FLOOR_PHRASE if ' ' in a else FLOOR_CHAT
    words[a] = max(words.get(a, 0), f)

# honorific names (column 6 of honorifics.tsv): make sure every name word exists
hon = os.path.join(HERE, '../../app/src/main/assets/honorifics/honorifics.tsv')
if os.path.exists(hon):
    for line in open(hon, encoding='utf-8'):
        p = line.rstrip('\n').split('\t')
        if line.startswith('#') or len(p) < 6 or p[0] != 'N': continue
        for tok in p[5].split():
            tok = clean(tok)
            if tok and tok not in words: words[tok] = 3e-6

tot = sum(words.values())
vocab = list(words)
print('vocabulary', len(vocab), 'curated', len(curated))

# ---------------------------------------------------------------- observed spellings
variants = collections.defaultdict(dict)
for a, vs in curated.items():
    for i, v in enumerate(vs):
        if re.fullmatch(r"[a-z0-9']+", v) and 1 <= len(v) <= 30:
            variants[a].setdefault(v, 0.8 if i == 0 else 0.6)

# ---------------------------------------------------------------- export
ch = json.load(open(os.path.join(HERE, 'channel.json'), encoding='utf-8'))
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
    f32(math.log(words[w] / tot))
    outv = sorted(variants.get(w, {}).items(), key=lambda x: -x[1])[:12]
    b.append(len(outv))
    for r, q in outv: s(r); b.append(max(1, min(255, round(255 * q))))
i32(0)   # no bigrams
os.makedirs(os.path.dirname(os.path.abspath(OUT)), exist_ok=True)
open(OUT, 'wb').write(b)
print('wrote', OUT, len(b) // 1024, 'KB')
