package com.sanlin.mkeyboard.keyboard.parser

import android.content.Context
import android.content.res.Resources
import android.content.res.XmlResourceParser
import android.util.Log
import android.util.TypedValue
import com.sanlin.mkeyboard.keyboard.model.Key
import com.sanlin.mkeyboard.keyboard.model.Keyboard
import com.sanlin.mkeyboard.keyboard.model.Row
import org.xmlpull.v1.XmlPullParser

/**
 * Parser for keyboard XML layout files.
 * Parses the existing MUA-Keyboard XML format compatible with AOSP Keyboard XML.
 */
object KeyboardXmlParser {

    private const val TAG = "KeyboardXmlParser"

    // XML tag names
    private const val TAG_KEYBOARD = "Keyboard"
    private const val TAG_ROW = "Row"
    private const val TAG_KEY = "Key"

    // Android namespace
    private const val ANDROID_NS = "http://schemas.android.com/apk/res/android"

    /**
     * Parse a keyboard XML resource into a Keyboard model.
     */
    fun parse(context: Context, xmlLayoutResId: Int, keyboard: Keyboard) {
        val res = context.resources
        val parser = res.getXml(xmlLayoutResId)
        val displayMetrics = res.displayMetrics
        val displayWidth = displayMetrics.widthPixels

        var currentRow: Row? = null
        var currentX = 0
        var currentY = 0

        // Default values from the keyboard element
        var defaultKeyWidth = displayWidth / 10
        var defaultKeyHeight = (50 * displayMetrics.density).toInt()
        var defaultHorizontalGap = 0
        var defaultVerticalGap = 0

        Log.d(TAG, "Starting parse of keyboard resource $xmlLayoutResId, displayWidth=$displayWidth, defaultHeight=$defaultKeyHeight")

        try {
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                when (event) {
                    XmlPullParser.START_TAG -> {
                        when (parser.name) {
                            TAG_KEYBOARD -> {
                                // Parse keyboard attributes
                                defaultKeyWidth = getDimensionOrFraction(
                                    res, parser, ANDROID_NS, "keyWidth", displayWidth, displayWidth / 10
                                )
                                defaultKeyHeight = getDimensionOrFraction(
                                    res, parser, ANDROID_NS, "keyHeight", displayWidth,
                                    (50 * displayMetrics.density).toInt()
                                )
                                defaultHorizontalGap = getDimensionOrFraction(
                                    res, parser, ANDROID_NS, "horizontalGap", displayWidth, 0
                                )
                                defaultVerticalGap = getDimensionOrFraction(
                                    res, parser, ANDROID_NS, "verticalGap", displayWidth, 0
                                )

                                keyboard.setDimensions(
                                    defaultKeyWidth,
                                    defaultKeyHeight,
                                    defaultHorizontalGap,
                                    defaultVerticalGap
                                )

                                Log.d(TAG, "Keyboard attrs: width=$defaultKeyWidth, height=$defaultKeyHeight, hGap=$defaultHorizontalGap, vGap=$defaultVerticalGap")
                            }
                            TAG_ROW -> {
                                currentX = 0
                                currentRow = Row(
                                    defaultWidth = defaultKeyWidth,
                                    defaultHeight = defaultKeyHeight,
                                    defaultHorizontalGap = defaultHorizontalGap,
                                    verticalGap = defaultVerticalGap
                                )
                                // Parse row attributes
                                val rowEdgeFlags = parser.getAttributeValue(ANDROID_NS, "rowEdgeFlags")
                                if (rowEdgeFlags != null) {
                                    currentRow.rowEdgeFlags = parseEdgeFlags(rowEdgeFlags)
                                }
                            }
                            TAG_KEY -> {
                                currentRow?.let { row ->
                                    val key = parseKey(context, res, parser, row, currentX, currentY, displayWidth)
                                    // Apply the gap before this key, but skip for left edge keys
                                    val hasLeftEdge = (key.edgeFlags and Key.EDGE_LEFT) != 0
                                    if (hasLeftEdge) {
                                        key.x = currentX
                                    } else {
                                        key.x = currentX + key.gap
                                    }
                                    row.keys.add(key)
                                    // Move to the end of this key
                                    currentX = key.x + key.width
                                    Log.d(TAG, "Key: label=${key.label}, x=${key.x}, width=${key.width}, endX=${key.x + key.width}, displayWidth=$displayWidth")
                                }
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        when (parser.name) {
                            TAG_ROW -> {
                                currentRow?.let { row ->
                                    // Extend the last key (right edge) to fill the remaining space
                                    if (row.keys.isNotEmpty()) {
                                        val lastKey = row.keys.last()
                                        val hasRightEdge = (lastKey.edgeFlags and Key.EDGE_RIGHT) != 0
                                        val currentEndX = lastKey.x + lastKey.width
                                        Log.d(TAG, "Row end: lastKey.label=${lastKey.label}, x=${lastKey.x}, width=${lastKey.width}, endX=$currentEndX, displayWidth=$displayWidth, hasRightEdge=$hasRightEdge, edgeFlags=${lastKey.edgeFlags}")
                                        if (hasRightEdge && currentEndX < displayWidth) {
                                            val oldWidth = lastKey.width
                                            lastKey.width = displayWidth - lastKey.x
                                            Log.d(TAG, "Extended right edge key from width=$oldWidth to width=${lastKey.width}")
                                        } else if (currentEndX < displayWidth) {
                                            // Even without right edge flag, extend the last key to fill gap
                                            val oldWidth = lastKey.width
                                            lastKey.width = displayWidth - lastKey.x
                                            Log.d(TAG, "Extended last key (no right edge flag) from width=$oldWidth to width=${lastKey.width}")
                                        }
                                    }
                                    keyboard.addRow(row)
                                    Log.d(TAG, "Added row with ${row.keys.size} keys at Y=$currentY, height=${row.defaultHeight}")
                                    currentY += row.defaultHeight + row.verticalGap
                                }
                                currentRow = null
                            }
                        }
                    }
                }
                event = parser.next()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing keyboard XML", e)
        } finally {
            parser.close()
        }

        keyboard.finalizeParsing()
        Log.d(TAG, "Finished parsing: ${keyboard.keys.size} keys, totalHeight=${keyboard.totalHeight}, totalWidth=${keyboard.totalWidth}")
    }

    /**
     * Parse a Key element.
     */
    private fun parseKey(
        context: Context,
        res: Resources,
        parser: XmlResourceParser,
        row: Row,
        x: Int,
        y: Int,
        displayWidth: Int
    ): Key {
        val key = Key(
            width = row.defaultWidth,
            height = row.defaultHeight,
            gap = row.defaultHorizontalGap,
            x = x,
            y = y
        )

        // Parse codes
        val codesStr = parser.getAttributeValue(ANDROID_NS, "codes")
        if (codesStr != null) {
            key.codes = parseCodes(codesStr)
        }

        // Parse label
        val label = parser.getAttributeValue(ANDROID_NS, "keyLabel")
        if (label != null) {
            key.label = label
        }

        // Parse icon
        val iconResId = parser.getAttributeResourceValue(ANDROID_NS, "keyIcon", 0)
        if (iconResId != 0) {
            try {
                key.icon = context.getDrawable(iconResId)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to load key icon: $iconResId", e)
            }
        }

        // Parse key width (optional, uses row default if not specified)
        val keyWidthValue = parser.getAttributeValue(ANDROID_NS, "keyWidth")
        if (keyWidthValue != null) {
            key.width = getDimensionOrFraction(res, parser, ANDROID_NS, "keyWidth", displayWidth, row.defaultWidth)
        }

        // Parse key height (optional, uses row default if not specified)
        val keyHeightValue = parser.getAttributeValue(ANDROID_NS, "keyHeight")
        if (keyHeightValue != null) {
            key.height = getDimensionOrFraction(res, parser, ANDROID_NS, "keyHeight", displayWidth, row.defaultHeight)
        }

        // Parse horizontal gap (optional)
        val gapValue = parser.getAttributeValue(ANDROID_NS, "horizontalGap")
        if (gapValue != null) {
            key.gap = getDimensionOrFraction(res, parser, ANDROID_NS, "horizontalGap", displayWidth, row.defaultHorizontalGap)
        }

        // Parse boolean attributes
        val isModifier = parser.getAttributeValue(ANDROID_NS, "isModifier")
        key.modifier = isModifier == "true"

        val isSticky = parser.getAttributeValue(ANDROID_NS, "isSticky")
        key.sticky = isSticky == "true"

        val isRepeatable = parser.getAttributeValue(ANDROID_NS, "isRepeatable")
        key.repeatable = isRepeatable == "true"

        // Parse popup characters
        val popupChars = parser.getAttributeValue(ANDROID_NS, "popupCharacters")
        if (popupChars != null) {
            key.popupCharacters = popupChars
        }

        // Parse popup keyboard
        key.popupResId = parser.getAttributeResourceValue(ANDROID_NS, "popupKeyboard", 0)

        // Parse edge flags
        val edgeFlags = parser.getAttributeValue(ANDROID_NS, "keyEdgeFlags")
        if (edgeFlags != null) {
            key.edgeFlags = parseEdgeFlags(edgeFlags)
        }

        Log.d(TAG, "Parsed key: codes=${key.codes.contentToString()}, label=${key.label}, x=${key.x}, y=${key.y}, w=${key.width}, h=${key.height}")
        return key
    }

    /**
     * Get a dimension or fraction value from an attribute.
     * Handles @dimen/, @fraction/, percentages (e.g., "10%p"), and raw values.
     */
    private fun getDimensionOrFraction(
        res: Resources,
        parser: XmlResourceParser,
        namespace: String,
        attribute: String,
        base: Int,
        defaultValue: Int
    ): Int {
        val value = parser.getAttributeValue(namespace, attribute)
        Log.d(TAG, "getDimensionOrFraction: attr=$attribute, value=$value, namespace=$namespace")

        if (value == null) {
            Log.d(TAG, "  -> value is null, returning default=$defaultValue")
            return defaultValue
        }

        // Try to get as resource ID first
        val resId = parser.getAttributeResourceValue(namespace, attribute, 0)
        Log.d(TAG, "  -> resId=$resId (0x${Integer.toHexString(resId)})")

        if (resId != 0) {
            try {
                // Check if it's a fraction or dimension
                val typedValue = TypedValue()
                res.getValue(resId, typedValue, true)
                Log.d(TAG, "  -> typedValue.type=${typedValue.type}, TYPE_FRACTION=${TypedValue.TYPE_FRACTION}, TYPE_DIMENSION=${TypedValue.TYPE_DIMENSION}")

                return when (typedValue.type) {
                    TypedValue.TYPE_FRACTION -> {
                        // Fraction - calculate based on base
                        val fraction = typedValue.getFraction(base.toFloat(), base.toFloat())
                        Log.d(TAG, "  -> fraction result=${fraction.toInt()} (base=$base)")
                        fraction.toInt()
                    }
                    TypedValue.TYPE_DIMENSION -> {
                        // Dimension
                        val dimValue = res.getDimensionPixelSize(resId)
                        Log.d(TAG, "  -> dimension result=$dimValue")
                        dimValue
                    }
                    else -> {
                        // Try as dimension anyway
                        try {
                            val dimValue = res.getDimensionPixelSize(resId)
                            Log.d(TAG, "  -> fallback dimension result=$dimValue")
                            dimValue
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to get dimension for $attribute, using default", e)
                            defaultValue
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to parse resource for $attribute: $value", e)
                return defaultValue
            }
        }

        // Parse percentage value like "10%p"
        if (value.endsWith("%p")) {
            val percent = value.dropLast(2).toFloatOrNull() ?: return defaultValue
            val result = (base * percent / 100).toInt()
            Log.d(TAG, "  -> percentage result=$result (percent=$percent, base=$base)")
            return result
        }

        // Parse raw numeric value
        val numericValue = value.toFloatOrNull()
        if (numericValue != null) {
            Log.d(TAG, "  -> numeric result=${numericValue.toInt()}")
            return numericValue.toInt()
        }

        Log.d(TAG, "  -> no match, returning default=$defaultValue")
        return defaultValue
    }

    /**
     * Parse the codes attribute which can be comma-separated.
     */
    private fun parseCodes(value: String): IntArray {
        if (value.isEmpty()) return intArrayOf(0)

        val parts = value.split(",")
        return parts.map { part ->
            part.trim().toIntOrNull() ?: 0
        }.toIntArray()
    }

    /**
     * Parse edge flags from the attribute value.
     */
    private fun parseEdgeFlags(value: String): Int {
        var flags = 0
        if (value.contains("left")) flags = flags or Key.EDGE_LEFT
        if (value.contains("right")) flags = flags or Key.EDGE_RIGHT
        if (value.contains("top")) flags = flags or Key.EDGE_TOP
        if (value.contains("bottom")) flags = flags or Key.EDGE_BOTTOM
        return flags
    }
}
