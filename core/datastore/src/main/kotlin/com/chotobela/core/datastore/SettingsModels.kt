package com.chotobela.core.datastore

data class GraphicsSettings(
    val resolutionScale: Float = 1.0f,
    val aspectRatio: AspectRatio = AspectRatio.AUTO,
    val integerScaling: Boolean = false,
    val frameSkip: Int = 0,
    val vsync: Boolean = true,
    val shader: ShaderPreset = ShaderPreset.NONE,
    val performanceMode: PerformanceMode = PerformanceMode.BALANCED
)

enum class AspectRatio(val label: String) {
    AUTO("Auto"), RATIO_4_3("4:3"), RATIO_16_9("16:9"), STRETCH("Stretch")
}

enum class ShaderPreset(val label: String) {
    NONE("None"),
    CRT("CRT"),
    SCANLINES("Scanlines"),
    LCD("LCD Grid")
}

enum class PerformanceMode(val label: String) {
    BATTERY("Battery Saver"),
    BALANCED("Balanced"),
    PERFORMANCE("Performance")
}

data class AudioSettings(
    val enabled: Boolean = true,
    val volume: Float = 1.0f,
    val lowLatency: Boolean = true
)

data class InputSettings(
    val hapticFeedback: Boolean = true,
    val overlayOpacity: Float = 0.6f,
    val showFps: Boolean = true
)
