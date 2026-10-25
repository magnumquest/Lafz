// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.utils

enum class CodeKind { OTP, IBAN, CARD, ACCOUNT, PHONE, EMAIL, LINK }

/** a value found inside copied text; [value] is what gets pasted (ASCII digits for numeric values; phone numbers retain their international + prefix) */
data class FoundCode(val value: String, val kind: CodeKind, val start: Int, val end: Int)

/**
 * Finds one-time codes, PINs, IBANs, card and account numbers inside a longer copied text (an SMS, a bank message), so they can be offered
 * on their own. Pure functions, no state; the text is capped so a huge clip costs nothing.
 *
 * Deliberately conservative: a number is only a code when words around it say so (otp, code, pin, account, ...) or a checksum proves it
 * (IBAN mod 97, card Luhn). Phone numbers need a label, international prefix, or a recognizable local format. Amounts and years are left alone.
 */
object SmartCodeExtractor {
    private const val MAX_TEXT = 10000
    private const val NEAR = 45 // characters between a keyword and the number

    private val otpWords = listOf("otp", "one-time", "one time", "verification", "verify", "passcode", "pass code", "pin", "code", "security code",
        "authentication", "authorization", "authorisation", "login", "log in", "sign in", "tac",
        "کوڈ", "پن", "رمز", "کلمة المرور", "التحقق", "كود", "ওটিপি")
    private val accountWords = listOf("account", "acct", "a/c", "acc no", "acc#", "acc.", "iban", "اکاؤنٹ", "اکاونٹ", "حساب", "کھاتہ")
    private val moneyBefore = Regex("""(?i)(rs\.?|pkr|inr|usd|eur|gbp|sar|aed|rupees?|amount|balance|bal|total|\$|€|£)\s*$""")

    private val phoneWords = listOf("phone", "mobile", "tel", "call", "contact", "whatsapp", "فون", "موبائل", "هاتف", "جوال")
    private val dateToken = Regex("""(?:[0-9]{4}-[0-9]{2}-[0-9]{2}|[0-9]{2}-[0-9]{2}-[0-9]{4})""")
    private val emailToken = Regex("""[A-Za-z0-9.!#%&'*+/=?^_`{|}~-]{1,64}@[A-Za-z0-9](?:[A-Za-z0-9.-]{0,251}[A-Za-z0-9])?\.[A-Za-z]{2,63}\b""")
    private val linkToken = Regex("""(?i)\b(?:https?://|www\.)[^\s<>]+""")
    private val phoneToken = Regex("""(?<![\p{L}\p{N}])\+?(?:\([0-9]{2,4}\)|[0-9])(?:[ ()-]{0,3}[0-9]){6,29}(?![\p{L}\p{N}])""")
    private val otpToken = Regex("""(?<![0-9٠-٩۰-۹])[0-9٠-٩۰-۹]{3}[- ]?[0-9٠-٩۰-۹]{3}(?![0-9٠-٩۰-۹])|(?<![0-9٠-٩۰-۹])[0-9٠-٩۰-۹]{4,8}(?![0-9٠-٩۰-۹])""")
    private val ibanStart = Regex("""(?i)(?<![A-Za-z0-9])[A-Z]{2}[0-9]{2}""")
    private val longDigits = Regex("""(?<![\p{L}\p{N}])[0-9](?:[ -]?[0-9]){7,29}(?![\p{L}\p{N}])""")

