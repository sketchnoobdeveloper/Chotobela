package com.chotobela.core.database

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): ChotobelaDatabase =
        Room.databaseBuilder(context, ChotobelaDatabase::class.java, ChotobelaDatabase.NAME)
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideLibraryDao(db: ChotobelaDatabase) = db.libraryDao()

    @Provides
    fun provideSaveStateDao(db: ChotobelaDatabase) = db.saveStateDao()
}
