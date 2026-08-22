package com.chotobela.feature.download

import android.content.Context
import com.chotobela.core.common.DispatcherProvider
import com.chotobela.core.database.dao.LibraryDao
import com.chotobela.core.database.entity.GameEntity
import com.chotobela.core.network.dto.GameDto
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.coroutineContext
import kotlinx.coroutines.ensureActive
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * DEMO MODE download manager.
 *
 * Resolves `asset://roms/...` URIs to bundled assets, streams them into
 * app-private storage with progress reporting, then installs into the library.
 * The public API matches the production resumable-HTTP implementation so
 * screens never change when the backend goes live.
 */
@Singleton
class DemoDownloadManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val libraryDao: LibraryDao,
    dispatchers: DispatcherProvider
) : DownloadManager {

    private val scope = CoroutineScope(SupervisorJob() + dispatchers.default)
    private val jobs = mutableMapOf<String, Job>()

    private val _tasks = MutableStateFlow<List<DownloadTask>>(emptyList())
    override val tasks: StateFlow<List<DownloadTask>> = _tasks.asStateFlow()

    override fun enqueue(game: GameDto) {
        if (_tasks.value.any { it.gameId == game.id && it.isActive }) return

        updateTask(game.id) { current ->
            current ?: DownloadTask(
                gameId = game.id,
                title = game.title,
                platform = game.platform,
                status = DownloadStatus.QUEUED,
                bytesTotal = game.size
            )
        }

        jobs[game.id] = scope.launch { runDemoTransfer(game) }
    }

    override fun pause(gameId: String) {
        jobs[gameId]?.cancel()
        jobs.remove(gameId)
        updateTask(gameId) { it?.copy(status = DownloadStatus.PAUSED) }
    }

    override fun resume(gameId: String) {
        val task = _tasks.value.firstOrNull { it.gameId == gameId } ?: return
        val game = DemoGameResolver.resolve(task.gameId, task.title, task.platform) ?: return
        updateTask(gameId) { it?.copy(status = DownloadStatus.DOWNLOADING) }
        jobs[gameId] = scope.launch { runDemoTransfer(game) }
    }

    override fun cancel(gameId: String) {
        jobs[gameId]?.cancel()
        jobs.remove(gameId)
        pendingFile(gameId).delete()
        _tasks.value = _tasks.value.filterNot { it.gameId == gameId }
    }

    override suspend fun isInstalled(gameId: String): Boolean =
        runCatching { libraryDao.getById(gameId) != null }.getOrDefault(false)

    // ---- transfer pipeline ----

    private suspend fun runDemoTransfer(game: GameDto) {
        try {
            setTask(game.id, DownloadStatus.DOWNLOADING)
            val dest = pendingFile(game.id)
            dest.parentFile?.mkdirs()

            val assetPath = assetPathFor(game.downloadUrl)
            var total = 0L
            context.assets.open(assetPath).use { input ->
                dest.outputStream().use { output ->
                    val buffer = ByteArray(8192)
                    while (true) {
                        coroutineContext.ensureActive()
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        total += read
                        updateTask(game.id) { it?.copy(bytesDone = total) }
                    }
                }
            }
            updateTask(game.id) { it?.copy(bytesDone = total, bytesTotal = total) }

            setTask(game.id, DownloadStatus.INSTALLING)
            installIntoLibrary(game, dest)

            setTask(game.id, DownloadStatus.COMPLETED)
            Timber.i("Installed demo ROM %s (%d bytes)", game.title, total)
        } catch (t: Throwable) {
            if (t is kotlinx.coroutines.CancellationException) throw t
            Timber.e(t, "Demo download failed")
            updateTask(game.id) {
                it?.copy(status = DownloadStatus.FAILED, error = t.message ?: "Unknown error")
            }
        }
    }

    /** Streams [input] to disk; returns total bytes written. */
    @Suppress("UNUSED_PARAMETER")
    private suspend fun installIntoLibrary(game: GameDto, downloaded: File) {
        val romDir = File(context.filesDir, "roms/${game.core}").apply { mkdirs() }
        val ext = game.downloadUrl.substringAfterLast('.', "rom")
        val target = File(romDir, "${game.id}.$ext")

        if (!downloaded.renameTo(target)) {
            downloaded.copyTo(target, overwrite = true)
            downloaded.delete()
        }

        libraryDao.upsert(
            GameEntity(
                id = game.id,
                title = game.title,
                platform = game.platform,
                core = game.core,
                description = game.description,
                developer = game.developer,
                year = game.year,
                coverUrl = game.coverImage,
                rating = game.rating,
                romPath = target.absolutePath,
                sizeBytes = target.length(),
                addedAt = System.currentTimeMillis()
            )
        )
    }

    private fun pendingFile(gameId: String): File =
        File(context.filesDir, "downloads/$gameId.part")

    private fun assetPathFor(downloadUrl: String): String {
        require(downloadUrl.startsWith("asset://")) {
            "DEMO MODE cannot fetch remote URL: $downloadUrl"
        }
        return downloadUrl.removePrefix("asset://")
    }

    private fun setTask(gameId: String, status: DownloadStatus) {
        updateTask(gameId) { it?.copy(status = status) }
    }

    private fun updateTask(
        gameId: String,
        transform: (DownloadTask?) -> DownloadTask?
    ) {
        val list = _tasks.value.toMutableList()
        val existing = list.firstOrNull { it.gameId == gameId }
        val updated = transform(existing) ?: return
        list.removeAll { it.gameId == gameId }
        list.add(0, updated)
        _tasks.value = list
    }
}

/** Maps demo catalog ids back to GameDto for resume support. */
internal object DemoGameResolver {
    fun resolve(gameId: String, title: String, platform: String): GameDto? =
        com.chotobela.core.network.DemoCatalog.games.firstOrNull { it.id == gameId }
}
