package com.moonlight.feed.data

import java.util.UUID

enum class MediaKind { VIDEO, IMAGE, BILI, EMBED }

data class MediaEntry(
    val id: String = UUID.randomUUID().toString(),
    val kind: MediaKind,
    val title: String,
    val localPath: String? = null,
    val remoteUrl: String? = null,
    val coverUrl: String? = null,
    val biliBvid: String? = null,
    /** Platform id: bili, youtube, vimeo, dailymotion, archive, nasa, commons, peertube, pexels, pixabay, twitch, web, local, url */
    val platform: String? = null,
    /** Official embed player URL when kind is EMBED/BILI */
    val embedUrl: String? = null,
    val author: String = "",
    val favorite: Boolean = false,
    val hidden: Boolean = false,
    val source: String = "local",
    val createdAt: Long = System.currentTimeMillis(),
) {
    fun isWebEmbed(): Boolean = kind == MediaKind.BILI || kind == MediaKind.EMBED

    fun resolvedPlatform(): String = when {
        !platform.isNullOrBlank() -> platform
        kind == MediaKind.BILI || !biliBvid.isNullOrBlank() -> PlatformId.BILI
        kind == MediaKind.VIDEO && source == "local" -> PlatformId.LOCAL
        kind == MediaKind.IMAGE && source == "local" -> PlatformId.LOCAL
        else -> source.ifBlank { PlatformId.WEB }
    }

    fun resolvedEmbedUrl(mute: Boolean = true): String? {
        embedUrl?.let { return it }
        val bv = biliBvid
        if (!bv.isNullOrBlank()) {
            val muteQ = if (mute) "1" else "0"
            return "https://player.bilibili.com/player.html?bvid=$bv&page=1&high_quality=1&autoplay=1&danmaku=0&mute=$muteQ"
        }
        return null
    }

    fun platformLabel(): String = PlatformId.label(resolvedPlatform())

    fun isDownloadable(): Boolean = when (resolvedPlatform()) {
        PlatformId.ARCHIVE, PlatformId.NASA, PlatformId.COMMONS,
        PlatformId.PEXELS, PlatformId.PIXABAY, PlatformId.PEERTUBE,
        PlatformId.LOCAL, PlatformId.URL ->
            !remoteUrl.isNullOrBlank() || !localPath.isNullOrBlank()
        else -> kind == MediaKind.VIDEO || kind == MediaKind.IMAGE
    }
}

object PlatformId {
    const val BILI = "bili"
    const val YOUTUBE = "youtube"
    const val VIMEO = "vimeo"
    const val DAILYMOTION = "dailymotion"
    const val ARCHIVE = "archive"
    const val NASA = "nasa"
    const val COMMONS = "commons"
    const val PEERTUBE = "peertube"
    const val PEXELS = "pexels"
    const val PIXABAY = "pixabay"
    const val TWITCH = "twitch"
    const val WEB = "web"
    const val LOCAL = "local"
    const val URL = "url"

    val ALL_TOGGLEABLE = listOf(
        BILI, YOUTUBE, VIMEO, DAILYMOTION, ARCHIVE, NASA, COMMONS, PEERTUBE, PEXELS, PIXABAY, TWITCH, WEB,
    )

    val DEFAULT_ENABLED = setOf(
        BILI, YOUTUBE, VIMEO, DAILYMOTION, ARCHIVE, NASA, COMMONS, PEERTUBE, WEB,
    )

    fun label(id: String): String = when (id) {
        BILI -> "哔哩"
        YOUTUBE -> "YouTube"
        VIMEO -> "Vimeo"
        DAILYMOTION -> "Dailymotion"
        ARCHIVE -> "Archive"
        NASA -> "NASA"
        COMMONS -> "Commons"
        PEERTUBE -> "PeerTube"
        PEXELS -> "Pexels"
        PIXABAY -> "Pixabay"
        TWITCH -> "Twitch"
        WEB -> "网页"
        LOCAL -> "本地"
        URL -> "直链"
        else -> id
    }
}

data class DownloadJob(
    val id: String,
    val url: String,
    val title: String,
    val progress: Float = 0f,
    val error: String? = null,
    val done: Boolean = false,
)

data class BiliCard(
    val bvid: String,
    val title: String,
    val cover: String,
    val author: String,
    val play: Long = 0,
    val duration: Int = 0,
)

data class DiscoverCard(
    val entry: MediaEntry,
    val badge: String = entry.platformLabel(),
)
