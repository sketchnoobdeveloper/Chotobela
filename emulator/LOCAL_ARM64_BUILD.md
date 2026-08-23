# Building Chotobela Arcade on an ARM64 Linux host

Google ships no aarch64 NDK/aapt2 binaries. This repo carries two shims:

1. **aapt2**: x86_64 binary from Google's maven jar runs under qemu-user.
   `tools/patch-aapt2-cache.sh` swaps the launcher into the Gradle module
   cache (survives re-extraction because the source jar itself is patched).
2. **JNI shim**: compiled natively with Debian clang against the NDK sysroot
   (bionic crt), output committed to `app/src/main/arm64jni/arm64-v8a/`
   (gitignored; regenerate with):

```sh
NDK=$ANDROID_HOME/ndk/28.2.13676358
S=$NDK/toolchains/llvm/prebuilt/linux-x86_64/sysroot/usr/lib/aarch64-linux-android/29
clang -target aarch64-linux-android29 --sysroot=$NDK/toolchains/llvm/prebuilt/linux-x86_64/sysroot \
  -fuse-ld=lld -nostdlib -shared -fPIC -O2 -I app/src/main/jni \
  app/src/main/jni/mame4droid-jni.c $S/crtbegin_so.o -L$S -llog -ldl -lm -lc $S/crtend_so.o \
  -o app/src/main/arm64jni/arm64-v8a/libmame4droid-jni.so
```

Gradle picks the right path automatically (`os.arch` check in app/build.gradle).

Run: `./gradlew :app:assembleDebug --no-daemon --max-workers=1`

NOTE: this APK contains the JNI shim only. The MAME core library still comes
from the OSD build glue phase (see docs/MAME_INTEGRATION.md) and must be built
on x86_64 CI.
