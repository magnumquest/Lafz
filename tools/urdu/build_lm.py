"""Count unigrams/bigrams over the Dakshina native-script Wikipedia text -> lm.pkl"""
import sys, pickle, collections
sys.path.insert(0, '.')
from common import *
V = int(sys.argv[1]) if len(sys.argv) > 1 and sys.argv[1].isdigit() else 60000
uni = collections.Counter(); big = collections.Counter()
# hold out the evaluation sentences (Dakshina romanized set) so measured accuracy is honest
HELD = set(norm(u.strip()) for u, _ in romanized_sentences()) if '--holdout' in sys.argv else set()
HELD_SENT = set()
for u, _ in romanized_sentences():
    if '--holdout' in sys.argv: HELD_SENT.add(' '.join(tokens(u)))
for l in native_lines():
    if HELD_SENT and ' '.join(tokens(l)) in HELD_SENT: continue
    for s in re.split('[۔؟!\n.?]', l):
        t = ['<s>'] + tokens(s)
        if len(t) < 2: continue
        uni.update(t[1:])
        big.update(zip(t, t[1:]))
vocab = [w for w, _ in uni.most_common(V)]
vs = set(vocab) | {'<s>'}
big = {k: c for k, c in big.items() if k[0] in vs and k[1] in vs and c >= 2}
print('vocab', len(vocab), 'bigrams kept', len(big))
pickle.dump({'vocab': vocab, 'uni': dict(uni), 'big': big}, open('lm.pkl', 'wb'))
