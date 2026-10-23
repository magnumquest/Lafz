import sys, time, os
sys.path.insert(0, os.path.dirname(__file__))
import fakeflash
from evalcommon import *
import torch
from transformers import AutoModelForCTC, AutoProcessor
model_dir, lang, n = sys.argv[1], sys.argv[2], int(sys.argv[3])
test = {'ur': ('../data/fleurs_ur/data/ur_pk/test.tsv', '../data/fleurs_ur/data/ur_pk/audio/test'),
        'en': ('../data/fleurs_en/data/en_us/test.tsv', '../data/fleurs_en/data/en_us/audio/test')}[lang]
rows = load_test(*test, n=n or None)
proc = AutoProcessor.from_pretrained(model_dir)
model = AutoModelForCTC.from_pretrained(model_dir).to('cuda:0').eval()
hyps, refs, audio_s = [], [], 0.0
t0 = time.time()
for path, ref in rows:
    x, sr = sf.read(path, dtype='float32')
    if x.ndim > 1: x = x.mean(axis=1)
    audio_s += len(x) / sr
    fe = getattr(proc, 'feature_extractor', proc)
    inp = fe(x, sampling_rate=16000, return_tensors='pt')
    key = 'input_values' if 'input_values' in inp else 'input_features'
    with torch.no_grad():
        logits = model(inp[key].to('cuda:0')).logits
    tok = getattr(proc, 'tokenizer', proc)
    hyps.append(tok.batch_decode(logits.argmax(-1).cpu())[0]); refs.append(ref)
wall = time.time() - t0
for r, h in list(zip(refs, hyps))[:2]: print('REF', r, '\nHYP', h, flush=True)
report(f'ctc:{os.path.basename(model_dir)} (GPU)', refs, hyps, lang, audio_s, wall, f'params={sum(p.numel() for p in model.parameters())/1e6:.0f}M')
