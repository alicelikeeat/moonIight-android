package com.moonlight.feed.data.web

import kotlinx.coroutines.delay
import java.net.URI
import kotlin.random.Random

data class FetchResult(
    val url: String,
    val code: Int,
    val contentType: String? = null,
    val body: String? = null,
    val finalUrl: String = url,
    val error: String? = null,
)

fun interface PageFetcher { suspend fun fetch(url: String): FetchResult }

enum class FetchMode { LATEST, RANDOM }

data class CrawlConfig(
    val mode: FetchMode = FetchMode.RANDOM,
    val depth: Int = 1,           // 1..2 link hops from the start page (RANDOM only)
    val maxPages: Int = 20,       // hard page budget per run (incl. start page)
    val sample: Int = 20,         // items returned
    val minSide: Int = HtmlMediaExtractor.DEFAULT_MIN_SIDE,
    val deadlineMs: Long = 90_000,
    val seed: Long = System.nanoTime(),
)

data class CrawlResult(
    val media: List<HtmlMediaExtractor.Media>,
    val pagesFetched: Int,
    val pagesSkippedByRobots: Int,
    val siteTitle: String?,
    val error: String?,
    val blocked: Boolean = false,
)

/**
 * Polite, bounded crawler for user-added sites:
 *  - robots.txt honoured per host (Crawl-delay respected, min 1 s between requests per host)
 *  - same-site links only, depth ≤ 2, ≤ maxPages pages, global deadline
 *  - no cookies / login / captcha handling: challenge or login walls stop the run with a clear error
 */
