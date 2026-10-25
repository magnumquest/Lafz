// SPDX-License-Identifier: GPL-3.0-only

package helium314.keyboard.keyboard.clipboard

import android.annotation.SuppressLint
import android.graphics.Typeface
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import helium314.keyboard.latin.ClipboardHistoryManager
import helium314.keyboard.latin.R
import helium314.keyboard.latin.common.ColorType
import helium314.keyboard.latin.settings.Settings

class ClipboardAdapter(
       val clipboardLayoutParams: ClipboardLayoutParams,
       val keyEventListener: OnKeyEventListener
) : RecyclerView.Adapter<ClipboardAdapter.ViewHolder>() {

    var clipboardHistoryManager: ClipboardHistoryManager? = null
    private var rows: List<ClipboardRow> = emptyList()

    fun refresh() {
        val manager = clipboardHistoryManager
        val entries = if (manager == null) emptyList() else
            (0 until manager.getHistorySize()).mapNotNull { manager.getHistoryEntry(it) }
        val settings = Settings.getValues()
        rows = clipboardRows(entries, settings.mSmartClipboard, settings.mClipboardHistoryPinnedFirst)
        notifyDataSetChanged()
    }

    fun canSwipe(position: Int) = (rows.getOrNull(position) as? ClipboardRow.Clip)?.entry?.isPinned == false

    fun removeAt(position: Int) {
        val entry = (rows.getOrNull(position) as? ClipboardRow.Clip)?.entry ?: return
        val manager = clipboardHistoryManager ?: return
        val index = (0 until manager.getHistorySize()).firstOrNull { manager.getHistoryEntry(it)?.id == entry.id } ?: return
        manager.removeEntry(index)
        refresh()
    }

    var pinnedIconResId = 0
    var itemBackgroundId = 0
    var itemTypeFace: Typeface? = null
    var itemTextColor = 0
    var itemTextSize = 0f

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.clipboard_entry_key, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.setContent(rows[position])
    }

    override fun getItemCount() = rows.size

    inner class ViewHolder(
            view: View
    ) : RecyclerView.ViewHolder(view), View.OnClickListener, View.OnTouchListener, View.OnLongClickListener {

        private val pinnedIconView: ImageView
        private val contentTextView: TextView
        private val contentImageView: ImageView
        private val chipsView: android.widget.LinearLayout

        init {
            view.apply {
                setOnClickListener(this@ViewHolder)
                setOnTouchListener(this@ViewHolder)
                setOnLongClickListener(this@ViewHolder)
                setBackgroundResource(itemBackgroundId)
                isHapticFeedbackEnabled = false
            }
            Settings.getValues().mColors.setBackground(view, ColorType.KEY_BACKGROUND)
            pinnedIconView = view.findViewById<ImageView>(R.id.clipboard_entry_pinned_icon).apply {
                visibility = View.GONE
                setImageResource(pinnedIconResId)
            }
            contentTextView = view.findViewById<TextView>(R.id.clipboard_entry_text_content).apply {
                typeface = itemTypeFace
                setTextColor(itemTextColor)
                setTextSize(TypedValue.COMPLEX_UNIT_PX, itemTextSize)
            }
            contentImageView = view.findViewById(R.id.clipboard_entry_image_content)
            chipsView = view.findViewById(R.id.clipboard_entry_chips)
            clipboardLayoutParams.setItemProperties(view)
            val colors = Settings.getValues().mColors
            colors.setColor(pinnedIconView, ColorType.CLIPBOARD_PIN)
        }

        private var boundRow: ClipboardRow? = null

        internal fun setContent(row: ClipboardRow) {
            boundRow = row
            val heading = row is ClipboardRow.Heading
            (itemView.layoutParams as? StaggeredGridLayoutManager.LayoutParams)?.isFullSpan = heading
            itemView.isClickable = !heading
            itemView.isLongClickable = !heading
            androidx.core.view.ViewCompat.setAccessibilityHeading(itemView, heading)
            chipsView.visibility = View.GONE
            contentImageView.visibility = View.GONE
            pinnedIconView.visibility = View.GONE
            contentTextView.visibility = View.VISIBLE
            contentTextView.typeface = if (heading) Typeface.create(itemTypeFace, Typeface.BOLD) else itemTypeFace
            contentTextView.setTextSize(TypedValue.COMPLEX_UNIT_PX, if (heading) itemTextSize * 0.85f else itemTextSize)
            if (heading) {
                itemView.background = null
                contentTextView.setText(row.title)
                return
            }
            itemView.setBackgroundResource(itemBackgroundId)
            Settings.getValues().mColors.setBackground(itemView, ColorType.KEY_BACKGROUND)
            if (row is ClipboardRow.Detail) {
                val label = itemView.context.getString(CodeChips.labelResId(row.code.kind))
                contentTextView.text = "$label\n${row.code.value}"
                return
            }
            val historyEntry = (row as ClipboardRow.Clip).entry
            if (historyEntry.filename != null) {
                historyEntry.setImageAndDescription(contentImageView, contentTextView)
            } else {
                contentTextView.text = historyEntry.text?.take(1000) // truncate displayed text for performance reasons
            }
            pinnedIconView.visibility = if (historyEntry.isPinned) View.VISIBLE else View.GONE
            contentImageView.visibility = if (historyEntry.filename != null) View.VISIBLE else View.GONE
            contentTextView.visibility = if (contentTextView.text.isNullOrEmpty()) View.GONE else View.VISIBLE
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouch(view: View, event: MotionEvent): Boolean {
            if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                (boundRow as? ClipboardRow.Clip)?.let { keyEventListener.onKeyDown(it.entry.id) }
            }
            return false
        }

        override fun onClick(view: View) {
            when (val row = boundRow) {
                is ClipboardRow.Clip -> keyEventListener.onKeyUp(row.entry.id)
                is ClipboardRow.Detail -> keyEventListener.onValue(row.code.value)
                else -> Unit
            }
        }

        override fun onLongClick(view: View): Boolean {
            when (val row = boundRow) {
                is ClipboardRow.Clip -> clipboardHistoryManager?.toggleClipPinned(row.entry.id)
                is ClipboardRow.Detail -> clipboardHistoryManager?.pinText(row.code.value)
                else -> return false
            }
            return true
        }
    }
}
