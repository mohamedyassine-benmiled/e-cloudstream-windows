Windows test build from `feature/native-extension-resume`, commit `9ddce58639ea3459ac63bf4aacb66a1d17c548b4`.

## Downloads

- **CloudStream-1.0.3-windows-x64-setup.exe**: normal setup installer (recommended).
- **CloudStream-1.0.3-windows-x64.msi**: alternative Windows Installer package; use one installer, not both.
- **CloudStream-1.0.3-windows-x64-portable.zip**: portable app, with settings stored alongside it.
- **SHA256SUMS.txt**: SHA-256 checksums for all three downloads.

## Test-build status

- Built successfully by [GitHub Actions run 38799294038](https://github.com/mohamedyassine-benmiled/eimon-cloudstream-windows/actions/runs/38799294038), including native-resume unit tests.
- Contains the optional native-resume contract, Windows UI integration and tests. Native-resume UI capabilities remain behind the `cloudstream.nativeResume.experimental` JVM property; they are not enabled by default in this build.
- Immediate pause/exit progress uploads and a durable upload queue are not implemented.
- This is an unsigned prerelease, not a stable release. Windows may show a SmartScreen warning. Back up your settings before testing; installers use the existing CloudStream app identity and may update an existing installation.
- Qrmzi and AniKoto Account extensions are distributed separately through the Eimon extension repository; they are not bundled into these installers.

These are the original CI-built artifacts, not a new rebuild.
