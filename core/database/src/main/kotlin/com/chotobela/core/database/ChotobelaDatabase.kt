package com.chotobela.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.chotobela.core.database.dao.LibraryDao
import com.chotobela.core.database.dao.SaveStateDao
import com.chotobela.core.database.entity.GameEntity
import com.chotobela.core.database.entity.SaveStateEntity

@Database(
    entities = [GameEntity::class, SaveStateEntity::class],
    version = 1,
    exportSchema = true
)
abstract class ChotobelaDatabase : RoomDatabase() {
    abstract fun libraryDao(): LibraryDao
    abstract fun saveStateDao(): SaveStateDao

    companion object {
        const val NAME = "chotobela.db"
    }
}
