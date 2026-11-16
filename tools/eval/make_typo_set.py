#!/usr/bin/env python3
"""Builds an English suggestion test set for SuggestionEval (debug builds).

Lines: typed TAB expected TAB category. Categories: ok (correct word, must not be "corrected"),
sub (neighbouring key), del (letter missing), ins (extra neighbouring key), swap (two letters swapped),
dbl (letter doubled), two (two neighbouring-key errors), pre3/pre4/pre5 (word cut after 3/4/5 letters: completion).
Words: the most frequent English words from wordfreq (letters only, 3+ letters), fixed random seed.
usage: make_typo_set.py OUT.tsv [words] [per-category]
"""
import random, sys
from wordfreq import top_n_list

ROWS = ["qwertyuiop", "asdfghjkl", "zxcvbnm"]
OFFS = [0.0, 0.5, 1.5]
POS = {c: (x + OFFS[r], r) for r, row in enumerate(ROWS) for x, c in enumerate(row)}
NEIGH = {c: [d for d in POS if d != c and abs(POS[d][0] - POS[c][0]) <= 1.0 and abs(POS[d][1] - POS[c][1]) <= 1] for c in POS}

def sub(w, r):
    i = r.randrange(len(w)); return w[:i] + r.choice(NEIGH[w[i]]) + w[i + 1:]
def dele(w, r):
    i = r.randrange(1, len(w)); return w[:i] + w[i + 1:]
def ins(w, r):
    i = r.randrange(len(w)); return w[:i + 1] + r.choice(NEIGH[w[i]]) + w[i + 1:]
def swap(w, r):
    i = r.randrange(len(w) - 1); return w[:i] + w[i + 1] + w[i] + w[i + 2:]
def dbl(w, r):
    i = r.randrange(len(w)); return w[:i] + w[i] + w[i:]
def two(w, r):
    return sub(sub(w, r), r)

def main():
    out = sys.argv[1]; n = int(sys.argv[2]) if len(sys.argv) > 2 else 3000; per = int(sys.argv[3]) if len(sys.argv) > 3 else 300
    words = [w for w in top_n_list("en", n * 2) if w.isalpha() and w.isascii() and len(w) >= 3 and w == w.lower()][:n]
    long_words = [w for w in words if len(w) >= 5]
    r = random.Random(17); lines = []
    for w in r.sample(words, per): lines.append((w, w, "ok"))
    for name, f, pool in (("sub", sub, words), ("del", dele, long_words), ("ins", ins, words), ("swap", swap, words),
                          ("dbl", dbl, words), ("two", two, [w for w in long_words if len(w) >= 6])):
        for w in r.sample(pool, per):
            t = f(w, r)
            if t != w and t not in words[:n]: lines.append((t, w, name))  # a typo that is itself a common word is not a typo
    for k in (3, 4, 5):
        for w in r.sample([w for w in long_words if len(w) > k + 1], per): lines.append((w[:k], w, "pre%d" % k))
    with open(out, "w") as fh:
        for t, w, c in lines: fh.write("%s\t%s\t\t%s\n" % (t, w, c))
    print(len(lines), "lines ->", out)

main()
