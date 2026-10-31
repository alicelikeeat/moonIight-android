package com.moonlight.feed.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class LibraryRepository(private val ctx: Context) {
    private val file = File(ctx.filesDir, "library.json")
    private val mediaDir = File(ctx.filesDir, "media").also { it.mkdirs() }
    private val _items = MutableStateFlow<List<MediaEntry>>(emptyList())
    val items: StateFlow<List<MediaEntry>> = _items.asStateFlow()
    private val _jobs = MutableStateFlow<List<DownloadJob>>(emptyList())
    val jobs: StateFlow<List<DownloadJob>> = _jobs.asStateFlow()

    init { _items.value = load() }

    fun mediaDir(): File = mediaDir

    private fun load(): List<MediaEntry> {
        if (!file.exists()) return emptyList()
        return try {
            val arr = JSONArray(file.readText())
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                val kindRaw = o.getString("kind")
                val kind = try { MediaKind.valueOf(kindRaw) } catch (_: Throwable) { MediaKind.VIDEO }
                MediaEntry(
                    id = o.getString("id"),
                    kind = kind,
                    title = o.optString("title"),
                    localPath = o.optString("localPath").ifBlank { null },
                    remoteUrl = o.optString("remoteUrl").ifBlank { null },
                    coverUrl = o.optString("coverUrl").ifBlank { null },
                    biliBvid = o.optString("biliBvid").ifBlank { null },
                    platform = o.optString("platform").ifBlank { null },
                    embedUrl = o.optString("embedUrl").ifBlank { null },
                    author = o.optString("author"),
                    favorite = o.optBoolean("favorite"),
                    hidden = o.optBoolean("hidden"),
                    source = o.optString("source", "local"),
                    createdAt = o.optLong("createdAt", System.currentTimeMillis()),
                )
            }
        } catch (_: Throwable) { emptyList() }
    }

    private fun persist(list: List<MediaEntry>) {
        val arr = JSONArray()
        list.forEach { e ->
            arr.put(JSONObject().apply {
                put("id", e.id); put("kind", e.kind.name); put("title", e.title)
                put("localPath", e.localPath ?: ""); put("remoteUrl", e.remoteUrl ?: "")
                put("coverUrl", e.coverUrl ?: ""); put("biliBvid", e.biliBvid ?: "")
                put("platform", e.platform ?: ""); put("embedUrl", e.embedUrl ?: "")
                put("author", e.author); put("favorite", e.favorite); put("hidden", e.hidden)
                put("source", e.source); put("createdAt", e.createdAt)
            })
        }
        file.writeText(arr.toString())
    }

    fun upsert(entry: MediaEntry) {
        val cur = _items.value.toMutableList()
        val i = cur.indexOfFirst { it.id == entry.id }
        if (i >= 0) cur[i] = entry else cur.add(0, entry)
        _items.value = cur
        persist(cur)
    }

    /** Inserts entries at random positions (feed order follows library order). */
    fun insertRandom(entries: List<MediaEntry>, rnd: kotlin.random.Random = kotlin.random.Random.Default) {
        if (entries.isEmpty()) return
        val cur = _items.value.toMutableList()
        val ids = cur.map { it.id }.toHashSet()
        entries.filter { ids.add(it.id) }.forEach { e -> cur.add(rnd.nextInt(cur.size + 1), e) }
        _items.value = cur
        persist(cur)
    }

    fun update(id: String, transform: (MediaEntry) -> MediaEntry) {
        val cur = _items.value.map { if (it.id == id) transform(it) else it }
        _items.value = cur
        persist(cur)
    }

    fun remove(id: String, deleteLocalFile: Boolean = true, deleteGallery: Boolean = false) {
        val cur = _items.value.toMutableList()
        val e = cur.find { it.id == id }
        cur.removeAll { it.id == id }
        _items.value = cur
        persist(cur)
        if (deleteLocalFile) e?.localPath?.let { runCatching { File(it).delete() } }
        // gallery delete is async via GallerySaver from UI when deleteGallery=true
    }

    fun removeMany(ids: Collection<String>, deleteLocalFile: Boolean = true) {
        ids.forEach { remove(it, deleteLocalFile = deleteLocalFile) }
    }

    fun feedList(shuffle: Boolean): List<MediaEntry> {
        var list = _items.value.filter { !it.hidden }
        if (shuffle) list = list.shuffled()
        return list
    }

    fun setJobs(jobs: List<DownloadJob>) { _jobs.value = jobs }
    fun updateJob(id: String, transform: (DownloadJob) -> DownloadJob) {
        _jobs.value = _jobs.value.map { if (it.id == id) transform(it) else it }
    }

    suspend fun importUris(uris: List<Uri>): List<MediaEntry> = withContext(Dispatchers.IO) {
        val added = mutableListOf<MediaEntry>()
        uris.forEach { uri ->
            try {
                val cr = ctx.contentResolver
                val type = cr.getType(uri) ?: ""
                val name = uri.lastPathSegment?.substringAfterLast('/')?.substringBefore('?')
                    ?: "import_${System.currentTimeMillis()}"
                val isVideo = type.startsWith("video") || name.endsWith(".mp4", true) ||
                    name.endsWith(".webm", true) || name.endsWith(".mkv", true)
                val isImage = type.startsWith("image") || name.endsWith(".jpg", true) ||
                    name.endsWith(".jpeg", true) || name.endsWith(".png", true) ||
                    name.endsWith(".webp", true) || name.endsWith(".gif", true)
                if (!isVideo && !isImage) return@forEach
                try {
                    cr.takePersistableUriPermission(
                        uri,
                        android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (_: SecurityException) { /* temporary grant ok */ }
                val ext = when {
                    isVideo && name.contains('.') -> name.substringAfterLast('.')
                    isVideo -> "mp4"
                    name.contains('.') -> name.substringAfterLast('.')
                    else -> "jpg"
                }
                val out = File(mediaDir, "imp_${System.currentTimeMillis()}_${added.size}.$ext")
                val copied = cr.openInputStream(uri)?.use { input ->
                    out.outputStream().use { input.copyTo(it) }
                } ?: return@forEach
                if (out.length() == 0L) {
                    out.delete()
                    return@forEach
                }
                val e = MediaEntry(
                    kind = if (isVideo) MediaKind.VIDEO else MediaKind.IMAGE,
                    title = name.substringBeforeLast('.').ifBlank { "导入" },
                    localPath = out.absolutePath,
                    source = "local",
                )
                upsert(e)
                added += e
            } catch (_: Throwable) { }
        }
        added
    }
}
