# Lull

A calm, text-only YouTube Music client for E Ink phones, built with
[Mudita Mindful Design (MMD)](https://github.com/mudita/MMD). Designed for the Mudita Kompakt
running LineageOS, with a lean Android Auto mode.

Play something on purpose: no home feed, no recommendations, no artwork, no endless scroll.
The queue ends when it ends.

## Features

- Sign in with your own YouTube Music account (Google's sign-in page; Lull keeps only the
  session cookies, encrypted with the Android Keystore).
- Search songs, albums, artists, playlists, podcasts and episodes, in fixed pages with
  Previous / Next.
- Library: Downloads, Playlists, Liked songs, Podcasts, Albums, Artists.
- Long listening (classes, episodes, anything over 10 minutes) resumes where you stopped;
  playback speed from 0.75× to 2×.
- Offline downloads (YouTube Music Premium), Wi-Fi-only by default.
- Now Playing with large controls, like, download and a sleep timer. Position updates every
  15 seconds or on tap, to spare the E Ink screen.
- Queue with a clear end. Radio is opt-in, from the Queue screen or in Settings.
- Android Auto: text-only lists (Downloads, Playlists, Podcasts, Liked songs), 30 rows max, voice and
  typed search, one shared player, small buffer for phones with little RAM.

## Install

Download the latest `Lull-vX.Y.Z.apk` from [Releases](https://github.com/stephane849/Lull/releases)
and open it on the phone. Releases are signed with one fixed key, so later releases install
as updates.

## Build

**With GitHub (no setup):** every push builds a debug APK; open the run under **Actions** and
download `lull-debug-apk`. Pushing a tag like `v1.0.1` builds a signed APK and publishes a
release (needs the `LULL_KEYSTORE_B64` and `LULL_KEYSTORE_PASSWORD` repository secrets).

**With Android Studio:** open the folder, let Gradle sync, then Run. Requires JDK 17+.

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

## Disclaimer

Lull is unofficial and not affiliated with YouTube, Google or Mudita. It uses YouTube
Music's undocumented web API, which is against YouTube's terms of service; your account is
your responsibility. Consider testing with a secondary account first.

## License

GPL-3.0-or-later (see `LICENSE`). Lull uses NewPipe Extractor (GPL-3.0), MMD and Media3
(Apache-2.0).
