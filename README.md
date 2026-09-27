# RidingVerse 🏍️

[![Android CI](https://github.com/YOUR_GITHUB_USERNAME/ridingverse/actions/workflows/android.yml/badge.svg)](https://github.com/YOUR_GITHUB_USERNAME/ridingverse/actions/workflows/android.yml)

Real-time motorcycle group telemetry, navigation & safety — native Android
(Kotlin + Jetpack Compose).

> Replace `YOUR_GITHUB_USERNAME/ridingverse` in the badge URLs above with your
> actual GitHub repo path after pushing.

## What works end-to-end today

With **two devices + a deployed relay** (see below), these flows genuinely work:

- **Cockpit HUD** — digital TFT speedometer, glowing gear indicator (N / 1–6,
  ▲ SHIFT prompt), 24-segment tachometer with redline flash, SPORT / TRACK /
  TOURING / RAIN modes, and the live telemetry strip. Runs on a simulated ride
  out of the box; switches to real GPS when the ride service is running.
- **World map → cockpit navigation** — search any destination, preview
  Fastest / Twisties / Scenic routes (OSRM), tap *Start Ride*: the route
  polyline renders in the cockpit mini-map and a live **turn-by-turn overlay**
  (next maneuver, distance to turn, remaining, ETA) tracks you in both
  simulated and real-GPS modes.
- **Convoy rooms** — create/join a room with a code like `RV-9042`; live rider
  pins with names, roles (LEAD / SWEEP / MEDIC / PACK) and pin colors sync
  through the relay in real time.
- **Crash detection → SOS** — 50 Hz accelerometer + gyroscope monitor;
  >3.8 G shock correlated with >2.5 rad/s tumble starts a 30-second abortable
  countdown, then SMSes your emergency contacts (managed in Settings) with a
  Google Maps link to your last GPS fix. Manual SOS button included.
- **PTT intercom** — half-duplex push-to-talk with a real AudioRecord →
  AudioTrack pipeline, energy-gate wind-noise indicator, and an on-device
  loopback self-test. The network voice packet protocol is defined
  (JSON header + base64 PCM); only the relay transport wiring remains.
- **Offline** — routes, GPX waypoints, track points and the convoy roster are
  cached in Room (SQLite).

## Setup

1. **Prerequisites:** Android Studio (Hedgehog+), **JDK 17**.
2. Open this folder in Android Studio and let Gradle sync.
3. **Gradle wrapper:** if `gradle/wrapper/gradle-wrapper.jar` is missing
   (network-blocked environments), run once locally with any Gradle 8.x:
   `gradle wrapper --gradle-version 8.7`.
4. Run on a device (or emulator with GPS): grant the permissions when asked.
5. **Settings tab — do these three things:**
   - *Rider profile*: set your display name + map pin color.
   - *Relay server*: paste your relay's `wss://…` URL (deploy `relay/` first —
     one command on fly.io, see [relay/README.md](relay/README.md)).
   - *Emergency contacts*: add at least one name + phone number, or SOS
     texts have nowhere to go.
6. Map tab → find a route → **Start Ride** → the cockpit takes over with live
   guidance. Convoy tab → create/join a room to ride together.

## Getting the APK from GitHub Actions

Every push to `main` builds a debug APK automatically:

1. Push this repo to GitHub.
2. Open the **Actions** tab → latest *Android CI* run → **Artifacts** →
   download `app-debug`.
3. Transfer the APK to your phone and install (enable "install unknown apps"
   once). No Play Store needed for testing.

## What still needs a human

| Item | What to do |
|---|---|
| **Deploy the relay** | `cd relay && fly launch --no-deploy && fly deploy` (or Render web service). Paste the `wss://` URL into Settings. Without this, convoy sync can't work. |
| **PTT voice over network** | ~20 lines: in `ConvoyScreen`, pass an `onFrame` to `PttManager.startTransmit` that sends `frame.toJson()` over `TelemetrySocket`, and route incoming `ptt` packets to `pttManager.playReceivedFrame()`. Protocol is documented in `PttManager.kt`. |
| **Play Store signing** | Generate an upload key (`keytool`), add a `signingConfigs.release` block, keep the `.jks` out of git (already ignored). |
| **Wrapper jar** | If your clone lacks `gradle/wrapper/gradle-wrapper.jar`, run `gradle wrapper --gradle-version 8.7` once locally and commit it. CI bootstraps it automatically. |
| **API keys / maps** | Tiles are OSM/Esri/Carto (no key needed). If you add Google Places, put the key in `local.properties`, never in git. |

## Roadmap

- Ride recorder: persist `TrackPointEntity` breadcrumbs + GPX export.
- Offline maps: MBTiles download for no-coverage areas.
- Opus encoding for PTT voice frames (smaller than raw PCM).
- Crash sensitivity auto-calibration per bike.
- Wear OS companion: SOS abort + mini speedo on the wrist.

## Project layout

```
app/src/main/java/com/ridingverse/app/
├── MainActivity.kt            # nav, permissions onboarding, SOS dialog, GPS wiring
├── RidingVerseApp.kt          # shared singletons (settings, socket, db, convoy repo)
├── data/
│   ├── local/                 # Room: routes, waypoints, track points, riders
│   ├── remote/                # TelemetrySocket (OkHttp WS) + ConvoyRepository
│   └── settings/              # AppSettings (DataStore): contacts, relay URL, identity
├── presentation/
│   ├── cockpit/               # TFT cluster + RideState machine + mini-map
│   ├── map/                   # RidingMapView (reusable Leaflet WebView) + bridge
│   ├── navigation/            # OSRM step parser, geometry fallback, RouteNavigator
│   ├── convoy/                # room join/leave, roster, PTT button
│   ├── permissions/           # one-time runtime permission flow
│   └── settings/              # DataStore-backed settings UI
├── service/                   # foreground ride service, crash detection, SOS
└── intercom/                  # PttManager: real audio path + voice protocol
relay/                         # Node.js WebSocket relay (ws only) + Dockerfile
.github/workflows/android.yml  # CI: JDK 17 → assembleDebug → APK artifact
```
