import sys; sys.path.insert(0,'.')
from common import *
from collections import Counter
c=Counter(); n=0
for l in native_lines():
    n+=1; c.update(tokens(l))
tot=sum(c.values()); print("lines",n,"tokens",tot,"types",len(c))
mc=c.most_common()
for k in (20000,50000,100000):
    print(k, sum(v for _,v in mc[:k])/tot)
V50=set(w for w,_ in mc[:50000])
lex=Counter()
for u,r,a in lexicon('train'): lex[u]+=a
print("lex words",len(lex), "in top50k", sum(1 for w in lex if w in V50), "in corpus", sum(1 for w in lex if w in c))
rs=list(romanized_sentences()); print(len(rs))
V=set(w for w,_ in mc[:60000]); tt=ii=0
for u,r in rs:
    for w in tokens(u):
        tt+=1; ii+= w in V
print("romanized-eval token coverage by top60k vocab", ii/tt, tt)
print(rs[100]); print(rs[2000])
