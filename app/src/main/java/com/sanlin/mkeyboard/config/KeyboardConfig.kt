package com.sanlin.mkeyboard.config

/**
 * Runtime configuration singleton for the MUA Keyboard.
 * Stores user preferences that affect keyboard behavior.
 */
object KeyboardConfig {
    private var soundOn: Boolean = false
    private var primeBookOn: Boolean = false
    private var currentTheme: Int = 1
    private var showHintLabel: Boolean = true
    private var doubleTap: Boolean = false
    private var proximityEnabled: Boolean = true
    private var proximityThresholdDp: Float = 20f
    private var hapticEnabled: Boolean = true
    private var hapticStrength: Int = 25  // 0-100 range (low = EFFECT_TICK)

    @JvmStatic
    fun isSoundOn(): Boolean = soundOn

    @JvmStatic
    fun setSoundOn(value: Boolean) {
        soundOn = value
    }

    @JvmStatic
    fun isPrimeBookOn(): Boolean = primeBookOn

    @JvmStatic
    fun setPrimeBookOn(value: Boolean) {
        primeBookOn = value
    }

    @JvmStatic
    fun getCurrentTheme(): Int = currentTheme

    @JvmStatic
    fun setCurrentTheme(value: Int) {
        currentTheme = value
    }

    @JvmStatic
    fun isShowHintLabel(): Boolean = showHintLabel

    @JvmStatic
    fun setShowHintLabel(value: Boolean) {
        showHintLabel = value
    }

    @JvmStatic
    fun isDoubleTap(): Boolean = doubleTap

    @JvmStatic
    fun setDoubleTap(value: Boolean) {
        doubleTap = value
    }

    @JvmStatic
    fun isProximityEnabled(): Boolean = proximityEnabled

    @JvmStatic
    fun setProximityEnabled(value: Boolean) {
        proximityEnabled = value
    }

    @JvmStatic
    fun getProximityThresholdDp(): Float = proximityThresholdDp

    @JvmStatic
    fun setProximityThresholdDp(value: Float) {
        proximityThresholdDp = value
    }

    @JvmStatic
    fun isHapticEnabled(): Boolean = hapticEnabled

    @JvmStatic
    fun setHapticEnabled(value: Boolean) {
        hapticEnabled = value
    }

    @JvmStatic
    fun getHapticStrength(): Int = hapticStrength

    @JvmStatic
    fun setHapticStrength(value: Int) {
        // Clamp to 0-100 range
        hapticStrength = value.coerceIn(0, 100)
    }
}
