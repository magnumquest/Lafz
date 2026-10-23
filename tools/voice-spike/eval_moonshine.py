import sys, time, os
sys.path.insert(0, os.path.dirname(__file__))
import fakeflash
from evalcommon import *
import torch
from transformers import AutoProcessor, MoonshineForConditionalGeneration
model_dir, lang, n = sys.argv[1], sys.argv[2], int(sys.argv[3])
test = {'ar': ('../data/fleurs_ar/data/ar_eg/test.tsv', '../data/fleurs_ar/data/ar_eg/audio/test')}[lang]
rows = load_test(*test, n=n or None)
proc = AutoProcessor.from_pretrained(model_dir)
model = MoonshineForConditionalGeneration.from_pretrained(model_dir, torch_dtype=torch.float32).to('cuda:0').eval()
hyps, refs, audio_s = [], [], 0.0
t0 = time.time()
for path, ref in rows:
    x, sr = sf.read(path, dtype='float32')
    if x.ndim > 1: x = x.mean(axis=1)
    audio_s += len(x) / sr
    inp = proc(x, sampling_rate=16000, return_tensors='pt').to('cuda:0')
    with torch.no_grad():
        ids = model.generate(**inp, max_new_tokens=min(400, int(len(x) / sr * 8) + 10))
    hyps.append(proc.batch_decode(ids, skip_special_tokens=True)[0]); refs.append(ref)
wall = time.time() - t0
for r, h in list(zip(refs, hyps))[:3]: print('REF', r, '\nHYP', h, flush=True)
report(f'moonshine:{os.path.basename(model_dir)} (GPU fp32, one clip at a time)', refs, hyps, lang, audio_s, wall)
