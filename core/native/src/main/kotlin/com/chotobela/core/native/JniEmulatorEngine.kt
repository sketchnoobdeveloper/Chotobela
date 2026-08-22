package com.chotobela.core.native

import com.chotobela.engine.NativeEngine
import java.nio.ByteBuffer
import javax.inject.Inject
import javax.inject.Singleton

/**
 * JNI-backed implementation of [EmulatorEngineApi].
 * Delegates to libchotobela_engine.so via [NativeEngine].
 */
@Singleton
class JniEmulatorEngine @Inject constructor() : EmulatorEngineApi {

    override lateinit var coreId: CoreId
        private set

    private var initialized = false
    private var romLoaded = false

    fun init(savesDir: String): Boolean {
        NativeEngine.ensureLoaded()
        initialized = NativeEngine.nativeInit(savesDir)
        return initialized
    }

    override fun load(romPath: String): Boolean {
        if (!initialized) return false
        val id = when (romPath.substringAfterLast('.').lowercase()) {
            "ch8", "chip8" -> CoreId.CHIP8.id
            else -> CoreId.CHIP8.id // future: sniff by extension/header per platform table
        }
        coreId = CoreId.fromId(id)
        romLoaded = NativeEngine.nativeLoadRom(coreId.id, romPath)
        return romLoaded
    }

    override fun close() {
        if (initialized) {
            NativeEngine.nativeDeinit()
            initialized = false
            romLoaded = false
        }
    }

    override fun reset() = NativeEngine.nativeReset()

    override fun step(): Boolean = romLoaded && NativeEngine.nativeStepFrame()

    override fun saveStateTo(slot: Int): Boolean =
        romLoaded && NativeEngine.nativeSaveState(slot)

    override fun loadStateFrom(slot: Int): Boolean =
        romLoaded && NativeEngine.nativeLoadState(slot)

    override fun slotInfo(slot: Int): SaveSlotInfo {
        val size = if (romLoaded) NativeEngine.nativeSlotSize(slot) else -1L
        val mtime = if (romLoaded) NativeEngine.nativeSlotTime(slot) else -1L
        return SaveSlotInfo(
            slot = slot,
            exists = size >= 0,
            timestampMs = mtime,
            sizeBytes = if (size > 0) size else 0L
        )
    }

    override fun videoSize(): Pair<Int, Int> {
        val dims = NativeEngine.nativeGetVideoSize()
        return Pair(dims.getOrElse(0) { 0 }, dims.getOrElse(1) { 0 })
    }

    override fun framebuffer(): ByteBuffer = NativeEngine.nativeGetFramebuffer()
        ?: ByteBuffer.allocateDirect(0)

    override fun pullAudio(out: ShortArray, maxSamples: Int): Int = 0 // AAudio pulls natively

    override fun targetSampleRate(): Int = 48000

    override fun setButtons(mask: Int) = NativeEngine.nativeSetButtons(mask)

    override fun setAxis(x: Float, y: Float) = NativeEngine.nativeSetAxis(x, y)

    override fun perfStats(): PerfStats = PerfStats() // computed by EngineLoop

    companion object {
        init {
            // Fail fast in debug if the native library is missing from the APK
            runCatching { NativeEngine.ensureLoaded() }
        }
    }
}
