package com.sanlin.mkeyboard.util

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import android.media.ToneGenerator
import android.os.Build
import android.util.Log
import android.view.HapticFeedbackConstants
import android.view.View
import com.sanlin.mkeyboard.keyboard.model.Key

/**
 * Utility class for managing keyboard sounds using SoundPool.
 * Falls back to ToneGenerator if sound resources are not available.
 */
object SoundManager {

    private const val TAG = "SoundManager"

    // SoundPool for playing custom sounds
    private var soundPool: SoundPool? = null
    private var clickSoundId: Int = 0
    private var spaceSoundId: Int = 0
    private var deleteSoundId: Int = 0
    private var returnSoundId: Int = 0
    private var isInitialized = false
    private var useToneGenerator = false

    // ToneGenerator as fallback
    private var toneGenerator: ToneGenerator? = null

    // Volume settings
    private const val SOUND_VOLUME = 0.5f
    private const val TONE_DURATION_MS = 30

    /**
     * Initialize the sound system. Should be called when the keyboard service starts.
     */
    @JvmStatic
    fun initialize(context: Context) {
        if (isInitialized) return

        try {
            // Create SoundPool with proper audio attributes
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            soundPool = SoundPool.Builder()
                .setMaxStreams(4)
                .setAudioAttributes(audioAttributes)
                .build()

            // Try to load sound resources by name (allows optional sound files)
            val hasClickSound = loadSoundByName(context, "key_click") { clickSoundId = it }
            val hasSpaceSound = loadSoundByName(context, "key_space") { spaceSoundId = it }
            val hasDeleteSound = loadSoundByName(context, "key_delete") { deleteSoundId = it }
            val hasReturnSound = loadSoundByName(context, "key_return") { returnSoundId = it }

            // If no custom sounds, use click for all or fall back to ToneGenerator
            if (!hasClickSound) {
                Log.d(TAG, "No custom sounds found, using ToneGenerator")
                useToneGenerator = true
                initToneGenerator()
            } else {
                // Use click sound for missing sounds
                if (!hasSpaceSound) spaceSoundId = clickSoundId
                if (!hasDeleteSound) deleteSoundId = clickSoundId
                if (!hasReturnSound) returnSoundId = clickSoundId
                useToneGenerator = false
            }

            isInitialized = true
            Log.d(TAG, "SoundManager initialized, useToneGenerator=$useToneGenerator")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize SoundPool, using ToneGenerator", e)
            useToneGenerator = true
            initToneGenerator()
            isInitialized = true
        }
    }

    /**
     * Try to load a sound resource by name if it exists.
     * This allows sound files to be optional.
     */
    private fun loadSoundByName(context: Context, soundName: String, onLoaded: (Int) -> Unit): Boolean {
        return try {
            // Look up resource ID by name at runtime
            val resId = context.resources.getIdentifier(soundName, "raw", context.packageName)
            if (resId == 0) {
                Log.d(TAG, "Sound resource not found: $soundName")
                return false
            }

            val soundId = soundPool?.load(context, resId, 1) ?: 0
            if (soundId > 0) {
                onLoaded(soundId)
                Log.d(TAG, "Loaded sound: $soundName with id $soundId")
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.d(TAG, "Failed to load sound: $soundName", e)
            false
        }
    }

    /**
     * Initialize ToneGenerator as fallback.
     */
    private fun initToneGenerator() {
        try {
            toneGenerator = ToneGenerator(AudioManager.STREAM_SYSTEM, 50)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize ToneGenerator", e)
        }
    }

    /**
     * Play click sound for a key press.
     *
     * @param context The context (used for initialization if needed).
     * @param keyCode The key code that was pressed.
     */
    @JvmStatic
    fun playClick(context: Context, keyCode: Int) {
        if (!isInitialized) {
            initialize(context)
        }

        if (useToneGenerator) {
            playTone(keyCode)
        } else {
            playSoundPoolClick(keyCode)
        }
    }

    /**
     * Play sound using SoundPool.
     */
    private fun playSoundPoolClick(keyCode: Int) {
        val soundId = when (keyCode) {
            32 -> spaceSoundId        // Spacebar
            Key.KEYCODE_DONE, 10 -> returnSoundId  // Enter/Return
            Key.KEYCODE_DELETE -> deleteSoundId    // Delete
            else -> clickSoundId      // Regular keys
        }

        if (soundId > 0) {
            soundPool?.play(soundId, SOUND_VOLUME, SOUND_VOLUME, 1, 0, 1.0f)
        }
    }

    /**
     * Play sound using ToneGenerator (fallback).
     */
    private fun playTone(keyCode: Int) {
        try {
            val toneType = when (keyCode) {
                32 -> ToneGenerator.TONE_PROP_ACK           // Spacebar - acknowledgment tone
                Key.KEYCODE_DONE, 10 -> ToneGenerator.TONE_PROP_BEEP2  // Enter - different beep
                Key.KEYCODE_DELETE -> ToneGenerator.TONE_PROP_NACK     // Delete - negative ack
                else -> ToneGenerator.TONE_CDMA_PIP         // Regular keys - short pip
            }
            toneGenerator?.startTone(toneType, TONE_DURATION_MS)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to play tone", e)
        }
    }

    /**
     * Release resources. Should be called when the keyboard service is destroyed.
     */
    @JvmStatic
    fun release() {
        try {
            soundPool?.release()
            soundPool = null
            toneGenerator?.release()
            toneGenerator = null
            isInitialized = false
            clickSoundId = 0
            spaceSoundId = 0
            deleteSoundId = 0
            returnSoundId = 0
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing SoundManager", e)
        }
    }

    /**
     * Perform haptic feedback on a view.
     *
     * @param view The view to perform haptic feedback on.
     */
    @JvmStatic
    fun performHapticFeedback(view: View) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        } else {
            @Suppress("DEPRECATION")
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        }
    }
}
