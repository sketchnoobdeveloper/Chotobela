package com.chotobela.feature.store

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chotobela.core.database.dao.LibraryDao
import com.chotobela.core.network.StoreApi
import com.chotobela.core.network.dto.GameDto
import com.chotobela.feature.download.DownloadManager
import com.chotobela.feature.download.DownloadStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface DownloadUiState {
    data object NotInstalled : DownloadUiState
    data class Downloading(val progress: Float) : DownloadUiState
    data class Failed(val reason: String) : DownloadUiState
    data object Installed : DownloadUiState
}

data class GameDetailUiState(
    val game: GameDto? = null,
    val isLoading: Boolean = true,
    val error: String? = null,
    val downloadState: DownloadUiState = DownloadUiState.NotInstalled,
    val favorite: Boolean = false
)

@HiltViewModel
class GameDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val storeApi: StoreApi,
    private val libraryDao: LibraryDao,
    private val downloadManager: DownloadManager
) : ViewModel() {

    private val gameId: String = checkNotNull(savedStateHandle["gameId"])

    private val _uiState = MutableStateFlow(GameDetailUiState())
    val uiState: StateFlow<GameDetailUiState> = _uiState.asStateFlow()

    fun load() {
        if (_uiState.value.game != null) return
        viewModelScope.launch {
            runCatching { storeApi.gameById(gameId) }
                .onSuccess { game ->
                    if (game == null) {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            error = "Game not found"
                        )
                        return@launch
                    }
                    observeDownloads(game)
                    val installed = libraryDao.getById(game.id) != null
                    val entity = libraryDao.getById(game.id)
                    _uiState.value = _uiState.value.copy(
                        game = game,
                        isLoading = false,
                        favorite = entity?.favorite ?: false,
                        downloadState =
                            if (installed || downloadManager.isInstalled(game.id))
                                DownloadUiState.Installed
                            else DownloadUiState.NotInstalled
                    )
                }
                .onFailure { t ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = t.message ?: "Failed to load game"
                    )
                }
        }
    }

    private fun observeDownloads(game: GameDto) {
        viewModelScope.launch {
            downloadManager.tasks.collect { tasks ->
                val task = tasks.firstOrNull { it.gameId == game.id } ?: return@collect
                val newState = when (task.status) {
                    DownloadStatus.COMPLETED -> DownloadUiState.Installed
                    DownloadStatus.FAILED ->
                        DownloadUiState.Failed(task.error ?: "Download failed")
                    else -> DownloadUiState.Downloading(task.progress)
                }
                if (_uiState.value.downloadState != newState) {
                    _uiState.value = _uiState.value.copy(downloadState = newState)
                }
            }
        }
    }

    fun startDownload() {
        val game = _uiState.value.game ?: return
        _uiState.value = _uiState.value.copy(downloadState = DownloadUiState.Downloading(0f))
        downloadManager.enqueue(game)
    }

    fun toggleFavorite() {
        val game = _uiState.value.game ?: return
        viewModelScope.launch {
            libraryDao.upsert(
                com.chotobela.core.database.entity.GameEntity(
                    id = game.id,
                    title = game.title,
                    platform = game.platform,
                    core = game.core,
                    description = game.description,
                    developer = game.developer,
                    year = game.year,
                    coverUrl = game.coverImage,
                    rating = game.rating,
                    romPath = "",
                    addedAt = System.currentTimeMillis()
                )
            )
        }
    }
}
