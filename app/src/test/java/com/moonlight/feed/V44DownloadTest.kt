package com.moonlight.feed

import com.moonlight.feed.data.FeedScope
import com.moonlight.feed.data.MediaEntry
import com.moonlight.feed.data.MediaKind
import com.moonlight.feed.data.PlatformId
import com.moonlight.feed.data.download.AutoCache
import com.moonlight.feed.data.download.CoalescingWriter
import com.moonlight.feed.data.download.DlState
import com.moonlight.feed.data.download.DownloadCodec
import com.moonlight.feed.data.download.DownloadEngine
import com.moonlight.feed.data.download.DownloadManager
import com.moonlight.feed.data.download.DownloadPolicy
import com.moonlight.feed.data.download.DownloadScheduler
import com.moonlight.feed.data.download.DownloadSource
import com.moonlight.feed.data.download.DownloadStore
import com.moonlight.feed.data.download.DownloadTask
import com.moonlight.feed.data.download.RangeFetch
import com.moonlight.feed.data.social.Attachment
import com.moonlight.feed.data.social.AttachmentKind
import com.moonlight.feed.ui.library.LIB_FILTERS
import com.moonlight.feed.ui.library.libFilter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit

/** v4.4 主动下载: Range resume against a fake server, restart persistence, manual vs auto, 我的下载, menus, social writes. */
class V44DownloadTest {

    // ------------------------------------------------------------------ fake HTTP server
    /** Minimal HTTP/1.1 server on a raw socket (one response per connection, `Connection: close`). */
    class FakeServer(@Volatile var body: ByteArray, @Volatile var range: Boolean, @Volatile var etag: String? = "\"v1\"") : AutoCloseable {
        data class Req(val range: String?, val ifRange: String?)
        val seen = CopyOnWriteArrayList<Req>()
        /** The first [dropTimes] responses are cut off after this many body bytes. */
        @Volatile var dropAfter: Int? = null
        @Volatile var dropTimes = 1
        @Volatile var status: Int? = null
        @Volatile var contentType = "video/mp4"
        private val server = java.net.ServerSocket(0, 50, java.net.InetAddress.getByName("127.0.0.1"))
        val url get() = "http://127.0.0.1:${server.localPort}/clip.mp4"
        private val loop = Thread {
            while (!server.isClosed) {
                val sock = runCatching { server.accept() }.getOrNull() ?: break
                runCatching { sock.use { serve(it) } }
            }
        }.apply { isDaemon = true; start() }

        private fun serve(sock: java.net.Socket) {
            val input = sock.getInputStream().bufferedReader(Charsets.ISO_8859_1)
            input.readLine() ?: return
            val headers = HashMap<String, String>()
            while (true) { val l = input.readLine() ?: break; if (l.isEmpty()) break; val i = l.indexOf(':'); if (i > 0) headers[l.substring(0, i).trim().lowercase()] = l.substring(i + 1).trim() }
            val rh = headers["range"]; val ir = headers["if-range"]
            seen += Req(rh, ir)
            val out = sock.getOutputStream()
            fun head(code: Int, reason: String, extra: List<String>, len: Long) {
                val h = StringBuilder("HTTP/1.1 $code $reason\r\nConnection: close\r\nContent-Length: $len\r\n")
                extra.forEach { h.append(it).append("\r\n") }
                out.write(h.append("\r\n").toString().toByteArray(Charsets.ISO_8859_1))
            }
            status?.let { head(it, "X", emptyList(), 0); out.flush(); return }
            val b = body
            val common = listOfNotNull("Content-Type: $contentType", etag?.let { "ETag: $it" })
            val start = rh?.let { Regex("""bytes=(\d+)-""").find(it)?.groupValues?.get(1)?.toLong() }
            val ok = range && start != null && (ir == null || ir == etag)
            if (ok && start!! >= b.size) { head(416, "Range Not Satisfiable", common + "Content-Range: bytes */${b.size}", 0); out.flush(); return }
            val from = if (ok) start!!.toInt() else 0
            head(if (ok) 206 else 200, if (ok) "Partial Content" else "OK",
                common + (if (ok) listOf("Accept-Ranges: bytes", "Content-Range: bytes $from-${b.size - 1}/${b.size}") else emptyList()), (b.size - from).toLong())
            val cut = dropAfter?.takeIf { dropTimes > 0 }
            if (cut != null) { dropTimes--; out.write(b, from, minOf(cut, b.size - from)); out.flush(); return }   // close early → client sees EOF
            out.write(b, from, b.size - from); out.flush()
        }
        override fun close() { server.close(); loop.join(1000) }
    }

