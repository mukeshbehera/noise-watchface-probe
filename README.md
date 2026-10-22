# Noise Watchface Probe

An open-source companion for the **Noise Evolve 3** (model `NSW-28`) that is
intended to set custom watch faces without a NoiseFit subscription.

This repo currently contains **phase 1: a read-only BLE probe**. It exists to
answer one question before any custom-package work begins — *which BLE transport
family does this watch actually use?*

## Why a probe first

Analysis of the official app (`com.noisefit`) showed that the watch itself
exposes the watch-face install operation. `assets/python/wearable/dial.py` inside
the official APK drives the watch over uRPC and calls, on the watch:

```
svc_dial_install(remote_path, alias)
svc_dial_apply(dial_id)
```

There is no server call and no signature check in that path. The subscription
lives in the app's UI, not in the firmware.

But the APK contains several unrelated BLE stacks (`com.zhapp.ble`,
`com.topstep.wearkit`, `com.sifli.*`, `com.crrepa.*`), and the model code
`NSW-28` does not appear anywhere in the APK — the app selects a stack at
runtime. Guessing would be a waste of time. The probe reports what the watch
actually advertises.

## What the probe does

- Scans for BLE peripherals.
- Connects and enumerates every GATT service and characteristic, with properties.
- Requests a large MTU and reports the negotiated value.
- Subscribes to every notifying/indicating characteristic.
- Streams every incoming frame as a hex dump, timestamped.
- Exports the whole transcript as text for analysis.

It **never writes a command** to the watch. The only write it performs is the
CCCD descriptor needed to enable notifications, which is a subscribe operation,
not a device command.

## Install

Download the APK from the **Actions** tab (latest successful run → Artifacts),
or from a release if one is tagged. Then install it and grant the Bluetooth
permissions it asks for.

## Use

1. Open the probe, tap **Scan**.
2. Select your watch and wait for the GATT table to be listed.
3. Open NoiseFit and start a custom watch-face transfer.
4. Watch frames stream into the transcript.
5. Tap **Export log** and share the text — it tells us the framing, whether the
   session is encrypted, and which stack is in play.

## Status codes from the official app

Mapped from `dial.py`, useful once install is implemented:

| Code | Meaning |
|---|---|
| `CODE_DIAL+1` | Invalid dial id |
| `CODE_DIAL+3` | Installed dial faces at limit |
| `CODE_DIAL+4` | Dial JSON incorrect (would also signal a signature check) |
| `CODE_DIAL+5` | Installation busy |

## Roadmap

1. **Probe** — identify transport family. ← this repo, now
2. **Format** — parse and byte-exactly rebuild the shipped `TOPSTEP` samples,
   entirely offline.
3. **Install** — first real write; resolves the signature risk.
4. **Designer** — richer layouts.

See [`docs/superpowers/specs/`](docs/superpowers/specs/) for the full design and
[`docs/FINDINGS.md`](docs/FINDINGS.md) for the raw analysis.

## Scope and intent

This project reimplements a file format and a BLE interface for hardware the
owner physically possesses. It does not bypass a technical protection measure:
the watch accepts `svc_dial_install` from any connected client, which is exactly
how the official app uses it. It is not intended to redistribute Noise's
artwork, firmware, or paid content.

Firmware flashing and OTA are deliberately out of scope — those paths are easy
to brick a device with.

## Build

CI builds a debug APK on every push to `main` (see
`.github/workflows/build-apk.yml`). Locally:

```sh
./gradlew assembleDebug
```

Requires JDK 17 and the Android SDK.

## Licence

MIT for the code in this repository.
