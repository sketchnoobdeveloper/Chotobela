package com.chotobela.feature.store

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chotobela.core.network.StoreApi
import com.chotobela.core.network.dto.GameDto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class StoreUiState(
    val featured: List<GameDto> = emptyList(),
    val trending: List<GameDto> = emptyList(),
    val recent: List<GameDto> = emptyList(),
    val categories: List<String> = emptyList(),
    val selectedCategory: String? = null,
    val searchResults: List<GameDto>? = null,
    val isLoading: Boolean = true,
    val error: String? = null
)

@HiltViewModel
class StoreViewModel @Inject constructor(
    private val storeApi: StoreApi
) : ViewModel() {

    private val _uiState = MutableStateFlow(StoreUiState())
    val uiState: StateFlow<StoreUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            runCatching { loadCatalog(null) }
                .onSuccess { state -> _uiState.value = state.copy(isLoading = false) }
                .onFailure { t ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = t.message ?: "Store unavailable"
                    )
                }
        }
    }

    private suspend fun loadCatalog(category: String?): StoreUiState {
        return if (category != null) {
            StoreUiState(
                featured = emptyList(),
                trending = emptyList(),
                recent = storeApi.byCategory(category),
                categories = storeApi.categories(),
                selectedCategory = category,
                searchResults = null
            )
        } else {
            StoreUiState(
                featured = storeApi.featuredGames(),
                trending = storeApi.trendingGames(),
                recent = storeApi.recentlyAdded(),
                categories = storeApi.categories(),
                selectedCategory = null,
                searchResults = null
            )
        }
    }

    fun selectCategory(category: String?) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            runCatching { loadCatalog(category) }
                .onSuccess { state -> _uiState.value = state.copy(isLoading = false) }
                .onFailure { t ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = t.message ?: "Failed to load category"
                    )
                }
        }
    }

    fun search(query: String) {
        viewModelScope.launch {
            if (query.isBlank()) {
                _uiState.value = _uiState.value.copy(searchResults = null)
                return@launch
            }
            _uiState.value = _uiState.value.copy(isLoading = true)
            runCatching { storeApi.search(query) }
                .onSuccess { results ->
                    _uiState.value = _uiState.value.copy(searchResults = results, isLoading = false)
                }
                .onFailure { t ->
                    _uiState.value = _uiState.value.copy(isLoading = false, error = t.message)
                }
        }
    }
}
