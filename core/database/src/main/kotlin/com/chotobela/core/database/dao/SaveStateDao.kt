package com.chotobela.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.chotobela.core.database.entity.SaveStateEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SaveStateDao {

    @Query("SELECT * FROM save_states WHERE gameId = :gameId ORDER BY slot ASC")
    fun observeForGame(gameId: String): Flow<List<SaveStateEntity>>

    @Query("SELECT * FROM save_states WHERE gameId = :gameId AND slot = :slot")
    suspend fun get(gameId: String, slot: Int): SaveStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(state: SaveStateEntity)

    @Query("DELETE FROM save_states WHERE gameId = :gameId AND slot = :slot")
    suspend fun delete(gameId: String, slot: Int)
}
