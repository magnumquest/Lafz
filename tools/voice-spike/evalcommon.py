import re, csv, time, sys, unicodedata
import numpy as np, soundfile as sf, jiwer

MAP = str.maketrans({'ي':'ی','ى':'ی','ك':'ک','ه':'ہ','ة':'ہ','ۃ':'ہ','ھ':'ہ','ۂ':'ہ','أ':'ا','إ':'ا','آ':'ا','ٱ':'ا','ؤ':'و','ئ':'ی','ے':'ی','ې':'ی','ۓ':'ی'})
def norm(s, lang):
    s = unicodedata.normalize('NFKC', s)
    s = re.sub(r'[ً-ٰٟۖ-ۭـ]', '', s)         # diacritics, tatweel
    if lang == 'ur': s = s.translate(MAP)
    else: s = s.translate(str.maketrans({'ى':'ي','ة':'ه','أ':'ا','إ':'ا','آ':'ا','ٱ':'ا'}))
    s = re.sub(r'[^\w\s]', ' ', s)                                         # punctuation
    s = re.sub(r'[0-9٠-٩۰-۹]', '', s)                                       # digits differ between written and spoken forms
    return ' '.join(s.split())

def load_test(tsv, audio_dir, n=None):
    rows = []
    with open(tsv, encoding='utf-8') as f:
        for r in csv.reader(f, delimiter='\t'):
            rows.append((f'{audio_dir}/{r[1]}', r[3]))
    return rows[:n] if n else rows

def score(refs, hyps, lang):
    R = [norm(r, lang) for r in refs]; H = [norm(h, lang) for h in hyps]
    keep = [i for i, r in enumerate(R) if r]
    R = [R[i] for i in keep]; H = [H[i] for i in keep]
    return jiwer.cer(R, H), jiwer.wer(R, H)

def report(name, refs, hyps, lang, audio_s, wall_s, extra=''):
    cer, wer = score(refs, hyps, lang)
    print(f'RESULT {name} lang={lang} n={len(refs)} CER={cer*100:.1f}% WER={wer*100:.1f}% RTF={wall_s/audio_s:.3f} {extra}', flush=True)
