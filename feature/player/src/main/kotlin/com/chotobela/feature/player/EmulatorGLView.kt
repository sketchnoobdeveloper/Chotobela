package com.chotobela.feature.player

import android.content.Context
import android.opengl.GLSurfaceView
import com.chotobela.core.engine.EmulatorEngineApi
import com.chotobela.feature.player.render.GameShaderRenderer

/**
 * Fullscreen GL surface hosting the shader renderer.
 * GLSurfaceView owns the EGL context, render thread and lifecycle pauses.
 */
class EmulatorGLView(
    context: Context,
    engine: EmulatorEngineApi
) : GLSurfaceView(context) {

    val renderer = GameShaderRenderer(engine)

    init {
        setEGLContextClientVersion(3)
        setRenderer(renderer)
        renderMode = RENDERMODE_CONTINUOUSLY
        preserveEGLContextOnPause = true
    }
}
