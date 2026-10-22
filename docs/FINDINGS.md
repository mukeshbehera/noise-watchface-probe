# NoiseFit / Noise Evolve 3 — watch face RE findings

Source: `com.noisefit` base.apk (207.8 MB, 18 dex) pulled from device via `pm path`.
Analysis date: 2026-10-22.

## Verified: the watch-face engine ships in cleartext

`assets/python/wearable/dial.py` (RT-Thread wearable runtime, Apache-2.0 headers) is
the install driver. It calls FFI functions **on the watch**:

    svc_dial_install(remote_path, alias)   -> status code table in file
    svc_dial_apply(dial_id)
    svc_dial_get_current / svc_dial_info
    svc_dial_uninstall / svc_dial_hide / svc_dial_unhide
    svc_set_dial_order_info

Flow (from `service_dial_install`): push resource folder to watch FS via
`wearable.files.push.push()`, then on `onSuccess` call `__dial_install__`, then
`remove(remote_path)` deletes the local staged copy.

Status codes are documented in-source, e.g.:
- CODE_DIAL+1 "Invalid dial id"
- CODE_DIAL+3 "number of installed dial faces has reached the upper limit"
- CODE_DIAL+4 "The dial JSON information is incorrect"
- CODE_DIAL+5 "The installation dial is busy"

## Verified: transport framing

`wearable/files/push.py`:
- `FileSvc(rpc, rpc.block_size - 58)`  -> 58 bytes framing overhead per packet
- CRC32 per file, skip-if-identical, resumable via `fs_continue_write`
- no signature check on the push path (in the Python layer)

## Verified: two/three dial package formats

### 1. Template dial (modern, "GuiDial")
- `assets/video/firmware/dial.json`, `file.json`, `Time/`, `Date/`, `Week/`,
  `Fade/`, `Ani/`
- `frame_version: 1`, `dial_version: 1`, `dial_type: 9`
- `resolution_ratio: "480x480"` (frames are 80x80 in assets)
- `platform: "ats3085s"`
- widgets: `animal` (animated), `text` (font frames, fontnum + min_numwidth),
  `array` (7 weekday images)
- `assets/video/app/app.json` defines clock positions (3 slots), 19 preset
  `pure_color` values, and a color-picture pipeline

### 2. Legacy textbox dial
- `assets/dial/style/OSW-802N/template1..4.json`
- widget `textbox`, `data` in {`time_hour24`, `minute`, `dot_second`,
  `date_weekday_day`}, `view: [x,y,w,h]` (negative offsets allowed),
  `font_type`, `font_size`, `font_color` RGBA, `zorder`, `animation`
- `assets/dial/style/4362/template.bin` — an uncompressed prebuilt dial
  (`0100020209...` header, ff-filled tables, 967 KB)

### 3. TOPSTEP container (self-describing, verified by parsing)
`assets/gui_dial.bin`, `gui_dial_368_448.bin`, `gui_dial_titan.bin`, `header_tp.bin`

Little-endian header:
    0x00  "TOPSTEP"  + 0x01          8-byte magic
    0x0c  u32 total file size        (matches len exactly in all 5 samples)
    0x24  u32 header size = 1024
    0x28  u32 payload size           (total - 1024, matches exactly)
    0x30  u32 version 0x00340001
    0x38  ASCII container name       e.g. "gui_dial_binfile_watch_1024000_1_20241011_ota.bin"

Payload at 0x400 begins with its own magic **"TBUI"** + u32, then a table of
widget descriptors (x, y, w, h appear as u16/u32 at regular strides).
Payload also contains embedded BMP (3) and JPEG (8) blobs — so the dial renders
from real image data, not a proprietary codec.
File tail ends with 28+ zero bytes then another "TBUI" magic at EOF-4.
No zlib/gzip/zip anywhere. No obvious signing block in the header.

## Verified: BLE stack layering

- `com.zhapp.ble` / `com.zh.wear` / `com.zh.ble` — ~1491 class refs (Evolve2 family)
- `com.topstep.wearkit` / `fitcloud` / `flywear` — ~130 refs, includes
  `GUIWriter$WatchFaceOffset_t{control_offset=...}` and `$WatchFaceControl_t{index=...}`
- `com.sifli.watchfacelibrary` / `siflidfu` — JADX-visible name strings for
  `WatchFaceLib::install`
- `com.crrepa.ble.trans.watchface` — Jieli + Sifli entity models
- `com/example/proto/Watchdial` — protobuf `protocol_watch_dial_plate_{operate,
  list_item, inquire_reply}` plus `dialStyle`, `dialFileMd5`
- `D1 02` write header observed as a string constant
- `libble-crypto.so` (10 KB) present in arm64 split

## NOT supported claims (retracted)

- `FEE7/FEE8/FEE9` UUIDs from riviangeralt/NoiseFitWatchfaceSender do NOT appear
  in the official APK. That project's 20-byte chunker is unreliable prior art.
- Low nibbles 1/2/3/5/6 in the `#5:4362` strings are **Kotlin SMAP debug
  metadata**, not a device->style mapping table.
- `4362` / `OSW-802N` are dial style asset folders; the link to specific device
  models is unproven.

## Open questions blocking a build

1. Does the watch verify a signature/checksum at `svc_dial_install`? Header shows
   no obvious signature block, but firmware behaviour is unverified.
2. Which transport family does Evolve 3 use (zhapp / topstep / sifli / jieli)?
3. Is `libble-crypto.so` used on this model? Session key derivation unknown.
4. Exact `TBUI` descriptor table schema inside the TOPSTEP payload.
5. Whether the package is streamed (never hits disk) or staged in app-private cache.
