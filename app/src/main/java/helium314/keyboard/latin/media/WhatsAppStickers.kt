// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.media

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.res.AssetFileDescriptor
import android.database.Cursor
import android.database.MatrixCursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.FileNotFoundException
import java.util.UUID

/** WhatsApp's public sticker-pack protocol; no WhatsApp SDK or account access. */
object WhatsAppStickers {
    const val ID = "lafz_local"
    private const val NAME = "Lafz personal stickers"
    internal val lock = Any()
    internal fun directory(context: Context) = File(context.filesDir, "whatsapp-stickers")

    fun createPack(context: Context) {
        check(MediaPrefs.enabled(context)) { "Media is disabled." }
        val images = MediaRepository.libraryDir(context).listFiles().orEmpty()
            .filter { it.extension in listOf("png", "jpg", "webp") }
            .filter { it.extension != "webp" || !animatedWebp(it.readBytes()) }
            .sortedByDescending { it.lastModified() }
        check(images.size in 3..30) { "Import 3–30 static images into Local first. GIFs and animated WebP are excluded." }
        val temp = File(context.filesDir, "sticker-build-${UUID.randomUUID()}").apply { mkdirs() }
        try {
            images.forEachIndexed { index, file ->
                val bitmap = stickerBitmap(file)
                try {
                    val output = File(temp, "$index.webp")
                    @Suppress("DEPRECATION")
                    for (quality in listOf(90, 75, 55, 35, 15)) {
                        output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.WEBP, quality, it) }
                        if (output.length() <= 100 * 1024) break
                    }
                    check(output.length() in 1..100 * 1024) { "An image cannot fit WhatsApp's sticker limit." }
                    if (index == 0) {
                        val tray = Bitmap.createScaledBitmap(bitmap, 96, 96, true)
                        try { File(temp, "tray.png").outputStream().use { tray.compress(Bitmap.CompressFormat.PNG, 100, it) } }
                        finally { tray.recycle() }
                    }
                } finally { bitmap.recycle() }
            }
            File(temp, "version").writeText(System.currentTimeMillis().toString())
            synchronized(lock) {
                val old = directory(context)
                val backup = File(context.filesDir, "whatsapp-stickers-old")
                backup.deleteRecursively()
                check(!old.exists() || old.renameTo(backup)) { "Could not update sticker pack." }
                if (!temp.renameTo(old)) { backup.renameTo(old); error("Could not save sticker pack.") }
                backup.deleteRecursively()
            }
        } finally { temp.deleteRecursively() }
    }

    internal fun animatedWebp(bytes: ByteArray): Boolean {
        var offset = 12
        while (offset + 8 <= bytes.size) {
            val tag = String(bytes, offset, 4, Charsets.US_ASCII)
            if (tag == "ANIM" || tag == "ANMF") return true
            val size = (0..3).fold(0L) { value, i -> value or ((bytes[offset + 4 + i].toLong() and 255) shl (8 * i)) }
            if (size > bytes.size - offset - 8) return false
            offset += 8 + size.toInt() + (size.toInt() and 1)
        }
        return false
    }

    internal fun stickerBitmap(file: File): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Invalid image." }
        val options = BitmapFactory.Options().apply { inSampleSize = 1 }
        while (maxOf(bounds.outWidth, bounds.outHeight) / options.inSampleSize > 1024) options.inSampleSize *= 2
        val source = requireNotNull(BitmapFactory.decodeFile(file.path, options)) { "Cannot decode image." }
        try {
            val target = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
            val scale = minOf(480f / source.width, 480f / source.height)
            val width = source.width * scale
            val height = source.height * scale
            Canvas(target).drawBitmap(source, null, RectF((512 - width) / 2, (512 - height) / 2,
                (512 + width) / 2, (512 + height) / 2), Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
            return target
        } finally { source.recycle() }
    }

    fun addIntent(context: Context): Intent {
        val whatsapp = try { context.packageManager.getPackageInfo("com.whatsapp", 0); "com.whatsapp" }
        catch (_: android.content.pm.PackageManager.NameNotFoundException) { "com.whatsapp.w4b" }
        return Intent("com.whatsapp.intent.action.ENABLE_STICKER_PACK").setPackage(whatsapp)
            .putExtra("sticker_pack_id", ID).putExtra("sticker_pack_authority", context.packageName + ".stickers")
            .putExtra("sticker_pack_name", NAME)
    }
}

