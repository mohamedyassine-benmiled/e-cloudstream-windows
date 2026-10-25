# Windows WebView session compatibility (work in progress)

## Scope

Fix the Windows Android-compatibility APIs used by existing extensions, rather than adding AniKoto-specific logic to the host or changing Android's working implementation. This work does not prove or override a website's server-side logout policy.

The upstream integration is isolated on `fix/anikoto-session-handoff`, includes upstream `main` through `b9085f8` and the fork's current `main`, and leaves the original working folder's unrelated edits intact. Merge regression validation runs before compatibility changes.

## Implemented locally

- `CookieManager.getCookie()` asks the running backend for fresh cookies rather than relying solely on the periodic mirror. Concurrent readers coalesce. The wait is capped at 200 ms on the AWT UI thread and 1500 ms elsewhere; requests expire and late callbacks cannot apply their result. Without a running backend, reads use the existing encrypted jar and do not start a browser.
- A failed/timed-out live read returns `null`, not a stale authentication header. The existing saved jar is retained. Android's API has no separate error channel; extensions that treat any null read as a logout may need to retry, and this behaviour needs real-extension acceptance. Native WebView2 callback and JCEF interception threads never wait on their own cookie callback.
- `CookieManager.flush()` now refreshes acknowledged browser state and waits up to three seconds for a serialized encrypted disk write. A failed refresh still persists the existing jar and logs an incomplete-flush status without cookie values. The store no longer deletes the old file as a rename fallback.
- Both engine defaults can be updated from the running browser (WebView2 native Settings2 getter before overrides; JCEF `Browser.getVersion`). Existing explicit per-view overrides and reset behaviour remain unchanged. Before a live engine is available, the previous version-based/configured fallback remains.
- Snapshot revision/sequence/backend checks reject reads made before local renewal/deletion, out-of-order completions and callbacks from replaced engines. Incomplete WebView2 enumeration and timed-out JCEF enumeration do not replace the saved jar. Closing the last WebView2 page or stopping JCEF detaches its cookie backend.
- Cookie header path matching now requires a path-component boundary (`/account` does not match `/accounting`). HttpOnly cookies remain included; secure, host and expiry filtering remain in place.

## Safety requirements

- Keep existing Android API signatures; benefit extensions without rebuilding them.
- Use bounded, coalesced cookie refreshes with no wait on the browser's own callback thread. Failed/timed-out reads must not be reported as a freshly captured login or wipe the persisted jar.
- Preserve HttpOnly cookies, expiry, domain and path matching. Never expose cookie values in logs, diagnostics or tests involving real accounts.
- Do not start a browser just to read persisted cookies when none is running.
- Do not automatically copy browser cookies into Java/OkHttp requests, intercept arbitrary extension HTTP, replay uploads or bypass website challenges. A site that rejects non-browser networking may still need an explicit browser transport integration; fixing cookie capture is not a promise to eliminate that restriction.
- Keep tests synthetic/local. Live fresh-login, idle, restart and renewal checks require separate acceptance testing; do not disturb the working phone session.

## Verification status

Upstream conflicts resolved locally. The merged baseline passed compatibility-layer/app tests, 25 shared resume API tests and `:app:portableZip` before the session changes. Focused synthetic tests cover AJAX freshness/HttpOnly cookies, failure versus empty jars, timeout/late/duplicate callbacks, concurrent readers, callback-thread waits, local renewal/deletion, out-of-order/backend-lifecycle checks, scope filtering, encrypted flushing and observed user-agent defaults. The full post-change test/build run is recorded in the task report.

### Local validation: 2026-10-25

- `gradlew.bat :android:test :app:test :library:jvmTest --tests 'com.lagradost.cloudstream3.resume.*' --console=plain`: passed, 30 compatibility-layer tests (including 18 new regressions), 173 app tests and 25 shared resume API tests; no failures/errors/skips.
- `gradlew.bat :app:portableZip --console=plain`: passed on the final source tree, including runtime compression and startup archive generation. One earlier overlapping packaging attempt failed on locked build-output jars; the serialized retry succeeded. Training uses the build's throw-away data folder, not the installed app's data.
- Existing Kotlin hierarchy/Compose-Skiko dependency warnings remain. MSI/setup packaging was not rerun for these compatibility changes.

Live WebView2/JCEF browser acceptance, actual navigator/header equality with explicit user-agent overrides, real AniKoto fresh login, idle renewal, application restart and long-lived server retention remain unverified. Unit tests do not establish the backend's reason for rejecting a session. No Android implementation or extension source is changed, no real account cookies are used by the tests, and nothing has been published or installed.
