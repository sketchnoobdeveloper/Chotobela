package com.chotobela.core.engine.di

import com.chotobela.core.engine.EmulatorEngineApi
import com.chotobela.core.engine.EngineLoop
import com.chotobela.core.engine.JniEmulatorEngine
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
