package com.chotobela.feature.player

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chotobela.core.datastore.AudioSettings
import com.chotobela.core.datastore.GraphicsSettings
import com.chotobela.core.datastore.InputSettings
import com.chotobela.core.datastore.SettingsRepository
import com.chotobela.core.database.dao.LibraryDao
import com.chotobela.core.emulator.EmulatorPhase
import com.chotobela.core.emulator.EmulatorSession
import com.chotobela.core.engine.EmulatorEngineApi
import com.chotobela.core.engine.SaveSlotInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val session: EmulatorSession,
    private val libraryDao: LibraryDao,
    private val settingsRepository: SettingsRepository,
    val engine: EmulatorEngineApi
) : ViewModel() {

    companion object {
        const val AUTO_SLOT = 0
        val SLOT_IDS = (0..5).toList()
    }

    val gameId: String = checkNotNull(savedStateHandle["gameId"])

    val phase: StateFlow<EmulatorPhase> = session.phase

    /** Shared by GL renderer + input sources. */
    val inputMediator = PlayerInputMediator(engine)

    fun sessionFps(): Float = session.perfStats().fps

    private val _romPath = MutableStateFlow<String?>(null)
    val romPath: StateFlow<String?> = _romPath.asStateFlow()

    private val _slots = MutableStateFlow<List<SaveSlotInfo>>(emptyList())
    val slots: StateFlow<List<SaveSlotInfo>> = _slots.asStateFlow()

    val graphicsSettings = settingsRepository.graphicsSettings
    val audioSettings = settingsRepository.audioSettings
    val inputSettings = settingsRepository.inputSettings

    init {
        viewModelScope.launch {
            val entity = runCatching { libraryDao.getById(gameId) }.getOrNull()
            _romPath.value = entity?.romPath
            val path = entity?.romPath
            if (path != null && session.currentGameId.value != gameId) {
                session.load(gameId, path)
            }
            refreshSlots()
        }
        // Live-apply audio settings (volume, mute)
        viewModelScope.launch {
            settingsRepository.audioSettings.collect { session.applyAudioNow(it) }
        }
        // Haptics flag for the mediator
        viewModelScope.launch {
            settingsRepository.inputSettings.collect {
                inputMediator.hapticsEnabled = it.hapticFeedback
            }
        }
    }

    fun graphicsOf(g: GraphicsSettings): GraphicsSettings = g
    fun audioOf(a: AudioSettings): AudioSettings = a
    fun inputOf(i: InputSettings): InputSettings = i

    fun refreshSlots() {
        viewModelScope.launch(Dispatchers.IO) {
            _slots.value = session.slotInfos()
        }
    }

    fun togglePause() = session.togglePause()

    fun pause() = session.pause()
    fun resume() = session.resume()

    fun saveSlot(slot: Int) {
        session.saveToSlot(slot)
        refreshSlots()
    }

    fun loadSlot(slot: Int) {
        session.loadFromSlot(slot)
    }

    fun screenshot() {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { session.captureScreenshot() }
        }
    }

    fun stopSession(secondsPlayed: Long) = session.stop(secondsPlayed)

    override fun onCleared() {
        inputMediator.reset()
        super.onCleared()
    }
}
