package com.chotobela.core.emulator

import android.content.Context
import android.graphics.Bitmap
import com.chotobela.core.common.DispatcherProvider
import com.chotobela.core.database.dao.LibraryDao
import com.chotobela.core.database.dao.SaveStateDao
import com.chotobela.core.database.entity.SaveStateEntity
import com.chotobela.core.datastore.AudioSettings
import com.chotobela.core.datastore.SettingsRepository
import com.chotobela.core.engine.EmulatorEngineApi
import com.chotobela.core.engine.EngineLoop
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single active emulator session controller.
 *
 * Owns the full lifecycle: ROM load -> engine loop -> AAudio output,
 * plus save-state slots, screenshots and playtime bookkeeping.
 */
@Singleton
class EmulatorSession @Inject constructor(
    @ApplicationContext private val context: Context,
    private val engine: EmulatorEngineApi,
    private val loop: EngineLoop,
    private val stateHolder: EmulatorStateHolder,
    private val settings: SettingsRepository,
    private val libraryDao: LibraryDao,
    private val saveStateDao: SaveStateDao,
    dispatchers: DispatcherProvider
) {
    companion object {
        const val SLOT_COUNT = 6 // 0 = auto slot, 1..5 manual
        private const val MAX_SAVES_DIR = "saves"
        private const val SCREENSHOTS_DIR = "screenshots"
    }

    private val scope = CoroutineScope(SupervisorJob() + dispatchers.main)

    val phase get() = stateHolder.phase
    val currentGameId get() = stateHolder.currentGameId
    fun perfStats() = loop.stats()

    init {
        scope.launch {
            loop.onFpsUpdate = { /* surfaced via perfStats polling in player UI */ }
        }
        engine.setButtons(0)
    }

    /** Prepares native host once per process; safe to call repeatedly. */
    fun ensureHostReady(): Boolean {
        val dir = File(context.filesDir, MAX_SAVES_DIR).apply { mkdirs() }
        return engine.initHost(dir.absolutePath)
    }

    suspend fun load(gameId: String, romPath: String): Boolean {
        if (!ensureHostReady()) {
            stateHolder.set(EmulatorPhase.Error("Native engine failed to initialize"))
            return false
        }
        stateHolder.setGame(gameId)
        stateHolder.set(EmulatorPhase.Loading(romPath))
        val ok = engine.load(romPath)
        if (!ok) {
            stateHolder.set(EmulatorPhase.Error("Failed to load ROM: $romPath"))
            return false
        }
        applyAudioSettings()
        loop.start()
        loop.setPaused(false)
        stateHolder.set(EmulatorPhase.Running)
        Timber.i("Emulator running game=%s rom=%s", gameId, romPath)
        return true
    }

    fun pause() {
        if (stateHolder.phase.value == EmulatorPhase.Running) {
            loop.setPaused(true)
            stateHolder.set(EmulatorPhase.Paused)
        }
    }

    fun resume() {
        if (stateHolder.phase.value == EmulatorPhase.Paused) {
            loop.setPaused(false)
            stateHolder.set(EmulatorPhase.Running)
        }
    }

    fun togglePause() {
        when (stateHolder.phase.value) {
            is EmulatorPhase.Running -> pause()
            is EmulatorPhase.Paused -> resume()
            else -> Unit
        }
    }

    fun stop(sessionSecondsPlayed: Long) {
        val gameId = stateHolder.currentGameId.value
        if (gameId != null && sessionSecondsPlayed > 0) {
            scope.launch {
                runCatching {
                    libraryDao.recordPlaySession(gameId, System.currentTimeMillis(), sessionSecondsPlayed)
                }.onFailure { Timber.w(it, "playtime record failed") }
            }
        }
        loop.stop()
        stateHolder.set(EmulatorPhase.Idle)
        stateHolder.setGame(null)
    }

    // ---- Save states ----

    fun saveToSlot(slot: Int): Boolean {
        val ok = engine.saveStateTo(slot)
        if (ok) {
            val gameId = stateHolder.currentGameId.value ?: return true
            scope.launch {
                runCatching {
                    val info = engine.slotInfo(slot)
                    saveStateDao.upsert(
                        SaveStateEntity(
                            gameId = gameId,
                            slot = slot,
                            filePath = slotFilePath(slot),
                            screenshotPath = null,
                            createdAt = if (info.timestampMs > 0) info.timestampMs else System.currentTimeMillis()
                        )
                    )
                }
            }
        }
        return ok
    }

    fun loadFromSlot(slot: Int): Boolean = engine.loadStateFrom(slot)

    suspend fun slotInfos(): List<com.chotobela.core.engine.SaveSlotInfo> =
        (0 until SLOT_COUNT).map { engine.slotInfo(it) }

    private fun slotFilePath(slot: Int): String =
        File(context.filesDir, "$MAX_SAVES_DIR/slot$slot.state").absolutePath

    // ---- Screenshot ----

    /**
     * Captures the current framebuffer as a PNG.
     * @return saved file, or null on failure.
     */
    fun captureScreenshot(): File? {
        val (w, h) = engine.videoSize()
        if (w <= 0 || h <= 0) return null
        val buffer = engine.framebuffer()
        if (!buffer.isDirect || buffer.remaining() < w * h * 4) return null

        val pixels = IntArray(w * h)
        buffer.rewind()
        for (p in 0 until w * h) {
            val argb = buffer.int // big-endian read of ARGB bytes as written by core
            // Core writes ARGB8888 in memory order R,G,B,A (little-endian uint32).
            val r = (argb shr 24) and 0xFF
            val g = (argb shr 16) and 0xFF
            val b = (argb shr 8) and 0xFF
            pixels[p] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }

        val dir = File(context.filesDir, SCREENSHOTS_DIR).apply { mkdirs() }
        val out = File(dir, "shot_${System.currentTimeMillis()}.png")
        return runCatching {
            val bmp = Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)
            out.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bmp.recycle()
            out
        }.getOrNull()
    }

    // ---- Settings application ----

    fun applyAudioSettings() {
        scope.launch {
            val audio = settings.audioSettings.first()
            applyAudioNow(audio)
        }
    }

    fun applyAudioNow(audio: AudioSettings) {
        if (!audio.enabled) {
            engine.stopAudioTransport()
            return
        }
        engine.setMasterVolume(if (audio.volume > 0f) audio.volume else 1f)
        engine.startAudioTransport()
    }
}
