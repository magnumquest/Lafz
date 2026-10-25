// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.media

import android.content.Context
import android.net.Uri
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.latin.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.json.JSONObject
import org.json.JSONArray
import org.json.JSONTokener
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

object MediaPrefs {
    const val ENABLED = "media_enabled"
    const val KEY = "media_giphy_key"
    const val USE_API = "media_use_giphy_api"
    const val MATURE = "media_mature"
    fun enabled(context: Context) = context.prefs().getBoolean(ENABLED, false)
    fun useApi(context: Context) = context.prefs().getBoolean(USE_API, BuildConfig.GIPHY_API_KEY.isNotEmpty())
    fun apiKey(context: Context) = context.prefs().getString(KEY, null)?.trim() ?: BuildConfig.GIPHY_API_KEY
}

enum class MediaKind { GIFS, STICKERS, LOCAL }

data class MediaItem(
    val id: String, val title: String, val preview: String, val url: String,
    val mime: String, val source: String
)

/** No singleton HTTP client or provider SDK is initialized by the IME. */
class MediaRepository(private val context: Context) {
    private val requests = Semaphore(3)
    private var webQuery: String? = null
    private var webResults = emptyList<MediaItem>()

    suspend fun search(kind: MediaKind, query: String, page: Int): List<MediaItem> = withContext(Dispatchers.IO) {
        check(MediaPrefs.enabled(context)) { "Media is disabled in Settings." }
        if (kind == MediaKind.LOCAL) return@withContext local()
        if (!MediaPrefs.useApi(context)) return@withContext webSearch(kind, query, page)
        val key = MediaPrefs.apiKey(context)
        check(key.isNotEmpty()) { "Turn off the advanced GIPHY API option to search without a key, or add your own API key." }
        val type = if (kind == MediaKind.STICKERS) "stickers" else "gifs"
        val endpoint = if (query.isBlank()) "trending" else "search"
        val uri = Uri.parse("https://api.giphy.com/v1/$type/$endpoint").buildUpon()
            .appendQueryParameter("api_key", key).appendQueryParameter("limit", "24")
            .appendQueryParameter("offset", (page * 24).toString())
            .appendQueryParameter("rating", if (context.prefs().getBoolean(MediaPrefs.MATURE, false)) "r" else "pg")
            .appendQueryParameter("lang", "en")
        if (query.isNotBlank()) uri.appendQueryParameter("q", query.take(50))
        try { parseGiphy(json(uri.build().toString())) }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { webSearch(kind, query, page) }
    }

    private suspend fun webSearch(kind: MediaKind, query: String, page: Int): List<MediaItem> {
        val mature = context.prefs().getBoolean(MediaPrefs.MATURE, false)
        val cacheKey = "$kind:$query:$mature"
        if (page == 0 || webQuery != cacheKey) {
            val output = java.io.ByteArrayOutputStream()
            request(webSearchUrl(kind, query), 3 * 1024 * 1024, output)
            webResults = parseGiphyWeb(output.toString("UTF-8"), kind == MediaKind.STICKERS, mature)
            webQuery = cacheKey
        }
        return webResults.drop(page * 24).take(24)
    }

    private suspend fun json(url: String): JSONObject {
        val bytes = java.io.ByteArrayOutputStream()
        request(url, 2 * 1024 * 1024, bytes)
        return JSONObject(bytes.toString("UTF-8"))
    }

    suspend fun thumbnail(item: MediaItem): ByteArray = withContext(Dispatchers.IO) {
        requireEnabled()
        if (item.preview.startsWith("file:")) return@withContext File(Uri.parse(item.preview).path!!).inputStream().use {
            val bytes = java.io.ByteArrayOutputStream(); copyBounded(it, bytes, MAX_MEDIA); bytes.toByteArray()
        }
        val bytes = java.io.ByteArrayOutputStream()
        request(item.preview, 1024 * 1024, bytes)
        bytes.toByteArray()
    }

