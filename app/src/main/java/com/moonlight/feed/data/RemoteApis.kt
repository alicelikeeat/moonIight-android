package com.moonlight.feed.data

import com.moonlight.feed.MoonApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import kotlin.random.Random

object RemoteApis {
    private val http get() = MoonApp.instance.http
    private val ua = "Moonlight/2.5 (Android; educational)"

    private fun get(url: String, accept: String = "application/json,*/*", timeoutHintMs: Long = 12_000): String? {
        val req = Request.Builder().url(url)
            .header("User-Agent", ua)
            .header("Accept", accept)
            .build()
        return try {
            http.newCall(req).execute().use { r ->
                if (!r.isSuccessful) null else r.body?.string()
            }
        } catch (_: Throwable) { null }
    }

    private suspend fun getTimed(url: String, accept: String = "application/json,*/*"): String? =
        withTimeoutOrNull(12_000) { withContext(Dispatchers.IO) { get(url, accept) } }

    // --- Bilibili ---
    suspend fun biliPopular(pn: Int = 1, ps: Int = 20): List<BiliCard> = withContext(Dispatchers.IO) {
        val raw = get("https://api.bilibili.com/x/web-interface/popular?ps=$ps&pn=$pn") ?: return@withContext emptyList()
        parseBiliList(raw)
    }

    suspend fun biliRanking(rid: Int): List<BiliCard> = withContext(Dispatchers.IO) {
        val raw = get("https://api.bilibili.com/x/web-interface/ranking/v2?rid=$rid&type=all")
            ?: return@withContext emptyList()
        parseBiliList(raw)
    }

    suspend fun biliPrecious(): List<BiliCard> = withContext(Dispatchers.IO) {
        val raw = get("https://api.bilibili.com/x/web-interface/popular/precious")
            ?: return@withContext emptyList()
        parseBiliList(raw)
    }

    suspend fun biliResolveBvid(bvid: String): BiliCard? = withContext(Dispatchers.IO) {
        get("https://api.bilibili.com/x/web-interface/view?bvid=$bvid")?.let { raw ->
            try {
                val d = JSONObject(raw).optJSONObject("data") ?: return@let null
                return@withContext BiliCard(
                    bvid = d.optString("bvid", bvid),
                    title = d.optString("title"),
                    cover = d.optString("pic").replace("http://", "https://"),
                    author = d.optJSONObject("owner")?.optString("name") ?: "",
                    play = d.optJSONObject("stat")?.optLong("view") ?: 0,
                    duration = d.optInt("duration"),
                )
            } catch (_: Throwable) { }
        }
        BiliCard(bvid = bvid, title = bvid, cover = "", author = "哔哩哔哩")
    }

    fun biliCardToEntry(card: BiliCard): MediaEntry = MediaEntry(
        kind = MediaKind.EMBED,
        title = card.title,
        coverUrl = card.cover.ifBlank { null },
        biliBvid = card.bvid,
        platform = PlatformId.BILI,
        embedUrl = PlatformResolver.biliEmbed(card.bvid),
        author = card.author,
        source = "bili",
    )

    private fun parseBiliList(raw: String): List<BiliCard> = try {
        val root = JSONObject(raw)
        if (root.optInt("code") != 0) emptyList()
        else {
            val data = root.optJSONObject("data") ?: return emptyList()
            val list = data.optJSONArray("list") ?: return emptyList()
            (0 until list.length()).mapNotNull { i ->
                val o = list.optJSONObject(i) ?: return@mapNotNull null
                val bvid = o.optString("bvid")
                if (bvid.isBlank()) return@mapNotNull null
                BiliCard(
                    bvid = bvid,
                    title = o.optString("title"),
                    cover = o.optString("pic").replace("http://", "https://"),
                    author = o.optJSONObject("owner")?.optString("name") ?: "",
                    play = o.optJSONObject("stat")?.optLong("view") ?: 0L,
                    duration = o.optInt("duration"),
                )
            }
        }
    } catch (_: Throwable) { emptyList() }

    val BILI_RIDS = listOf(
        1 to "动画", 3 to "音乐", 4 to "游戏", 5 to "娱乐",
        36 to "科技", 119 to "鬼畜", 129 to "舞蹈", 155 to "时尚",
        160 to "生活", 181 to "影视", 188 to "数码", 211 to "美食",
        217 to "动物圈", 223 to "汽车", 234 to "运动"
    )

