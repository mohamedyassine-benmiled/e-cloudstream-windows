# Optional native extension resume — experimental groundwork

The additive API lives in `library/src/commonMain/kotlin/com/lagradost/cloudstream3/resume` and is kept identical in the Eimon Android and Windows forks.

The data contract, registry, native account cards and typed exact resume are integrated behind the `cloudstream.nativeResume.experimental` JVM property. Eimon desktop packages enable this property in their launcher; hosts without it keep the gate disabled. Native progress event dispatch is not implemented. Do not disable a stock-host fallback just because the bridge class exists.

## Provider-owned Home rows

### Unreleased: cloud title progress and Watching – New Episodes

The optional `TITLE_PROGRESS` / `NEW_EPISODES` capabilities add an in-memory watched frontier to series details and a second ordinary provider-owned native row. Earlier episodes in the same season/audio edition are inferred watched, the saved episode uses its cloud timestamp, and future episodes use the existing spoiler toggle. The new-episodes row starts the newest eligible same-edition episode at zero after fresh validation. Fetching either feature never writes inferred local history. Old extension adapters and ordinary providers keep their existing behaviour; matching updated extension support is required. Signed-in acceptance tests are pending.

Account shelves now enter the ordinary provider Home-page row pipeline rather than a separate global block. Both the provider name and plugin identity must match the selected source. The standard row ordering/hiding and pagination apply; progress, remaining time, resume/open-title/device-hide actions, and sign-in/retry notices remain available. Local Continue Watching/history are unchanged. Remote rows do not replace provider loading/failure states or appear inside another plugin with the same provider name.

## Minimal host integration policy

- Reuse the existing local Continue Watching card presentation and resume/player flow.
- Add generic inputs for remote episode identity, exact position/duration, display metadata, and scoped callbacks.
- Preserve existing local-history behaviour; fetching remote entries must not create local watches.
- Keep provider HTTP/authentication logic, website matching, and **prefer dub over sub** settings inside the extension, not the app.
- A resolver may choose another audio edition of the requested episode/season, not another episode.
- Expose capabilities only after their UI/player behaviour is installed and tested.
- Preserve old plugin constructors/interfaces. An updated extension must load its optional adapter only after successful version/capability detection on a supported host.

## Current contract

- `RemoteResumeEntry`: stable account/item/episode identities, artwork, episode/season/audio, exact timestamp/duration, optional remote ordering metadata, and a bounded opaque resolver token.
- `ResumeProgress`: retains raw milliseconds while providing clamped seek position, known-duration progress fraction, and remaining time. Unknown duration remains unknown.
- `ContinueWatchingSource`: optional list, exact episode resolver, and player-progress handler.
- `ResumeProviderRegistry`: host-owned source keys, capability/version validation, per-source error isolation, account response validation, bounded calls, and cancellation/invalidation when a source is closed/unregistered.
- No credentials or expiring stream URLs belong in list records or logs. Cancellation cannot undo a network request already accepted by a remote server.

## Tests

```powershell
.\gradlew.bat :library:jvmTest --tests 'com.lagradost.cloudstream3.resume.*'
```

The tests cover version negotiation, raw/clamped timestamps, duration handling, malformed/cross-account records, provider isolation, timeout, caller cancellation, unregister cleanup, stale registration handles, and extension-controlled audio substitution. They do not constitute native UI, full-app, or stock-extension binary compatibility tests.

Signed-in UI/resume and device behaviour remain acceptance tests. No alternative player is planned.
