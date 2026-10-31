package com.moonlight.feed.data

import android.content.ContentValues
import android.app.Activity
import android.app.RecoverableSecurityException
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

object GallerySaver {
    const val IMAGE_REL = "Pictures/月光"
    const val VIDEO_REL = "Movies/月光"

    fun locationHint(): String =
        if (Build.VERSION.SDK_INT >= 29) "Pictures/月光 · Movies/月光（系统相册）"
        else "/sdcard/Pictures/月光 · /sdcard/Movies/月光"

    data class Result(val ok: Boolean, val uri: Uri? = null, val path: String? = null, val error: String? = null)

    suspend fun saveLocalFile(
        ctx: Context,
        file: File,
        isVideo: Boolean,
        displayName: String? = null,
    ): Result = withContext(Dispatchers.IO) {
        if (!file.exists() || file.length() == 0L) return@withContext Result(false, error = "文件不存在")
        file.inputStream().use { input ->
            writeStream(ctx, input, isVideo, displayName ?: file.name, guessMime(file.name, isVideo))
        }
    }

    suspend fun saveFromUrl(
        ctx: Context,
        http: OkHttpClient,
        url: String,
        isVideo: Boolean,
        displayName: String? = null,
    ): Result = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder().url(url).header("User-Agent", "Moonlight/2.1").build()
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext Result(false, error = "HTTP ${resp.code}")
                val body = resp.body ?: return@withContext Result(false, error = "空响应")
                val mime = body.contentType()?.toString()
                    ?: guessMime(url.substringAfterLast('/'), isVideo)
                val name = displayName
                    ?: url.substringAfterLast('/').substringBefore('?').ifBlank {
                        "moon_${System.currentTimeMillis()}"
                    }
                body.byteStream().use { input ->
                    writeStream(ctx, input, isVideo, name, mime)
                }
            }
        } catch (t: Throwable) {
            Result(false, error = t.message ?: "保存失败")
        }
    }

    suspend fun saveEntry(
        ctx: Context,
        http: OkHttpClient,
        entry: MediaEntry,
    ): Result {
        when (entry.kind) {
            MediaKind.BILI, MediaKind.EMBED -> {
                // Prefer downloadable remote; else cover only
                if (entry.kind == MediaKind.EMBED && !entry.remoteUrl.isNullOrBlank() &&
                    entry.resolvedPlatform() in setOf(
                        PlatformId.ARCHIVE, PlatformId.NASA, PlatformId.COMMONS,
                        PlatformId.PEERTUBE, PlatformId.PEXELS, PlatformId.PIXABAY, PlatformId.URL,
                    )
                ) {
                    // shouldn't happen for pure embed, but keep safe
                }
                if (!entry.remoteUrl.isNullOrBlank() && entry.kind == MediaKind.EMBED &&
                    listOf(".mp4", ".webm", ".ogv").any { entry.remoteUrl!!.lowercase().contains(it) }
                ) {
                    return saveFromUrl(ctx, http, entry.remoteUrl!!, isVideo = true, displayName = safeName(entry.title, true))
                }
                val cover = entry.coverUrl ?: return Result(false, error = "嵌入内容仅可保存封面")
                return saveFromUrl(ctx, http, cover, isVideo = false, displayName = "${entry.biliBvid ?: entry.platform ?: entry.id}_cover.jpg")
            }
            MediaKind.VIDEO, MediaKind.IMAGE -> {
                val isVideo = entry.kind == MediaKind.VIDEO
                entry.localPath?.let { path ->
                    return saveLocalFile(ctx, File(path), isVideo, displayName = safeName(entry.title, isVideo))
                }
                val url = entry.remoteUrl ?: return Result(false, error = "无文件可保存")
                return saveFromUrl(ctx, http, url, isVideo, displayName = safeName(entry.title, isVideo))
            }
        }
    }

    private fun safeName(title: String, isVideo: Boolean): String {
        val base = title.replace(Regex("[\\\\/:*?\"<>|]"), "_").take(40).ifBlank { "moon" }
        val ext = if (isVideo) ".mp4" else ".jpg"
        return if (base.contains('.')) base else base + ext
    }

    private fun guessMime(name: String, isVideo: Boolean): String {
        val n = name.lowercase()
        return when {
            n.endsWith(".png") -> "image/png"
            n.endsWith(".webp") -> "image/webp"
            n.endsWith(".gif") -> "image/gif"
            n.endsWith(".webm") -> "video/webm"
            n.endsWith(".mkv") -> "video/x-matroska"
            n.endsWith(".mp4") -> "video/mp4"
            isVideo -> "video/mp4"
            else -> "image/jpeg"
        }
    }

    private fun writeStream(
        ctx: Context,
        input: InputStream,
        isVideo: Boolean,
        displayName: String,
        mime: String,
    ): Result {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            writeMediaStore(ctx, input, isVideo, displayName, mime)
        } else {
            writeLegacy(ctx, input, isVideo, displayName)
        }
    }

    private fun writeMediaStore(
        ctx: Context,
        input: InputStream,
        isVideo: Boolean,
        displayName: String,
        mime: String,
    ): Result {
        val collection = if (isVideo) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        }
        val relative = if (isVideo) VIDEO_REL else IMAGE_REL
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, relative)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val resolver = ctx.contentResolver
        val uri = resolver.insert(collection, values)
            ?: return Result(false, error = "无法创建相册条目")
        try {
            resolver.openOutputStream(uri)?.use { out -> input.copyTo(out) }
                ?: return Result(false, error = "无法写入相册").also {
                    resolver.delete(uri, null, null)
                }
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            return Result(true, uri = uri, path = "$relative/$displayName")
        } catch (t: Throwable) {
            runCatching { resolver.delete(uri, null, null) }
            return Result(false, error = t.message ?: "写入失败")
        }
    }

    private fun writeLegacy(
        ctx: Context,
        input: InputStream,
        isVideo: Boolean,
        displayName: String,
    ): Result {
        val root = if (isVideo) {
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
        } else {
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
        }
        val dir = File(root, "月光").also { it.mkdirs() }
        val outFile = File(dir, displayName)
        try {
            FileOutputStream(outFile).use { out -> input.copyTo(out) }
            MediaScannerConnection.scanFile(
                ctx,
                arrayOf(outFile.absolutePath),
                arrayOf(guessMime(displayName, isVideo)),
                null,
            )
            return Result(true, uri = Uri.fromFile(outFile), path = outFile.absolutePath)
        } catch (t: Throwable) {
            return Result(false, error = t.message ?: "写入失败")
        }
    }

    suspend fun deleteGalleryCopy(ctx: Context, entry: MediaEntry): Result = withContext(Dispatchers.IO) {
        try {
            val isVideo = entry.kind == MediaKind.VIDEO
            val coll = if (isVideo) {
                if (Build.VERSION.SDK_INT >= 29) MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                else MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            } else {
                if (Build.VERSION.SDK_INT >= 29) MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            }
            val names = buildList {
                add(safeName(entry.title, isVideo))
                entry.biliBvid?.let { add("${it}_cover.jpg") }
                entry.id.take(8).let { add(it) }
            }
            val uris = mutableListOf<Uri>()
            val projection = arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME)
            ctx.contentResolver.query(coll, projection, null, null, "${MediaStore.MediaColumns.DATE_ADDED} DESC")?.use { c ->
                val idCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val nameCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                var scanned = 0
                while (c.moveToNext() && scanned < 400) {
                    scanned++
                    val name = c.getString(nameCol) ?: continue
                    if (names.any { n -> n.isNotBlank() && (name == n || name.contains(n.take(10))) }) {
                        uris += Uri.withAppendedPath(coll, c.getLong(idCol).toString())
                    }
                }
            }
            if (uris.isEmpty() && Build.VERSION.SDK_INT < 29) {
                val root = if (isVideo) Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
                else Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                File(root, "月光").listFiles()?.forEach { f ->
                    if (names.any { n -> n.isNotBlank() && f.name.contains(n.take(8)) }) f.delete()
                }
                return@withContext Result(true, path = "legacy")
            }
            if (uris.isEmpty()) return@withContext Result(false, error = "未找到相册副本")
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && ctx is Activity -> {
                    val req = MediaStore.createDeleteRequest(ctx.contentResolver, uris)
                    ctx.startIntentSenderForResult(req.intentSender, 2002, null, 0, 0, 0)
                    Result(true, path = "pending_confirm")
                }
                else -> {
                    var ok = 0
                    for (u in uris) {
                        try {
                            if (ctx.contentResolver.delete(u, null, null) > 0) ok++
                        } catch (e: SecurityException) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                                e is RecoverableSecurityException && ctx is Activity
                            ) {
                                ctx.startIntentSenderForResult(
                                    e.userAction.actionIntent.intentSender, 2003, null, 0, 0, 0
                                )
                            }
                        }
                    }
                    Result(ok > 0, path = "deleted=$ok")
                }
            }
        } catch (t: Throwable) {
            Result(false, error = t.message)
        }
    }

}
