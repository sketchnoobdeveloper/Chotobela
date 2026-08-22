package com.chotobela.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chotobela.core.database.dao.LibraryDao
import com.chotobela.core.network.DemoCatalog
import com.chotobela.core.network.dto.ProfileDto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProfileUiState(
    val username: String = "RetroPlayer",
    val gamesPlayed: Int = 0,
    val totalSeconds: Long = 0L,
    val favorites: Int = 0
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val libraryDao: LibraryDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val demo: ProfileDto? =
                runCatching { DemoCatalog.demoProfile }.getOrNull()
            _uiState.value = _uiState.value.copy(
                username = demo?.username ?: "RetroPlayer"
            )
            libraryDao.observeAll().collect { games ->
                _uiState.value = _uiState.value.copy(
                    gamesPlayed = games.count { it.lastPlayedAt != null },
                    totalSeconds = games.sumOf { it.playtimeSeconds },
                    favorites = games.count { it.favorite }
                )
            }
        }
    }
}
