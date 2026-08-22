package com.chotobela.feature.download

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.chotobela.core.ui.components.EmptyState

@Composable
fun DownloadsScreen(viewModel: DownloadsViewModel = hiltViewModel()) {
    val tasks by viewModel.tasks.collectAsState()

    Column(Modifier.fillMaxSize()) {
        Text(
            "Downloads",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        )

        if (tasks.isEmpty()) {
            EmptyState(
                title = "No downloads",
                subtitle = "Grab something from the Chotobela Store"
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(tasks, key = { it.gameId }) { task ->
                    DownloadCard(
                        task = task,
                        onPause = { viewModel.pause(task.gameId) },
                        onResume = { viewModel.resume(task.gameId) },
                        onCancel = { viewModel.cancel(task.gameId) },
                        onRetry = { viewModel.retry(task.gameId) }
                    )
                }
            }
        }
    }
}

@Composable
private fun DownloadCard(
    task: DownloadTask,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Text(task.title, style = MaterialTheme.typography.titleLarge)
            Text(
                text = "${task.platform} · ${statusLabel(task.status)}",
                style = MaterialTheme.typography.labelLarge,
                color = when (task.status) {
                    DownloadStatus.FAILED -> MaterialTheme.colorScheme.error
                    DownloadStatus.COMPLETED -> MaterialTheme.colorScheme.secondary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
            )

            Spacer(Modifier.height(10.dp))

            if (task.isActive || task.status == DownloadStatus.PAUSED) {
                LinearProgressIndicator(
                    progress = { task.progress },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(6.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "${formatMb(task.bytesDone)} / ${formatMb(task.bytesTotal)}",
                        style = MaterialTheme.typography.labelLarge
                    )
                    Row {
                        if (task.isActive && task.status == DownloadStatus.DOWNLOADING) {
                            TextButton(onClick = onPause) { Text("Pause") }
                        }
                        if (task.status == DownloadStatus.PAUSED) {
                            TextButton(onClick = onResume) { Text("Resume") }
                        }
                        TextButton(onClick = onCancel) { Text("Cancel") }
                    }
                }
            } else if (task.status == DownloadStatus.FAILED) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        task.error ?: "Failed",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onRetry) { Text("Retry") }
                }
            } else if (task.status == DownloadStatus.COMPLETED) {
                Text(
                    "Installed to library ✓",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }
    }
}

private fun statusLabel(status: DownloadStatus): String = when (status) {
    DownloadStatus.QUEUED -> "Queued"
    DownloadStatus.DOWNLOADING -> "Downloading"
    DownloadStatus.PAUSED -> "Paused"
    DownloadStatus.VERIFYING -> "Verifying"
    DownloadStatus.EXTRACTING -> "Extracting"
    DownloadStatus.INSTALLING -> "Installing"
    DownloadStatus.COMPLETED -> "Completed"
    DownloadStatus.FAILED -> "Failed"
}

private fun formatMb(bytes: Long): String = when {
    bytes >= 1024L * 1024L -> "%.1f MB".format(bytes / (1024f * 1024f))
    bytes >= 1024L -> "%.1f KB".format(bytes / 1024f)
    else -> "$bytes B"
}
