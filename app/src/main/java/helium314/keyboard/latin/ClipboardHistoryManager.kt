// SPDX-License-Identifier: GPL-3.0-only

package helium314.keyboard.latin

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.text.InputType
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.core.view.inputmethod.InputContentInfoCompat
import androidx.core.view.isGone
import androidx.core.view.isVisible
import helium314.keyboard.keyboard.KeyboardTypeface
import helium314.keyboard.keyboard.KeyboardSwitcher
import helium314.keyboard.compat.ClipboardManagerCompat
import helium314.keyboard.event.Event
import helium314.keyboard.event.HapticEvent
import helium314.keyboard.keyboard.internal.KeyboardIconsSet
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode
import helium314.keyboard.keyboard.clipboard.CodeChips
import helium314.keyboard.latin.common.ColorType
import helium314.keyboard.latin.utils.FoundCode
import helium314.keyboard.latin.utils.CodeKind
import helium314.keyboard.latin.utils.SmartCodeExtractor
import helium314.keyboard.latin.common.Constants
import helium314.keyboard.latin.common.isValidNumber
import helium314.keyboard.latin.database.ClipboardDao
import helium314.keyboard.latin.databinding.ClipboardSuggestionBinding
import helium314.keyboard.latin.settings.Defaults
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.InputTypeUtils
import helium314.keyboard.latin.utils.Log
import helium314.keyboard.latin.utils.ToolbarKey
import helium314.keyboard.latin.utils.prefs
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class ClipboardHistoryManager(
        private val latinIME: LatinIME
) : ClipboardManager.OnPrimaryClipChangedListener {

    private lateinit var clipboardManager: ClipboardManager
    private var clipboardSuggestionView: View? = null
    private var clipboardDao: ClipboardDao? = null
    private var tempPrimaryClip = false

    fun onCreate() {
        clipboardManager = latinIME.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboardManager.addPrimaryClipChangedListener(this)
        clipboardDao = ClipboardDao.getInstance(latinIME)
        if (latinIME.mSettings.current.mClipboardHistoryEnabled)
            fetchPrimaryClip()
    }

    fun onDestroy() {
        clipboardManager.removePrimaryClipChangedListener(this)
    }

    override fun onPrimaryClipChanged() {
        if (tempPrimaryClip) return
        dontShowCurrentSuggestion = false
        lastClipIdentity = clipIdentity()
        // Make sure we read clipboard content only if history settings is set
        if (latinIME.mSettings.current.mClipboardHistoryEnabled) {
            fetchPrimaryClip()
        }
        // Copying with the keyboard open does not restart the input view. Refresh the
        // strip explicitly, independently of whether the user keeps clipboard history.
        latinIME.mHandler.post {
            if (latinIME.isInputViewShown && KeyboardSwitcher.getInstance().mainKeyboardView?.isShown == true) {
                if (!latinIME.tryShowClipboardSuggestion() && clipboardSuggestionView?.isShown == true)
                    latinIME.setNeutralSuggestionStrip()
            }
        }
    }

    /** Reconcile clips copied while this IME was stopped or Android withheld callbacks. */
    fun onStartInputView() {
        val identity = clipIdentity()
        if (identity != lastClipIdentity) dontShowCurrentSuggestion = false
        lastClipIdentity = identity
        if (latinIME.mSettings.current.mClipboardHistoryEnabled) fetchPrimaryClip()
    }

    private var lastClipIdentity: Pair<Long, String?>? = null
    private var lastCapturedClip: Pair<Pair<Long, String?>, Boolean>? = null

    private fun clipIdentity(clip: ClipData? = clipboardManager.primaryClip): Pair<Long, String?>? {
        clip ?: return null
        if (clip.itemCount == 0) return null
        val timestamp = if (android.os.Build.VERSION.SDK_INT >= 26) clip.description.timestamp else 0L
        val item = clip.getItemAt(0)
        return timestamp to (item.text?.toString() ?: item.uri?.toString())
    }

    // TODO: support additional items in a multi-item clip.
    private fun fetchPrimaryClip() {
        if (tempPrimaryClip) return // avoid updating history
        val clipData = clipboardManager.primaryClip ?: return
        if (clipData.itemCount == 0) return
        val clipItem = clipData.getItemAt(0) ?: return
        val description = clipData.description ?: return
        val settings = latinIME.mSettings.current
        val identity = clipIdentity(clipData) ?: return
        val captureKey = identity to (settings.mSmartClipboard && settings.mClipboardSkipCodes)
        // Reconcile missed copies without resurrecting a clip deleted from history.
        if (captureKey == lastCapturedClip) return
        if (clipboardDao == null) clipboardDao = ClipboardDao.getInstance(latinIME)
        if (clipboardDao == null) return
        lastCapturedClip = captureKey
        // Respect the source app's sensitive flag; never persist passwords or secrets.
        if (ClipboardManagerCompat.getClipSensitivity(description) == true) return
        val timeStamp = ClipboardManagerCompat.getClipTimestamp(clipData)

        if (description.hasMimeType("text/*")) {
            val content = clipItem.coerceToText(latinIME)
            if (TextUtils.isEmpty(content)) return
            if (settings.mSmartClipboard && settings.mClipboardSkipCodes && codesIn(content).any {
                it.kind in setOf(CodeKind.OTP, CodeKind.IBAN, CodeKind.CARD, CodeKind.ACCOUNT)
            }) return // kept out of the history on purpose
            clipboardDao?.addClip(timeStamp, false, content.toString())
        } else if (maySaveFromUri(clipItem.uri, latinIME)) {
            clipboardDao?.addClipUri(timeStamp, false, clipItem.uri, description, latinIME)
        }
    }

    // the extraction runs once per distinct clip, not each time the suggestion bar is rebuilt
    private var codesFor: String? = null
    private var codes: List<FoundCode> = emptyList()

    private fun codesIn(content: CharSequence): List<FoundCode> {
        val s = content.toString()
        if (s != codesFor) { codes = SmartCodeExtractor.find(s); codesFor = s }
        return codes
    }

    fun getPrimaryClipIfText(): String? {
        if (tempPrimaryClip) return null // avoid updating history
        val clipData = clipboardManager.primaryClip ?: return null
        if (clipData.itemCount == 0) return null
        val clipItem = clipData.getItemAt(0) ?: return null
        return if (clipData.description?.hasMimeType("text/*") == true)
            clipItem.coerceToText(latinIME).toString().takeIf { it.isNotEmpty() }
        else null
    }

    // fallback method because in some apps there is no supported mime type and commitContend does nothing,
    // but KeyEvent.KEYCODE_PASTE for pasting from primary clip works fine
    // (actually we do change the primary clip, but (try to) revert immediately)
    fun pasteWithoutChangingClips(content: InputContentInfoCompat) {
        Log.d(TAG, "trying fallback pasting with system clipboard")
        val primaryClip = clipboardManager.primaryClip
        val tempClip = ClipData(content.description, ClipData.Item(content.contentUri))
        tempPrimaryClip = true
        clipboardManager.setPrimaryClip(tempClip)
        latinIME.onEvent(Event.createSoftwareKeypressEvent(KeyCode.CLIPBOARD_PASTE, 0,
            Constants.NOT_A_COORDINATE, Constants.NOT_A_COORDINATE, false))
        tempPrimaryClip = false
        if (primaryClip == null)
            return
        // we need to wait a little before switching back to the original primary clip
        // a. it can happen that we switch back before the pasting has started, in that case we only past the primary clip
        // b. if we switch while the clip is pasted, it might crash the app (tested with joplin and logseq)
        // todo: replacing the current primary clip is far from ideal, try finding a different way
        GlobalScope.launch {
            delay(500)
            try {
                clipboardManager.setPrimaryClip(primaryClip)
            } catch (e: Exception) {
                Log.i(TAG, "could not go back to old primary clip", e)
                // happens wen the clip was a file
                // try to find it in out clipboard entries
                val clip = clipboardDao?.getAll()?.firstOrNull { it.timeStamp == ClipboardManagerCompat.getClipTimestamp(primaryClip) }
                if (clip?.filename != null)
                    clipboardManager.setPrimaryClip(ClipData(
                        ClipDescription(clip.text, clip.mimeTypes?.toTypedArray()),
                        ClipData.Item(clip.getContentUri(latinIME))
                    ))
                else if (clip != null)
                    clipboardManager.setPrimaryClip(ClipData(
                        ClipDescription("", arrayOf("text/*")),
                        ClipData.Item(clip.text)
                    ))
            }
        }
    }

    fun pinText(text: String) {
        val dao = clipboardDao ?: return
        dao.addClip(System.currentTimeMillis(), true, text)
        val entry = dao.getAll().firstOrNull { it.filename == null && it.text == text } ?: return
        if (!entry.isPinned) dao.togglePinned(entry.id)
    }

    fun toggleClipPinned(id: Long) {
        clipboardDao?.togglePinned(id)
    }

    fun clearHistory() {
        clipboardDao?.clearNonPinned()
        ClipboardManagerCompat.clearPrimaryClip(clipboardManager)
        removeClipboardSuggestion()
    }

    fun canRemove(index: Int) = clipboardDao?.isPinned(index) == false

    fun removeEntry(index: Int) {
        if (canRemove(index))
            clipboardDao?.deleteClipAt(index)
    }

    fun sortHistoryEntries() {
        clipboardDao?.sort()
    }

    // We do not want to update history while user is visualizing it, so we check retention only
    // when history is about to be shown
    fun prepareClipboardHistory() {
        if (latinIME.mSettings.current.mClipboardHistoryEnabled) fetchPrimaryClip()
        clipboardDao?.clearOldClips(true)
    }

    fun getHistorySize() = clipboardDao?.count() ?: 0

    fun getHistoryEntry(position: Int) = clipboardDao?.getAt(position)

    fun getHistoryEntryContent(id: Long) = clipboardDao?.get(id)

    fun setHistoryChangeListener(listener: ClipboardDao.Listener?) {
        clipboardDao?.listener = listener
    }

    private fun isClipSensitive(inputType: Int): Boolean {
        return ClipboardManagerCompat.getClipSensitivity(clipboardManager.primaryClip?.description) == true ||
            InputTypeUtils.isPasswordInputType(inputType)
    }

    fun getClipboardSuggestionView(editorInfo: EditorInfo?, parent: ViewGroup?): View? {
        // maybe no need to create a new view
        // but a cache has to consider a few possible changes, so better don't implement without need
        // get the content, or return null
        if (!latinIME.mSettings.current.mSuggestClipboardContent) return null
        if (dontShowCurrentSuggestion) return null
        if (parent == null) return null
        val clipData = clipboardManager.primaryClip ?: return null
        if (clipData.itemCount == 0) return null
        val clipItem = clipData.getItemAt(0) ?: return null
        val hasText = clipData.description?.hasMimeType("text/*") == true
        val hasImage = clipData.description?.hasMimeType("image/*") == true && clipItem.uri != null
        if (!hasText && !hasImage) return null
        val timeStamp = ClipboardManagerCompat.getClipTimestamp(clipData)
        if (System.currentTimeMillis() - timeStamp > RECENT_TIME_MILLIS) return null
        val content = clipItem.coerceToText(latinIME)

        // create the view
        val binding = ClipboardSuggestionBinding.inflate(LayoutInflater.from(latinIME), parent, false)
        val textView = binding.clipboardSuggestionText
        val clipIcon = KeyboardIconsSet.instance.getIconDrawable(ToolbarKey.PASTE.name.lowercase())
        clipIcon?.setBounds(0, 0, textView.lineHeight, textView.lineHeight) // scale the icon to the text
        textView.setCompoundDrawablesRelative(clipIcon, null, null, null)
        val inputType = editorInfo?.inputType ?: InputType.TYPE_NULL
        val foundCodes = if (hasText && latinIME.mSettings.current.mSmartClipboard) codesIn(content).filter {
            !InputTypeUtils.isNumberInputType(inputType) || it.value.isValidNumber()
        } else emptyList()
        if (foundCodes.isNotEmpty()) {
            // a code or number inside a longer text: offer the values on their own instead of the whole text
            val colors = latinIME.mSettings.current.mColors
            val chips = binding.clipboardSuggestionChips
            textView.visibility = View.GONE
            binding.clipboardSuggestionScroll.visibility = View.VISIBLE
            CodeChips.fill(chips, foundCodes, colors.get(ColorType.KEY_TEXT), colors.get(ColorType.KEY_BACKGROUND), textView.textSize,
                isClipSensitive(inputType), fullText = content.toString().takeIf { !InputTypeUtils.isNumberInputType(inputType) }) { value ->
                dontShowCurrentSuggestion = true
                latinIME.onTextInput(value)
                AudioAndHapticFeedbackManager.getInstance().performHapticAndAudioFeedback(KeyCode.NOT_SPECIFIED, chips, HapticEvent.KEY_PRESS)
                removeClipboardSuggestion()
            }
            val closeButton = binding.clipboardSuggestionClose
            closeButton.setImageDrawable(KeyboardIconsSet.instance.getIconDrawable(ToolbarKey.CLOSE_HISTORY.name.lowercase()))
            closeButton.layoutParams.width = textView.lineHeight
            closeButton.layoutParams.height = textView.lineHeight
            closeButton.setOnClickListener { removeClipboardSuggestion() }
            colors.setColor(closeButton, ColorType.REMOVE_SUGGESTION_ICON)
            colors.setBackground(binding.root, ColorType.CLIPBOARD_SUGGESTION_BACKGROUND)
            clipboardSuggestionView = binding.root
            return clipboardSuggestionView
        }
        if (hasText) {
            if (TextUtils.isEmpty(content)) return null
            if (InputTypeUtils.isNumberInputType(inputType) && !content.isValidNumber()) return null
            val shortenedContent = content.take(200) // for display only
            KeyboardTypeface.applyToTextView(textView)
            textView.text = (if (isClipSensitive(inputType)) "*".repeat(shortenedContent.length) else shortenedContent)
        }
        val onClickListener = View.OnClickListener {
            dontShowCurrentSuggestion = true
            if (hasText) latinIME.onTextInput(content.toString())
            else latinIME.onEvent(Event.createSoftwareKeypressEvent(KeyCode.CLIPBOARD_PASTE, 0,
                Constants.NOT_A_COORDINATE, Constants.NOT_A_COORDINATE, false))
            AudioAndHapticFeedbackManager.getInstance().performHapticAndAudioFeedback(KeyCode.NOT_SPECIFIED, it, HapticEvent.KEY_PRESS)
            removeClipboardSuggestion()
        }
        textView.setOnClickListener(onClickListener)

        if (hasImage) {
            if (InputTypeUtils.isNumberInputType(inputType)) return null
            val imageView = binding.clipboardSuggestionImage
            imageView.isVisible = true
            try {
                imageView.setImageURI(clipItem.uri)
            } catch (e: Exception) {
                Log.w(TAG, "error setting clipboard image", e) // happens with SecurityException: Permission Denial
                return null
            }
            imageView.setOnClickListener(onClickListener)
        }

        val closeButton = binding.clipboardSuggestionClose
        closeButton.setImageDrawable(KeyboardIconsSet.instance.getIconDrawable(ToolbarKey.CLOSE_HISTORY.name.lowercase()))
        closeButton.layoutParams.width = textView.lineHeight // scale the icon to the text
        closeButton.layoutParams.height = textView.lineHeight
        closeButton.setOnClickListener { removeClipboardSuggestion() }

        val colors = latinIME.mSettings.current.mColors
        textView.setTextColor(colors.get(ColorType.KEY_TEXT))
        clipIcon?.let { colors.setColor(it, ColorType.CLIPBOARD_SUGGESTION_ICON) }
        colors.setColor(closeButton, ColorType.REMOVE_SUGGESTION_ICON)
        colors.setBackground(binding.root, ColorType.CLIPBOARD_SUGGESTION_BACKGROUND)

        clipboardSuggestionView = binding.root
        return clipboardSuggestionView
    }

    fun isSuggestionVisible() = clipboardSuggestionView?.isShown == true

    private fun removeClipboardSuggestion() {
        dontShowCurrentSuggestion = true
        val csv = clipboardSuggestionView ?: return
        if (csv.parent != null && !csv.isGone) {
            // clipboard view is shown ->
            latinIME.setNeutralSuggestionStrip()
            latinIME.mHandler.postResumeSuggestions(false)
        }
        csv.isGone = true
    }

    companion object {
        private val TAG = "ClipboardHistoryManager"

        // avoid showing the current suggestion because it has been dismissed or pasted
        private var dontShowCurrentSuggestion: Boolean = false

        const val RECENT_TIME_MILLIS = 3 * 60 * 1000L // 3 minutes (for clipboard suggestions)

        private fun maySaveFromUri(uri: Uri?, context: Context): Boolean {
            val maxSize = context.prefs().getInt(Settings.PREF_CLIPBOARD_FILES_SIZE_LIMIT, Defaults.PREF_CLIPBOARD_FILES_SIZE_LIMIT)
            val saveUriData = context.prefs().getBoolean(Settings.PREF_CLIPBOARD_USE_FILES, Defaults.PREF_CLIPBOARD_USE_FILES)
            if (uri == null || !saveUriData) return false
            try {
                context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null).use {
                    if (it?.moveToFirst() != true) return false
                    val size = it.getLong(0)
                    return size <= maxSize * 1000000 // maxSize is megabytes
                }
            } catch (e: Exception) {
                Log.w(TAG, "error checking clip size", e) // happens with SecurityException: Permission Denial
                return false
            }
        }
    }
}
