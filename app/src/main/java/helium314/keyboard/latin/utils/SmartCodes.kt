// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.utils

enum class CodeKind { OTP, IBAN, CARD, ACCOUNT }

/** a value found inside copied text; [value] is what gets pasted (ASCII digits, no separators) */
data class FoundCode(val value: String, val kind: CodeKind, val start: Int, val end: Int)

/**
 * Finds one-time codes, PINs, IBANs, card and account numbers inside a longer copied text (an SMS, a bank message), so they can be offered
 * on their own. Pure functions, no state; the text is capped so a huge clip costs nothing.
 *
 * Deliberately conservative: a number is only a code when words around it say so (otp, code, pin, account, ...) or a checksum proves it
 * (IBAN mod 97, card Luhn). Phone numbers, amounts and years are left alone.
 */
object SmartCodeExtractor {
    private const val MAX_TEXT = 2000
    private const val NEAR = 45 // characters between a keyword and the number

    private val otpWords = listOf("otp", "one-time", "one time", "verification", "verify", "passcode", "pass code", "pin", "code", "security code",
        "authentication", "authorization", "authorisation", "login", "log in", "sign in", "tac",
        "کوڈ", "پن", "رمز", "کلمة المرور", "التحقق", "كود", "ওটিপি")
    private val accountWords = listOf("account", "acct", "a/c", "acc no", "acc#", "acc.", "iban", "اکاؤنٹ", "اکاونٹ", "حساب", "کھاتہ")
    private val moneyBefore = Regex("""(?i)(rs\.?|pkr|inr|usd|eur|gbp|sar|aed|rupees?|amount|balance|bal|total|\$|€|£)\s*$""")

    private val digitRun = Regex("""[0-9٠-٩۰-۹]""")
    private val otpToken = Regex("""(?<![0-9٠-٩۰-۹])[0-9٠-٩۰-۹]{3}[- ]?[0-9٠-٩۰-۹]{3}(?![0-9٠-٩۰-۹])|(?<![0-9٠-٩۰-۹])[0-9٠-٩۰-۹]{4,8}(?![0-9٠-٩۰-۹])""")
    private val ibanToken = Regex("""(?<![A-Za-z0-9])[A-Z]{2}[0-9]{2}(?:[ ]?[A-Z0-9]{2,4}){3,8}(?![A-Za-z0-9])""")
    private val longDigits = Regex("""(?<![0-9٠-٩۰-۹])[0-9٠-٩۰-۹](?:[ -]?[0-9٠-٩۰-۹]){7,18}(?![0-9٠-٩۰-۹])""")

    fun find(text: CharSequence, max: Int = 3): List<FoundCode> {
        if (text.length < 4) return emptyList()
        val t = if (text.length > MAX_TEXT) text.subSequence(0, MAX_TEXT).toString() else text.toString()
        if (!digitRun.containsMatchIn(t)) return emptyList() // every kind needs digits: the common case (plain text) ends here
        val lower = t.lowercase()
        val out = ArrayList<FoundCode>()
        fun overlaps(s: Int, e: Int) = out.any { s < it.end && e > it.start }

        for (m in ibanToken.findAll(t)) {
            val compact = m.value.replace(" ", "")
            if (compact.length in 15..34 && ibanValid(compact)) out.add(FoundCode(compact, CodeKind.IBAN, m.range.first, m.range.last + 1))
        }
        for (m in longDigits.findAll(t)) {
            if (overlaps(m.range.first, m.range.last + 1)) continue
            val digits = toAscii(m.value).filter { it.isDigit() }
            val kind = when {
                digits.length in 13..19 && luhnValid(digits) && !isRepeated(digits) -> CodeKind.CARD
                digits.length in 8..20 && nearWord(lower, m.range, accountWords) -> CodeKind.ACCOUNT
                else -> null
            } ?: continue
            out.add(FoundCode(digits, kind, m.range.first, m.range.last + 1))
        }
        run {
            for (m in otpToken.findAll(t)) {
                if (overlaps(m.range.first, m.range.last + 1)) continue
                if (!nearWord(lower, m.range, otpWords)) continue
                if (moneyBefore.containsMatchIn(t.substring(maxOf(0, m.range.first - 8), m.range.first))) continue
                val next = t.getOrNull(m.range.last + 1)
                if (next == '%' || next == '/' || t.getOrNull(m.range.first - 1) == '/') continue // percentage, date
                val digits = toAscii(m.value).filter { it.isDigit() }
                if (digits.length < 4) continue
                out.add(FoundCode(digits, CodeKind.OTP, m.range.first, m.range.last + 1))
            }
        }
        out.sortBy { it.start }
        return out.distinctBy { it.value }.take(max)
    }

    private fun nearWord(lower: String, range: IntRange, words: List<String>): Boolean {
        val from = maxOf(0, range.first - NEAR)
        val to = minOf(lower.length, range.last + 1 + NEAR)
        val window = lower.substring(from, to)
        return words.any { w ->
            val i = window.indexOf(w)
            if (i < 0) false
            else if (w.length > 3 || !w[0].isLetter() || w[0].code > 0x590) true
            else { // short latin words ("pin", "tac", "otp", "acc") must be whole words
                val before = window.getOrNull(i - 1); val after = window.getOrNull(i + w.length)
                (before == null || !before.isLetter()) && (after == null || !after.isLetter())
            }
        }
    }

    private fun toAscii(s: String): String = buildString {
        for (c in s) append(when (c) { in '٠'..'٩' -> '0' + (c - '٠'); in '۰'..'۹' -> '0' + (c - '۰'); else -> c })
    }

    private fun isRepeated(d: String) = d.all { it == d[0] }

    /** Luhn checksum of a card number */
    fun luhnValid(digits: String): Boolean {
        var sum = 0; var alt = false
        for (i in digits.length - 1 downTo 0) {
            var n = digits[i] - '0'
            if (alt) { n *= 2; if (n > 9) n -= 9 }
            sum += n; alt = !alt
        }
        return sum % 10 == 0
    }

    /** ISO 13616 check: move the first four characters to the end, letters become 10..35, the number mod 97 must be 1 */
    fun ibanValid(iban: String): Boolean {
        val s = iban.uppercase()
        if (s.length < 15 || !s.all { it.isLetterOrDigit() }) return false
        val r = s.substring(4) + s.substring(0, 4)
        var rem = 0
        for (c in r) {
            val v = if (c.isDigit()) c - '0' else c - 'A' + 10
            rem = if (v < 10) (rem * 10 + v) % 97 else (rem * 100 + v) % 97
        }
        return rem == 1
    }
}
