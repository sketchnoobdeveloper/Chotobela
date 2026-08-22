package com.chotobela.feature.download

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chotobela.core.network.DemoCatalog
import com.chotobela.core.network.dto.GameDto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DownloadsViewModel @Inject constructor(
    private val downloadManager: DownloadManager
) : ViewModel() {

    val tasks: StateFlow<List<DownloadTask>> = downloadManager.tasks

    fun pause(gameId: String) = downloadManager.pause(gameId)
    fun resume(gameId: String) = downloadManager.resume(gameId)
    fun cancel(gameId: String) = downloadManager.cancel(gameId)

    /** Re-enqueues after failure (demo catalog lookup; production uses stored request). */
    fun retry(gameId: String) {
        viewModelScope.launch {
            val game: GameDto? = DemoCatalog.games.firstOrNull { it.id == gameId }
            if (game != null) downloadManager.enqueue(game)
        }
    }
}
