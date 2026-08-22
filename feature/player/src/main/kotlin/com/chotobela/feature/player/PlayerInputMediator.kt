package com.chotobela.feature.player

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.chotobela.core.engine.EmulatorEngineApi

/**
 * CHIP-8 hex keypad mapping used by the touch overlay and gamepads.
 * Bit n of the engine button mask = CHIP-8 key n.
 */
object Chip8KeyMap {
    const val DPAD_UP = 0x2
    const val DPAD_DOWN = 0x8
    const val DPAD_LEFT = 0x4
    const val DPAD_RIGHT = 0x6
    const val BUTTON_A = 0x5
    const val BUTTON_B = 0x0

    /** All keys reachable from the overlay. */
    val overlayKeys = intArrayOf(DPAD_UP, DPAD_DOWN, DPAD_LEFT, DPAD_RIGHT, BUTTON_A, BUTTON_B)
}

/**
 * Merges multiple input sources (touch overlay, gamepad, keyboard) into the
 * single button bitmask consumed by the engine. Lock-free; any thread.
 */
class PlayerInputMediator(
    private val engine: EmulatorEngineApi,
    private val onFirstPress: () -> Unit = {}
) {
    @Volatile var hapticsEnabled: Boolean = true

    // per-source masks so releases from one source never clear another's hold
    @Volatile private var touchMask = 0
    @Volatile private var padMask = 0
    private val lock = Any()

    init {
        engine.setButtons(0)
    }

    fun setTouch(key: Int, pressed: Boolean) {
        synchronized(lock) {
            touchMask = if (pressed) touchMask or (1 shl key) else touchMask and (1 shl key).inv()
            push()
        }
        if (pressed) haptic()
    }

    fun setPad(key: Int, pressed: Boolean) {
        synchronized(lock) {
            padMask = if (pressed) padMask or (1 shl key) else padMask and (1 shl key).inv()
            push()
        }
        if (pressed && touchMask == 0) haptic()
    }

    fun reset() {
        synchronized(lock) {
            touchMask = 0
            padMask = 0
            push()
        }
    }

    private fun push() {
        engine.setButtons(touchMask or padMask)
    }

    private fun haptic() {
        if (!hapticsEnabled) return
        runCatching { vibrator?.vibrate(VibrationEffect.createOneShot(18, 120)) }
    }

    private var vibrator: Vibrator? = null

    fun attachVibrator(context: Context) {
        vibrator = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)
                    ?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
        }.getOrNull()
    }
}
