# openplayer: code and product audit

Audit of `0.1.0-beta.11` (commit `8665cd7`), October 2026.

All of the Kotlin sources, the manifest, the Gradle setup, the resources, the Play listing and the release docs were read. This was a static review. No Android SDK was available in the review environment, so the app was **not built, run or profiled**. Anything about runtime behaviour is reasoned from the code. Items marked *verify* should be confirmed on a device before you act on them.

Paths are shortened: `…/` means `app/src/main/java/io/github/parthibdebnath/openplayer/`.

---

## 1. Verdict

The playback core is carefully built and well defended. The edge cases around gapless and crossfade handover, the sleep timer, error recovery, SAF scanning, backup validation and process-death restore have clearly been worked through one by one. Most hobby players don't get this far.

But as a *product* it is not yet what most people expect from an Android music player, and some platform integration choices work against it:

* **What people expect is missing:** album art, tag-based browsing (album, artist, genre), real ordered playlists, repeat, an equaliser, a full queue view and lyrics.
* **Background playback is fragile.** With notifications off or denied, the foreground service is deliberately never started (see §2.1). Android only shields media playback from the background process freezer when a foreground service is running.
* **Accessibility is partial.** The seek arc and every custom slider are invisible to TalkBack. Several states are shown only by opacity. Most touch targets are below 48 dp. The text-colour rule allows contrast well under WCAG AA.
* **Engineering hygiene:** there are no tests, no CI and no `LICENSE` file, and the `PRIVACY.md` the Play docs ask for doesn't exist. All 250-odd UI strings are hard-coded, so the app can't be translated.

| Area | Rating | One-line reason |
|---|---|---|
| Playback engine | Good | Gapless, crossfade and recovery are well handled; a few timing and DSP bugs remain |
| Platform integration | Weak | Foreground service logic, no playback resumption, no Auto/Wear, controller commands leak past the queue |
| Feature completeness | Weak | No artwork, metadata library, repeat, EQ, ordered playlists or full queue |
| UX / discoverability | Mixed | Distinctive, but leans on hidden gestures and immersive mode, and is phone-portrait only |
| Accessibility | Weak | Some semantics present; seeking and sliders unusable with TalkBack; contrast and target sizes fail |
| Security / privacy | Good | No internet, careful import validation; a few leaks of metadata into shared folders |
| Performance / battery | Good, with exceptions | Tick pacing and scan batching are thoughtful; queue persistence and folder backups write too much |
| Code quality | Mixed | Readable, defensive code; god objects, no DI, no tests, comments carry history |
| Release / legal | Weak | No licence, no privacy policy, no CI; dependencies about two years old |

---

## 2. Highest-priority issues

These are ranked by user impact. Each is backed by code references.

### 2.1 Foreground service is skipped when notifications are off or denied, so background playback can die
`…/viewmodel/MainViewModel.kt:466-562`

`updatePlaybackServiceState` only starts `PlaybackService` when `canPostNotifications()` is true *and* "Notification media controls" is on. Otherwise playback runs with no foreground service, and `MediaButtonSession` stands in for the buttons.

* The premise is wrong. Since Android 13, notifications tied to a media session are **exempt** from the `POST_NOTIFICATIONS` runtime permission. When a user blocks notifications in system settings, a foreground service still runs; its notification just isn't shown.
* Without a foreground service, a backgrounded app is cached. On Android 12–14 and later, the cached-app freezer and OEM task killers will stop it. The battery-optimisation hint in Settings (`SettingsScreen.kt`) treats a symptom of this.
* The service is also only ever started while the app is on screen (`inputs.visible`). Playback started from a headset button while backgrounded, after the system stopped the service, has no foreground service either.

**Fix:** always run the media session service while something is loaded. Let Media3 own the foreground state (`startForegroundService` is allowed for media playback started from a media button or session). Treat "Notification media controls" as a cosmetic choice, or drop it: Android requires the notification for a media foreground service.

