#!/usr/bin/env python3
"""Copies a finished release from the forge (the source of truth) to GitHub magnumquest/lafz. Nothing else ever pushes to GitHub.

  tools/publish_github.py v0.6.0            push main + that tag (asks first)
  tools/publish_github.py v0.6.0 --force    allow replacing a diverged GitHub main (asks twice)

The 'github' remote has its push URL disabled on purpose; this script pushes to a URL spelled differently from 'https://github.com/'.
"""
import subprocess, sys

REPO = "https://magnumquest@github.com/magnumquest/lafz.git"
NOREPLY = "users.noreply.github.com"


def git(*a, check=True):
    r = subprocess.run(["git", *a], capture_output=True, text=True)
    if check and r.returncode:
        sys.exit(f"git {' '.join(a)} failed:\n{r.stderr}")
    return r.stdout.strip()


args = [x for x in sys.argv[1:] if not x.startswith("--")]
force = "--force" in sys.argv
if len(args) != 1:
    sys.exit(__doc__)
tag = args[0]
if git("rev-parse", "--abbrev-ref", "HEAD") != "main":
    sys.exit("Switch to main first.")
if git("status", "--porcelain", "--untracked-files=no"):
    sys.exit("Uncommitted tracked changes: commit first.")
git("fetch", "origin", "--no-tags")
head, forge = git("rev-parse", "main"), git("rev-parse", "origin/main")
if head != forge:
    sys.exit("Local main differs from forge main. Push/pull on forge first: forge is the source of truth.")
if git("tag", "-l", tag) != tag:
    sys.exit(f"No tag {tag}.")
if subprocess.run(["git", "merge-base", "--is-ancestor", tag, "main"]).returncode:
    sys.exit(f"{tag} is not on main.")

remote = git("ls-remote", REPO, "refs/heads/main").split()
remote_sha = remote[0] if remote else None
diverged = False
if remote_sha:
    have = subprocess.run(["git", "cat-file", "-e", remote_sha], capture_output=True).returncode == 0
    diverged = not (have and subprocess.run(["git", "merge-base", "--is-ancestor", remote_sha, "main"]).returncode == 0)

bad = sorted({e for e in git("log", "main", "--not", "--remotes=upstream", "--format=%ae").splitlines() if NOREPLY not in e and e != "noreply@anthropic.com"})
print(f"GitHub main now : {remote_sha or '(empty)'}\nwill become     : {head}  (tag {tag})")
if bad:
    print("Warning: our own commits carry non-noreply emails (GitHub may reject, and they become public):", *bad, sep="\n  ")
if diverged and not force:
    sys.exit("GitHub main has history that is not in ours. Re-run with --force if replacing it is intended.")
if input(f"Type 'publish' to push to {REPO}: ").strip() != "publish":
    sys.exit("Cancelled.")
cmd = ["push", REPO, f"{head}:refs/heads/main", f"refs/tags/{tag}:refs/tags/{tag}"]
if diverged:
    if input("This REPLACES GitHub history. Type 'replace': ").strip() != "replace":
        sys.exit("Cancelled.")
    cmd = ["push", f"--force-with-lease=refs/heads/main:{remote_sha}", *cmd[1:]]
sys.exit(subprocess.run(["git", *cmd]).returncode)
