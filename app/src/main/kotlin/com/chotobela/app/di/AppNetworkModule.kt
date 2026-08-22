package com.chotobela.app.di

import com.chotobela.core.network.StoreApi
import com.chotobela.core.network.StoreRepository
import com.chotobela.core.network.SupabaseClientProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Composition-root binding for the network layer.
 * Lives in :app so the network module stays annotation-free.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppNetworkModule {

    @Provides
    @Singleton
    fun provideSupabaseClientProvider(): SupabaseClientProvider = SupabaseClientProvider()

    @Provides
    @Singleton
    fun provideStoreRepository(provider: SupabaseClientProvider): StoreApi =
        StoreRepository(provider)
}
