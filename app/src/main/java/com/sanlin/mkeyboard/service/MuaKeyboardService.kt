package com.sanlin.mkeyboard.service

import android.content.ClipboardManager
import android.content.Context
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import android.view.inputmethod.InputMethodSubtype
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.graphics.Color
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import android.content.SharedPreferences
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
import com.sanlin.mkeyboard.view.FlickKeyboardView
import com.sanlin.mkeyboard.view.OnFlickKeyboardActionListener
import com.sanlin.mkeyboard.view.SuggestionBarView
import com.sanlin.mkeyboard.keyboard.model.FlickKeyboard
import com.sanlin.mkeyboard.keyboard.model.FlickKey
import com.sanlin.mkeyboard.keyboard.model.FlickCharacter
import com.sanlin.mkeyboard.keyboard.model.FlickDirection
import com.sanlin.mkeyboard.suggestion.SuggestionManager
import com.sanlin.mkeyboard.suggestion.SuggestionMethod
import com.sanlin.mkeyboard.suggestion.SyllableBreaker
import com.sanlin.mkeyboard.autocorrect.AutoCapitalizer
import com.sanlin.mkeyboard.autocorrect.AutoCorrector
import java.util.concurrent.Executors

/**
 * Main Input Method Service for MUA Keyboard.
 * This service handles all keyboard interactions using the new non-deprecated components.
 */
class MuaKeyboardService : InputMethodService(), OnKeyboardActionListener, OnFlickKeyboardActionListener {

    private var kv: MuaKeyboardView? = null
    private var flickKv: FlickKeyboardView? = null
    private var flickKeyboard: FlickKeyboard? = null
    private var emojiView: EmojiView? = null
    private var suggestionBar: SuggestionBarView? = null
    private var keyboardContainer: FrameLayout? = null
    private var inputMethodManager: InputMethodManager? = null
    private var caps = false
    private var isEmojiMode = false
    private var isFlickMode = false
    private var wasFlickModeBeforeSymbol = false  // Track if we came from flick mode when entering symbol/emoji
    private var lastMainSubtypeId = 1  // Track the last used main subtype (not symbol/emoji)

    // Suggestion engine
    private var suggestionManager: SuggestionManager? = null
    private val suggestionHandler = Handler(Looper.getMainLooper())
    private var pendingSuggestionUpdate: Runnable? = null
    private val backgroundExecutor = Executors.newSingleThreadExecutor()
    private var lastSuggestionWord: String? = null  // Track which suggestion was selected

    // English autocorrect/capitalize
    private var autoCapitalizer: AutoCapitalizer? = null
    private var autoCorrector: AutoCorrector? = null
    private var englishSuggestionsEnabled = true
    private var autoCapitalizeEnabled = true
    private var autoCorrectEnabled = true

    // Clipboard
    private var clipboardManager: ClipboardManager? = null
    private val clipboardListener = ClipboardManager.OnPrimaryClipChangedListener {
        updateClipboardText()
    }

