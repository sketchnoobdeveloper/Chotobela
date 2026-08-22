package com.chotobela.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.chotobela.core.datastore.AspectRatio
import com.chotobela.core.datastore.PerformanceMode
import com.chotobela.core.datastore.ShaderPreset

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val graphics by viewModel.graphics.collectAsState()
    val audio by viewModel.audio.collectAsState()
    val input by viewModel.input.collectAsState()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text("Settings", style = MaterialTheme.typography.headlineMedium)
        }

        SectionTitle("Graphics")
        ChipRow(
            label = "Aspect ratio",
            options = AspectRatio.entries.map { it.label },
            selected = graphics.aspectRatio.label,
            onSelect = { label ->
                AspectRatio.entries.firstOrNull { it.label == label }
                    ?.let(viewModel::setAspectRatio)
            }
        )
        ChipRow(
            label = "Shader",
            options = ShaderPreset.entries.map { it.label },
            selected = graphics.shader.label,
            onSelect = { label ->
                ShaderPreset.entries.firstOrNull { it.label == label }
                    ?.let(viewModel::setShader)
            }
        )
        ChipRow(
            label = "Performance",
            options = PerformanceMode.entries.map { it.label },
            selected = graphics.performanceMode.label,
            onSelect = { label ->
                PerformanceMode.entries.firstOrNull { it.label == label }
                    ?.let(viewModel::setPerformanceMode)
            }
        )
        SwitchRow("Integer scaling", graphics.integerScaling, viewModel::toggleIntegerScaling)
        SwitchRow("VSync", graphics.vsync, viewModel::toggleVSync)
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Text("Frame skip", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.weight(1f))
            FilterChip(
                selected = true,
                onClick = { viewModel.setFrameSkip((graphics.frameSkip + 1) % 4) },
                label = { Text(graphics.frameSkip.toString()) }
            )
        }

        SectionTitle("Audio")
        SwitchRow("Sound enabled", audio.enabled, viewModel::toggleAudio)
        Column(Modifier.padding(horizontal = 16.dp)) {
            Text("Volume: ${(audio.volume * 100).toInt()}%")
            Slider(
                value = audio.volume,
                onValueChange = viewModel::setVolume
            )
        }

        SectionTitle("Input & Overlay")
        SwitchRow("Show FPS counter", input.showFps) { viewModel.setShowFps(it) }

        SectionTitle("About")
        Column(Modifier.padding(horizontal = 16.dp)) {
            Text("Chotobela v0.1.0", style = MaterialTheme.typography.titleMedium)
            Text(
                "Relive your childhood gaming memories.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 8.dp)
    )
}

@Composable
private fun ChipRow(
    label: String,
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit
) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.titleMedium)
        Row {
            options.forEach { option ->
                FilterChip(
                    selected = option == selected,
                    onClick = { onSelect(option) },
                    label = { Text(option) },
                    modifier = Modifier.padding(end = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