    private fun media(n: Int, seed: Int = 1): ByteArray = ByteArray(n) { i -> ((i * 31 + seed * 7) % 251).toByte() }.also {
        byteArrayOf(0, 0, 0, 0x18, 'f'.code.toByte(), 't'.code.toByte(), 'y'.code.toByte(), 'p'.code.toByte()).copyInto(it)
    }

    private val http = OkHttpClient.Builder().readTimeout(5, TimeUnit.SECONDS).retryOnConnectionFailure(false).build()
    private lateinit var dir: File
    private lateinit var io: CoroutineScope

    @Before fun setUp() { dir = Files.createTempDirectory("dl").toFile(); io = CoroutineScope(SupervisorJob() + Dispatchers.IO) }
    @After fun tearDown() { io.cancel(); dir.deleteRecursively() }

    // ------------------------------------------------------------------ RangeFetch
    @Test fun resumesWithRangeAfterADrop() = FakeServer(media(300_000), range = true).use { s ->
        s.dropAfter = 120_000
        val part = File(dir, "a.part"); val f = RangeFetch(http)
        val first = f.run(s.url, part)
        assertTrue("dropped connection → retry, got $first", first is RangeFetch.Outcome.Retry)
        val kept = part.length()
        assertTrue("partial bytes kept: $kept", kept in 1 until 300_000)
        val meta = (first as RangeFetch.Outcome.Retry).meta
        val second = f.run(s.url, part, meta)
        assertTrue("$second", second is RangeFetch.Outcome.Done)
        assertEquals("bytes=$kept-", s.seen.last().range)
        assertEquals("strong ETag sent as If-Range", "\"v1\"", s.seen.last().ifRange)
        assertArrayEquals(s.body, part.readBytes())
    }

    @Test fun serverWithoutRangeRestartsCleanly() = FakeServer(media(200_000), range = false).use { s ->
        s.dropAfter = 90_000
        val part = File(dir, "b.part"); val f = RangeFetch(http)
        val first = f.run(s.url, part) as RangeFetch.Outcome.Retry
        assertTrue(part.length() > 0)
        val second = f.run(s.url, part, first.meta)
        assertTrue(second is RangeFetch.Outcome.Done)
        assertNotNull("we still asked to resume", s.seen.last().range)
        assertEquals("200 → truncated and rewritten, never spliced", 200_000L, part.length())
        assertArrayEquals(s.body, part.readBytes())
    }

    @Test fun changedFileOnServerRestartsInsteadOfSplicing() = FakeServer(media(150_000, seed = 1), range = true).use { s ->
        s.dropAfter = 50_000
        val part = File(dir, "c.part"); val f = RangeFetch(http)
        val first = f.run(s.url, part) as RangeFetch.Outcome.Retry
        s.body = media(160_000, seed = 9); s.etag = "\"v2\""          // file replaced between attempts
        assertTrue(f.run(s.url, part, first.meta) is RangeFetch.Outcome.Done)
        assertArrayEquals(s.body, part.readBytes())
    }

