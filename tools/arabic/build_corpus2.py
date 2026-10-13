#!/usr/bin/env python3
"""Normalise the big Arabic text corpora downloaded to data/Arabic/raw2 into one-sentence-per-line token files in data/Arabic/corpus2.

    python3 -I tools/arabic/build_corpus2.py [subs wiki cc tashkeela]      (default: everything that is downloaded)

Outputs (corpus2/):  <src>_plain.txt   tokens separated by spaces, harakat stripped, only Arabic-letter tokens, one sentence per line
                     tashkeela_diac.txt  the diacritized sentences (vowels known: used to romanize words phonetically)
                     counts_<src>.tsv    word<TAB>count
Sources: subs = OpenSubtitles (conversation, MSA + dialect), wiki = Arabic Wikipedia, cc = CC-100 Arabic web text, tashkeela = classical diacritized books.
"""
import collections, glob, gzip, io, lzma, multiprocessing as mp, os, re, sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from arnorm import strip_marks

ROOT = os.path.join(HERE, '..', '..', 'data', 'Arabic')
RAW, OUT = os.path.join(ROOT, 'raw2'), os.path.join(ROOT, 'corpus2')
TOK = re.compile('[ءآأؤإئابةتثجحخدذرزسشصضطظعغفقكلمنهوىي]+')
SENT = re.compile(r'[.!?؟;؛:\n\r"«»()\[\]{}<>…،,\-–—*/\\|]+')
ELONG = re.compile(r'(.)\1\1')


def norm_text(text):
    """-> list of token lists (sentences)"""
    out = []
    t = strip_marks(text)
    for part in SENT.split(t):
        toks = [w for w in TOK.findall(part) if len(w) <= 20 and not ELONG.search(w)]
        if len(toks) >= 2: out.append(toks)
    return out


def work(batch):
    cnt = collections.Counter(); lines = []
    if isinstance(batch, bytes): batch = batch.decode('utf-8', 'replace').split('\n')
    for text in batch:
        for toks in norm_text(text):
            cnt.update(toks); lines.append(' '.join(toks))
    return cnt, lines


def batches(it, n=2000):
    b = []
    for x in it:
        b.append(x)
        if len(b) >= n: yield b; b = []
    if b: yield b


def byte_chunks(stream, size=32 << 20):
    """big raw chunks cut at line ends; decoding and tokenising happen in the workers"""
    rest = b''
    while True:
        buf = stream.read(size)
        if not buf:
            if rest: yield rest
            return
        buf = rest + buf
        i = buf.rfind(b'\n')
        if i < 0: rest = buf; continue
        yield buf[:i]; rest = buf[i + 1:]


def run(src, text_iter, pool):
    total = collections.Counter(); nl = 0
    with open(os.path.join(OUT, f'{src}_plain.txt'), 'w', encoding='utf-8') as f:
        for cnt, lines in pool.imap(work, text_iter if src == 'cc' else batches(text_iter), chunksize=1):
            total.update(cnt); nl += len(lines)
            if lines: f.write('\n'.join(lines) + '\n')
    with open(os.path.join(OUT, f'counts_{src}.tsv'), 'w', encoding='utf-8') as f:
        for w, c in total.most_common(): f.write(f'{w}\t{c}\n')
    print(f'{src}: {nl} sentences, {sum(total.values())} tokens, {len(total)} types', flush=True)


def parquet_texts(pattern, col):
    import pyarrow.parquet as pq
    for fn in sorted(glob.glob(os.path.join(RAW, pattern))):
        if fn.endswith('.part'): continue
        pf = pq.ParquetFile(fn)
        for g in range(pf.num_row_groups):
            for v in pf.read_row_group(g, columns=[col]).column(col).to_pylist():
                if v: yield v


def main():
    os.makedirs(OUT, exist_ok=True)
    want = sys.argv[1:] or ['subs', 'wiki', 'cc', 'tashkeela']
    with mp.Pool(28) as pool:
        if 'subs' in want and os.path.exists(os.path.join(RAW, 'opensubtitles_ar.txt.gz')):
            run('subs', (l for l in io.TextIOWrapper(gzip.open(os.path.join(RAW, 'opensubtitles_ar.txt.gz')), encoding='utf-8')), pool)
        if 'wiki' in want and glob.glob(os.path.join(RAW, 'wiki_ar_*.parquet')):
            run('wiki', parquet_texts('wiki_ar_*.parquet', 'text'), pool)
        if 'cc' in want and os.path.exists(os.path.join(RAW, 'cc100_ar.txt.xz')):
            import subprocess  # xz -T decompresses the independent blocks in parallel
            proc = subprocess.Popen(['xz', '-dc', '-T8', os.path.join(RAW, 'cc100_ar.txt.xz')], stdout=subprocess.PIPE)
            run('cc', byte_chunks(proc.stdout), pool)
        if 'tashkeela' in want and glob.glob(os.path.join(RAW, 'tashkeela_*.parquet')):
            # plain counts from the plain column, diacritized sentences kept for phonetic romanization
            run('tashkeela', parquet_texts('tashkeela_*.parquet', 'text'), pool)
            n = 0
            with open(os.path.join(OUT, 'tashkeela_diac.txt'), 'w', encoding='utf-8') as f:
                for v in parquet_texts('tashkeela_*.parquet', 'diacratized'):
                    v = re.sub(r'\s+', ' ', v).strip()
                    if v: f.write(v + '\n'); n += 1
            print('tashkeela_diac:', n, 'lines')


if __name__ == '__main__':
    main()