class WebCrawler(
    private val fetcher: PageFetcher,
    private val now: () -> Long = System::currentTimeMillis,
    private val sleep: suspend (Long) -> Unit = { delay(it) },
    private val minIntervalMs: Long = 1000,
    private val agent: String = "moonlight",
) {
    private val robotsCache = HashMap<String, RobotsTxt?>()   // null = robots unavailable (5xx) → disallow
    private val lastHit = HashMap<String, Long>()
    private val hostDelay = HashMap<String, Long>()

    private fun origin(url: String): String? = try {
        val u = URI(url); "${u.scheme}://${u.rawAuthority}"
    } catch (_: Throwable) { null }

    private fun pathOf(url: String): String = try {
        val u = URI(url); (u.rawPath ?: "/").ifBlank { "/" } + (u.rawQuery?.let { "?$it" } ?: "")
    } catch (_: Throwable) { "/" }

    private suspend fun politeFetch(url: String): FetchResult {
        val host = UrlNorm.host(url) ?: return FetchResult(url, 0, error = "无效地址")
        val interval = maxOf(minIntervalMs, hostDelay[host] ?: 0L)
        lastHit[host]?.let { last ->
            val wait = last + interval - now()
            if (wait > 0) sleep(wait)
        }
        lastHit[host] = now()
        return fetcher.fetch(url)
    }

    private suspend fun robotsFor(url: String): RobotsTxt? {
        val o = origin(url) ?: return RobotsTxt.ALLOW_ALL
        if (robotsCache.containsKey(o)) return robotsCache[o]
        val r = politeFetch("$o/robots.txt")
        val parsed = when {
            r.code in 200..299 && r.body != null -> RobotsTxt.parse(r.body, agent)
            r.code in 500..599 -> null            // RFC 9309: unreachable → assume complete disallow
            else -> RobotsTxt.ALLOW_ALL            // 4xx / missing → allowed
        }
        parsed?.crawlDelaySec?.let { d -> UrlNorm.host(url)?.let { hostDelay[it] = (d * 1000).toLong().coerceIn(minIntervalMs, 10_000) } }
        robotsCache[o] = parsed
        return parsed
    }

    private fun blockReason(r: FetchResult): String? {
        val b = r.body?.take(20_000)?.lowercase().orEmpty()
        val challenge = listOf("cf-chl", "challenge-platform", "g-recaptcha", "h-captcha", "hcaptcha", "captcha", "verify you are human", "人机验证", "滑动验证")
            .any { it in b }
        val fin = r.finalUrl.lowercase()
        return when {
            challenge && (r.code == 403 || r.code == 503 || r.code == 429 || b.length < 20_000) -> "站点启用了人机验证 / 反爬保护（不会绕过）"
            Regex("""/(login|signin|sign-in|passport|account/login)""").containsMatchIn(fin) -> "该页面需要登录（不支持登录 / Cookie）"
            r.code == 401 -> "需要登录（HTTP 401）"
            r.code == 403 -> "站点拒绝访问（HTTP 403）"
            r.code == 429 -> "请求过于频繁被限流（HTTP 429），稍后再试"
            else -> null
        }
    }

    suspend fun crawl(startUrl: String, cfg: CrawlConfig): CrawlResult {
        val started = now()
        val rnd = Random(cfg.seed)
        val visited = HashSet<String>()
        val media = LinkedHashMap<String, HtmlMediaExtractor.Media>()
        var pages = 0; var robotsSkipped = 0
        var siteTitle: String? = null
        var firstError: String? = null
        var blocked = false

        suspend fun visit(url: String): HtmlMediaExtractor.Page? {
            val norm = UrlNorm.normalize(url)
            if (!visited.add(norm)) return null
            val robots = robotsFor(url)
            if (robots == null) { firstError = firstError ?: "robots.txt 暂不可用（按规范暂停抓取）"; robotsSkipped++; return null }
            if (!robots.isAllowed(pathOf(url))) { robotsSkipped++; return null }
            val r = politeFetch(url)
            pages++
            blockReason(r)?.let { firstError = firstError ?: it; blocked = true; return null }
            if (r.error != null) { firstError = firstError ?: r.error; return null }
            if (r.code !in 200..299 || r.body == null) { firstError = firstError ?: "HTTP ${r.code}"; return null }
            val ct = r.contentType?.lowercase().orEmpty()
            if (ct.isNotEmpty() && "html" !in ct && "xml" !in ct) { firstError = firstError ?: "不是网页（$ct）"; return null }
            val page = HtmlMediaExtractor.extract(r.body, r.finalUrl, cfg.minSide)
            page.media.forEach { m -> media.putIfAbsent(UrlNorm.normalize(m.url), m) }
            return page
        }

        val outOfTime = { now() - started > cfg.deadlineMs }
        val start = visit(startUrl)
        if (start == null) {
            val err = when {
                robotsSkipped > 0 && firstError == null -> "robots.txt 禁止抓取该页面"
                else -> firstError ?: "页面无法访问"
            }
            return CrawlResult(emptyList(), pages, robotsSkipped, null, err, blocked || robotsSkipped > 0)
        }
        siteTitle = start.title

        if (cfg.mode == FetchMode.RANDOM && !blocked) {
            var budget = cfg.maxPages.coerceIn(1, 40) - 1
            val depth = cfg.depth.coerceIn(1, 2)
            var frontier = start.links
            for (level in 1..depth) {
                if (budget <= 0 || outOfTime()) break
                val share = if (level < depth) (budget + 1) / 2 else budget
                val picks = LinkSampler.sample(frontier, visited, share, rnd)
                val next = ArrayList<String>()
                for (u in picks) {
                    if (budget <= 0 || outOfTime() || blocked) break
                    budget--
                    visit(u)?.let { next += it.links }
                }
                frontier = next
            }
        }

        val all = media.values.toList()
        val picked = if (cfg.mode == FetchMode.RANDOM) LinkSampler.sampleItems(all, cfg.sample, rnd) else all.take(cfg.sample)
        val err = when {
            picked.isEmpty() && firstError != null -> firstError
            picked.isEmpty() -> "未在页面中发现可用的图片或视频（可能由脚本动态加载）"
            else -> null
        }
        return CrawlResult(picked, pages, robotsSkipped, siteTitle, err, blocked && picked.isEmpty())
    }
}
