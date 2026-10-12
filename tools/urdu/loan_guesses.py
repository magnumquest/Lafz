"""English word -> a few phonetic Roman-Urdu style guesses (from CMUdict), to be decoded by the transliterator.
Writes eng_guesses.tsv: english <TAB> guess1,guess2,..."""
import cmudict, re
d = cmudict.dict()
CONS = {'B':'b','CH':'ch','D':'d','DH':'d','F':'f','G':'g','HH':'h','JH':'j','K':'k','L':'l','M':'m','N':'n','NG':'ng',
        'P':'p','R':'r','S':'s','SH':'sh','T':'t','TH':'t','V':'v','W':'w','Y':'y','Z':'z','ZH':'zh'}
STYLES = [
 {'AA':'a','AE':'e','AH':'a','AO':'o','AW':'au','AY':'ai','EH':'e','ER':'ar','EY':'e','IH':'i','IY':'i','OW':'o','OY':'oi','UH':'u','UW':'u'},
 {'AA':'aa','AE':'a','AH':'','AO':'a','AW':'ao','AY':'ay','EH':'ai','ER':'r','EY':'ay','IH':'i','IY':'ee','OW':'o','OY':'oi','UH':'u','UW':'oo'},
 {'AA':'a','AE':'ai','AH':'a','AO':'au','AW':'au','AY':'ai','EH':'e','ER':'er','EY':'ei','IH':'e','IY':'ee','OW':'o','OY':'oi','UH':'oo','UW':'oo'},
]
def rom(ph, st):
    out = []
    for i, p in enumerate(ph):
        b = re.sub(r'\d', '', p)
        if b in CONS: out.append(CONS[b])
        else:
            v = st[b]
            if b == 'AH' and v == '' and (i == 0 or i == len(ph) - 1): v = 'a'
            out.append(v)
    return ''.join(out)
n = 0
with open('eng_guesses.tsv', 'w') as f:
    for w, prons in d.items():
        if not re.fullmatch('[a-z]{3,14}', w): continue
        g = []
        for st in STYLES:
            r = rom(prons[0], st)
            if r and r not in g: g.append(r)
        f.write(w + '\t' + ','.join(g) + '\n'); n += 1
print(n, 'english words')
