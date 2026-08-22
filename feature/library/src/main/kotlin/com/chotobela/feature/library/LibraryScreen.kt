package com.chotobela.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.chotobela.core.database.entity.GameEntity
import com.chotobela.core.ui.components.EmptyState

@Composable
fun LibraryScreen(
    onPlayGame: (String) -> Unit,
    viewModel: LibraryViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    var sortMenuOpen by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {

        Text(
            text = "Library",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        )

        OutlinedTextField(
            value = state.query,
            onValueChange = viewModel::setQuery,
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            placeholder = { Text("Search games") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            LibrarySection.entries.forEach { section ->
                FilterChip(
                    selected = state.section == section,
                    onClick = { viewModel.setSection(section) },
                    label = { Text(section.label) }
                )
            }
            androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
            androidx.compose.foundation.layout.Box {
                TextButton(onClick = { sortMenuOpen = true }) {
                    Text("Sort: ${state.sort.label}")
                }
                DropdownMenu(expanded = sortMenuOpen, onDismissRequest = { sortMenuOpen = false }) {
                    SortOrder.entries.forEach { order ->
                        DropdownMenuItem(
                            text = { Text(order.label) },
                            onClick = {
                                viewModel.setSort(order)
                                sortMenuOpen = false
                            }
                        )
                    }
                }
            }
        }

        if (state.isEmpty) {
            EmptyState(
                title = "No games here yet",
                subtitle = "Import ROMs or download from the Chotobela Store"
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(110.dp),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(state.games, key = { it.id }) { game ->
                    LibraryGameCard(
                        game = game,
                        onClick = { onPlayGame(game.id) },
                        onToggleFavorite = { viewModel.toggleFavorite(game) },
                        onDelete = { viewModel.delete(game) }
                    )
                }
            }
        }
    }
}

