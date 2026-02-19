package com.sanlin.mkeyboard.keyboard.data

import android.content.Context
import android.content.res.XmlResourceParser
import com.sanlin.mkeyboard.R
import com.sanlin.mkeyboard.keyboard.model.FlickCharacter
import com.sanlin.mkeyboard.keyboard.model.FlickKey
import org.xmlpull.v1.XmlPullParser

/**
 * Parser for flick keyboard XML layout files.
 *
 * XML format:
 * <FlickKeyboard>
 *     <Row>
 *         <FlickKey
 *             app:centerCodes="4096" app:centerLabel="က"
 *             app:upCodes="4097" app:upLabel="ခ"
 *             app:downCodes="4098" app:downLabel="ဂ"
 *             app:leftCodes="4099" app:leftLabel="ဃ"
 *             app:rightCodes="4100" app:rightLabel="င" />
 *     </Row>
 * </FlickKeyboard>
 *
 * For multi-character output, use comma-separated codes:
 *     app:centerCodes="4141,4143" app:centerLabel="ို"
 */
object FlickKeyboardParser {

    private const val TAG_FLICK_KEYBOARD = "FlickKeyboard"
    private const val TAG_ROW = "Row"
    private const val TAG_FLICK_KEY = "FlickKey"

    /**
     * Parse a flick keyboard XML resource and return a list of FlickKey objects.
     *
     * @param context The context to access resources
     * @param xmlResId The XML resource ID (e.g., R.xml.my_flick)
     * @return List of FlickKey objects (12 keys for 4 rows x 3 columns)
     */
    fun parse(context: Context, xmlResId: Int): List<FlickKey> {
        val flickKeys = mutableListOf<FlickKey>()
        val parser = context.resources.getXml(xmlResId)

        try {
            var eventType = parser.eventType

            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        when (parser.name) {
                            TAG_FLICK_KEY -> {
                                val flickKey = parseFlickKey(context, parser)
                                flickKeys.add(flickKey)
                            }
                        }
                    }
                }
                eventType = parser.next()
            }
        } finally {
            parser.close()
        }

        return flickKeys
    }

    /**
     * Parse a single FlickKey from the XML parser.
     */
    private fun parseFlickKey(context: Context, parser: XmlResourceParser): FlickKey {
        val attrs = context.obtainStyledAttributes(
            parser,
            R.styleable.FlickKey
        )

        try {
            // Parse center (required)
            val centerCodes = attrs.getString(R.styleable.FlickKey_centerCodes) ?: ""
            val centerLabel = attrs.getString(R.styleable.FlickKey_centerLabel) ?: ""
            val centerIconResId = attrs.getResourceId(R.styleable.FlickKey_centerIcon, 0)
            val center = parseFlickCharacter(centerCodes, centerLabel, centerIconResId)
                ?: FlickCharacter(0, "")  // Fallback for empty key

            // Parse up (optional)
            val upCodes = attrs.getString(R.styleable.FlickKey_upCodes)
            val upLabel = attrs.getString(R.styleable.FlickKey_upLabel)
            val up = parseFlickCharacter(upCodes, upLabel)

            // Parse down (optional)
            val downCodes = attrs.getString(R.styleable.FlickKey_downCodes)
            val downLabel = attrs.getString(R.styleable.FlickKey_downLabel)
            val down = parseFlickCharacter(downCodes, downLabel)

            // Parse left (optional)
            val leftCodes = attrs.getString(R.styleable.FlickKey_leftCodes)
            val leftLabel = attrs.getString(R.styleable.FlickKey_leftLabel)
            val leftIconResId = attrs.getResourceId(R.styleable.FlickKey_leftIcon, 0)
            val left = parseFlickCharacter(leftCodes, leftLabel, leftIconResId)

            // Parse right (optional)
            val rightCodes = attrs.getString(R.styleable.FlickKey_rightCodes)
            val rightLabel = attrs.getString(R.styleable.FlickKey_rightLabel)
            val right = parseFlickCharacter(rightCodes, rightLabel)

            return FlickKey(
                center = center,
                up = up,
                down = down,
                left = left,
                right = right
            )
        } finally {
            attrs.recycle()
        }
    }

    /**
     * Parse codes string and label into a FlickCharacter.
     *
     * @param codesStr Comma-separated decimal code points (e.g., "4096" or "4141,4143")
     * @param label Display label for the character
     * @return FlickCharacter or null if input is invalid
     */
    private fun parseFlickCharacter(codesStr: String?, label: String?, iconResId: Int = 0): FlickCharacter? {
        if (codesStr.isNullOrBlank()) {
            return null
        }
        // Allow empty label if icon is provided
        if (label.isNullOrBlank() && iconResId == 0) {
            return null
        }

        val codes = parseCodes(codesStr)
        if (codes.isEmpty()) {
            return null
        }

        // Primary code is the first one
        val primaryCode = codes[0]

        // For multi-character sequences, label already contains the combined display
        return FlickCharacter(
            code = primaryCode,
            label = label ?: "",
            codes = if (codes.size > 1) codes else null,
            iconResId = iconResId
        )
    }

    /**
     * Parse comma-separated code points string into IntArray.
     *
     * @param codesStr e.g., "4096" or "4141,4143"
     * @return IntArray of code points
     */
    private fun parseCodes(codesStr: String): IntArray {
        return codesStr.split(",")
            .mapNotNull { it.trim().toIntOrNull() }
            .toIntArray()
    }
}
