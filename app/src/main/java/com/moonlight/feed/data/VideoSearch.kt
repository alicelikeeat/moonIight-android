package com.moonlight.feed.data

import com.moonlight.feed.data.web.UrlNorm
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.random.Random

/**
 * Pure merge / dedupe / random-sample logic for keyword video search (unit-tested, no Android).
 */
object SearchMerge {
    private val ytId = Regex("""(?:youtube(?:-nocookie)?\.com/(?:embed/|watch\?v=)|youtu\.be/)([\w-]{11})""")
    private val dmId = Regex("""dailymotion\.com/(?:embed/)?video/([a-z0-9]+)""", RegexOption.IGNORE_CASE)
    private val ptId = Regex("""/videos/(?:watch|embed)/([0-9a-fA-F-]{8,}|[A-Za-z0-9]{20,24})""")

    /** Platform-independent identity: canonical video ids first, then normalized media/embed URL. */
    fun key(e: MediaEntry): String {
        val urls = listOfNotNull(e.embedUrl, e.remoteUrl)
        urls.firstNotNullOfOrNull { ytId.find(it)?.groupValues?.get(1) }?.let { return "yt:$it" }
        urls.firstNotNullOfOrNull { dmId.find(it)?.groupValues?.get(1) }?.let { return "dm:$it" }
        urls.firstNotNullOfOrNull { ptId.find(it)?.groupValues?.get(1) }?.let { return "pt:$it" }
        e.biliBvid?.let { return "bv:$it" }
        val u = e.remoteUrl ?: e.embedUrl ?: return "id:${e.id}"
        return "u:" + UrlNorm.key(u)
    }

    private fun titleKey(e: MediaEntry) =
        e.title.lowercase().replace(Regex("""[\s\p{Punct}·•|｜—\-_]+"""), "").take(60)

    fun isVideo(e: MediaEntry) = e.kind == MediaKind.VIDEO || e.kind == MediaKind.EMBED || e.kind == MediaKind.BILI

    /** Appends [incoming] videos to [pool], dropping duplicates (same id/URL, or same title+author on one platform) and excluded words. */
    fun merge(pool: List<MediaEntry>, incoming: List<MediaEntry>, exclude: Set<String> = emptySet()): List<MediaEntry> {
        val keys = pool.map { key(it) }.toHashSet()
        val titles = pool.map { (it.platform ?: "") + "|" + it.author + "|" + titleKey(it) }.toHashSet()
        val out = pool.toMutableList()
        for (e in incoming) {
            if (!isVideo(e)) continue
            if (exclude.any { x -> x.isNotBlank() && (e.title.contains(x, true) || e.author.contains(x, true)) }) continue
            val tk = (e.platform ?: "") + "|" + e.author + "|" + titleKey(e)
            if (!keys.add(key(e))) continue
            if (titleKey(e).length >= 4 && !titles.add(tk)) continue
            out += e
        }
        return out
    }

    /** Random [n] from [pool] not yet in [shown] (keys). Returns fewer when the pool is exhausted. */
    fun sample(pool: List<MediaEntry>, shown: Set<String>, n: Int, rnd: Random = Random.Default): List<MediaEntry> =
        pool.filter { key(it) !in shown }.shuffled(rnd).take(n)

    /** Interleave-ish order: avoid long runs from the same platform in the shown batch. */
    fun spread(batch: List<MediaEntry>): List<MediaEntry> {
        val byP = batch.groupBy { it.platform ?: "" }.values.map { it.toMutableList() }.toMutableList()
        val out = mutableListOf<MediaEntry>()
        while (byP.any { it.isNotEmpty() }) byP.sortedByDescending { it.size }.forEach { l -> if (l.isNotEmpty()) out += l.removeAt(0) }
        return out
    }
}

/** One provider: a page fetcher. page is 1-based. */
class SearchProvider(val id: String, val label: String, val fetch: suspend (query: String, page: Int) -> List<MediaEntry>)

data class ProviderStatus(val id: String, val label: String, val count: Int, val failed: Boolean, val exhausted: Boolean = false)

/**
 * A keyword search session: parallel per-platform queries with timeouts, merged pool, random batches.
 * '换一批' samples unseen items; when fewer than n remain, it fetches the next page from providers that still return.
 */
