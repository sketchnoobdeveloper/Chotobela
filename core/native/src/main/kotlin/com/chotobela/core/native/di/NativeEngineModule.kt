package com.chotobela.core.native.di

import com.chotobela.core.native.EmulatorEngineApi
import com.chotobela.core.native.EngineLoop
import com.chotobela.core.native.JniEmulatorEngine
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class NativeEngineModule {

    @Binds
    @Singleton
    abstract fun bindEngine(impl: JniEmulatorEngine): EmulatorEngineApi

    companion object {
        @Provides
        @Singleton
        fun provideEngineLoop(engine: JniEmulatorEngine): EngineLoop = EngineLoop(engine)
    }
}