    @Test fun completePartAnd416IsDone() = FakeServer(media(64_000), range = true).use { s ->
        val part = File(dir, "d.part").apply { writeBytes(s.body) }
        val out = RangeFetch(http).run(s.url, part, RangeFetch.Meta("\"v1\"", null, 64_000))
        assertTrue("$out", out is RangeFetch.Outcome.Done)
        assertArrayEquals(s.body, part.readBytes())
    }

    @Test fun errorsAreClassified() = FakeServer(media(10_000), range = true).use { s ->
        val part = File(dir, "e.part")
        s.status = 503; assertTrue(RangeFetch(http).run(s.url, part) is RangeFetch.Outcome.Retry)
        s.status = 404; assertTrue(RangeFetch(http).run(s.url, part) is RangeFetch.Outcome.Fatal)
        s.status = null; s.contentType = "text/html"
        assertTrue(RangeFetch(http).run(s.url, part) is RangeFetch.Outcome.Fatal); assertFalse(part.exists())
        s.contentType = "video/mp4"; s.body = ByteArray(5000) { 7 }
        assertTrue("not media → fatal", RangeFetch(http).run(s.url.replace("clip.mp4", "x.bin"), part) is RangeFetch.Outcome.Fatal)
        assertEquals(Triple(100L, 199L, 1000L), RangeFetch.parseContentRange("bytes 100-199/1000"))
        assertEquals(-1L, RangeFetch.parseContentRange("bytes 0-9/*")!!.third)
    }

    @Test fun pauseStopsAndKeepsThePart() = FakeServer(media(400_000), range = true).use { s ->
        val part = File(dir, "f.part"); var n = 0
        val out = RangeFetch(http).run(s.url, part, isStopped = { ++n > 2 })
        assertTrue(out is RangeFetch.Outcome.Stopped)
        assertTrue(part.length() in 1 until 400_000)
        assertTrue(RangeFetch(http).run(s.url, part, (out as RangeFetch.Outcome.Stopped).meta) is RangeFetch.Outcome.Done)
        assertArrayEquals(s.body, part.readBytes())
    }

    // ------------------------------------------------------------------ manager / engine / restart
    class FakeScheduler : DownloadScheduler {
        val enq = mutableListOf<Pair<String, Boolean>>(); val cancelled = mutableListOf<String>()
        override fun enqueue(id: String, replace: Boolean) { enq += id to replace }
        override fun cancel(id: String) { cancelled += id }
    }

    private fun src(id: String, url: String) = DownloadSource(id, url, "片段 $id", MediaKind.VIDEO, "art:3", PlatformId.ARCHIVE, "Archive")

