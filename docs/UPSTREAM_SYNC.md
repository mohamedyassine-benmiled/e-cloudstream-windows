# Upstream synchronization

Before code changes, follow the mandatory sync procedure in [AGENTS.md](../AGENTS.md). Fetch upstream, compare ancestry and merge missing default-branch commits without rewriting fork history. Review conflicts and rerun affected tests. Report blocked/offline checks explicitly.

## Last verified: 2026-10-25

- Upstream: `https://github.com/lelouchcodes11/cs3d.git`
- Default branch checked: `main`
- Commit: `b9085f8` (upstream 1.0.7).
- Result: merged missing default-branch commits in an isolated worktree, resolving overlaps in branding, settings, touchscreen/player controls and updater safety. The fork's current `main` commits were also merged; the original working folder's unrelated edits were not touched.
- Retained e-cloudstream branding, fork-owned update URLs, provider capability handling and cloud progress. Used upstream's newer touch implementation where it already includes the fork behaviour. Upstream README and donation wording do not replace the fork's ownership/attribution policy.
- `upstream/dev` was intentionally not merged instead of the default branch. Compilation and regression results are recorded in the task report; hardware acceptance and session-retention testing remain separate.

Do not treat this record as permission to skip fetching on the next task. `UPSTREAM_COMMIT` at the repository root describes the inherited engine import, not the last cs3d fork synchronization.