### 2.2 Audio focus: plays without focus, and ducks under calls by default
`…/audio/AudioFocusController.kt:76-85, 195-220`

* `requestFocus()` logs a denial and **plays anyway**. A denial happens during a phone call, among other times. ExoPlayer's own focus handling would refuse to start.
* `AUDIOFOCUS_LOSS_TRANSIENT` is what calls, voice assistants and voice notes send. Under the default `LOWER_VOLUME` it only ducks to 20%, and under `KEEP_PLAYING` it does nothing. Android's guidance is to *pause* on a transient loss and duck only on `…_CAN_DUCK`. With the defaults, music is likely to keep playing under a call or Assistant query (*verify*).
* `AUDIOFOCUS_REQUEST_DELAYED` is not handled.

**Fix:** never start playback without granted focus, or at least never while `AudioManager.mode` is in-call or in-communication. Always pause on `LOSS_TRANSIENT` when it comes from voice communication or assistant usages. Keep the game-sound heuristics for `CAN_DUCK` and for the "fake permanent loss" case only.

### 2.3 Music pauses when headphones or Bluetooth are *connected*
`…/audio/OutputDeviceManager.kt:110-114`, `…/audio/PlaybackEngine.kt:169-176`

Any change to the set of external outputs pauses playback, including connecting earbuds or a car. The standard behaviour is to pause on *disconnect* (already covered by `setHandleAudioBecomingNoisy(true)`), and either keep playing or optionally *resume* on connect. Pausing the moment a car connects reads as a bug.

**Fix:** pause only on removal of the device currently in use. Consider an optional "resume when headphones connect" setting.

### 2.4 No artwork, and the library ignores tags
`…/data/repository/PlaylistScanner.kt:854-873`, `…/data/db/SongEntity.kt`

The scanner reads only `ARTIST` and `DURATION`. The song title shown is the **file name**, and the `TITLE` tag is never read. Album, album artist, track and disc number, year and genre are never stored, and embedded art is never extracted. So there's:

* no cover on the Play screen, notification, lock screen, car display or watch;
* no album, artist or genre views, no "play this album in track order", and no tag-correct titles for files named `01 - track.mp3`.

Most people's first impression of a music player is the now-playing artwork. Its absence is the most visible gap in the app.

### 2.5 No repeat at all
`…/playback/EngineSessionPlayer.kt:46-48`, `…/ui/components/SmallControlsRow.kt:20-22`, `…/audio/PlaybackEngine.kt:845-847`

The queue fully supports `RepeatMode.ONE/ALL`, but the button was removed ("it was never used"). Repeat set from a car, watch or Bluetooth device is swallowed, and restored queues are forced to `OFF`. Repeat-one and repeat-all are among the most-used controls in any player, especially for language learning, practice and sleep.

**Fix:** put the button back (a cycling off → all → one control). Route `setRepeatMode` from the session to `engine.setRepeatMode` rather than ignoring it.

### 2.6 Seek-back and seek-forward are blocked for every controller
`…/playback/EngineSessionPlayer.kt:50-56`

The comment explains why: a five-second jump "out of nowhere". But the fix removes rewind and forward from headsets, cars and the lock screen altogether. For the long-track support the app advertises (podcasts, audiobooks, DJ mixes over 20 minutes), ±10 or 30 s seeking is a core control. The real bug was the action being reachable from an unexpected place. Expose it deliberately: increments that can be set, shown only for long tracks.

### 2.7 Controller commands that bypass the engine
`…/playback/EngineSessionPlayer.kt`

Only play, pause, seek, skip and repeat are intercepted. `setShuffleModeEnabled`, `setPlaybackParameters`/`setPlaybackSpeed`, `stop`, `setMediaItem(s)`, `seekToDefaultPosition` and volume commands go straight to whichever ExoPlayer is active. A car or watch can therefore shuffle ExoPlayer's two-item window, change speed outside the effects state, or replace the media item, desyncing what plays from what the app shows.