    // Preference listener for suggestion method/order changes
    private val preferenceChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
        when (key) {
            "suggestion_method" -> {
                val methodStr = prefs.getString("suggestion_method", "word") ?: "word"
                val method = when (methodStr) {
                    "syllable" -> SuggestionMethod.SYLLABLE
                    "both" -> SuggestionMethod.BOTH
                    else -> SuggestionMethod.WORD
                }
                backgroundExecutor.execute {
                    suggestionManager?.setMethod(method)
                }
                // Clear and update suggestions with new method
                suggestionBar?.clearSuggestions()
                updateSuggestions()
            }
            "suggestion_order" -> {
                // Order change just needs to refresh suggestions
                // The SuggestionManager reads the preference directly
                suggestionBar?.clearSuggestions()
                updateSuggestions()
            }
            "english_suggestions" -> {
                englishSuggestionsEnabled = prefs.getBoolean("english_suggestions", true)
                if (englishSuggestionsEnabled && suggestionManager?.isEnglishReady != true) {
                    backgroundExecutor.execute {
                        suggestionManager?.initializeEnglish()
                    }
                }
                suggestionBar?.clearSuggestions()
                updateSuggestions()
            }
            "auto_capitalize" -> {
                autoCapitalizeEnabled = prefs.getBoolean("auto_capitalize", true)
            }
            "auto_correct" -> {
                autoCorrectEnabled = prefs.getBoolean("auto_correct", true)
            }
        }
    }

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
    private var capsLock = false  // Caps lock mode (double-tap shift)
    private var lastShiftTime = 0L  // For detecting double-tap
    private var symbol = false
    private var wordSeparators: String = ""
    private var shanConsonants: String = ""

    companion object {
        private const val TAG = "MuaKeyboardService"
        private const val FLICK_KEYBOARD_ID = 8
        private const val SUGGESTION_DEBOUNCE_MS = 100L
        private const val TEXT_BEFORE_CURSOR_LENGTH = 100
        private const val DOUBLE_TAP_TIMEOUT_MS = 400L  // Time window for double-tap
    }

    override fun onCreate() {
        super.onCreate()
        wordSeparators = resources.getString(R.string.word_separators)
        shanConsonants = resources.getString(R.string.shan_consonants)
        inputMethodManager = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        initializeInputHandlers()

        // Initialize sound and haptic systems
        SoundManager.initialize(this)
        HapticManager.initialize(this)

        // Register clipboard listener
        clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboardManager?.addPrimaryClipChangedListener(clipboardListener)

        // Initialize recent emoji manager
        RecentEmojiManager.initialize(this)

        // Initialize suggestion engine in background
        initializeSuggestionEngine()

        // Register preference change listener
        PreferenceManager.getDefaultSharedPreferences(this)
            .registerOnSharedPreferenceChangeListener(preferenceChangeListener)
    }

    private fun initializeSuggestionEngine() {
        val sharedPref = PreferenceManager.getDefaultSharedPreferences(this)
        val methodStr = sharedPref.getString("suggestion_method", "word") ?: "word"
        val method = when (methodStr) {
            "syllable" -> SuggestionMethod.SYLLABLE
            "both" -> SuggestionMethod.BOTH
            else -> SuggestionMethod.WORD
        }

        // Read English preferences
        englishSuggestionsEnabled = sharedPref.getBoolean("english_suggestions", true)
        autoCapitalizeEnabled = sharedPref.getBoolean("auto_capitalize", true)
        autoCorrectEnabled = sharedPref.getBoolean("auto_correct", true)

        backgroundExecutor.execute {
            suggestionManager = SuggestionManager(this@MuaKeyboardService)
            val success = suggestionManager?.initialize(method) ?: false
            if (!success) {
                android.util.Log.e(TAG, "Failed to initialize suggestion manager")
            }

            // Initialize English engine if enabled
            if (englishSuggestionsEnabled) {
                val englishSuccess = suggestionManager?.initializeEnglish() ?: false
                if (englishSuccess) {
                    // Create autocorrect components on main thread
                    suggestionHandler.post {
                        autoCapitalizer = AutoCapitalizer()
                        autoCorrector = AutoCorrector()
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Unregister preference listener
        PreferenceManager.getDefaultSharedPreferences(this)
            .unregisterOnSharedPreferenceChangeListener(preferenceChangeListener)

        // Unregister clipboard listener
        clipboardManager?.removePrimaryClipChangedListener(clipboardListener)

        // Release sound and haptic resources
        SoundManager.release()
        HapticManager.release()

        // Release suggestion engine
        suggestionManager?.release()
        suggestionManager = null
        backgroundExecutor.shutdown()
    }

    private fun initializeInputHandlers() {
        inputHandlers[1] = EnglishInputHandler()
        inputHandlers[2] = BamarInputHandler(wordSeparators)
        inputHandlers[3] = ShanInputHandler(shanConsonants)
        inputHandlers[4] = MonInputHandler(wordSeparators)
        inputHandlers[5] = EnglishInputHandler() // SG Karen - pass-through
        inputHandlers[6] = EnglishInputHandler() // WP Karen - pass-through
        inputHandlers[7] = KarenInputHandler()
        inputHandlers[FLICK_KEYBOARD_ID] = BamarInputHandler(wordSeparators) // Flick uses Bamar handler
    }

    override fun onInitializeInterface() {
        val sharedPref = PreferenceManager.getDefaultSharedPreferences(this)
        KeyboardConfig.setDoubleTap(sharedPref.getBoolean("double_tap", false))

        enKeyboard = Keyboard(this, R.xml.en_qwerty)
        symKeyboard = Keyboard(this, R.xml.en_symbol)
        symShiftedKeyboard = Keyboard(this, R.xml.en_shift_symbol)

        shifted = false
        symbol = false

        // Restore lastMainSubtypeId from preferences, or use current locale as fallback
        val savedSubtypeId = sharedPref.getInt("last_main_subtype_id", -1)
        lastMainSubtypeId = if (savedSubtypeId > 0) savedSubtypeId else getLocaleId()

        currentKeyboard = enKeyboard
        currentKeyboard = getKeyboard(lastMainSubtypeId)
        currentInputHandler = inputHandlers[lastMainSubtypeId] ?: EnglishInputHandler()
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

        // Create a vertical container for suggestion bar + keyboard
        val verticalContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
        }

        // Create suggestion bar (always visible)
        suggestionBar = SuggestionBarView(this).apply {
            setOnSuggestionClickListener { word ->
                commitSuggestion(word)
            }
            setOnPasteClickListener {
                pasteFromClipboard()
            }
        }
        // Initial clipboard check
        updateClipboardText()
        verticalContainer.addView(suggestionBar, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        kv?.let { keyboardView ->
            // Set explicit LayoutParams since inflate with null parent ignores XML layout params
            val layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            // Add top margin for gap at top of keyboard
            layoutParams.topMargin = topPadding
            verticalContainer.addView(keyboardView, layoutParams)
        }

        container.addView(verticalContainer)

        // Create flick keyboard view
        flickKeyboard = FlickKeyboard(this)
        flickKv = FlickKeyboardView(this).apply {
            val layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            layoutParams.topMargin = topPadding
            visibility = View.GONE  // Initially hidden
            setKeyboard(flickKeyboard!!)
            setOnFlickKeyboardActionListener(this@MuaKeyboardService)
            verticalContainer.addView(this, layoutParams)
        }

        // Create emoji view
        emojiView = EmojiView(this).apply {
            val layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
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

            verticalContainer.addView(this, layoutParams)
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
        // Track if we came from flick mode
        wasFlickModeBeforeSymbol = isFlickMode
        isEmojiMode = true
        kv?.visibility = View.GONE
        flickKv?.visibility = View.GONE
        emojiView?.visibility = View.VISIBLE
        // Set back button label based on current language (will be ကခဂ if from flick mode)
        emojiView?.setBackButtonLabel(getBackButtonLabel())
    }

    /**
     * Hide the emoji view and show the keyboard.
     */
    private fun hideEmojiView() {
        isEmojiMode = false
        emojiView?.visibility = View.GONE
        // Return to the last used main subtype
        if (lastMainSubtypeId == FLICK_KEYBOARD_ID || wasFlickModeBeforeSymbol) {
            // Return to flick keyboard
            flickKv?.visibility = View.VISIBLE
            isFlickMode = true
        } else {
            // Return to standard keyboard with last main subtype
            kv?.keyboard = getKeyboard(lastMainSubtypeId)
            kv?.visibility = View.VISIBLE
            isFlickMode = false
        }
        wasFlickModeBeforeSymbol = false
    }

    /**
     * Show the flick keyboard and hide the standard keyboard.
     */
    private fun showFlickKeyboard() {
        isFlickMode = true
        kv?.visibility = View.GONE
        emojiView?.visibility = View.GONE
        flickKv?.visibility = View.VISIBLE
        currentInputHandler = inputHandlers[FLICK_KEYBOARD_ID] ?: BamarInputHandler(wordSeparators)
        currentInputHandler.reset()
    }

    /**
     * Hide the flick keyboard and show the standard keyboard.
     */
    private fun hideFlickKeyboard() {
        isFlickMode = false
        flickKv?.visibility = View.GONE
        emojiView?.visibility = View.GONE
        kv?.visibility = View.VISIBLE
    }

    /**
     * Get the back button label based on last used main keyboard language.
     * When from flick mode, always return Myanmar label.
     */
    private fun getBackButtonLabel(): String {
        // If we came from flick mode, always show Myanmar label
        if (isFlickMode || wasFlickModeBeforeSymbol || lastMainSubtypeId == FLICK_KEYBOARD_ID) {
            return "ကခဂ"
        }
        return when (lastMainSubtypeId) {
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
     * Update suggestions based on current text before cursor.
     * Uses debouncing to avoid excessive updates while typing.
     */
    private fun updateSuggestions() {
        // Cancel any pending update
        pendingSuggestionUpdate?.let { suggestionHandler.removeCallbacks(it) }

        pendingSuggestionUpdate = Runnable {
            val ic = currentInputConnection
            val manager = suggestionManager

            // Clear suggestions if no input connection or manager not ready
            if (ic == null || manager == null) {
                suggestionBar?.clearSuggestions()
                return@Runnable
            }

            // Clear suggestions during emoji mode
            if (isEmojiMode) {
                suggestionBar?.clearSuggestions()
                return@Runnable
            }

            val localeId = getLocaleId()
            val textBefore = ic.getTextBeforeCursor(TEXT_BEFORE_CURSOR_LENGTH, 0)?.toString() ?: ""

            // Handle English suggestions (locale 1)
            if (localeId == 1) {
                if (!englishSuggestionsEnabled || !manager.isEnglishReady) {
                    suggestionBar?.clearSuggestions()
                    return@Runnable
                }

                if (textBefore.isEmpty()) {
                    suggestionBar?.clearSuggestions()
                    return@Runnable
                }

                var suggestions = manager.getEnglishSuggestions(textBefore)

                // Filter out skipped words
                autoCorrector?.let { corrector ->
                    suggestions = corrector.filterSkipped(suggestions)
                }

                suggestionBar?.setSuggestions(suggestions)
                return@Runnable
            }

            // Handle Myanmar suggestions (locale 2-7 and flick)
            if (localeId !in 2..7 && localeId != FLICK_KEYBOARD_ID) {
                suggestionBar?.clearSuggestions()
                return@Runnable
            }

            if (!manager.isReady) {
                suggestionBar?.clearSuggestions()
                return@Runnable
            }

            // Clear suggestions if no Myanmar text
            if (textBefore.isEmpty() || !SyllableBreaker.containsMyanmarText(textBefore)) {
                suggestionBar?.clearSuggestions()
                return@Runnable
            }

            val suggestions = manager.getSuggestions(textBefore)
            suggestionBar?.setSuggestions(suggestions)
        }

        suggestionHandler.postDelayed(pendingSuggestionUpdate!!, SUGGESTION_DEBOUNCE_MS)
    }

    /**
     * Commit a suggestion from the suggestion bar.
     * For word suggestions, replaces the matched syllables.
     * For syllable suggestions, appends the syllable.
     */
    private fun commitSuggestion(word: String) {
        val ic = currentInputConnection ?: return
        val manager = suggestionManager ?: return

        val localeId = getLocaleId()
        val textBefore = ic.getTextBeforeCursor(TEXT_BEFORE_CURSOR_LENGTH, 0)?.toString() ?: ""

        val replaceLength = if (localeId == 1) {
            // English - replace current word
            manager.getEnglishReplacementLength(textBefore)
        } else {
            // Myanmar - use existing logic
            manager.getReplacementLength(textBefore, word)
        }

        if (replaceLength > 0) {
            ic.deleteSurroundingText(replaceLength, 0)
        }

        // For English, add space after word
        val commitText = if (localeId == 1) "$word " else word
        ic.commitText(commitText, 1)

        // Track committed suggestion for skip-after-delete
        if (localeId == 1) {
            autoCorrector?.onSuggestionCommitted(word)
            lastSuggestionWord = word
        }

        // Play feedback
        if (KeyboardConfig.isSoundOn()) {
            SoundManager.playClick(this, 0)
        }
        if (KeyboardConfig.isHapticEnabled()) {
            HapticManager.performHapticFeedback(this)
        }

        // Clear suggestions and trigger new update
        suggestionBar?.clearSuggestions()
        updateSuggestions()
    }

    /**
     * Update the clipboard text shown in suggestion bar when empty.
     */
    private fun updateClipboardText() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val clip = clipboard?.primaryClip
        val text = if (clip != null && clip.itemCount > 0) {
            clip.getItemAt(0)?.text?.toString()
        } else {
            null
        }
        suggestionBar?.setClipboardText(text)
    }

    /**
     * Paste text from clipboard.
     */
    private fun pasteFromClipboard() {
        val ic = currentInputConnection ?: return
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val clip = clipboard?.primaryClip
        val text = if (clip != null && clip.itemCount > 0) {
            clip.getItemAt(0)?.text?.toString()
        } else {
            null
        }

        if (!text.isNullOrEmpty()) {
            ic.commitText(text, 1)

            // Play feedback
            if (KeyboardConfig.isSoundOn()) {
                SoundManager.playClick(this, 0)
            }
            if (KeyboardConfig.isHapticEnabled()) {
                HapticManager.performHapticFeedback(this)
            }

            // Update suggestions
            updateSuggestions()
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

        // Restore the last used main subtype from preferences
        val sharedPref = PreferenceManager.getDefaultSharedPreferences(this)
        val savedSubtypeId = sharedPref.getInt("last_main_subtype_id", -1)
        if (savedSubtypeId > 0) {
            lastMainSubtypeId = savedSubtypeId
            applySubtype(savedSubtypeId)
        }

        // Reset autocorrector session for new input field
        // (skipped words will reappear in new sessions)
        autoCorrector?.resetSession()
        lastSuggestionWord = null

        // Reset suggestion bar state for new input field
        suggestionBar?.resetState()

        // Update clipboard text for paste suggestion
        updateClipboardText()

        // Auto-shift for English keyboard at start of text field
        // (except for email, URI, password fields)
        if (autoCapitalizeEnabled && shouldAutoShiftOnStart(info)) {
            shiftByLocale()
        }

        // Update suggestions for any existing text
        updateSuggestions()
    }

    /**
     * Check if keyboard should auto-shift at start of input.
     * Returns true for English keyboard at start of normal text fields.
     */
    private fun shouldAutoShiftOnStart(info: EditorInfo?): Boolean {
        if (info == null) return false

        // Only auto-shift for English keyboard
        val localeId = getLocaleId()
        if (localeId != 1) return false

        // Check if cursor is at start of text field
        val ic = currentInputConnection ?: return false
        val textBefore = ic.getTextBeforeCursor(1, 0)?.toString() ?: ""
        if (textBefore.isNotEmpty()) return false  // Not at start

        // Check input type - don't auto-shift for certain types
        val inputType = info.inputType and android.text.InputType.TYPE_MASK_CLASS
        val variation = info.inputType and android.text.InputType.TYPE_MASK_VARIATION

        // Skip for non-text types
        if (inputType != android.text.InputType.TYPE_CLASS_TEXT) return false

        // Skip for email, URI, password, and other special fields
        val skipVariations = listOf(
            android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
            android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_SUBJECT,
            android.text.InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS,
            android.text.InputType.TYPE_TEXT_VARIATION_URI,
            android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD,
            android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            android.text.InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
        )

        if (variation in skipVariations) return false

        return true
    }

    /**
     * Apply the given subtype to show the correct keyboard.
     */
    private fun applySubtype(subtypeId: Int) {
        // Handle flick keyboard subtype
        if (subtypeId == FLICK_KEYBOARD_ID) {
            isFlickMode = true
            kv?.visibility = View.GONE
            emojiView?.visibility = View.GONE
            flickKv?.visibility = View.VISIBLE
            currentInputHandler = inputHandlers[FLICK_KEYBOARD_ID] ?: BamarInputHandler(wordSeparators)
            currentInputHandler.reset()
            return
        }

        // For other subtypes, show standard keyboard
        isFlickMode = false
        flickKv?.visibility = View.GONE
        emojiView?.visibility = View.GONE
        kv?.visibility = View.VISIBLE

        val newKeyboard = getKeyboard(subtypeId)
        currentKeyboard = newKeyboard
        currentInputHandler = inputHandlers[subtypeId] ?: EnglishInputHandler()
        currentInputHandler.reset()

        kv?.keyboard = newKeyboard
    }

    override fun onCurrentInputMethodSubtypeChanged(newSubtype: InputMethodSubtype) {
        super.onCurrentInputMethodSubtypeChanged(newSubtype)

        val subtypeId = newSubtype.extraValue.toIntOrNull() ?: 1

        // Track the main subtype (this is the user-selected language subtype)
        lastMainSubtypeId = subtypeId

        // Persist to SharedPreferences
        PreferenceManager.getDefaultSharedPreferences(this)
            .edit()
            .putInt("last_main_subtype_id", subtypeId)
            .apply()

        // Handle flick keyboard subtype
        if (subtypeId == FLICK_KEYBOARD_ID) {
            showFlickKeyboard()
            return
        }

        // For other subtypes, hide flick and show standard keyboard
        if (isFlickMode) {
            hideFlickKeyboard()
        }

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
                updateSuggestions()
            }
            Key.KEYCODE_MYANMAR_MONEY -> {
                val localeId = getLocaleId()
                when (localeId) {
                    2, 3, 4 -> currentInputHandler.handleMoneySym(ic)
                }
                handleSymByLocale()
                updateSuggestions()
            }
            Key.KEYCODE_MODE_CHANGE -> {
                handleSymByLocale()
            }
            Key.KEYCODE_MYANMAR_DELETE -> {
                if (KeyboardConfig.isPrimeBookOn()) {
                    currentInputHandler.handleDelete(ic, DeleteHandler.isEndOfText(ic))
                } else {
                    DeleteHandler.deleteChar(ic)
                }
                updateSuggestions()
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
                val localeId = getLocaleId()

                // Auto-capitalize for English
                if (localeId == 1 && autoCapitalizeEnabled && Character.isLetter(code)) {
                    val capitalizedCode = autoCapitalizer?.capitalizeIfNeeded(primaryCode, ic)
                    if (capitalizedCode != null && capitalizedCode != primaryCode) {
                        code = capitalizedCode.toChar()
                    }
                }

                if (Character.isLetter(code) && caps) {
                    code = Character.toUpperCase(code)
                }
                var cText = code.toString()

                // Check for double-tap on Myanmar keyboard
                if (localeId == 2 && KeyboardConfig.isDoubleTap()) {
                    val alternateCode = currentInputHandler.checkDoubleTap(primaryCode, keyCodes)
                    if (alternateCode != -1) {
                        // Double-tap detected - check actual text to determine action
                        // Check 3 chars to detect [consonant][E_VOWEL][first_tap_char] pattern
                        val textBefore3 = ic.getTextBeforeCursor(3, 0)
                        val textBefore = ic.getTextBeforeCursor(2, 0)

                        // Check if alternate is a medial (needs special reordering)
                        val isMedial = MyanmarUnicode.isMedial(alternateCode)

                        if (textBefore3 != null && textBefore3.length >= 3 && isMedial) {
                            val thirdChar = textBefore3[0].code  // Character 3 positions back
                            val secondChar = textBefore3[1].code // E_VOWEL position
                            val firstChar = textBefore3[2].code  // First tap character

                            // Check for pattern [consonant][E_VOWEL][first_tap] -> insert medial
                            if (secondChar == MyanmarUnicode.E_VOWEL &&
                                MyanmarUnicode.isConsonant(thirdChar)) {
                                // Pattern: [consonant][E_VOWEL][first_tap]
                                // Delete 2 chars (first_tap + E_VOWEL), insert [medial][E_VOWEL]
                                ic.deleteSurroundingText(2, 0)
                                val reorderedText = charArrayOf(
                                    alternateCode.toChar(),
                                    MyanmarUnicode.E_VOWEL.toChar()
                                )
                                ic.commitText(String(reorderedText), 1)
                                currentInputHandler.prepareForDoubleTap()
                                if (shifted) unshiftByLocale()
                                updateSuggestions()
                                return
                            }
                        }

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
                        updateSuggestions()
                        return
                    }
                }

                // Use input handler for language-specific processing
                cText = if (localeId in 2..7) {
                    currentInputHandler.handleInput(primaryCode, ic)
                } else {
                    cText
                }

                // Smart punctuation: if punctuation after space, move punctuation before space
                if (localeId == 1 && isSmartPunctuation(code)) {
                    if (handleSmartPunctuation(ic, code)) {
                        // Smart punctuation handled, skip normal commit
                        // Don't unshift if caps lock is on
                        if (shifted && !capsLock) {
                            unshiftByLocale()
                        }
                        updateSuggestions()
                        return
                    }
                }

                ic.commitText(cText, 1)

                // For English, fix standalone "i" after space
                if (localeId == 1 && autoCapitalizeEnabled && cText == " ") {
                    autoCapitalizer?.fixStandaloneI(ic, ' ')
                }

                // For English, autocorrect contractions after space
                if (localeId == 1 && autoCorrectEnabled && cText == " ") {
                    autoCorrector?.correctContraction(ic)
                }

                // Clear autocorrector tracking when typing (not deleting)
                if (localeId == 1) {
                    autoCorrector?.onCharacterTyped()
                }

                // Don't unshift if caps lock is on (English only)
                if (shifted && !(localeId == 1 && capsLock)) {
                    unshiftByLocale()
                }

                // Sync shift state with auto-capitalization (after unshift)
                syncAutoShiftState()

                updateSuggestions()
            }
        }
    }

    private fun handleDeleteKey(ic: InputConnection) {
        val localeId = getLocaleId()

        // For English, track deletions for skip-after-delete
        if (localeId == 1) {
            val textBefore = ic.getTextBeforeCursor(TEXT_BEFORE_CURSOR_LENGTH, 0)?.toString() ?: ""
            autoCorrector?.onDelete(textBefore, 1)
        }

        if (currentInputHandler is BamarInputHandler && KeyboardConfig.isPrimeBookOn() && KeyboardConfig.isDoubleTap()) {
            (currentInputHandler as BamarInputHandler).handleSingleDelete(ic)
        } else {
            DeleteHandler.deleteChar(ic)
        }

        // Sync shift state after delete (might be back at sentence start)
        syncAutoShiftState()

        updateSuggestions()
    }

    /**
     * Check if character is a punctuation that should trigger smart handling.
     * These are punctuation marks that typically follow text without a space before.
     */
    private fun isSmartPunctuation(char: Char): Boolean {
        return char in listOf('.', ',', '!', '?', ';', ':', ')', ']', '}', '\'', '"')
    }

    /**
     * Handle smart punctuation: if there's a space before cursor,
     * delete the space, insert punctuation, then add space after.
     *
     * Example: "Hello |" + "." -> "Hello. |"
     *
     * @return true if smart punctuation was handled, false otherwise
     */
    private fun handleSmartPunctuation(ic: InputConnection, punctuation: Char): Boolean {
        val textBefore = ic.getTextBeforeCursor(1, 0)?.toString() ?: return false

        // Check if there's a space before cursor
        if (textBefore == " ") {
            // Delete the space
            ic.deleteSurroundingText(1, 0)
            // Insert punctuation + space
            ic.commitText("$punctuation ", 1)
            return true
        }

        return false
    }

    private fun handleSymByLocale() {
        if (!symbol) {
            // Entering symbol mode - use lastMainSubtypeId to get correct symbol keyboard
            if (isFlickMode || lastMainSubtypeId == FLICK_KEYBOARD_ID) {
                // Coming from flick keyboard - track it and show Myanmar symbol
                wasFlickModeBeforeSymbol = true
                flickKv?.visibility = View.GONE
                kv?.visibility = View.VISIBLE
                kv?.keyboard = getSymKeyboard(2)  // Use Myanmar symbol layout (locale 2)
            } else {
                kv?.keyboard = getSymKeyboard(lastMainSubtypeId)
            }
            symbol = true
        } else {
            // Exiting symbol mode - return to last main subtype
            if (wasFlickModeBeforeSymbol || lastMainSubtypeId == FLICK_KEYBOARD_ID) {
                // Return to flick keyboard
                kv?.visibility = View.GONE
                flickKv?.visibility = View.VISIBLE
                isFlickMode = true
                wasFlickModeBeforeSymbol = false
            } else {
                kv?.keyboard = getKeyboard(lastMainSubtypeId)
                isFlickMode = false
            }
            symbol = false
        }
    }

    private fun handleShift() {
        val localeId = getLocaleId()
        val currentTime = System.currentTimeMillis()

        // For English keyboard, handle caps lock (double-tap)
        if (localeId == 1) {
            if (capsLock) {
                // Already in caps lock, turn it off
                capsLock = false
                unshiftByLocale()
            } else if (shifted && (currentTime - lastShiftTime) < DOUBLE_TAP_TIMEOUT_MS) {
                // Double-tap detected while shifted - enable caps lock
                capsLock = true
                // Keep shifted state, keyboard already shows capitals
            } else if (!shifted) {
                // First tap - shift
                shiftByLocale()
                lastShiftTime = currentTime
            } else {
                // Shifted but not double-tap - unshift
                unshiftByLocale()
            }
        } else {
            // Non-English keyboards - normal shift behavior
            if (!shifted) {
                shiftByLocale()
            } else {
                unshiftByLocale()
            }
        }
    }

    private fun shiftByLocale() {
        if (kv?.keyboard == symKeyboard) {
            kv?.keyboard = symShiftedKeyboard
            kv?.keyboard?.isShifted = true
        } else {
            val localeId = getLocaleId()
            if (localeId == 1) {
                // English - just set shifted state and update display
                kv?.keyboard?.isShifted = true
                caps = true
                kv?.invalidateAllKeys()
            } else {
                // Other languages - load shifted keyboard layout
                getShiftedKeyboard(localeId)?.let { shiftedKeyboard ->
                    kv?.keyboard = shiftedKeyboard
                    kv?.keyboard?.isShifted = true
                }
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

    /**
     * Sync keyboard shift state with auto-capitalization.
     * If the next character should be capitalized, shift the keyboard.
     * Only applies to English keyboard when auto-capitalize is enabled.
     */
    private fun syncAutoShiftState() {
        val localeId = getLocaleId()
        if (localeId != 1) return  // Only for English
        if (!autoCapitalizeEnabled) return
        if (capsLock) return  // Don't interfere with caps lock

        val ic = currentInputConnection ?: return
        val shouldShift = autoCapitalizer?.shouldCapitalize(ic) ?: false

        if (shouldShift && !shifted) {
            shiftByLocale()
        } else if (!shouldShift && shifted && !capsLock) {
            // Don't auto-unshift here - let normal flow handle it
        }
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

    // ==================== OnFlickKeyboardActionListener Implementation ====================

    override fun onFlickCharacter(character: FlickCharacter, direction: FlickDirection, key: FlickKey) {
        val ic = currentInputConnection ?: return

        // Ignore empty/placeholder keys
        if (character.label.isEmpty() && character.code == 0) {
            return
        }

        // Play sound and haptic feedback
        if (KeyboardConfig.isSoundOn()) {
            SoundManager.playClick(this, character.code)
        }
        if (KeyboardConfig.isHapticEnabled()) {
            HapticManager.performHapticFeedback(this)
        }

        // Get the text to commit
        val commitText = character.getCommitText()

        // Use input handler for Myanmar-specific processing (E-vowel reordering, etc.)
        val processedText = if (commitText.length == 1) {
            currentInputHandler.handleInput(character.code, ic)
        } else {
            // Multi-character sequence - commit each character through handler
            val sb = StringBuilder()
            for (c in commitText) {
                sb.append(currentInputHandler.handleInput(c.code, ic))
            }
            sb.toString()
        }

        ic.commitText(processedText, 1)
        updateSuggestions()
    }

    override fun onSpecialKey(primaryCode: Int, key: Key) {
        val ic = currentInputConnection ?: return

        // Play sound and haptic feedback
        if (KeyboardConfig.isSoundOn()) {
            SoundManager.playClick(this, primaryCode)
        }
        if (KeyboardConfig.isHapticEnabled()) {
            HapticManager.performHapticFeedback(this)
        }

        when (primaryCode) {
            Key.KEYCODE_MODE_CHANGE -> {
                handleSymByLocale()
            }
            Key.KEYCODE_SWITCH_KEYBOARD -> {
                // Switch to next input method subtype (globe key)
                inputMethodManager?.switchToNextInputMethod(getToken(), true)
            }
            Key.KEYCODE_DELETE -> {
                if (KeyboardConfig.isPrimeBookOn()) {
                    currentInputHandler.handleDelete(ic, DeleteHandler.isEndOfText(ic))
                } else {
                    DeleteHandler.deleteChar(ic)
                }
                updateSuggestions()
            }
            Key.KEYCODE_DONE -> {
                ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
                ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
            }
            Key.KEYCODE_EMOJI -> {
                showEmojiView()
            }
            32 -> { // Space
                ic.commitText(" ", 1)
                updateSuggestions()
            }
        }
    }

    override fun onSpecialKeyRepeat(primaryCode: Int, key: Key) {
        val ic = currentInputConnection ?: return

        when (primaryCode) {
            Key.KEYCODE_DELETE -> {
                DeleteHandler.deleteChar(ic)
                updateSuggestions()
            }
        }
    }

    override fun onKeyDown(key: Any) {
        // Key press feedback is handled in onFlickCharacter and onSpecialKey
    }

    override fun onKeyUp(key: Any) {
        // Key release - no action needed
    }

    override fun onSwipeLeft() {
        inputMethodManager?.switchToNextInputMethod(getToken(), true)
    }

    override fun onSwipeRight() {
        inputMethodManager?.switchToNextInputMethod(getToken(), true)
    }

    override fun onPunctuationSingleTap() {
        val ic = currentInputConnection ?: return

        // Play sound and haptic feedback
        if (KeyboardConfig.isSoundOn()) {
            SoundManager.playClick(this, 0x104A)
        }
        if (KeyboardConfig.isHapticEnabled()) {
            HapticManager.performHapticFeedback(this)
        }

        // Insert ၊ (Little Section)
        ic.commitText("၊", 1)
        updateSuggestions()
    }

    override fun onPunctuationDoubleTap() {
        val ic = currentInputConnection ?: return

        // Play sound and haptic feedback
        if (KeyboardConfig.isSoundOn()) {
            SoundManager.playClick(this, 0x104B)
        }
        if (KeyboardConfig.isHapticEnabled()) {
            HapticManager.performHapticFeedback(this)
        }

        // Double tap detected before single tap was executed,
        // so just insert ။ (Section) directly - no deletion needed
        ic.commitText("။", 1)
        updateSuggestions()
    }

    override fun onFlickKeyLongPress(key: FlickKey, alternateCode: Int) {
        val ic = currentInputConnection ?: return

        // Play sound and haptic feedback
        if (KeyboardConfig.isSoundOn()) {
            SoundManager.playClick(this, alternateCode)
        }
        if (KeyboardConfig.isHapticEnabled()) {
            HapticManager.performHapticFeedback(this)
        }

        // Use input handler for proper Myanmar text processing (reordering, etc.)
        val processedText = currentInputHandler.handleInput(alternateCode, ic)
        ic.commitText(processedText, 1)
        updateSuggestions()
    }
}
