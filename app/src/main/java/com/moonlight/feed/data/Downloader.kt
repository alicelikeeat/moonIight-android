package com.moonlight.feed.data

import android.content.Context
import com.moonlight.feed.MoonApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.util.UUID

object Downloader {
    class IncompleteDownloadException(msg: String) : Exception(msg)
    class NotMediaException(msg: String) : Exception(msg)

    suspend fun downloadUrl(
        url: String,
        titleHint: String = "",
        onProgress: (Float) -> Unit = {},
        saveToGallery: Boolean? = null,
    ): MediaEntry = withContext(Dispatchers.IO) {
        val app = MoonApp.instance
        val http = app.http // already follows redirects
        val lib = app.library
        val req = Request.Builder().url(url)
            .header("User-Agent", "Moonlight/2.3")
            .header("Accept", "video/*,image/*,*/*")
            .build()
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) error("HTTP ${resp.code}")
            val body = resp.body ?: error("空响应")
            val declaredLen = body.contentLength()
            val contentType = body.contentType()?.toString()
            if (contentType != null && contentType.lowercase().contains("text/html")) {
                throw NotMediaException("链接返回 HTML（可能需要登录或不是直链）")
            }

            val tmp = File(lib.mediaDir(), "tmp_${System.currentTimeMillis()}.part")
            var written = 0L
            body.byteStream().use { input ->
                tmp.outputStream().use { output ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n <= 0) break
                        output.write(buf, 0, n)
                        written += n
                        if (declaredLen > 0) onProgress((written.toFloat() / declaredLen).coerceIn(0f, 1f))
                    }
                    output.flush()
                }
            }

            if (written == 0L) {
                tmp.delete()
                throw IncompleteDownloadException("下载为 0 字节")
            }
            if (declaredLen > 0 && written < declaredLen) {
                tmp.delete()
                throw IncompleteDownloadException("不完整：已写 $written / 期望 $declaredLen")
            }

            val probe = MediaProbe.sniff(tmp)
            if (probe.kind == MediaProbe.Kind.HTML) {
                tmp.delete()
                throw NotMediaException(probe.note)
            }
            if (!probe.isVideo && !probe.isImage) {
                // allow octet-stream if URL extension hints
                val hintVideo = url.contains(".mp4", true) || url.contains(".webm", true)
                val hintImage = url.contains(".jpg", true) || url.contains(".png", true) || url.contains(".webp", true)
                if (!hintVideo && !hintImage) {
                    tmp.delete()
                    throw NotMediaException("无法识别为图片/视频（${probe.note}）")
                }
            }

            val isVideo = probe.isVideo || (probe.kind == MediaProbe.Kind.UNKNOWN &&
                (url.contains(".mp4", true) || MediaProbe.looksLikeVideoMime(contentType)))
            val ext = when {
                probe.kind == MediaProbe.Kind.MP4 -> "mp4"
                probe.kind == MediaProbe.Kind.WEBM || probe.kind == MediaProbe.Kind.MKV -> "webm"
                probe.kind == MediaProbe.Kind.PNG -> "png"
                probe.kind == MediaProbe.Kind.WEBP -> "webp"
                probe.kind == MediaProbe.Kind.GIF -> "gif"
                probe.kind == MediaProbe.Kind.JPEG -> "jpg"
                isVideo -> "mp4"
                else -> "jpg"
            }
            val out = File(lib.mediaDir(), "dl_${System.currentTimeMillis()}.$ext")
            if (!tmp.renameTo(out)) {
                tmp.copyTo(out, overwrite = true)
                tmp.delete()
            }
            // final size check
            if (out.length() == 0L) throw IncompleteDownloadException("落盘后为 0 字节")

            val entry = MediaEntry(
                id = UUID.randomUUID().toString(),
                kind = if (isVideo) MediaKind.VIDEO else MediaKind.IMAGE,
                title = titleHint.ifBlank {
                    url.substringAfterLast('/').substringBefore('?').ifBlank { "下载" }
                },
                localPath = out.absolutePath,
                remoteUrl = url,
                source = "url",
            )
            val auto = saveToGallery ?: app.settings.flow.first().autoSaveGallery
            if (auto) {
                GallerySaver.saveLocalFile(
                    app, out, isVideo,
                    displayName = entry.title.let { if (it.contains('.')) it else "$it.$ext" },
                )
            }
            onProgress(1f)
            entry
        }
    }

    suspend fun materializeRemote(
        ctx: Context,
        entry: MediaEntry,
        saveToGallery: Boolean,
    ): MediaEntry = withContext(Dispatchers.IO) {
        if (entry.localPath != null) {
            val f = File(entry.localPath)
            if (!f.exists() || f.length() == 0L) {
                error("本地文件缺失或为 0 字节")
            }
            if (saveToGallery) GallerySaver.saveEntry(ctx, MoonApp.instance.http, entry)
            return@withContext entry
        }
        val url = entry.remoteUrl ?: return@withContext entry
        val got = downloadUrl(url, entry.title, saveToGallery = saveToGallery)
        entry.copy(
            id = entry.id,
            localPath = got.localPath,
            kind = got.kind,
            source = entry.source,
            author = entry.author,
            coverUrl = entry.coverUrl ?: got.localPath,
        ).also { MoonApp.instance.library.upsert(it) }
    }
}
