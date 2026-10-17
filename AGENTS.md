# e-cloudstream Windows: instructions for coding agents

## Product and ownership

- This is `mohamedyassine-benmiled/e-cloudstream-windows`; the displayed app name is **e-cloudstream**. `main` is the maintained branch.
- Fork features are conceived/architected by the repository owner and intentionally include website-specific behaviour. Do not prepare or submit upstream PRs for them unless the owner explicitly asks. Preserve upstream credits, copyright notices and GPL licensing.
- Versions no longer use `-eimon`. Do not rename internal engine namespaces or change the CloudStream engine version just to rebrand the app.

## Required before code changes: upstream sync

1. Read this file, `AI-POLICY.md`, the task and relevant design docs. Inspect `git status`, current branch and remotes. Preserve unrelated/uncommitted work; never reset, clean or overwrite it.
2. Fetch `upstream` (`https://github.com/lelouchcodes11/cs3d.git`) and compare the working branch with **`upstream/main`**, the original app's default branch. Do not substitute its development branch without approval.
3. Before implementing new code, merge any missing upstream commits into the working branch using a normal, non-destructive merge. Never force-push or rewrite published fork history. If dirty files make this unsafe, request guidance or isolate work safely; do not silently stash/discard user changes.
4. Review and resolve conflicts while retaining fork functionality. If the intended resolution is unclear, stop and ask. If the network is unavailable, report that synchronization is unverified rather than claiming it is current.
5. If no upstream commits are missing, record that result in the task report. After a merge, run relevant tests before proceeding. Record the upstream repository, branch and commit checked in the task report or `docs/UPSTREAM_SYNC.md`; `UPSTREAM_COMMIT` records the engine import and is not a fork-sync tracker.

## Architecture and compatibility

- Desktop Compose UI: `app/src/main/kotlin/com/lagradost/desktop`; inherited engine UI/viewmodels: `app/src/main/kotlin/com/lagradost/cloudstream3`; shared contracts: `library/src/commonMain/.../resume`.
- Keep API version 1 and existing resume data-class constructors binary compatible. Optional capabilities must be negotiated; old extensions and ordinary extension rows must still work.
- Provider/plugin/profile/account/revision ownership must be validated. Never expose one account's state to another provider or profile. Native rows belong to the owning provider, not a global Home shelf.
- Cloud episode projection is in memory only, scoped to exact title/season/audio edition. Do not import inferred watches into local history. Latest-episode cards must revalidate and start at zero, never reuse the previous episode's timestamp.
- Spoiler state must remain correct across sorting/ranges. Preserve touchscreen axis/tap/drag behaviour and mouse/keyboard support.
- Extension sources are maintained separately. Never copy private extension source or host API definitions into distributed extension binaries. Keep matching API/registry contracts synchronized with the Android fork and compile-only extension SDK when applicable.

## Data, credentials and distribution

- Keep `CloudStream.exe`, Windows installer AppId/MSI upgrade UUID, `%APPDATA%\CloudStream` and portable data behaviour stable. Rebranding must not create a new data store or wipe sessions/history. Preserve installer data protections and old/new updater compatibility.
- Never log/share session values, credentials, response bodies or raw preference backups. Do not replay old tokens or disturb a working mobile login. Session diagnostics show observations, not a proven server revocation cause.
- Do not install, publish, delete releases or change GitHub settings without task authorization. Back up and verify app data before an authorized installation. Do not commit build output, personal data or secrets.
- Release via `.github/workflows/native-test-build.yml`; releases remain unofficial prereleases. Keep checksums and test results. Never claim hardware/device acceptance from unit tests alone.
- Native and inherited updater entry points must use the Windows fork's repository and native EXE/MSI/ZIP updater, never official Android APKs or cs3d releases. Keep prerelease eligibility independent of the removed `-eimon` suffix; preserve checksum verification, correct package-kind selection and user notification preferences.

## Validation and collaboration

- Follow surrounding Kotlin/Compose patterns; keep changes scoped and explain tradeoffs. Do not delegate to subagents unless asked.
- Baseline: `./gradlew :app:test --tests 'com.lagradost.desktop.resume.*' --tests 'com.lagradost.desktop.ui.fluent.TouchScroll*' :library:jvmTest --tests 'com.lagradost.cloudstream3.resume.*' :app:portableZip --console=plain` (use `gradlew.bat` on Windows).
- Add focused regression tests; validate installers/updater when packaging changes. Report exactly what passed, failed or remains untested. See `docs/NATIVE_EXTENSION_RESUME.md`, `docs/UPSTREAM_SYNC.md`, `SECURITY.md` and `CONTRIBUTING.md`.
