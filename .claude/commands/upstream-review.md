---
description: Review new HeliBoard (upstream) commits, merge the useful ones, mark the rest as ignored
---
Review new upstream HeliBoard commits for Lafz. Do not push anything.

1. Run `python3 tools/upstream_sync.py new`. If it says nothing is new, say so and stop.
2. For each new commit (`git show <sha>`), judge it against Lafz: our changes live in `latin/urdu/`, `Suggest.kt`, `InputLogic.kt`, settings, layouts, theme/branding, the voice and swipe code (see CLAUDE.md).
   * Useful = bug fix or feature that helps Lafz and does not touch or break our code paths. Skip translations-only, release chores, upstream CI/docs, features we removed or replaced.
   * Check overlap: `git diff --name-only <sha>^ <sha>` against files we changed (`git diff --name-only $(git merge-base main upstream/main) main`).
3. Give me a table: sha, subject, verdict (merge / ignore / later), one-line reason. Wait for my OK unless I already said to go ahead.
4. Merge approved ones with `git cherry-pick -x <sha>` (resolve conflicts, keep our behaviour), build/test what is affordable (`./gradlew testDebugUnitTest`), commit on main.
5. Record every reviewed commit: `python3 tools/upstream_sync.py mark merged|ignored|later <sha...> -m "reason"`, then commit `docs/upstream-sync.tsv`.
Reviewed commits are never listed again. Only `later` is meant to be revisited (`tools/upstream_sync.py log`).
