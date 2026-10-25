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
import helium314.keyboard.latin.utils.CodeKind
import helium314.keyboard.latin.R
import helium314.keyboard.keyboard.KeyboardTypeface

/** small tappable pills with the values found in copied text (see SmartCodeExtractor); used in the suggestion bar and in the history list */
object CodeChips {
    /** fills [container] with one chip per code; [mask] hides the digits (password fields) but the tap still pastes the real value */
    fun fill(container: LinearLayout, codes: List<FoundCode>, textColor: Int, backgroundColor: Int, textSizePx: Float,
             mask: Boolean, fullText: String? = null, onClick: (String) -> Unit) {
        container.removeAllViews()
        val context = container.context
        fun addChip(label: String, value: String, display: String) {
            val displayedValue = if (mask) "••••" else display
            val chip = TextView(context).apply {
                text = if (displayedValue.isEmpty()) label else "$label · $displayedValue"
                KeyboardTypeface.applyToTextView(this)
                setTextSize(TypedValue.COMPLEX_UNIT_PX, textSizePx)
                setTextColor(textColor)
                setSingleLine()
                ellipsize = TextUtils.TruncateAt.MIDDLE
                gravity = android.view.Gravity.CENTER
                minimumHeight = dp(context, 48)
                maxWidth = dp(context, 340)
                textDirection = android.view.View.TEXT_DIRECTION_LTR
                val h = dp(context, 12); val v = dp(context, 4)
                setPadding(h, v, h, v)
                background = GradientDrawable().apply {
                    cornerRadius = dp(context, 16).toFloat()
                    setColor(backgroundColor)
                    setStroke(dp(context, 1), (textColor and 0x00FFFFFF) or 0x55000000)
                }
                isHapticFeedbackEnabled = false
                isSoundEffectsEnabled = false
                contentDescription = "$label: $displayedValue"
                setOnClickListener { onClick(value) }
            }
            container.addView(chip, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                if (container.orientation == LinearLayout.VERTICAL) bottomMargin = dp(context, 4) else marginEnd = dp(context, 6)
            })
        }
        for (code in codes) {
            val label = context.getString(labelResId(code.kind))
            addChip(label, code.value, code.value)
        }
        if (fullText != null && codes.none { it.value == fullText })
            addChip(context.getString(R.string.clipboard_paste_full_text), fullText, "")

    }

    fun labelResId(kind: CodeKind): Int = when (kind) {
        CodeKind.OTP -> R.string.clipboard_value_code
        CodeKind.IBAN -> R.string.clipboard_value_iban
        CodeKind.CARD -> R.string.clipboard_value_card
        CodeKind.ACCOUNT -> R.string.clipboard_value_account
        CodeKind.PHONE -> R.string.clipboard_value_phone
        CodeKind.EMAIL -> R.string.clipboard_value_email
        CodeKind.LINK -> R.string.clipboard_value_link
    }

    private fun dp(context: Context, v: Int) = (v * context.resources.displayMetrics.density + 0.5f).toInt()
}