class SearchSession(
    val query: String,
    private val providers: List<SearchProvider>,
    private val batchSize: Int = 10,
    private val exclude: Set<String> = emptySet(),
    private val timeoutMs: Long = 10_000,
    private val rnd: Random = Random.Default,
) {
    var pool: List<MediaEntry> = emptyList(); private set
    private val shown = HashSet<String>()
    private val page = HashMap<String, Int>()
    private val counts = HashMap<String, Int>()
    private val failed = HashSet<String>()
    private val exhausted = HashSet<String>()
    var batch: List<MediaEntry> = emptyList(); private set
    var recycled = false; private set

    val status: List<ProviderStatus>
        get() = providers.map { ProviderStatus(it.id, it.label, counts[it.id] ?: 0, it.id in failed && (counts[it.id] ?: 0) == 0, it.id in exhausted) }

    val remaining: Int get() = pool.count { SearchMerge.key(it) !in shown }

    private suspend fun fetchRound(): Int = coroutineScope {
        val live = providers.filter { it.id !in exhausted }
        if (live.isEmpty()) return@coroutineScope 0
        val jobs: List<Pair<SearchProvider, Deferred<List<MediaEntry>?>>> = live.map { p ->
            val pg = (page[p.id] ?: 0) + 1
            // Blocking HTTP runs on a detached IO scope so a slow platform can't hold the timeout hostage.
            val d = detached.async { runCatching { p.fetch(query, pg) }.getOrNull() }
            p to async { withTimeoutOrNull(timeoutMs) { d.await() } }
        }
        jobs.map { it.second }.awaitAll()
        var added = 0
        for ((p, d) in jobs) {
            val res = d.await()
            page[p.id] = (page[p.id] ?: 0) + 1
            if (res == null) { failed += p.id; exhausted += p.id; continue }
            val before = pool.size
            pool = SearchMerge.merge(pool, res, exclude)
            val n = pool.size - before
            counts[p.id] = (counts[p.id] ?: 0) + n
            added += n
            if (res.isEmpty() || n == 0) exhausted += p.id
        }
        added
    }

    /** First batch. */
    suspend fun start(): List<MediaEntry> { fetchRound(); return next(fetch = false) }

    /** 换一批: next random unseen batch; fetches more pages if needed; recycles seen ones when everything is exhausted. */
    suspend fun next(fetch: Boolean = true): List<MediaEntry> {
        var rounds = 0
        while (fetch && remaining < batchSize && rounds < 2 && providers.any { it.id !in exhausted }) {
            if (fetchRound() == 0) break
            rounds++
        }
        recycled = false
        var b = SearchMerge.sample(pool, shown, batchSize, rnd)
        if (b.size < batchSize && pool.size > b.size) {
            // Everything has been shown: start over (excluding the current batch) so 换一批 always returns something.
            recycled = true
            val cur = batch.map { SearchMerge.key(it) }.toSet()
            shown.clear()
            shown += b.map { SearchMerge.key(it) }
            b = b + SearchMerge.sample(pool, shown + cur, batchSize - b.size, rnd)
            // Pool smaller than a batch: the just-shown items are allowed back in (reshuffled).
            if (b.size < batchSize) b = b + SearchMerge.sample(pool, b.map { SearchMerge.key(it) }.toSet(), batchSize - b.size, rnd)
        }
        shown += b.map { SearchMerge.key(it) }
        batch = SearchMerge.spread(b)
        return batch
    }

    companion object {
        private val detached = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}

/** The real providers: keyword search without signing / login. Bilibili search requires WBI signing → not included. */
object VideoSearch {
    fun providers(s: SettingsRepository.Snapshot): List<SearchProvider> {
        val on = s.enabledPlatforms
        val out = mutableListOf<SearchProvider>()
        fun add(id: String, label: String, f: suspend (String, Int) -> List<MediaEntry>) { if (id in on) out += SearchProvider(id, label, f) }
        add(PlatformId.DAILYMOTION, "Dailymotion") { q, p -> RemoteApis.dailymotionSearch(q, 20, p) }
        add(PlatformId.ARCHIVE, "Archive") { q, p -> RemoteApis.archiveSearch(q, 8, p) }
        add(PlatformId.NASA, "NASA") { q, p -> RemoteApis.nasaSearch(q, 8, p) }
        add(PlatformId.PEERTUBE, "PeerTube") { q, p -> RemoteApis.peertubeSearch(q, 15, (p - 1) * 15) }
        add(PlatformId.COMMONS, "Commons") { q, p -> RemoteApis.commonsVideoSearch(q, 16, (p - 1) * 16) }
        if (s.youtubeKey.isNotBlank()) {
            add(PlatformId.YOUTUBE, "YouTube") { q, p -> if (p > 1) emptyList() else RemoteApis.youtubeSearchApi(s.youtubeKey, q, 25) }
        }
        if (s.pexelsKey.isNotBlank()) add(PlatformId.PEXELS, "Pexels") { q, p -> RemoteApis.pexelsVideoSearch(s.pexelsKey, q, p) }
        if (s.pixabayKey.isNotBlank()) add(PlatformId.PIXABAY, "Pixabay") { q, p -> RemoteApis.pixabayVideoSearch(s.pixabayKey, q, p) }
        return out
    }

    /** Platforms enabled but not searchable, shown as a small note. */
    fun skippedNote(s: SettingsRepository.Snapshot): String? {
        val notes = mutableListOf<String>()
        if (PlatformId.BILI in s.enabledPlatforms) notes += "B站搜索需签名，已跳过"
        if (PlatformId.YOUTUBE in s.enabledPlatforms && s.youtubeKey.isBlank()) notes += "YouTube 需 API Key"
        if (PlatformId.VIMEO in s.enabledPlatforms) notes += "Vimeo 无公开搜索"
        return notes.takeIf { it.isNotEmpty() }?.joinToString(" · ")
    }
}
