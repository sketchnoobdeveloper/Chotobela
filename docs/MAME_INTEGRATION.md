# MAME Integration Plan

Vendored tree: `third_party/mame` (see `third_party/VENDORED_MAME.md`).

## Goal

Arcade emulation inside Chotobela through the existing swappable-core ABI
(`native-engine/src/main/cpp/engine-host/core_abi.h`). No Kotlin changes are
required when the adapter is complete: a new core registers itself exactly like
`cores/chip8` does today.

## Architecture (mirrors how professional ports do it)

```
Kotlin (feature/player)
      |
JNI bridge (existing)
      |
engine host (existing cb_core_api)
      |                        <- NEW: mame/ adapter implements cb_core_api
MAME OSD shim  <----------------- maps MAME's osd_interface onto our backends
      |
GLES3 renderer / AAudio / input   (already shipped in Chotobela)
```

Key insight from studying upstream: MAME never touches the platform directly —
everything goes through its OSD layer (`src/osd/modules/*`). Our adapter
implements a **minimal OSD** that forwards:

| MAME OSD module | Chotobela backend |
|---|---|
| video / draw | our GLES3 texture pipeline (`GameShaderRenderer`) |
| sound | AAudio pull callback (`audio_backend.c`) |
| input | button bitmask via `cb_set_buttons` |
| file | app-private storage paths |
| window | none needed (single fullscreen surface) |

## Build strategy

Upstream ships an `android-project/` and supports cross-builds; for v1 we do
NOT build all of MAME (hours of CI + giant binary). Instead:

1. Compile only the emulator core library + selected driver sets using
   upstream's make system (`make SUBTARGET=tiny` pattern) against NDK.
2. Start with 2–3 drivers (e.g., `puckman`, `dkong`) to validate the pipeline.
3. Scale driver coverage per release; each added set grows APK size ~linearly.

## Steps

- [x] Vendor unmodified sources (tag mame0289)
- [ ] Minimal OSD skeleton compiling against NDK (C++17, exceptions off)
- [ ] cb_core_api adapter registering as `"mame"`
- [ ] One driver running at full speed on arm64
- [ ] ROM hash/database plumbing for store catalog entries

## License note

GPL-2.0+: shipping MAME-derived binaries makes the distributed APK GPLv2.
See `third_party/VENDORED_MAME.md`.