**Fix:** remove those commands from `getAvailableCommands()`, or route each through the engine.

### 2.8 Crossfade timing assumes ticks are exactly 50 ms and playback is 1×
`…/audio/PlaybackEngine.kt:516-539, 569-602`

`fadeProgress` advances `TICK_MS / activeFadeDurationMs` per tick, a fixed amount, while `activeFadeDurationMs` is measured in **media time** from the outgoing player's remaining position:

* At speed 2× the outgoing track hits its end halfway through the fade and stops at about 70% gain: an audible cut. At 0.5× the fade completes and `finishCrossfade()` stops the outgoing track before its real end.
* When the main thread stalls (GC, composition of a big list), ticks arrive late and the fade stretches past the outgoing track's end, with the same cut.

**Fix:** drive the fade from the incoming player's `currentPosition` (or the outgoing one's remaining time) instead of counting ticks. Better still, do the gain ramp inside an `AudioProcessor`, so it is sample-accurate and independent of the main thread.

### 2.9 No `LICENSE` file, and no privacy policy
The README and store listing say "free and open source", but the repository has no `LICENSE` or `COPYING`. Without one the code is legally all-rights-reserved. F-Droid won't include it, and contributors can't safely send patches. `docs/play-release.md` requires a published `PRIVACY.md`, which doesn't exist; Play requires a policy URL for any app holding a foreground-service permission.

### 2.10 No automated tests or CI
There is no `app/src/test` or `app/src/androidTest`, and no `.github/workflows`. The riskiest logic is pure Kotlin and easy to test:

* `PlaybackQueueManager`: shuffle, reorder, slot swap, repeat-all wrap, removal
* `SongSort` and `NaturalKey`
* `replayGainDb` and `parseDb`
* the `SettingsRepository.importAll` validation
* Room migrations: the schemas are exported, but `MigrationTestHelper` isn't used
* the DSP processors' byte-length and passthrough invariants

The commit history is full of regressions fixed after release ("beta.3 shipped crashing on launch"). A unit-test suite plus a CI job running `lint`, `test` and `assembleRelease` would catch most of them.

---

## 3. Feature gaps against user expectations

Compared with mainstream offline players (Poweramp, Musicolet, Auxio, Retro, Samsung Music, Pixel-era Play Music):

| Expected feature | Status | Priority | Notes |
|---|---|---|---|
| Album art (player, notification, lock screen, lists) | Missing | **P0** | §2.4 |
| Title, album and artist from tags | Missing (filename used as title) | **P0** | §2.4 |
| Browse by album, artist, genre | Missing | P1 | Folder = playlist is the only structure |
| Repeat one / all | Removed | **P0** | §2.5 |
| Ordered, user-built playlists across folders | Missing | P1 | Tags act as playlists but have no manual order |
| M3U/M3U8 import and export | Missing; `.m3u` files are explicitly skipped | P1 | Standard interchange with PCs and other players |
| Full queue view: remove, clear, save as playlist | Only the next 5 are visible and editable | P1 | `VISIBLE_UPCOMING_COUNT = 5` |
| Equaliser, bass boost, or hand-off to the system EQ | Missing | P1 | No `ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION` broadcast, so Wavelet or the OEM EQ can't attach either |
| Lyrics (embedded or `.lrc`) | Missing | P2 | |
| Android Auto, Wear OS, other controllers' browsing | Missing | P1 | Needs `MediaLibraryService` and a browse tree |
| Resume from a cold start (headset play after reboot, Android 13+ resumption card) | Missing | P1 | No `MediaButtonReceiver` in the manifest, no `onPlaybackResumption` |
| Notification actions for shuffle, favourite, repeat | Missing | P2 | Media3 `CommandButton`s |
| Seek ±N seconds for long tracks | Blocked | P1 | §2.6 |
| Playback speed | 5 fixed steps (0.5, 0.75, 1, 1.5, 2) | P2 | 1.25× is the most-used podcast speed. `EffectsState.kt:13` |
| ReplayGain album mode, pre-amp | Track only | P2 | |
| "All songs" view, or a MediaStore scan option | Missing; every folder must be picked by hand | P2 | Picking folders is friction for the 90% who just want "my music" |
| Multi-select and batch actions | Missing | P2 | |
| Undo after remove or delete | Missing | P2 | A snackbar with Undo is the norm |
| Recently added, recently played, most played | Missing | P3 | |
| Home-screen widget, Quick Settings tile | Missing | P3 | |
| Translations | Impossible today | P1 | 0 string resources, ~250 hard-coded strings, `localeFilters = en` |
| Landscape, tablet, foldable | Portrait only | P2 | Android 16 ignores the lock on screens ≥600 dp, and the arc layout isn't designed for it |
| Light or system theme, Material You | Custom background colour only | P3 | |

