"""Devanagari (Hindustani speech recogniser output) -> Roman letters, tolerant spelling that Lafz's Roman->Urdu decoder understands."""
CONS = {'क':'k','ख':'kh','ग':'g','घ':'gh','ङ':'n','च':'ch','छ':'chh','ज':'j','झ':'jh','ञ':'n','ट':'t','ठ':'th','ड':'d','ढ':'dh','ण':'n',
        'त':'t','थ':'th','द':'d','ध':'dh','न':'n','प':'p','फ':'f','ब':'b','भ':'bh','म':'m','य':'y','र':'r','ल':'l','व':'w','श':'sh','ष':'sh','स':'s','ह':'h',
        'क़':'q','ख़':'kh','ग़':'gh','ज़':'z','ड़':'r','ढ़':'rh','फ़':'f','ऩ':'n','ऱ':'r','ळ':'l'}
NUKTA = {'क':'q','ख':'kh','ग':'gh','ज':'z','ड':'r','ढ':'rh','फ':'f'}
VOW = {'अ':'a','आ':'aa','इ':'i','ई':'ee','उ':'u','ऊ':'oo','ऋ':'ri','ए':'e','ऐ':'ai','ओ':'o','औ':'au','ऑ':'o','ऍ':'e'}
MATRA = {'ा':'aa','ि':'i','ी':'ee','ु':'u','ू':'oo','े':'e','ै':'ai','ो':'o','ौ':'au','ॉ':'o','ॅ':'e','ृ':'ri'}
def word(w):
    out = []; i = 0; n = len(w)
    while i < n:
        c = w[i]
        if c in CONS or c in NUKTA:
            base = c; i += 1
            if i < n and w[i] == '़': base = NUKTA.get(c, c); r = CONS.get(base, base) if base in CONS else base; i += 1
            else: r = CONS[c]
            out.append(r)
            if i < n and w[i] == '्': i += 1; continue                        # halant: no vowel
            if i < n and w[i] in MATRA: out.append(MATRA[w[i]]); i += 1; continue
            if i < n and w[i] in 'ंँः': out.append('a'); continue            # nasal after the inherent vowel
            if i < n and (w[i] in CONS or w[i] in VOW): out.append('a')        # inherent vowel inside a word
            elif i >= n: pass                                                  # word-final schwa is silent
            continue
        if c in VOW: out.append(VOW[c]); i += 1; continue
        if c in 'ंँ': out.append('n'); i += 1; continue
        if c == 'ः': out.append('h'); i += 1; continue
        if c in '़्': i += 1; continue
        out.append(c) if c.isascii() and c.isalnum() else None; i += 1
    return ''.join(out)
def sentence(s): return ' '.join(word(w) for w in s.split())
if __name__ == '__main__':
    import sys
    for l in sys.stdin: print(sentence(l.strip()))
