// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin

import helium314.keyboard.latin.voice.CtcOnnxEngine
import helium314.keyboard.latin.voice.VoiceModels
import helium314.keyboard.latin.voice.VoicePerfTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class VoiceTypingTest {
    @Test fun characterErrorRate() {
        assertEquals(0.0, VoicePerfTest.characterErrorRate("Hello, World!", "hello world"))
        assertEquals(0.2, VoicePerfTest.characterErrorRate("hello", "hallo"), 1e-9)
        assertEquals(1.0, VoicePerfTest.characterErrorRate("abc", ""), 1e-9)
        assertEquals(0.0, VoicePerfTest.characterErrorRate("مرحبا بك", "مرحبا بك."))
    }

    @Test fun wavReaderParsesPcm16() {
        val pcm = byteArrayOf(0, 0, 0, 64, 0, -64) // 0, 16384, -16384
        val header = ByteArray(44)
        "RIFF".toByteArray().copyInto(header); "WAVE".toByteArray().copyInto(header, 8); "fmt ".toByteArray().copyInto(header, 12)
        "data".toByteArray().copyInto(header, 36); header[40] = pcm.size.toByte()
        val f = VoicePerfTest.readWav(header + pcm)
        assertEquals(3, f.size)
        assertEquals(0.5f, f[1], 1e-6f); assertEquals(-0.5f, f[2], 1e-6f)
    }

    @Test fun tokenFileKeepsSpaceTokenAndOrder() {
        val file = Files.createTempFile("tokens", ".txt").toFile()
        file.writeText("<s> 0\n<pad> 1\n  2\nا 3\n", Charsets.UTF_8) // the token for id 2 is a single space
        val t = CtcOnnxEngine.readTokens(file)
        assertEquals(listOf("<s>", "<pad>", " ", "ا"), t.toList())
    }

    @Test fun modelDescriptorNeedsItsFiles() {
        val dir = Files.createTempDirectory("vm").toFile()
        File(dir, "model.json").writeText("""{"name":"X","language":"ur","model":"m.onnx","tokens":"t.txt","blank_id":1,"skip_ids":[0,2],"normalize":false}""")
        assertNull(VoiceModels.read(dir)) // files named in the descriptor are missing
        File(dir, "m.onnx").writeBytes(ByteArray(10)); File(dir, "t.txt").writeText("a 0\n")
        val info = assertNotNull(VoiceModels.read(dir))
        assertEquals("ur", info.language); assertEquals(1, info.blankId); assertEquals(setOf(0, 2), info.skipIds); assertTrue(!info.normalize)
        File(dir, "model.json").writeText("not json"); assertNull(VoiceModels.read(dir))
    }
}
