// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.updates

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import helium314.keyboard.latin.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Locale

data class GithubRelease(
    val versionName: String,
    val tag: String,
    val notes: String,
    val downloadUrl: String,
    val sizeBytes: Long,
    val sha256: String?,
)

object GithubReleaseUpdater {
    private const val API_URL = "https://api.github.com/repos/magnumquest/Lafz/releases/latest"
    private const val RELEASE_ASSET_PREFIX = "https://github.com/magnumquest/Lafz/releases/download/"
    private val stableTag = Regex("^v?(\\d+)\\.(\\d+)\\.(\\d+)$")

    suspend fun findUpdate(): GithubRelease? = withContext(Dispatchers.IO) {
        val connection = (URL(API_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 20_000
            requestMethod = "GET"
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            setRequestProperty("User-Agent", "Lafz-Android-Update-Checker")
        }
        try {
            if (connection.responseCode !in 200..299) throw IOException("GitHub returned HTTP ${connection.responseCode}")
            val release = JSONObject(connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() })
            val tag = release.optString("tag_name")
            val version = tag.removePrefix("v")
            if (compareVersions(version, BuildConfig.VERSION_NAME) <= 0) return@withContext null
            val assetName = "Lafz_${version}-release.apk"
            val assets = release.optJSONArray("assets") ?: throw IOException("Release has no APK assets")
            val asset = (0 until assets.length()).asSequence().map { assets.getJSONObject(it) }
                .firstOrNull { it.optString("name") == assetName }
                ?: throw IOException("Release is missing $assetName")
            val downloadUrl = asset.optString("browser_download_url")
            if (!downloadUrl.startsWith(RELEASE_ASSET_PREFIX)) throw IOException("Unexpected APK download source")
            val size = asset.optLong("size", -1L)
            if (size <= 0 || size > MAX_APK_BYTES) throw IOException("APK size is invalid or too large")
            val digest = asset.optString("digest").takeIf { it.startsWith("sha256:") }
                ?.substringAfter(':')?.lowercase(Locale.ROOT)
            GithubRelease(version, tag, release.optString("body").trim(), downloadUrl, size, digest)
        } finally {
            connection.disconnect()
        }
    }

    suspend fun download(
        context: Context,
        release: GithubRelease,
        onProgress: (Int) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        val directory = File(context.cacheDir, "updates").apply { mkdirs() }
        val partial = File(directory, "Lafz_${release.versionName}.apk.part")
        val destination = File(directory, "Lafz_${release.versionName}.apk")
        partial.delete()
        destination.delete()
        val connection = (URL(release.downloadUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 45_000
            instanceFollowRedirects = true
            requestMethod = "GET"
            setRequestProperty("User-Agent", "Lafz-Android-Update-Checker")
        }
        try {
            if (connection.responseCode !in 200..299) throw IOException("APK download failed (HTTP ${connection.responseCode})")
            val digest = MessageDigest.getInstance("SHA-256")
            var total = 0L
            var lastPercent = -1
            connection.inputStream.buffered().use { input ->
                FileOutputStream(partial).buffered().use { output ->
                    val buffer = ByteArray(128 * 1024)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        if (total > MAX_APK_BYTES || total > release.sizeBytes + SIZE_TOLERANCE_BYTES)
                            throw IOException("Downloaded APK exceeded its advertised size")
                        digest.update(buffer, 0, count)
                        output.write(buffer, 0, count)
                        val percent = (total * 100 / release.sizeBytes).toInt().coerceIn(0, 100)
                        if (percent != lastPercent) {
                            lastPercent = percent
                            withContext(Dispatchers.Main.immediate) { onProgress(percent) }
                        }
                    }
                }
            }
            if (total != release.sizeBytes) throw IOException("Downloaded APK size did not match the release")
            val actualDigest = digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
            if (release.sha256 != null && !actualDigest.equals(release.sha256, ignoreCase = true))
                throw IOException("APK checksum did not match the GitHub release")
            validatePackage(context, partial, release)
            if (!partial.renameTo(destination)) throw IOException("Could not stage the downloaded APK")
            destination
        } catch (e: Exception) {
            partial.delete()
            throw e
        } finally {
            connection.disconnect()
        }
    }

    fun install(context: Context, apk: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", apk)
        val intent = android.content.Intent(android.content.Intent.ACTION_INSTALL_PACKAGE).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    private fun validatePackage(context: Context, apk: File, release: GithubRelease) {
        val archive = context.packageManager.getPackageArchiveInfo(apk.absolutePath, PackageManager.GET_META_DATA)
            ?: throw IOException("Downloaded file is not a valid Android package")
        if (archive.packageName != context.packageName) throw IOException("APK package name does not match this Lafz installation")
        if (archive.versionName != release.versionName) throw IOException("APK version does not match the GitHub release")
        val installed = context.packageManager.getPackageInfo(context.packageName, 0)
        val installedCode = PackageInfoCompat.getLongVersionCode(installed)
        val downloadedCode = PackageInfoCompat.getLongVersionCode(archive)
        if (downloadedCode <= installedCode) throw IOException("Downloaded APK is not newer than this installation")
    }

    private fun compareVersions(left: String, right: String): Int {
        fun parts(value: String) = stableTag.matchEntire(value)?.groupValues?.drop(1)?.map(String::toInt)
            ?: throw IOException("Unsupported release version: $value")
        val a = parts(left)
        val b = parts(right)
        for (i in 0..2) if (a[i] != b[i]) return a[i].compareTo(b[i])
        return 0
    }

    private const val MAX_APK_BYTES = 300L * 1024 * 1024
    private const val SIZE_TOLERANCE_BYTES = 1024L
}
