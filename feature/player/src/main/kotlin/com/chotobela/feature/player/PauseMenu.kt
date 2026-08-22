package com.chotobela.feature.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chotobela.core.engine.SaveSlotInfo
import java.text.DateFormat
import java.util.Date

/**
 * In-game pause overlay: resume, six save/load slots, screenshot, exit.
 */
@Composable
fun PauseMenu(
    viewModel: PlayerViewModel,
    onResume: () -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val slots by viewModel.slots.collectAsStateWithLifecycle()

    Column(
        modifier
            .background(Color.Black.copy(alpha = 0.86f))
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Paused", style = MaterialTheme.typography.headlineMedium, color = Color.White)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = {
                viewModel.screenshot()
                onResume()
            }) {
                Icon(Icons.Filled.CameraAlt, "Screenshot", tint = Color.White)
            }
            IconButton(onClick = onResume) {
                Icon(Icons.Filled.Close, "Resume", tint = Color.White)
            }
        }

        Spacer(Modifier.height(12.dp))

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(PlayerViewModel.SLOT_IDS, key = { it }) { id ->
                SlotCard(
                    info = slots.firstOrNull { it.slot == id },
                    onSave = {
                        viewModel.saveSlot(id)
                        viewModel.refreshSlots()
                    },
                    onLoad = {
                        viewModel.loadSlot(id)
                        viewModel.refreshSlots()
                        onResume()
                    }
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TextButton(onClick = onExit, modifier = Modifier.weight(1f)) {
                Text("Save & Exit", color = MaterialTheme.colorScheme.error)
            }
            androidx.compose.material3.Button(onClick = {
                viewModel.saveSlot(PlayerViewModel.AUTO_SLOT)
                onResume()
            }, modifier = Modifier.weight(1f)) {
                Text("Quick Save & Continue")
            }
        }
    }
}

@Composable
private fun SlotCard(
    info: SaveSlotInfo?,
    onSave: () -> Unit,
    onLoad: () -> Unit
) {
    Card {
        Column(Modifier.padding(10.dp)) {
            val label = info?.let {
                if (it.exists) {
                    "Slot ${it.slot} · ${DateFormat.getDateTimeInstance().format(Date(it.timestampMs))}"
                } else "Slot ${it.slot} · Empty"
            } ?: "Slot ? · Empty"

            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 2,
                color = if (info?.exists == true) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onSave, enabled = true) { Text("Save") }
                TextButton(onClick = onLoad, enabled = info?.exists == true) { Text("Load") }
            }
        }
    }
}
