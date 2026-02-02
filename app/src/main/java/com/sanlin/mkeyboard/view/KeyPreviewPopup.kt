package com.sanlin.mkeyboard.view

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.PopupWindow
import android.widget.TextView
import com.sanlin.mkeyboard.keyboard.model.Key

/**
 * Popup window for key preview when a key is pressed.
 */
class KeyPreviewPopup(private val context: Context) {

    private var popupWindow: PopupWindow? = null
    private var previewTextView: TextView? = null

    private var previewOffsetX = 0
    private var previewOffsetY = 0

    init {
        createPreviewView()
    }

    private fun createPreviewView() {
        previewTextView = TextView(context).apply {
            setBackgroundColor(Color.parseColor("#333333"))
            setTextColor(Color.WHITE)
            textSize = 32f
            gravity = Gravity.CENTER
            setPadding(32, 24, 32, 24)
        }

        popupWindow = PopupWindow(
            previewTextView,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            isClippingEnabled = false
            isTouchable = false
        }
    }

    /**
     * Show the key preview popup for the given key.
     */
    fun show(key: Key, parentView: View, parentLocationOnScreen: IntArray) {
        val label = key.label
        if (label == null || label.isEmpty()) {
            dismiss()
            return
        }

        previewTextView?.text = label

        // Calculate position
        val popupX = parentLocationOnScreen[0] + key.x + key.width / 2 - 50
        val popupY = parentLocationOnScreen[1] + key.y - key.height - 20

        try {
            if (popupWindow?.isShowing == true) {
                popupWindow?.update(popupX, popupY, -1, -1)
            } else {
                popupWindow?.showAtLocation(parentView, Gravity.NO_GRAVITY, popupX, popupY)
            }
        } catch (e: Exception) {
            // Ignore popup errors
        }
    }

    /**
     * Dismiss the key preview popup.
     */
    fun dismiss() {
        try {
            popupWindow?.dismiss()
        } catch (e: Exception) {
            // Ignore dismiss errors
        }
    }

    /**
     * Set the preview offset for positioning.
     */
    fun setPreviewOffset(offsetX: Int, offsetY: Int) {
        previewOffsetX = offsetX
        previewOffsetY = offsetY
    }

    /**
     * Release resources.
     */
    fun release() {
        dismiss()
        popupWindow = null
        previewTextView = null
    }
}
