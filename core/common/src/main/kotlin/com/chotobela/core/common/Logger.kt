package com.chotobela.core.common

import javax.inject.Inject
import javax.inject.Singleton

interface Logger {
    fun d(message: String, vararg args: Any?)
    fun i(message: String, vararg args: Any?)
    fun w(throwable: Throwable? = null, message: String, vararg args: Any?)
    fun e(throwable: Throwable? = null, message: String, vararg args: Any?)
}

@Singleton
class TimberLogger @Inject constructor() : Logger {
    override fun d(message: String, vararg args: Any?) = timber.log.Timber.d(message, *args)
    override fun i(message: String, vararg args: Any?) = timber.log.Timber.i(message, *args)
    override fun w(throwable: Throwable?, message: String, vararg args: Any?) =
        timber.log.Timber.w(throwable, message, *args)
    override fun e(throwable: Throwable?, message: String, vararg args: Any?) =
        timber.log.Timber.e(throwable, message, *args)
}
