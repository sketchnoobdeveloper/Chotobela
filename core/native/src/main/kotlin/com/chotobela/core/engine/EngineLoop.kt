package com.chotobela.core.engine

import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Dedicated emulation thread with fixed-rate frame pacing (default 60Hz).
 * Mirrors the MAME4droid threading model: engine steps on its own thread,
 * rendering samples the framebuffer asynchronously on the GL thread.
 */
@Singleton
class EngineLoop @Inject constructor(
    private val engine: JniEmulatorEngine
) {
    companion object {
        const val TARGET_FPS = 60.0
        private const val FRAME_NS = (1_000_000_000.0 / TARGET_FPS).toLong()
    }

    private var thread: Thread? = null
    private val running = AtomicBoolean(false)
    private val paused = AtomicBoolean(false)

    private val frameCount = AtomicLong(0)
    private val skippedFrames = AtomicLong(0)
    @Volatile private var lastFps: Float = 0f
    @Volatile private var lastFrameMs: Float = 0f

    /** Called once per second with the measured FPS. */
    @Volatile var onFpsUpdate: ((Float) -> Unit)? = null

    fun start() {
        if (running.getAndSet(true)) return
        thread = Thread({
            var nextFrameNs = System.nanoTime()
            var windowFrames = 0L
            var windowStartNs = nextFrameNs

            while (running.get()) {
                if (!paused.get()) {
                    val t0 = System.nanoTime()
                    if (!engine.step()) {
                        skippedFrames.incrementAndGet()
                    }
                    lastFrameMs = (System.nanoTime() - t0) / 1_000_000f
                    frameCount.incrementAndGet()
                    windowFrames++

                    val now = System.nanoTime()
                    if (now - windowStartNs >= 1_000_000_000L) {
                        lastFps = windowFrames * 1_000_000_000f / (now - windowStartNs)
                        onFpsUpdate?.invoke(lastFps)
                        windowFrames = 0
                        windowStartNs = now
                    }
                }

                nextFrameNs += FRAME_NS
                val sleepNs = nextFrameNs - System.nanoTime()
                if (sleepNs > 0) {
                    Thread.sleep(sleepNs / 1_000_000, (sleepNs % 1_000_000).toInt())
                } else {
                    // fell behind; resync without accumulating debt
                    nextFrameNs = System.nanoTime()
                }
            }
        }, "chotobela-engine").apply {
            priority = Thread.MAX_PRIORITY
            start()
        }
    }

    fun setPaused(value: Boolean) {
        paused.set(value)
    }

    fun isPaused(): Boolean = paused.get()

    fun stop() {
        running.set(false)
        thread?.interrupt()
        thread?.join(1500)
        thread = null
    }

    fun stats(): PerfStats = PerfStats(
        fps = lastFps,
        frameTimeMs = lastFrameMs,
        skippedFrames = skippedFrames.get(),
        audioUnderruns = 0
    )
}
