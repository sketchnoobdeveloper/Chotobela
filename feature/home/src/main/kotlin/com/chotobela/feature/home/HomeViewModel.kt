package com.chotobela.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chotobela.core.database.entity.GameEntity
import com.chotobela.core.database.dao.LibraryDao
import com.chotobela.core.network.StoreApi
import com.chotobela.core.network.dto.GameDto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val featured: List<GameDto> = emptyList(),
    val trending: List<GameDto> = emptyList(),
    val isLoadingStore: Boolean = true,
    val error: String? = null
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    libraryDao: LibraryDao,
    private val storeApi: StoreApi
) : ViewModel() {

    val continuePlaying: StateFlow<List<GameEntity>> =
        libraryDao.observeContinuePlaying()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingStore = true, error = null)
            runCatching {
                StoreSections(
                    featured = storeApi.featuredGames(),
                    trending = storeApi.trendingGames()
                )
            }.onSuccess { sections ->
                _uiState.value = HomeUiState(
                    featured = sections.featured,
                    trending = sections.trending,
                    isLoadingStore = false
                )
            }.onFailure { t ->
                _uiState.value = _uiState.value.copy(
                    isLoadingStore = false,
                    error = t.message ?: "Failed to load store"
                )
            }
        }
    }

    private data class StoreSections(val featured: List<GameDto>, val trending: List<GameDto>)
}
