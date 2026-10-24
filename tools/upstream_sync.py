#!/usr/bin/env python3
"""Tracks which HeliBoard (upstream) commits have been looked at, so only new ones are reviewed.

  tools/upstream_sync.py new                       fetch upstream, list commits not yet reviewed (oldest first)
  tools/upstream_sync.py mark ignored SHA... -m "why"   record a decision (ignored | merged | later); SHA may be a range A..B
  tools/upstream_sync.py log                       show the ledger

A commit counts as handled when it is reachable from our main (merged), was cherry-picked with -x ("cherry picked from commit SHA"),
or has a line in docs/upstream-sync.tsv. Everything else in upstream/main is "new". The ledger is committed, so every machine agrees.
"""
import argparse, datetime, os, subprocess, sys

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
    return {l.split("commit ")[1].strip(" )") for l in out.splitlines() if "cherry picked from commit" in l}


def new_commits():
    done = ledger()
    picked = cherry_picked()
    shas = git("rev-list", "--reverse", "--no-merges", f"main..{UPSTREAM}").split()
    return [s for s in shas if s not in done and s not in picked]


def cmd_new(_):
    git("fetch", "upstream", "--no-tags")
    new = new_commits()
    if not new:
        print("No new upstream commits. Nothing to review.")
        return
    print(f"{len(new)} new upstream commit(s) to review (oldest first):")
    for s in new:
        print(git("log", "-1", "--format=%h  %ad  %an  %s", "--date=short", s))


def cmd_mark(a):
    shas = []
    for r in a.shas:
        shas += git("rev-list", "--reverse", r).split() if ".." in r else [git("rev-parse", r)]
    rows = ledger()
    new_file = not os.path.exists(LEDGER)
    with open(LEDGER, "a", encoding="utf-8") as f:
        if new_file:
            f.write(HEADER)
        for s in shas:
            if s in rows:
                continue
            subj, date = git("log", "-1", "--format=%s%x09%ad", "--date=short", s).split("\t")
            f.write(f"{s}\t{date}\t{a.decision}\t{subj}\t{a.m or ''}\n")
            print(f"{a.decision}: {s[:8]} {subj}")


def cmd_log(_):
    print(open(LEDGER, encoding="utf-8").read() if os.path.exists(LEDGER) else "(empty ledger)")


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
