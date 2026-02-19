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
    private var proximityThresholdDp: Float = 10f
    private var hapticEnabled: Boolean = true
    private var hapticStrength: Int = 25  // 0-100 range (low = EFFECT_TICK)
    private var userHandedness: String = "right"  // "right", "left"
    private var flickCompactMode: Boolean = false
    private var flickCompactSize: Int = 85  // 50-100 percent of screen width
    private var splitKeyboardMode: String = "off"  // "off", "on", "auto" (auto = landscape only)
    private var splitGapPercent: Int = 15  // 10-30 percent of screen width

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

    @JvmStatic
    fun getUserHandedness(): String = userHandedness

    @JvmStatic
    fun setUserHandedness(value: String) {
        userHandedness = value
    }

    @JvmStatic
    fun isFlickCompactMode(): Boolean = flickCompactMode

    @JvmStatic
    fun setFlickCompactMode(value: Boolean) {
        flickCompactMode = value
    }

    /** Returns "full", "left", or "right" for backward compat with FlickKeyboard/FlickKeyboardView */
    @JvmStatic
    fun getEffectiveFlickHandMode(): String {
        return if (!flickCompactMode) "full" else userHandedness
    }

    @JvmStatic
    fun getFlickCompactSize(): Int = flickCompactSize

    @JvmStatic
    fun setFlickCompactSize(value: Int) {
        flickCompactSize = value.coerceIn(50, 100)
    }

    @JvmStatic
    fun getSplitKeyboardMode(): String = splitKeyboardMode

    @JvmStatic
    fun setSplitKeyboardMode(value: String) {
        splitKeyboardMode = value
    }

    /**
     * Check if split keyboard is effectively enabled for the given orientation.
     * @param isLandscape true if current orientation is landscape
     */
    @JvmStatic
    fun isSplitKeyboardEnabled(isLandscape: Boolean = false): Boolean {
        return when (splitKeyboardMode) {
            "on" -> true
            "auto" -> isLandscape
            else -> false
        }
    }

    @JvmStatic
    fun getSplitGapPercent(): Int = splitGapPercent

    @JvmStatic
    fun setSplitGapPercent(value: Int) {
        splitGapPercent = value.coerceIn(10, 30)
    }
}
