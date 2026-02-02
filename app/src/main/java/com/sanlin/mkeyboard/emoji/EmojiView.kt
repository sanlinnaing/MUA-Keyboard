package com.sanlin.mkeyboard.emoji

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.sanlin.mkeyboard.R
import com.sanlin.mkeyboard.config.KeyboardConfig

/**
 * Modern emoji keyboard view with category tabs and scrollable grid.
 */
class EmojiView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private var emojiClickListener: OnEmojiClickListener? = null
    private var backClickListener: OnClickListener? = null
    private var deleteClickListener: OnClickListener? = null

    private val categoryTabs = mutableListOf<TextView>()
    private var selectedCategoryIndex = 0  // Start with Recent if available, otherwise Smileys
    private var categoryIndexOffset = 0  // Offset when Recent is hidden
    private lateinit var emojiRecyclerView: RecyclerView
    private lateinit var emojiAdapter: EmojiAdapter
    private lateinit var tabContainer: LinearLayout
    private lateinit var backButton: TextView

    // Fixed height for emoji grid (number of rows * emoji size)
    private val emojiGridHeight: Int

    // Colors based on theme
    private val backgroundColor: Int
    private val tabBackgroundColor: Int
    private val tabSelectedColor: Int
    private val tabBorderColor: Int
    private val textColor: Int

    init {
        orientation = VERTICAL

        // Calculate fixed height for emoji grid (4 rows of emojis)
        val emojiSize = dpToPx(44)
        emojiGridHeight = emojiSize * 4 + dpToPx(16)  // 4 rows + padding

        // Get colors based on theme
        backgroundColor = getThemeBackgroundColor()
        val theme = KeyboardConfig.getCurrentTheme()
        val isLightTheme = theme == 6 || (theme == 1 && !isSystemInDarkMode())
        if (isLightTheme) {
            tabBackgroundColor = adjustBrightness(backgroundColor, 0.95f)
            tabSelectedColor = adjustBrightness(backgroundColor, 0.88f)
            tabBorderColor = adjustBrightness(backgroundColor, 0.70f)  // Darker border for light theme
            textColor = Color.BLACK
        } else {
            tabBackgroundColor = adjustBrightness(backgroundColor, 1.3f)
            tabSelectedColor = adjustBrightness(backgroundColor, 1.5f)
            tabBorderColor = adjustBrightness(backgroundColor, 2.2f)  // Lighter border for dark theme
            textColor = Color.WHITE
        }

        setBackgroundColor(backgroundColor)

        setupCategoryTabs()
        setupEmojiGrid()
        setupBottomBar()

        // Select Recent if available, otherwise Smileys
        val startIndex = if (RecentEmojiManager.hasRecentEmojis()) 0 else 1
        selectCategory(startIndex)
    }

    /**
     * Set the back button label based on current keyboard language.
     */
    fun setBackButtonLabel(label: String) {
        backButton.text = label
    }

    private fun getThemeBackgroundColor(): Int {
        return when (KeyboardConfig.getCurrentTheme()) {
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

    private fun isSystemInDarkMode(): Boolean {
        val nightModeFlags = resources.configuration.uiMode and
                android.content.res.Configuration.UI_MODE_NIGHT_MASK
        return nightModeFlags == android.content.res.Configuration.UI_MODE_NIGHT_YES
    }

    private fun adjustBrightness(color: Int, factor: Float): Int {
        val r = ((Color.red(color) * factor).toInt()).coerceIn(0, 255)
        val g = ((Color.green(color) * factor).toInt()).coerceIn(0, 255)
        val b = ((Color.blue(color) * factor).toInt()).coerceIn(0, 255)
        return Color.rgb(r, g, b)
    }

    private fun setupCategoryTabs() {
        // Create tab bar background with bottom border
        val tabBarBackground = GradientDrawable().apply {
            setColor(tabBackgroundColor)
        }

        val tabScrollView = HorizontalScrollView(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, dpToPx(48))
            isHorizontalScrollBarEnabled = false
            background = tabBarBackground
        }

        // Container with bottom border line
        val tabWrapper = LinearLayout(context).apply {
            orientation = VERTICAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        }

        tabContainer = LinearLayout(context).apply {
            orientation = HORIZONTAL
            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, dpToPx(46))
            setPadding(dpToPx(4), dpToPx(4), dpToPx(4), 0)
        }

        // Add all category tabs including Recent
        EmojiCategory.values().forEachIndexed { index, category ->
            val tab = TextView(context).apply {
                text = category.icon
                textSize = 20f
                gravity = Gravity.CENTER
                layoutParams = LayoutParams(dpToPx(42), dpToPx(42)).apply {
                    setMargins(dpToPx(2), 0, dpToPx(2), 0)
                }
                setPadding(dpToPx(6), dpToPx(6), dpToPx(6), dpToPx(6))
                setOnClickListener { selectCategory(index) }
            }
            categoryTabs.add(tab)
            tabContainer.addView(tab)
        }

        // Bottom border line
        val bottomBorder = View(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, dpToPx(2))
            setBackgroundColor(tabBorderColor)
        }

        tabWrapper.addView(tabContainer)
        tabWrapper.addView(bottomBorder)
        tabScrollView.addView(tabWrapper)
        addView(tabScrollView)
    }

    private fun setupEmojiGrid() {
        emojiRecyclerView = RecyclerView(context).apply {
            // Use fixed height for scrollable grid
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, emojiGridHeight)
            setBackgroundColor(backgroundColor)

            // Calculate number of columns based on screen width
            val screenWidth = resources.displayMetrics.widthPixels
            val emojiSize = dpToPx(44)
            val columns = (screenWidth / emojiSize).coerceIn(6, 10)

            layoutManager = GridLayoutManager(context, columns)
            setPadding(dpToPx(4), dpToPx(4), dpToPx(4), dpToPx(4))
            clipToPadding = false
        }

        emojiAdapter = EmojiAdapter { emoji ->
            // Save to recent emojis
            RecentEmojiManager.addEmoji(emoji)
            // Notify listener
            emojiClickListener?.onEmojiClick(emoji)
        }
        emojiRecyclerView.adapter = emojiAdapter

        addView(emojiRecyclerView)
    }

    private fun setupBottomBar() {
        val bottomBar = LinearLayout(context).apply {
            orientation = HORIZONTAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, dpToPx(50))
            setBackgroundColor(tabBackgroundColor)
            gravity = Gravity.CENTER_VERTICAL
        }

        // Back button (ABC or language-specific)
        backButton = TextView(context).apply {
            text = "ABC"  // Default, will be updated based on language
            setTextColor(textColor)
            textSize = 14f
            gravity = Gravity.CENTER
            layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, 1f)
            setOnClickListener { backClickListener?.onClick(it) }
        }

        // Space bar
        val spaceBar = View(context).apply {
            layoutParams = LayoutParams(0, dpToPx(36), 3f).also {
                it.setMargins(dpToPx(8), dpToPx(7), dpToPx(8), dpToPx(7))
            }
            setBackgroundColor(adjustBrightness(tabBackgroundColor, 1.3f))
        }

        // Delete button
        val deleteButton = FrameLayout(context).apply {
            layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, 1f)
            setOnClickListener { deleteClickListener?.onClick(it) }
        }

        // Try to load delete icon, fallback to text
        try {
            val deleteIcon = ContextCompat.getDrawable(context, R.drawable.sym_keyboard_delete)
            deleteIcon?.setTint(textColor)
            val iconView = android.widget.ImageView(context).apply {
                setImageDrawable(deleteIcon)
                layoutParams = FrameLayout.LayoutParams(dpToPx(24), dpToPx(24), Gravity.CENTER)
            }
            deleteButton.addView(iconView)
        } catch (e: Exception) {
            val textView = TextView(context).apply {
                text = "⌫"
                setTextColor(textColor)
                textSize = 20f
                gravity = Gravity.CENTER
                layoutParams = FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
            }
            deleteButton.addView(textView)
        }

        bottomBar.addView(backButton)
        bottomBar.addView(spaceBar)
        bottomBar.addView(deleteButton)
        addView(bottomBar)
    }

    private fun selectCategory(index: Int) {
        selectedCategoryIndex = index

        // Update tab selection visuals
        categoryTabs.forEachIndexed { i, tab ->
            if (i == index) {
                // Selected tab: background with border on all sides
                tab.background = createSelectedTabBackground()
            } else {
                // Unselected tab: transparent background
                tab.setBackgroundColor(Color.TRANSPARENT)
            }
        }

        // Update emoji grid
        val category = EmojiCategory.values()[index]
        val emojis = if (index == 0) {
            // Recent category - load from manager
            RecentEmojiManager.getRecentEmojis()
        } else {
            category.emojis
        }
        emojiAdapter.setEmojis(emojis)
        emojiRecyclerView.scrollToPosition(0)
    }

    /**
     * Create a drawable for the selected tab with border.
     */
    private fun createSelectedTabBackground(): GradientDrawable {
        return GradientDrawable().apply {
            setColor(tabSelectedColor)
            setStroke(dpToPx(2), tabBorderColor)
            cornerRadius = dpToPx(6).toFloat()
        }
    }

    fun setOnEmojiClickListener(listener: OnEmojiClickListener) {
        emojiClickListener = listener
    }

    fun setOnBackClickListener(listener: OnClickListener) {
        backClickListener = listener
    }

    fun setOnDeleteClickListener(listener: OnClickListener) {
        deleteClickListener = listener
    }

    private fun dpToPx(dp: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp.toFloat(),
            resources.displayMetrics
        ).toInt()
    }

    interface OnEmojiClickListener {
        fun onEmojiClick(emoji: String)
    }

    /**
     * Adapter for emoji grid
     */
    private inner class EmojiAdapter(
        private val onClick: (String) -> Unit
    ) : RecyclerView.Adapter<EmojiAdapter.EmojiViewHolder>() {

        private var emojis: List<String> = emptyList()

        fun setEmojis(newEmojis: List<String>) {
            emojis = newEmojis
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EmojiViewHolder {
            val textView = TextView(parent.context).apply {
                layoutParams = RecyclerView.LayoutParams(
                    RecyclerView.LayoutParams.MATCH_PARENT,
                    dpToPx(44)
                )
                gravity = Gravity.CENTER
                textSize = 24f
                setBackgroundColor(Color.TRANSPARENT)
            }
            return EmojiViewHolder(textView)
        }

        override fun onBindViewHolder(holder: EmojiViewHolder, position: Int) {
            holder.bind(emojis[position])
        }

        override fun getItemCount(): Int = emojis.size

        inner class EmojiViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val textView = itemView as TextView

            fun bind(emoji: String) {
                textView.text = emoji
                textView.setOnClickListener { onClick(emoji) }
            }
        }
    }
}
