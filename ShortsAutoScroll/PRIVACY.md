# Privacy Policy — Shorts Auto Scroll

_Last updated: 2026-08-25_

Shorts Auto Scroll does not collect, store, transmit or sell any personal information.

## What the app can see

To know when a Short has finished playing, the app runs an Android **accessibility
service**. While the YouTube app is in the foreground, Android hands the service a
description of what is on screen. The app reads only:

* whether a Shorts player is on screen,
* the playback progress of the current Short,
* whether the comment or share panel is open.

The service is restricted in its configuration to the YouTube app
(`com.google.android.youtube`); Android does not deliver it any content from other apps.

## What the app does with it

The screen information is examined in memory and immediately discarded. Nothing is written
to storage, and nothing leaves your device. The app declares **no internet permission**, so
it is technically incapable of sending data anywhere.

The optional Diagnostics screen keeps the most recent few hundred log lines in memory only,
so you can see why the app did or did not scroll. Closing the app clears them. Nothing is
uploaded; if you choose to copy a diagnostic dump and share it, that is entirely your
action.

## What is stored

Your settings (timer length, threshold, switches) are stored in the app's private
preferences on your device and are removed when you uninstall the app.

## Permissions

| Permission | Why |
|-----------|-----|
| Accessibility service | read the Shorts playback position and perform the swipe |
| `SYSTEM_ALERT_WINDOW` | optional floating pause/resume bubble; only if you turn it on |
| `VIBRATE` | optional short buzz when a scroll happens; off by default |

## Children

The app is not directed at children and collects no data from anyone.

## Contact

Questions about this policy: eunseokchoi.code@gmail.com
