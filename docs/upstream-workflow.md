# Reviewing and integrating HeliBoard updates

Lafz is maintained as its own project. The source repository (`origin`) is the source of truth for Lafz; HeliBoard (`upstream`) is a read-only source of candidate changes. GitHub is a public mirror and its “ahead/behind” count is not the Lafz upstream review status: it compares commit ancestry, which does not reflect selectively ported or adapted changes.

## When to review

Run an upstream review whenever requested, or when `tools/upstream_sync.py new` shows around 10 or more pending commits. A monthly check is useful during active upstream development. Review related commits together when that makes their purpose clearer, but decide and record each commit separately. Never merge a batch just to make the GitHub behind count smaller.

## 1. Check the pending list

From the Lafz repository root, run:

```sh
tools/upstream_sync.py new
```

This fetches `upstream` without tags and lists non-merge commits on `upstream/main` that are not in Lafz `main`, have not already been accounted for in the ledger, and were not exact cherry-picks marked with `-x`. Output is oldest first. `[NEW]` means no review decision has been recorded; `[LATER]` means it was deferred and still needs review.

To inspect existing review decisions:

```sh
tools/upstream_sync.py log
```

For each candidate SHA, inspect the commit and its context before deciding:

```sh
git show --stat --oneline <sha>
git show --format=fuller <sha>
git log --oneline --decorate --all -- <affected-path>
```

Use read-only inspection of HeliBoard while preparing the review. Do not edit Lafz or cherry-pick during this discovery step.

## 2. Prepare a review and merge plan

Review this guide, `docs/upstream-sync.md`, the pending commit list, relevant Lafz code, and the corresponding upstream diffs. The review should:

1. Summarize what each commit changes and group commits that depend on one another.
2. Classify each commit as **adopt**, **skip**, or **defer**, with a short reason.
3. For adopted commits, explain whether a clean cherry-pick is appropriate or whether Lafz needs an adapted port. Identify Lafz custom behavior and files that need protection.
4. Give a concrete integration order and verification plan before changing files.

The maintainer reviews and approves the plan before integration begins. If implementation requires a material change to the plan, get approval for the revised approach before continuing.

## 3. Integrate on an isolated branch

Start from the latest `origin/main`, not from `upstream/main`:

```sh
git switch main
git pull --ff-only origin main
git switch -c upstream/review-YYYY-MM-DD
```

For an independent, compatible commit, use `git cherry-pick -x <sha>` so the source commit remains traceable. For structural or conflicting changes, port the behavior into Lafz's current structure instead of merging upstream wholesale. Keep Lafz's settings, dictionaries, prediction behavior, branding, release setup, and other documented product changes intact.

After each related group, inspect the diff and run the relevant checks. Before merging, build and run the appropriate tests; use the emulator for user-visible keyboard behavior or settings changes. Compare the resulting behavior against the accepted plan. Do not merge if the build, tests, or protected Lafz behavior regress.

When the branch is ready, merge it into `main` locally, then push `main` to the source repository. Do not push to the read-only `upstream` remote. Preserve normal release and mirror rules from `docs/upstream-sync.md`.

## 4. Record each decision

Record skipped and deferred commits with a reason:

```sh
tools/upstream_sync.py mark ignored <sha> -m "Why Lafz does not need this"
tools/upstream_sync.py mark later <sha> -m "What needs to be resolved first"
```

After adopting an upstream change, record it as merged if its source commit is not already detected in Lafz history or as a `-x` cherry-pick:

```sh
tools/upstream_sync.py mark merged <sha> -m "Ported with Lafz behavior preserved; verified with ..."
```

`later` decisions remain visible in future `new` output. Revisit them and change their decision when resolved; `mark` updates an existing ledger entry:

```sh
tools/upstream_sync.py mark merged <sha> -m "Now adopted after ..."
tools/upstream_sync.py mark ignored <sha> -m "Revisited; still not needed"
```

For a contiguous group, a Git revision range can be used, but review the range first so the same decision and note genuinely apply to every commit:

```sh
tools/upstream_sync.py mark ignored <oldest-sha>..<newest-sha> -m "Reason applying to this whole group"
```

Commit the updated ledger with the Lafz integration. Do not mark deferred or unreviewed commits as handled, and do not move a global baseline past commits that have not received a decision. The ledger, rather than GitHub's behind count, is the record of review coverage.

## Script reference

| Command | Purpose |
|---|---|
| `tools/upstream_sync.py new` | Fetch upstream and list new plus deferred commits still needing a decision. |
| `tools/upstream_sync.py log` | Print the committed review ledger. |
| `tools/upstream_sync.py mark <decision> <sha>... -m "reason"` | Add or update a decision (`merged`, `ignored` or `later`) for one commit or a reviewed range. |
| `tools/build-release.sh` | Build the signed production APK; use only for a release, not for ordinary upstream review. |
| `tools/forge-release.py vX.Y.Z` | Create/upload a versioned release on the source repository after its tag and APK are ready. |
| `tools/publish_github.py vX.Y.Z` | Explicitly mirror the committed source `main` and named release tag to GitHub. It does not push to HeliBoard. |

See [repository and release setup](upstream-sync.md) for remotes and release ordering.