Features that do differentiate the app and are worth protecting: seeded shuffle, the next-five editor, the play-last-song-to-end sleep timer, multi-playlist play, the custom DSP that behaves the same on every device, remembered position in long tracks, and the folder backups that survive a reinstall.

---

## 4. Playback engine and audio

### Correctness

1. **3D "room" comb delays are scaled by sample rate twice.** `…/audio/ThreeDAudioProcessor.kt:57` multiplies `ms × sampleRate/1000` by `scale = sampleRate/44100` again. At 48 kHz the delays are 9% long; at 96 kHz they're 2.2× too long, so the effect sounds different per file.
2. **The limiter has no look-ahead and no attack time.** `…/audio/NormalizationAudioProcessor.kt:149-151`. Gain follows the instantaneous peak sample by sample, which is effectively waveshaping and adds distortion on boosted transients. Use a few ms of look-ahead, or a soft-knee limiter.
3. **"Even out volume" without tags is an AGC that rides the level within a song** (15 dB/s down, 1 dB/s up). It changes the dynamics of untagged music, and quiet intros get pumped. That may be fine as a default, but the setting text promises "about the same loudness", not compression. Consider a per-song pre-scan (EBU R128 on first play, cached in the database) and apply a fixed gain.
4. **The DSP chain is 16-bit only.** If a device or format delivers float or 24-bit PCM, every effect and normalisation silently drops out (`onConfigure` returns `NOT_SET`). The FX indicator still says "FX on".
5. **Seeded shuffle isn't reproducible in the way the app promises.** `…/audio/PlaybackQueueManager.kt:161`. The order depends on:
   * `kotlin.random.Random(seed)`, whose algorithm is only guaranteed stable *within a Kotlin version*;
   * the exact pool contents and order. Adding one song to the folder, or changing the sort setting, reshuffles everything.

   "Save the combination that lines up all the best songs" is fragile as a result. Use a fixed, documented PRNG (for example SplitMix64), and shuffle a canonical order (sorted by `documentUri`) so a pool change only perturbs locally.
6. **A changed file loses its remembered position.** `…/data/repository/PlaylistScanner.kt:110-121` rewrites the row with `REPLACE` and carries over `isFavourite`, but not `resumePositionMs`.
7. **`sortIndex` is a hash of the file name** (`PlaylistScanner.kt:118`), and the DAO orders by it. Every consumer re-sorts, so this is dead and misleading. Drop it, or make it mean something (an insertion order for manual playlists).
8. **`ExternalSongs.idFor`** packs a 31-bit `String.hashCode()` into a negative id. Two opened files can collide and share a resume position or queue identity. Low risk, but a 64-bit hash costs nothing.
9. **Formatting slip:** `…/audio/NormalizingAudioRenderer.kt:66` has a closing brace on the statement line.

### Design notes
* Two full `ExoPlayer`s, each with its own processor chain, is a reasonable way to do a real overlap. Keep it, but move the fade into the audio path (§2.8).
* The gapless two-item window, with the sleep timer and resume positions able to drop the preload, is well reasoned.
* Retry, then DSP bypass, then skip, with a stop after five unplayable songs in a row, is good. The error message is only 12 sp text at 60% alpha in the top corner (`PlayScreen.kt:308`). Make it a visible, dismissable banner, and say which song was skipped.