/** Only validated pack assets are exposed, guarded by WhatsApp's read permission in the manifest. */
class WhatsAppStickerProvider : ContentProvider() {
    override fun onCreate() = true
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor =
        synchronized(WhatsAppStickers.lock) {
            val dir = WhatsAppStickers.directory(requireNotNull(context))
            val parts = uri.pathSegments
            when {
                parts == listOf("metadata") || parts == listOf("metadata", WhatsAppStickers.ID) -> {
                    MatrixCursor(arrayOf("sticker_pack_identifier", "sticker_pack_name", "sticker_pack_publisher", "sticker_pack_icon",
                        "android_play_store_link", "ios_app_download_link", "sticker_pack_publisher_email", "sticker_pack_publisher_website",
                        "sticker_pack_privacy_policy_website", "sticker_pack_license_agreement_website", "image_data_version",
                        "whatsapp_will_not_cache_stickers", "animated_sticker_pack")).apply {
                        if (File(dir, "version").exists()) addRow(arrayOf<Any>(WhatsAppStickers.ID, "Lafz personal stickers", "Lafz", "tray.png",
                            "", "", "", "", "", "", File(dir, "version").readText(), 0, 0))
                    }
                }
                parts == listOf("stickers", WhatsAppStickers.ID) -> {
                    MatrixCursor(arrayOf("sticker_file_name", "sticker_emoji", "sticker_accessibility_text")).apply {
                        dir.listFiles().orEmpty().filter { it.name.matches(Regex("[0-9]+\\.webp")) }.sortedBy { it.name }.forEach {
                            addRow(arrayOf(it.name, "", "Personal sticker"))
                        }
                    }
                }
                else -> throw IllegalArgumentException("Unknown sticker URI")
            }
        }

    override fun getType(uri: Uri): String {
        val parts = uri.pathSegments
        return when {
            parts.size == 3 && parts[0] == "stickers_asset" && parts[1] == WhatsAppStickers.ID && parts[2] == "tray.png" -> "image/png"
            parts.size == 3 && parts[0] == "stickers_asset" && parts[1] == WhatsAppStickers.ID && parts[2].matches(Regex("[0-9]+\\.webp")) -> "image/webp"
            parts == listOf("metadata") -> "vnd.android.cursor.dir/vnd.${uri.authority}.metadata"
            parts == listOf("metadata", WhatsAppStickers.ID) -> "vnd.android.cursor.item/vnd.${uri.authority}.metadata"
            parts == listOf("stickers", WhatsAppStickers.ID) -> "vnd.android.cursor.dir/vnd.${uri.authority}.stickers"
            else -> throw IllegalArgumentException("Unknown sticker URI")
        }
    }

    override fun openAssetFile(uri: Uri, mode: String): AssetFileDescriptor = synchronized(WhatsAppStickers.lock) {
        if (mode != "r") throw FileNotFoundException("Read only")
        val type = getType(uri)
        if (type != "image/webp" && type != "image/png") throw FileNotFoundException()
        val file = File(WhatsAppStickers.directory(requireNotNull(context)), uri.lastPathSegment!!)
        AssetFileDescriptor(ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY), 0, file.length())
    }
    override fun insert(uri: Uri, values: ContentValues?): Uri = throw UnsupportedOperationException()
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = throw UnsupportedOperationException()
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = throw UnsupportedOperationException()
}
