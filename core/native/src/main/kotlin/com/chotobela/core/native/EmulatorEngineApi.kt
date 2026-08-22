package com.chotobela.core.native

import java.io.Closeable

/**
 * Platform-agnostic emulator engine API.
 *
 * The implementation delegates to the native engine host (:native-engine).
 * Threading contract:
 *  - [load], [unload], [saveStateTo], [loadStateFrom] are safe from any thread (internally locked)
 *  - [step] must be called on the dedicated engine thread
 *  - [setButton], [setAxis] are lock-free and safe from any thread
 */
interface EmulatorEngineApi : Closeable {

    val coreId: CoreId

    /** Loads a ROM and prepares the core. Must be called before [start]. */
    fun load(romPath: String): Boolean

    /** Releases all core resources. */
    override fun close()

    /** Resets emulation to power-on state. */
    fun reset()

    /**
     * Advances exactly one video frame. Called by the engine loop.
     * @return false if the engine is in an unrecoverable state.
     */
    fun step(): Boolean

    // --- Save states ---
    fun saveStateTo(slot: Int): Boolean
    fun loadStateFrom(slot: Int): Boolean
    fun slotInfo(slot: Int): SaveSlotInfo

    // --- Video ---
    /** Current framebuffer dimensions (may change per game). */
    fun videoSize(): Pair<Int, Int>

    /** Direct handle to the native frame buffer for the render backend. */
    fun framebuffer(): java.nio.ByteBuffer

    // --- Audio ---
    /** Copies up to [maxSamples] stereo 16-bit samples into [out]; returns count written. */
    fun pullAudio(out: ShortArray, maxSamples: Int): Int
    fun targetSampleRate(): Int

    // --- Input ---
    /** Button bitmask update; bit positions defined per-core mapping table. */
    fun setButtons(mask: Int)
    fun setAxis(x: Float, y: Float)

    // --- Perf ---
    fun perfStats(): PerfStats
}
