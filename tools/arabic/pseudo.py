"""Context pseudo-letters for the Arabic channel model.

The channel (RomanTransliterator) conditions each letter's Roman chunks on the letter and its position in the word only.
Two very common contextual effects are therefore folded into the *letters*: words are stored and trained with private-use
characters, and the display text maps them back (the model file carries the mapping).

  PS_WASL      the alif of the definite article ال (hamzat wasl): "al" at the start of a word, but silent after a prefix or when the
               previous word ends in a vowel ("innamal'amal" = إنما الأعمال, "bil", "wal")
  PS_ASSIM[x]  the lam of ال before a sun letter x (ت ث د ذ ر ز س ش ص ض ط ظ ل ن): pronounced as x ("an-nas", "ash-shams", "binniyyat"),
               so Roman typing has "n"/"sh"/"" there but not "l" (though many people write "alnas")

annotate() decides by spelling only, using a set of known words to avoid treating words such as والد (father) as articles.
"""
PS_WASL = ''
SUN = 'تثدذرزسشصضطظلن'
PS_ASSIM = {c: chr(0xE010 + i) for i, c in enumerate(SUN)}
PROCLITIC = 'وفبك'
REAL = {PS_WASL: 'ا'}
REAL.update({v: 'ل' for v in PS_ASSIM.values()})
MAP = sorted(REAL.items())             # (pseudo, real) pairs written to the model file


def article_at(w, known=None):
    """index of the article's alif in plain word w (0, or 1 after one of و ف ب ك), or -1"""
    for a in (0, 1):
        if a == 1 and (len(w) < 2 or w[0] not in PROCLITIC): break
        if len(w) >= a + 3 and w[a] == 'ا' and w[a + 1] == 'ل':
            stem = w[a + 2:]
            if known is None or stem in known: return a
    return -1


def annotate(w, known=None):
    a = article_at(w, known)
    if a < 0: return w
    nxt = w[a + 2]
    lam = PS_ASSIM[nxt] if nxt in SUN else 'ل'
    return w[:a] + PS_WASL + lam + w[a + 2:]


def display(s):
    return ''.join(REAL.get(c, c) for c in s)


def prior_for(th):
    """extend a channel prior {letter+pos: {chunk: p}} with the pseudo-letters (copies of the base letter with article-specific weights)"""
    out = dict(th)
    for p in 'IMF':
        base = th.get('ا' + p, {})
        # wasl alif: spoken "a" (also e/i) at the start of a word, silent when glued after a vowel or prefix
        w = {'a': 5.0, '': 3.0, 'e': 1.5, 'i': 1.0, 'u': 0.4, 'o': 0.4, 'aa': 0.4} if p == 'I' else \
            {'': 6.0, 'a': 2.0, 'i': 1.0, 'e': 0.8, 'u': 0.3}
        s = sum(w.values()); out[PS_WASL + p] = {k: v / s for k, v in w.items()}
        for x, ps in PS_ASSIM.items():
            lam = th.get('ل' + p, {})
            roman = {}
            for ch, v in th.get(x + 'M', {}).items():                  # the sun letter's own consonant (n, sh, s ...), no vowel part
                if ch and ch[-1] not in 'aeiouy' and len(ch) <= 2: roman[ch] = roman.get(ch, 0) + v
            tot = sum(roman.values()) or 1.0
            d = {ch: 4.0 * v / tot for ch, v in roman.items()}
            d['l'] = d.get('l', 0) + 2.5            # alnas
            d[''] = d.get('', 0) + 2.5              # annas written with the letter doubled on the noun side only
            d['ll'] = d.get('ll', 0) + 0.2
            s = sum(d.values()); out[ps + p] = {k: v / s for k, v in d.items()}
    return out


if __name__ == '__main__':
    known = {'نيات', 'الله', 'له', 'تعليم', 'أعمال', 'ناس', 'شمس'}
    for w in ('بالنيات', 'الأعمال', 'والله', 'الناس', 'الشمس', 'والد', 'التعليم', 'لله'):
        a = annotate(w, known); print(w, '->', repr(a), display(a) == w)
