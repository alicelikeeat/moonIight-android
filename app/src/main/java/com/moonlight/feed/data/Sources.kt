package com.moonlight.feed.data

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.moonlight.feed.MoonApp
import com.moonlight.feed.data.web.CrawlConfig
import com.moonlight.feed.data.web.FetchMode
import com.moonlight.feed.data.web.FetchResult
import com.moonlight.feed.data.web.HtmlMediaExtractor
import com.moonlight.feed.data.web.PageFetcher
import com.moonlight.feed.data.web.UrlNorm
import com.moonlight.feed.data.web.WebCrawler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit

/** A website the user added by URL. Content-neutral: nothing is pre-configured. */
data class UserSource(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val url: String,
    val enabled: Boolean = true,
    val mode: FetchMode = FetchMode.RANDOM,
    val depth: Int = 1,
    val sample: Int = 20,
    val maxPages: Int = 20,
    val autoRefreshHours: Int = 0,          // 0 = off (default)
    val lastFetchAt: Long = 0,
    val lastCount: Int = 0,
    val totalCount: Int = 0,
    val lastError: String? = null,
    val fetching: Boolean = false,
) {
    val host: String get() = UrlNorm.host(url) ?: url
}

class SourcesRepository(ctx: Context) {
    private val file = File(ctx.filesDir, "sources.json")
    private val _items = MutableStateFlow(load())
    val items: StateFlow<List<UserSource>> = _items.asStateFlow()

    private fun load(): List<UserSource> = try {
        if (!file.exists()) emptyList() else {
            val arr = JSONArray(file.readText())
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                UserSource(
                    id = o.getString("id"), name = o.optString("name"), url = o.getString("url"),
                    enabled = o.optBoolean("enabled", true),
                    mode = runCatching { FetchMode.valueOf(o.optString("mode", "RANDOM")) }.getOrDefault(FetchMode.RANDOM),
                    depth = o.optInt("depth", 1), sample = o.optInt("sample", 20), maxPages = o.optInt("maxPages", 20),
                    autoRefreshHours = o.optInt("autoRefreshHours", 0), lastFetchAt = o.optLong("lastFetchAt"),
                    lastCount = o.optInt("lastCount"), totalCount = o.optInt("totalCount"),
                    lastError = o.optString("lastError").ifBlank { null },
                )
            }
        }
    } catch (_: Throwable) { emptyList() }

    @Synchronized private fun persist(list: List<UserSource>) {
        val arr = JSONArray()
        list.forEach { s ->
            arr.put(JSONObject().apply {
                put("id", s.id); put("name", s.name); put("url", s.url); put("enabled", s.enabled); put("mode", s.mode.name)
                put("depth", s.depth); put("sample", s.sample); put("maxPages", s.maxPages); put("autoRefreshHours", s.autoRefreshHours)
                put("lastFetchAt", s.lastFetchAt); put("lastCount", s.lastCount); put("totalCount", s.totalCount); put("lastError", s.lastError ?: "")
            })
        }
        file.writeText(arr.toString())
    }

    fun upsert(s: UserSource) {
        val cur = _items.value.toMutableList()
        val i = cur.indexOfFirst { it.id == s.id }
        if (i >= 0) cur[i] = s else cur.add(0, s)
        _items.value = cur; persist(cur)
    }

    fun update(id: String, f: (UserSource) -> UserSource) {
        val cur = _items.value.map { if (it.id == id) f(it) else it }
        _items.value = cur; persist(cur)
    }

    fun remove(id: String) {
        val cur = _items.value.filter { it.id != id }
        _items.value = cur; persist(cur)
    }

    fun findByUrl(url: String) = _items.value.firstOrNull { UrlNorm.normalize(it.url) == UrlNorm.normalize(url) }
}

/** OkHttp fetcher: ordinary browser-like UA (+ Moonlight token), no cookies, 12 s call timeout, 3 MB body cap. */
class OkHttpPageFetcher(base: OkHttpClient) : PageFetcher {
    private val client = base.newBuilder()
        .callTimeout(12, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .cookieJar(okhttp3.CookieJar.NO_COOKIES)
        .build()

    override suspend fun fetch(url: String): FetchResult = withContext(Dispatchers.IO) {
        val req = try {
            Request.Builder().url(url)
                .header("User-Agent", UA)
                .header("Accept", "text/html,application/xhtml+xml,text/plain;q=0.9,*/*;q=0.5")
                .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                .build()
        } catch (_: Throwable) { return@withContext FetchResult(url, 0, error = "无效地址") }
        try {
            client.newCall(req).execute().use { r ->
                val ct = r.header("Content-Type")
                val body = r.body?.let { b ->
                    val src = b.source(); src.request(MAX_BYTES); val buf = src.buffer
                    buf.readString(minOf(buf.size, MAX_BYTES), b.contentType()?.charset() ?: Charsets.UTF_8)
                }
                FetchResult(url, r.code, ct, body, r.request.url.toString())
            }
        } catch (e: java.net.SocketTimeoutException) {
            FetchResult(url, 0, error = "连接超时")
        } catch (e: java.io.InterruptedIOException) {
            FetchResult(url, 0, error = "请求超时")
        } catch (e: java.net.UnknownHostException) {
            FetchResult(url, 0, error = "无法解析域名")
        } catch (e: Throwable) {
            FetchResult(url, 0, error = "网络错误：${e.javaClass.simpleName}")
        }
    }

    companion object {
        const val UA = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36 Moonlight/2.8"
        const val MAX_BYTES = 3L * 1024 * 1024
    }
}

/** Runs a source fetch: crawl → media → dedupe vs library → insert at random feed positions → stats. */
object SourceFetcher {
    private val lock = Mutex()

