# Shorts Auto Scroll

An Android app that watches the YouTube app while you are looking at Shorts and swipes to
the next Short **the moment the current one finishes playing** — so you can keep watching
without touching the screen.

It does not replace, embed or re-implement YouTube. It drives the real YouTube app through
Android's accessibility APIs, the same mechanism a screen reader or a switch-access device
uses.

---

## 1. How "the Short just ended" is detected

There is no public API that reports YouTube's playback position, so the app reads the
Shorts player's own accessibility tree (~4 times a second, ~10 times a second near the end)
and uses the first of these signals that the installed YouTube build exposes:

| # | Signal | Precision |
|---|--------|-----------|
| 1 | The scrubber's progress reaches the configured threshold (default 98 %) | ±100 ms |
| 2 | Progress jumps backwards — YouTube looping the Short, i.e. an exact "it just ended" event | exact, but ~0.2 s of the replay is visible |
| 3 | An `0:07 of 0:31` style label reaches its duration | ±300 ms |
| 4 | A plain timer (fallback when nothing above can be read) | user-set |

It also knows when **not** to scroll:

* playback is paused (progress stops advancing) — the countdown is put on hold;
* the comment sheet, share sheet or a text field is open;
* for ~900 ms after a swipe, so the next Short can settle.

A safety cap (default 180 s) guarantees it never sits on one Short forever.

**Important:** YouTube's view ids are private implementation details, so signal 1–3 can stop
working after a YouTube update. That is why the app ships a **Diagnostics** screen showing
exactly what it is reading, and why the timer fallback exists — the app degrades, it does
not break.

## 2. Building

Everything is a standard Gradle Android project; nothing else is needed.

### Option A — no tools at all (recommended to start)

Push this branch, then open the repository's **Actions → "Shorts Auto Scroll (Android)"**
run and download the `shorts-auto-scroll-debug-apk` artifact. That APK is ready to install.

### Option B — locally

```bash
cd ShortsAutoScroll
./gradlew assembleDebug          # -> app/build/outputs/apk/debug/app-debug.apk
./gradlew bundleRelease          # -> app/build/outputs/bundle/release/app-release.aab
```

Requirements: JDK 17, Android SDK with platform 35 and build-tools 35.0.0 (Android Studio
installs both). Windows users run `gradlew.bat` instead of `./gradlew`.

## 3. Installing on your phone

1. Copy `app-debug.apk` to the phone (USB, Drive, email to yourself — anything).
2. Tap it in the Files app and allow "install unknown apps" for whichever app you tapped
   it from.
3. Or, over USB with developer options + USB debugging on:
   ```bash
   adb install -r app-debug.apk
   ```
4. Open **Shorts Auto Scroll** → *Open accessibility settings* →
   **Installed apps → Shorts Auto Scroll → On**. Android will warn you that the service can
   observe your screen; that warning is expected for every accessibility service.
5. Open YouTube, go to Shorts, and stop touching your phone.

Optional extras in the app: a Quick Settings tile and a floating bubble, both of which
pause/resume auto scroll without leaving YouTube.

### If the accessibility toggle is greyed out ("Restricted setting")

Android 13 and newer block sideloaded apps from being granted accessibility access until
you explicitly allow it. This is expected and is not a bug in the app:

**Settings → Apps → Shorts Auto Scroll → ⋮ (top-right menu) → Allow restricted settings**

Then go back to Accessibility and the toggle will work. Installing with
`adb install -r app-debug.apk` usually avoids the restriction entirely.

### Other things worth knowing on a first run

* **Nothing happens at all.** Check *Diagnostics*: "accessibility enabled: true" and
  "service connected: true" must both be shown. If the service is enabled but not
  connected, force-stop the app and re-toggle the accessibility switch.
* **It scrolls, but on a timer rather than at the real end.** The log will say
  `timer (no progress bar found)`. Use *Dump current screen* while a Short is playing —
  the view id holding the playback position is what `ShortsProbe.kt` needs to match.
* **It scrolls twice in a row.** Raise "Wait before swiping" to ~400 ms.
* **It stops working after the screen has been off a while.** Some manufacturers (Samsung,
  Xiaomi, Oppo, Huawei) kill accessibility services aggressively. Exempt the app from
  battery optimisation: Settings → Apps → Shorts Auto Scroll → Battery → Unrestricted.
* **Detection while the phone is locked** is not a thing — the service only sees YouTube
  while YouTube is on screen and in the foreground.

## 4. Installing on a PC

Android apps do not run natively on Windows/macOS/Linux, and Windows Subsystem for Android
was discontinued in March 2025 — so use an emulator that also has the YouTube app:

* **Android Studio emulator**: create a device with a *Google Play* system image (Play
  Store included), start it, drag the APK onto the window, install YouTube from the Play
  Store, then enable the accessibility service in Settings exactly as on a phone.
* **BlueStacks / other Play-enabled emulators**: same steps; accessibility services and
  gesture dispatch work there too.

Mouse-wheel scrolling in an emulator does not interfere — the app dispatches its swipe
through the accessibility API, not through the mouse.

## 5. Tuning

| Setting | What it changes |
|---------|-----------------|
| Smart / Fixed timer | whether to detect the real end of the Short or just wait N seconds |
| Treat as finished at | 98 % swipes a hair early (feels seamless); 100 % waits for the loop |
| Wait before swiping | a deliberate pause between end and swipe |
| Safety cap | hard maximum time on one Short |
| How to scroll | gesture swipe (most reliable) or the accessibility scroll action |

If Smart mode never fires, open **Diagnostics → Dump current screen** while a Short is
playing. The dump lists every view id, label and progress range on screen; whatever holds
the playback position is the id that `ShortsProbe.kt` needs to match.

## 6. Publishing to Google Play — read this first

The app is technically ready for Play (release AAB, signing wired to environment
variables), but there are two policy realities you should decide about before spending
money on a developer account:

1. **Accessibility API policy.** Google Play requires apps that use `AccessibilityService`
   to either be genuine accessibility tools or complete a *Permissions declaration* in Play
   Console explaining the use. Apps that use the API purely for convenience automation are
   regularly rejected or removed. Hands-free Shorts browsing is a defensible accessibility
   use case (limited motor control, one-handed or no-handed use) — but the listing must
   describe it truthfully, and approval is not guaranteed. Do not set
   `android:isAccessibilityTool="true"` unless you genuinely position the app as an
   accessibility tool; a false declaration is itself a violation.
2. **YouTube's Terms of Service** prohibit accessing the service through automated means.
   An app whose entire purpose is automating the YouTube client can be reported and taken
   down on that basis, whatever the Play review says. Sideloading for your own use is a
   different risk profile from selling it to the public.

Neither of those blocks you from building and using it yourself — which is what step 3 and
4 above cover. See `PLAY_STORE.md` for the full submission checklist if you decide to go
ahead.

## 7. What the app does not do

No network access of any kind (the app requests no internet permission), no accounts, no
video downloading, no ad skipping, no analytics, nothing stored off the device. See
`PRIVACY.md`.

## 8. Source map

| File | Role |
|------|------|
| `AutoScrollService.kt` | the accessibility service: polling loop, end detection, swipe |
| `ShortsProbe.kt` | reads the YouTube window (progress bar, duration text, panels) |
| `Prefs.kt` | settings storage |
| `MainActivity.kt` | setup and settings UI |
| `DiagnosticsActivity.kt` | live view of what the service is reading |
| `OverlayController.kt` | optional floating pause/resume bubble |
| `ToggleTileService.kt` | Quick Settings tile |
