// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.keyboard.emoji

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.RippleDrawable
import android.text.TextUtils
import android.util.AttributeSet
import android.view.Gravity
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import helium314.keyboard.latin.R
import helium314.keyboard.latin.common.ColorType
import helium314.keyboard.latin.common.Colors

/** Small native views, styled with the actual keyboard palette rather than the Activity theme. */
class EmojiMediaTabsView(context: Context, attrs: AttributeSet? = null) : LinearLayout(context, attrs) {
    fun interface Listener { fun onMediaClick(kind: String) }

    private val track = LinearLayout(context).apply {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(4), dp(4), dp(4), dp(4))
    }

    init {
        orientation = VERTICAL
        setPadding(dp(12), dp(4), dp(12), dp(4))
        addView(track, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    fun bind(colors: Colors, listener: Listener) {
        if (track.childCount != 0) return
        val surface = ColorUtils.blendARGB(colors.get(ColorType.MAIN_BACKGROUND), colors.get(ColorType.KEY_BACKGROUND), 0.55f)
        track.background = pill(surface, dp(28).toFloat())
        val tabs = listOf(
            Triple(R.id.media_emoji, R.string.media_emoji, R.drawable.ic_toolbar_emoji),
            Triple(R.id.media_gifs, R.string.media_gifs, R.drawable.ic_media_gif),
            Triple(R.id.media_stickers, R.string.media_stickers, R.drawable.ic_media_sticker),
        )
        tabs.forEachIndexed { index, (viewId, labelId, iconId) ->
            val selected = index == 0
            val textColor = colors.get(if (selected) ColorType.FUNCTIONAL_KEY_TEXT else ColorType.KEY_TEXT)
            val tab = object : TextView(context) {
                override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
                    super.onInitializeAccessibilityNodeInfo(info)
                    info.className = Button::class.java.name
                }
            }.apply {
                id = viewId
                text = context.getString(labelId)
                contentDescription = text
                gravity = Gravity.CENTER
                setTextColor(textColor)
                textSize = 13f
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                isSelected = selected
                isFocusable = true
                setPadding(dp(8), 0, dp(8), 0)
                compoundDrawablePadding = dp(6)
                val icon = ContextCompat.getDrawable(context, iconId)!!.mutate()
                icon.setTint(textColor)
                icon.setBounds(0, 0, dp(20), dp(20))
                setCompoundDrawablesRelative(icon, null, null, null)
                val fill = if (selected) colors.get(ColorType.FUNCTIONAL_KEY_BACKGROUND) else Color.TRANSPARENT
                background = RippleDrawable(ColorStateList.valueOf(ColorUtils.setAlphaComponent(textColor, 35)),
                    pill(fill, dp(24).toFloat()), pill(Color.WHITE, dp(24).toFloat()))
                setOnClickListener { listener.onMediaClick(if (index == 1) "GIFS" else if (index == 2) "STICKERS" else "EMOJI") }
            }
            track.addView(tab, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f).apply {
                if (index != 0) marginStart = dp(4)
            })
        }
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    companion object {
        private fun pill(color: Int, radius: Float) = GradientDrawable().apply {
            cornerRadius = radius
            setColor(color)
        }

        @JvmStatic fun styleCategory(view: ImageView, colors: Colors, selected: Boolean) {
            val dp = view.resources.displayMetrics.density
            val inset = (5 * dp).toInt()
            val fill = if (selected) colors.get(ColorType.FUNCTIONAL_KEY_BACKGROUND) else Color.TRANSPARENT
            val shape = pill(fill, 18 * dp)
            view.background = RippleDrawable(ColorStateList.valueOf(ColorUtils.setAlphaComponent(colors.get(ColorType.KEY_TEXT), 35)),
                InsetDrawable(shape, inset), InsetDrawable(pill(Color.WHITE, 18 * dp), inset))
            view.isSelected = selected
            colors.setColor(view, if (selected) ColorType.EMOJI_CATEGORY_SELECTED else ColorType.EMOJI_CATEGORY)
        }
    }
}
