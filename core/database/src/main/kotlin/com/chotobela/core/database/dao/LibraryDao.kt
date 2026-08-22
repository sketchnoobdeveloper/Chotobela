package com.chotobela.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.chotobela.core.database.entity.GameEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LibraryDao {

    @Query("SELECT * FROM games ORDER BY title ASC")
    fun observeAll(): Flow<List<GameEntity>>

    @Query("SELECT * FROM games WHERE favorite = 1 ORDER BY lastPlayedAt DESC")
    fun observeFavorites(): Flow<List<GameEntity>>

    @Query("SELECT * FROM games WHERE lastPlayedAt IS NOT NULL ORDER BY lastPlayedAt DESC LIMIT 10")
    fun observeRecentlyPlayed(): Flow<List<GameEntity>>

    @Query(
        "SELECT * FROM games WHERE lastPlayedAt IS NOT NULL " +
            "ORDER BY (lastPlayedAt - playtimeSeconds * 1000) DESC LIMIT 5"
    )
    fun observeContinuePlaying(): Flow<List<GameEntity>>

    @Query("SELECT * FROM games WHERE id = :id")
    suspend fun getById(id: String): GameEntity?

    @Query("SELECT * FROM games WHERE id = :id")
    fun observeById(id: String): Flow<GameEntity?>

    @Query(
        "SELECT * FROM games WHERE title LIKE '%' || :query || '%' " +
            "ORDER BY title ASC"
    )
    fun search(query: String): Flow<List<GameEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(game: GameEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun upsertAll(games: List<GameEntity>)

    @Update
    suspend fun update(game: GameEntity)

    @Query("UPDATE games SET favorite = :favorite WHERE id = :id")
    suspend fun setFavorite(id: String, favorite: Boolean)

    @Query(
        "UPDATE games SET lastPlayedAt = :playedAt, " +
            "playtimeSeconds = playtimeSeconds + :sessionSeconds WHERE id = :id"
    )
    suspend fun recordPlaySession(id: String, playedAt: Long, sessionSeconds: Long)

    @Query("DELETE FROM games WHERE id = :id")
    suspend fun deleteById(id: String)
}
