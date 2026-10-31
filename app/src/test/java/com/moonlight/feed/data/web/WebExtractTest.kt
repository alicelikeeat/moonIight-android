package com.moonlight.feed.data.web

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

private fun fixture(name: String): String =
    WebExtractTest::class.java.classLoader!!.getResource("web/$name")!!.readText()

class WebExtractTest {
    private val page by lazy { HtmlMediaExtractor.extract(fixture("gallery.html"), "https://example.org/blog/") }
    private val urls by lazy { page.media.map { it.url } }

    @Test fun ogMetaAndTitle() {
        assertEquals("Night Photo Blog", page.title)
        assertTrue("og:image", "https://cdn.example.org/og/cover-1200.jpg" in urls)
        val v = page.media.first { it.url == "https://example.org/media/clip.mp4" }
        assertEquals(HtmlMediaExtractor.Kind.VIDEO, v.kind)
        assertTrue(v.fromMeta)
        assertFalse("og:video page (non-file) is not a direct media", urls.any { "youtube.com/embed" in it })
    }

    @Test fun srcsetPicksLargest() {
        assertTrue("https://example.org/blog/img/a-1600.jpg" in urls)
        assertFalse(urls.any { it.endsWith("a-480.jpg") || it.endsWith("a-960.jpg") || it.endsWith("a-small.jpg") })
        assertEquals(1600, HtmlMediaExtractor.largestFromSrcset("a.jpg 480w, b.jpg 1600w, c.jpg 960w")!!.second)
        assertEquals("b.jpg", HtmlMediaExtractor.largestFromSrcset("a.jpg 1x, b.jpg 2x")!!.first)
        assertTrue("picture/source srcset largest", "https://example.org/blog/img/p-2000.avif" in urls)
    }

    @Test fun lazyAndBaseHref() {
        assertTrue("data-src preferred over data: placeholder", "https://example.org/blog/img/lazy.webp" in urls)
        assertFalse(urls.any { it.startsWith("data:") })
        assertTrue("noscript images kept", "https://example.org/blog/img/noscript.jpg" in urls)
    }

    @Test fun skipsIconsTinyTrackersSvgAndComments() {
        listOf("logo.png", "tiny.jpg", "google-analytics", "pixel.tracker", "photo-150x150", "sprite.png", "icon.svg", "favicon", "commented.jpg", "in-script.jpg")
            .forEach { bad -> assertFalse("should skip $bad", urls.any { bad in it }) }
    }

    @Test fun videosAndDirectLinks() {
        val vids = page.media.filter { it.kind == HtmlMediaExtractor.Kind.VIDEO }.map { it.url }
        assertTrue("https://example.org/blog/media/v1.webm" in vids)
        assertTrue("https://example.org/blog/media/v1.mp4" in vids)
        assertEquals("https://example.org/blog/img/poster.jpg", page.media.first { it.url.endsWith("v1.mp4") }.poster)
        assertTrue("https://example.org/blog/files/movie.mov?dl=1" in vids)
        assertTrue("https://example.org/blog/files/full-res.JPG" in urls)
    }

    @Test fun noDuplicates() {
        assertEquals(urls.size, urls.map { UrlNorm.normalize(it) }.toSet().size)
    }

    @Test fun linksSameSiteOnlyAndFiltered() {
        val links = page.links
        assertTrue("https://example.org/blog/page/2" in links)
        assertTrue("https://example.org/about" in links)
        assertTrue("subdomain counts as same site", "https://m.example.org/mobile" in links)
        assertFalse(links.any { "other.com" in it })
        assertFalse(links.any { "/login" in it || ".css" in it || "mailto" in it || "javascript" in it || "/tag/x" in it })
        assertFalse("self link dropped", links.any { UrlNorm.normalize(it) == "https://example.org/blog" })
    }

    @Test fun samplerRespectsVisitedAndK() {
        val links = listOf("https://e.org/a", "https://e.org/b", "https://e.org/c", "https://e.org/a?utm_source=x", "https://e.org/d")
        val s = LinkSampler.sample(links, setOf(UrlNorm.normalize("https://e.org/b")), 2, Random(1))
        assertEquals(2, s.size)
        assertFalse(s.any { it.endsWith("/b") })
        assertEquals(s.size, s.map { UrlNorm.normalize(it) }.toSet().size)
        assertEquals(LinkSampler.sample(links, emptySet(), 3, Random(7)), LinkSampler.sample(links, emptySet(), 3, Random(7)))
        assertEquals(4, LinkSampler.sample(links, emptySet(), 10, Random(1)).size) // utm dup collapsed
    }

    @Test fun urlNormAndKey() {
        assertEquals(UrlNorm.normalize("https://WWW.Example.org/x/?utm_source=a&b=2#frag"), UrlNorm.normalize("https://example.org/x?b=2"))
        assertEquals(UrlNorm.key("https://example.org/x?b=2"), UrlNorm.key("https://www.example.org/x/?b=2&utm_medium=z"))
        assertTrue(UrlNorm.sameSite("https://m.example.org/a", "https://example.org/"))
        assertFalse(UrlNorm.sameSite("https://example.org.evil.com/a", "https://example.org/"))
    }

