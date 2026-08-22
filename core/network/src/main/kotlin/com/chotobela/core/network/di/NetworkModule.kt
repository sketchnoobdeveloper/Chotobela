package com.chotobela.core.network.di

import com.chotobela.core.network.StoreApi
import com.chotobela.core.network.StoreRepository
import com.chotobela.core.network.SupabaseClientProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideSupabaseClientProvider(): SupabaseClientProvider = SupabaseClientProvider()

    @Provides
    @Singleton
    fun provideStoreRepository(provider: SupabaseClientProvider): StoreApi =
        StoreRepository(provider)
}
