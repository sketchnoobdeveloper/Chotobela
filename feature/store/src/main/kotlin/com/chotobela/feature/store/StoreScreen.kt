package com.chotobela.feature.store

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import com.chotobela.core.network.dto.GameDto
import com.chotobela.core.ui.components.EmptyState
import com.chotobela.core.ui.components.GameCard
import com.chotobela.core.ui.components.SectionHeader

@Composable
fun StoreScreen(
    onGameClick: (String) -> Unit,
    viewModel: StoreViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    var searchQuery by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        item {
            Text(
                "Chotobela Store",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = searchQuery,
                onValueChange = {
                    searchQuery = it
                    viewModel.search(it)
                },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                placeholder = { Text("Search the store") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            )
        }

        item {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(listOf("All") + state.categories.map { it.replaceFirstChar { c -> c.uppercase() } }) { cat ->
                    val raw = cat.lowercase()
                    val selected =
                        if (cat == "All") state.selectedCategory == null
                        else state.selectedCategory == raw
                    FilterChip(
                        selected = selected,
                        onClick = {
                            if (cat == "All") viewModel.selectCategory(null)
                            else viewModel.selectCategory(raw)
                        },
                        label = { Text(cat) }
                    )
                }
            }
        }

        when {
            state.isLoading && !state.isContentPresent() -> item {
                Box(
                    Modifier.fillParentMaxWidth().padding(vertical = 48.dp),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator() }
            }
            state.error != null && !state.isContentPresent() -> item {
                EmptyState(
                    title = "Store unavailable",
                    subtitle = state.error
                )
            }
            else -> {
                val results = state.searchResults
                if (results != null) {
                    item { SectionHeader("Results (${results.size})") }
                    if (results.isEmpty()) {
                        item { EmptyState(title = "Nothing found", subtitle = "Try another search") }
                    } else {
                        item {
                            GameGrid(results, onGameClick)
                        }
                    }
                } else {
                    if (state.featured.isNotEmpty()) {
                        item {
                            SectionHeader("Featured")
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(state.featured, key = { it.id }) { game ->
                                    GameCard(
                                        title = game.title,
                                        coverUrl = game.coverImage,
                                        platform = game.platform,
                                        rating = game.rating,
                                        onClick = { onGameClick(game.id) }
                                    )
                                }
                            }
                        }
                    }
                    if (state.trending.isNotEmpty()) {
                        item {
                            SectionHeader("Trending Now")
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(state.trending, key = { it.id }) { game ->
                                    GameCard(
                                        title = game.title,
                                        coverUrl = game.coverImage,
                                        platform = game.platform,
                                        rating = game.rating,
                                        onClick = { onGameClick(game.id) }
                                    )
                                }
                            }
                        }
                    }
                    if (state.recent.isNotEmpty()) {
                        item { SectionHeader(if (state.selectedCategory != null) state.selectedCategory!!.replaceFirstChar { it.uppercase() } else "Recently Added") }
                        item { GameGrid(state.recent, onGameClick) }
                    }
                }
            }
        }
    }
}

private fun StoreUiState.isContentPresent(): Boolean =
    featured.isNotEmpty() || trending.isNotEmpty() || recent.isNotEmpty() ||
        (searchResults?.isNotEmpty() == true)

@Composable
private fun GameGrid(games: List<GameDto>, onGameClick: (String) -> Unit) {
    Column(
        Modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        games.chunked(2).forEach { rowGames ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                rowGames.forEach { game ->
                    GameCard(
                        title = game.title,
                        coverUrl = game.coverImage,
                        platform = game.platform,
                        rating = game.rating,
                        onClick = { onGameClick(game.id) }
                    )
                }
            }
        }
    }
}
