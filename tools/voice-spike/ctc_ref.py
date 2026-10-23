"""Reference CTC decode with plain onnxruntime (what the Android code will do): waveform -> logits -> greedy -> tokens."""
import sys, numpy as np, soundfile as sf, onnxruntime as ort
sys.path.insert(0, '.')
from evalcommon import load_test
def load_tokens(path):
    toks = {}
    for l in open(path, encoding='utf-8'):
        l = l.rstrip('\n'); i = l.rfind(' ')
        toks[int(l[i+1:])] = l[:i]
    return toks
def greedy(logits, toks, blank, skip=()):
    ids = logits.argmax(-1); out = []; prev = -1
    for i in ids:
        if i != prev and i != blank and i not in skip: out.append(toks.get(int(i), ''))
        prev = i
    return ''.join(out)
if __name__ == '__main__':
    model, tokens, lang, n, blank, norm = sys.argv[1], sys.argv[2], sys.argv[3], int(sys.argv[4]), int(sys.argv[5]), sys.argv[6] == 'norm'
    sess = ort.InferenceSession(model, providers=['CPUExecutionProvider'])
    toks = load_tokens(tokens)
    test = {'ur': ('../data/fleurs_ur/data/ur_pk/test.tsv', '../data/fleurs_ur/data/ur_pk/audio/test'),
            'ar': ('../data/fleurs_ar/data/ar_eg/test.tsv', '../data/fleurs_ar/data/ar_eg/audio/test')}[lang]
    for path, ref in load_test(*test, n=n):
        x, sr = sf.read(path, dtype='float32')
        if norm: x = (x - x.mean()) / (x.std() + 1e-7)
        lg = sess.run(None, {'x': x[None, :]})[0][0]
        print('REF', ref[:90]); print('HYP', greedy(lg, toks, blank, skip=(0, 2, 3)).replace('▁', ' ')[:90], flush=True)