    suspend fun download(item: MediaItem): File = withContext(Dispatchers.IO) {
        requireEnabled()
        if (item.url.startsWith("file:")) return@withContext File(Uri.parse(item.url).path!!)
        val directory = File(context.cacheDir, "media").apply { mkdirs() }
        // Keep files long enough for receiving apps to read granted URIs.
        directory.listFiles()?.filter { System.currentTimeMillis() - it.lastModified() > 86_400_000 }?.forEach { it.delete() }
        val available = 64L * 1024 * 1024 - directory.listFiles().orEmpty().sumOf { it.length() }
        check(available > 0) {
            "Transfer cache is full. Clear it in media settings after recipients finish reading."
        }
        val file = File(directory, UUID.randomUUID().toString() + extension(item.mime))
        try {
            file.outputStream().use { request(item.url, minOf(MAX_MEDIA.toLong(), available).toInt(), it) }
            validate(file, item.mime)
            file
        } catch (e: Exception) { file.delete(); throw e }
    }

    private fun requireEnabled() {
        check(MediaPrefs.enabled(context)) { "Media is disabled." }
    }

    suspend fun save(item: MediaItem, file: File): MediaItem = withContext(Dispatchers.IO) {
        requireEnabled()
        val dir = libraryDir(context)
        check(dir.listFiles().orEmpty().sumOf { it.length() } + file.length() <= 64L * 1024 * 1024) { "Local library is full (64 MB)." }
        val dest = File(dir, UUID.randomUUID().toString() + extension(item.mime))
        file.copyTo(dest)
        item.copy(id = dest.name, url = Uri.fromFile(dest).toString(), preview = Uri.fromFile(dest).toString(), source = "Local")
    }

    suspend fun import(uri: Uri): MediaItem = withContext(Dispatchers.IO) {
        check(MediaPrefs.enabled(context)) { "Media is disabled." }
        val mime = context.contentResolver.getType(uri).orEmpty()
        check(mime in listOf("image/gif", "image/webp", "image/png", "image/jpeg")) { "Choose a GIF, WebP, PNG or JPEG image." }
        val temp = File(context.cacheDir, UUID.randomUUID().toString() + extension(mime))
        try {
            context.contentResolver.openInputStream(uri)!!.use { input -> temp.outputStream().use { copyBounded(input, it, MAX_MEDIA) } }
            validate(temp, mime)
            save(MediaItem("", "Imported image", "", "", mime, "Local"), temp)
        } finally { temp.delete() }
    }

    fun local(): List<MediaItem> = libraryDir(context).listFiles().orEmpty()
        .filter { it.extension in listOf("gif", "webp", "png", "jpg") }.sortedByDescending { it.lastModified() }.map {
            val mime = when (it.extension) { "gif" -> "image/gif"; "webp" -> "image/webp"; "jpg" -> "image/jpeg"; else -> "image/png" }
            MediaItem(it.name, "Saved ${it.extension.uppercase()}", Uri.fromFile(it).toString(), Uri.fromFile(it).toString(), mime, "Local")
        }

    private suspend fun request(address: String, limit: Int, output: OutputStream) {
        requests.withPermit { requestLimited(address, limit, output) }
    }

