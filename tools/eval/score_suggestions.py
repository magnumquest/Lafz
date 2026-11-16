#!/usr/bin/env python3
"""Scores SuggestionEval output against the test set (see make_typo_set.py).

For each category: first = the word committed on space is the expected one (auto-correction, or the typed word when it is
already right), top3 = expected among the first three suggestions, wrongAC = auto-corrected to something else.
For pre3-5 (completions) only top3 matters.  usage: score_suggestions.py SET.tsv OUT.tsv [--misses CAT]
"""
import sys, collections

def main():
    cats = {}
    for line in open(sys.argv[1]):
        p = line.rstrip("\n").split("\t"); cats[(p[0], p[1])] = p[3]
    stats = collections.defaultdict(lambda: [0, 0, 0, 0]); misses = collections.defaultdict(list)
    for line in open(sys.argv[2]):
        typed, exp, ac, words = line.rstrip("\n").split("\t"); words = words.split("|")
        cat = cats.get((typed, exp), "?"); s = stats[cat]; s[0] += 1
        cands = [w for w in words[1:] if w.lower() != typed.lower()] if typed != exp else words[1:]
        committed = words[1] if ac == "1" and len(words) > 1 else typed
        if committed.lower() == exp.lower(): s[1] += 1
        if exp.lower() in [w.lower() for w in cands[:3]] or (typed == exp and committed.lower() == exp): s[2] += 1
        else: misses[cat].append("%s -> %s : %s" % (typed, exp, " | ".join(words[1:5])))
        if ac == "1" and committed.lower() != exp.lower(): s[3] += 1
    print("%-6s %5s %7s %7s %7s" % ("cat", "n", "first%", "top3%", "wrongAC%"))
    for c in sorted(stats):
        n, f, t, w = stats[c]; print("%-6s %5d %7.1f %7.1f %7.1f" % (c, n, 100 * f / n, 100 * t / n, 100 * w / n))
    if "--misses" in sys.argv:
        for m in misses[sys.argv[sys.argv.index("--misses") + 1]][:40]: print("  ", m)

main()
