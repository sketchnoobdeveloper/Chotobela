package com.chotobela.feature.download.di

import com.chotobela.feature.download.DemoDownloadManager
import com.chotobela.feature.download.DownloadManager
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DownloadModule {

    /**
     * DEMO MODE binding. When Supabase credentials land, swap this for the
     * resumable-HTTP implementation — screens and view models stay unchanged.
     */
    @Binds
    @Singleton
    abstract fun bindDownloadManager(impl: DemoDownloadManager): DownloadManager
}
