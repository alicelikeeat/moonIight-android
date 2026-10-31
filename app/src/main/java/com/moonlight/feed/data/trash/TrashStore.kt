package com.moonlight.feed.data.trash

import com.moonlight.feed.data.MediaEntry
import com.moonlight.feed.data.MediaKind
import com.moonlight.feed.data.social.Moment
import com.moonlight.feed.data.social.SocialJson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** One deleted thing kept in 最近删除: a 库 entry or a 朋友圈 post (exactly one of [entry] / [moment]). */
data class TrashItem(val id: String, val deletedAt: Long, val entry: MediaEntry? = null, val moment: Moment? = null) {
    val title: String get() = entry?.title?.ifBlank { null } ?: moment?.text?.take(40)?.ifBlank { null } ?: if (moment != null) "朋友圈动态" else "内容"
    /** Whole days left before it's purged for good. */
    fun daysLeft(now: Long, keepDays: Int): Int = (keepDays - ((now - deletedAt) / DAY).toInt()).coerceAtLeast(0)
    companion object { const val DAY = 24L * 3600_000L }
}

object TrashCodec {
    fun entry(e: MediaEntry) = JSONObject().apply {
        put("id", e.id); put("kind", e.kind.name); put("title", e.title); put("localPath", e.localPath ?: ""); put("remoteUrl", e.remoteUrl ?: "")
        put("coverUrl", e.coverUrl ?: ""); put("biliBvid", e.biliBvid ?: ""); put("platform", e.platform ?: ""); put("embedUrl", e.embedUrl ?: "")
        put("author", e.author); put("favorite", e.favorite); put("hidden", e.hidden); put("source", e.source); put("createdAt", e.createdAt)
    }
    private fun JSONObject.s(k: String) = optString(k).ifBlank { null }
    fun entry(o: JSONObject) = MediaEntry(
        id = o.getString("id"), kind = runCatching { MediaKind.valueOf(o.optString("kind")) }.getOrDefault(MediaKind.IMAGE), title = o.optString("title"),
        localPath = o.s("localPath"), remoteUrl = o.s("remoteUrl"), coverUrl = o.s("coverUrl"), biliBvid = o.s("biliBvid"), platform = o.s("platform"),
        embedUrl = o.s("embedUrl"), author = o.optString("author"), favorite = o.optBoolean("favorite"), hidden = o.optBoolean("hidden"),
        source = o.optString("source", "local"), createdAt = o.optLong("createdAt"),
    )
    fun encode(items: List<TrashItem>): String = JSONArray().apply {
        items.forEach { t -> put(JSONObject().apply {
            put("id", t.id); put("deletedAt", t.deletedAt)
            t.entry?.let { put("entry", entry(it)) }; t.moment?.let { put("moment", SocialJson.moment(it)) }
        }) }
    }.toString()
    fun decode(raw: String?): List<TrashItem> {
        if (raw.isNullOrBlank()) return emptyList()
        val a = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
        return (0 until a.length()).mapNotNull { i -> runCatching {
            val o = a.getJSONObject(i)
            val e = o.optJSONObject("entry")?.let(::entry); val m = o.optJSONObject("moment")?.let { SocialJson.moment(it) }
            if (e == null && m == null) null else TrashItem(o.getString("id"), o.optLong("deletedAt"), e, m)
        }.getOrNull() }
    }
}

/**
 * 最近删除: deleted 库 items and 朋友圈 posts are kept here for N days (设置 › 删除 › 保留天数), then purged — a purged
 * library item's app-private file is deleted too ([onPurge]). Writes are atomic (tmp + fsync + rename).
 */
class TrashStore(private val file: File, private val clock: () -> Long = System::currentTimeMillis, private val onPurge: (TrashItem) -> Unit = {}) {
    private val _items = MutableStateFlow(TrashCodec.decode(runCatching { file.takeIf { it.exists() }?.readText() }.getOrNull()))
    val items: StateFlow<List<TrashItem>> = _items

    private fun save(list: List<TrashItem>) {
        _items.value = list
        runCatching { com.moonlight.feed.data.download.AtomicFiles.write(file, TrashCodec.encode(list)) }
    }

    fun putEntry(e: MediaEntry): TrashItem = put(TrashItem("lib:" + e.id, clock(), entry = e))
    fun putMoment(m: Moment): TrashItem = put(TrashItem("moment:" + m.id, clock(), moment = m))
    private fun put(t: TrashItem): TrashItem { save(listOf(t) + _items.value.filter { it.id != t.id }); return t }

    /** Takes [id] out of the trash (the caller puts it back where it lived). */
    fun restore(id: String): TrashItem? {
        val t = _items.value.firstOrNull { it.id == id } ?: return null
        save(_items.value.filter { it.id != id }); return t
    }

    /** 彻底删除 one item now. */
    fun deleteForever(id: String) {
        val t = _items.value.firstOrNull { it.id == id } ?: return
        save(_items.value.filter { it.id != id }); onPurge(t)
    }
    fun empty() { val all = _items.value; save(emptyList()); all.forEach(onPurge) }

    /** Drops everything older than [keepDays]; returns what was purged. */
    fun purge(keepDays: Int): List<TrashItem> {
        val now = clock()
        val (old, keep) = _items.value.partition { now - it.deletedAt >= keepDays.coerceAtLeast(1) * TrashItem.DAY }
        if (old.isNotEmpty()) { save(keep); old.forEach(onPurge) }
        return old
    }
}
