package com.chotobela.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chotobela.core.database.dao.LibraryDao
import com.chotobela.core.database.entity.GameEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Injectenum class LibrarySection(val label: String) {
    MY_GAMES("My Games"),
    RECENT("Recently Played"),
    FAVORITES("Favorites")
}

enum class SortOrder(val label: String) {
    TITLE("Title"), RECENT("Recent"), SIZE("Size")
}

data class LibraryUiState(
    val games: List<GameEntity> = emptyList(),
    val section: LibrarySection = LibrarySection.MY_GAMES,
    val query: String = "",
    val sort: SortOrder = SortOrder.TITLE,
    val isEmpty: Boolean = false
)

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val libraryDao: LibraryDao,
    private val romImporter: RomImporter
) : ViewModel() {

    private val section = MutableStateFlow(LibrarySection.MY_GAMES)
    private val query = MutableStateFlow("")
    private val sort = MutableStateFlow(SortOrder.TITLE)

    val uiState: StateFlow<LibraryUiState> =
        combine(section, query, sort) { s, q, o -> Triple(s, q, o) }
            .flatMapLatest { (s, q, o) ->
                val base = when (s) {
                    LibrarySection.MY_GAMES -> libraryDao.observeAll()
                    LibrarySection.RECENT -> libraryDao.observeRecentlyPlayed()
                    LibrarySection.FAVORITES -> libraryDao.observeFavorites()
                }
                base.map { games ->
                    var filtered = games
                    if (q.isNotBlank()) {
                        filtered = filtered.filter { it.title.contains(q, ignoreCase = true) }
                    }
                    filtered = when (o) {
                        SortOrder.TITLE -> filtered.sortedBy { it.title.lowercase() }
                        SortOrder.RECENT ->
                            filtered.sortedByDescending { it.lastPlayedAt ?: 0L }
                        SortOrder.SIZE -> filtered.sortedByDescending { it.sizeBytes }
                    }
                    LibraryUiState(
                        games = filtered,
                        section = s,
                        query = q,
                        sort = o,
                        isEmpty = filtered.isEmpty()
                    )
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LibraryUiState())

    private val _actions = MutableStateFlow<LibraryAction?>(null)
    val actions: StateFlow<LibraryAction?> = _actions.asStateFlow()

    private val _importMessage = MutableStateFlow<String?>(null)
    val importMessage: StateFlow<String?> = _importMessage.asStateFlow()

    fun importRom(uri: android.net.Uri) {
        viewModelScope.launch {
            when (val result = romImporter.import(uri)) {
                is com.chotobela.feature.library.ImportResult.Success ->
                    _importMessage.value = "Imported \"${result.gameTitle}\""
                is com.chotobela.feature.library.ImportResult.Rejected ->
                    _importMessage.value = result.reason
            }
        }
    }

    fun consumeImportMessage() { _importMessage.value = null }

    fun setSection(value: LibrarySection) { section.value = value }
    fun setQuery(value: String) { query.value = value }
    fun setSort(value: SortOrder) { sort.value = value }

    fun toggleFavorite(game: GameEntity) {
        viewModelScope.launch { libraryDao.setFavorite(game.id, !game.favorite) }
    }

    fun delete(game: GameEntity) {
        viewModelScope.launch {
            libraryDao.deleteById(game.id)
            java.io.File(game.romPath).delete()
        }
    }

    fun consumeAction() { _actions.value = null }
}

sealed interface LibraryAction
