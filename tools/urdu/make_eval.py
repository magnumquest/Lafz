import sys, re
sys.path.insert(0, '.')
from common import *
n = k = 0
with open('eval.tsv', 'w', encoding='utf-8') as f:
    for u, r in romanized_sentences():
        n += 1
        ut = tokens(u)
        rt = [re.sub('[^a-z]', '', t) for t in r.lower().split()]
        rt = [t for t in rt if t]
        if len(ut) == len(rt) and len(ut) > 0:
            k += 1; f.write(' '.join(ut) + '\t' + ' '.join(rt) + '\n')
print(k, 'of', n, 'sentences usable')
