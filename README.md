# e-cloudstream for Windows

An independent, unofficial Windows app forked from [lelouchcodes11/cs3d](https://github.com/lelouchcodes11/cs3d), built on the [CloudStream](https://github.com/recloudstream/cloudstream) engine.

**e-cloudstream is developed around features conceived and architected by [@mohamedyassine-benmiled](https://github.com/mohamedyassine-benmiled).** Its direction includes website-specific account integration and cross-device cloud progress, rather than changes intended for every CloudStream user. These integrations are specific to certain websites: **there is no expectation or plan to submit this fork's features as pull requests to the upstream app.** Upstream fixes and improvements are still merged into this fork before new feature work.

This is not an official CloudStream/cs3d release or an upstream-endorsed project. Please report fork-specific issues [here](../../issues), not to upstream.

## Download

Use [this repository's Releases](https://github.com/mohamedyassine-benmiled/e-cloudstream-windows/releases) for the setup EXE, MSI or portable ZIP and `SHA256SUMS.txt`. The app is named **e-cloudstream**. New versions use numeric labels without `-eimon`; older releases keep their original names. Windows packages are unsigned experimental prereleases.

- **Setup:** recommended for normal installations. The stable installer identity and data location are retained for upgrades. After the repository rename, older apps may require a manually downloaded setup once.
- **MSI:** per-user Windows Installer package, with the same upgrade identity as earlier builds.
- **Portable:** unpack and run `CloudStream.exe`; portable data remains beside the executable.

The executable remains `CloudStream.exe` and installed data stays under `%APPDATA%\CloudStream` for compatibility. Back up before changing installations. The app does not include content sources; install extension repositories separately.

Automatic/manual app updates use **this fork's release repository**, never cs3d's. The updater accepts this fork's prereleases even though version labels no longer contain `-eimon`, selects the correct EXE/MSI/portable package and verifies SHA-256 before installation. The update notification setting remains respected.

## Fork features

- Provider-owned account **Continue Watching**, with progress, pagination and episode/timestamp resume.
- Cloud series-page progress: earlier same-season/same-audio episodes are inferred watched in memory, not imported into local history; Resume uses the saved timestamp.
- Native **Watching – New Episodes**: launches the latest eligible episode in the saved audio edition at **00:00** after revalidation.
- Cloud-aware spoiler handling and Windows touchscreen swipe/player controls.
- Touch double-tap seeking on the video: left half rewinds 10 seconds, right half skips 10 seconds. Mouse double-click keeps fullscreen behaviour.

Account features are available to extensions that advertise the required host capabilities, such as remote-list reading, exact resume, cloud title progress, and latest-episode discovery. Other extensions continue to work normally without these optional features. Website login, episode matching and optional uploads remain extension responsibilities. Opening details/rows does not advance website progress. Existing optional uploads remain position-based, not immediate pause/exit reporting, and have no durable offline queue.

## Development

`main` is the maintained default branch. Read [AGENTS.md](AGENTS.md), [CONTRIBUTING.md](CONTRIBUTING.md) and [the native-resume design](docs/NATIVE_EXTENSION_RESUME.md) before changing code. AI-assisted work must follow [AI-POLICY.md](AI-POLICY.md), including an upstream-sync check before implementation.

The release workflow tests and publishes packages on pushes to `main` or manual dispatch. Windows UI/player acceptance has been exercised for cloud resume and latest-episode zero-position playback; full hardware touchscreen acceptance remains device-dependent. Do not confuse automated tests with device verification.

## Credits and license

Credit for the original app, engine and inherited functionality belongs to the cs3d, reCloudStream and other upstream contributors. This fork's product direction does not claim authorship of their code. Original notices and the [GPL-3.0 license](LICENSE) are retained. [Archived pre-rename documentation](docs/LEGACY_README.md) is available for reference; its old community/support links belong to upstream, not this fork.
