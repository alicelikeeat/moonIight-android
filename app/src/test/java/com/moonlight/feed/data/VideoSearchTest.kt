package com.moonlight.feed.data

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class VideoSearchTest {
    private fun yt(id: String, title: String = "yt $id") = MediaEntry(kind = MediaKind.EMBED, title = title, platform = PlatformId.YOUTUBE, author = "ch",
        remoteUrl = "https://www.youtube.com/watch?v=$id", embedUrl = "https://www.youtube-nocookie.com/embed/$id?autoplay=1")
    private fun dm(id: String, title: String = "dm $id", author: String = "a") = MediaEntry(kind = MediaKind.EMBED, title = title, platform = PlatformId.DAILYMOTION, author = author,
        remoteUrl = "https://www.dailymotion.com/video/$id", embedUrl = "https://www.dailymotion.com/embed/video/$id")
    private fun file(url: String, p: String = PlatformId.ARCHIVE, title: String = url) = MediaEntry(kind = MediaKind.VIDEO, title = title, platform = p, author = p, remoteUrl = url)

    @Test fun keyIsPlatformCanonical() {
        // watch URL vs embed URL of the same YouTube video → same key
        val a = yt("dQw4w9WgXcQ")
        val b = a.copy(id = "other", remoteUrl = null, embedUrl = "https://www.youtube.com/embed/dQw4w9WgXcQ")
        assertEquals(SearchMerge.key(a), SearchMerge.key(b))
        assertEquals(SearchMerge.key(dm("x7abc")), SearchMerge.key(dm("x7abc").copy(id = "z", remoteUrl = null)))
        // tracking params / www / trailing slash don't create a new item
        assertEquals(SearchMerge.key(file("https://archive.org/download/a/b.mp4?utm_source=x")), SearchMerge.key(file("https://www.archive.org/download/a/b.mp4")))
    }

    @Test fun mergeDedupesAcrossBatchesAndDropsNonVideo() {
        val img = MediaEntry(kind = MediaKind.IMAGE, title = "photo", remoteUrl = "https://x.org/a.jpg")
        val pool1 = SearchMerge.merge(emptyList(), listOf(yt("aaaaaaaaaaa"), dm("x1"), img, dm("x1")))
        assertEquals(2, pool1.size)
        val pool2 = SearchMerge.merge(pool1, listOf(yt("aaaaaaaaaaa").copy(id = "dup"), dm("x2"), file("https://archive.org/download/q/q.mp4")))
        assertEquals(4, pool2.size)
        assertEquals(pool1, pool2.take(2)) // stable order, append-only
    }

    @Test fun mergeDropsSameTitleSameAuthorSamePlatform() {
        val p = SearchMerge.merge(emptyList(), listOf(dm("x1", "Night Sky — Timelapse 4K", "bob"), dm("x9", "night sky timelapse 4k", "bob"), dm("x8", "night sky timelapse 4k", "alice")))
        assertEquals(listOf("x1", "x8"), p.map { it.remoteUrl!!.substringAfterLast('/') })
    }

    @Test fun mergeHonoursExcludeWords() {
        val p = SearchMerge.merge(emptyList(), listOf(dm("x1", "cats compilation"), dm("x2", "dogs"), dm("x3", "ok", author = "spamchannel")), exclude = setOf("cats", "spam"))
        assertEquals(1, p.size)
    }

    @Test fun sampleIsRandomWithoutRepeatsAndDeterministicPerSeed() {
        val pool = (1..30).map { dm("x$it") }
        val a = SearchMerge.sample(pool, emptySet(), 10, Random(42))
        assertEquals(10, a.size)
        assertEquals(10, a.map { SearchMerge.key(it) }.toSet().size)
        assertEquals(a, SearchMerge.sample(pool, emptySet(), 10, Random(42)))
        assertTrue(a != pool.take(10)) // actually shuffled
        val b = SearchMerge.sample(pool, a.map { SearchMerge.key(it) }.toSet(), 10, Random(1))
        assertTrue(b.none { it in a })
        assertEquals(5, SearchMerge.sample(pool.take(5), emptySet(), 10).size)
    }

    @Test fun spreadInterleavesPlatforms() {
        val batch = listOf(dm("1"), dm("2"), dm("3"), yt("bbbbbbbbbbb"), yt("ccccccccccc"))
        val s = SearchMerge.spread(batch)
        assertEquals(batch.toSet(), s.toSet())
        assertTrue((0 until s.size - 2).none { i -> s[i].platform == s[i + 1].platform && s[i + 1].platform == s[i + 2].platform })
    }

    // ------------------------------------------------ session

    private fun provider(id: String, perPage: Int, pages: Int, delayMs: Long = 0, fail: Boolean = false) = SearchProvider(id, id) { _, page ->
        if (delayMs > 0) delay(delayMs)
        if (fail) throw RuntimeException("boom")
        if (page > pages) emptyList() else (1..perPage).map { file("https://$id.org/v/$page-$it.mp4", p = id, title = "$id $page-$it") }
    }

    @Test fun sessionMergesProvidersAndShuffleNeverRepeatsUntilExhausted() = runBlocking {
        val s = SearchSession("q", listOf(provider("a", 6, 3), provider("b", 4, 3)), batchSize = 10, rnd = Random(7))
        val first = s.start()
        assertEquals(10, first.size)
        assertEquals(10, s.pool.size)                // page 1: 6 + 4
        val second = s.next()                        // remaining 0 → fetches page 2
        assertEquals(10, second.size)
        assertTrue(second.none { e -> first.any { SearchMerge.key(it) == SearchMerge.key(e) } })
        assertEquals(20, s.pool.size)
        val third = s.next(); s.next()               // page 3, then everything exhausted → recycle
        assertEquals(30, s.pool.size)
        assertEquals(10, third.size)
        assertTrue(s.recycled)
        assertEquals(10, s.batch.size)
    }

    @Test fun failingAndSlowProvidersAreSkipped() = runBlocking {
        val s = SearchSession("q", listOf(provider("ok", 12, 1), provider("bad", 5, 1, fail = true), provider("slow", 5, 1, delayMs = 5_000)), batchSize = 10, timeoutMs = 300)
        val t0 = System.currentTimeMillis()
        val b = s.start()
        assertTrue("timeout respected", System.currentTimeMillis() - t0 < 2_000)
        assertEquals(10, b.size)
        val st = s.status.associateBy { it.id }
        assertEquals(12, st["ok"]!!.count)
        assertTrue(st["bad"]!!.failed); assertTrue(st["slow"]!!.failed)
        assertFalse(st["ok"]!!.failed)
    }

    @Test fun smallPoolReturnsWhatExists() = runBlocking {
        val s = SearchSession("q", listOf(provider("a", 3, 1)), batchSize = 10)
        assertEquals(3, s.start().size)
        assertEquals(3, s.next().size) // recycle keeps returning the full (small) pool
    }
}
