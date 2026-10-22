# Open Noise Watchface — Design

Date: 2026-10-22
Target: Noise Evolve 3 (model code NSW-28), official app `com.noisefit`

## Problem

NoiseFit, the official companion app for the Noise Evolve 3, gates custom watch
faces behind a paid subscription. The watch itself does not appear to gate them.

## Feasibility verdict: yes, with one open risk

Three facts were verified against the device and APK, not assumed:

1. **The firmware exposes the install operation.**
   `assets/python/wearable/dial.py` in the official APK drives the watch over a
   uRPC channel and calls FFI entry points on-device:
   `svc_dial_install(path, alias)` then `svc_dial_apply(dial_id)`, plus
   `svc_dial_uninstall`, `svc_dial_hide/unhide`, `svc_set_dial_order_info`.
   The Python does no signature check and makes no server call.

2. **The behaviour was observed.** A live transfer on the user's NSW-28 showed
   the two-phase pattern the code implies: transfer, a pause to apply, then a
   completed state.

3. **The package format is parseable.** Five shipped `TOPSTEP` samples share a
   consistent little-endian header whose size fields match the files exactly,
   and whose payload contains real BMP and JPEG blobs.

**Open risk:** the header shows no signature block and the app-side Python does
no verification, but firmware behaviour at install time is unverified. If
firmware rejects unsigned packages, the project fails at phase 3. This is the
single kill-switch and is treated as such in the plan.

### Rejected capture routes

- **File staging on `/sdcard`.** A watcher ran during a real transfer;
  `/sdcard/Android/media/com.noisefit/NoiseFit` stayed empty. The package is
  streamed from app-private cache and never reaches external storage.
- **adb bugreport / `btsnoop_hci.log`.** Not verified on this MIUI build;
  `/sdcard/MIUI/debug_log` is not listable by an unprivileged process.
- **Third-party prior art.** `riviangeralt/NoiseFitWatchfaceSender` documents
  `FEE7/FEE8/FEE9` UUIDs that do not appear in the official APK. Its chunker is
  unreliable and is not used.

Capture requires root or a hardware sniffer. **Phase 1 is designed to avoid
needing either** by identifying the transport family from the GATT table.

## Open inputs needed from the watch

- Native display resolution. The shipped `TOPSTEP` samples are labelled
  `368x448` and `1024000`, which are likely two different panels; NSW-28's own
  resolution is not yet confirmed and is required before the builder can size an
  image correctly.
- Whether the watch enforces a dial file-size ceiling. `gui_dial.bin` is 1.13 MB
  and `header.bin` hints at a 300 KB budget for some formats, so the limit is
  model-dependent.
- Confirmation that a free dial slot exists during testing.

## Scope

### In scope for v0.1

- Connect to the watch over BLE.
- Dump the full GATT table and raw notification frames to a log the user can
  export. This is the diagnostic artifact and identifies the transport family.
- Build a full-screen image watch face from a user-picked image.
- Install it to a free dial slot and apply it.

### Explicitly out of scope for v0.1

- Multi-widget layouts (time/date/steps at arbitrary coordinates).
- Live preview rendering.
- Watch-face marketplace or browsing.
- Firmware or OTA functions. Out of scope by choice, not omission — the app has
  `libble-crypto.so` and DFU paths, and those are easy to brick a watch with.
- Any modification of the official app.

## Delivery phases

Phases are ordered so the cheapest falsifiable check comes first.

1. **Probe** — connect, dump the GATT table and raw frames. Identifies the
   transport family and whether frames are encrypted. No protocol knowledge
   required, no write to the watch.
2. **Format** — parse and byte-exactly rebuild the shipped samples. Purely
   offline; needs no watch. If the format cannot be reproduced, stop here cheaply.
3. **Install** — first real write to the watch. This is where the signature risk
   resolves.
4. **Designer** — richer layouts, deferred until 1–3 are proven.

## Architecture

Four units with narrow interfaces. Each is independently testable; the lower two
have no Android dependency.

