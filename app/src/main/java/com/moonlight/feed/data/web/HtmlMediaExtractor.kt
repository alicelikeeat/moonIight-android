package com.moonlight.feed.data.web

/**
 * Extracts media candidates from an HTML page:
 * og:image / og:video / twitter:image, <img> (incl. srcset → largest, lazy data-src),
 * <video>/<source>, <picture><source srcset>, and <a href> to direct media files.
 * Skips data URIs, SVG, trackers/pixels, icons/logos/avatars and images smaller than [minSide] px when size is known.
 */
object HtmlMediaExtractor {
    enum class Kind { IMAGE, VIDEO }
    data class Media(val url: String, val kind: Kind, val title: String? = null, val poster: String? = null, val width: Int? = null, val height: Int? = null, val fromMeta: Boolean = false)
    data class Page(val url: String, val title: String?, val media: List<Media>, val links: List<String>)

    const val DEFAULT_MIN_SIDE = 200

    private val videoExt = Regex("""\.(mp4|webm|m4v|mov|ogv)(\?|$)""", RegexOption.IGNORE_CASE)
    private val imageExt = Regex("""\.(jpe?g|png|webp|gif|avif)(\?|$)""", RegexOption.IGNORE_CASE)
    private val skipName = Regex("""(^|[/_.-])(favicon|icon|icons|logo|logos|sprite|avatar|avatars|emoji|emojis|spacer|blank|pixel|badge|button|loading|placeholder|1x1|transparent)([/_.-]|$)""", RegexOption.IGNORE_CASE)
    private val trackerHosts = listOf(
        "doubleclick.net", "google-analytics.com", "googletagmanager.com", "googlesyndication.com", "facebook.com/tr",
        "scorecardresearch.com", "quantserve.com", "adservice.", "analytics.", "pixel.", "stats.", "hm.baidu.com", "cnzz.com", "bat.bing.com",
    )
    private val sizeInName = Regex("""[-_x/](\d{2,4})x(\d{2,4})[-_./]""")

    fun isTracker(url: String): Boolean {
        val low = url.lowercase()
        return trackerHosts.any { low.contains(it) }
    }

