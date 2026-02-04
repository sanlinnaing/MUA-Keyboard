package com.sanlin.mkeyboard.view

import android.content.Context
import android.graphics.*
import com.sanlin.mkeyboard.keyboard.model.FlickDirection
import com.sanlin.mkeyboard.keyboard.model.FlickKey

/**
 * Cross-shaped preview popup for flick keyboard.
 *
 * Displays all 5 characters available on a flick key in a cross pattern:
 *
 *        [UP]
 *   [LEFT][CENTER][RIGHT]
 *        [DOWN]
 *
 * The currently selected direction is highlighted as the user drags their finger.
 * This class provides drawing utilities used by FlickKeyboardView to render
 * the preview directly on its canvas (avoiding PopupWindow issues in IME context).
 */
class FlickPreviewPopup(private val context: Context) {

    // Cell dimensions
    private val cellSize: Int
    private val centerCellSize: Int
    private val padding: Int
    private val cornerRadius: Float

    // Colors
    private val backgroundColor = 0xFF2D2D35.toInt()
    private val cellBackgroundColor = 0xFF4A4B55.toInt()
    private val selectedCellColor = 0xFF6A9FE8.toInt()  // Blue highlight
    private val textColor = 0xFFFFFFFF.toInt()
    private val borderColor = 0xFF5A5B65.toInt()

    // Text sizes
    private val centerTextSize: Float
    private val directionTextSize: Float

    // Paints
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = textColor
        textAlign = Paint.Align.CENTER
    }

    // State
    var isVisible: Boolean = false
        private set
    var currentKey: FlickKey? = null
        private set
    var highlightedDirection: FlickDirection = FlickDirection.CENTER
        private set

    // Position
    var popupX: Int = 0
        private set
    var popupY: Int = 0
        private set

    init {
        val density = context.resources.displayMetrics.density

        // Cell sizes
        cellSize = (52 * density).toInt()
        centerCellSize = (60 * density).toInt()
        padding = (8 * density).toInt()
        cornerRadius = 12 * density

        // Text sizes
        centerTextSize = 28 * density
        directionTextSize = 22 * density
    }

    /**
     * Get the total width of the preview popup.
     */
    fun getTotalWidth(): Int {
        // Width: LEFT + CENTER + RIGHT + padding
        return cellSize + centerCellSize + cellSize + padding * 4
    }

    /**
     * Get the total height of the preview popup.
     */
    fun getTotalHeight(): Int {
        // Height: UP + CENTER + DOWN + padding
        return cellSize + centerCellSize + cellSize + padding * 4
    }

    /**
     * Show the preview for a flick key.
     *
     * @param key The FlickKey to preview
     * @param keyX The X position of the key on screen
     * @param keyY The Y position of the key on screen
     * @param keyWidth The width of the key
     */
    fun show(key: FlickKey, keyX: Int, keyY: Int, keyWidth: Int) {
        currentKey = key
        highlightedDirection = FlickDirection.CENTER
        isVisible = true

        // Center popup above the key
        val popupWidth = getTotalWidth()
        val popupHeight = getTotalHeight()

        popupX = keyX + (keyWidth - popupWidth) / 2
        popupY = keyY - popupHeight - padding

        // Position will be clamped in draw() method based on actual view width
    }

    /**
     * Update the highlighted direction based on touch position.
     */
    fun updateHighlight(direction: FlickDirection) {
        highlightedDirection = direction
    }

    /**
     * Dismiss the preview.
     */
    fun dismiss() {
        isVisible = false
        currentKey = null
    }

    /**
     * Draw the preview popup on the given canvas.
     *
     * @param canvas The canvas to draw on
     * @param viewWidth The width of the view (for position clamping)
     */
    fun draw(canvas: Canvas, viewWidth: Int) {
        val key = currentKey ?: return
        if (!isVisible) return

        // Clamp popup position to view bounds
        val popupWidth = getTotalWidth()
        val clampedX = popupX.coerceIn(padding, viewWidth - popupWidth - padding)
        val clampedY = popupY.coerceAtLeast(padding)

        // Draw shadow
        paint.style = Paint.Style.FILL
        paint.color = 0x44000000
        canvas.drawRoundRect(
            RectF(
                (clampedX + 4).toFloat(),
                (clampedY + 6).toFloat(),
                (clampedX + popupWidth + 4).toFloat(),
                (clampedY + getTotalHeight() + 6).toFloat()
            ),
            cornerRadius, cornerRadius, paint
        )

        // Draw background
        paint.color = backgroundColor
        val bgRect = RectF(
            clampedX.toFloat(),
            clampedY.toFloat(),
            (clampedX + popupWidth).toFloat(),
            (clampedY + getTotalHeight()).toFloat()
        )
        canvas.drawRoundRect(bgRect, cornerRadius, cornerRadius, paint)

        // Draw border
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.5f * context.resources.displayMetrics.density
        paint.color = borderColor
        canvas.drawRoundRect(bgRect, cornerRadius, cornerRadius, paint)
        paint.style = Paint.Style.FILL

        // Calculate cell positions
        val centerX = clampedX + cellSize + padding * 2
        val centerY = clampedY + cellSize + padding * 2

        // Draw UP cell
        key.up?.let { char ->
            drawCell(
                canvas,
                centerX + (centerCellSize - cellSize) / 2,
                clampedY + padding,
                cellSize,
                cellSize,
                char.label,
                directionTextSize,
                FlickDirection.UP == highlightedDirection
            )
        }

        // Draw LEFT cell
        key.left?.let { char ->
            drawCell(
                canvas,
                clampedX + padding,
                centerY + (centerCellSize - cellSize) / 2,
                cellSize,
                cellSize,
                char.label,
                directionTextSize,
                FlickDirection.LEFT == highlightedDirection
            )
        }

        // Draw CENTER cell (larger)
        drawCell(
            canvas,
            centerX,
            centerY,
            centerCellSize,
            centerCellSize,
            key.center.label,
            centerTextSize,
            FlickDirection.CENTER == highlightedDirection
        )

        // Draw RIGHT cell
        key.right?.let { char ->
            drawCell(
                canvas,
                centerX + centerCellSize + padding,
                centerY + (centerCellSize - cellSize) / 2,
                cellSize,
                cellSize,
                char.label,
                directionTextSize,
                FlickDirection.RIGHT == highlightedDirection
            )
        }

        // Draw DOWN cell
        key.down?.let { char ->
            drawCell(
                canvas,
                centerX + (centerCellSize - cellSize) / 2,
                centerY + centerCellSize + padding,
                cellSize,
                cellSize,
                char.label,
                directionTextSize,
                FlickDirection.DOWN == highlightedDirection
            )
        }
    }

    /**
     * Draw a single cell in the preview.
     */
    private fun drawCell(
        canvas: Canvas,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        label: String,
        textSize: Float,
        isHighlighted: Boolean
    ) {
        val cellRect = RectF(
            x.toFloat(),
            y.toFloat(),
            (x + width).toFloat(),
            (y + height).toFloat()
        )

        // Draw cell background
        paint.style = Paint.Style.FILL
        paint.color = if (isHighlighted) selectedCellColor else cellBackgroundColor
        canvas.drawRoundRect(cellRect, cornerRadius - 4, cornerRadius - 4, paint)

        // Draw label
        textPaint.textSize = textSize
        val textX = x + width / 2f
        val textY = y + (height + textPaint.textSize - textPaint.descent()) / 2f
        canvas.drawText(label, textX, textY, textPaint)
    }

    /**
     * Release resources.
     */
    fun release() {
        dismiss()
    }
}
