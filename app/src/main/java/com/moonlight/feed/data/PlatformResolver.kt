package com.moonlight.feed.data

/**
 * Detect platform from pasted URL / text and build MediaEntry (embed or downloadable).
 * No stream ripping — only official embeds or public direct files.
 */
object PlatformResolver {
    private val unsupportedHosts = listOf(
        "douyin.com", "iesdouyin.com", "tiktok.com", "vm.tiktok.com",
        "kuaishou.com", "kwai.com", "xiaohongshu.com", "xhslink.com",
        "ixigua.com", "youku.com", "iqiyi.com", "v.qq.com", "qq.com/x/cover",
    )

    data class ResolveResult(
        val entry: MediaEntry? = null,
        val error: String? = null,
        val unsupported: Boolean = false,
    )

    fun detectPlatform(text: String): String? {
        val t = text.trim()
        if (t.isBlank()) return null
        if (RemoteApis.extractBvid(t) != null) return PlatformId.BILI
        val lower = t.lowercase()
        return when {
            unsupportedHosts.any { lower.contains(it) } -> null
            "youtube.com" in lower || "youtu.be" in lower || "youtube-nocookie.com" in lower -> PlatformId.YOUTUBE
            "vimeo.com" in lower -> PlatformId.VIMEO
            "dailymotion.com" in lower || "dai.ly" in lower -> PlatformId.DAILYMOTION
            "archive.org" in lower -> PlatformId.ARCHIVE
            "images.nasa.gov" in lower || "images-assets.nasa.gov" in lower || "nasa.gov" in lower -> PlatformId.NASA
            "wikimedia.org" in lower || "wikipedia.org" in lower -> PlatformId.COMMONS
            "twitch.tv" in lower || "clips.twitch.tv" in lower -> PlatformId.TWITCH
            "pexels.com" in lower -> PlatformId.PEXELS
            "pixabay.com" in lower -> PlatformId.PIXABAY
            Regex("""https?://[^/\s]+/videos/(watch|embed)/""", RegexOption.IGNORE_CASE).containsMatchIn(t) -> PlatformId.PEERTUBE
            t.startsWith("http://") || t.startsWith("https://") -> PlatformId.WEB
            else -> null
        }
    }

    fun isUnsupported(text: String): String? {
        val lower = text.lowercase()
        return when {
            listOf("douyin", "tiktok").any { it in lower } -> "抖音/TikTok 不支持（无公开嵌入）"
            "kuaishou" in lower || "kwai.com" in lower -> "快手不支持（无公开嵌入）"
            "xiaohongshu" in lower || "xhslink" in lower -> "小红书不支持（无公开嵌入）"
            "ixigua.com" in lower -> "西瓜视频无稳定公开嵌入"
            "youku.com" in lower -> "优酷无稳定公开嵌入"
            "iqiyi.com" in lower -> "爱奇艺无稳定公开嵌入"
            "v.qq.com" in lower -> "腾讯视频无稳定公开嵌入"
            else -> null
        }
    }

    fun extractYoutubeId(text: String): String? {
        Regex("""(?:youtube\.com/watch\?v=|youtu\.be/|youtube\.com/shorts/|youtube\.com/embed/|youtube-nocookie\.com/embed/)([A-Za-z0-9_-]{6,})""")
            .find(text)?.groupValues?.getOrNull(1)?.let { return it }
        Regex("""[?&]v=([A-Za-z0-9_-]{6,})""").find(text)?.groupValues?.getOrNull(1)?.let { return it }
        return null
    }

    fun extractVimeoId(text: String): String? =
        Regex("""vimeo\.com/(?:video/)?(\d+)""").find(text)?.groupValues?.getOrNull(1)

    fun extractDailymotionId(text: String): String? =
        Regex("""(?:dailymotion\.com/video/|dai\.ly/)([A-Za-z0-9]+)""")
            .find(text)?.groupValues?.getOrNull(1)

    fun extractTwitchClip(text: String): String? =
        Regex("""(?:clips\.twitch\.tv/|twitch\.tv/\w+/clip/)([A-Za-z0-9_-]+)""")
            .find(text)?.groupValues?.getOrNull(1)

    fun youtubeEmbed(id: String, mute: Boolean = true): String {
        val m = if (mute) "1" else "0"
        // Prefer www.youtube.com/embed (nocookie still hits Error 153 without Referer)
        return "https://www.youtube.com/embed/$id?autoplay=1&playsinline=1&mute=$m&rel=0&modestbranding=1&enablejsapi=1&origin=https%3A%2F%2Fcom.moonlight.feed"
    }