    suspend fun biliRandomDiscovery(exclude: Set<String>): List<BiliCard> {
        val pick = BILI_RIDS.random()
        val page = Random.nextInt(1, 6)
        val fromRank = biliRanking(pick.first)
        val fromPop = biliPopular(page, 20)
        return (fromRank + fromPop)
            .distinctBy { it.bvid }
            .filter { card ->
                exclude.none { ex ->
                    card.title.contains(ex, true) || card.author.contains(ex, true)
                }
            }
            .shuffled()
            .take(24)
    }

    // --- YouTube RSS / Data API ---
    /** Public channels for RSS-based discovery (no API key). */
    val YT_CHANNELS = listOf(
        "UC_x5XG1OV2P6uZZ5FSM9Ttw", // Google Developers
        "UCEAKeXNzKuCMLACSp1mvY4A", // NASA
        "UCKWaEZ-_VkcN4HnlCf2QaLQ", // Kurzgesagt-ish may change — NatGeo
        "UC6nSFpj9HTCZ5t-N3Rm3-HA", // Vsauce
        "UCHnyfMqiRRG1u-2MsSQLbXA", // Veritasium
    )

    suspend fun youtubeChannelRss(channelId: String, limit: Int = 10): List<MediaEntry> =
        withContext(Dispatchers.IO) {
            val raw = get(
                "https://www.youtube.com/feeds/videos.xml?channel_id=$channelId",
                accept = "application/atom+xml,application/xml,text/xml,*/*",
            ) ?: return@withContext emptyList()
            parseYoutubeAtom(raw, limit)
        }

    suspend fun youtubePlaylistRss(playlistId: String, limit: Int = 10): List<MediaEntry> =
        withContext(Dispatchers.IO) {
            val raw = get(
                "https://www.youtube.com/feeds/videos.xml?playlist_id=$playlistId",
                accept = "application/atom+xml,application/xml,text/xml,*/*",
            ) ?: return@withContext emptyList()
            parseYoutubeAtom(raw, limit)
        }

