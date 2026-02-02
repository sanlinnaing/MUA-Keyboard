package com.sanlin.mkeyboard.view

import android.content.Context
import android.graphics.*
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import com.sanlin.mkeyboard.R
import com.sanlin.mkeyboard.config.KeyboardConfig
import com.sanlin.mkeyboard.keyboard.model.Key
import com.sanlin.mkeyboard.keyboard.model.Keyboard
import java.lang.ref.WeakReference
import kotlin.math.abs

/**
 * Custom keyboard view that renders and handles input for the MUA Keyboard.
 * This replaces the deprecated android.inputmethodservice.KeyboardView.
 */
open class MuaKeyboardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // Keyboard data
    private var _keyboard: Keyboard? = null
    var keyboard: Keyboard?
        get() = _keyboard
        set(value) {
            _keyboard = value
            requestLayout()
            invalidate()
        }

    // Listener for keyboard actions
    private var keyboardActionListener: OnKeyboardActionListener? = null

    // Drawing resources
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val keyBackground: Drawable?
    private val keyTextColor: Int
    private val keyTextSize: Float
    private val hintTextColor: Int
    private val hintTextSize: Float
    private val shadowColor: Int
    private val shadowRadius: Float
    private val labelTextSize: Float

    // Key state tracking
    private var currentKey: Key? = null
    private var lastKey: Key? = null
    private var downTime: Long = 0
    private var lastTapTime: Long = 0
    private var repeatKey: Key? = null

    // Key preview
    private var keyPreviewPopup: KeyPreviewPopup? = null
    private var showKeyPreview = true

    // Popup keyboard for long press (drawn directly on canvas)
    private var miniKeyboardOnScreen = false

    // Gesture detection for swipes
    private val gestureDetector: GestureDetector

    // Handler for key repeat
    private val handler: Handler

    // Location array for popup positioning
    private val parentLocation = IntArray(2)

    // Swipe threshold
    private val swipeThreshold = 100

    // Long press timeout
    private val longPressTimeout = 400L

    // Key repeat interval
    private val repeatInterval = 50L

    // Handler message types
    private companion object {
        const val MSG_REPEAT = 1
        const val MSG_LONGPRESS = 2
    }

    init {
        // Load styled attributes
        val a = context.obtainStyledAttributes(attrs, R.styleable.MuaKeyboardView)

        keyBackground = a.getDrawable(R.styleable.MuaKeyboardView_keyBackground)
        keyTextColor = a.getColor(R.styleable.MuaKeyboardView_keyTextColor, Color.BLACK)
        keyTextSize = a.getDimension(R.styleable.MuaKeyboardView_keyTextSize, 18f * resources.displayMetrics.scaledDensity)
        hintTextColor = a.getColor(R.styleable.MuaKeyboardView_hintTextColor, Color.parseColor("#FFCC00"))
        hintTextSize = a.getDimension(R.styleable.MuaKeyboardView_hintTextSize, 12f * resources.displayMetrics.scaledDensity)
        shadowColor = a.getColor(R.styleable.MuaKeyboardView_shadowColor, 0)
        shadowRadius = a.getDimension(R.styleable.MuaKeyboardView_shadowRadius, 0f)
        labelTextSize = a.getDimension(R.styleable.MuaKeyboardView_labelTextSize, 14f * resources.displayMetrics.scaledDensity)

        a.recycle()

        // Initialize gesture detector
        gestureDetector = GestureDetector(context, GestureListener())

        // Initialize handler for key repeat
        handler = KeyHandler(this)

        // Initialize key preview
        keyPreviewPopup = KeyPreviewPopup(context)

        // Make focusable for touch events
        isFocusable = true
        isFocusableInTouchMode = true
    }

    /**
     * Set the keyboard action listener.
     */
    fun setOnKeyboardActionListener(listener: OnKeyboardActionListener?) {
        keyboardActionListener = listener
    }

    /**
     * Enable or disable key preview.
     */
    fun setPreviewEnabled(enabled: Boolean) {
        showKeyPreview = enabled
    }

    /**
     * Invalidate all keys (force complete redraw).
     */
    fun invalidateAllKeys() {
        invalidate()
    }

    /**
     * Invalidate a specific key.
     */
    fun invalidateKey(key: Key) {
        invalidate(key.x, key.y, key.x + key.width, key.y + key.height)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val keyboard = _keyboard
        if (keyboard != null) {
            setMeasuredDimension(keyboard.getWidth(), keyboard.getHeight())
        } else {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val keyboard = _keyboard ?: return

        // Draw each key
        for (key in keyboard.keys) {
            drawKey(canvas, key)
        }

        // Draw hint labels if enabled
        if (KeyboardConfig.isShowHintLabel()) {
            drawHintLabels(canvas, keyboard)
        }

        // Draw popup keyboard overlay if visible
        if (miniKeyboardOnScreen && popupKeyboardKeys.isNotEmpty()) {
            drawPopupKeyboard(canvas)
        }
    }

    /**
     * Draw the popup keyboard directly on the canvas.
     * Styled to look elevated/lifted from the keyboard.
     */
    private fun drawPopupKeyboard(canvas: Canvas) {
        val cornerRadius = 12 * resources.displayMetrics.density
        val padding = 6 * resources.displayMetrics.density

        // Colors - brighter than normal keys to stand out
        val containerBgColor = 0xFF2D2D35.toInt()  // Slightly lighter dark background
        val keyBgColor = 0xFF4A4B55.toInt()        // Brighter key background
        val selectedBgColor = 0xFF6A6B7A.toInt()   // Highlighted selection
        val textColor = 0xFFFFFFFF.toInt()
        val borderColor = 0xFF5A5B65.toInt()       // Subtle border

        // Calculate expanded container rect with padding
        val containerLeft = popupX - padding
        val containerTop = popupY - padding
        val containerRight = popupX + popupTotalWidth + padding
        val containerBottom = popupY + popupKeyHeight + padding

        // Draw multiple shadow layers for depth effect
        paint.style = Paint.Style.FILL

        // Outer shadow (larger, more diffuse)
        paint.color = 0x33000000
        canvas.drawRoundRect(
            RectF(containerLeft + 6, containerTop + 8, containerRight + 6, containerBottom + 8),
            cornerRadius, cornerRadius, paint
        )

        // Middle shadow
        paint.color = 0x44000000
        canvas.drawRoundRect(
            RectF(containerLeft + 3, containerTop + 4, containerRight + 3, containerBottom + 4),
            cornerRadius, cornerRadius, paint
        )

        // Inner shadow (tighter)
        paint.color = 0x55000000
        canvas.drawRoundRect(
            RectF(containerLeft + 1, containerTop + 2, containerRight + 1, containerBottom + 2),
            cornerRadius, cornerRadius, paint
        )

        // Draw container background
        paint.color = containerBgColor
        val containerRect = RectF(containerLeft, containerTop, containerRight, containerBottom)
        canvas.drawRoundRect(containerRect, cornerRadius, cornerRadius, paint)

        // Draw container border for definition
        paint.color = borderColor
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.5f * resources.displayMetrics.density
        canvas.drawRoundRect(containerRect, cornerRadius, cornerRadius, paint)
        paint.style = Paint.Style.FILL

        // Draw each popup key
        for ((index, key) in popupKeyboardKeys.withIndex()) {
            // Draw key background
            paint.color = if (index == selectedPopupKeyIndex) selectedBgColor else keyBgColor
            val keyRect = RectF(
                (popupX + key.x + 3).toFloat(),
                (popupY + 3).toFloat(),
                (popupX + key.x + key.width - 3).toFloat(),
                (popupY + popupKeyHeight - 3).toFloat()
            )
            canvas.drawRoundRect(keyRect, cornerRadius - 4, cornerRadius - 4, paint)

            // Draw key label
            key.label?.let { label ->
                paint.color = textColor
                paint.textSize = keyTextSize * 1.1f  // Slightly larger text
                paint.textAlign = Paint.Align.CENTER
                val x = popupX + key.x + key.width / 2f
                val y = popupY + (popupKeyHeight + paint.textSize - paint.descent()) / 2f
                canvas.drawText(label.toString(), x, y, paint)
            }
        }
    }

    /**
     * Draw a single key.
     */
    private fun drawKey(canvas: Canvas, key: Key) {
        // Draw key background
        if (keyBackground != null) {
            val bg = keyBackground.mutate()
            val drawableState = key.getCurrentDrawableState()
            bg.state = drawableState

            bg.setBounds(key.x, key.y, key.x + key.width, key.y + key.height)
            bg.draw(canvas)
        } else {
            // If no background, draw a simple rectangle so we can at least see the key bounds
            paint.color = Color.DKGRAY
            paint.style = Paint.Style.FILL
            canvas.drawRect(
                key.x.toFloat(), key.y.toFloat(),
                (key.x + key.width).toFloat(), (key.y + key.height).toFloat(),
                paint
            )
            paint.style = Paint.Style.STROKE
            paint.color = Color.GRAY
            canvas.drawRect(
                key.x.toFloat(), key.y.toFloat(),
                (key.x + key.width).toFloat(), (key.y + key.height).toFloat(),
                paint
            )
        }

        // Draw key icon or label
        if (key.icon != null) {
            drawKeyIcon(canvas, key)
        } else if (key.label != null) {
            drawKeyLabel(canvas, key)
        }
    }

    /**
     * Draw the key icon.
     */
    private fun drawKeyIcon(canvas: Canvas, key: Key) {
        val icon = key.icon ?: return
        val iconWidth = icon.intrinsicWidth
        val iconHeight = icon.intrinsicHeight

        val iconX = key.x + (key.width - iconWidth) / 2
        val iconY = key.y + (key.height - iconHeight) / 2

        icon.setBounds(iconX, iconY, iconX + iconWidth, iconY + iconHeight)
        icon.draw(canvas)
    }

    /**
     * Draw the key label.
     */
    private fun drawKeyLabel(canvas: Canvas, key: Key) {
        val label = key.label ?: return

        // Set up paint for label
        paint.color = keyTextColor
        paint.textSize = if (label.length > 1 && key.codes[0] < 0) labelTextSize else keyTextSize
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = Typeface.DEFAULT

        if (shadowRadius > 0) {
            paint.setShadowLayer(shadowRadius, 0f, 0f, shadowColor)
        }

        // Calculate text position (centered in key)
        val x = key.x + key.width / 2f
        val y = key.y + (key.height + paint.textSize - paint.descent()) / 2f

        // If keyboard is shifted and label is a single letter, show uppercase
        val displayLabel = if (_keyboard?.isShifted == true && label.length == 1 &&
                               Character.isLetter(label[0])) {
            label.toString().uppercase()
        } else {
            label.toString()
        }

        canvas.drawText(displayLabel, x, y, paint)

        // Remove shadow layer
        paint.clearShadowLayer()
    }

    /**
     * Draw hint labels (popup characters) for keys.
     * Positioned in top-right corner as superscript style.
     */
    private fun drawHintLabels(canvas: Canvas, keyboard: Keyboard) {
        // Use slightly smaller text size for hints
        paint.textSize = hintTextSize * 0.85f
        paint.color = hintTextColor
        paint.textAlign = Paint.Align.RIGHT

        val padding = 6 * resources.displayMetrics.density  // 6dp from right edge

        for (key in keyboard.keys) {
            if (key.label != null) {
                // Handle emoji label special case
                if (key.label == ";)") {
                    key.label = String(Character.toChars(key.codes[0]))
                }

                key.popupCharacters?.let { popupChars ->
                    val popKeyLabel = if (popupChars.length > 1) {
                        popupChars.subSequence(0, minOf(2, popupChars.length)).toString()
                    } else {
                        popupChars.toString()
                    }

                    // Position in top-right corner
                    val xPos = key.x + key.width - padding
                    val yPos = key.y + hintTextSize + (4 * resources.displayMetrics.density)

                    canvas.drawText(popKeyLabel, xPos, yPos, paint)
                }
            }
        }

        // Reset text alignment for other drawing operations
        paint.textAlign = Paint.Align.CENTER
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        // Let gesture detector handle fling events
        if (gestureDetector.onTouchEvent(event)) {
            return true
        }

        // Handle touch events for key presses
        val action = event.action
        val touchX = event.x.toInt()
        val touchY = event.y.toInt()

        when (action) {
            MotionEvent.ACTION_DOWN -> {
                handleTouchDown(touchX, touchY)
            }
            MotionEvent.ACTION_MOVE -> {
                handleTouchMove(touchX, touchY)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                handleTouchUp(touchX, touchY, action == MotionEvent.ACTION_CANCEL)
            }
        }

        return true
    }

    private fun handleTouchDown(x: Int, y: Int) {
        val keyboard = _keyboard ?: return
        val key = keyboard.getKeyAt(x, y) ?: return

        downTime = System.currentTimeMillis()
        currentKey = key
        key.pressed = true

        // Show key preview
        if (showKeyPreview && key.icon == null) {
            getLocationOnScreen(parentLocation)
            keyPreviewPopup?.show(key, this, parentLocation)
        }

        // Notify listener
        val primaryCode = if (key.codes.isNotEmpty()) key.codes[0] else 0
        keyboardActionListener?.onPress(primaryCode)

        // Set up key repeat for repeatable keys
        if (key.repeatable) {
            repeatKey = key
            handler.sendEmptyMessageDelayed(MSG_REPEAT, longPressTimeout)
        }

        // Set up long press detection for popup keyboard (only need popupCharacters)
        // Also set up long press for space key to show input method picker
        if (!key.popupCharacters.isNullOrEmpty() || primaryCode == 32) {  // 32 = space
            handler.sendMessageDelayed(
                handler.obtainMessage(MSG_LONGPRESS, key),
                longPressTimeout
            )
        }

        invalidateKey(key)
    }

    private fun handleTouchMove(x: Int, y: Int) {
        // If popup keyboard is showing, track selection on it
        if (miniKeyboardOnScreen) {
            handlePopupKeyboardMove(x, y)
            return
        }

        val keyboard = _keyboard ?: return
        val newKey = keyboard.getKeyAt(x, y)

        if (newKey != currentKey) {
            // Key changed, cancel any pending messages
            handler.removeMessages(MSG_REPEAT)
            handler.removeMessages(MSG_LONGPRESS)

            // Update key states
            currentKey?.let { oldKey ->
                oldKey.pressed = false
                invalidateKey(oldKey)
            }

            if (newKey != null) {
                currentKey = newKey
                newKey.pressed = true
                invalidateKey(newKey)

                // Show preview for new key
                if (showKeyPreview && newKey.icon == null) {
                    getLocationOnScreen(parentLocation)
                    keyPreviewPopup?.show(newKey, this, parentLocation)
                }
            } else {
                currentKey = null
                keyPreviewPopup?.dismiss()
            }
        }
    }

    private fun handleTouchUp(x: Int, y: Int, cancelled: Boolean) {
        handler.removeMessages(MSG_REPEAT)
        handler.removeMessages(MSG_LONGPRESS)
        repeatKey = null

        // Dismiss key preview
        keyPreviewPopup?.dismiss()

        // Handle popup keyboard selection
        if (miniKeyboardOnScreen) {
            val selectedCode = getSelectedPopupKeyCode()
            dismissPopupKeyboard()

            if (selectedCode > 0 && !cancelled) {
                // Send the selected popup key
                keyboardActionListener?.onKey(selectedCode, intArrayOf(selectedCode))
            }

            // Reset key state
            currentKey?.let { pressedKey ->
                pressedKey.pressed = false
                invalidateKey(pressedKey)
            }
            currentKey = null
            return
        }

        val key = currentKey
        if (key != null && !cancelled) {
            val primaryCode = if (key.codes.isNotEmpty()) key.codes[0] else 0

            // Notify listener of key release
            keyboardActionListener?.onRelease(primaryCode)

            // Check if text should be committed
            if (key.text != null) {
                keyboardActionListener?.onText(key.text)
            } else {
                // Send key code
                keyboardActionListener?.onKey(primaryCode, key.codes)
            }
        }

        // Reset key state
        currentKey?.let { pressedKey ->
            pressedKey.pressed = false
            invalidateKey(pressedKey)
        }
        currentKey = null
    }

    /**
     * Handle key repeat for repeatable keys.
     */
    private fun repeatKey() {
        val key = repeatKey ?: return
        val primaryCode = if (key.codes.isNotEmpty()) key.codes[0] else 0

        // Pass isRepeat=true so service knows not to play sound/haptic
        keyboardActionListener?.onKey(primaryCode, key.codes, isRepeat = true)
        handler.sendEmptyMessageDelayed(MSG_REPEAT, repeatInterval)
    }

    // Popup keyboard tracking (drawn directly on canvas, not using PopupWindow)
    private var popupKey: Key? = null
    private var popupKeyboardKeys: List<Key> = emptyList()
    private var selectedPopupKeyIndex: Int = -1
    private var popupX: Int = 0
    private var popupY: Int = 0
    private var popupTotalWidth: Int = 0
    private var popupKeyWidth: Int = 0
    private var popupKeyHeight: Int = 0

    /**
     * Handle long press for popup keyboard or special keys like space.
     */
    private fun handleLongPress(key: Key) {
        // Cancel key repeat if active
        handler.removeMessages(MSG_REPEAT)
        repeatKey = null

        // Check if it's the space key - show input method picker
        val primaryCode = if (key.codes.isNotEmpty()) key.codes[0] else 0
        if (primaryCode == 32) {  // 32 = space
            // Dismiss key preview
            keyPreviewPopup?.dismiss()
            // Reset key state
            key.pressed = false
            invalidateKey(key)
            currentKey = null
            // Call the listener to show input method picker
            keyboardActionListener?.onSpaceLongPress()
            return
        }

        // For other keys, show popup keyboard if they have popup characters
        if (key.popupCharacters.isNullOrEmpty()) {
            return
        }

        // Show popup keyboard
        showPopupKeyboard(key)
    }

    /**
     * Show the popup keyboard for a key with popup characters.
     * Uses direct canvas drawing instead of PopupWindow for IME compatibility.
     */
    private fun showPopupKeyboard(key: Key) {
        val popupChars = key.popupCharacters ?: return
        if (popupChars.isEmpty()) return

        // Dismiss key preview
        keyPreviewPopup?.dismiss()

        // Store popup dimensions
        popupKeyWidth = key.width
        popupKeyHeight = key.height
        popupTotalWidth = popupChars.length * popupKeyWidth

        // Create popup keys from characters
        val keys = mutableListOf<Key>()
        for (i in popupChars.indices) {
            val char = popupChars[i]
            val newKey = Key(
                codes = intArrayOf(char.code),
                label = char.toString(),
                width = popupKeyWidth,
                height = popupKeyHeight,
                x = i * popupKeyWidth,
                y = 0
            )
            keys.add(newKey)
        }

        popupKeyboardKeys = keys
        popupKey = key
        selectedPopupKeyIndex = -1

        // Padding for the popup container
        val popupPadding = (6 * resources.displayMetrics.density).toInt()

        // Calculate popup position (centered above the key, but within view bounds)
        popupX = key.x + (key.width - popupTotalWidth) / 2

        // Try to position above the key, but if that would go above the view, position it overlapping the key
        val idealY = key.y - popupKeyHeight - 10
        popupY = if (idealY >= popupPadding) {
            idealY
        } else {
            // Position at top of keyboard with padding
            popupPadding
        }

        // Ensure popup doesn't go off screen horizontally (account for padding)
        popupX = popupX.coerceIn(popupPadding, (width - popupTotalWidth - popupPadding).coerceAtLeast(popupPadding))

        miniKeyboardOnScreen = true

        // Redraw to show popup
        invalidate()
    }

    /**
     * Handle touch move on popup keyboard.
     * Returns the selected key index or -1 if none.
     */
    private fun handlePopupKeyboardMove(x: Int, y: Int): Int {
        if (!miniKeyboardOnScreen || popupKeyboardKeys.isEmpty()) return -1

        // Check if touch is within popup bounds
        val relX = x - popupX
        val relY = y - popupY

        // Find which popup key is under the touch
        for ((index, key) in popupKeyboardKeys.withIndex()) {
            if (relX >= key.x && relX < key.x + key.width &&
                relY >= 0 && relY < popupKeyHeight) {
                if (selectedPopupKeyIndex != index) {
                    selectedPopupKeyIndex = index
                    // Redraw to show selection
                    invalidate()
                }
                return index
            }
        }

        // Touch is outside popup keys
        if (selectedPopupKeyIndex != -1) {
            selectedPopupKeyIndex = -1
            invalidate()
        }
        return -1
    }

    /**
     * Dismiss the popup keyboard.
     */
    fun dismissPopupKeyboard(): Boolean {
        if (miniKeyboardOnScreen) {
            popupKey = null
            popupKeyboardKeys = emptyList()
            selectedPopupKeyIndex = -1
            miniKeyboardOnScreen = false
            invalidate()
            return true
        }
        return false
    }

    /**
     * Get the selected popup key code, or -1 if none selected.
     */
    private fun getSelectedPopupKeyCode(): Int {
        if (selectedPopupKeyIndex >= 0 && selectedPopupKeyIndex < popupKeyboardKeys.size) {
            val key = popupKeyboardKeys[selectedPopupKeyIndex]
            return if (key.codes.isNotEmpty()) key.codes[0] else -1
        }
        return -1
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        handler.removeCallbacksAndMessages(null)
        keyPreviewPopup?.release()
        dismissPopupKeyboard()
    }

    /**
     * Gesture listener for swipe detection.
     */
    private inner class GestureListener : GestureDetector.SimpleOnGestureListener() {
        override fun onFling(
            e1: MotionEvent?,
            e2: MotionEvent,
            velocityX: Float,
            velocityY: Float
        ): Boolean {
            if (e1 == null) return false

            val deltaX = e2.x - e1.x
            val deltaY = e2.y - e1.y

            // Determine swipe direction
            if (abs(deltaX) > abs(deltaY) && abs(deltaX) > swipeThreshold) {
                if (deltaX > 0) {
                    keyboardActionListener?.swipeRight()
                } else {
                    keyboardActionListener?.swipeLeft()
                }
                return true
            } else if (abs(deltaY) > swipeThreshold) {
                if (deltaY > 0) {
                    keyboardActionListener?.swipeDown()
                } else {
                    keyboardActionListener?.swipeUp()
                }
                return true
            }
            return false
        }
    }

    /**
     * Handler for key repeat and long press.
     */
    private class KeyHandler(view: MuaKeyboardView) : Handler(Looper.getMainLooper()) {
        private val viewRef = WeakReference(view)

        override fun handleMessage(msg: Message) {
            val view = viewRef.get() ?: return
            when (msg.what) {
                MSG_REPEAT -> view.repeatKey()
                MSG_LONGPRESS -> {
                    val key = msg.obj as? Key
                    if (key != null) {
                        view.handleLongPress(key)
                    }
                }
            }
        }
    }
}
