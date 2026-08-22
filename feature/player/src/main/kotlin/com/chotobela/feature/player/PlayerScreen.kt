package com.chotobela.feature.player

import android.app.Activity
import android.content.pm.ActivityInfo
import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chotobela.core.datastore.AspectRatio
import com.chotobela.core.emulator.EmulatorPhase
import com.chotobela.feature.player.render.GameShaderRenderer
import kotlinx.coroutines.delay

/**
 * Full emulator player:
 *  GL surface + touch overlay + gamepad + haptics + pause/save menu + HUD.
 */
@Composable
fun PlayerRoute(
    onExit: () -> Unit,
    viewModel: PlayerViewModel = hiltViewModel()
) {
    val phase by viewModel.phase.collectAsStateWithLifecycle()
    val graphics by viewModel.graphicsSettings.collectAsStateWithLifecycle(
        initialValue = com.chotobela.core.datastore.GraphicsSettings()
    )
    val input by viewModel.inputSettings.collectAsStateWithLifecycle(
        initialValue = com.chotobela.core.datastore.InputSettings()
    )

    var pausedMenuOpen by remember { mutableStateOf(false) }
    var fps by remember { mutableStateOf(0f) }

    val context = LocalContext.current
    val view = LocalView.current
    val glView = remember { EmulatorGLView(context, viewModel.engine) }
    val enteredAtMs = remember { System.currentTimeMillis() }

    // Landscape + keep-screen-on while playing
    DisposableEffect(Unit) {
        val activity = context as? Activity
        val previousOrientation = activity?.requestedOrientation
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        view.keepScreenOn = true
        viewModel.inputMediator.attachVibrator(context)
        onDispose {
            view.keepScreenOn = false
            activity?.requestedOrientation =
                previousOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    // FPS polling
    LaunchedEffect(phase is EmulatorPhase.Running) {
        while (true) {
            fps = viewModel.sessionFps()
            delay(500)
        }
    }

    // Graphics settings -> renderer
    LaunchedEffect(graphics.shader, graphics.aspectRatio, graphics.integerScaling) {
        with(glView.renderer) {
            shaderPresetId = graphics.shader.ordinal
            aspectMode = when (graphics.aspectRatio) {
                AspectRatio.AUTO -> GameShaderRenderer.ASPECT_AUTO
                AspectRatio.RATIO_4_3 -> GameShaderRenderer.ASPECT_4_3
                AspectRatio.RATIO_16_9 -> GameShaderRenderer.ASPECT_16_9
                AspectRatio.STRETCH -> GameShaderRenderer.ASPECT_STRETCH
            }
            pixelPerfect = graphics.integerScaling
        }
    }

    BackHandler { pausedMenuOpen = !pausedMenuOpen }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onPreviewKeyEvent { event ->
                handleGamepadKey(viewModel, event)
            }
    ) {
        AndroidView(
            factory = { glView },
            modifier = Modifier.fillMaxSize()
        )

        when (val p = phase) {
            is EmulatorPhase.Loading -> CenterStatus("Loading…") { CircularProgressIndicator() }
            is EmulatorPhase.Error -> CenterStatus(p.message) {
                Button(onClick = onExit) { Text("Back") }
            }
            else -> Unit
        }

        if (phase is EmulatorPhase.Running || phase is EmulatorPhase.Paused) {
            if (input.showFps && phase is EmulatorPhase.Running) {
                Text(
                    text = "${fps.toInt()} FPS",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color(0xFF06D6A0),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp)
                )
            }

            TouchControls(
                mediator = viewModel.inputMediator,
                opacity = input.overlayOpacity,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
            )
        }

        Row(
            Modifier
                .align(Alignment.TopStart)
                .padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            androidx.compose.material3.TextButton(onClick = {
                if (phase is EmulatorPhase.Paused || pausedMenuOpen) {
                    pausedMenuOpen = !pausedMenuOpen
                } else {
                    viewModel.pause()
                    viewModel.refreshSlots()
                    pausedMenuOpen = true
                }
            }) {
                Text(
                    if (pausedMenuOpen) "Resume" else "☰ Menu",
                    color = Color.White
                )
            }
        }

        if (pausedMenuOpen) {
            PauseMenu(
                viewModel = viewModel,
                onResume = {
                    pausedMenuOpen = false
                    viewModel.resume()
                },
                onExit = {
                    viewModel.saveSlot(PlayerViewModel.AUTO_SLOT)
                    viewModel.stopSession((System.currentTimeMillis() - enteredAtMs) / 1000)
                    onExit()
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

/** Android gamepad/keyboard events -> CHIP-8 keys. Returns true when consumed. */
private fun handleGamepadKey(
    viewModel: PlayerViewModel,
    event: androidx.compose.ui.input.key.KeyEvent
): Boolean {
    val action = event.nativeKeyEvent.action
    val keyCode = event.nativeKeyEvent.keyCode
    val key: Int = when (keyCode) {
        KeyEvent.KEYCODE_DPAD_UP -> Chip8KeyMap.DPAD_UP
        KeyEvent.KEYCODE_DPAD_DOWN -> Chip8KeyMap.DPAD_DOWN
        KeyEvent.KEYCODE_DPAD_LEFT -> Chip8KeyMap.DPAD_LEFT
        KeyEvent.KEYCODE_DPAD_RIGHT -> Chip8KeyMap.DPAD_RIGHT
        KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_Z -> Chip8KeyMap.BUTTON_A
        KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_X -> Chip8KeyMap.BUTTON_B
        else -> null
    } ?: return false

    when (action) {
        KeyEvent.ACTION_DOWN -> {
            if (event.nativeKeyEvent.repeatCount == 0) {
                viewModel.inputMediator.setPad(key, true)
            }
            return true
        }
        KeyEvent.ACTION_UP -> {
            viewModel.inputMediator.setPad(key, false)
            return true
        }
    }
    return false
}

@Composable
private fun CenterStatus(message: String, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            content()
            Text(message, color = Color.White, modifier = Modifier.padding(top = 12.dp))
        }
    }
}
