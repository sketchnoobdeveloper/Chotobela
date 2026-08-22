package com.chotobela.feature.player.render

import android.opengl.GLES30
import android.opengl.GLSurfaceView
import com.chotobela.core.engine.EmulatorEngineApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * GLES3 render backend.
 *
 * Uploads the engine's ARGB framebuffer as a texture each vsync and draws it
 * through a post-processing shader pipeline (shader-ready per spec).
 * The engine keeps stepping on its own thread; this thread only samples.
 */
class GameShaderRenderer(
    private val engine: EmulatorEngineApi
) : GLSurfaceView.Renderer {

    companion object {
        const val ASPECT_AUTO = 0
        const val ASPECT_4_3 = 1
        const val ASPECT_16_9 = 2
        const val ASPECT_STRETCH = 3

        private const val VERTEX_SRC = """
            attribute vec2 a_pos;
            attribute vec2 a_uv;
            varying vec2 v_uv;
            void main() {
                v_uv = a_uv;
                gl_Position = vec4(a_pos, 0.0, 1.0);
            }
        """

        private const val FRAGMENT_SRC = """
            precision mediump float;
            varying vec2 v_uv;
            uniform sampler2D u_tex;
            uniform int u_preset;      // 0 none, 1 crt, 2 scanlines, 3 lcd
            uniform vec2 u_res;        // source resolution
            uniform float u_time;

            float rand(vec2 co) {
                return fract(sin(dot(co, vec2(12.9898, 78.233))) * 43758.5453);
            }

            void main() {
                vec2 uv = v_uv;

                if (u_preset == 1) {
                    // CRT: mild barrel curvature
                    vec2 cc = uv - 0.5;
                    float dist = dot(cc, cc);
                    uv = uv + cc * dist * 0.18;
                    if (uv.x < 0.0 || uv.x > 1.0 || uv.y < 0.0 || uv.y > 1.0) {
                        gl_FragColor = vec4(0.0, 0.0, 0.0, 1.0);
                        return;
                    }
                }

                vec3 color = texture2D(u_tex, uv).rgb;

                if (u_preset == 1 || u_preset == 2) {
                    // Scanlines keyed to source rows
                    float srcY = uv.y * u_res.y;
                    float line = abs(fract(srcY) - 0.5);
                    float shade = smoothstep(0.0, 0.35, line) * 0.35 + 0.65;
                    color *= shade;
                }
                if (u_preset == 1) {
                    // Phosphor glow approximation + vignette + subtle flicker
                    vec3 bloom = texture2D(u_tex, uv, 1.0).rgb;
                    color += bloom * 0.22;
                    vec2 cc = uv - 0.5;
                    color *= 1.0 - dot(cc, cc) * 0.55;
                    color *= 0.97 + 0.03 * sin(u_time * 60.0);
                }
                if (u_preset == 3) {
                    // LCD grid on both axes
                    vec2 cell = fract(uv * u_res);
                    float gridX = smoothstep(0.0, 0.08, cell.x);
                    float gridY = smoothstep(0.0, 0.08, cell.y);
                    color *= mix(0.75, 1.0, gridX * gridY);
                }

                gl_FragColor = vec4(color, 1.0);
            }
        """
    }

    @Volatile var shaderPresetId: Int = 0      // ShaderPreset ordinal
    @Volatile var aspectMode: Int = ASPECT_AUTO
    @Volatile var pixelPerfect: Boolean = false
    @Volatile var vsyncEnabled: Boolean = true

    private var program = 0
    private var textureId = 0
    private var posLoc = 0
    private var uvLoc = 0
    private var presetLoc = 0
    private var resLoc = 0
    private var timeLoc = 0

    private var texW = 0
    private var texH = 0
    private var surfaceW = 1
    private var surfaceH = 1

    private val quadData: FloatBuffer = floatBufferOf(
        // x,y,u,v
        -1f, -1f, 0f, 1f,
        1f, -1f, 1f, 1f,
        -1f, 1f, 0f, 0f,
        1f, 1f, 1f, 0f
    )

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        program = buildProgram()
        posLoc = GLES30.glGetAttribLocation(program, "a_pos")
        uvLoc = GLES30.glGetAttribLocation(program, "a_uv")
        presetLoc = GLES30.glGetUniformLocation(program, "u_preset")
        resLoc = GLES30.glGetUniformLocation(program, "u_res")
        timeLoc = GLES30.glGetUniformLocation(program, "u_time")

        val ids = IntArray(1)
        GLES30.glGenTextures(1, ids, 0)
        textureId = ids[0]
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textureId)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)

        GLES30.glClearColor(0.055f, 0.067f, 0.086f, 1f)
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        surfaceW = width.coerceAtLeast(1)
        surfaceH = height.coerceAtLeast(1)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
        if (program == 0) return

        val (vw, vh) = engine.videoSize()
        if (vw <= 0 || vh <= 0) return
        uploadFrame(vw, vh)

        GLES30.glUseProgram(program)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textureId)
        applyFilters(vw, vh)

        GLES30.glUniform1i(presetLoc, shaderPresetId)
        GLES30.glUniform2f(resLoc, vw.toFloat(), vh.toFloat())
        GLES30.glUniform1f(timeLoc, (System.nanoTime() % 3_600_000_000_000L) / 1e9f)

        val (outW, outH) = computeOutputSize(vw, vh)
        drawQuad(outW, outH)
    }

    /**
     * Clip-space output size for the source frame.
     * - STRETCH fills the surface.
     * - Otherwise letterboxes to the selected aspect.
     * - pixelPerfect snaps to the largest whole-pixel multiple of the source
     *   that still fits after letterboxing.
     */
    private fun computeOutputSize(vw: Int, vh: Int): Pair<Float, Float> {
        if (aspectMode == ASPECT_STRETCH) return 1f to 1f

        val srcAspect = vw.toFloat() / vh.toFloat()
        val targetAspect = when (aspectMode) {
            ASPECT_4_3 -> 4f / 3f
            ASPECT_16_9 -> 16f / 9f
            else -> srcAspect
        }
        val surfaceAspect = surfaceW.toFloat() / surfaceH

        var pxW: Float
        var pxH: Float
        if (targetAspect >= surfaceAspect) {
            pxW = surfaceW.toFloat()
            pxH = pxW / targetAspect
        } else {
            pxH = surfaceH.toFloat()
            pxW = pxH * targetAspect
        }

        if (pixelPerfect) {
            val mult = maxOf(1, minOf((pxW / vw).toInt(), (pxH / vh).toInt()))
            pxW = vw.toFloat() * mult
            pxH = vh.toFloat() * mult
            if (pxW > surfaceW || pxH > surfaceH) {
                // safety: never exceed surface (aspect mismatch edge cases)
                val shrink = minOf(surfaceW / pxW, surfaceH / pxH)
                pxW *= shrink
                pxH *= shrink
            }
        }
        return (pxW / surfaceW) to (pxH / surfaceH)
    }

    private fun applyFilters(vw: Int, vh: Int) {
        val filter = if (pixelPerfect) GLES30.GL_NEAREST else GLES30.GL_LINEAR
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, filter)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, filter)
    }

    private fun uploadFrame(vw: Int, vh: Int) {
        if (vw != texW || vh != texH) {
            texW = vw
            texH = vh
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textureId)
            GLES30.glTexImage2D(
                GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA, vw, vh, 0,
                GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, null
            )
        }
        val buffer: ByteBuffer = engine.framebuffer()
        if (!buffer.isDirect || buffer.capacity() < vw * vh * 4) return
        buffer.position(0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textureId)
        GLES30.glTexSubImage2D(
            GLES30.GL_TEXTURE_2D, 0, 0, 0, vw, vh,
            GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, buffer
        )
    }

    private fun drawQuad(width: Float, height: Float) {
        quadData.rewind()
        GLES30.glVertexAttribPointer(posLoc, 2, GLES30.GL_FLOAT, false, 16, quadData)
        quadData.position(2)
        GLES30.glVertexAttribPointer(uvLoc, 2, GLES30.GL_FLOAT, false, 16, quadData)
        GLES30.glEnableVertexAttribArray(posLoc)
        GLES30.glEnableVertexAttribArray(uvLoc)
        // scale via viewport trick: draw into sub-viewport region
        val pxW = (width * surfaceW / 2f).toInt().coerceAtLeast(1)
        val pxH = (height * surfaceH / 2f).toInt().coerceAtLeast(1)
        val x0 = (surfaceW - pxW) / 2
        val y0 = (surfaceH - pxH) / 2
        GLES30.glViewport(x0, y0, pxW, pxH)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
        GLES30.glDisableVertexAttribArray(posLoc)
        GLES30.glDisableVertexAttribArray(uvLoc)
        GLES30.glViewport(0, 0, surfaceW, surfaceH)
    }

    private fun buildProgram(): Int {
        val vs = compile(GLES30.GL_VERTEX_SHADER, VERTEX_SRC.trimIndent())
        val fs = compile(GLES30.GL_FRAGMENT_SHADER, FRAGMENT_SRC.trimIndent())
        val p = GLES30.glCreateProgram()
        GLES30.glAttachShader(p, vs)
        GLES30.glAttachShader(p, fs)
        GLES30.glLinkProgram(p)
        val status = IntArray(1)
        GLES30.glGetProgramiv(p, GLES30.GL_LINK_STATUS, status, 0)
        require(status[0] == GLES30.GL_TRUE) { "Shader link failed" }
        return p
    }

    private fun compile(type: Int, src: String): Int {
        val shader = GLES30.glCreateShader(type)
        GLES30.glShaderSource(shader, src)
        GLES30.glCompileShader(shader)
        val status = IntArray(1)
        GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, status, 0)
        require(status[0] == GLES30.GL_TRUE) {
            "Shader compile failed: ${GLES30.glGetShaderInfoLog(shader)}"
        }
        return shader
    }

    private fun floatBufferOf(vararg values: Float): FloatBuffer =
        ByteBuffer.allocateDirect(values.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(values)
                position(0)
            }
}
