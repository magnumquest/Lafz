"""Mine Urdu<->English loanword pairs from Urdu Wikipedia text written like: کمپیوٹر (computer) / (Computer)"""
import gzip, re, collections, sys
sys.path.insert(0, '.')
from common import *
pat = re.compile(r'([ء-ۓ]+)\s*[\(\[،,]?\s*[\(\[]\s*([A-Za-z]{3,15})\s*[\)\]]')
cnt = collections.Counter(); n = 0
p = f"{DAK}/native_script_wikipedia/ur.wiki-full.text.sorted.tsv.gz"
for l in gzip.open(p, 'rt', encoding='utf-8'):
    n += 1
    for m in pat.finditer(l):
        cnt[(norm(m.group(1)), m.group(2).lower())] += 1
print(n, 'lines', len(cnt), 'distinct pairs')
import pickle; pickle.dump(cnt, open('mined_pairs.pkl', 'wb'))
for (u, e), c in cnt.most_common(40): print(u, e, c)

with open('mined_pairs.tsv', 'w', encoding='utf-8') as f:
    for (u, e), n in cnt.most_common():
        if n >= 2 and len(u) >= 3: f.write(f'{u}\t{e}\t{n}\n')
