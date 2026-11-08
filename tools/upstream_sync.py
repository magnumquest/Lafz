#!/usr/bin/env python3
"""Tracks which HeliBoard (upstream) commits have been reviewed.

  tools/upstream_sync.py new                       fetch upstream, list commits not yet reviewed (oldest first)
  tools/upstream_sync.py mark ignored SHA... -m "why"   record a decision (ignored | merged | later); SHA may be a range A..B
  tools/upstream_sync.py log                       show the ledger

Commits marked "later" stay visible in `new` until their decision is changed to "merged" or "ignored".
Exact cherry-picks with `-x` and commits already reachable from Lafz main are detected automatically.
The ledger is committed, so every machine agrees.
"""
import argparse, os, re, subprocess, sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
LEDGER = os.path.join(ROOT, "docs", "upstream-sync.tsv")
UPSTREAM = "upstream/main"
HEADER = "sha\tdate\tdecision\tsubject\tnote\n"


def git(*a, check=True):
    r = subprocess.run(["git", "-C", ROOT, *a], capture_output=True, text=True)
    if check and r.returncode:
        sys.exit(f"git {' '.join(a)} failed:\n{r.stderr}")
    return r.stdout.strip()


def ledger():
    if not os.path.exists(LEDGER):
        return {}
    rows = {}
    for line in open(LEDGER, encoding="utf-8").read().splitlines()[1:]:
        p = line.split("\t")
        if len(p) >= 3:
            rows[p[0]] = p
    return rows


def cherry_picked():
    out = git("log", "main", "--grep=cherry picked from commit", "--format=%B")
    return {m.group(1) for m in re.finditer(r"cherry picked from commit ([0-9a-f]{7,40})", out, re.I)}


def new_commits():
    done = ledger()
    picked = cherry_picked()
    shas = git("rev-list", "--reverse", "--no-merges", f"main..{UPSTREAM}").split()
    return [(s, done.get(s)) for s in shas if s not in picked and (s not in done or done[s][2] == "later")]


def cmd_new(_):
    git("fetch", "upstream", "--no-tags")
    pending = new_commits()
    if not pending:
        print("No new upstream commits. Nothing to review.")
        return
    deferred = sum(1 for _, row in pending if row and row[2] == "later")
    fresh = len(pending) - deferred
    print(f"{fresh} new and {deferred} deferred upstream commit(s) to review (oldest first):")
    for s, row in pending:
        label = "LATER" if row else "NEW"
        print(f"[{label}] " + git("log", "-1", "--format=%h  %ad  %an  %s", "--date=short", s))


def cmd_mark(a):
    shas = []
    for r in a.shas:
        shas += git("rev-list", "--reverse", "--no-merges", r).split() if ".." in r else [git("rev-parse", r)]
    rows = ledger()
    for s in shas:
        subj, date = git("log", "-1", "--format=%s%x09%ad", "--date=short", s).split("\t")
        previous = rows.get(s)
        if previous:
            previous_decision = previous[2]
            previous[2] = a.decision
            previous[4] = a.m or ""
            print(f"{previous_decision} -> {a.decision}: {s[:8]} {subj}")
        else:
            rows[s] = [s, date, a.decision, subj, a.m or ""]
            print(f"{a.decision}: {s[:8]} {subj}")
    with open(LEDGER, "w", encoding="utf-8") as f:
        f.write(HEADER)
        for row in rows.values():
            f.write("\t".join(part.replace("\t", " ").replace("\n", " ") for part in row[:5]) + "\n")


def cmd_log(_):
    rows = ledger()
    if not rows:
        print("(empty ledger)")
        return
    for row in rows.values():
        print("\t".join(row))


p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
sub = p.add_subparsers(dest="cmd", required=True)
sub.add_parser("new").set_defaults(f=cmd_new)
m = sub.add_parser("mark")
m.add_argument("decision", choices=["ignored", "merged", "later"])
m.add_argument("shas", nargs="+")
m.add_argument("-m", help="reason / note")
m.set_defaults(f=cmd_mark)
sub.add_parser("log").set_defaults(f=cmd_log)
a = p.parse_args()
a.f(a)
