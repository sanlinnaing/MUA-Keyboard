package com.sanlin.mkeyboard.service

import android.inputmethodservice.InputMethodService
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import android.view.inputmethod.InputMethodSubtype
import android.widget.FrameLayout
import android.graphics.Color
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.preference.PreferenceManager
import com.sanlin.mkeyboard.R
import com.sanlin.mkeyboard.config.KeyboardConfig
import com.sanlin.mkeyboard.input.*
import com.sanlin.mkeyboard.keyboard.model.Key
import com.sanlin.mkeyboard.keyboard.model.Keyboard
import com.sanlin.mkeyboard.unicode.MyanmarUnicode
import com.sanlin.mkeyboard.util.DeleteHandler
import com.sanlin.mkeyboard.util.HapticManager
import com.sanlin.mkeyboard.util.SoundManager
import com.sanlin.mkeyboard.emoji.EmojiView
import com.sanlin.mkeyboard.emoji.RecentEmojiManager
import com.sanlin.mkeyboard.view.MuaKeyboardView
import com.sanlin.mkeyboard.view.OnKeyboardActionListener

/**
 * Main Input Method Service for MUA Keyboard.
 * This service handles all keyboard interactions using the new non-deprecated components.
 */
class MuaKeyboardService : InputMethodService(), OnKeyboardActionListener {

    private var kv: MuaKeyboardView? = null
    private var emojiView: EmojiView? = null
    private var keyboardContainer: FrameLayout? = null
    private var inputMethodManager: InputMethodManager? = null
    private var caps = false
    private var isEmojiMode = false

    // Keyboards
    private lateinit var enKeyboard: Keyboard
    private lateinit var symKeyboard: Keyboard
    private lateinit var symShiftedKeyboard: Keyboard
    private var currentKeyboard: Keyboard? = null

    // Input handlers
    private val inputHandlers = mutableMapOf<Int, InputHandler>()
    private var currentInputHandler: InputHandler = EnglishInputHandler()

    // State
    private var shifted = false
    private var symbol = false
    private var wordSeparators: String = ""
    private var shanConsonants: String = ""

