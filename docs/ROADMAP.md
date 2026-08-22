# Chotobela Roadmap

## Shipped
- [x] M1 — 16-module skeleton, Hilt graph, M3 theme, navigation, splash, CI
- [x] M2 — Native engine host (C ABI), JNI bridge, CHIP-8 core, save states
- [x] M3 — Player: GLES3 renderer, AAudio audio, touch overlay + gamepad, pause menu
- [x] M4 — Library: Room, SAF import, sections, search/sort/filter
- [x] M5 — Store + resumable download manager (demo mode; Supabase-ready)
- [x] M6 — Profile, settings (graphics/audio/input), playtime tracking
- [x] M7 — Polish, docs, roadmap

## Next
1. **Live backend** — add `supabase.url/key` to local.properties, run
   `database-schema/supabase_schema.sql`, swap `DemoDownloadManager` binding for the
   HTTP implementation (interface already stable).
2. **Core adapters** — FBNeo port first (GPL, smaller than MAME), then a scoped MAME
   driver build; both plug into `cb_core_api` without Kotlin changes.
3. **GLES post-processing shaders** — CRT/scanline/LCD presets are wired as native
   hooks (`cb_shader_preset`) and need GPU implementations.
4. **Cloud save sync** — Room index exists; upload/download via Supabase Storage.
5. **Achievements & leaderboards** — tables + RLS shipped in schema; client UI pending.
6. **NetPlay** — rollback netcode on top of deterministic cores (MAME4droid-style).
7. **Controller editor UI** — per-game touch layout persistence.
