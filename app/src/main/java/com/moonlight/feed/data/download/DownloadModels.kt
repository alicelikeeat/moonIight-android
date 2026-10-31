package com.moonlight.feed.data.download

import com.moonlight.feed.data.MediaKind
import org.json.JSONArray
import org.json.JSONObject

/** 主动下载 lifecycle. DONE tasks stay as a small record so rings can say 已下载; the file lives in the 库. */
enum class DlState { QUEUED, RUNNING, PAUSED, FAILED, DONE }

data class DownloadTask(
    /** = the source item id (feed entry id / "att:<attachment id>"): one unique work per item. */
    val id: String,
    val url: String,
    val title: String,
    val kind: MediaKind,
    val coverUrl: String? = null,
    val platform: String? = null,
    val author: String = "",
    val state: DlState = DlState.QUEUED,
    val bytes: Long = 0,
    /** -1 = unknown (no Content-Length). */
    val total: Long = -1,
    val error: String? = null,
    /** Validators from the first response; sent as If-Range so a changed file restarts instead of splicing. */
    val etag: String? = null,
    val lastModified: String? = null,
    val resultEntryId: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
) {
    val progress: Float? get() = if (total > 0) (bytes.toFloat() / total).coerceIn(0f, 1f) else null
    val active: Boolean get() = state == DlState.QUEUED || state == DlState.RUNNING
}

object DownloadCodec {
    fun encode(list: List<DownloadTask>): String = JSONArray().apply {
        list.forEach { t ->
            put(JSONObject().apply {
                put("id", t.id); put("url", t.url); put("title", t.title); put("kind", t.kind.name)
                t.coverUrl?.let { put("cover", it) }; t.platform?.let { put("platform", it) }; put("author", t.author)
                put("state", t.state.name); put("bytes", t.bytes); put("total", t.total)
                t.error?.let { put("error", it) }; t.etag?.let { put("etag", it) }; t.lastModified?.let { put("lastModified", it) }
                t.resultEntryId?.let { put("entry", it) }; put("createdAt", t.createdAt)
            })
        }
    }.toString()

    fun decode(raw: String?): List<DownloadTask> {
        if (raw.isNullOrBlank()) return emptyList()
        val arr = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            runCatching {
                val o = arr.getJSONObject(i)
                fun s(k: String) = if (o.has(k) && !o.isNull(k)) o.getString(k).ifBlank { null } else null
                DownloadTask(
                    id = o.getString("id"), url = o.getString("url"), title = o.optString("title"),
                    kind = runCatching { MediaKind.valueOf(o.getString("kind")) }.getOrDefault(MediaKind.VIDEO),
                    coverUrl = s("cover"), platform = s("platform"), author = o.optString("author"),
                    state = runCatching { DlState.valueOf(o.getString("state")) }.getOrDefault(DlState.QUEUED),
                    bytes = o.optLong("bytes"), total = o.optLong("total", -1), error = s("error"),
                    etag = s("etag"), lastModified = s("lastModified"), resultEntryId = s("entry"),
                    createdAt = o.optLong("createdAt", 0),
                )
            }.getOrNull()
        }
    }
}