    override fun onCreate() {
        super.onCreate()
        wordSeparators = resources.getString(R.string.word_separators)
        shanConsonants = resources.getString(R.string.shan_consonants)
        inputMethodManager = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        initializeInputHandlers()

        // Initialize sound and haptic systems
        SoundManager.initialize(this)
        HapticManager.initialize(this)

        // Initialize recent emoji manager
        RecentEmojiManager.initialize(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        // Release sound and haptic resources
        SoundManager.release()
        HapticManager.release()
    }

    private fun initializeInputHandlers() {
        inputHandlers[1] = EnglishInputHandler()
        inputHandlers[2] = BamarInputHandler(wordSeparators)
        inputHandlers[3] = ShanInputHandler(shanConsonants)
        inputHandlers[4] = MonInputHandler(wordSeparators)
        inputHandlers[5] = EnglishInputHandler() // SG Karen - pass-through
        inputHandlers[6] = EnglishInputHandler() // WP Karen - pass-through
        inputHandlers[7] = KarenInputHandler()
    }

    override fun onInitializeInterface() {
        val sharedPref = PreferenceManager.getDefaultSharedPreferences(this)
        KeyboardConfig.setDoubleTap(sharedPref.getBoolean("double_tap", false))

        enKeyboard = Keyboard(this, R.xml.en_qwerty)
        symKeyboard = Keyboard(this, R.xml.en_symbol)
        symShiftedKeyboard = Keyboard(this, R.xml.en_shift_symbol)

        shifted = false
        symbol = false
        currentKeyboard = enKeyboard
        currentKeyboard = getKeyboard(getLocaleId())
        currentInputHandler = inputHandlers[getLocaleId()] ?: EnglishInputHandler()
    }

    override fun onCreateInputView(): View {
        val sharedPref = PreferenceManager.getDefaultSharedPreferences(this)

        KeyboardConfig.setSoundOn(sharedPref.getBoolean("play_sound", false))
        KeyboardConfig.setPrimeBookOn(sharedPref.getBoolean("prime_book_typing_on_off", true))
        KeyboardConfig.setCurrentTheme(sharedPref.getString("choose_theme", "1")?.toIntOrNull() ?: 1)
        KeyboardConfig.setShowHintLabel(sharedPref.getBoolean("hint_keylabel", true))
        KeyboardConfig.setDoubleTap(sharedPref.getBoolean("double_tap", false))
        KeyboardConfig.setProximityEnabled(sharedPref.getBoolean("proximity_correction", true))
        KeyboardConfig.setHapticEnabled(sharedPref.getBoolean("haptic_feedback", true))
        KeyboardConfig.setHapticStrength(sharedPref.getInt("haptic_strength", 25))

        kv = when (KeyboardConfig.getCurrentTheme()) {
            6 -> layoutInflater.inflate(R.layout.light_keyboard, null) as? MuaKeyboardView  // Light
            5 -> layoutInflater.inflate(R.layout.gold_keyboard, null) as? MuaKeyboardView   // Golden Yellow
            4 -> layoutInflater.inflate(R.layout.blue_gray_keyboard, null) as? MuaKeyboardView  // Blue Gray
            3 -> layoutInflater.inflate(R.layout.flat_green_keyboard, null) as? MuaKeyboardView  // Green
            2 -> layoutInflater.inflate(R.layout.flat_black_keyboard, null) as? MuaKeyboardView  // Dark
            1 -> {
                // Default theme follows system dark/light mode
                if (isSystemInDarkMode()) {
                    layoutInflater.inflate(R.layout.default_keyboard, null) as? MuaKeyboardView
                } else {
                    layoutInflater.inflate(R.layout.light_keyboard, null) as? MuaKeyboardView
                }
            }
            else -> layoutInflater.inflate(R.layout.default_keyboard, null) as? MuaKeyboardView
        }

        kv?.keyboard = currentKeyboard
        kv?.setOnKeyboardActionListener(this)

        // Get background color based on theme
        val backgroundColor = getThemeBackgroundColor(KeyboardConfig.getCurrentTheme())

        // Calculate top padding (same as vertical gap between keys: 0.5% of screen width)
        val displayWidth = resources.displayMetrics.widthPixels
        val topPadding = (displayWidth * 0.005f).toInt().coerceAtLeast(4)

        // Wrap keyboard in container with bottom padding for system navigation bar
        val container = FrameLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            // Set background color to match keyboard
            setBackgroundColor(backgroundColor)
        }

        kv?.let { keyboardView ->
            // Set explicit LayoutParams since inflate with null parent ignores XML layout params
            val layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
            // Add top margin for gap at top of keyboard
            layoutParams.topMargin = topPadding
            container.addView(keyboardView, layoutParams)
        }

        // Create emoji view
        emojiView = EmojiView(this).apply {
            val layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
            layoutParams.topMargin = topPadding
            visibility = View.GONE  // Initially hidden

            setOnEmojiClickListener(object : EmojiView.OnEmojiClickListener {
                override fun onEmojiClick(emoji: String) {
                    currentInputConnection?.commitText(emoji, 1)
                    if (KeyboardConfig.isSoundOn()) {
                        SoundManager.playClick(this@MuaKeyboardService, 0)
                    }
                    if (KeyboardConfig.isHapticEnabled()) {
                        HapticManager.performHapticFeedback(this@MuaKeyboardService)
                    }
                }
            })

            setOnBackClickListener {
                hideEmojiView()
            }

            setOnDeleteClickListener {
                currentInputConnection?.let { ic ->
                    DeleteHandler.deleteChar(ic)
                }
                if (KeyboardConfig.isSoundOn()) {
                    SoundManager.playClick(this@MuaKeyboardService, Key.KEYCODE_DELETE)
                }
                if (KeyboardConfig.isHapticEnabled()) {
                    HapticManager.performHapticFeedback(this@MuaKeyboardService)
                }
            }

            container.addView(this, layoutParams)
        }

        keyboardContainer = container
        isEmojiMode = false

        // Use WindowInsets API to handle navigation bar across all devices
        // This works for Pixel, Samsung, OnePlus, etc. regardless of navigation mode
        ViewCompat.setOnApplyWindowInsetsListener(container) { view, windowInsets ->
            // Get system bar insets (includes navigation bar)
            val systemBarsInsets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            // Get IME insets for proper keyboard positioning
            val imeInsets = windowInsets.getInsets(WindowInsetsCompat.Type.ime())

            // Use the larger of system bars or IME bottom inset
            // This ensures keyboard doesn't overlap with navigation bar
            val bottomInset = maxOf(systemBarsInsets.bottom, imeInsets.bottom)

            // Apply padding to avoid navigation bar overlap
            view.setPadding(
                systemBarsInsets.left,
                0,
                systemBarsInsets.right,
                bottomInset
            )

            // Return the insets so child views can also handle them if needed
            windowInsets
        }

        // Fallback: Apply initial padding using legacy method for devices where
        // WindowInsets listener might not fire immediately
        val fallbackNavBarHeight = getNavigationBarHeightFallback()
        container.setPadding(0, 0, 0, fallbackNavBarHeight)

        return container
    }

