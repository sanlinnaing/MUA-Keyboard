package com.sanlin.mkeyboard.view

import android.content.Context
import android.graphics.*
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import com.sanlin.mkeyboard.R
import com.sanlin.mkeyboard.config.KeyboardConfig
import com.sanlin.mkeyboard.keyboard.model.*
import java.lang.ref.WeakReference
import kotlin.math.abs

/**
 * Custom view for rendering and handling the Myanmar flick keyboard.
 *
 * Features:
 * - 4x3 grid of flick keys with 5 characters each
 * - Touch-and-flick gesture detection
 * - Directional visual feedback (shadow/mist + edge highlight bar on the key)
 * - Side columns with special keys (delete, space, enter, mode, globe)
 * - Theme support
 */
class FlickKeyboardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // Keyboard model
    private var flickKeyboard: FlickKeyboard? = null

    // Action listener
    private var actionListener: OnFlickKeyboardActionListener? = null

    // Drawing resources
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    // Colors (will be set from theme)
    private var keyBackgroundColor: Int = 0xFF3A3B45.toInt()
    private var keyPressedColor: Int = 0xFF5A5B65.toInt()
    private var keyTextColor: Int = 0xFFFFFFFF.toInt()
    private var hintTextColor: Int = 0xFFFFCC00.toInt()
    private var dividerColor: Int = 0xFF4A4B55.toInt()
    private var specialKeyBackgroundColor: Int = 0xFF5C5D6A.toInt()  // Special key color

    // Directional flick visual effect colors
    private val flickShadowColor: Int = 0x60000000  // Semi-transparent black for shadow/mist
    private val flickEdgeHighlightColor: Int = 0xFF6A9FE8.toInt()  // Blue highlight for edge bar

    // Paint for directional gradient effect
    private val gradientPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val edgePaint = Paint(Paint.ANTI_ALIAS_FLAG)

    // Text sizes
    private val centerTextSize: Float
    private val hintTextSize: Float

    // Touch tracking
    private var touchDownX: Float = 0f
    private var touchDownY: Float = 0f
    private var currentFlickKey: FlickKey? = null
    private var currentSpecialKey: Key? = null
    private var currentShiftedKey: Key? = null
    private var currentDirection: FlickDirection = FlickDirection.CENTER

    // Flick threshold (40dp converted to pixels)
    private val flickThreshold: Float

    // Key repeat
    private var repeatKey: Key? = null
    private val handler: Handler

    // Gesture detector for swipes
    private val gestureDetector: GestureDetector

    // Handler message types
    private companion object {
        const val MSG_REPEAT = 1
        const val MSG_LONGPRESS = 2
        const val MSG_PUNCTUATION_SINGLE_TAP = 3
        const val MSG_FLICK_KEY_LONGPRESS = 4

        // Myanmar punctuation characters
        const val LITTLE_SECTION = 0x104A  // ၊
        const val SECTION = 0x104B         // ။

        // Myanmar vowel characters
        const val AA_VOWEL = 0x102C        // ာ
        const val TALL_AA = 0x102B         // ါ

        // Double tap timing
        const val DOUBLE_TAP_TIMEOUT = 300L

        // Long press timing for flick keys
        const val FLICK_LONGPRESS_TIMEOUT = 400L
    }

    // Double tap tracking for punctuation key
    private var lastPunctuationTapTime: Long = 0
    private var pendingPunctuationTap: Boolean = false

    // Punctuation popup tracking
    private var showPunctuationPopup: Boolean = false
    private var punctuationPopupKey: Key? = null

    // Flick preview popup tracking
    private var showFlickPreview: Boolean = false
    private var flickPreviewLabel: String = ""

    // Long press and repeat timing
    private val longPressTimeout = 400L
    private val repeatInterval = 50L

    // Key corner radius
    private val cornerRadius: Float

    init {
        val density = resources.displayMetrics.density

        // Calculate sizes (reduced for smaller keys)
        centerTextSize = 20 * density
        hintTextSize = 11 * density
        flickThreshold = 24 * density
        cornerRadius = 6 * density

        // Load colors from theme attributes
        loadThemeColors(attrs)

        // Set up text paint
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.typeface = Typeface.DEFAULT

        // Initialize gesture detector
        gestureDetector = GestureDetector(context, GestureListener())

        // Initialize handler for key repeat
        handler = KeyHandler(this)

        // Make focusable
        isFocusable = true
        isFocusableInTouchMode = true
    }

    /**
     * Load theme colors from styled attributes.
     */
    private fun loadThemeColors(attrs: AttributeSet?) {
        val a = context.obtainStyledAttributes(attrs, R.styleable.MuaKeyboardView)

        keyTextColor = a.getColor(R.styleable.MuaKeyboardView_keyTextColor, 0xFFFFFFFF.toInt())
        hintTextColor = a.getColor(R.styleable.MuaKeyboardView_hintTextColor, 0xFFFFCC00.toInt())

        a.recycle()

        // Set additional colors based on theme
        updateColorsForTheme()
    }

    /**
     * Update colors based on current theme setting.
     */
    private fun updateColorsForTheme() {
        when (KeyboardConfig.getCurrentTheme()) {
            6 -> { // Light
                keyBackgroundColor = 0xFFE0E0E6.toInt()
                keyPressedColor = 0xFFC0C0C8.toInt()
                keyTextColor = 0xFF202020.toInt()
                dividerColor = 0xFFC8C8D0.toInt()
                hintTextColor = 0xFF808000.toInt()
                specialKeyBackgroundColor = 0xFFD0D0D8.toInt()
            }
            5 -> { // Golden Yellow
                keyBackgroundColor = 0xFF3D3525.toInt()
                keyPressedColor = 0xFF5D5535.toInt()
                dividerColor = 0xFF4D4535.toInt()
                specialKeyBackgroundColor = 0xFF4D4028.toInt()
            }
            4 -> { // Blue Gray
                keyBackgroundColor = 0xFF2D3045.toInt()
                keyPressedColor = 0xFF4D5065.toInt()
                dividerColor = 0xFF3D4055.toInt()
                specialKeyBackgroundColor = 0xFF3D4055.toInt()
            }
            3 -> { // Green
                keyBackgroundColor = 0xFF2D3D2D.toInt()
                keyPressedColor = 0xFF4D5D4D.toInt()
                dividerColor = 0xFF3D4D3D.toInt()
                specialKeyBackgroundColor = 0xFF3D4D3D.toInt()
            }
            2 -> { // Dark
                keyBackgroundColor = 0xFF202020.toInt()
                keyPressedColor = 0xFF404040.toInt()
                dividerColor = 0xFF303030.toInt()
                specialKeyBackgroundColor = 0xFF303030.toInt()
            }
            else -> { // Default (1)
                keyBackgroundColor = 0xFF3A3B45.toInt()
                keyPressedColor = 0xFF5A5B65.toInt()
                dividerColor = 0xFF4A4B55.toInt()
                specialKeyBackgroundColor = 0xFF5C5D6A.toInt()
            }
        }
    }

    /**
     * Set the flick keyboard to display.
     */
    fun setKeyboard(keyboard: FlickKeyboard) {
        flickKeyboard = keyboard
        updateColorsForTheme()
        requestLayout()
        invalidate()
    }

    /**
     * Set the action listener.
     */
    fun setOnFlickKeyboardActionListener(listener: OnFlickKeyboardActionListener) {
        actionListener = listener
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val keyboard = flickKeyboard
        if (keyboard != null) {
            setMeasuredDimension(keyboard.getWidth(), keyboard.getHeight())
        } else {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val keyboard = flickKeyboard ?: return

        // Update theme colors
        updateColorsForTheme()

        // Draw main area: either shifted keys or normal flick keys
        if (keyboard.isShifted) {
            for (key in keyboard.shiftedKeys) {
                drawShiftedKey(canvas, key)
            }
        } else {
            for (key in keyboard.flickKeys) {
                drawFlickKey(canvas, key)
            }
        }

        // Draw side keys (normal keys, left and right columns)
        for (key in keyboard.getAllSideKeys()) {
            drawSideKey(canvas, key)
        }

        // Draw punctuation popup if active
        if (showPunctuationPopup && punctuationPopupKey != null) {
            drawPunctuationPopup(canvas, punctuationPopupKey!!)
        }

        // Draw flick preview popup if active
        if (showFlickPreview && currentFlickKey != null) {
            drawFlickPreview(canvas, currentFlickKey!!)
        }
    }

    /**
     * Draw a single flick key with its center character and directional hints.
     */
    private fun drawFlickKey(canvas: Canvas, key: FlickKey) {
        val padding = 3f

        // Draw key background
        paint.style = Paint.Style.FILL
        paint.color = if (key.pressed) keyPressedColor else keyBackgroundColor

        val rect = RectF(
            key.x + padding,
            key.y + padding,
            key.x + key.width - padding,
            key.y + key.height - padding
        )
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, paint)

        // Draw directional visual effect if this key is being flicked
        if (key.pressed && key == currentFlickKey && currentDirection != FlickDirection.CENTER) {
            drawDirectionalEffect(canvas, key, currentDirection, rect)
        }

        // Skip drawing text for empty/placeholder keys
        if (key.center.label.isEmpty()) {
            return
        }

        // Draw center character (large)
        textPaint.color = keyTextColor
        textPaint.textSize = centerTextSize
        val centerX = key.x + key.width / 2f
        val centerY = key.y + key.height / 2f + centerTextSize / 3f
        canvas.drawText(key.center.label, centerX, centerY, textPaint)

        // Draw directional hints if enabled
        if (KeyboardConfig.isShowHintLabel()) {
            drawDirectionalHints(canvas, key)
        }
    }

    /**
     * Draw directional visual effect (triangle gradient pointing toward center + edge highlight bar).
     */
    private fun drawDirectionalEffect(canvas: Canvas, key: FlickKey, direction: FlickDirection, keyRect: RectF) {
        val shadowDepth = key.height * 0.45f  // How deep the triangle extends toward center
        val edgeBarThickness = 3f * resources.displayMetrics.density  // Edge highlight bar thickness

        val centerX = keyRect.centerX()
        val centerY = keyRect.centerY()

        // Save canvas state for clipping
        canvas.save()

        // Clip to key bounds (rounded rect)
        val clipPath = Path()
        clipPath.addRoundRect(keyRect, cornerRadius, cornerRadius, Path.Direction.CW)
        canvas.clipPath(clipPath)

        // Create triangle path for the gradient
        val trianglePath = Path()

        when (direction) {
            FlickDirection.UP -> {
                // Triangle: top-left, top-right, center pointing down
                trianglePath.moveTo(keyRect.left, keyRect.top)
                trianglePath.lineTo(keyRect.right, keyRect.top)
                trianglePath.lineTo(centerX, keyRect.top + shadowDepth)
                trianglePath.close()

                // Gradient from top edge toward center
                gradientPaint.shader = LinearGradient(
                    centerX, keyRect.top,
                    centerX, keyRect.top + shadowDepth,
                    flickShadowColor, Color.TRANSPARENT,
                    Shader.TileMode.CLAMP
                )
                canvas.drawPath(trianglePath, gradientPaint)

                // Edge highlight bar at top
                edgePaint.color = flickEdgeHighlightColor
                edgePaint.style = Paint.Style.FILL
                canvas.drawRect(keyRect.left, keyRect.top, keyRect.right, keyRect.top + edgeBarThickness, edgePaint)
            }
            FlickDirection.DOWN -> {
                // Triangle: bottom-left, bottom-right, center pointing up
                trianglePath.moveTo(keyRect.left, keyRect.bottom)
                trianglePath.lineTo(keyRect.right, keyRect.bottom)
                trianglePath.lineTo(centerX, keyRect.bottom - shadowDepth)
                trianglePath.close()

                // Gradient from bottom edge toward center
                gradientPaint.shader = LinearGradient(
                    centerX, keyRect.bottom,
                    centerX, keyRect.bottom - shadowDepth,
                    flickShadowColor, Color.TRANSPARENT,
                    Shader.TileMode.CLAMP
                )
                canvas.drawPath(trianglePath, gradientPaint)

                // Edge highlight bar at bottom
                edgePaint.color = flickEdgeHighlightColor
                edgePaint.style = Paint.Style.FILL
                canvas.drawRect(keyRect.left, keyRect.bottom - edgeBarThickness, keyRect.right, keyRect.bottom, edgePaint)
            }
            FlickDirection.LEFT -> {
                // Triangle: top-left, bottom-left, center pointing right
                trianglePath.moveTo(keyRect.left, keyRect.top)
                trianglePath.lineTo(keyRect.left, keyRect.bottom)
                trianglePath.lineTo(keyRect.left + shadowDepth, centerY)
                trianglePath.close()

                // Gradient from left edge toward center
                gradientPaint.shader = LinearGradient(
                    keyRect.left, centerY,
                    keyRect.left + shadowDepth, centerY,
                    flickShadowColor, Color.TRANSPARENT,
                    Shader.TileMode.CLAMP
                )
                canvas.drawPath(trianglePath, gradientPaint)

                // Edge highlight bar at left
                edgePaint.color = flickEdgeHighlightColor
                edgePaint.style = Paint.Style.FILL
                canvas.drawRect(keyRect.left, keyRect.top, keyRect.left + edgeBarThickness, keyRect.bottom, edgePaint)
            }
            FlickDirection.RIGHT -> {
                // Triangle: top-right, bottom-right, center pointing left
                trianglePath.moveTo(keyRect.right, keyRect.top)
                trianglePath.lineTo(keyRect.right, keyRect.bottom)
                trianglePath.lineTo(keyRect.right - shadowDepth, centerY)
                trianglePath.close()

                // Gradient from right edge toward center
                gradientPaint.shader = LinearGradient(
                    keyRect.right, centerY,
                    keyRect.right - shadowDepth, centerY,
                    flickShadowColor, Color.TRANSPARENT,
                    Shader.TileMode.CLAMP
                )
                canvas.drawPath(trianglePath, gradientPaint)

                // Edge highlight bar at right
                edgePaint.color = flickEdgeHighlightColor
                edgePaint.style = Paint.Style.FILL
                canvas.drawRect(keyRect.right - edgeBarThickness, keyRect.top, keyRect.right, keyRect.bottom, edgePaint)
            }
            else -> { /* CENTER - no effect */ }
        }

        // Restore canvas state
        canvas.restore()

        // Clear shader to avoid affecting other draws
        gradientPaint.shader = null
    }

    /**
     * Draw small directional hint characters around the center.
     * Hints are positioned with padding from edges to be closer to the center character.
     */
    private fun drawDirectionalHints(canvas: Canvas, key: FlickKey) {
        textPaint.color = hintTextColor
        textPaint.textSize = hintTextSize

        val centerX = key.x + key.width / 2f
        val centerY = key.y + key.height / 2f

        // Padding from edges - hints will be closer to center
        val horizontalPadding = key.width * 0.15f  // 15% from edge
        val verticalPadding = key.height * 0.12f   // 12% from edge

        // UP hint (closer to center)
        key.up?.let { char ->
            canvas.drawText(
                char.label,
                centerX,
                key.y + verticalPadding + hintTextSize,
                textPaint
            )
        }

        // DOWN hint (closer to center)
        key.down?.let { char ->
            canvas.drawText(
                char.label,
                centerX,
                key.y + key.height - verticalPadding,
                textPaint
            )
        }

        // LEFT hint (closer to center)
        key.left?.let { char ->
            textPaint.textAlign = Paint.Align.LEFT
            canvas.drawText(
                char.label,
                key.x + horizontalPadding,
                centerY + hintTextSize / 3f,
                textPaint
            )
            textPaint.textAlign = Paint.Align.CENTER
        }

        // RIGHT hint (closer to center)
        key.right?.let { char ->
            textPaint.textAlign = Paint.Align.RIGHT
            canvas.drawText(
                char.label,
                key.x + key.width - horizontalPadding,
                centerY + hintTextSize / 3f,
                textPaint
            )
            textPaint.textAlign = Paint.Align.CENTER
        }
    }

    /**
     * Draw a side key (normal key, not flick).
     * These are the left and right column keys with special key styling.
     */
    private fun drawSideKey(canvas: Canvas, key: Key) {
        val padding = 3f

        // Highlight shift key when shifted
        val isShiftActive = key.codes.isNotEmpty() &&
                key.codes[0] == Key.KEYCODE_SHIFT &&
                flickKeyboard?.isShifted == true

        // Use special key background for modifier/function keys, regular for others
        val bgColor = when {
            key.pressed -> keyPressedColor
            isShiftActive -> flickEdgeHighlightColor
            key.modifier -> specialKeyBackgroundColor
            else -> keyBackgroundColor
        }

        // Draw key background
        paint.style = Paint.Style.FILL
        paint.color = bgColor

        val rect = RectF(
            key.x + padding,
            key.y + padding,
            key.x + key.width - padding,
            key.y + key.height - padding
        )
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, paint)

        // Draw icon if present
        if (key.icon != null) {
            drawKeyIcon(canvas, key)
            return
        }

        // Skip drawing text for empty/placeholder keys
        if (key.label.isNullOrEmpty()) {
            return
        }

        // Draw label (smaller text for function keys like ?123)
        textPaint.color = keyTextColor
        textPaint.textSize = if (key.modifier) centerTextSize * 0.7f else centerTextSize
        val x = key.x + key.width / 2f
        val y = key.y + (key.height + textPaint.textSize - textPaint.descent()) / 2f
        canvas.drawText(key.label.toString(), x, y, textPaint)
    }

    /**
     * Draw a shifted key (simple centered text, no flick hints).
     */
    private fun drawShiftedKey(canvas: Canvas, key: Key) {
        val padding = 3f

        paint.style = Paint.Style.FILL
        paint.color = if (key.pressed) keyPressedColor else keyBackgroundColor

        val rect = RectF(
            key.x + padding,
            key.y + padding,
            key.x + key.width - padding,
            key.y + key.height - padding
        )
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, paint)

        if (key.label.isNullOrEmpty()) return

        textPaint.color = keyTextColor
        // Use smaller text for multi-character labels
        val label = key.label.toString()
        textPaint.textSize = if (label.length > 2) centerTextSize * 0.75f else centerTextSize
        val x = key.x + key.width / 2f
        val y = key.y + (key.height + textPaint.textSize - textPaint.descent()) / 2f
        canvas.drawText(label, x, y, textPaint)
    }

    /**
     * Draw a key icon centered in the key.
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
     * Draw the punctuation popup showing both ၊ and ။ options.
     * Popup appears above the key.
     */
    private fun drawPunctuationPopup(canvas: Canvas, key: Key) {
        val density = resources.displayMetrics.density
        val popupWidth = key.width * 2f
        val popupHeight = key.height * 1.2f
        val popupPadding = 8 * density

        // Position popup above the key, centered horizontally
        val popupX = key.x + key.width / 2f - popupWidth / 2f
        val popupY = key.y - popupHeight - 8 * density

        // Ensure popup stays within screen bounds
        val adjustedX = popupX.coerceIn(0f, width - popupWidth)
        val adjustedY = popupY.coerceAtLeast(0f)

        // Draw popup background with shadow
        paint.style = Paint.Style.FILL
        paint.color = 0xFF2A2A35.toInt()  // Dark background

        // Draw shadow
        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        shadowPaint.color = 0x40000000
        shadowPaint.style = Paint.Style.FILL
        canvas.drawRoundRect(
            adjustedX + 4 * density,
            adjustedY + 4 * density,
            adjustedX + popupWidth + 4 * density,
            adjustedY + popupHeight + 4 * density,
            cornerRadius * 1.5f,
            cornerRadius * 1.5f,
            shadowPaint
        )

        // Draw popup background
        val popupRect = RectF(adjustedX, adjustedY, adjustedX + popupWidth, adjustedY + popupHeight)
        canvas.drawRoundRect(popupRect, cornerRadius * 1.5f, cornerRadius * 1.5f, paint)

        // Draw border
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f * density
        paint.color = 0xFF4A4A55.toInt()
        canvas.drawRoundRect(popupRect, cornerRadius * 1.5f, cornerRadius * 1.5f, paint)
        paint.style = Paint.Style.FILL

        // Draw the two punctuation options
        val cellWidth = popupWidth / 2f
        val centerY = adjustedY + popupHeight / 2f

        // Draw ။ (single tap)
        textPaint.color = keyTextColor
        textPaint.textSize = centerTextSize * 1.3f
        val leftCenterX = adjustedX + cellWidth / 2f
        canvas.drawText("။", leftCenterX, centerY + textPaint.textSize / 3f, textPaint)

        // Draw "tap" label below
        textPaint.textSize = hintTextSize * 0.9f
        textPaint.color = hintTextColor
        canvas.drawText("tap", leftCenterX, adjustedY + popupHeight - popupPadding, textPaint)

        // Draw ၊ (double tap)
        textPaint.color = keyTextColor
        textPaint.textSize = centerTextSize * 1.3f
        val rightCenterX = adjustedX + cellWidth + cellWidth / 2f
        canvas.drawText("၊", rightCenterX, centerY + textPaint.textSize / 3f, textPaint)

        // Draw "×2" label below
        textPaint.textSize = hintTextSize * 0.9f
        textPaint.color = hintTextColor
        canvas.drawText("×2", rightCenterX, adjustedY + popupHeight - popupPadding, textPaint)

        // Draw vertical divider
        paint.color = 0xFF4A4A55.toInt()
        paint.strokeWidth = 1f * density
        canvas.drawLine(
            adjustedX + cellWidth,
            adjustedY + popupPadding * 2,
            adjustedX + cellWidth,
            adjustedY + popupHeight - popupPadding * 2,
            paint
        )
    }

    /**
     * Draw the flick preview popup above the currently touched flick key.
     * Shows the selected character in a floating bubble.
     */
    private fun drawFlickPreview(canvas: Canvas, key: FlickKey) {
        if (flickPreviewLabel.isEmpty()) return

        val density = resources.displayMetrics.density
        val popupSize = key.width * 0.7f
        val popupHeight = key.height * 0.75f
        val popupRadius = popupSize / 2f  // Fully rounded (pill/circle shape)
        val gap = 4 * density

        // Position popup above the key, centered horizontally
        val popupX = key.x + key.width / 2f - popupSize / 2f
        val popupY = key.y - popupHeight - gap

        // Ensure popup stays within screen bounds
        val adjustedX = popupX.coerceIn(0f, width - popupSize)
        val adjustedY = popupY.coerceAtLeast(0f)

        // Draw shadow
        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        shadowPaint.color = 0x30000000
        shadowPaint.style = Paint.Style.FILL
        canvas.drawRoundRect(
            adjustedX + 2 * density,
            adjustedY + 2 * density,
            adjustedX + popupSize + 2 * density,
            adjustedY + popupHeight + 2 * density,
            popupRadius, popupRadius,
            shadowPaint
        )

        // Draw popup background
        paint.style = Paint.Style.FILL
        paint.color = 0xFF2A2A35.toInt()
        val popupRect = RectF(adjustedX, adjustedY, adjustedX + popupSize, adjustedY + popupHeight)
        canvas.drawRoundRect(popupRect, popupRadius, popupRadius, paint)

        // Draw border matching flick edge highlight color
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.5f * density
        paint.color = flickEdgeHighlightColor
        canvas.drawRoundRect(popupRect, popupRadius, popupRadius, paint)
        paint.style = Paint.Style.FILL

        // Draw the character label
        textPaint.color = keyTextColor
        textPaint.textSize = centerTextSize * 1.1f
        val textX = adjustedX + popupSize / 2f
        val textY = adjustedY + (popupHeight + textPaint.textSize - textPaint.descent()) / 2f
        canvas.drawText(flickPreviewLabel, textX, textY, textPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        // Let gesture detector handle fling events
        gestureDetector.onTouchEvent(event)

        val touchX = event.x.toInt()
        val touchY = event.y.toInt()

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                handleTouchDown(touchX, touchY, event.x, event.y)
            }
            MotionEvent.ACTION_MOVE -> {
                handleTouchMove(event.x, event.y)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                handleTouchUp(event.action == MotionEvent.ACTION_CANCEL)
            }
        }

        return true
    }

    private fun handleTouchDown(touchX: Int, touchY: Int, rawX: Float, rawY: Float) {
        val keyboard = flickKeyboard ?: return

        touchDownX = rawX
        touchDownY = rawY

        // When shifted, check shifted keys in the main area
        if (keyboard.isShifted) {
            val shiftedKey = keyboard.getShiftedKeyAt(touchX, touchY)
            if (shiftedKey != null) {
                currentShiftedKey = shiftedKey
                currentFlickKey = null
                currentSpecialKey = null
                shiftedKey.pressed = true
                invalidate()
                return
            }
        }

        // Check if touching a flick key (center 3 columns)
        val flickKey = keyboard.getFlickKeyAt(touchX, touchY)
        if (flickKey != null) {
            currentFlickKey = flickKey
            currentSpecialKey = null
            flickKey.pressed = true
            currentDirection = FlickDirection.CENTER

            // Show flick preview with center character
            flickPreviewLabel = flickKey.center.label
            showFlickPreview = flickPreviewLabel.isNotEmpty()

            // Start long-press timer for flick keys that support it (e.g., ာ key)
            if (flickKey.center.code == AA_VOWEL) {
                val msg = handler.obtainMessage(MSG_FLICK_KEY_LONGPRESS, flickKey)
                handler.sendMessageDelayed(msg, FLICK_LONGPRESS_TIMEOUT)
            }

            // Notify listener
            actionListener?.onKeyDown(flickKey)

            invalidate()
            return
        }

        // Check if touching a side key (normal key, left/right columns)
        val sideKey = keyboard.getSideKeyAt(touchX, touchY)
        if (sideKey != null) {
            currentSpecialKey = sideKey  // Treat like special key
            currentFlickKey = null
            sideKey.pressed = true

            // Show popup for punctuation key
            val primaryCode = if (sideKey.codes.isNotEmpty()) sideKey.codes[0] else 0
            if (primaryCode == Key.KEYCODE_PUNCTUATION) {
                showPunctuationPopup = true
                punctuationPopupKey = sideKey
            }

            // Notify listener
            actionListener?.onKeyDown(sideKey)

            invalidate()
        }
    }

    private fun handleTouchMove(x: Float, y: Float) {
        val flickKey = currentFlickKey
        if (flickKey != null) {
            // Calculate flick direction
            val deltaX = x - touchDownX
            val deltaY = y - touchDownY
            val newDirection = FlickDirection.fromDelta(deltaX, deltaY, flickThreshold)

            if (newDirection != currentDirection) {
                currentDirection = newDirection
                // Cancel long-press if user starts flicking
                if (newDirection != FlickDirection.CENTER) {
                    handler.removeMessages(MSG_FLICK_KEY_LONGPRESS)
                }

                // Update flick preview label
                val character = flickKey.getCharacterForDirection(
                    newDirection,
                    flickKeyboard?.isShifted ?: false
                )
                flickPreviewLabel = character?.label ?: ""
                showFlickPreview = flickPreviewLabel.isNotEmpty()

                // Redraw to show directional effect on the key
                invalidate()
            }
        }
    }

    private fun handleTouchUp(cancelled: Boolean) {
        handler.removeMessages(MSG_REPEAT)
        handler.removeMessages(MSG_LONGPRESS)
        handler.removeMessages(MSG_FLICK_KEY_LONGPRESS)
        repeatKey = null

        // Handle shifted key release
        val shiftedKey = currentShiftedKey
        if (shiftedKey != null && !cancelled) {
            val text = shiftedKey.text?.toString()
            if (!text.isNullOrEmpty()) {
                actionListener?.onFlickShiftedKey(text)
            }
            shiftedKey.pressed = false
        }

        // Handle flick key release
        val flickKey = currentFlickKey
        if (flickKey != null && !cancelled) {
            val character = flickKey.getCharacterForDirection(
                currentDirection,
                flickKeyboard?.isShifted ?: false
            )

            if (character != null) {
                actionListener?.onFlickCharacter(character, currentDirection, flickKey)
            }

            actionListener?.onKeyUp(flickKey)
            flickKey.pressed = false
        }

        // Handle special key release
        val specialKey = currentSpecialKey
        if (specialKey != null && !cancelled) {
            val primaryCode = if (specialKey.codes.isNotEmpty()) specialKey.codes[0] else 0

            // Handle punctuation key with double-tap detection
            if (primaryCode == Key.KEYCODE_PUNCTUATION) {
                handlePunctuationKeyRelease(specialKey)
            } else {
                actionListener?.onSpecialKey(primaryCode, specialKey)
            }

            actionListener?.onKeyUp(specialKey)
            specialKey.pressed = false
        }

        // Reset state
        currentFlickKey = null
        currentSpecialKey = null
        currentShiftedKey = null
        currentDirection = FlickDirection.CENTER
        showPunctuationPopup = false
        punctuationPopupKey = null
        showFlickPreview = false
        flickPreviewLabel = ""

        invalidate()
    }

    /**
     * Handle key repeat for repeatable keys (like delete).
     */
    private fun repeatKey() {
        val key = repeatKey ?: return
        val primaryCode = if (key.codes.isNotEmpty()) key.codes[0] else 0

        actionListener?.onSpecialKeyRepeat(primaryCode, key)
        handler.sendEmptyMessageDelayed(MSG_REPEAT, repeatInterval)
    }

    /**
     * Handle long press on space key.
     */
    private fun handleLongPress(key: Key) {
        handler.removeMessages(MSG_REPEAT)
        repeatKey = null

        val primaryCode = if (key.codes.isNotEmpty()) key.codes[0] else 0
        if (primaryCode == 32) { // Space
            // Reset key state
            key.pressed = false
            currentSpecialKey = null
            invalidate()

            // Notify listener
            actionListener?.onSpaceLongPress()
        }
    }

    /**
     * Handle punctuation key release with double-tap detection.
     * Single tap: ။ (Section)
     * Double tap: ၊ (Little Section)
     */
    private fun handlePunctuationKeyRelease(key: Key) {
        val currentTime = System.currentTimeMillis()

        if (pendingPunctuationTap && (currentTime - lastPunctuationTapTime) < DOUBLE_TAP_TIMEOUT) {
            // Double tap detected - cancel pending single tap and send ၊
            handler.removeMessages(MSG_PUNCTUATION_SINGLE_TAP)
            pendingPunctuationTap = false

            // Delete the previously inserted ၊ and insert ။
            actionListener?.onPunctuationDoubleTap()
        } else {
            // First tap - schedule single tap action
            pendingPunctuationTap = true
            lastPunctuationTapTime = currentTime

            // Schedule the single tap action after timeout
            handler.sendEmptyMessageDelayed(MSG_PUNCTUATION_SINGLE_TAP, DOUBLE_TAP_TIMEOUT)
        }
    }

    /**
     * Execute the pending single tap for punctuation key.
     */
    private fun executePunctuationSingleTap() {
        if (pendingPunctuationTap) {
            pendingPunctuationTap = false
            // Send ။ (Section)
            actionListener?.onPunctuationSingleTap()
        }
    }

    /**
     * Handle long-press on a flick key.
     * For ာ key: output ါ instead.
     */
    private fun handleFlickKeyLongPress(flickKey: FlickKey) {
        // Only handle if this is still the current key and direction is CENTER
        if (flickKey == currentFlickKey && currentDirection == FlickDirection.CENTER) {
            when (flickKey.center.code) {
                AA_VOWEL -> {
                    // Long-press on ာ -> output ါ
                    actionListener?.onFlickKeyLongPress(flickKey, TALL_AA)

                    // Reset key state to prevent normal tap action
                    flickKey.pressed = false
                    currentFlickKey = null
                    showFlickPreview = false
                    flickPreviewLabel = ""
                    invalidate()
                }
            }
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        handler.removeCallbacksAndMessages(null)
    }

    /**
     * Gesture listener - swipe-to-switch is disabled for flick keyboard
     * since horizontal swipes are used for flick input (left/right characters).
     * Users can switch input methods via space long-press instead.
     */
    private inner class GestureListener : GestureDetector.SimpleOnGestureListener() {
        // Swipe handling disabled - conflicts with flick left/right gestures
        override fun onFling(
            e1: MotionEvent?,
            e2: MotionEvent,
            velocityX: Float,
            velocityY: Float
        ): Boolean {
            // Return false to not consume fling events
            // Flick direction detection is handled in handleTouchMove/handleTouchUp
            return false
        }
    }

    /**
     * Handler for key repeat and long press.
     */
    private class KeyHandler(view: FlickKeyboardView) : Handler(Looper.getMainLooper()) {
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
                MSG_PUNCTUATION_SINGLE_TAP -> view.executePunctuationSingleTap()
                MSG_FLICK_KEY_LONGPRESS -> {
                    val flickKey = msg.obj as? FlickKey
                    if (flickKey != null) {
                        view.handleFlickKeyLongPress(flickKey)
                    }
                }
            }
        }
    }
}
