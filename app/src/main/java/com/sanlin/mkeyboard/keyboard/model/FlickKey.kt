package com.sanlin.mkeyboard.keyboard.model

/**
 * Data model representing a single flick key with 5 characters accessible via tap and flick gestures.
 *
 * The key layout follows the Japanese-style flick input pattern:
 * - Center: Tap (no flick)
 * - Up/Down/Left/Right: Flick in that direction
 */
data class FlickKey(
    val center: FlickCharacter,
    val up: FlickCharacter? = null,
    val down: FlickCharacter? = null,
    val left: FlickCharacter? = null,
    val right: FlickCharacter? = null,
    var x: Int = 0,
    var y: Int = 0,
    var width: Int = 0,
    var height: Int = 0,
    var pressed: Boolean = false
) {
    /**
     * Get the character for a given flick direction, optionally using shifted variant.
     *
     * @param direction The flick direction
     * @param shifted Whether to use shifted character variant
     * @return The FlickCharacter for the direction, or null if none defined
     */
    fun getCharacterForDirection(direction: FlickDirection, shifted: Boolean = false): FlickCharacter? {
        val char = when (direction) {
            FlickDirection.CENTER -> center
            FlickDirection.UP -> up
            FlickDirection.DOWN -> down
            FlickDirection.LEFT -> left
            FlickDirection.RIGHT -> right
        }

        // If shifted and character has shifted variant, return a modified character
        if (shifted && char != null && char.shiftedCode != null) {
            return FlickCharacter(
                code = char.shiftedCode,
                label = char.shiftedLabel ?: char.label
            )
        }
        return char
    }

    /**
     * Check if a touch point is inside this key's bounds.
     */
    fun isInside(touchX: Int, touchY: Int): Boolean {
        return touchX >= x && touchX < x + width &&
                touchY >= y && touchY < y + height
    }

    /**
     * Get all non-null characters for this key (for preview display).
     */
    fun getAllCharacters(): Map<FlickDirection, FlickCharacter> {
        val result = mutableMapOf<FlickDirection, FlickCharacter>()
        result[FlickDirection.CENTER] = center
        up?.let { result[FlickDirection.UP] = it }
        down?.let { result[FlickDirection.DOWN] = it }
        left?.let { result[FlickDirection.LEFT] = it }
        right?.let { result[FlickDirection.RIGHT] = it }
        return result
    }
}

/**
 * Represents a single character that can be input from a flick key.
 *
 * @param code The Unicode code point to input
 * @param label The display label for the character
 * @param shiftedCode Optional alternate code when shift is active
 * @param shiftedLabel Optional alternate label when shift is active
 * @param codes Optional array of codes for multi-character sequences (e.g., ို)
 */
data class FlickCharacter(
    val code: Int,
    val label: String,
    val shiftedCode: Int? = null,
    val shiftedLabel: String? = null,
    val codes: IntArray? = null  // For multi-character sequences
) {
    /**
     * Get the text to commit for this character.
     * For multi-character sequences, builds string from all codes.
     */
    fun getCommitText(): String {
        return if (codes != null && codes.isNotEmpty()) {
            // Multi-character sequence
            buildString {
                for (c in codes) {
                    append(String(Character.toChars(c)))
                }
            }
        } else {
            // Single character
            String(Character.toChars(code))
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as FlickCharacter
        if (code != other.code) return false
        if (label != other.label) return false
        if (codes != null) {
            if (other.codes == null) return false
            if (!codes.contentEquals(other.codes)) return false
        } else if (other.codes != null) return false
        return true
    }

    override fun hashCode(): Int {
        var result = code
        result = 31 * result + label.hashCode()
        result = 31 * result + (codes?.contentHashCode() ?: 0)
        return result
    }

    companion object {
        /**
         * Create a FlickCharacter from a single Unicode code point.
         */
        fun fromCodePoint(code: Int): FlickCharacter {
            return FlickCharacter(
                code = code,
                label = String(Character.toChars(code))
            )
        }

        /**
         * Create a FlickCharacter from a single character.
         */
        fun fromChar(char: Char): FlickCharacter {
            return FlickCharacter(
                code = char.code,
                label = char.toString()
            )
        }

        /**
         * Create a FlickCharacter from multiple code points.
         */
        fun fromCodes(codes: IntArray, label: String): FlickCharacter {
            return FlickCharacter(
                code = if (codes.isNotEmpty()) codes[0] else 0,
                label = label,
                codes = codes
            )
        }

        /**
         * Create a FlickCharacter from a string (for multi-character sequences).
         */
        fun fromString(text: String): FlickCharacter {
            return FlickCharacter(
                code = if (text.isNotEmpty()) text.codePointAt(0) else 0,
                label = text
            )
        }
    }
}

/**
 * Enum representing the five possible flick directions.
 */
enum class FlickDirection {
    CENTER,
    UP,
    DOWN,
    LEFT,
    RIGHT;

    companion object {
        /**
         * Determine the flick direction from delta X and Y values.
         *
         * @param deltaX Horizontal displacement from touch start
         * @param deltaY Vertical displacement from touch start
         * @param threshold Minimum distance to register as a flick (in pixels)
         * @return The detected flick direction
         */
        fun fromDelta(deltaX: Float, deltaY: Float, threshold: Float): FlickDirection {
            val distance = kotlin.math.sqrt(deltaX * deltaX + deltaY * deltaY)

            // If within threshold, it's a tap (center)
            if (distance < threshold) {
                return CENTER
            }

            // Calculate angle in degrees (-180 to 180)
            val angle = kotlin.math.atan2(deltaY.toDouble(), deltaX.toDouble()) * 180.0 / kotlin.math.PI

            // Determine direction based on angle
            // Right: -45 to 45
            // Down: 45 to 135
            // Left: 135 to 180 or -180 to -135
            // Up: -135 to -45
            return when {
                angle >= -45 && angle < 45 -> RIGHT
                angle >= 45 && angle < 135 -> DOWN
                angle >= -135 && angle < -45 -> UP
                else -> LEFT
            }
        }
    }
}
