# Chotobela

**Relive your childhood gaming memories.**

Chotobela is a premium retro gaming platform for Android: emulator engine + game
library + integrated store + cloud platform, built with a production-grade
clean architecture.

## Tech stack

- **Kotlin 2.0 / Jetpack Compose / Material 3** (dynamic color, dark & light)
- **Clean Architecture, MVVM, multi-module** (16 Gradle modules)
- **Hilt** DI · **Coroutines/Flow** · **Room** · **DataStore**
- **Android NDK / C17 native engine** (`libchotobela_engine.so`)
- **Supabase** backend (auth/catalog/storage) — runs in **DEMO MODE** until credentials are configured

## Module map

```
app/                    Application entry, navigation host
core/
  common/               Result types, dispatchers, logging
  database/             Room (library, save-state index)
  datastore/            Settings (DataStore) + encrypted SecureStore
  network/              Supabase client, catalog API, demo fallback
  emulator/             Session controller (load/pause/save/playtime)
  native/               EmulatorEngineApi facade + paced engine loop
  ui/                   Theme system + shared composables
feature/
  home/ library/ store/ player/ profile/ settings/ download/
native-engine/          C sources -> libchotobela_engine.so
  engine-host/          Core ABI registry, ROM I/O, save states
  cores/chip8/          Original CHIP-8 interpreter (reference core)
  renderer/ audio/ input/   Platform backend hooks (GLES3/AAudio)
```

## Emulator architecture

Kotlin app → JNI bridge → engine host (C ABI `cb_core_api`) → swappable cores.

The `cb_core_api` contract (see `native-engine/src/main/cpp/engine-host/core_abi.h`)
is core-agnostic by design — future MAME / FBNeo / Libretro adapters implement the
same interface without touching any Kotlin code.

Threading model (mirrors professional emulators):
- dedicated engine thread @60Hz fixed pacing
- render samples framebuffer asynchronously (GLES3 surface)
- AAudio low-latency callback pulls audio natively (no JNI in the audio path)

## Build

Requirements: JDK 17, Android SDK 35, NDK 27.2+, CMake 3.22.1.

```bash
./gradlew :app:assembleDebug
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`

### Supabase configuration (optional)

Add to `local.properties` (never committed):

```properties
supabase.url=https://YOUR_PROJECT.supabase.co
supabase.key=YOUR_ANON_KEY
```

Without these values the app runs fully offline in DEMO MODE with a seeded
CHIP-8 catalog. Backend schema lives in `database-schema/supabase_schema.sql`.

## CI

GitHub Actions builds debug APKs on every push/PR to `main`
(`.github/workflows/ci.yml`) and uploads them as artifacts.

## Roadmap

See `docs/ROADMAP.md`.
