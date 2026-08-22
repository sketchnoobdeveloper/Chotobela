package com.chotobela.engine

/**
 * Low-level JNI bindings to libchotobela_engine.so.
 * Prefer [com.chotobela.core.native.EmulatorEngineApi] for application use.
 */
object NativeEngine {

    @Volatile
    private var loaded = false

    fun ensureLoaded() {
        if (!loaded) {
            synchronized(this) {
                if (!loaded) {
                    System.loadLibrary("chotobela_engine")
                    loaded = true
                }
            }
        }
    }

    // ---- Host lifecycle ----
    external fun nativeInit(savesDir: String): Boolean
    external fun nativeDeinit()

    // ---- Core ----
    external fun nativeLoadRom(coreId: String, romPath: String): Boolean
    external fun nativeReset()
    external fun nativeStepFrame(): Boolean

    // ---- Video ----
    external fun nativeGetVideoSize(): IntArray
    external fun nativeGetFramebuffer(): java.nio.ByteBuffer?
    external fun nativeGetAspect(): Float

    // ---- Audio ----
    external fun nativeAudioStart(): Int
    external fun nativeAudioStop()
    external fun nativeSetVolume(volume: Float)

    // ---- Input ----
    external fun nativeSetButtons(mask: Int)
    external fun nativeSetAxis(x: Float, y: Float)

    // ---- Save states ----
    external fun nativeSaveState(slot: Int): Boolean
    external fun nativeLoadState(slot: Int): Boolean
    external fun nativeSlotSize(slot: Int): Long
    external fun nativeSlotTime(slot: Int): Long
}