    @Test fun surviveProcessRestartAndReEnqueue() = FakeServer(media(250_000), range = true).use { s ->
        val tasksFile = File(dir, "downloads.json"); val parts = File(dir, "parts"); val mediaDir = File(dir, "media").apply { mkdirs() }
        // ---- process 1
        val sch1 = FakeScheduler()
        val store1 = DownloadStore(tasksFile, io, debounceMs = 0)
        val m1 = DownloadManager(store1, sch1, parts)
        assertEquals(DownloadManager.Request.STARTED, m1.request(src("a", s.url)))
        assertEquals(DownloadManager.Request.STARTED, m1.request(src("b", s.url)))
        assertEquals("unique work per item: asking again doesn't add a task", DownloadManager.Request.ALREADY_ACTIVE, m1.request(src("a", s.url)))
        m1.pause("b")
        assertEquals(listOf("a" to true, "b" to true), sch1.enq); assertEquals(listOf("b"), sch1.cancelled)
        s.dropAfter = 100_000
        val e1 = DownloadEngine(store1, RangeFetch(http), m1, mediaDir, finish = {})
        assertEquals(DownloadEngine.Step.RETRY, e1.execute("a", 0, 6, { false }))
        store1.put(store1.get("a")!!.copy(state = DlState.RUNNING))          // killed while running
        runBlocking { store1.flush() }
        val partBytes = m1.partFile("a").length()
        assertTrue(partBytes > 0)

        // ---- process 2: everything rebuilt from disk
        val sch2 = FakeScheduler(); val finished = mutableListOf<MediaEntry>()
        val store2 = DownloadStore(tasksFile, io, debounceMs = 0)
        val m2 = DownloadManager(store2, sch2, parts)
        assertEquals(listOf("a"), m2.restore())
        assertEquals("KEEP, so WorkManager's own persisted work is not duplicated", listOf("a" to false), sch2.enq)
        assertEquals(DlState.QUEUED, store2.get("a")!!.state); assertEquals(DlState.PAUSED, store2.get("b")!!.state)
        assertEquals(partBytes, store2.get("a")!!.bytes)
        val e2 = DownloadEngine(store2, RangeFetch(http), m2, mediaDir, finish = { finished += it })
        assertEquals(DownloadEngine.Step.DONE, e2.execute("a", 1, 6, { false }))
        assertEquals("bytes=$partBytes-", s.seen.last().range)
        val e = finished.single()
        assertArrayEquals(s.body, File(e.localPath!!).readBytes())
        assertFalse(m2.partFile("a").exists())
        assertEquals(DownloadPolicy.SOURCE, e.source); assertEquals(DownloadPolicy.entryId("a"), e.id)
        assertTrue("finished download is a 本地 item on 首页", FeedScope.LOCAL.matches(e))
        assertEquals(DlState.DONE, store2.get("a")!!.state)
        // resume the paused one, then cancel it
        assertEquals(DownloadManager.Request.RESUMED, m2.request(src("b", s.url)))
        m2.cancel("b"); assertNull(store2.get("b")); assertFalse(m2.partFile("b").exists())
    }

    @Test fun pausedTaskIsSkippedAndDoneOneIsNotRedownloaded() = FakeServer(media(30_000), range = true).use { s ->
        var exists = true
        val m = DownloadManager(DownloadStore(File(dir, "t.json"), io, 0), FakeScheduler(), File(dir, "p")) { exists }
        val e = DownloadEngine(m.store, RangeFetch(http), m, dir, finish = {})
        m.request(src("x", s.url)); m.pause("x")
        assertEquals(DownloadEngine.Step.SKIPPED, e.execute("x", 0, 6, { false }))
        m.resume("x"); assertEquals(DownloadEngine.Step.DONE, e.execute("x", 0, 6, { false }))
        assertEquals(DownloadManager.Request.ALREADY_DONE, m.request(src("x", s.url)))
        exists = false   // deleted from the 库 → a new request downloads again
        assertEquals(DownloadManager.Request.STARTED, m.request(src("x", s.url)))
    }

    @Test fun lastAttemptMarksFailedAndRetryRequeues() = FakeServer(media(10_000), range = true).use { s ->
        s.status = 503
        val sch = FakeScheduler()
        val m = DownloadManager(DownloadStore(File(dir, "t.json"), io, 0), sch, File(dir, "p"))
        val e = DownloadEngine(m.store, RangeFetch(http), m, dir, finish = {})
        m.request(src("y", s.url))
        assertEquals(DownloadEngine.Step.RETRY, e.execute("y", 0, 3, { false }))
        assertEquals(DlState.QUEUED, m.store.get("y")!!.state); assertEquals("HTTP 503", m.store.get("y")!!.error)
        assertEquals(DownloadEngine.Step.FAILED, e.execute("y", 2, 3, { false }))
        assertEquals(DlState.FAILED, m.store.get("y")!!.state)
        m.retry("y"); assertEquals(DlState.QUEUED, m.store.get("y")!!.state); assertNull(m.store.get("y")!!.error)
        assertEquals("y" to true, sch.enq.last())
    }