    fun extract(html: String, pageUrl: String, minSide: Int = DEFAULT_MIN_SIDE): Page {
        val clean = Html.strip(html)
        val base = Html.baseHref(clean, pageUrl)
        val title = metaContent(html, "og:title") ?: Html.title(html)
        val out = LinkedHashMap<String, Media>()

        fun accept(raw: String?, kind: Kind, w: Int? = null, h: Int? = null, poster: String? = null, meta: Boolean = false, alt: String? = null) {
            val url = Html.resolve(base, raw) ?: return
            val low = url.lowercase()
            if (low.substringBefore('?').endsWith(".svg") || low.substringBefore('?').endsWith(".ico")) return
            if (isTracker(url)) return
            if (kind == Kind.IMAGE && skipName.containsMatchIn(url.substringBefore('?'))) return
            val (nw, nh) = sizeInName.find(url)?.let { it.groupValues[1].toIntOrNull() to it.groupValues[2].toIntOrNull() } ?: (null to null)
            val ew = w ?: nw; val eh = h ?: nh
            if (kind == Kind.IMAGE && ((ew != null && ew < minSide) || (eh != null && eh < minSide))) return
            val key = UrlNorm.normalize(url)
            val prev = out[key]
            if (prev == null || (prev.kind == Kind.IMAGE && kind == Kind.VIDEO)) {
                out[key] = Media(url, kind, alt?.takeIf { it.isNotBlank() } ?: title, Html.resolve(base, poster), ew, eh, meta)
            }
        }

        // 1) Open Graph / Twitter cards
        val metas = Html.tags(html, setOf("meta")).toList()
        fun metaInt(name: String) = metas.firstOrNull { (it.attrs["property"] ?: it.attrs["name"])?.lowercase() == name }?.attrs?.get("content")?.toIntOrNull()
        val ogW = metaInt("og:image:width"); val ogH = metaInt("og:image:height")
        metas.forEach { m ->
            val p = (m.attrs["property"] ?: m.attrs["name"] ?: m.attrs["itemprop"])?.lowercase() ?: return@forEach
            val c = m.attrs["content"]
            when (p) {
                "og:video", "og:video:url", "og:video:secure_url", "twitter:player:stream" ->
                    if (c != null && videoExt.containsMatchIn(c)) accept(c, Kind.VIDEO, meta = true, poster = metaContent(html, "og:image"))
                "og:image", "og:image:url", "og:image:secure_url", "twitter:image", "twitter:image:src", "image" ->
                    accept(c, Kind.IMAGE, ogW, ogH, meta = true)
            }
        }

        // 2) Body tags
        var inVideo: Html.Tag? = null
        Html.tags(clean, setOf("img", "video", "source", "a")).forEach { t ->
            val a = t.attrs
            when (t.name) {
                "img" -> {
                    val w = a["width"]?.filter(Char::isDigit)?.toIntOrNull()
                    val h = a["height"]?.filter(Char::isDigit)?.toIntOrNull()
                    val srcset = a["srcset"] ?: a["data-srcset"]
                    val best = srcset?.let { largestFromSrcset(it) }
                    val src = best?.first ?: a["data-src"] ?: a["data-original"] ?: a["data-lazy-src"] ?: a["src"]
                    // srcset width descriptor is the real rendered size → trust it over tiny width attrs
                    val bw = best?.second?.takeIf { it > 0 } ?: w
                    accept(src, Kind.IMAGE, bw, if (best?.second != null && best.second > 0) null else h, alt = a["alt"])
                }
                "video" -> {
                    inVideo = t
                    accept(a["src"] ?: a["data-src"], Kind.VIDEO, poster = a["poster"])
                    a["poster"]?.let { accept(it, Kind.IMAGE) }
                }
                "source" -> {
                    val type = a["type"]?.lowercase().orEmpty()
                    val src = a["src"]
                    val srcset = a["srcset"]
                    when {
                        src != null && (type.startsWith("video") || videoExt.containsMatchIn(src)) ->
                            accept(src, Kind.VIDEO, poster = inVideo?.attrs?.get("poster"))
                        srcset != null -> largestFromSrcset(srcset)?.let { (u, w) -> accept(u, Kind.IMAGE, w.takeIf { it > 0 }) }
                        src != null && imageExt.containsMatchIn(src) -> accept(src, Kind.IMAGE)
                    }
                }
                "a" -> {
                    val href = a["href"] ?: return@forEach
                    when {
                        videoExt.containsMatchIn(href.substringBefore('#')) -> accept(href, Kind.VIDEO)
                        imageExt.containsMatchIn(href.substringBefore('#')) -> accept(href, Kind.IMAGE)
                    }
                }
            }
        }
        return Page(pageUrl, title, out.values.toList(), LinkSampler.extractLinks(clean, base))
    }

    /** Picks the largest candidate of a srcset ("a.jpg 320w, b.jpg 1280w" or "a.jpg 1x, b.jpg 2x"). Returns url to width (or 0 for x). */
    fun largestFromSrcset(srcset: String): Pair<String, Int>? {
        var best: Pair<String, Float>? = null
        var bestW = 0
        Regex("""\s*([^\s,][^\s]*?)(?:\s+(\d+(?:\.\d+)?)([wx]))?\s*(?:,|$)""").findAll(srcset).forEach { m ->
            val u = m.groupValues[1].trim().trimEnd(',')
            if (u.isBlank()) return@forEach
            val n = m.groupValues[2].toFloatOrNull() ?: 1f
            val unit = m.groupValues[3]
            val score = if (unit == "w") n else n * 1000f
            if (best == null || score > best!!.second) {
                best = u to score
                bestW = if (unit == "w") n.toInt() else 0
            }
        }
        return best?.let { it.first to bestW }
    }

    private fun metaContent(html: String, prop: String): String? =
        Html.tags(html, setOf("meta")).firstOrNull { (it.attrs["property"] ?: it.attrs["name"])?.lowercase() == prop }
            ?.attrs?.get("content")?.ifBlank { null }
}
