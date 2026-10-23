import sys, time, glob, os
sys.path.insert(0, os.path.dirname(__file__))
from evalcommon import *
import sherpa_onnx as so
kind, model_dir, lang, n, threads = sys.argv[1], sys.argv[2], sys.argv[3], int(sys.argv[4]), int(sys.argv[5])
test = {'ur': ('../data/fleurs_ur/data/ur_pk/test.tsv', '../data/fleurs_ur/data/ur_pk/audio/test'),
        'ar': ('../data/fleurs_ar/data/ar_eg/test.tsv', '../data/fleurs_ar/data/ar_eg/audio/test')}[lang]
rows = load_test(*test, n=n or None)
if kind == 'omni':
    f = glob.glob(model_dir + '/model*.onnx')[0]
    rec = so.OfflineRecognizer.from_omnilingual_asr_ctc(model=f, tokens=model_dir + '/tokens.txt', num_threads=threads, debug=False)
else:
    raise SystemExit('unknown kind')
hyps, refs, audio_s = [], [], 0.0
t0 = time.time()
for path, ref in rows:
    x, sr = sf.read(path, dtype='float32')
    if x.ndim > 1: x = x.mean(axis=1)
    audio_s += len(x) / sr
    s = rec.create_stream(); s.accept_waveform(sr, x); rec.decode_stream(s)
    hyps.append(s.result.text); refs.append(ref)
wall = time.time() - t0
open(f'hyp_{kind}_{lang}.tsv','w',encoding='utf-8').write(''.join(f'{r}\t{h}\n' for r,h in zip(refs,hyps)))
for r, h in list(zip(refs, hyps))[:3]: print('REF', r, '\nHYP', h, flush=True)
report(f'{kind}:{os.path.basename(model_dir)} threads={threads}', refs, hyps, lang, audio_s, wall)
