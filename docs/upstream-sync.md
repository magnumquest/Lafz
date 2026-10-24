# Repository layout and sync workflow

Single working copy: `~/python-projects/lafz`. Forge is the source of truth.

| Remote | Purpose | Push |
|---|---|---|
| `origin` | forge `thomas/lafz` (ssh, port 2222) | yes: plain `git push` / `git push origin vX.Y.Z` |
| `github` | `magnumquest/lafz` public mirror, releases only | disabled; use `tools/publish_github.py vX.Y.Z` |
| `upstream` | HeliBoard, read only | disabled |

Commit identity in this repo: `Magnum Quest <139659490+magnumquest@users.noreply.github.com>` (local config), so the same commits can go to both remotes.
Never `git push --tags` (it would push HeliBoard's tags); push your own tag by name.

## Releasing
1. Release on forge (`git push origin main vX.Y.Z`, `tools/forge-release.py`).
2. When it should go public: `tools/publish_github.py vX.Y.Z` (shows what changes, asks to confirm).

## Taking HeliBoard changes
Ask Claude: `/upstream-review` (or "check new upstream commits"). `tools/upstream_sync.py new` lists only commits not yet reviewed;
decisions (merged / ignored / later) are stored in `docs/upstream-sync.tsv`, so they are never shown again. Baseline: everything up to `e97acdbe` is already in main.