    @Test fun codecRoundTripAndGarbage() {
        val t = DownloadTask("i", "https://x/y.mp4", "t", MediaKind.IMAGE, "c", "nasa", "a", DlState.PAUSED, 10, 100, "e", "\"z\"", "lm", "dl-i", 5)
        assertEquals(listOf(t), DownloadCodec.decode(DownloadCodec.encode(listOf(t))))
        assertEquals(emptyList<DownloadTask>(), DownloadCodec.decode("{oops"))
    }

    // ------------------------------------------------------------------ manual vs auto, 库 category
    private val archive = MediaEntry(id = "ar1", kind = MediaKind.VIDEO, title = "月出", remoteUrl = "https://archive.org/download/x/moon.mp4", platform = PlatformId.ARCHIVE, source = "archive")
    private val done = DownloadEngine.entryFor(DownloadTask("ar1", archive.remoteUrl!!, "月出", MediaKind.VIDEO, platform = PlatformId.ARCHIVE),
        File("/data/media/dl_ar1.mp4"), com.moonlight.feed.data.MediaProbe.sniff(java.io.ByteArrayInputStream(media(64))))

    @Test fun manualDownloadsAndAutoCacheAreSeparate() {
        val imported = MediaEntry(kind = MediaKind.VIDEO, title = "导入", localPath = "/data/media/imp.mp4", source = "local")
        val linked = MediaEntry(kind = MediaKind.IMAGE, title = "直链", localPath = "/data/media/dl_1.jpg", source = "url")
        val lib = listOf(archive, done, imported, linked)
        assertEquals("我的下载 = only finished 主动下载", listOf(done), libFilter(lib, DownloadPolicy.CATEGORY))
        assertFalse(DownloadPolicy.isManualDownload(archive)); assertFalse(DownloadPolicy.isManualDownload(imported))
        // 自动缓存 lives in cacheDir (best-effort, the OS may clear it); 主动下载 parts in filesDir, never in the cache
        val files = File("/data/user/0/app/files"); val cache = File("/data/user/0/app/cache")
        assertTrue(AutoCache.imageDir(cache).path.startsWith(cache.path))
        assertTrue(AutoCache.downloadPartDir(files).path.startsWith(files.path))
        assertFalse(AutoCache.downloadPartDir(files).path.startsWith(cache.path))
        val src = listOf(File("src/main/java"), File("app/src/main/java")).first { it.exists() }
        val app = File(src, "com/moonlight/feed/MoonApp.kt").readText()
        assertTrue("Coil disk cache uses AutoCache.imageDir", "AutoCache.imageDir(cacheDir)" in app)
        // nothing but the download engine creates source = "download" entries
        val makers = src.walkTopDown().filter { it.extension == "kt" }.filter { "source = DownloadPolicy.SOURCE" in it.readText() }.map { it.name }.toList()
        assertEquals(listOf("DownloadManager.kt"), makers)
    }

    @Test fun libraryCategory() {
        assertEquals(1, LIB_FILTERS.indexOf(DownloadPolicy.CATEGORY))
        assertTrue(done.id.startsWith("dl-")); assertEquals(MediaKind.VIDEO, done.kind)
        assertTrue("also on 首页 › 本地", FeedScope.LOCAL.matches(done)); assertFalse(FeedScope.ONLINE.matches(done))
        assertEquals(listOf(done), libFilter(listOf(done), "全部")); assertEquals(listOf(done), libFilter(listOf(done), "视频"))
        assertEquals(DownloadPolicy.CATEGORY, DownloadPolicy.check(done).reason?.removePrefix("已在「")?.removeSuffix("」"))
    }

