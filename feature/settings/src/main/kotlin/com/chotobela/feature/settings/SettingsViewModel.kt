package com.chotobela.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chotobela.core.datastore.AspectRatio
import com.chotobela.core.datastore.AudioSettings
import com.chotobela.core.datastore.GraphicsSettings
import com.chotobela.core.datastore.InputSettings
import com.chotobela.core.datastore.PerformanceMode
import com.chotobela.core.datastore.SettingsRepository
import com.chotobela.core.datastore.ShaderPreset
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsRepository
) : ViewModel() {

    val graphics: StateFlow<GraphicsSettings> = settings.graphicsSettings
        .stateIn(viewModelScope, SharingStarted.Eagerly, GraphicsSettings())

    val audio: StateFlow<AudioSettings> = settings.audioSettings
        .stateIn(viewModelScope, SharingStarted.Eagerly, AudioSettings())

    val input: StateFlow<InputSettings> = settings.inputSettings
        .stateIn(viewModelScope, SharingStarted.Eagerly, InputSettings())

    fun updateGraphics(transform: (GraphicsSettings) -> GraphicsSettings) {
        viewModelScope.launch { settings.updateGraphics(transform) }
    }

    fun setAspectRatio(ratio: AspectRatio) = updateGraphics { it.copy(aspectRatio = ratio) }
    fun setShader(shader: ShaderPreset) = updateGraphics { it.copy(shader = shader) }
    fun setFrameSkip(skip: Int) = updateGraphics {
        it.copy(frameSkip = skip.coerceIn(0, 3))
    }
    fun toggleIntegerScaling() =
        updateGraphics { it.copy(integerScaling = !it.integerScaling) }
    fun toggleVSync() = updateGraphics { it.copy(vsync = !it.vsync) }
    fun setPerformanceMode(mode: PerformanceMode) =
        updateGraphics { it.copy(performanceMode = mode) }

    fun setVolume(volume: Float) {
        viewModelScope.launch {
            settings.updateAudio { it.copy(volume = volume.coerceIn(0f, 1f)) }
        }
    }

    fun toggleAudio() {
        viewModelScope.launch { settings.updateAudio { it.copy(enabled = !it.enabled) } }
    }

    fun setShowFps(show: Boolean) {
        viewModelScope.launch { settings.updateInput { it.copy(showFps = show) } }
    }
}
