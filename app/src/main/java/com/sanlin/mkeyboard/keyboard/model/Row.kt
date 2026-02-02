package com.sanlin.mkeyboard.keyboard.model

/**
 * Row data class representing a row of keys in the keyboard.
 * Simplified from AOSP Keyboard.Row for MUA-Keyboard needs.
 */
data class Row(
    /** The keys in this row. */
    val keys: MutableList<Key> = mutableListOf(),

    /** Default width of a key in this row. Width is expressed as a percentage of the screen width. */
    var defaultWidth: Int = 0,

    /** Default height of a key in this row. Height is expressed in pixels. */
    var defaultHeight: Int = 0,

    /** Default horizontal gap between keys in this row. Gap is expressed in pixels. */
    var defaultHorizontalGap: Int = 0,

    /** Vertical gap following this row. */
    var verticalGap: Int = 0,

    /** Edge flags for this row (top, bottom). */
    var rowEdgeFlags: Int = 0,

    /** The keyboard mode for this row (for different keyboard modes). */
    var mode: Int = 0
) {
    companion object {
        /** Row edge flag indicating this row is at the top of the keyboard. */
        const val ROW_EDGE_TOP = 0x01
        /** Row edge flag indicating this row is at the bottom of the keyboard. */
        const val ROW_EDGE_BOTTOM = 0x02
    }
}