    // ------------------------------------------------------------------ what is downloadable
    @Test fun onlyDirectFilesAreDownloadable() {
        fun e(p: String, k: MediaKind, url: String?) = MediaEntry(kind = k, title = "t", remoteUrl = url, platform = p, source = p)
        val yes = listOf(
            e(PlatformId.ARCHIVE, MediaKind.VIDEO, "https://archive.org/download/a/b.mp4"),
            e(PlatformId.NASA, MediaKind.VIDEO, "https://images-assets.nasa.gov/video/x/x~orig.mp4"),
            e(PlatformId.COMMONS, MediaKind.IMAGE, "https://upload.wikimedia.org/a.jpg"),
            e(PlatformId.PEERTUBE, MediaKind.VIDEO, "https://tube.example/download/videos/u-720.mp4"),
            e(PlatformId.PEXELS, MediaKind.VIDEO, "https://videos.pexels.com/v.mp4"),
            e(PlatformId.PIXABAY, MediaKind.IMAGE, "https://cdn.pixabay.com/p.jpg"),
            e(PlatformId.URL, MediaKind.VIDEO, "https://example.com/clip.webm"),
            e(PlatformId.WEB, MediaKind.IMAGE, "https://blog.example/img.png"),
        )
        yes.forEach { assertTrue("${it.platform}", DownloadPolicy.check(it).ok) }
        val no = mapOf(
            MediaEntry(kind = MediaKind.BILI, title = "b", biliBvid = "BV1", platform = PlatformId.BILI) to "哔哩",
            e(PlatformId.YOUTUBE, MediaKind.EMBED, "https://www.youtube.com/watch?v=x") to "YouTube",
            e(PlatformId.VIMEO, MediaKind.EMBED, "https://vimeo.com/1") to "Vimeo",
            e(PlatformId.DAILYMOTION, MediaKind.VIDEO, "https://www.dailymotion.com/video/x") to "Dailymotion",
            e(PlatformId.TWITCH, MediaKind.EMBED, "https://clips.twitch.tv/x") to "Twitch",
            e(PlatformId.PEERTUBE, MediaKind.EMBED, "https://tube.example/w/x") to "网页",
            e(PlatformId.URL, MediaKind.VIDEO, "https://cdn.example/live/master.m3u8") to "流媒体",
            e(PlatformId.URL, MediaKind.VIDEO, "https://cdn.example/a.mpd") to "流媒体",
            e(PlatformId.WEB, MediaKind.VIDEO, "https://upos-sz-mirror.bilivideo.com/x.m4s") to "流媒体",
            e(PlatformId.URL, MediaKind.VIDEO, null) to "没有",
            MediaEntry(kind = MediaKind.VIDEO, title = "l", localPath = "/x.mp4") to "已在本机",
        )
        no.forEach { (entry, why) -> val o = DownloadPolicy.check(entry); assertFalse(entry.platform, o.ok); assertTrue("${o.reason} ∌ $why", why in o.reason!!) }
        // 朋友圈
        assertTrue(DownloadPolicy.check(Attachment(kind = AttachmentKind.VIDEO, remoteUrl = "https://chat.example/u/v.mp4"), null).ok)
        assertEquals("已在本机", DownloadPolicy.check(Attachment(kind = AttachmentKind.VIDEO, localPath = "/a.mp4"), null).reason)
        assertTrue(DownloadPolicy.check(Attachment(kind = AttachmentKind.VIDEO, libraryEntryId = "ar1"), archive).ok)
        assertEquals("att:z", DownloadPolicy.check(Attachment(id = "z", kind = AttachmentKind.VIDEO, remoteUrl = "https://c/v.mp4"), null).source!!.id)
    }

