package com.chotobela.feature.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.chotobela.core.emulator.EmulatorPhase

/**
 * Player shell (Milestone 1): session lifecycle + phase feedback.
 * Milestone 3 replaces the placeholder body with the full GLES3 renderer,
 * touch overlay, gamepad input and pause menu.
 */
@Composable
fun PlayerRoute(
    onExit: () -> Unit,
    viewModel: PlayerViewModel = hiltViewModel()
) {
    val phase by viewModel.phase.collectAsState()
    val romPath by viewModel.romPath.collectAsState()

    val enteredAt = remember { System.currentTimeMillis() }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.stopSession((System.currentTimeMillis() - enteredAt) / 1000)
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        when (val p = phase) {
            is EmulatorPhase.Idle -> if (romPath == null) {
                Text("Game not found in library", color = Color.White)
            }
            is EmulatorPhase.Loading -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Text("Loading…", color = Color.White, modifier = Modifier.padding(top = 12.dp))
            }
            is EmulatorPhase.Running, is EmulatorPhase.Paused -> {
                // Placeholder viewport; replaced by GLES surface in M3.
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "ENGINE RUNNING",
                        style = MaterialTheme.typography.headlineMedium,
                        color = Color(0xFFFFB74D)
                    )
                    Text(
                        p.toString(),
                        color = Color.White.copy(alpha = 0.6f),
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                    Button(onClick = viewModel::togglePause) {
                        Text(if (p is EmulatorPhase.Paused) "Resume" else "Pause")
                    }
                }
            }
            is EmulatorPhase.Error -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    p.message,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(16.dp)
                )
                Button(onClick = onExit) { Text("Back") }
            }
        }

        Button(onClick = onExit, modifier = Modifier.align(Alignment.BottomStart).padding(16.dp)) {
            Text("Exit")
        }
    }
}