    data class Outcome(val added: Int, val found: Int, val pages: Int, val error: String?)

    fun mediaToEntry(m: HtmlMediaExtractor.Media, src: UserSource): MediaEntry {
        val video = m.kind == HtmlMediaExtractor.Kind.VIDEO
        return MediaEntry(
            id = "web-" + UrlNorm.key(m.url),
            kind = if (video) MediaKind.VIDEO else MediaKind.IMAGE,
            title = (m.title ?: src.name).take(80),
            remoteUrl = m.url,
            coverUrl = if (video) m.poster else m.url,
            platform = PlatformId.WEB,
            author = src.name,
            source = "user:${src.id}",
        )
    }

    fun libraryKeys(items: List<MediaEntry>): Set<String> =
        items.flatMap { listOfNotNull(it.remoteUrl, it.coverUrl.takeIf { _ -> it.kind == MediaKind.IMAGE }) }
            .map { UrlNorm.key(it) }.toSet() + items.map { it.id }.toSet()

    suspend fun run(src: UserSource, mode: FetchMode = src.mode, deadlineMs: Long = 90_000): Outcome = lock.withLock {
        val app = MoonApp.instance
        PlatformResolver.isUnsupported(src.url)?.let { msg ->
            app.sources.update(src.id) { it.copy(lastError = msg, lastFetchAt = System.currentTimeMillis()) }
            return Outcome(0, 0, 0, msg)
        }
        app.sources.update(src.id) { it.copy(fetching = true) }
        val res = WebCrawler(OkHttpPageFetcher(app.http)).crawl(
            src.url,
            CrawlConfig(mode = mode, depth = src.depth, maxPages = src.maxPages, sample = src.sample, deadlineMs = deadlineMs),
        )
        val known = libraryKeys(app.library.items.value)
        val fresh = res.media
            .filter { PlatformResolver.isUnsupported(it.url) == null }
            .map { mediaToEntry(it, src) }
            .filter { it.id !in known && UrlNorm.key(it.remoteUrl!!) !in known }
            .distinctBy { it.id }
        if (fresh.isNotEmpty()) app.library.insertRandom(fresh)
        val err = when {
            res.error != null && fresh.isEmpty() -> res.error
            fresh.isEmpty() && res.media.isNotEmpty() -> null // all duplicates — not an error
            else -> null
        }
        app.sources.update(src.id) {
            it.copy(
                fetching = false, lastFetchAt = System.currentTimeMillis(), lastCount = fresh.size,
                totalCount = it.totalCount + fresh.size, lastError = err,
                name = if (it.name.isBlank()) res.siteTitle?.take(30) ?: it.host else it.name,
            )
        }
        Outcome(fresh.size, res.media.size, res.pagesFetched, err)
    }

    /** Light-weight sampling for 多源 discovery (no insert): ≤4 pages, depth 1, short deadline. */
    suspend fun discover(sources: List<UserSource>, limitPer: Int, deadlineMs: Long = 12_000): List<MediaEntry> {
        val app = MoonApp.instance
        return sources.filter { it.enabled && PlatformResolver.isUnsupported(it.url) == null }.shuffled().take(2).flatMap { s ->
            runCatching {
                WebCrawler(OkHttpPageFetcher(app.http)).crawl(
                    s.url, CrawlConfig(mode = FetchMode.RANDOM, depth = 1, maxPages = 4, sample = limitPer, deadlineMs = deadlineMs),
                ).media.map { mediaToEntry(it, s) }
            }.getOrDefault(emptyList())
        }
    }

    // ---- periodic background refresh (off by default; only scheduled if a source opts in) ----
    private const val WORK = "moon-user-sources"

    fun reschedule(ctx: Context, sources: List<UserSource>) {
        val wm = WorkManager.getInstance(ctx)
        val hours = sources.filter { it.enabled && it.autoRefreshHours > 0 }.minOfOrNull { it.autoRefreshHours }
        if (hours == null) { wm.cancelUniqueWork(WORK); return }
        val req = PeriodicWorkRequestBuilder<SourceRefreshWorker>(hours.toLong().coerceAtLeast(1), TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.UNMETERED).setRequiresBatteryNotLow(true).build())
            .build()
        wm.enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.UPDATE, req)
    }
}

class SourceRefreshWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val app = MoonApp.instance
        val now = System.currentTimeMillis()
        app.sources.items.value
            .filter { it.enabled && it.autoRefreshHours > 0 && now - it.lastFetchAt >= it.autoRefreshHours * 3_600_000L - 60_000 }
            .forEach { s -> runCatching { SourceFetcher.run(s, deadlineMs = 120_000) } }
        return Result.success()
    }
}