    /**
     * Show the emoji view and hide the keyboard.
     */
    private fun showEmojiView() {
        isEmojiMode = true
        kv?.visibility = View.GONE
        emojiView?.visibility = View.VISIBLE
        // Set back button label based on current language
        emojiView?.setBackButtonLabel(getBackButtonLabel())
    }

    /**
     * Hide the emoji view and show the keyboard.
     */
    private fun hideEmojiView() {
        isEmojiMode = false
        emojiView?.visibility = View.GONE
        kv?.visibility = View.VISIBLE
    }

    /**
     * Get the back button label based on current keyboard language.
     */
    private fun getBackButtonLabel(): String {
        return when (getLocaleId()) {
            2 -> "ကခဂ"      // Burmese
            3 -> "ၵၶင"       // Shan
            4 -> "ကခဂ"      // Mon (uses Burmese script)
            5 -> "ကခဂ"      // SG Karen
            6 -> "ကခဂ"      // WP Karen
            7 -> "ကခဂ"      // EP Karen
            else -> "ABC"   // English
        }
    }

    /**
     * Check if system is in dark mode.
     */
    private fun isSystemInDarkMode(): Boolean {
        val nightModeFlags = resources.configuration.uiMode and
                android.content.res.Configuration.UI_MODE_NIGHT_MASK
        return nightModeFlags == android.content.res.Configuration.UI_MODE_NIGHT_YES
    }

    /**
     * Get background color for the current theme.
     */
    private fun getThemeBackgroundColor(theme: Int): Int {
        return when (theme) {
            6 -> Color.parseColor("#E8E8EE")  // Light
            5 -> Color.parseColor("#2B2518")  // Golden Yellow
            4 -> Color.parseColor("#1B1D2B")  // Blue Gray
            3 -> Color.parseColor("#1B2B1B")  // Green
            2 -> Color.parseColor("#000000")  // Dark
            1 -> {
                // Default theme follows system dark/light mode
                if (isSystemInDarkMode()) {
                    Color.parseColor("#1B1B1B")  // Dark
                } else {
                    Color.parseColor("#E8E8EE")  // Light
                }
            }
            else -> Color.parseColor("#1B1B1B")  // Default dark
        }
    }

    /**
     * Fallback method to get navigation bar height for devices where
     * WindowInsets API doesn't fire immediately. This uses the legacy
     * resource-based approach but the WindowInsets listener will override
     * this with the correct value when it fires.
     */
    private fun getNavigationBarHeightFallback(): Int {
        // For Android Q+, check gesture navigation mode
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val navModeResourceId = resources.getIdentifier(
                "config_navBarInteractionMode", "integer", "android"
            )
            if (navModeResourceId > 0) {
                val navMode = resources.getInteger(navModeResourceId)
                // Mode 2 = gesture navigation (small pill indicator)
                if (navMode == 2) {
                    // Gesture navigation has a small bottom inset (~48dp)
                    return (48 * resources.displayMetrics.density).toInt()
                }
            }
        }

