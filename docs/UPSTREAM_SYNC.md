# Upstream synchronization

Before code changes, follow the mandatory sync procedure in [AGENTS.md](../AGENTS.md). Fetch upstream, compare ancestry and merge missing default-branch commits without rewriting fork history. Review conflicts and rerun affected tests. Report blocked/offline checks explicitly.

## Last verified: 2026-10-17

- Upstream: `https://github.com/lelouchcodes11/cs3d.git`
- Default branch checked: `main`
- Commit: `469cb776eb4b19ba6d8d90776aa56a69168a2385`
- Result: already an ancestor of the fork; no missing default-branch commits and no merge required before branding/main-branch work.
- `upstream/dev` has separate changes; it was intentionally not merged instead of the default branch.

Do not treat this record as permission to skip fetching on the next task. `UPSTREAM_COMMIT` at the repository root describes the inherited engine import, not the last cs3d fork synchronization.
