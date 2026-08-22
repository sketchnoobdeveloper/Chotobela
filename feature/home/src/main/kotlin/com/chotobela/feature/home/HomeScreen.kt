package com.chotobela.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.chotobela.core.database.entity.GameEntity
import com.chotobela.core.network.dto.GameDto
import com.chotobela.core.ui.components.GameCard
import com.chotobela.core.ui.components.SectionHeader

@Composable
fun HomeScreen(
    onGameClick: (String) -> Unit,
    onSeeAllLibrary: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val continuePlaying by viewModel.continuePlaying.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text(
                text = "CHOTOBELA",
                style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Text(
                text = "Relive your childhood gaming memories",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Spacer(Modifier.height(8.dp))
        }

        if (continuePlaying.isNotEmpty()) {
            item {
                SectionHeader("Continue Playing", onSeeAll = onSeeAllLibrary)
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(continuePlaying, key = { it.id }) { game ->
                        LibraryGameCard(game, onClick = { onGameClick(game.id) })
                    }
                }
            }
        }

        when {
            state.isLoadingStore -> item {
                androidx.compose.foundation.layout.Box(
                    Modifier.fillParentMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            state.error != null && state.featured.isEmpty() -> item {
                Text(
                    text = "Store unavailable: ${state.error}",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
            else -> {
                if (state.featured.isNotEmpty()) {
                    item {
                        SectionHeader("Featured")
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(state.featured, key = { it.id }) { game ->
                                StoreGameCard(game, onClick = { onGameClick(game.id) })
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
                                StoreGameCard(game, onClick = { onGameClick(game.id) })
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryGameCard(game: GameEntity, onClick: () -> Unit) {
    GameCard(
        title = game.title,
        coverUrl = game.coverUrl,
        platform = game.platform,
        rating = null,
        onClick = onClick
    )
}

@Composable
private fun StoreGameCard(game: GameDto, onClick: () -> Unit) {
    GameCard(
        title = game.title,
        coverUrl = game.coverImage,
        platform = game.platform,
        rating = game.rating,
        onClick = onClick
    )
}