---

## 5. Platform integration

* **Media session** (`PlaybackService.kt`): there's no `MediaSession.Callback`, so there's no `onPlaybackResumption` and no custom commands. `onGetSession` returns the session to *any* controller, with no `ControllerInfo` checks. That is fine for local playback, but be deliberate about it if a library service is added.
* **Two sessions.** `MediaButtonSession` and the service's session are swapped as the service comes and goes. Fixing §2.1 removes the need for the second session entirely.
* **The timeline holds 1–2 items**, so system UIs and controllers can never show the queue. A `MediaLibraryService` that exposes the queue (or a forwarding timeline) fixes this.
* **No `MediaButtonReceiver`** in the manifest, so a headset press after the process has died does nothing.
* **Permanent immersive mode** (`MainActivity.kt:154`). The status and navigation bars are always hidden, so the app redraws its own clock and battery. Users lose notification icons, signal and time format consistency, and every system gesture needs an extra swipe. Hiding the bars is unusual for a music player and should be opt-in at most.
* **Portrait lock** (`AndroidManifest.xml:35`) is ignored on large screens from Android 16. Either design for it or test what users will get.
* **Predictive back.** Compose `BackHandler` is used correctly. Make sure the custom overlays (tag editor, notifications, context menus) animate with it under targetSdk 36.

---

## 6. Accessibility

What's already done well: icon buttons have `contentDescription` and `Role.Button`; `ToggleRow` is a real `toggleable` with `Role.Switch`; tabs carry `Role.Tab` and `selected`; the hold-to-edit-tags gesture is exposed as a labelled long-click action; "Scroll long titles" can be switched off.

Problems:

1. **The seek arc has no semantics.** `…/ui/components/ArcPlaybackControls.kt`. There's no `progressBarRangeInfo` and no `setProgress` action, so TalkBack, Switch Access and Voice Access users **cannot seek at all**. The position text isn't tied to it either.
2. **`CustomSlider` has no semantics** (`CustomSlider.kt`). Crossfade, colour-cycle speed and every effect slider are unreachable. `EffectSlider`'s +/- buttons partly save the effects; the crossfade and cycle sliders have nothing.
3. **State shown only by opacity.** Shuffle (`SmallControlsRow.kt:35-41`) is always announced as "Shuffle" whether it's on or off. Favourite changes its label, which is good. This fails WCAG 1.4.1 and 4.1.2. Use `toggleable` with a `stateDescription`.
4. **Touch targets below 48 dp.** Previous is 38 dp, the small controls 35 dp, the shuffle-seed buttons 30 dp, and the seek thumb is 11 dp drawn (40 dp tolerance on touch). Pad the hit area to 48 dp even where the visual stays small.
5. **Contrast.** `…/ui/theme/AppColors.kt:61-62` switches to black text only above luminance 0.45. White on a 0.4-luminance background is about 2.3:1. The crossover that maximises contrast is about 0.18. Secondary text is then drawn at 50–70% alpha (time readout, playlist name, notes, artist), so many user-picked colours fail WCAG AA (4.5:1) badly. Compute contrast for both candidates, pick the higher, and enforce a minimum for secondary text.
6. **Sleep timer is hidden behind a double-tap or long-press on the clock**, with `onClick = {}` (`TopClock.kt:143`). TalkBack announces the clock as actionable, and the action does nothing. Add `onClickLabel` and `onLongClickLabel`, or better, a visible timer button.
7. **Upcoming-list reordering is drag-only.** Add `customActions` ("Move up" / "Move down") on each row.
8. **The multi-finger gestures are on by default.** The three-finger swipe collides with the screenshot gesture on Samsung, Xiaomi, OnePlus and Oppo devices, and multi-finger gestures are hard for many motor-impaired users. Ship them off by default.
9. **Motion.** The marquee, background colour cycling, glass animations and spring reorders don't check the system "Remove animations" setting (`Settings.Global.ANIMATOR_DURATION_SCALE == 0`).
10. **Haptics bypass the system setting.** `…/util/Haptics.kt:73` plays as `USAGE_MEDIA` specifically so the phone's touch-vibration setting doesn't apply. If someone has turned system haptics off, the app still vibrates (default `LIGHT`). Honour the system setting when it's off, or at least default to Off when it is.
11. **No `heading()` semantics** on section titles in Settings or the library, so screen-reader users can't jump between sections.
12. **`ToggleRow` contains a separate clickable button**, so each setting has two focus stops. Clear the inner button's semantics.
13. **Fixed dp spacers** (`Spacer(100.dp)`, `55.dp` on the Play screen) and 20 sp list text: at 200% font scale on a small phone the Play screen will collide with the arc. Test at the largest font and display size.

