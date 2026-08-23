package com.chotobela.core.engine

/** Identifies which emulator core runs a given game. */
enum class CoreId(val id: String, val displayName: String) {
    CHIP8("chip8", "CHIP-8"),
    SI8080("si8080", "Arcade 8080"),
    MAME("mame", "MAME"),
    FBNEO("fbneo", "FinalBurn Neo");

    companion object {
        fun fromId(id: String): CoreId = entries.firstOrNull { it.id == id } ?: CHIP8
    }
}

sealed interface EngineEvent {
    data object Loaded : EngineEvent
    data object Started : EngineEvent
    data object Paused : EngineEvent
    data object Resumed : EngineEvent
    data object Stopped : EngineEvent
    data class Error(val message: String, val code: Int = -1) : EngineEvent
}

data class PerfStats(
    val fps: Float = 0f,
    val frameTimeMs: Float = 0f,
    val skippedFrames: Long = 0,
    val audioUnderruns: Long = 0
)

data class SaveSlotInfo(
    val slot: Int,
    val exists: Boolean,
    val timestampMs: Long = 0L,
    val sizeBytes: Long = 0L
)
