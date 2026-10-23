import sys, time, os
import types, importlib.machinery
from unittest.mock import MagicMock
class _Fake(types.ModuleType):   # the system flash_attn is built for CUDA 12 and unused here
    def __getattr__(self, n):
        if n.startswith('__'): raise AttributeError(n)
        return MagicMock()
for _m in ('flash_attn', 'flash_attn.flash_attn_interface', 'flash_attn.bert_padding', 'flash_attn_2_cuda'):
    _f = _Fake(_m); _f.__spec__ = importlib.machinery.ModuleSpec(_m, None); _f.__path__ = []; sys.modules[_m] = _f
sys.path.insert(0, os.path.dirname(__file__))
from evalcommon import *
import torch
from transformers import WhisperForConditionalGeneration, WhisperProcessor
model_dir, lang, n = sys.argv[1], sys.argv[2], int(sys.argv[3])
test = {'ur': ('../data/fleurs_ur/data/ur_pk/test.tsv', '../data/fleurs_ur/data/ur_pk/audio/test', 'urdu'),
        'ar': ('../data/fleurs_ar/data/ar_eg/test.tsv', '../data/fleurs_ar/data/ar_eg/audio/test', 'arabic'),
        'en': ('../data/fleurs_en/data/en_us/test.tsv', '../data/fleurs_en/data/en_us/audio/test', 'english')}[lang]
rows = load_test(test[0], test[1], n=n or None)
proc = WhisperProcessor.from_pretrained(model_dir if os.path.exists(model_dir + '/preprocessor_config.json') else '../models/whisper_small')
model = WhisperForConditionalGeneration.from_pretrained(model_dir, torch_dtype=torch.float16).to('cuda:0').eval()
audio, refs = [], []
audio_s = 0.0
for path, ref in rows:
    x, sr = sf.read(path, dtype='float32')
    if x.ndim > 1: x = x.mean(axis=1)
    audio.append({'raw': x, 'sampling_rate': sr}); refs.append(ref); audio_s += len(x) / sr
t0 = time.time()
hyps = []
for i in range(0, len(audio), 16):
    batch = [a['raw'] for a in audio[i:i+16]]
    feats = proc.feature_extractor(batch, sampling_rate=16000, return_tensors='pt').input_features.to('cuda:0', torch.float16)
    with torch.no_grad():
        ids = model.generate(feats, **({} if not getattr(model.generation_config, 'is_multilingual', True) else {'language': test[2], 'task': 'transcribe'}), max_new_tokens=220)
    hyps += proc.batch_decode(ids, skip_special_tokens=True)
wall = time.time() - t0
for r, h in list(zip(refs, hyps))[:3]: print('REF', r, '\nHYP', h, flush=True)
report(f'whisper:{os.path.basename(model_dir)} (GPU fp16, batch 16)', refs, hyps, lang, audio_s, wall)
