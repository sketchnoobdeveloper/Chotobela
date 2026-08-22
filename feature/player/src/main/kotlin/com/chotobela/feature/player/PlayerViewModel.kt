package com.chotobela.feature.player

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chotobela.core.database.dao.LibraryDao
import com.chotobela.core.emulator.EmulatorPhase
import com.chotobela.core.emulator.EmulatorSession
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val session: EmulatorSession,
    private val libraryDao: LibraryDao
) : ViewModel() {

    val gameId: String = checkNotNull(savedStateHandle["gameId"])

    val phase: StateFlow<EmulatorPhase> = session.phase

    private val _romPath = MutableStateFlow<String?>(null)
    val romPath: StateFlow<String?> = _romPath.asStateFlow()

    init {
        viewModelScope.launch {
            val entity = runCatching { libraryDao.getById(gameId) }.getOrNull()
            _romPath.value = entity?.romPath
            val path = entity?.romPath
            if (path != null && session.currentGameId.value != gameId) {
                session.load(gameId, path)
            }
        }
    }

    fun togglePause() = session.togglePause()
    fun saveSlot(slot: Int) = session.saveToSlot(slot)
    fun loadSlot(slot: Int) = session.loadFromSlot(slot)

    override fun onCleared() {
        // Session keeps running if navigating away briefly; explicit stop on exit route.
        super.onCleared()
    }

    fun stopSession(secondsPlayed: Long) = session.stop(secondsPlayed)
}
