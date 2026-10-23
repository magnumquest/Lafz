// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.urdu

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EmojiKeywordsTest {
    private val keywords = File("src/main/assets/emoji_keywords.tsv").reader().use { EmojiKeywords.load(it) }

    @Test fun `English triggers match whole words only`() {
        assertEquals("❤️", keywords.suggest("LOVE", 2).first())
        assertEquals("🤗", keywords.suggest("hug", 2).first())
        assertTrue(keywords.suggest("glove", 2).isEmpty())
        assertTrue(keywords.suggest("lov", 2).isEmpty())
        assertTrue(keywords.suggest("unknownword", 2).isEmpty())
    }

    @Test fun `Roman and native Urdu and Arabic match`() {
        for (word in listOf("pyar", "mohabbat", "محبت", "پیار", "حُبّ", "\u200fحب"))
            assertEquals("❤️", keywords.suggest(word, 2).first(), word)
        for (word in listOf("hug", "jhappi", "عناق"))
            assertEquals("🤗", keywords.suggest(word, 2).first(), word)
    }

    @Test fun `phrase keywords and bounds are respected`() {
        assertEquals("🎂", keywords.suggest("happy birthday", 2).first())
        assertEquals(1, keywords.suggest("love", 1).size)
        assertTrue(keywords.suggest("love", 0).isEmpty())
        assertTrue(keywords.suggest(null, 2).isEmpty())
    }

    @Test fun `aliases merge without duplicate emoji`() {
        val index = EmojiKeywords.load("❤️ 🥰\tlove\n❤️ 😍\tlove\n".reader())
        assertEquals(listOf("❤️", "🥰", "😍"), index.suggest("love", 10))
    }
}
