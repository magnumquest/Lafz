---
description: Review new HeliBoard commits for possible Lafz integration
---
Review pending HeliBoard commits for Lafz using `docs/upstream-workflow.md`.

1. Run `tools/upstream_sync.py new` and inspect each new or deferred commit and its context without modifying the working tree.
2. Prepare a concise summary, an adopt/skip/defer recommendation for each commit, and a proposed integration and verification plan.
3. Review the plan with the maintainer before changing code.
4. If integration is approved, work from the latest Forge `main` on an isolated branch. Preserve Lafz-specific behavior, verify the affected features, then record decisions using `tools/upstream_sync.py mark`.
5. Do not push to the HeliBoard remote. Follow the repository's release and mirror workflow for any publishing.
