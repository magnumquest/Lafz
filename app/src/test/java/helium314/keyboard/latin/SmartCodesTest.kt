// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin

import helium314.keyboard.latin.utils.CodeKind
import helium314.keyboard.latin.utils.SmartCodeExtractor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SmartCodesTest {
    private fun values(text: String) = SmartCodeExtractor.find(text).map { it.value to it.kind }

    @Test fun otpInSms() {
        assertEquals(listOf("482916" to CodeKind.OTP), values("Your OTP is 482916. Do not share it with anyone."))
        assertEquals(listOf("123456" to CodeKind.OTP), values("123456 is your verification code"))
        assertEquals(listOf("123456" to CodeKind.OTP), values("G-123456 is your Google verification code"))
        assertEquals(listOf("4821" to CodeKind.OTP), values("Use 4821 as PIN for login"))
        assertEquals(listOf("123456" to CodeKind.OTP), values("Your code is 123 456"))
    }

    @Test fun otpInUrduAndArabic() {
        assertEquals(listOf("482916" to CodeKind.OTP), values("آپ کا کوڈ ۴۸۲۹۱۶ ہے"))
        assertEquals(listOf("482916" to CodeKind.OTP), values("رمز التحقق الخاص بك هو ٤٨٢٩١٦"))
    }

    @Test fun ordinaryNumbersAreLeftAlone() {
        for (t in listOf("The meeting is in 2024", "Balance Rs 5000 debited", "1234", "I have 12345 followers",
            "Discount 15000% off", "see you on 12/05/2025", "hello world", "", "123")) assertEquals(emptyList(), values(t), t)
    }

    @Test fun codeNextToAmountOnlyKeepsTheCode() {
        assertEquals(listOf("739201" to CodeKind.OTP), values("Rs 5000 debited. Your code is 739201, valid 10 minutes"))
    }

    @Test fun shortWordsMustBeWholeWords() {
        assertEquals(emptyList(), values("the shipping 4821 arrived")) // "pin" inside shipping
        assertEquals(emptyList(), values("Spinach 55555"))
    }

    @Test fun iban() {
        assertEquals(listOf("GB82WEST12345698765432" to CodeKind.IBAN), values("Pay to GB82 WEST 1234 5698 7654 32 today"))
        assertEquals(emptyList(), values("Pay to GB82 WEST 1234 5698 7654 33 today")) // bad checksum
        assertTrue(SmartCodeExtractor.ibanValid("PK36SCBL0000001123456702"))
        assertEquals(listOf("PK36SCBL0000001123456702" to CodeKind.IBAN), values("IBAN PK36SCBL0000001123456702 please transfer"))
    }

    @Test fun cardAndAccount() {
        assertEquals(listOf("4111111111111111" to CodeKind.CARD), values("My card 4111 1111 1111 1111 exp 12/27"))
        assertEquals(emptyList(), values("number 4111 1111 1111 1112")) // fails Luhn, no account word
        assertEquals(listOf("0123456789012" to CodeKind.ACCOUNT), values("Account number: 0123456789012"))
        assertEquals(listOf("0123456789012" to CodeKind.ACCOUNT), values("اکاؤنٹ نمبر 0123456789012"))
        assertEquals(emptyList(), values("Order 0123456789012 shipped"))
    }

    @Test fun severalAndInOrder() {
        val found = SmartCodeExtractor.find("Account 0123456789012. Your OTP is 482916.")
        assertEquals(listOf("0123456789012", "482916"), found.map { it.value })
    }

    @Test fun phonesAndAccountLabels() {
        assertEquals(listOf("03001234567" to CodeKind.PHONE), values("Call me at 03001234567"))
        assertEquals(listOf("+923001234567" to CodeKind.PHONE), values("WhatsApp +92 300 1234567"))
        assertEquals(listOf("4155552671" to CodeKind.PHONE), values("Call (415) 555-2671"))
        assertEquals(listOf("03001234567" to CodeKind.PHONE), values("موبائل ۰۳۰۰۱۲۳۴۵۶۷"))
        assertEquals(listOf("03001234567" to CodeKind.ACCOUNT), values("Account 03001234567"))
        assertEquals(listOf("+442079460958" to CodeKind.PHONE), values("+44 20 7946 0958"))
        assertEquals(listOf("123456789012345678901234" to CodeKind.ACCOUNT), values("Account: 123456789012345678901234"))
    }

    @Test fun phoneLabelsDoNotSwallowVerificationCodesOrDates() {
        assertEquals(listOf("12345678" to CodeKind.OTP, "03001234567" to CodeKind.PHONE),
            values("Your OTP: 12345678. Phone 03001234567"))
        assertEquals(emptyList(), values("Call on 2026-10-25"))
    }

    @Test fun internationalPhoneWithParenthesizedAreaCode() {
        assertEquals(listOf("+14155552671" to CodeKind.PHONE), values("Phone: +1 (415) 555-2671"))
        assertEquals(emptyList(), values("IBAN: PK36SCBL0000001123456703"))
    }

    @Test fun mixedBankDetailsAreSeparateAndCorrectlyLabelled() {
        assertEquals(listOf("PK36SCBL0000001123456702" to CodeKind.IBAN,
            "03001234567" to CodeKind.PHONE, "0123456789012" to CodeKind.ACCOUNT),
            values("IBAN: PK36SCBL0000001123456702 Phone: 03001234567 Account: 0123456789012"))
        assertEquals(listOf("GB82WEST12345698765432" to CodeKind.IBAN),
            values("iban: gb82 west 1234 5698 7654 32 THANK YOU"))
    }

    @Test fun emailsLinksDeduplicationAndLimit() {
        assertEquals(listOf("hello@example.com" to CodeKind.EMAIL, "https://example.com/pay?id=123456" to CodeKind.LINK),
            values("Email hello@example.com or https://example.com/pay?id=123456."))
        assertEquals(listOf("03001234567" to CodeKind.PHONE), values("Call 03001234567. Phone 03001234567."))
        assertEquals(1, SmartCodeExtractor.find("Call 03001234567. Email hi@example.com", 1).size)
        assertTrue(SmartCodeExtractor.find("Phone 03001234567", 0).isEmpty())
    }

    @Test fun numbersInsideIdentifiersAreNotCodes() {
        assertEquals(emptyList(), values("Your code reference is ABC123456XYZ"))
        assertEquals(emptyList(), values("Phone balance Rs 12345678"))
    }

    @Test fun hugeTextIsCheap() {
        val big = "lorem ipsum ".repeat(100_000)
        val t0 = System.nanoTime(); assertEquals(emptyList(), SmartCodeExtractor.find(big)); assertTrue(System.nanoTime() - t0 < 50_000_000L)
        val digits = "1234 ".repeat(50_000)
        val t1 = System.nanoTime(); SmartCodeExtractor.find(digits); assertTrue(System.nanoTime() - t1 < 200_000_000L)
    }
}
