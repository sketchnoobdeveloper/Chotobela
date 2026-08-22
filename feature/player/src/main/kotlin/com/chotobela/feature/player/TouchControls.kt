package com.chotobela.feature.player

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Semi-transparent on-screen controller.
 * Multi-touch works naturally: every button tracks its own pointer state.
 */
@Composable
fun TouchControls(
    mediator: PlayerInputMediator,
    opacity: Float,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            PadButton("▲", Icons.Filled.KeyboardArrowUp, Chip8KeyMap.DPAD_UP, mediator, opacity)
            Row {
                PadButton("◀", Icons.Filled.KeyboardArrowLeft, Chip8KeyMap.DPAD_LEFT, mediator, opacity)
                Box(Modifier.size(56.dp))
                PadButton("▶", Icons.Filled.KeyboardArrowRight, Chip8KeyMap.DPAD_RIGHT, mediator, opacity)
            }
            PadButton("▼", Icons.Filled.KeyboardArrowDown, Chip8KeyMap.DPAD_DOWN, mediator, opacity)
        }

        Box(Modifier.weight(1f))

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            ActionButton("A", "5", Chip8KeyMap.BUTTON_A, mediator, opacity, size = 64.dp)
            ActionButton("B", "0", Chip8KeyMap.BUTTON_B, mediator, opacity, size = 52.dp)
        }
    }
}

@Composable
private fun PadButton(
    glyph: String,
    icon: ImageVector,
    key: Int,
    mediator: PlayerInputMediator,
    opacity: Float,
    size: Dp = 60.dp
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    LaunchedEffect(pressed) { mediator.setTouch(key, pressed) }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(
                Color.White.copy(alpha = if (pressed) opacity * 1.6f else opacity * 0.7f)
            )
    ) {
        Icon(
            icon,
            contentDescription = glyph,
            tint = Color.Black.copy(alpha = 0.65f),
            modifier = Modifier.size(30.dp)
        )
    }
}

@Composable
private fun ActionButton(
    label: String,
    keyName: String,
    key: Int,
    mediator: PlayerInputMediator,
    opacity: Float,
    size: Dp
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    LaunchedEffect(pressed) { mediator.setTouch(key, pressed) }

    val base = if (key == Chip8KeyMap.BUTTON_A) Color(0xFFFFB74D) else Color(0xFF06D6A0)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(size)
            .padding(4.dp)
            .clip(CircleShape)
            .background(base.copy(alpha = if (pressed) 0.95f else opacity + 0.15f))
    ) {
        Text(
            label,
            style = MaterialTheme.typography.titleLarge,
            color = Color.Black
        )
    }
}
