package com.chotobela.feature.download

import com.chotobela.core.network.dto.GameDto
import kotlinx.coroutines.flow.StateFlow

enum class DownloadStatus {
    QUEUED,
    DOWNLOADING,
    PAUSED,
    VERIFYING,
    EXTRACTING,
    INSTALLING,
    COMPLETED,
    FAILED
}

data class DownloadTask(
    val gameId: String,
    val title: String,
    val platform: String,
    val status: DownloadStatus,
    val bytesDone: Long = 0L,
    val bytesTotal: Long = 0L,
    val error: String? = null,
    val createdAtMs: Long = System.currentTimeMillis()
) {
    val progress: Float
        get() = if (bytesTotal > 0) (bytesDone.toFloat() / bytesTotal) else 0f

    val isActive: Boolean
        get() = status == DownloadStatus.QUEUED || status == DownloadStatus.DOWNLOADING ||
            status == DownloadStatus.VERIFYING || status == DownloadStatus.EXTRACTING ||
            status == DownloadStatus.INSTALLING
}

/**
 * Professional download pipeline:
 * enqueue -> background transfer -> verification -> extraction -> library install.
 *
 * v1 implementation runs in DEMO MODE (copies bundled CHIP-8 ROMs).
 * Production implementation swaps in resumable HTTP + SHA-256 verification.
 */
interface DownloadManager {
    /** All tasks, most recent first. */
    val tasks: StateFlow<List<DownloadTask>>

    fun enqueue(game: GameDto)
    fun pause(gameId: String)
    fun resume(gameId: String)
    fun cancel(gameId: String)

    /** True when [gameId] has completed installation into the library. */
    suspend fun isInstalled(gameId: String): Boolean
}