---

## 7. UX and usability

* **Discoverability.** Too much lives behind invisible gestures: hold the title for tags, double-tap or hold the clock for the sleep timer, swipe for the upcoming list, two or three fingers for brightness and volume, swipe between tabs, long-press rows for menus. Each is fine as a shortcut but needs a visible equivalent. Add a first-run hint layer or an "Explore gestures" card in Settings.
* **The output switch asks for confirmation** ("Switch output device?"). It's a reversible action, so just do it and show what happened.
* **"Remove" moves the file on disk** into `removedSongs/`. The dialog says so, which is good, but "Remove from playlist" reads like a library action, not a file operation. Name it "Move to removedSongs" and offer Undo.
* **Folders are the only real structure**, so there's no way to make "Gym mix" across folders in a chosen order. Tags fill some of the gap, but they're unordered. Either give tags a manual order, or add real playlists.
* **No empty-state guidance** beyond "Start a playlist". A first run with no folders should explain the folder model and offer a one-tap "Add your Music folder".
* **Toasts are used for results** (import, export, add songs). They're transient, not accessible to all users, and can't offer Undo. Use an in-app snackbar or banner.
* **The lock screen** ("Locked / Tap to unlock") doesn't re-prompt on return, has no app identity, and the media notification still shows the title. That's acceptable, but mention it in the setting's description.
* **"Require authorization to open" with no device lock** silently does nothing (`AuthGateManager.authenticate` returns `true`). Tell the user when they turn it on.
* **"Background playback" off pauses on `ON_STOP`.** That includes the screen turning off and opening the system file picker to add songs.

---

## 8. Data, library and storage

* **The whole library is rescanned on every launch** (`MainViewModel` init, then `rescanAll`), one folder after another. Good batching, but no `ContentObserver`/`DocumentsContract` notifications, no "last scanned" skip and no manual pull-to-refresh on the list.
* **Overlapping folders** (a parent with subfolders included, plus a child folder) produce two song rows for the same file. Favourites, tags and resume positions then diverge between them.
* **The search** uses `LIKE … COLLATE NOCASE`, which is ASCII-only: "beyonce" won't find "Beyoncé", and "é" won't match "É". Store a normalised (NFD, accents stripped, lowercase) search column.
* **Folder backups** (`MainViewModel.kt:761`, on by default, `SettingsRepository.kt:89`):
  * write `.openplayer-backup.json` into *every* playlist folder, 3 s after any change: each favourite, tag, rename, setting or effect slider. On SD cards and cloud-synced folders (Syncthing, Drive) that is constant churn;
  * each copy contains **every** folder URI, every setting, the install id and the effects. Sharing one music folder therefore leaks the names of your other folders and your settings. Write only that folder's own playlist into it, and consider making this opt-in.
* **The Room migrations** are hand-written. Add `MigrationTestHelper` tests against the exported schemas (`app/schemas/1..4.json`).

---

## 9. Security and privacy