    // ------------------------------------------------------------------ menus
    @Test fun downloadSitsOnTheShareSaveArcAndInTheSheet() {
        val h = com.moonlight.feed.ui.feed.FeedMenuHandlers()
        val spec = com.moonlight.feed.ui.feed.feedMenuSpec(archive, false, true, 1f, true, true, h)
        val ring = spec.radial!!
        assertEquals("ring cap respected", 4, ring.size)
        val arc = ring.single { it.title == "分享·保存" }.children!!
        assertTrue("下载 on the secondary arc", arc.any { it.title == "下载" })
        assertTrue(arc.size <= com.moonlight.feed.ui.design.SUB_ARC_MAX)
        val sheet = spec.actions.flatten().single { it.title.startsWith("下载") }
        assertNull(sheet.disabledReason)
        val running = DownloadTask("ar1", archive.remoteUrl!!, "月出", MediaKind.VIDEO, state = DlState.RUNNING, bytes = 42, total = 100)
        assertEquals("下载中 42%", com.moonlight.feed.ui.feed.feedMenuSpec(archive, false, true, 1f, true, true, h, dl = running).radial!!
            .single { it.title == "分享·保存" }.children!!.single { it.title == "下载" }.subtitle)

        val bili = MediaEntry(kind = MediaKind.BILI, title = "b", biliBvid = "BV1", platform = PlatformId.BILI)
        val b = com.moonlight.feed.ui.feed.feedMenuSpec(bili, false, true, 1f, true, true, h)
        assertTrue("not downloadable → absent from the ring", b.radial!!.flatMap { listOf(it) + it.children.orEmpty() }.none { it.title == "下载" })
        assertTrue(b.actions.flatten().single { it.title.startsWith("下载") }.disabledReason!!.contains("哔哩"))

        val p = com.moonlight.feed.ui.player.playerMediaMenu(androidx.compose.ui.geometry.Offset(1f, 1f), archive, false, false, com.moonlight.feed.ui.player.PlayerActions())
        assertEquals(4, p.radial!!.size); assertTrue(p.radial!!.single { it.title == "分享·保存" }.children!!.any { it.title == "下载" })
        val pe = com.moonlight.feed.ui.player.playerMediaMenu(androidx.compose.ui.geometry.Offset(1f, 1f), bili, false, true, com.moonlight.feed.ui.player.PlayerActions())
        assertNotNull(pe.actions.flatten().single { it.title.startsWith("下载") }.disabledReason)

        val mv = com.moonlight.feed.ui.social.momentVideoMenuSpec(androidx.compose.ui.geometry.Rect.Zero, null, false, true, true, true, true,
            com.moonlight.feed.ui.social.MomentVideoHandlers(), download = com.moonlight.feed.ui.download.DownloadActions.ringChild(DownloadPolicy.check(archive), null) {})
        assertEquals(4, mv.radial!!.size)
        assertEquals(listOf("保存到相册", "下载"), mv.radial!!.single { it.title == "保存" }.children!!.map { it.title })
    }

    // ------------------------------------------------------------------ social.json writes
    @Test fun coalescingWriterIsOffThreadLatestWinsAndAtomic() = runBlocking {
        val f = File(dir, "social.json"); val caller = Thread.currentThread()
        val threads = CopyOnWriteArrayList<Thread>()
        val w = CoalescingWriter<Int>(f, io, debounceMs = 120) { v -> threads += Thread.currentThread(); "{\"v\":$v}" }
        repeat(500) { w.submit(it) }
        kotlinx.coroutines.delay(600)
        assertEquals("{\"v\":499}", f.readText())
        assertTrue("500 rapid edits coalesced into ${w.writes} writes", w.writes in 1..2)
        assertTrue("encode never runs on the submitting thread", threads.none { it == caller })
        assertFalse("no tmp left behind", File(dir, "social.json.tmp").exists())
        w.submit(500); w.flush(); assertEquals("{\"v\":500}", f.readText())
    }

    @Test fun socialRepositoryNoLongerEncodesOnTheCaller() {
        val src = listOf(File("src/main/java"), File("app/src/main/java")).first { it.exists() }
        val repo = File(src, "com/moonlight/feed/data/social/SocialRepository.kt").readText()
        val edit = repo.substringAfter("private fun edit(").substringBefore("\n    }")
        assertFalse("edit() must not encode", "SocialJson.encode" in edit)
        assertTrue("edit() hands the state to the IO writer", "writer.submit(n)" in edit)
        assertTrue("CoalescingWriter(file, io" in repo)
        assertFalse("no direct writeText of social.json", "file.writeText" in repo)
    }
}
