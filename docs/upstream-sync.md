# Repository and release workflow

The source repository is the source of truth for Lafz. The remotes have distinct roles:

| Remote | Purpose | Push |
|---|---|---|
| `origin` | primary Lafz source repository | yes: push Lafz branches and individual tags |
| `github` | `magnumquest/lafz` public mirror | use `tools/publish_github.py vX.Y.Z` |
| `upstream` | HeliBoard, read only | disabled |

Do not push tags to HeliBoard. Push Lafz release tags by name; never use `git push --tags`.

## Releasing
1. Push the approved Lafz commit and version tag to the source repository, build the signed APK with `tools/build-release.sh`, and create the source release with `tools/forge-release.py vX.Y.Z`.
2. To publish the same commit and tag to GitHub, run `tools/publish_github.py vX.Y.Z`; confirm the displayed changes before publishing. Create the GitHub release and attach the APK so in-app update checks can find it.

## Taking HeliBoard changes
Follow [the upstream review and integration workflow](upstream-workflow.md). `tools/upstream_sync.py new` fetches HeliBoard and lists commits that are new or deferred; it does not change Lafz code. Decisions are recorded in `docs/upstream-sync.tsv`. HeliBoard remains read-only, and the “behind” count on GitHub is not a measure of Lafz review coverage.