Good: no `INTERNET` permission, `ACCESS_NETWORK_STATE` stripped, a size cap and type validation on imports, an import can only turn the app lock *on*, verbose logs stripped from release builds, `setRecentsScreenshotEnabled(false)` while locked, an immutable `PendingIntent`, `RECEIVER_NOT_EXPORTED` receivers, and the service not exported.

To address:
* The backup contents in shared folders, as described in §8.
* Recents thumbnails are only hidden on API 33+. On 26–32 the library shows in Recents while locked. Use `FLAG_SECURE` there, only while locked.
* Imported settings accept **unknown keys** verbatim ("kept, so a backup from a newer version loses nothing"). Harmless today, but any key a future version adds can be planted by any app that can write to a music folder. Consider an allow-list, at least for keys that change behaviour.
* `USE_BIOMETRIC` with `BIOMETRIC_WEAK` is fine for a convenience lock. Don't describe it as security in the listing.

---

## 10. Performance and battery

* **Queue persistence rewrites the whole file every 2 s while playing.** `…/data/settings/PlaybackStateRepository.kt`. The queue and position are "stored separately… so the frequent writes stay tiny", but both keys live in the **same Preferences DataStore file**, and DataStore rewrites the whole file on every `edit`. With a 10k-song library the saved queue (pool + upcoming + history ids) is hundreds of KB, rewritten on every position save. Put the position in its own DataStore or `SharedPreferences`, and the queue in Room or a separate file.
* **History grows without bound** within a session and is saved with the queue.
* **Effect and crossfade sliders write to DataStore on every drag step.** Each write fans out to the engine, the settings flow and the (debounced) folder backups. Commit on release, or debounce.
* **Background colour cycling** recomposes the whole tree as the colour changes. Fine on screen, but make sure it stops when the window isn't visible (it is lifecycle-scoped, *verify*).
* **Liquid-glass rendering** (graphics layers, refraction) on every control: check frame times on a low-end API 26–28 device. It's off by default, which is the right call.
* **`BatteryMonitor`** registers in `Application.onCreate`, so even a service-only process wakes for every battery broadcast. Register it only while the UI is visible.

Good: the adaptive tick (50 ms only during a fade, 250 ms visible, 1 s background), bulk SAF child queries instead of `DocumentFile`, batched metadata writes, `distinctUntilChanged` everywhere, `currentSong` split out to avoid recomposing lists, R8 with resource shrinking, and the trimmed renderers and extractors.

---

## 11. Code quality and architecture

* **God objects.** `MainViewModel` (1,071 lines) does settings, library, playback, backup, restore, import/export, tags, search, service management and toasts. `PlaybackEngine` (1,340 lines) does transport, crossfade, persistence, resume positions, sleep fade, focus wiring and error policy. Split them by feature: `LibraryViewModel`, `PlaybackViewModel`, `SettingsViewModel`, `BackupManager`, `CrossfadeController`, `PlaybackPersistence`.
* **Engine configuration lives in an activity-scoped ViewModel.** Effects, crossfade, normalisation, skip-silence and the shuffle seed are pushed into the process-wide engine from `MainViewModel.init`. If the service ever runs without the activity (resumption, Auto, a headset press after process death), the engine runs on defaults and with no restored queue. Move the wiring into an application-scoped component that the service and the UI both use.
* **Hand-rolled singletons** (`get(context)` with double-checked locking) everywhere, and no DI. That makes testing and swapping implementations (a fake player, an in-memory database) impractical. Hilt or a small manual container would help.
* **Mutable internals exposed.** `PlaybackQueueManager.explicitQueue` and `.upcoming` are public `MutableList`s. Expose read-only views.
* **Comments narrate history.** Most comments explain what an earlier version did wrong ("used to…", "previously…", "this is the reason no notification ever appeared"). That's valuable context, but it belongs in commit messages or ADRs. In the code it buries the current contract and goes stale. Keep the *why* of the current behaviour and move the rest into history.
* **Hard-coded strings** everywhere (Toasts, dialogs, labels, content descriptions), including plurals done by hand. Move them to `strings.xml` with `plurals`, even if English stays the only language for now.
* **Style:** inline fully-qualified names (`kotlinx.coroutines.CoroutineScope(...)`, `android.provider.DocumentsContract`), unordered imports, and both `Toast` and `GlassNotification` for feedback. Add ktlint or detekt and Android Lint to CI.
* **Error handling** is broad `runCatching` and `catch (Throwable)`. It's understandable given the crash history, but it also swallows programming errors (including `CancellationException` inside `runCatching` in suspend code). Narrow it where possible, and rethrow cancellation.