    @Test fun robots() {
        val r = RobotsTxt.parse(fixture("robots.txt"))
        assertFalse(r.isAllowed("/private/x"))
        assertTrue(r.isAllowed("/private/public-ok/1"))
        assertFalse(r.isAllowed("/docs/file.pdf"))
        assertTrue(r.isAllowed("/docs/file.pdf?x=1"))
        assertTrue(r.isAllowed("/blog/page/2"))
        assertEquals(2.0, r.crawlDelaySec!!, 0.0)
        assertFalse(RobotsTxt.parse(fixture("robots.txt"), "badbot").isAllowed("/anything"))
        assertTrue(RobotsTxt.parse("").isAllowed("/x"))
    }

    // ---------------- crawler with fake fetcher (no network) ----------------

    private class FakeSite(val pages: Map<String, Pair<Int, String>>) : PageFetcher {
        val log = mutableListOf<Pair<String, Long>>()
        var clock = 0L
        override suspend fun fetch(url: String): FetchResult {
            log += url to clock
            val p = pages[UrlNorm.normalize(url)] ?: (404 to "")
            return FetchResult(url, p.first, if (url.endsWith("robots.txt")) "text/plain" else "text/html", p.second)
        }
    }

    private fun site(extra: Map<String, Pair<Int, String>> = emptyMap()) = FakeSite(
        mapOf(
            UrlNorm.normalize("https://example.org/robots.txt") to (200 to fixture("robots.txt")),
            UrlNorm.normalize("https://example.org/blog/") to (200 to fixture("gallery.html")),
            UrlNorm.normalize("https://example.org/blog/page/2") to (200 to fixture("page2.html")),
            UrlNorm.normalize("https://example.org/blog/page/3") to (200 to fixture("page3.html")),
            UrlNorm.normalize("https://example.org/about") to (200 to "<html><body><img src='/img/about.jpg' width='800' height='800'></body></html>"),
        ) + extra
    )

    private fun crawler(s: FakeSite) = WebCrawler(s, now = { s.clock }, sleep = { s.clock += it })

    @Test fun latestModeParsesOnlyStartPage() = runBlocking {
        val s = site()
        val r = crawler(s).crawl("https://example.org/blog/", CrawlConfig(mode = FetchMode.LATEST, sample = 5))
        assertNull(r.error)
        assertEquals(1, r.pagesFetched)
        assertEquals(5, r.media.size)
        assertEquals(listOf("https://example.org/robots.txt", "https://example.org/blog/"), s.log.map { it.first })
    }

    @Test fun randomModeFollowsLinksWithinBudgetAndRateLimit() = runBlocking {
        val s = site()
        val r = crawler(s).crawl("https://example.org/blog/", CrawlConfig(mode = FetchMode.RANDOM, depth = 2, maxPages = 4, sample = 100, seed = 3))
        assertTrue(r.pagesFetched <= 4)
        assertTrue(r.media.size > 10)
        // robots: /private/ never requested
        assertFalse(s.log.any { "/private/" in it.first })
        // never leaves the site
        assertTrue(s.log.all { UrlNorm.sameSite(it.first, "https://example.org/") })
        // per-host spacing ≥ Crawl-delay (2 s)
        s.log.filter { "example.org" in UrlNorm.host(it.first)!! && !it.first.contains("m.example") }
            .zipWithNext().forEach { (a, b) -> assertTrue("spacing ${b.second - a.second}", b.second - a.second >= 2000) }
    }

    @Test fun sampleSizeHonoured() = runBlocking {
        val r = crawler(site()).crawl("https://example.org/blog/", CrawlConfig(mode = FetchMode.RANDOM, depth = 1, maxPages = 3, sample = 4, seed = 9))
        assertEquals(4, r.media.size)
    }

    @Test fun robotsDisallowedStart() = runBlocking {
        val s = site(mapOf(UrlNorm.normalize("https://example.org/private/x") to (200 to fixture("page2.html"))))
        val r = crawler(s).crawl("https://example.org/private/x", CrawlConfig())
        assertTrue(r.media.isEmpty())
        assertNotNull(r.error); assertTrue(r.error!!.contains("robots"))
        assertFalse(s.log.any { it.first.endsWith("/private/x") })
    }

    @Test fun challengePageStopsWithClearError() = runBlocking {
        val s = site(mapOf(UrlNorm.normalize("https://example.org/cf") to (403 to fixture("challenge.html"))))
        val r = crawler(s).crawl("https://example.org/cf", CrawlConfig())
        assertTrue(r.blocked)
        assertTrue(r.error!!.contains("验证"))
    }

    @Test fun emptySiteExplains() = runBlocking {
        val s = site(mapOf(UrlNorm.normalize("https://example.org/empty") to (200 to "<html><body><p>hi</p></body></html>")))
        val r = crawler(s).crawl("https://example.org/empty", CrawlConfig(mode = FetchMode.LATEST))
        assertTrue(r.media.isEmpty()); assertTrue(r.error!!.contains("未在页面中发现"))
    }

    @Test fun robots5xxMeansStop() = runBlocking {
        val s = FakeSite(mapOf(UrlNorm.normalize("https://down.org/robots.txt") to (503 to ""), UrlNorm.normalize("https://down.org/") to (200 to fixture("gallery.html"))))
        val r = crawler(s).crawl("https://down.org/", CrawlConfig())
        assertTrue(r.media.isEmpty()); assertEquals(1, s.log.size)
    }
}
