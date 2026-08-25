# Google Play submission checklist

Work through this in order. Steps 1–3 are the ones that decide whether the app is
publishable at all, so do them before paying for anything.

## 1. Decide the positioning (do this first)

Google Play's **Accessibility API** policy allows `AccessibilityService` for accessibility
purposes, and requires a *Permissions declaration* explaining the use for everything else.
Reviewers reject convenience-automation apps under this policy regularly.

Two honest options:

* **Assistive tool.** Position the app for people who cannot repeatedly swipe — limited
  hand mobility, tremor, one-handed use, recovering from injury. Say exactly that in the
  listing and in the declaration. This is a real accessibility use case and it is the
  framing this app was written for.
* **Do not publish; sideload.** Build the APK, install it on your own devices, done. No
  review, no policy risk. For a tool you built for yourself this is often the right answer.

Also note YouTube's Terms of Service prohibit automated access to the service. Publicly
distributing an app whose sole purpose is automating the YouTube client is a takedown risk
independent of Play review.

## 2. Target API level

Play enforces a minimum `targetSdk` for new submissions (one year behind the newest
Android release, enforced every August). This project is on `compileSdk`/`targetSdk` **35**.
Check the current requirement in Play Console → *App bundle explorer*; if it asks for 36:

```kotlin
// ShortsAutoScroll/app/build.gradle.kts
compileSdk = 36
targetSdk  = 36
// and in ShortsAutoScroll/build.gradle.kts
id("com.android.application") version "8.9.2" apply false
```

and update the CI workflow's `packages:` line to `platforms;android-36 build-tools;36.0.0`.

## 3. Create the upload key

```bash
keytool -genkeypair -v \
  -keystore upload.jks -alias upload \
  -keyalg RSA -keysize 2048 -validity 10000
```

Keep `upload.jks` and its passwords safe and out of git (`.gitignore` already excludes
`*.jks`). Enrol in **Play App Signing** so Google holds the app-signing key and this one is
only the upload key.

Building signed, locally:

```bash
export RELEASE_STORE_FILE=/absolute/path/upload.jks
export RELEASE_STORE_PASSWORD=...
export RELEASE_KEY_ALIAS=upload
export RELEASE_KEY_PASSWORD=...
cd ShortsAutoScroll && ./gradlew bundleRelease
```

Building signed in CI — add these repository secrets and the workflow does the rest:

| Secret | Value |
|--------|-------|
| `RELEASE_KEYSTORE_BASE64` | `base64 -w0 upload.jks` |
| `RELEASE_STORE_PASSWORD` | keystore password |
| `RELEASE_KEY_ALIAS` | `upload` |
| `RELEASE_KEY_PASSWORD` | key password |

## 4. Store listing material

* **App name:** Shorts Auto Scroll
* **Short description (≤80 chars):** Hands-free Shorts: swipes to the next clip when the
  current one ends.
* **Full description:** state plainly that the app uses the accessibility service to detect
  the end of playback and perform the swipe, that it works with the YouTube app, that it
  sends no data anywhere, and who it is for.
* **Graphics:** 512×512 icon, 1024×500 feature graphic, at least 2 phone screenshots
  (the settings screen and a Short mid-playback are enough).
* **Privacy policy URL:** required. `PRIVACY.md` is the text — publish it at a public URL
  (GitHub Pages on this repository works).

## 5. Console forms

* **Data safety:** "No data collected", "No data shared". True for this app.
* **Permissions declaration → Accessibility API:** describe the assistive use from step 1
  and link a short screen recording showing the app performing the swipe.
* **Content rating** questionnaire, **target audience** (not children), **ads**: none.

## 6. Release

Upload the AAB to **Internal testing** first, install through the tester link on your own
phone, confirm the accessibility service still behaves after Play re-signing, then promote
to production.
