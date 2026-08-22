package com.chotobela.feature.store

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun GameDetailRoute(
    onBack: () -> Unit,
    onDownloaded: () -> Unit,
    viewModel: GameDetailViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) { viewModel.load() }

    val game = state.game
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Text("Game Details", style = MaterialTheme.typography.titleLarge)
            }
        }

        if (game == null) {
            item {
                Box(
                    Modifier.fillMaxWidth().padding(vertical = 64.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (state.isLoading) CircularProgressIndicator()
                    else Text(state.error ?: "Game not found")
                }
            }
        } else {
            item {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    Text(game.title, style = MaterialTheme.typography.headlineMedium)
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AssistChip(
                            onClick = {},
                            label = { Text(game.platform) }
                        )
                        AssistChip(
                            onClick = {},
                            label = { Text("★ ${String.format("%.1f", game.rating)}") }
                        )
                        AssistChip(
                            onClick = {},
                            label = { Text(formatBytes(game.size)) }
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        game.description.ifBlank { "No description available." },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "${game.developer} · ${if (game.year > 0) game.year else ""}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(20.dp))

                    when (val dl = state.downloadState) {
                        is DownloadUiState.NotInstalled -> {
                            Button(
                                onClick = viewModel::startDownload,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Filled.Download, contentDescription = null)
                                Spacer(Modifier.height(0.dp))
                                Text("  Download")
                            }
                        }
                        is DownloadUiState.Downloading -> {
                            Column {
                                LinearDeterminate(dl.progress)
                                Text(
                                    "Downloading… ${(dl.progress * 100).toInt()}%",
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        }
                        is DownloadUiState.Failed -> {
                            Column {
                                Text(
                                    "Download failed: ${dl.reason}",
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.labelLarge
                                )
                                OutlinedButton(
                                    onClick = viewModel::startDownload,
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text("Retry") }
                            }
                        }
                        is DownloadUiState.Installed -> {
                            Button(
                                onClick = onDownloaded,
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("In Library · View Downloads") }
                        }
                    }

                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
private fun LinearDeterminate(progress: Float) {
    androidx.compose.material3.LinearProgressIndicator(
        progress = { progress },
        modifier = Modifier.fillMaxWidth()
    )
}

internal fun formatBytes(bytes: Long): String = when {
    bytes <= 0 -> "—"
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024f)
    else -> "%.1f MB".format(bytes / (1024f * 1024f))
}
