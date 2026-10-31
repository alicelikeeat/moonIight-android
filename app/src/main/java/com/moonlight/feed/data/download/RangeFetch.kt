package com.moonlight.feed.data.download

import com.moonlight.feed.data.MediaProbe
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * One attempt at fetching [url] into [part], resuming from what is already there.
 *
 *  - part has bytes → `Range: bytes=N-` (+ `If-Range` with the strong ETag / Last-Modified seen before).
 *  - 206 starting at N → append.            - 200 (Range ignored, or the file changed) → truncate, start over.
 *  - 416 with N == known total → complete.  - 416 otherwise → drop the part, start over once.
 *  - 408 / 429 / 5xx / connection drop → [Outcome.Retry] (part kept → the next attempt resumes).
 *  - other 4xx, HTML instead of media, not an image/video → [Outcome.Fatal] (part deleted).
 *  - [isStopped] (pause / cancel / system stop) is checked between buffers → [Outcome.Stopped], part kept.
 * Completion is verified: byte count == total when the server told us one, and the file header sniffs as media.
 */
class RangeFetch(private val http: OkHttpClient, private val userAgent: String = "Moonlight/4.4") {
    data class Meta(val etag: String? = null, val lastModified: String? = null, val total: Long = -1)

    sealed class Outcome {
        data class Done(val bytes: Long, val probe: MediaProbe.Probe, val meta: Meta) : Outcome()
        data class Stopped(val bytes: Long, val meta: Meta) : Outcome()
        data class Retry(val reason: String, val bytes: Long, val meta: Meta) : Outcome()
        data class Fatal(val reason: String) : Outcome()
    }

    fun run(
        url: String,
        part: File,
        meta: Meta = Meta(),
        isStopped: () -> Boolean = { false },
        onProgress: (bytes: Long, total: Long) -> Unit = { _, _ -> },
    ): Outcome = attempt(url, part, meta, isStopped, onProgress, restarted = false)

    private fun attempt(url: String, part: File, meta0: Meta, isStopped: () -> Boolean, onProgress: (Long, Long) -> Unit, restarted: Boolean): Outcome {
        part.parentFile?.mkdirs()
        val have = if (part.exists()) part.length() else 0L
        var meta = meta0
        if (have > 0 && meta.total > 0 && have > meta.total) { part.delete(); return attempt(url, part, Meta(), isStopped, onProgress, restarted) }
        val rb = Request.Builder().url(url)
            .header("User-Agent", userAgent)
            .header("Accept", "video/*,image/*,*/*")
            .header("Accept-Encoding", "identity")   // byte offsets must mean file offsets
        if (have > 0) {
            rb.header("Range", "bytes=$have-")
            val validator = meta.etag?.takeIf { !it.startsWith("W/") } ?: meta.lastModified
            if (validator != null) rb.header("If-Range", validator)
        }
        val resp = try { http.newCall(rb.build()).execute() } catch (e: IOException) {
            return Outcome.Retry("网络错误：${e.message ?: e.javaClass.simpleName}", have, meta)
        }
        resp.use { r ->
            val code = r.code
            if (code == 416) {
                if (meta.total > 0 && have == meta.total) return finish(part, have, meta, url)
                part.delete()
                return if (restarted) Outcome.Fatal("服务器拒绝了续传请求（416）") else attempt(url, part, Meta(), isStopped, onProgress, restarted = true)
            }
            if (code == 408 || code == 429 || code in 500..599) return Outcome.Retry("HTTP $code", have, meta)
            if (code != 200 && code != 206) { part.delete(); return Outcome.Fatal("HTTP $code") }
            val body = r.body ?: return Outcome.Retry("空响应", have, meta)
            val type = r.header("Content-Type")?.lowercase()
            if (type != null && type.startsWith("text/html")) { part.delete(); return Outcome.Fatal("链接返回网页（需要登录或不是文件直链）") }

            val etag = r.header("ETag"); val lm = r.header("Last-Modified")
            val append: Boolean
            val total: Long
            if (code == 206) {
                val cr = parseContentRange(r.header("Content-Range"))
                if (cr == null || cr.first != have) {
                    // a range we did not ask for: start clean rather than splice
                    part.delete()
                    return if (restarted) Outcome.Fatal("服务器返回了错误的续传范围") else attempt(url, part, Meta(), isStopped, onProgress, restarted = true)
                }
                append = true
                total = cr.third.takeIf { it > 0 } ?: meta.total
            } else {
                append = false   // 200: no Range support, or If-Range said the file changed → restart from 0
                val len = body.contentLength()
                total = if (len > 0) len else -1
            }
            meta = Meta(etag ?: meta.etag.takeIf { code == 206 }, lm ?: meta.lastModified.takeIf { code == 206 }, total)
            var written = if (append) have else 0L
            onProgress(written, total)
            try {
                FileOutputStream(part, append).use { out ->
                    body.byteStream().use { input ->
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            if (isStopped()) { out.flush(); return Outcome.Stopped(written, meta) }
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            written += n
                            onProgress(written, total)
                        }
                        out.flush()
                    }
                }
            } catch (e: IOException) {
                return Outcome.Retry("连接中断（已下载 ${written} 字节）", written, meta)
            }
            if (total > 0 && written < total) return Outcome.Retry("不完整：$written / $total", written, meta)
            if (total > 0 && written > total) { part.delete(); return Outcome.Retry("大小不符，重新下载", 0, Meta()) }
            return finish(part, written, meta, url)
        }
    }

    private fun finish(part: File, bytes: Long, meta: Meta, url: String): Outcome {
        if (bytes <= 0L || part.length() != bytes) { part.delete(); return Outcome.Fatal("下载为 0 字节") }
        val probe = MediaProbe.sniff(part)
        if (probe.kind == MediaProbe.Kind.HTML) { part.delete(); return Outcome.Fatal(probe.note) }
        if (!probe.isVideo && !probe.isImage) {
            val path = url.substringBefore('?').lowercase()
            val hinted = listOf(".mp4", ".webm", ".mov", ".mkv", ".ogv", ".jpg", ".jpeg", ".png", ".webp", ".gif").any { path.endsWith(it) }
            if (!hinted) { part.delete(); return Outcome.Fatal("无法识别为图片或视频（${probe.note}）") }
        }
        return Outcome.Done(bytes, probe, meta)
    }

    companion object {
        /** "bytes 100-199/1000" → (100, 199, 1000); total "*" → -1. */
        fun parseContentRange(v: String?): Triple<Long, Long, Long>? {
            val m = Regex("""bytes\s+(\d+)-(\d+)/(\d+|\*)""").find(v ?: return null) ?: return null
            return Triple(m.groupValues[1].toLong(), m.groupValues[2].toLong(), m.groupValues[3].toLongOrNull() ?: -1)
        }
    }
}
