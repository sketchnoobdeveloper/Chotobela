package com.chotobela.core.emulator

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface EmulatorPhase {
    data object Idle : EmulatorPhase
    data class Loading(val romPath: String) : EmulatorPhase
    data object Running : EmulatorPhase
    data object Paused : EmulatorPhase
    data class Error(val message: String) : EmulatorPhase
}

/**
 * Observable state of the single active emulator session.
 */
class EmulatorStateHolder {
    private val _phase = MutableStateFlow<EmulatorPhase>(EmulatorPhase.Idle)
    val phase: StateFlow<EmulatorPhase> = _phase.asStateFlow()

    private val _currentGameId = MutableStateFlow<String?>(null)
    val currentGameId: StateFlow<String?> = _currentGameId.asStateFlow()

    internal fun set(phase: EmulatorPhase) { _phase.value = phase }
    internal fun setGame(gameId: String?) { _currentGameId.value = gameId }

    val isBusy: Boolean
        get() = _phase.value is EmulatorPhase.Loading
}
