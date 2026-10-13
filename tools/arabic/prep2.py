#!/usr/bin/env python3
"""work2/real_pairs.tsv: real chat spellings (prep_data.py's real_train.tsv) + hand-written religious terms, annotated with the article pseudo-letters.
   Run after prep_data.py and make_pairs2.py.   python3 -I prep2.py"""
import os, sys
HERE = os.path.dirname(os.path.abspath(__file__)); sys.path.insert(0, HERE)
import pseudo
ROOT = os.path.join(HERE, '..', '..', 'data', 'Arabic', 'corpus2')
known = set()
for s in ('subs', 'wiki', 'cc', 'tashkeela'):
    p = os.path.join(ROOT, f'counts_{s}.tsv')
    if os.path.exists(p):
        for line in open(p, encoding='utf-8'):
            w, n = line.split('\t')
            if int(n) >= 5: known.add(w)
n = 0
with open(os.path.join(HERE, 'work2', 'real_pairs.tsv'), 'w', encoding='utf-8') as out:
    for line in open(os.path.join(HERE, 'work', 'real_train.tsv'), encoding='utf-8'):
        a, r, c = line.rstrip('\n').split('\t')
        out.write(f'{pseudo.annotate(a, known)}\t{r}\t{c}\t2.0\treal\n'); n += 1
    for line in open(os.path.join(HERE, 'work', 'train_pairs.tsv'), encoding='utf-8'):
        p = line.rstrip('\n').split('\t')
        if len(p) > 4 and p[4] == 'curated': out.write(f'{pseudo.annotate(p[0], known)}\t{p[1]}\t1\t3.0\tcurated\n'); n += 1
print('real_pairs rows', n)