    private suspend fun requestLimited(address: String, limit: Int, output: OutputStream) {
        require(isAllowedUrl(address)) { "Unsupported media address." }
        currentCoroutineContext().ensureActive()
        val connection = URL(address).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 8000
            connection.readTimeout = 8000
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("User-Agent", "Lafz/1.0 (Android personal keyboard)")
            val code = connection.responseCode
            check(code in 200..299) {
                when (code) {
                    401, 403 -> "Provider denied access ($code). Check your key or network."
                    429 -> "Provider rate limit reached. Try again later."
                    else -> "Provider request failed ($code)."
                }
            }
            check(connection.contentLength <= limit) { "Media exceeds the download limit." }
            connection.inputStream.use { input ->
                val buffer = ByteArray(16 * 1024)
                var total = 0
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val n = input.read(buffer)
                    if (n < 0) break
                    total += n
                    check(total <= limit) { "Media exceeds the download limit." }
                    output.write(buffer, 0, n)
                }
            }
        } finally { connection.disconnect() }
    }

    companion object {
        const val MAX_MEDIA = 12 * 1024 * 1024
        fun webSearchUrl(kind: MediaKind, query: String): String {
            val term = query.trim().take(50).ifEmpty { "reactions" }
            val slug = term.replace(Regex("\\s+"), "-") + if (kind == MediaKind.STICKERS) "-stickers" else ""
            return Uri.Builder().scheme("https").authority("giphy.com").appendPath("search").appendPath(slug).build().toString()
        }

        /** Read only the public page's rendered results. Never extract or reuse website API keys. */
        fun parseGiphyWeb(html: String, stickers: Boolean, mature: Boolean): List<MediaItem> {
            val flight = StringBuilder()
            val scripts = Regex("<script\\b[^>]*>\\s*self\\.__next_f\\.push\\((.*?)\\)\\s*;?\\s*</script>",
                setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
            for (script in scripts.findAll(html)) {
                val chunk = runCatching { JSONArray(script.groupValues[1]) }.getOrNull() ?: continue
                if (chunk.optInt(0) == 1 && chunk.opt(1) is String) flight.append(chunk.getString(1))
            }
            val match = Regex("\"initialGifs\"\\s*:\\s*(\\[)").find(flight)
                ?: error("GIPHY web search is unavailable or its page format changed. Try Local or the optional API provider.")
            val initial = JSONTokener(flight.substring(match.groups[1]!!.range.first)).nextValue() as? JSONArray
                ?: error("GIPHY returned an unsupported search page.")
            val filtered = JSONArray()
            val ratings = if (mature) setOf("g", "pg", "pg-13", "r") else setOf("g", "pg")
            for (i in 0 until minOf(initial.length(), 100)) {
                val gif = initial.optJSONObject(i) ?: continue
                if (gif.optString("rating") !in ratings || (gif.optInt("is_sticker", -1) == 1) != stickers ||
                    !gif.has("is_sticker") || gif.optBoolean("is_removed", false) || gif.optInt("is_removed") == 1 ||
                    gif.optInt("is_hidden") == 1) continue
                filtered.put(gif)
            }
            // Public pages currently expose an initial batch; pagination slices this batch locally.
            return parseGiphy(JSONObject().put("data", filtered), 100).map { it.copy(source = "GIPHY web") }
        }
        fun libraryDir(context: Context) = File(context.filesDir, "media-library").apply { mkdirs() }
        fun extension(mime: String) = when (mime) {
            "image/gif" -> ".gif"; "image/webp" -> ".webp"; "image/png" -> ".png"; "image/jpeg" -> ".jpg"
            else -> error("Unsupported media type")
        }
        fun isAllowedUrl(address: String): Boolean = runCatching {
            val url = java.net.URI(address)
            val host = url.host?.lowercase() ?: return false
            url.scheme == "https" && url.userInfo == null && url.port in listOf(-1, 443) &&
                (host == "giphy.com" || host.endsWith(".giphy.com"))
        }.getOrDefault(false)

        fun copyBounded(input: InputStream, output: OutputStream, limit: Int) {
            val buffer = ByteArray(16384)
            var total = 0
            while (true) {
                val n = input.read(buffer)
                if (n < 0) return
                total += n
                require(total <= limit) { "Media exceeds the download limit." }
                output.write(buffer, 0, n)
            }
        }

        fun validate(file: File, mime: String) {
            val head = ByteArray(12)
            val count = file.inputStream().use { it.read(head) }
            require(count >= 12 && when (mime) {
                "image/gif" -> String(head, 0, 6, Charsets.US_ASCII) in listOf("GIF87a", "GIF89a")
                "image/webp" -> String(head, 0, 4, Charsets.US_ASCII) == "RIFF" && String(head, 8, 4, Charsets.US_ASCII) == "WEBP"
                "image/png" -> head.take(8) == listOf(137,80,78,71,13,10,26,10).map { it.toByte() }
                "image/jpeg" -> head[0] == 0xff.toByte() && head[1] == 0xd8.toByte()
                else -> false
            }) { "Provider returned an invalid media file." }
        }

        fun parseGiphy(json: JSONObject, limit: Int = 24): List<MediaItem> {
            val data = json.getJSONArray("data")
            return (0 until minOf(limit, data.length())).mapNotNull { i -> runCatching {
                val obj = data.getJSONObject(i)
                val images = obj.getJSONObject("images")
                val media = images.optJSONObject("downsized")?.optString("url").orEmpty()
                    .ifEmpty { images.getJSONObject("original").getString("url") }
                val preview = images.optJSONObject("fixed_width_still")?.optString("url").orEmpty()
                    .ifEmpty { images.getJSONObject("original_still").getString("url") }
                require(isAllowedUrl(media) && isAllowedUrl(preview))
                MediaItem(obj.getString("id"), obj.optString("title", "GIPHY reaction"), preview, media, "image/gif", "GIPHY")
            }.getOrNull() }
        }

    }
}
