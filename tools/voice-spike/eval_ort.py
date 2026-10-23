"""CER/WER of a CTC onnx model run with plain onnxruntime + greedy decoding (the algorithm the Android code will use)."""
import sys, time, os
sys.path.insert(0, os.path.dirname(__file__))
from evalcommon import *
from ctc_ref import load_tokens, greedy
import onnxruntime as ort
model, tokens, lang, n, blank, norm, threads = sys.argv[1], sys.argv[2], sys.argv[3], int(sys.argv[4]), int(sys.argv[5]), sys.argv[6] == 'norm', int(sys.argv[7])
test = {'ur': ('../data/fleurs_ur/data/ur_pk/test.tsv', '../data/fleurs_ur/data/ur_pk/audio/test'),
        'ar': ('../data/fleurs_ar/data/ar_eg/test.tsv', '../data/fleurs_ar/data/ar_eg/audio/test'),
        'en': ('../data/fleurs_en/data/en_us/test.tsv', '../data/fleurs_en/data/en_us/audio/test')}[lang]
so = ort.SessionOptions(); so.intra_op_num_threads = threads
sess = ort.InferenceSession(model, so, providers=['CPUExecutionProvider'])
toks = load_tokens(tokens)
hyps, refs, audio_s = [], [], 0.0
t0 = time.time()
for path, ref in load_test(*test, n=n or None):
    x, sr = sf.read(path, dtype='float32')
    if x.ndim > 1: x = x.mean(axis=1)
    audio_s += len(x) / sr
    if norm: x = (x - x.mean()) / (x.std() + 1e-7)
    lg = sess.run(None, {'x': x[None, :]})[0][0]
    hyps.append(greedy(lg, toks, blank, skip=(0, 2, 3)).replace('▁', ' ')); refs.append(ref)
wall = time.time() - t0
open(f'hyp_ort_{os.path.basename(model)}_{lang}_{"norm" if norm else "raw"}.tsv', 'w', encoding='utf-8').write(''.join(f'{r}\t{h}\n' for r, h in zip(refs, hyps)))
for r, h in list(zip(refs, hyps))[:2]: print('REF', r, '\nHYP', h, flush=True)
report(f'ort:{os.path.basename(model)} {"norm" if norm else "raw"} threads={threads}', refs, hyps, lang, audio_s, wall)
