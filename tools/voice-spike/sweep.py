import subprocess, sys, os, json, urllib.request, time
R = '../models/sweep'
os.makedirs(R, exist_ok=True)
CANDS = [  # (eval script, repo, lang, n)
 ('whisper',   'kingabzpro/whisper-tiny-urdu', 'ur', 0),
 ('whisper',   'sharjeel103/whisper-tiny-urdu', 'ur', 0),
 ('whisper',   'kingabzpro/whisper-base-urdu-full', 'ur', 0),
 ('whisper',   'khawajaaliarshad/whisper-small-urdu', 'ur', 0),
 ('moonshine', 'shaeel12/moonshine-tiny-urdu-final-pta-nhi-ab', 'ur', 0),
 ('moonshine', 'shaeel12/moonshine-base-urdu-final-repo', 'ur', 0),
 ('ctc',       'kingabzpro/wav2vec2-large-xls-r-300m-Urdu', 'ur', 0),
 ('ctc',       'UmarRamzan/w2v2-bert-urdu', 'ur', 0),
 ('whisper',   'openai/whisper-tiny.en', 'en', 200),
 ('whisper',   'openai/whisper-base.en', 'en', 200),
 ('whisper',   'openai/whisper-small.en', 'en', 200),
 ('moonshine', 'UsefulSensors/moonshine-tiny', 'en', 200),
 ('moonshine', 'UsefulSensors/moonshine-base', 'en', 200),
 ('whisper',   'kingabzpro/whisper-large-v3-turbo-urdu', 'ur', 0),
]
only = sys.argv[1:]
def files(repo):
    info = json.load(urllib.request.urlopen(f'https://huggingface.co/api/models/{repo}', timeout=30))
    names = [s['rfilename'] for s in info.get('siblings', [])]
    return names
for kind, repo, lang, n in CANDS:
    if only and not any(o in repo for o in only): continue
    d = f"{R}/{repo.replace('/', '__')}"
    try:
        names = files(repo)
        weights = [x for x in names if x.endswith('.safetensors')] or [x for x in names if x == 'pytorch_model.bin']
        allow = [x for x in names if x.endswith(('.json', '.txt', '.model', '.vocab', '.tiktoken')) ] + weights
        code = ("from huggingface_hub import snapshot_download;"
                f"snapshot_download({repo!r}, local_dir={d!r}, allow_patterns={allow!r})")
        t0 = time.time()
        subprocess.run([sys.executable, '-c', code], timeout=1500, check=True, capture_output=True)
        size = sum(os.path.getsize(os.path.join(d, w)) for w in weights if os.path.exists(os.path.join(d, w))) / 1e6
        print(f'DOWNLOADED {repo} {size:.0f}MB in {time.time()-t0:.0f}s', flush=True)
        if kind == 'whisper' and not os.path.exists(d + '/preprocessor_config.json'):
            import shutil; shutil.copy('../models/whisper_small/preprocessor_config.json', d + '/preprocessor_config.json')
        out = subprocess.run([sys.executable, f'eval_{kind}.py', d, lang, str(n)], timeout=1800, capture_output=True, text=True)
        res = [l for l in out.stdout.splitlines() if l.startswith('RESULT')]
        print(res[0] + f' size={size:.0f}MB repo={repo}' if res else f'FAILED {repo}: {out.stderr.strip().splitlines()[-1][:200] if out.stderr.strip() else out.stdout[-200:]}', flush=True)
    except Exception as e:
        print(f'FAILED {repo}: {str(e)[:200]}', flush=True)
print('SWEEP DONE', flush=True)
