# Lull

A calm, text-only YouTube Music client for E Ink phones, built with
[Mudita Mindful Design (MMD)](https://github.com/mudita/MMD). Designed for the Mudita Kompakt
running LineageOS, with a lean Android Auto mode.

Play something on purpose: no home feed, no recommendations, no artwork, no endless scroll.
The queue ends when it ends.

## Features

- Sign in with your own YouTube Music account (Google's sign-in page; Lull keeps only the
  session cookies, encrypted with the Android Keystore).
- Search songs, albums, artists and playlists, in fixed pages with Previous / Next.
- Library: Downloads, Playlists, Liked songs, Albums, Artists.
- Offline downloads (YouTube Music Premium), Wi-Fi-only by default.
- Now Playing with large controls, like, download and a sleep timer. Position updates every
  15 seconds or on tap, to spare the E Ink screen.
- Queue with a clear end. Radio is opt-in, from the Queue screen or in Settings.
- Android Auto: text-only lists (Downloads, Playlists, Liked songs), 30 rows max, voice and
  typed search, one shared player, small buffer for phones with little RAM.

## Build

**With GitHub (no setup):** push this folder to a GitHub repository. The included workflow
builds a debug APK on every push. Open the run under **Actions** and download
`lull-debug-apk`.

**With Android Studio:** open the folder, let Gradle sync, then Run. Requires JDK 17+.

Install on the phone with `adb install app-debug.apk`, or copy the APK over and open it.

## Android Auto

Android Auto hides apps that didn't come from the Play Store. On the phone:
Android Auto settings → tap **Version** about 10 times → ⋮ menu → **Developer settings** →
enable **Unknown sources**. Lull then appears in the car's app list. Recheck after Android
Auto updates.

## Project layout

- `core/` — pure Kotlin, unit-tested: the InnerTube (YouTube Music web API) client and a
  response parser that walks the JSON tree instead of fixed paths.
- `app/` — Android: Compose + MMD screens, Media3 playback service (phone, notification,
  Bluetooth and Android Auto), downloads, stream resolution via NewPipe Extractor.

## When something breaks

YouTube changes things without notice. The fragile pieces are isolated:

| Symptom | Where to look |
| --- | --- |
| Songs won't play | Bump `NewPipeExtractor` in `app/build.gradle.kts` (it deciphers stream URLs) |
| Requests fail with HTTP 400 | `CLIENT_VERSION` in `core/.../InnerTube.kt` |
| Search stops filtering | `SearchFilter` params in `core/.../Models.kt` |
| Lists come back empty | `Parser.kt` renderer names |
| Session expired | Settings → Sign out, then sign in again |

Library versions were picked in October 2026 and couldn't be test-compiled in the
environment that wrote them; if Gradle sync complains about a version, take the version it
suggests.

## Before publishing

- Set `SOURCE_URL` in `app/.../ui/screens/SettingsScreen.kt` to your repository.
- Release builds are signed with the debug key so CI APKs install directly. Use your own key
  for anything you distribute.

## Disclaimer

Lull is unofficial and not affiliated with YouTube, Google or Mudita. It uses YouTube
Music's undocumented web API, which is against YouTube's terms of service; your account is
your responsibility. Consider testing with a secondary account first.

## License

GPL-3.0-or-later (see `LICENSE`). Lull uses NewPipe Extractor (GPL-3.0), MMD and Media3
(Apache-2.0).
