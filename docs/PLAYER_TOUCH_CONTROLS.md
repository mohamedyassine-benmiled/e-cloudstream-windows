# Windows player touch controls

## Double-tap seeking

- Double-tap the left half of the unobstructed video to rewind 10 seconds; the right half skips 10 seconds.
- Uses the existing seek function, including timestamp limits, unsupported-source handling and cumulative seek feedback. Live streams do not use this gesture to seek.
- Touch singles still toggle play/pause after the system double-tap interval. A successful double tap cancels the single action, so playback is not briefly paused. Opposite-side taps are separate singles, not a seek.
- Real touch pointers and Windows mouse-promoted finger taps use the same path. Real mouse clicks remain immediate; mouse double-click still toggles fullscreen. Stylus input is not classified as a finger.
- Swipes, multitouch, consumed control interactions and completed 2x holds are not taps. PiP keeps its existing controls/dragging. Pending touch actions are cancelled when the player input layer is disposed, and do not fire through open menus/dialogs or loading/error screens.
- The normal video surface and native GPU player's overlay share this handler.

## Validation (2026-10-25)

Fetched upstream `main` before implementation; `b9085f8` was already included, with no new commits to merge. Original working-folder edits and installed app/data were left untouched.

`gradlew.bat :android:test :app:test :library:jvmTest --tests 'com.lagradost.cloudstream3.resume.*' :app:portableZip --console=plain` passed: 186 app tests (13 new touch-player cases), 30 compatibility-layer tests and 25 shared resume API tests. Portable packaging/startup archive generation passed. Existing dependency/compiler warnings remain.

Compose tests exercise native touch and synthetic Windows-style mouse promotion, left/right seeking, repeated pairs, singles, opposite-side taps, real mouse/stylus exclusion, control consumption, disabled/PiP input, hold suppression, swipes and multitouch. Physical Windows touchscreen acceptance in both rendering modes remains unverified; these tests do not substitute for device testing. Nothing was installed or published by this task.