    fun vimeoEmbed(id: String, mute: Boolean = true): String {
        val m = if (mute) "1" else "0"
        return "https://player.vimeo.com/video/$id?autoplay=1&muted=$m"
    }

    fun dailymotionEmbed(id: String, mute: Boolean = true): String {
        val m = if (mute) "true" else "false"
        return "https://www.dailymotion.com/embed/video/$id?autoplay=1&mute=$m"
    }

    fun twitchClipEmbed(slug: String): String =
        "https://clips.twitch.tv/embed?clip=$slug&parent=com.moonlight.feed&parent=www.com.moonlight.feed&autoplay=true&muted=true"

    fun biliEmbed(bvid: String, mute: Boolean = true): String {
        val m = if (mute) "1" else "0"
        return "https://player.bilibili.com/player.html?bvid=$bvid&page=1&high_quality=1&autoplay=1&danmaku=0&mute=$m"
    }

    /** Sync resolve for simple IDs; network enrichment done by RemoteApis.resolveUrl */
    fun resolveLocal(text: String): ResolveResult {
        val t = text.trim()
        isUnsupported(t)?.let { return ResolveResult(error = it, unsupported = true) }
        RemoteApis.extractBvid(t)?.let { bv ->
            return ResolveResult(
                MediaEntry(
                    kind = MediaKind.EMBED,
                    title = bv,
                    biliBvid = bv,
                    platform = PlatformId.BILI,
                    embedUrl = biliEmbed(bv),
                    author = "哔哩哔哩",
                    source = "bili",
                )
            )
        }
        extractYoutubeId(t)?.let { id ->
            return ResolveResult(
                MediaEntry(
                    kind = MediaKind.EMBED,
                    title = "YouTube $id",
                    remoteUrl = "https://www.youtube.com/watch?v=$id",
                    platform = PlatformId.YOUTUBE,
                    embedUrl = youtubeEmbed(id),
                    author = "YouTube",
                    source = "youtube",
                )
            )
        }
        extractVimeoId(t)?.let { id ->
            return ResolveResult(
                MediaEntry(
                    kind = MediaKind.EMBED,
                    title = "Vimeo $id",
                    remoteUrl = "https://vimeo.com/$id",
                    platform = PlatformId.VIMEO,
                    embedUrl = vimeoEmbed(id),
                    author = "Vimeo",
                    source = "vimeo",
                )
            )
        }
        extractDailymotionId(t)?.let { id ->
            return ResolveResult(
                MediaEntry(
                    kind = MediaKind.EMBED,
                    title = "Dailymotion $id",
                    remoteUrl = "https://www.dailymotion.com/video/$id",
                    platform = PlatformId.DAILYMOTION,
                    embedUrl = dailymotionEmbed(id),
                    author = "Dailymotion",
                    source = "dailymotion",
                )
            )
        }
        extractTwitchClip(t)?.let { slug ->
            return ResolveResult(
                MediaEntry(
                    kind = MediaKind.EMBED,
                    title = "Twitch $slug",
                    remoteUrl = "https://clips.twitch.tv/$slug",
                    platform = PlatformId.TWITCH,
                    embedUrl = twitchClipEmbed(slug),
                    author = "Twitch",
                    source = "twitch",
                )
            )
        }
        if (t.startsWith("http://") || t.startsWith("https://")) {
            val platform = detectPlatform(t) ?: PlatformId.WEB
            // Direct media file?
            val path = t.substringBefore('?').lowercase()
            val directVideo = listOf(".mp4", ".webm", ".ogv", ".mkv", ".mov").any { path.endsWith(it) }
            val directImage = listOf(".jpg", ".jpeg", ".png", ".webp", ".gif").any { path.endsWith(it) }
            if (directVideo || directImage) {
                return ResolveResult(
                    MediaEntry(
                        kind = if (directVideo) MediaKind.VIDEO else MediaKind.IMAGE,
                        title = t.substringAfterLast('/').substringBefore('?').ifBlank { "直链" },
                        remoteUrl = t,
                        coverUrl = if (directImage) t else null,
                        platform = if (platform == PlatformId.WEB) PlatformId.URL else platform,
                        author = PlatformId.label(platform),
                        source = platform,
                    )
                )
            }
            return ResolveResult(
                MediaEntry(
                    kind = MediaKind.EMBED,
                    title = t.hostish(),
                    remoteUrl = t,
                    platform = platform,
                    embedUrl = t, // generic web page in WebView
                    author = PlatformId.label(platform),
                    source = platform,
                )
            )
        }
        return ResolveResult(error = "无法识别链接")
    }

    private fun String.hostish(): String = try {
        java.net.URI(this).host?.removePrefix("www.") ?: take(40)
    } catch (_: Throwable) { take(40) }
}