    fun find(text: CharSequence, max: Int = 12): List<FoundCode> {
        if (text.length < 4 || max <= 0) return emptyList()
        val t = toAscii(text.take(MAX_TEXT).toString())
        val lower = t.lowercase(java.util.Locale.ROOT)
        val out = ArrayList<FoundCode>()
        fun overlaps(s: Int, e: Int) = out.any { s < it.end && e > it.start }

        for (m in linkToken.findAll(t)) {
            val value = m.value.trimEnd('.', ',', ';', ':', '!', '?', ')', ']', '}')
            out.add(FoundCode(value, CodeKind.LINK, m.range.first, m.range.first + value.length))
        }
        for (m in emailToken.findAll(t)) {
            if (!overlaps(m.range.first, m.range.last + 1))
                out.add(FoundCode(m.value, CodeKind.EMAIL, m.range.first, m.range.last + 1))
        }
        for (m in ibanStart.findAll(t)) {
            if (overlaps(m.range.first, m.range.last + 1)) continue
            val compact = StringBuilder()
            // Check token boundaries as we scan; trailing prose must not swallow a valid IBAN.
            for (end in m.range.first until minOf(t.length, m.range.first + 64)) {
                val c = t[end]
                if (c == ' ') continue
                if (c !in 'a'..'z' && c !in 'A'..'Z' && c !in '0'..'9') break
                compact.append(c.uppercaseChar())
                if (compact.length > 34) break
                if (compact.length >= 15 && t.getOrNull(end + 1)?.isLetterOrDigit() != true && ibanValid(compact.toString())) {
                    out.add(FoundCode(compact.toString(), CodeKind.IBAN, m.range.first, end + 1))
                    break
                }
            }
        }
        // Labelled accounts take priority over telephone heuristics and card checksums.
        for (m in phoneToken.findAll(t)) {
            if (overlaps(m.range.first, m.range.last + 1)) continue
            if (moneyBefore.containsMatchIn(t.substring(maxOf(0, m.range.first - NEAR), m.range.first))) continue
            if (dateToken.matches(m.value)) continue
            val digits = m.value.filter { it in '0'..'9' }
            val accountDistance = wordDistance(lower, m.range, accountWords)
            val phoneDistance = wordDistance(lower, m.range, phoneWords)
            val otpDistance = wordDistance(lower, m.range, otpWords)
            if (accountDistance < phoneDistance && accountDistance < otpDistance && digits.length in 8..30) {
                out.add(FoundCode(digits, CodeKind.ACCOUNT, m.range.first, m.range.last + 1))
                continue
            }
            val international = m.value.startsWith("+") && digits.length in 8..15
            val localMobile = digits.length == 11 && digits.startsWith("03")
            val formatted = digits.length == 10 && (m.value.contains('(') || m.value.count { it == '-' } == 2)
            if (digits.length in 7..15 && !isRepeated(digits) &&
                (international || localMobile || formatted || (phoneDistance < accountDistance && phoneDistance < otpDistance))) {
                out.add(FoundCode((if (international) "+" else "") + digits, CodeKind.PHONE, m.range.first, m.range.last + 1))
            }
        }
        for (m in longDigits.findAll(t)) {
            if (overlaps(m.range.first, m.range.last + 1)) continue
            val digits = toAscii(m.value).filter { it.isDigit() }
            val kind = when {
                digits.length in 13..19 && luhnValid(digits) && !isRepeated(digits) -> CodeKind.CARD
                digits.length in 8..30 && nearWord(lower, m.range, accountWords) -> CodeKind.ACCOUNT
                else -> null
            } ?: continue
            out.add(FoundCode(digits, kind, m.range.first, m.range.last + 1))
        }
        run {
            for (m in otpToken.findAll(t)) {
                if (overlaps(m.range.first, m.range.last + 1)) continue
                if (!nearWord(lower, m.range, otpWords)) continue
                if (t.getOrNull(m.range.first - 1)?.isLetterOrDigit() == true ||
                    t.getOrNull(m.range.last + 1)?.isLetterOrDigit() == true) continue
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

    private fun nearWord(lower: String, range: IntRange, words: List<String>) =
        wordDistance(lower, range, words) != Int.MAX_VALUE

    private fun wordDistance(lower: String, range: IntRange, words: List<String>): Int {
        var nearest = Int.MAX_VALUE
        for (word in words) {
            var i = lower.indexOf(word, maxOf(0, range.first - NEAR))
            while (i >= 0 && i <= range.last + NEAR) {
                val before = lower.getOrNull(i - 1)
                val after = lower.getOrNull(i + word.length)
                if (before?.isLetter() != true && after?.isLetter() != true) {
                    val distance = if (i <= range.first) range.first - i - word.length else i - range.last
                    if (distance in 0..NEAR) nearest = minOf(nearest, distance)
                }
                i = lower.indexOf(word, i + word.length)
            }
        }
        return nearest
    }

    private fun toAscii(s: String): String = buildString {
        for (c in s) append(when (c) { in '٠'..'٩' -> '0' + (c - '٠'); in '۰'..'۹' -> '0' + (c - '۰'); else -> c })
    }

    private fun isRepeated(d: String) = d.all { it == d[0] }

    /** Luhn checksum of a card number */
    fun luhnValid(digits: String): Boolean {
        if (digits.isEmpty() || digits.any { it !in '0'..'9' }) return false
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
        val s = iban.uppercase(java.util.Locale.ROOT)
        if (s.length !in 15..34 || s.take(2).any { it !in 'A'..'Z' } ||
            s.substring(2, 4).any { it !in '0'..'9' } || s.any { it !in 'A'..'Z' && it !in '0'..'9' }) return false
        val r = s.substring(4) + s.substring(0, 4)
        var rem = 0
        for (c in r) {
            val v = if (c.isDigit()) c - '0' else c - 'A' + 10
            rem = if (v < 10) (rem * 10 + v) % 97 else (rem * 100 + v) % 97
        }
        return rem == 1
    }
}
