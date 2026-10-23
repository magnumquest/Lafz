// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.keyboard.clipboard

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import helium314.keyboard.latin.utils.FoundCode

/** small tappable pills with the values found in copied text (see SmartCodeExtractor); used in the suggestion bar and in the history list */
object CodeChips {
    /** fills [container] with one chip per code; [mask] hides the digits (password fields) but the tap still pastes the real value */
    fun fill(container: LinearLayout, codes: List<FoundCode>, textColor: Int, backgroundColor: Int, textSizePx: Float,
             mask: Boolean, onClick: (String) -> Unit) {
        container.removeAllViews()
        val context = container.context
        for (code in codes) {
            val chip = TextView(context).apply {
                text = if (mask) "•".repeat(code.value.length) else code.value
                setTextSize(TypedValue.COMPLEX_UNIT_PX, textSizePx)
                setTextColor(textColor)
                setSingleLine()
                ellipsize = TextUtils.TruncateAt.MIDDLE
                gravity = android.view.Gravity.CENTER
                val h = dp(context, 12); val v = dp(context, 4)
                setPadding(h, v, h, v)
                background = GradientDrawable().apply {
                    cornerRadius = dp(context, 16).toFloat()
                    setColor(backgroundColor)
                    setStroke(dp(context, 1), (textColor and 0x00FFFFFF) or 0x55000000)
                }
                isHapticFeedbackEnabled = false
                isSoundEffectsEnabled = false
                contentDescription = code.kind.name.lowercase()
                setOnClickListener { onClick(code.value) }
            }
            container.addView(chip, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                if (container.orientation == LinearLayout.VERTICAL) bottomMargin = dp(context, 4) else marginEnd = dp(context, 6)
            })
        }
    }

    private fun dp(context: Context, v: Int) = (v * context.resources.displayMetrics.density + 0.5f).toInt()
}