```
UI (Compose)
  │  user picks image
  ▼
DialBuilder            pure Kotlin, JVM-testable
  │  Bitmap + template → DialPackage (ByteArray)
  ▼
DialInstaller          pure Kotlin state machine
  │  DialPackage → framed chunks
  ▼
Transport              interface
  ├─ ProbeTransport    v0.1: reads GATT, logs frames
  └─ <Family>Transport phase 3: install/apply
```

### `DialBuilder`

- **Does:** produce a `TOPSTEP`-wrapped package containing one full-screen image
  layer and a minimal time widget.
- **Interface:** `build(image: Image, template: DialTemplate): DialPackage`.
- **Depends on:** `DialFormat` only.

### `DialFormat`

- **Does:** parse and serialise the container. Byte-accurate, no Android types.
- **Interface:** `parse(bytes): DialPackage`, `serialise(pkg): ByteArray`.
- **Depends on:** nothing.
- **Tested by:** golden files, plus a parse→serialise round-trip that must be
  byte-identical on all five shipped samples.

### `DialInstaller`

- **Does:** drive the install state machine and map watch status codes to typed
  results.
- **Interface:** `install(pkg, transport): InstallResult`.
- **States:** `Push → Install → Apply → Applied`, with `Push` resumable.
- **Depends on:** `Transport`, `DialFormat`.

### `Transport`

- **Does:** move frames. Owns connection, MTU, and framing (58-byte overhead
  observed in `FileSvc(rpc, rpc.block_size - 58)`).
- **Interface:** `connect`, `services()`, `write(frame)`, `frames(): Flow<Frame>`.
- **Depends on:** Android BLE only.

### Status-code mapping

Taken verbatim from `dial.py`, surfaced as user-facing messages:

| Code | Meaning | UI |
|---|---|---|
| `CODE_DIAL+1` | Invalid dial id | "That dial slot doesn't exist" |
| `CODE_DIAL+3` | Installed dials at limit | "Watch is full — remove a face first" |
| `CODE_DIAL+4` | Dial JSON incorrect | "Package rejected by watch" |
| `CODE_DIAL+5` | Installation busy | "Watch busy, try again" |

`+4` is the signal that a signature check exists. It is treated as a distinct,
surfaced outcome rather than a generic error, because it decides the project.

## Data flow

1. User picks an image.
2. `DialBuilder` rasterises it to the watch resolution and wraps it with a
   minimal template.
3. `DialFormat.serialise` emits the container.
4. `DialInstaller` chunks it and drives `Push → Install → Apply` over
   `Transport`.
5. Watch reports status; the UI shows either applied or a specific failure.

## Error handling

- **Transport loss mid-push:** resume from last acknowledged offset rather than
  restarting; the format supports `fs_continue_write`.
- **Watch busy (`+5`):** bounded retry with backoff, then surface.
- **Package rejected (`+4`):** no retry. This is the signature-check signal.
- **Slots full (`+3`):** offer to list and remove a face.
- **Partial write:** CRC32 per file, already part of the push protocol.

## Testing

1. **Golden parse** — all five shipped `TOPSTEP` samples parse without error.
2. **Round-trip** — parse→serialise is byte-identical for each sample.
3. **Builder** — a built package re-parses to the intended widget list.
4. **Installer state machine** — driven against a fake `Transport` that replays
   recorded status sequences.
5. **On-device** — install to the watch, confirm the face appears and applies.
   This is the only test that can fail on the signature risk.

## Risks

| Risk | Impact | Mitigation |
|---|---|---|
| Firmware rejects unsigned packages | Project fails at phase 3 | Detected early via `+4`; phase 2 is offline so failure is cheap |
| Transport family unknown for NSW-28 | Blocks phase 3 | Phase 1 probe identifies it empirically |
| Session encryption (`libble-crypto.so`) | Blocks phase 3 | Probe logs reveal whether frames are encrypted |
| TBUI descriptor schema unknown | Blocks rich layouts | v0.1 uses a minimal template; schema work is deferred |

## Non-goals restated

This project does not bypass a technical protection measure. The watch exposes
`svc_dial_install` to any connected client, which is how the official app uses
it. The work here is reimplementing a documented-by-inspection file format and a
public BLE interface, for hardware the user owns.
