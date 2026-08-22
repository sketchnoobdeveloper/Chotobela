package com.chotobela.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "chotobela_settings"
)

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val RESOLUTION_SCALE = floatPreferencesKey("resolution_scale")
        val ASPECT_RATIO = intPreferencesKey("aspect_ratio")
        val INTEGER_SCALING = booleanPreferencesKey("integer_scaling")
        val FRAME_SKIP = intPreferencesKey("frame_skip")
        val VSYNC = booleanPreferencesKey("vsync")
        val SHADER = intPreferencesKey("shader_preset")
        val PERF_MODE = intPreferencesKey("performance_mode")

        val AUDIO_ENABLED = booleanPreferencesKey("audio_enabled")
        val AUDIO_VOLUME = floatPreferencesKey("audio_volume")
        val AUDIO_LOW_LATENCY = booleanPreferencesKey("audio_low_latency")

        val HAPTICS = booleanPreferencesKey("haptic_feedback")
        val OVERLAY_OPACITY = floatPreferencesKey("overlay_opacity")
        val SHOW_FPS = booleanPreferencesKey("show_fps")
    }

    val graphicsSettings: Flow<GraphicsSettings> = context.settingsDataStore.data.map { prefs ->
        GraphicsSettings(
            resolutionScale = prefs[Keys.RESOLUTION_SCALE] ?: 1.0f,
            aspectRatio = enumOrDefault(prefs[Keys.ASPECT_RATIO], AspectRatio.AUTO),
            integerScaling = prefs[Keys.INTEGER_SCALING] ?: false,
            frameSkip = prefs[Keys.FRAME_SKIP] ?: 0,
            vsync = prefs[Keys.VSYNC] ?: true,
            shader = enumOrDefault(prefs[Keys.SHADER], ShaderPreset.NONE),
            performanceMode = enumOrDefault(prefs[Keys.PERF_MODE], PerformanceMode.BALANCED)
        )
    }

    val audioSettings: Flow<AudioSettings> = context.settingsDataStore.data.map { prefs ->
        AudioSettings(
            enabled = prefs[Keys.AUDIO_ENABLED] ?: true,
            volume = prefs[Keys.AUDIO_VOLUME] ?: 1.0f,
            lowLatency = prefs[Keys.AUDIO_LOW_LATENCY] ?: true
        )
    }

    val inputSettings: Flow<InputSettings> = context.settingsDataStore.data.map { prefs ->
        InputSettings(
            hapticFeedback = prefs[Keys.HAPTICS] ?: true,
            overlayOpacity = prefs[Keys.OVERLAY_OPACITY] ?: 0.6f,
            showFps = prefs[Keys.SHOW_FPS] ?: true
        )
    }

    suspend fun updateGraphics(transform: (GraphicsSettings) -> GraphicsSettings) {
        context.settingsDataStore.edit { prefs ->
            val current = GraphicsSettings(
                resolutionScale = prefs[Keys.RESOLUTION_SCALE] ?: 1.0f,
                aspectRatio = enumOrDefault(prefs[Keys.ASPECT_RATIO], AspectRatio.AUTO),
                integerScaling = prefs[Keys.INTEGER_SCALING] ?: false,
                frameSkip = prefs[Keys.FRAME_SKIP] ?: 0,
                vsync = prefs[Keys.VSYNC] ?: true,
                shader = enumOrDefault(prefs[Keys.SHADER], ShaderPreset.NONE),
                performanceMode = enumOrDefault(prefs[Keys.PERF_MODE], PerformanceMode.BALANCED)
            )
            val updated = transform(current)
            prefs[Keys.RESOLUTION_SCALE] = updated.resolutionScale
            prefs[Keys.ASPECT_RATIO] = updated.aspectRatio.ordinal
            prefs[Keys.INTEGER_SCALING] = updated.integerScaling
            prefs[Keys.FRAME_SKIP] = updated.frameSkip
            prefs[Keys.VSYNC] = updated.vsync
            prefs[Keys.SHADER] = updated.shader.ordinal
            prefs[Keys.PERF_MODE] = updated.performanceMode.ordinal
        }
    }

    suspend fun updateAudio(transform: (AudioSettings) -> AudioSettings) {
        context.settingsDataStore.edit { prefs ->
            val current = AudioSettings(
                enabled = prefs[Keys.AUDIO_ENABLED] ?: true,
                volume = prefs[Keys.AUDIO_VOLUME] ?: 1.0f,
                lowLatency = prefs[Keys.AUDIO_LOW_LATENCY] ?: true
            )
            val updated = transform(current)
            prefs[Keys.AUDIO_ENABLED] = updated.enabled
            prefs[Keys.AUDIO_VOLUME] = updated.volume
            prefs[Keys.AUDIO_LOW_LATENCY] = updated.lowLatency
        }
    }

    suspend fun updateInput(transform: (InputSettings) -> InputSettings) {
        context.settingsDataStore.edit { prefs ->
            val current = InputSettings(
                hapticFeedback = prefs[Keys.HAPTICS] ?: true,
                overlayOpacity = prefs[Keys.OVERLAY_OPACITY] ?: 0.6f,
                showFps = prefs[Keys.SHOW_FPS] ?: true
            )
            val updated = transform(current)
            prefs[Keys.HAPTICS] = updated.hapticFeedback
            prefs[Keys.OVERLAY_OPACITY] = updated.overlayOpacity
            prefs[Keys.SHOW_FPS] = updated.showFps
        }
    }

    private inline fun <reified T : Enum<T>> enumOrDefault(ordinal: Int?, default: T): T =
        ordinal?.let { idx -> enumValues<T>().getOrNull(idx) } ?: default
}