---

## 12. Build, release, documentation

* **Dependencies are about two years old:** Compose BOM `2024.09.02`, Kotlin `2.0.21`, Room `2.6.1`, lifecycle `2.8.6`, coroutines `1.8.1`, activity `1.9.2`. `kotlinOptions {}` is deprecated in favour of `compilerOptions {}`. Update, and add Renovate or Dependabot.
* **No `lint` configuration** and no baseline. Run Lint in CI with `warningsAsErrors` for accessibility and correctness checks.
* **`smoke-test.sh` is good.** Run it in CI on an emulator (GitHub Actions with `reactivecircus/android-emulator-runner`) rather than by hand.
* **A release without `keystore.properties` silently builds unsigned.** Fail the `bundleRelease` task instead, so an unsigned artifact can't be shipped by accident.
* **README:** "Zero adds" should be "Zero ads". The screenshots link to `github.com/user-attachments` while `img/` holds local copies. There are no build instructions, no licence section and no contribution notes. The feature list is out of date: favourites, tags, normalisation and backups are missing from it.
* **No `CHANGELOG.md`** in the repository. The fastlane changelogs only cover 8–11.

---

## 13. Suggested roadmap

**Before a wider release (P0)**
1. Always run the media foreground service while a song is loaded (§2.1).
2. Fix audio focus: no playback without focus, and pause on calls and assistants (§2.2).
3. Stop pausing on output *connect* (§2.3).
4. Restore repeat, and route it from the session (§2.5).
5. Add `LICENSE` and `PRIVACY.md` (§2.9).
6. Make seeking and sliders accessible, fix the contrast threshold, and give shuffle a real state (§6.1–6.5).

**Next (P1)**
7. Read title, album, album artist, track number and embedded artwork. Show artwork everywhere, and pass it to `MediaMetadata`.
8. Add a full queue screen (remove, clear, save as playlist, jump to).
9. Add ordered playlists and M3U import/export.
10. Add a `MediaLibraryService` for Auto and Wear, playback resumption and `MediaButtonReceiver`.
11. Add an equaliser, or at minimum the audio-effect session broadcasts so external EQs work.
12. Move strings to resources.
13. Add unit tests for the queue, sort, ReplayGain parsing, settings import and migrations, plus CI with Lint.
14. Make crossfade sample-accurate and independent of speed (§2.8).

**Later (P2/P3)**
15. Configurable seek increments, more speed steps, ReplayGain album mode, lyrics.
16. Large-screen and landscape layouts. Make immersive mode optional.
17. Add an optional MediaStore scan or "All songs" view.
18. Add a widget, Quick Settings tile and notification custom actions.
19. Split `MainViewModel` and `PlaybackEngine`, add DI, and move the history out of comments.

---

## 14. What's done well

Worth saying plainly, since this report is mostly about problems:

* The playback core has clearly been tested against real devices: OEM focus quirks, HAL re-enumeration, the empty-buffer `put` crash, the `atan2` wrap on the seek arc, the SQLite 999-variable limit, and keeping the index intact when the SAF provider returns a null cursor.
* Persistence is careful: no save before restore, queue saves deferred until a rebuild settles, and a single restore per process.
* The privacy posture is genuinely strong for a media app.
* The size discipline is impressive (custom renderer and extractor set, locale filtering, no AppCompat).
* The individual decisions are consistently explained. They aren't always right, but the reasoning is always visible.
