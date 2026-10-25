// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.media

import android.content.Context
import android.net.Uri
import android.view.inputmethod.EditorInfo
import androidx.core.content.edit
import helium314.keyboard.latin.utils.prefs
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.test.*

@RunWith(RobolectricTestRunner::class)
class MediaRepositoryTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()

    @BeforeTest fun reset() {
        context.prefs().edit { clear() }
        MediaRepository.libraryDir(context).deleteRecursively()
        WhatsAppStickers.directory(context).deleteRecursively()
    }

    @Test fun `URLs reject cleartext credentials deceptive domains and private hosts`() {
        for (url in listOf("http://media.giphy.com/x.gif", "https://giphy.com.evil.test/x", "https://evilgiphy.com/x",
            "https://user@giphy.com/x", "https://giphy.com:444/x", "file:///tmp/x", "https://127.0.0.1/x"))
            assertFalse(MediaRepository.isAllowedUrl(url), url)
        assertTrue(MediaRepository.isAllowedUrl("https://media2.giphy.com/media/test/giphy.gif?x=1"))
    }

    @Test fun `bundled key needs no setup and can be overridden or bypassed`() {
        assertTrue(MediaPrefs.apiKey(context) == helium314.keyboard.latin.BuildConfig.GIPHY_API_KEY, "Build key should be used without setup")
        assertEquals(helium314.keyboard.latin.BuildConfig.GIPHY_API_KEY.isNotEmpty(), MediaPrefs.useApi(context))
        context.prefs().edit { putString(MediaPrefs.KEY, "personal-test-key") }
        assertEquals("personal-test-key", MediaPrefs.apiKey(context))
        context.prefs().edit { putBoolean(MediaPrefs.USE_API, false) }
        assertFalse(MediaPrefs.useApi(context))
        assertFalse(MediaPrefs.enabled(context))
    }

    @Test fun `disabled gates and missing key fail before any network request`() = runBlocking {
        val repository = MediaRepository(context)
        assertFailsWith<IllegalStateException> { repository.search(MediaKind.GIFS, "hello", 0) }
        context.prefs().edit { putBoolean(MediaPrefs.ENABLED, true) }
        context.prefs().edit { putBoolean(MediaPrefs.USE_API, true); putString(MediaPrefs.KEY, "") }
        assertFailsWith<IllegalStateException> { repository.search(MediaKind.GIFS, "hello", 0) }
        Unit
    }

    @Test fun `unknown length streams cannot exceed byte limit`() {
        val output = ByteArrayOutputStream()
        assertFailsWith<IllegalArgumentException> {
            MediaRepository.copyBounded(ByteArrayInputStream(ByteArray(100_000)), output, 1000)
        }
        assertTrue(output.size() <= 1000)
        val exact = ByteArrayOutputStream()
        MediaRepository.copyBounded(ByteArrayInputStream(ByteArray(1000)), exact, 1000)
        assertEquals(1000, exact.size())
    }

    @Test fun `GIPHY parser skips untrusted media and preserves gif delivery`() {
        val json = JSONObject("""{"data":[
            {"id":"good","title":"Hello","images":{"downsized":{"url":"https://media.giphy.com/a.gif"},"fixed_width_still":{"url":"https://media.giphy.com/a.jpg"}}},
            {"id":"bad","images":{"original":{"url":"https://evil.test/a.gif"},"original_still":{"url":"https://media.giphy.com/a.jpg"}}}
        ]}""")
        val parsed = MediaRepository.parseGiphy(json)
        assertEquals(1, parsed.size)
        assertEquals("image/gif", parsed.single().mime)
        assertEquals("Hello", parsed.single().title)
    }

    private fun webPage(vararg entries: JSONObject): String {
        val payload = JSONObject().put("initialGifs", org.json.JSONArray(entries.toList())).toString()
        // React may split JSON between independently escaped script chunks.
        return payload.chunked(70).joinToString("") {
            "<script>self.__next_f.push(" + org.json.JSONArray().put(1).put(it) + ")</script>"
        }
    }

    private fun webGif(id: String, rating: String = "g", sticker: Int = 0) = JSONObject("""{
        "id":"$id","rating":"$rating","is_sticker":$sticker,"title":"Reaction",
        "images":{"downsized":{"url":"https://media.giphy.com/a.gif"},"fixed_width_still":{"url":"https://media.giphy.com/a.jpg"}}
    }""")

    @Test fun `keyless web results filter ratings and stickers without extracting credentials`() {
        val html = webPage(webGif("safe"), webGif("mature", "r"), webGif("unknown", ""), webGif("sticker", sticker = 1),
            webGif("hidden").put("is_hidden", 1))
        assertEquals(listOf("safe"), MediaRepository.parseGiphyWeb(html, false, false).map { it.id })
        assertEquals(listOf("safe", "mature"), MediaRepository.parseGiphyWeb(html, false, true).map { it.id })
        assertEquals(listOf("sticker"), MediaRepository.parseGiphyWeb(html, true, false).map { it.id })
        assertEquals("GIPHY web", MediaRepository.parseGiphyWeb(html, false, false).single().source)
    }

    @Test fun `public page changes fail clearly rather than silently returning no results`() {
        val error = assertFailsWith<IllegalStateException> { MediaRepository.parseGiphyWeb("<html>No results data</html>", false, false) }
        assertTrue(error.message!!.contains("page format changed"))
    }

    @Test fun `keyless URLs encode search text as a path without appending any API key`() {
        val url = MediaRepository.webSearchUrl(MediaKind.STICKERS, "hello /? key=secret & wave")
        val uri = Uri.parse(url)
        assertEquals("giphy.com", uri.host)
        assertNull(uri.query)
        assertEquals(2, uri.pathSegments.size)
        assertTrue(uri.lastPathSegment!!.endsWith("-stickers"))
        assertEquals("https://giphy.com/search/reactions", MediaRepository.webSearchUrl(MediaKind.GIFS, ""))
    }

    @Test fun `editor handoff requires package field and input type to match`() {
        val target = MediaInsertion.Target("com.whatsapp", 42, 1)
        val editor = EditorInfo().apply { packageName = "com.whatsapp"; fieldId = 42; inputType = 1 }
        assertTrue(target.matches(editor))
        editor.packageName = "other.app"
        assertFalse(target.matches(editor))
        editor.packageName = "com.whatsapp"; editor.fieldId = 43
        assertFalse(target.matches(editor))
        editor.fieldId = 42; editor.inputType = 129
        assertFalse(target.matches(editor))
    }

    @Test fun `file validation refuses HTML masquerading as GIF`() {
        val file = File(context.cacheDir, "invalid.gif").apply { writeText("<html>not a gif</html>") }
        assertFailsWith<IllegalArgumentException> { MediaRepository.validate(file, "image/gif") }
        file.writeBytes("GIF89a".toByteArray() + ByteArray(16))
        MediaRepository.validate(file, "image/gif")
    }

    @Test fun `WhatsApp pack keeps old data if fewer than three images are supplied`() {
        context.prefs().edit { putBoolean(MediaPrefs.ENABLED, true) }
        val marker = File(WhatsAppStickers.directory(context).apply { mkdirs() }, "version").apply { writeText("old") }
        assertFailsWith<IllegalStateException> { WhatsAppStickers.createPack(context) }
        assertEquals("old", marker.readText())
    }

    @Test fun `animated WebP chunks are excluded from static pack`() {
        val header = "RIFF".toByteArray() + ByteArray(4) + "WEBP".toByteArray()
        assertTrue(WhatsAppStickers.animatedWebp(header + "ANIM".toByteArray() + ByteArray(8)))
        assertFalse(WhatsAppStickers.animatedWebp(header + "VP8 ".toByteArray() + ByteArray(8)))
        assertFalse(WhatsAppStickers.animatedWebp(header + "VP8 ".toByteArray() + byteArrayOf(-1,-1,-1,-1)))
    }

    @Test fun `sticker provider returns protocol metadata and rejects arbitrary files`() {
        val dir = WhatsAppStickers.directory(context).apply { mkdirs() }
        File(dir, "version").writeText("123")
        File(dir, "0.webp").writeText("test")
        val provider = Robolectric.buildContentProvider(WhatsAppStickerProvider::class.java).create().get()
        val authority = context.packageName + ".stickers"
        provider.query(Uri.parse("content://$authority/metadata/lafz_local"), null, null, null, null).use {
            assertTrue(it.moveToFirst())
            assertEquals("lafz_local", it.getString(it.getColumnIndexOrThrow("sticker_pack_identifier")))
            assertEquals(0, it.getInt(it.getColumnIndexOrThrow("animated_sticker_pack")))
        }
        provider.query(Uri.parse("content://$authority/stickers/lafz_local"), null, null, null, null).use {
            assertEquals(1, it.count)
        }
        assertFailsWith<IllegalArgumentException> { provider.getType(Uri.parse("content://$authority/stickers_asset/lafz_local/../secret")) }
        assertFailsWith<java.io.FileNotFoundException> { provider.openAssetFile(Uri.parse("content://$authority/stickers_asset/lafz_local/0.webp"), "w") }
    }

    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `exported stickers meet WhatsApp image size byte and transparency constraints`() {
        context.prefs().edit { putBoolean(MediaPrefs.ENABLED, true) }
        for (i in 0..2) {
            val image = android.graphics.Bitmap.createBitmap(720, 360, android.graphics.Bitmap.Config.ARGB_8888)
            image.eraseColor(android.graphics.Color.rgb(50 + i * 50, 100, 150))
            File(MediaRepository.libraryDir(context), "$i.png").outputStream().use {
                assertTrue(image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it))
            }
            image.recycle()
        }
        WhatsAppStickers.createPack(context)
        val dir = WhatsAppStickers.directory(context)
        assertEquals(3, dir.listFiles()!!.count { it.extension == "webp" })
        for (file in dir.listFiles()!!.filter { it.extension == "webp" }) {
            assertTrue(file.length() in 1..102400)
            MediaRepository.validate(file, "image/webp")
            val bitmap = android.graphics.BitmapFactory.decodeFile(file.path)
            assertEquals(512, bitmap.width)
            assertEquals(512, bitmap.height)
            assertEquals(0, android.graphics.Color.alpha(bitmap.getPixel(0, 0)))
            assertEquals(255, android.graphics.Color.alpha(bitmap.getPixel(256, 256)))
            bitmap.recycle()
        }
        val tray = File(dir, "tray.png")
        assertTrue(tray.length() <= 51200)
        val bitmap = android.graphics.BitmapFactory.decodeFile(tray.path)
        assertEquals(96, bitmap.width)
        assertEquals(96, bitmap.height)
        bitmap.recycle()
    }
}