    private fun parseYoutubeAtom(raw: String, limit: Int): List<MediaEntry> {
        val out = mutableListOf<MediaEntry>()
        val entryRe = Regex("""<entry>(.*?)</entry>""", setOf(RegexOption.DOT_MATCHES_ALL))
        val idRe = Regex("""<yt:videoId>([^<]+)</yt:videoId>""")
        val titleRe = Regex("""<title>(?:<!\[CDATA\[)?(.*?)(?:]]>)?</title>""", RegexOption.DOT_MATCHES_ALL)
        val authorRe = Regex("""<name>([^<]+)</name>""")
        val thumbRe = Regex("""media:thumbnail[^>]*url="([^"]+)"""")
        for (m in entryRe.findAll(raw)) {
            if (out.size >= limit) break
            val block = m.groupValues[1]
            val id = idRe.find(block)?.groupValues?.getOrNull(1) ?: continue
            val title = titleRe.find(block)?.groupValues?.getOrNull(1)?.trim().orEmpty().ifBlank { id }
            val author = authorRe.find(block)?.groupValues?.getOrNull(1).orEmpty()
            val thumb = thumbRe.find(block)?.groupValues?.getOrNull(1)
            out += MediaEntry(
                kind = MediaKind.EMBED,
                title = title,
                remoteUrl = "https://www.youtube.com/watch?v=$id",
                coverUrl = thumb,
                platform = PlatformId.YOUTUBE,
                embedUrl = PlatformResolver.youtubeEmbed(id),
                author = author.ifBlank { "YouTube" },
                source = "youtube",
            )
        }
        return out
    }

    suspend fun youtubeSearchApi(key: String, query: String, limit: Int = 12, pageToken: String? = null): List<MediaEntry> =
        withContext(Dispatchers.IO) {
            if (key.isBlank()) return@withContext emptyList()
            val q = URLEncoder.encode(query, "UTF-8")
            val raw = get(
                "https://www.googleapis.com/youtube/v3/search?part=snippet&type=video&maxResults=$limit&q=$q&key=$key" + (pageToken?.let { "&pageToken=$it" } ?: ""),
            ) ?: return@withContext emptyList()
            try {
                val items = JSONObject(raw).optJSONArray("items") ?: return@withContext emptyList()
                (0 until items.length()).mapNotNull { i ->
                    val o = items.optJSONObject(i) ?: return@mapNotNull null
                    val id = o.optJSONObject("id")?.optString("videoId") ?: return@mapNotNull null
                    val sn = o.optJSONObject("snippet") ?: return@mapNotNull null
                    MediaEntry(
                        kind = MediaKind.EMBED,
                        title = sn.optString("title"),
                        remoteUrl = "https://www.youtube.com/watch?v=$id",
                        coverUrl = sn.optJSONObject("thumbnails")?.optJSONObject("high")?.optString("url")
                            ?: sn.optJSONObject("thumbnails")?.optJSONObject("medium")?.optString("url"),
                        platform = PlatformId.YOUTUBE,
                        embedUrl = PlatformResolver.youtubeEmbed(id),
                        author = sn.optString("channelTitle"),
                        source = "youtube",
                    )
                }
            } catch (_: Throwable) { emptyList() }
        }

    // --- Vimeo oEmbed ---
    suspend fun vimeoOembed(url: String): MediaEntry? = withContext(Dispatchers.IO) {
        val enc = URLEncoder.encode(url, "UTF-8")
        val raw = get("https://vimeo.com/api/oembed.json?url=$enc") ?: return@withContext null
        try {
            val o = JSONObject(raw)
            val id = PlatformResolver.extractVimeoId(url)
                ?: Regex("""/video/(\d+)""").find(o.optString("html"))?.groupValues?.getOrNull(1)
                ?: return@withContext null
            MediaEntry(
                kind = MediaKind.EMBED,
                title = o.optString("title").ifBlank { "Vimeo $id" },
                remoteUrl = "https://vimeo.com/$id",
                coverUrl = o.optString("thumbnail_url").ifBlank { null },
                platform = PlatformId.VIMEO,
                embedUrl = PlatformResolver.vimeoEmbed(id),
                author = o.optString("author_name").ifBlank { "Vimeo" },
                source = "vimeo",
            )
        } catch (_: Throwable) { null }
    }

    // --- Dailymotion public API ---
    suspend fun dailymotionSearch(query: String, limit: Int = 12, page: Int = 1): List<MediaEntry> =
        withContext(Dispatchers.IO) {
            val q = URLEncoder.encode(query, "UTF-8")
            val raw = get(
                "https://api.dailymotion.com/videos?search=$q&limit=$limit&page=$page&fields=id,title,thumbnail_720_url,thumbnail_url,owner.screenname,url",
            ) ?: return@withContext emptyList()
            parseDailymotion(raw)
        }

    suspend fun dailymotionTrending(limit: Int = 12): List<MediaEntry> =
        withContext(Dispatchers.IO) {
            val raw = get(
                "https://api.dailymotion.com/videos?sort=trending&limit=$limit&fields=id,title,thumbnail_720_url,thumbnail_url,owner.screenname,url",
            ) ?: return@withContext emptyList()
            parseDailymotion(raw)
        }

    private fun parseDailymotion(raw: String): List<MediaEntry> = try {
        val list = JSONObject(raw).optJSONArray("list") ?: return emptyList()
        (0 until list.length()).mapNotNull { i ->
            val o = list.optJSONObject(i) ?: return@mapNotNull null
            val id = o.optString("id")
            if (id.isBlank()) return@mapNotNull null
            MediaEntry(
                kind = MediaKind.EMBED,
                title = o.optString("title"),
                remoteUrl = o.optString("url").ifBlank { "https://www.dailymotion.com/video/$id" },
                coverUrl = o.optString("thumbnail_720_url").ifBlank { o.optString("thumbnail_url") }.ifBlank { null },
                platform = PlatformId.DAILYMOTION,
                embedUrl = PlatformResolver.dailymotionEmbed(id),
                author = o.optString("owner.screenname").ifBlank { "Dailymotion" },
                source = "dailymotion",
            )
        }
    } catch (_: Throwable) { emptyList() }

    // --- Internet Archive ---
    suspend fun archiveSearch(query: String, limit: Int = 8, page: Int = 1): List<MediaEntry> =
        withContext(Dispatchers.IO) {
            val q = URLEncoder.encode("mediatype:movies AND format:\"MPEG4\" AND ($query)", "UTF-8")
            val raw = get(
                "https://archive.org/advancedsearch.php?q=$q&fl[]=identifier&fl[]=title&fl[]=creator&rows=$limit&page=$page&output=json",
            ) ?: return@withContext emptyList()
            try {
                val docs = JSONObject(raw).optJSONObject("response")?.optJSONArray("docs")
                    ?: return@withContext emptyList()
                val out = mutableListOf<MediaEntry>()
                for (i in 0 until docs.length()) {
                    val o = docs.optJSONObject(i) ?: continue
                    val id = o.optString("identifier")
                    if (id.isBlank()) continue
                    val entry = archiveResolveItem(id, o.optString("title"), o.opt("creator"))
                    if (entry != null) out += entry
                }
                out
            } catch (_: Throwable) { emptyList() }
        }

    private fun archiveResolveItem(id: String, titleHint: String, creator: Any?): MediaEntry? {
        val meta = get("https://archive.org/metadata/$id") ?: return null
        return try {
            val root = JSONObject(meta)
            val files = root.optJSONArray("files") ?: return null
            var bestName: String? = null
            var bestSize = Long.MAX_VALUE
            for (i in 0 until files.length()) {
                val f = files.optJSONObject(i) ?: continue
                val name = f.optString("name")
                if (!name.lowercase().endsWith(".mp4")) continue
                if (name.contains("sample", true)) continue
                val size = f.optString("size").toLongOrNull() ?: continue
                if (size in 100_000..bestSize) {
                    bestSize = size
                    bestName = name
                }
            }
            // fallback any mp4
            if (bestName == null) {
                for (i in 0 until files.length()) {
                    val f = files.optJSONObject(i) ?: continue
                    val name = f.optString("name")
                    if (name.lowercase().endsWith(".mp4") && !name.contains("sample", true)) {
                        bestName = name; break
                    }
                }
            }
            val file = bestName ?: return null
            val url = "https://archive.org/download/$id/${URLEncoder.encode(file, "UTF-8").replace("+", "%20")}"
            val creatorStr = when (creator) {
                is JSONArray -> (0 until creator.length()).joinToString { creator.optString(it) }
                is String -> creator
                else -> root.optJSONObject("metadata")?.optString("creator").orEmpty()
            }
            val title = titleHint.ifBlank {
                root.optJSONObject("metadata")?.optString("title").orEmpty()
            }.ifBlank { id }
            MediaEntry(
                kind = MediaKind.VIDEO,
                title = title,
                remoteUrl = url,
                coverUrl = "https://archive.org/services/img/$id",
                platform = PlatformId.ARCHIVE,
                author = creatorStr.ifBlank { "Internet Archive" },
                source = "archive",
            )
        } catch (_: Throwable) { null }
    }

    // --- NASA ---
    suspend fun nasaSearch(query: String, limit: Int = 8, page: Int = 1): List<MediaEntry> =
        withContext(Dispatchers.IO) {
            val q = URLEncoder.encode(query, "UTF-8")
            val raw = get("https://images-api.nasa.gov/search?q=$q&media_type=video&page_size=$limit&page=$page")
                ?: return@withContext emptyList()
            try {
                val items = JSONObject(raw).optJSONObject("collection")?.optJSONArray("items")
                    ?: return@withContext emptyList()
                val out = mutableListOf<MediaEntry>()
                for (i in 0 until items.length()) {
                    if (out.size >= limit) break
                    val item = items.optJSONObject(i) ?: continue
                    val data = item.optJSONArray("data")?.optJSONObject(0) ?: continue
                    val nasaId = data.optString("nasa_id")
                    if (nasaId.isBlank()) continue
                    val title = data.optString("title").ifBlank { nasaId }
                    val cover = item.optJSONArray("links")?.optJSONObject(0)?.optString("href")
                    val assetRaw = get("https://images-api.nasa.gov/asset/${URLEncoder.encode(nasaId, "UTF-8")}")
                        ?: continue
                    val mp4 = pickNasaMp4(assetRaw) ?: continue
                    out += MediaEntry(
                        kind = MediaKind.VIDEO,
                        title = title,
                        remoteUrl = mp4.replace("http://", "https://"),
                        coverUrl = cover?.replace("http://", "https://"),
                        platform = PlatformId.NASA,
                        author = data.optString("center").ifBlank { "NASA" },
                        source = "nasa",
                    )
                }
                out
            } catch (_: Throwable) { emptyList() }
        }

    private fun pickNasaMp4(assetRaw: String): String? = try {
        val items = JSONObject(assetRaw).optJSONObject("collection")?.optJSONArray("items")
            ?: return null
        val hrefs = (0 until items.length()).mapNotNull { items.optJSONObject(it)?.optString("href") }
        hrefs.firstOrNull { it.contains("~medium.mp4", true) }
            ?: hrefs.firstOrNull { it.contains("~mobile.mp4", true) }
            ?: hrefs.firstOrNull { it.endsWith(".mp4", true) && !it.contains("orig", true) }
            ?: hrefs.firstOrNull { it.endsWith(".mp4", true) }
    } catch (_: Throwable) { null }

    // --- Wikimedia Commons ---
    suspend fun commonsSearch(keyword: String, limit: Int = 16): List<MediaEntry> =
        withContext(Dispatchers.IO) {
            val q = URLEncoder.encode("$keyword filetype:bitmap|video", "UTF-8")
            val url =
                "https://commons.wikimedia.org/w/api.php?action=query&format=json" +
                    "&generator=search&gsrnamespace=6&gsrlimit=$limit&gsrsearch=$q" +
                    "&prop=imageinfo&iiprop=url|mime|size&iiurlwidth=1080"
            val raw = get(url) ?: return@withContext emptyList()
            parseCommons(raw)
        }

    suspend fun commonsRandom(keywords: List<String>, exclude: Set<String>): List<MediaEntry> {
        val kw = keywords.filter { it.isNotBlank() && it !in exclude }.ifEmpty { listOf("nature") }
        return commonsSearch(kw.random(), 20).filter { e ->
            exclude.none { ex -> e.title.contains(ex, true) }
        }
    }

    private fun parseCommons(raw: String): List<MediaEntry> = try {
        val pages = JSONObject(raw).optJSONObject("query")?.optJSONObject("pages")
            ?: return emptyList()
        val out = mutableListOf<MediaEntry>()
        val keys = pages.keys()
        while (keys.hasNext()) {
            val p = pages.getJSONObject(keys.next())
            val info = p.optJSONArray("imageinfo")?.optJSONObject(0) ?: continue
            val mime = info.optString("mime")
            val full = info.optString("url")
            val thumb = info.optString("thumburl").ifBlank { full }
            if (full.isBlank() && thumb.isBlank()) continue
            val kind = when {
                mime.startsWith("video") -> MediaKind.VIDEO
                mime.startsWith("image") -> MediaKind.IMAGE
                else -> continue
            }
            val title = p.optString("title").removePrefix("File:")
            out += MediaEntry(
                kind = kind,
                title = title,
                remoteUrl = full.ifBlank { thumb },
                coverUrl = thumb.ifBlank { null },
                platform = PlatformId.COMMONS,
                author = "Wikimedia",
                source = "commons",
            )
        }
        out
    } catch (_: Throwable) { emptyList() }

    // --- PeerTube (Sepia Search) ---
    suspend fun peertubeSearch(query: String, limit: Int = 10, start: Int = 0): List<MediaEntry> =
        withContext(Dispatchers.IO) {
            val q = URLEncoder.encode(query, "UTF-8")
            val raw = get("https://sepiasearch.org/api/v1/search/videos?search=$q&count=$limit&start=$start")
                ?: return@withContext emptyList()
            try {
                val data = JSONObject(raw).optJSONArray("data") ?: return@withContext emptyList()
                (0 until data.length()).mapNotNull { i ->
                    val o = data.optJSONObject(i) ?: return@mapNotNull null
                    val name = o.optString("name")
                    val embedUrl = o.optString("embedUrl").ifBlank {
                        val host = o.optJSONObject("account")?.optString("host") ?: return@mapNotNull null
                        val path = o.optString("embedPath")
                        if (path.isBlank()) return@mapNotNull null
                        "https://$host$path"
                    }
                    val thumb = o.optString("thumbnailUrl").ifBlank {
                        val host = o.optJSONObject("account")?.optString("host")
                        val path = o.optString("thumbnailPath")
                        if (host != null && path.isNotBlank()) "https://$host$path" else null
                    }
                    val uuid = o.optString("uuid")
                    val host = o.optJSONObject("account")?.optString("host")
                    // try downloadable file from instance API (optional)
                    var remote: String? = null
                    if (!host.isNullOrBlank() && uuid.isNotBlank()) {
                        remote = peertubeFileUrl(host, uuid)
                    }
                    if (remote != null) {
                        MediaEntry(
                            kind = MediaKind.VIDEO,
                            title = name,
                            remoteUrl = remote,
                            coverUrl = thumb,
                            platform = PlatformId.PEERTUBE,
                            author = o.optJSONObject("account")?.optString("displayName") ?: "PeerTube",
                            source = "peertube",
                        )
                    } else {
                        MediaEntry(
                            kind = MediaKind.EMBED,
                            title = name,
                            remoteUrl = o.optString("url").ifBlank { null },
                            coverUrl = thumb,
                            platform = PlatformId.PEERTUBE,
                            embedUrl = embedUrl,
                            author = o.optJSONObject("account")?.optString("displayName") ?: "PeerTube",
                            source = "peertube",
                        )
                    }
                }
            } catch (_: Throwable) { emptyList() }
        }

    private fun peertubeFileUrl(host: String, uuid: String): String? {
        val raw = get("https://$host/api/v1/videos/$uuid") ?: return null
        return try {
            val files = JSONObject(raw).optJSONArray("files") ?: return null
            // prefer mid quality
            var best: String? = null
            var bestH = 0
            for (i in 0 until files.length()) {
                val f = files.optJSONObject(i) ?: continue
                val h = f.optJSONObject("resolution")?.optInt("id") ?: 0
                val u = f.optString("fileDownloadUrl").ifBlank { f.optString("fileUrl") }
                if (u.isBlank()) continue
                if (h in 360..bestH.coerceAtLeast(360) || best == null) {
                    if (h >= 360 && (best == null || h < 900)) {
                        best = u; bestH = h
                    } else if (best == null) {
                        best = u; bestH = h
                    }
                }
            }
            best
        } catch (_: Throwable) { null }
    }

    // --- Pexels / Pixabay videos ---
    suspend fun pexelsSearch(key: String, query: String): List<MediaEntry> =
        withContext(Dispatchers.IO) {
            if (key.isBlank()) return@withContext emptyList()
            val q = URLEncoder.encode(query, "UTF-8")
            val req = Request.Builder()
                .url("https://api.pexels.com/v1/search?query=$q&per_page=12")
                .header("Authorization", key)
                .build()
            val photos = try {
                http.newCall(req).execute().use { r ->
                    val raw = r.body?.string() ?: return@withContext emptyList()
                    JSONObject(raw).optJSONArray("photos")
                }
            } catch (_: Throwable) { null } ?: return@withContext emptyList()
            val images = (0 until photos.length()).mapNotNull { i ->
                val o = photos.optJSONObject(i) ?: return@mapNotNull null
                val src = o.optJSONObject("src")?.optString("large2x")
                    ?: o.optJSONObject("src")?.optString("large") ?: return@mapNotNull null
                MediaEntry(
                    kind = MediaKind.IMAGE,
                    title = o.optString("alt").ifBlank { "Pexels" },
                    remoteUrl = src,
                    coverUrl = src,
                    platform = PlatformId.PEXELS,
                    author = o.optJSONObject("photographer")?.optString("name")
                        ?: o.optString("photographer").ifBlank { "Pexels" },
                    source = "pexels",
                )
            }
            val vids = pexelsVideos(key, query)
            images + vids
        }

    suspend fun pexelsVideoSearch(key: String, query: String, page: Int = 1): List<MediaEntry> =
        withContext(Dispatchers.IO) { if (key.isBlank()) emptyList() else pexelsVideos(key, query, page) }

    suspend fun pixabayVideoSearch(key: String, query: String, page: Int = 1): List<MediaEntry> =
        withContext(Dispatchers.IO) {
            if (key.isBlank()) return@withContext emptyList()
            val q = URLEncoder.encode(query, "UTF-8")
            val raw = get("https://pixabay.com/api/videos/?key=$key&q=$q&per_page=12&page=$page&safesearch=true") ?: return@withContext emptyList()
            try {
                val hits = JSONObject(raw).optJSONArray("hits") ?: return@withContext emptyList()
                (0 until hits.length()).mapNotNull { i ->
                    val o = hits.optJSONObject(i) ?: return@mapNotNull null
                    val v = o.optJSONObject("videos") ?: return@mapNotNull null
                    val pick = listOf("medium", "small", "large").firstNotNullOfOrNull { s -> v.optJSONObject(s)?.takeIf { it.optString("url").isNotBlank() } } ?: return@mapNotNull null
                    MediaEntry(
                        id = "pixabay-v-" + o.optString("id"),
                        kind = MediaKind.VIDEO,
                        title = o.optString("tags").ifBlank { "Pixabay video" },
                        remoteUrl = pick.optString("url"),
                        coverUrl = pick.optString("thumbnail").ifBlank { null },
                        platform = PlatformId.PIXABAY,
                        author = o.optString("user"),
                        source = "pixabay",
                    )
                }
            } catch (_: Throwable) { emptyList() }
        }

    /** Commons, video files only (webm/ogv), paged by offset. */
    suspend fun commonsVideoSearch(keyword: String, limit: Int = 16, offset: Int = 0): List<MediaEntry> =
        withContext(Dispatchers.IO) {
            val q = URLEncoder.encode("$keyword filetype:video", "UTF-8")
            val url = "https://commons.wikimedia.org/w/api.php?action=query&format=json" +
                "&generator=search&gsrnamespace=6&gsrlimit=$limit&gsroffset=$offset&gsrsearch=$q" +
                "&prop=imageinfo&iiprop=url|mime|size&iiurlwidth=960"
            val raw = get(url) ?: return@withContext emptyList()
            parseCommons(raw).filter { it.kind == MediaKind.VIDEO }
        }

    private fun pexelsVideos(key: String, query: String, page: Int = 1): List<MediaEntry> {
        val q = URLEncoder.encode(query, "UTF-8")
        val req = Request.Builder()
            .url("https://api.pexels.com/videos/search?query=$q&per_page=12&page=$page")
            .header("Authorization", key)
            .build()
        return try {
            http.newCall(req).execute().use { r ->
                val raw = r.body?.string() ?: return emptyList()
                val videos = JSONObject(raw).optJSONArray("videos") ?: return emptyList()
                (0 until videos.length()).mapNotNull { i ->
                    val o = videos.optJSONObject(i) ?: return@mapNotNull null
                    val files = o.optJSONArray("video_files") ?: return@mapNotNull null
                    var best: String? = null
                    var bestW = Int.MAX_VALUE
                    for (j in 0 until files.length()) {
                        val f = files.optJSONObject(j) ?: continue
                        if (f.optString("file_type") != "video/mp4") continue
                        val w = f.optInt("width")
                        val link = f.optString("link")
                        if (link.isBlank()) continue
                        if (w in 480..1280 && w < bestW) {
                            best = link; bestW = w
                        } else if (best == null) {
                            best = link; bestW = w
                        }
                    }
                    val url = best ?: return@mapNotNull null
                    val cover = o.optJSONObject("image")?.toString()?.let { null }
                        ?: o.optString("image").ifBlank { null }
                    MediaEntry(
                        kind = MediaKind.VIDEO,
                        title = "Pexels video",
                        remoteUrl = url,
                        coverUrl = cover,
                        platform = PlatformId.PEXELS,
                        author = o.optJSONObject("user")?.optString("name") ?: "Pexels",
                        source = "pexels",
                    )
                }
            }
        } catch (_: Throwable) { emptyList() }
    }

    suspend fun pixabaySearch(key: String, query: String): List<MediaEntry> =
        withContext(Dispatchers.IO) {
            if (key.isBlank()) return@withContext emptyList()
            val q = URLEncoder.encode(query, "UTF-8")
            val photos = get("https://pixabay.com/api/?key=$key&q=$q&image_type=photo&per_page=12")
            val images = try {
                val hits = JSONObject(photos ?: return@withContext emptyList()).optJSONArray("hits")
                    ?: return@withContext emptyList()
                (0 until hits.length()).mapNotNull { i ->
                    val o = hits.optJSONObject(i) ?: return@mapNotNull null
                    val url = o.optString("largeImageURL").ifBlank { o.optString("webformatURL") }
                    if (url.isBlank()) return@mapNotNull null
                    MediaEntry(
                        kind = MediaKind.IMAGE,
                        title = o.optString("tags").ifBlank { "Pixabay" },
                        remoteUrl = url,
                        coverUrl = url,
                        platform = PlatformId.PIXABAY,
                        author = o.optString("user"),
                        source = "pixabay",
                    )
                }
            } catch (_: Throwable) { emptyList() }
            val vidsRaw = get("https://pixabay.com/api/videos/?key=$key&q=$q&per_page=8")
            val vids = try {
                val hits = JSONObject(vidsRaw ?: return@withContext images).optJSONArray("hits")
                    ?: return@withContext images
                (0 until hits.length()).mapNotNull { i ->
                    val o = hits.optJSONObject(i) ?: return@mapNotNull null
                    val videos = o.optJSONObject("videos") ?: return@mapNotNull null
                    val mid = videos.optJSONObject("medium")?.optString("url")
                        ?: videos.optJSONObject("small")?.optString("url")
                        ?: videos.optJSONObject("large")?.optString("url")
                        ?: return@mapNotNull null
                    MediaEntry(
                        kind = MediaKind.VIDEO,
                        title = o.optString("tags").ifBlank { "Pixabay video" },
                        remoteUrl = mid,
                        coverUrl = null,
                        platform = PlatformId.PIXABAY,
                        author = o.optString("user"),
                        source = "pixabay",
                    )
                }
            } catch (_: Throwable) { emptyList() }
            images + vids
        }

    // --- Resolve pasted URL with enrichment ---
    suspend fun resolveUrl(text: String): PlatformResolver.ResolveResult = withContext(Dispatchers.IO) {
        val local = PlatformResolver.resolveLocal(text)
        if (local.unsupported || local.entry == null) return@withContext local
        val e = local.entry
        when (e.platform) {
            PlatformId.BILI -> {
                val bv = e.biliBvid ?: return@withContext local
                val card = biliResolveBvid(bv) ?: return@withContext local
                PlatformResolver.ResolveResult(biliCardToEntry(card))
            }
            PlatformId.VIMEO -> {
                val url = e.remoteUrl ?: return@withContext local
                PlatformResolver.ResolveResult(vimeoOembed(url) ?: e)
            }
            PlatformId.ARCHIVE -> {
                val id = Regex("""archive\.org/(?:details|download)/([^/?#]+)""")
                    .find(text)?.groupValues?.getOrNull(1)
                if (id != null) {
                    val resolved = archiveResolveItem(id, e.title, null)
                    PlatformResolver.ResolveResult(resolved ?: e)
                } else local
            }
            else -> local
        }
    }

    /**
     * Multi-platform random discovery. Each source is timed; failures are skipped.
     */
    suspend fun multiRandomDiscovery(
        keywords: List<String>,
        exclude: Set<String>,
        enabled: Set<String>,
        youtubeKey: String,
        pexelsKey: String,
        pixabayKey: String,
        limitPer: Int = 6,
        userSources: List<UserSource> = emptyList(),
    ): List<MediaEntry> = coroutineScope {
        val kw = keywords.filter { it.isNotBlank() }.ifEmpty { listOf("nature", "space", "city") }
        val q = kw.random()
        val jobs = mutableListOf<kotlinx.coroutines.Deferred<List<MediaEntry>>>()
        fun add(platform: String, block: suspend () -> List<MediaEntry>) {
            if (platform !in enabled) return
            jobs += async {
                withTimeoutOrNull(14_000) {
                    try { block() } catch (_: Throwable) { emptyList() }
                } ?: emptyList()
            }
        }
        add(PlatformId.BILI) {
            biliRandomDiscovery(exclude).map { biliCardToEntry(it) }.take(limitPer)
        }
        add(PlatformId.YOUTUBE) {
            if (youtubeKey.isNotBlank()) youtubeSearchApi(youtubeKey, q, limitPer)
            else youtubeChannelRss(YT_CHANNELS.random(), limitPer)
        }
        add(PlatformId.DAILYMOTION) {
            dailymotionSearch(q, limitPer).ifEmpty { dailymotionTrending(limitPer) }
        }
        add(PlatformId.ARCHIVE) { archiveSearch(q, limitPer.coerceAtMost(5)) }
        add(PlatformId.NASA) { nasaSearch(q, limitPer.coerceAtMost(5)) }
        add(PlatformId.COMMONS) { commonsSearch(q, limitPer) }
        add(PlatformId.PEERTUBE) { peertubeSearch(q, limitPer) }
        add(PlatformId.PEXELS) { pexelsSearch(pexelsKey, q).take(limitPer) }
        add(PlatformId.PIXABAY) { pixabaySearch(pixabayKey, q).take(limitPer) }
        // user-added websites (enabled ones) join 多源 random discovery
        if (userSources.any { it.enabled }) jobs += async {
            withTimeoutOrNull(14_000) {
                try { SourceFetcher.discover(userSources, limitPer) } catch (_: Throwable) { emptyList() }
            } ?: emptyList()
        }
        add(PlatformId.VIMEO) {
            // no free search — skip unless we had a staff pick; return empty
            emptyList()
        }
        jobs.flatMap { it.await() }
            .filter { e ->
                exclude.none { ex ->
                    e.title.contains(ex, true) || e.author.contains(ex, true)
                }
            }
            .distinctBy { it.remoteUrl ?: it.embedUrl ?: it.biliBvid ?: it.id }
            .shuffled()
    }

    fun extractBvid(text: String): String? {
        return Regex("""BV[0-9A-Za-z]{10}""").find(text)?.value
    }
}
