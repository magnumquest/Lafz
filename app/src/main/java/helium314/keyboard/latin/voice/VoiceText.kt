// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.voice

import java.util.Locale

/** Small pure helpers around dictation, kept apart so they can be unit tested. */
object VoiceText {
    private val noiseOnly = Regex("""^\s*[\[(*♪].*[\])*♪]\s*$""")

    /** Whisper marks silence and noise like "[BLANK_AUDIO]" or "(music)"; those are not text */
    fun clean(raw: String): String {
        val t = raw.trim().replace(Regex("\\s+"), " ")
        return if (noiseOnly.matches(t)) "" else t
    }

    /** the model language for a keyboard language (null: dictation is not offered for it) */
    fun modelLanguage(locale: Locale): String? = when (locale.language) {
        "ur", "ar", "en" -> locale.language
        else -> null
    }

    /** [text] with a space in front if it would otherwise stick to the letter before the cursor */
    fun withLeadingSpace(text: String, codePointBeforeCursor: Int): String {
        if (text.isEmpty() || codePointBeforeCursor <= 0) return text
        if (Character.isWhitespace(codePointBeforeCursor)) return text
        if (codePointBeforeCursor == '('.code || codePointBeforeCursor == '"'.code || codePointBeforeCursor == '‏'.code) return text
        return " $text"
    }

    /** 16 bit PCM to floats in -1..1 */
    fun toFloat(pcm: ShortArray, length: Int = pcm.size) = FloatArray(length) { pcm[it] / 32768f }

    /** true if the recording is so quiet that there is no point in sending it to the model */
    fun isSilent(pcm: ShortArray, length: Int = pcm.size): Boolean {
        var peak = 0
        for (i in 0 until length) { val a = Math.abs(pcm[i].toInt()); if (a > peak) peak = a }
        return peak < 150
    }
}
