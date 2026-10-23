import sys, os, time, subprocess, glob, shutil
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from evalcommon import *
model, lang, n = sys.argv[1], sys.argv[2], int(sys.argv[3])
threads = sys.argv[4] if len(sys.argv) > 4 else '8'
test = {'ur': ('../data/fleurs_ur/data/ur_pk/test.tsv', '../data/fleurs_ur/data/ur_pk/audio/test'),
        'en': ('../data/fleurs_en/data/en_us/test.tsv', '../data/fleurs_en/data/en_us/audio/test')}[lang]
rows = load_test(test[0], test[1], n=n or None)
work = f'../ggml/work_{os.path.basename(model)}_{lang}'
shutil.rmtree(work, ignore_errors=True); os.makedirs(work)
audio_s = 0.0; files = []
for i, (path, ref) in enumerate(rows):
    x, sr = sf.read(path, dtype='float32'); audio_s += len(x) / sr
    f = f'{work}/{i:04d}.wav'; sf.write(f, x, sr, subtype='PCM_16'); files.append(f)
t0 = time.time()
subprocess.run(['../whisper.cpp/build/bin/whisper-cli', '-m', model, '-l', lang, '-t', threads, '-nt', '-np', '-otxt', '-ng', '-f', *files], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, check=True)
wall = time.time() - t0
hyps = [open(f + '.txt', encoding='utf-8').read().strip() for f in files]
refs = [r for _, r in rows]
for r, h in list(zip(refs, hyps))[:2]: print('REF', r, '\nHYP', h, flush=True)
report(f'ggml:{os.path.basename(model)} (CPU {threads} threads)', refs, hyps, lang, audio_s, wall)