        // For 3-button or 2-button navigation, get actual nav bar height
        val resourceId = resources.getIdentifier("navigation_bar_height", "dimen", "android")
        if (resourceId > 0 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return resources.getDimensionPixelSize(resourceId)
        }

        // For older Android versions, no navigation bar padding needed
        // as the system handles it differently
        return 0
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        setInputView(onCreateInputView())
    }

    override fun onCurrentInputMethodSubtypeChanged(newSubtype: InputMethodSubtype) {
        super.onCurrentInputMethodSubtypeChanged(newSubtype)

        val subtypeId = newSubtype.extraValue.toIntOrNull() ?: 1
        val newKeyboard = getKeyboard(subtypeId)
        currentKeyboard = newKeyboard
        currentInputHandler = inputHandlers[subtypeId] ?: EnglishInputHandler()
        currentInputHandler.reset()

        caps = false
        shifted = false

        kv?.let { keyboardView ->
            keyboardView.keyboard = newKeyboard
            newKeyboard.isShifted = false
            newKeyboard.setSpaceBarSubtypeName(
                getString(newSubtype.nameResId),
                ContextCompat.getDrawable(this, R.drawable.sym_keyboard_space)
            )
        }
    }

    private fun getKeyboard(subTypeId: Int): Keyboard {
        return when (subTypeId) {
            1 -> enKeyboard
            2 -> if (KeyboardConfig.isDoubleTap()) Keyboard(this, R.xml.my_2_qwerty)
                 else Keyboard(this, R.xml.my_qwerty)
            3 -> Keyboard(this, R.xml.shn_qwerty)
            4 -> Keyboard(this, R.xml.mon_qwerty)
            5 -> Keyboard(this, R.xml.sg_karen_qwerty)
            6 -> Keyboard(this, R.xml.wp_karen_qwerty)
            7 -> Keyboard(this, R.xml.ep_karen_qwerty)
            else -> currentKeyboard ?: enKeyboard
        }
    }

    private fun getSymKeyboard(subTypeId: Int): Keyboard {
        return when (subTypeId) {
            1 -> symKeyboard
            2 -> Keyboard(this, R.xml.my_symbol)
            3 -> Keyboard(this, R.xml.shn_symbol)
            4 -> Keyboard(this, R.xml.mon_symbol)
            5 -> Keyboard(this, R.xml.sg_karen_symbol)
            6 -> Keyboard(this, R.xml.wp_karen_symbol)
            7 -> Keyboard(this, R.xml.ep_karen_symbol)
            else -> currentKeyboard ?: symKeyboard
        }
    }

    private fun getShiftedKeyboard(subTypeId: Int): Keyboard? {
        return when (subTypeId) {
            1 -> {
                kv?.keyboard?.isShifted = true
                caps = true
                kv?.invalidateAllKeys()
                null
            }
            2 -> Keyboard(this, R.xml.my_shifted_qwerty)
            3 -> Keyboard(this, R.xml.shn_shifted_qwerty)
            4 -> Keyboard(this, R.xml.mon_shifted_qwerty)
            5 -> Keyboard(this, R.xml.sg_karen_shifted_qwerty)
            6 -> Keyboard(this, R.xml.wp_karen_shifted_qwerty)
            7 -> Keyboard(this, R.xml.ep_karen_shifted_qwerty)
            else -> currentKeyboard
        }
    }

    private fun getToken(): IBinder? {
        val dialog = window ?: return null
        val window = dialog.window ?: return null
        return window.attributes.token
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == Key.KEYCODE_DONE) {
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKey(primaryCode: Int, keyCodes: IntArray?, isRepeat: Boolean) {
        val ic = currentInputConnection ?: return

        // Emoji handling
        if (primaryCode in 128000..128567) {
            ic.commitText(String(Character.toChars(primaryCode)), 1)
            return
        }

        Log.d("MuaKeyboardService", "onKey: primaryCode=$primaryCode, sound=${KeyboardConfig.isSoundOn()}, isRepeat=$isRepeat")

        // Only play sound and haptic on first press, not on repeats
        if (!isRepeat) {
            if (KeyboardConfig.isSoundOn()) {
                SoundManager.playClick(this, primaryCode)
            }

            if (KeyboardConfig.isHapticEnabled()) {
                HapticManager.performHapticFeedback(this)
            }
        }

        when (primaryCode) {
            Key.KEYCODE_SHAN_VOWEL -> {
                val shanHandler = currentInputHandler as? ShanInputHandler
                ic.commitText(shanHandler?.shanVowel1() ?: "", 1)
                unshiftByLocale()
            }
            Key.KEYCODE_MYANMAR_MONEY -> {
                val localeId = getLocaleId()
                when (localeId) {
                    2, 3, 4 -> currentInputHandler.handleMoneySym(ic)
                }
                handleSymByLocale()
            }
            Key.KEYCODE_MODE_CHANGE -> {
                Log.d("MuaKeyboardService", "switch Symbol key")
                handleSymByLocale()
            }
            Key.KEYCODE_MYANMAR_DELETE -> {
                Log.d("MuaKeyboardService", "Myanmar/Shan Delete key code")
                if (KeyboardConfig.isPrimeBookOn()) {
                    Log.d("MuaKeyboardService", "Prime Book on")
                    currentInputHandler.handleDelete(ic, DeleteHandler.isEndOfText(ic))
                } else {
                    DeleteHandler.deleteChar(ic)
                }
            }
            Key.KEYCODE_DELETE -> {
                handleDeleteKey(ic)
            }
            Key.KEYCODE_EMOJI -> {
                showEmojiView()
            }
            Key.KEYCODE_BACK_FROM_EMOJI -> {
                hideEmojiView()
            }
            Key.KEYCODE_DONE -> {
                ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
                ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
            }
            Key.KEYCODE_SWITCH_KEYBOARD -> {
                inputMethodManager?.switchToNextInputMethod(getToken(), true)
            }
            Key.KEYCODE_SHIFT -> {
                handleShift()
            }
            else -> {
                var code = primaryCode.toChar()
                Log.d("MuaKeyboardService", "handle shift: $code")
                if (Character.isLetter(code) && caps) {
                    Log.d("MuaKeyboardService", "Capital letters")
                    code = Character.toUpperCase(code)
                }
                var cText = code.toString()
                Log.d("MuaKeyboardService", "cText: $cText")

                // Check for double-tap on Myanmar keyboard
                val localeId = getLocaleId()
                if (localeId == 2 && KeyboardConfig.isDoubleTap()) {
                    val alternateCode = currentInputHandler.checkDoubleTap(primaryCode, keyCodes)
                    if (alternateCode != -1) {
                        // Double-tap detected - check actual text to determine action
                        val textBefore = ic.getTextBeforeCursor(2, 0)

                        if (textBefore != null && textBefore.length >= 2) {
                            val firstChar = textBefore[0].code
                            val secondChar = textBefore[1].code

                            // Check if E-vowel reordering was done: [consonant][E_VOWEL]
                            // The first tap would have produced "ဘေ" pattern
                            if (secondChar == MyanmarUnicode.E_VOWEL) {
                                // E-vowel is at position 1 (after consonant) - reordering happened
                                // Delete both consonant and E-vowel (2 chars)
                                ic.deleteSurroundingText(2, 0)
                                // Output reordered result: alternate char + E_VOWEL
                                val reorderedText = charArrayOf(
                                    alternateCode.toChar(),
                                    MyanmarUnicode.E_VOWEL.toChar()
                                )
                                ic.commitText(String(reorderedText), 1)
                                currentInputHandler.prepareForDoubleTap()
                            } else if (firstChar == MyanmarUnicode.E_VOWEL ||
                                       firstChar == MyanmarUnicode.ZWSP) {
                                // E-vowel or ZWSP is at position 0 - no reordering happened yet
                                // Pattern is [ZWSP?][E_VOWEL][consonant] or similar
                                // Delete the consonant
                                ic.deleteSurroundingText(1, 0)
                                // Now process the alternate through handleInput for proper reordering
                                cText = currentInputHandler.handleInput(alternateCode, ic)
                                ic.commitText(cText, 1)
                            } else {
                                // Normal case - just replace
                                ic.deleteSurroundingText(1, 0)
                                ic.commitText(alternateCode.toChar().toString(), 1)
                            }
                        } else if (textBefore != null && textBefore.length == 1) {
                            // Only 1 char before cursor - simple replacement
                            ic.deleteSurroundingText(1, 0)
                            ic.commitText(alternateCode.toChar().toString(), 1)
                        } else {
                            // No text before - just output alternate
                            ic.commitText(alternateCode.toChar().toString(), 1)
                        }

                        if (shifted) {
                            unshiftByLocale()
                        }
                        return
                    }
                }

                // Use input handler for language-specific processing
                cText = if (localeId in 2..7) {
                    Log.d("MuaKeyboardService", "Using input handler for locale $localeId")
                    currentInputHandler.handleInput(primaryCode, ic)
                } else {
                    cText
                }

                ic.commitText(cText, 1)
                if (shifted) {
                    unshiftByLocale()
                }
            }
        }
    }

    private fun handleDeleteKey(ic: InputConnection) {
        Log.d("MuaKeyboardService", "Delete key code double tap")
        if (currentInputHandler is BamarInputHandler && KeyboardConfig.isPrimeBookOn() && KeyboardConfig.isDoubleTap()) {
            (currentInputHandler as BamarInputHandler).handleSingleDelete(ic)
        } else {
            DeleteHandler.deleteChar(ic)
        }
    }

    private fun handleSymByLocale() {
        val localeId = getLocaleId()
        if (!symbol) {
            kv?.keyboard = getSymKeyboard(localeId)
            symbol = true
        } else {
            kv?.keyboard = getKeyboard(localeId)
            symbol = false
        }
    }

    private fun handleShift() {
        if (!shifted) {
            shiftByLocale()
        } else {
            unshiftByLocale()
        }
    }

    private fun shiftByLocale() {
        if (kv?.keyboard == symKeyboard) {
            kv?.keyboard = symShiftedKeyboard
            kv?.keyboard?.isShifted = true
        } else {
            getShiftedKeyboard(getLocaleId())?.let { shiftedKeyboard ->
                kv?.keyboard = shiftedKeyboard
                kv?.keyboard?.isShifted = true
            }
        }
        shifted = true
    }

    private fun unshiftByLocale() {
        if (kv?.keyboard == symShiftedKeyboard) {
            kv?.keyboard = symKeyboard
            kv?.keyboard?.isShifted = false
        } else {
            kv?.keyboard = getKeyboard(getLocaleId())
            kv?.keyboard?.isShifted = false
            caps = false
            if (getLocaleId() == 1) {
                kv?.invalidateAllKeys()
            }
        }
        shifted = false
    }

    private fun getLocaleId(): Int {
        return try {
            inputMethodManager?.currentInputMethodSubtype?.extraValue?.toInt() ?: 1
        } catch (ex: NumberFormatException) {
            1
        }
    }

    override fun onKeyLongPress(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == Key.KEYCODE_DONE) {
            Log.d("MuaKeyboardService", "Enter key is long press")
            return true
        }
        return super.onKeyLongPress(keyCode, event)
    }

    override fun onPress(primaryCode: Int) {}

    override fun onRelease(primaryCode: Int) {}

    override fun onText(text: CharSequence?) {}

    override fun swipeDown() {}

    override fun swipeLeft() {
        inputMethodManager?.switchToNextInputMethod(getToken(), true)
    }

    override fun swipeRight() {
        inputMethodManager?.switchToNextInputMethod(getToken(), true)
    }

    override fun swipeUp() {}

    override fun onSpaceLongPress() {
        // Show the system input method picker
        inputMethodManager?.showInputMethodPicker()
    }

    companion object {
        private const val TAG = "MuaKeyboardService"
    }
}
