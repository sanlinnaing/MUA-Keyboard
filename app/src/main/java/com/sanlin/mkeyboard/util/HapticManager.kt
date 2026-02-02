package com.sanlin.mkeyboard.util

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import com.sanlin.mkeyboard.config.KeyboardConfig

/**
 * Utility class for managing haptic feedback with adjustable strength.
 *
 * AOSP LatinIME uses HapticFeedbackConstants.KEYBOARD_TAP which is typically 3-5ms.
 * We use 10ms for custom vibration with amplitude control.
 */
object HapticManager {

    private const val TAG = "HapticManager"

    // Duration of vibration in milliseconds (AOSP uses ~3-5ms for KEYBOARD_TAP)
    private const val VIBRATION_DURATION_MS = 10L

    // Minimum amplitude (Android uses 1-255 range)
    private const val MIN_AMPLITUDE = 1

    // Maximum amplitude
    private const val MAX_AMPLITUDE = 255

    private var vibrator: Vibrator? = null
    private var isInitialized = false

    /**
     * Initialize the haptic system. Should be called when the keyboard service starts.
     */
    @JvmStatic
    fun initialize(context: Context) {
        if (isInitialized) return

        try {
            vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }

            isInitialized = true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize HapticManager", e)
        }
    }

    /**
     * Perform haptic feedback with the configured strength.
     * Strength is read from KeyboardConfig.
     */
    @JvmStatic
    fun performHapticFeedback(context: Context) {
        if (!KeyboardConfig.isHapticEnabled()) {
            return
        }

        if (!isInitialized) {
            initialize(context)
        }

        val vib = vibrator ?: return

        if (!vib.hasVibrator()) {
            return
        }

        val strength = KeyboardConfig.getHapticStrength()
        vibrate(vib, strength)
    }

    /**
     * Perform haptic feedback with a specific strength (0-100).
     */
    @JvmStatic
    fun performHapticFeedback(context: Context, strength: Int) {
        if (!isInitialized) {
            initialize(context)
        }

        val vib = vibrator ?: return

        if (!vib.hasVibrator()) {
            return
        }

        vibrate(vib, strength)
    }

    /**
     * Vibrate with the specified strength (0-100).
     *
     * For lower strengths (< 30), uses EFFECT_TICK which is similar to AOSP keyboard.
     * For higher strengths, uses custom amplitude control.
     */
    private fun vibrate(vibrator: Vibrator, strength: Int) {
        try {
            if (strength <= 0) return

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10+: Use predefined effects for light haptics
                if (strength < 30) {
                    // Use EFFECT_TICK for light feedback (AOSP-like)
                    val effect = VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
                    vibrator.vibrate(effect)
                    return
                } else if (strength < 60) {
                    // Use EFFECT_CLICK for medium feedback
                    val effect = VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
                    vibrator.vibrate(effect)
                    return
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // Convert 0-100 to Android's 1-255 amplitude range
                val amplitude = ((strength / 100.0) * (MAX_AMPLITUDE - MIN_AMPLITUDE) + MIN_AMPLITUDE).toInt()
                    .coerceIn(MIN_AMPLITUDE, MAX_AMPLITUDE)

                // Use VibrationEffect for API 26+ with custom amplitude
                val effect = VibrationEffect.createOneShot(VIBRATION_DURATION_MS, amplitude)
                vibrator.vibrate(effect)
            } else {
                // Fallback for older devices (no amplitude control)
                @Suppress("DEPRECATION")
                vibrator.vibrate(VIBRATION_DURATION_MS)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to vibrate", e)
        }
    }

    /**
     * Release resources. Should be called when the keyboard service is destroyed.
     */
    @JvmStatic
    fun release() {
        vibrator = null
        isInitialized = false
    }
}
