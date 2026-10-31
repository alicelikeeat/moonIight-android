package com.moonlight.feed.data.download

import com.moonlight.feed.data.MediaEntry
import com.moonlight.feed.data.MediaKind
import com.moonlight.feed.data.PlatformId
import com.moonlight.feed.data.isLocalFile
import com.moonlight.feed.data.social.Attachment
import com.moonlight.feed.data.social.AttachmentKind

/** What a 主动下载 needs: an item id, a direct file URL and display metadata. */
data class DownloadSource(
    val id: String, val url: String, val title: String, val kind: MediaKind,
    val coverUrl: String? = null, val platform: String? = null, val author: String = "",
)

/** [source] set → 下载 is offered; otherwise [reason] is the short why-not shown on the disabled row. */
data class DownloadOffer(val source: DownloadSource?, val reason: String?) {
    val ok get() = source != null
}

/**
 * Only sources that hand us a plain file URL are downloadable (Archive / NASA / Commons / PeerTube web-video files /
 * Pexels / Pixabay / pasted direct links / files found on user web sources). Nothing is ripped from players:
 * 哔哩 (playurl / WBI), YouTube, Vimeo, Dailymotion, Twitch and any HLS / DASH manifest are refused.
 */
object DownloadPolicy {
    /** [MediaEntry.source] of a finished 主动下载 → 库 › 我的下载 (and 首页 › 本地, as it is a local file). */
    const val SOURCE = "download"
    const val CATEGORY = "我的下载"

    val DIRECT = setOf(PlatformId.ARCHIVE, PlatformId.NASA, PlatformId.COMMONS, PlatformId.PEERTUBE, PlatformId.PEXELS, PlatformId.PIXABAY, PlatformId.URL, PlatformId.WEB)
    private val PLAYER_ONLY = setOf(PlatformId.BILI, PlatformId.YOUTUBE, PlatformId.VIMEO, PlatformId.DAILYMOTION, PlatformId.TWITCH)
    private val STREAM_HINTS = listOf(".m3u8", ".mpd", "/playurl", "bilivideo.", "googlevideo.", ".ism/")

    fun isManualDownload(e: MediaEntry) = e.source == SOURCE
    fun entryId(sourceId: String) = "dl-$sourceId"

    fun check(e: MediaEntry): DownloadOffer {
        val p = e.resolvedPlatform()
        fun no(r: String) = DownloadOffer(null, r)
        if (isManualDownload(e)) return no("已在「我的下载」")
        if (e.isLocalFile()) return no("已在本机")
        if (e.kind == MediaKind.BILI || p == PlatformId.BILI) return no("哔哩哔哩只提供网页播放器，不能下载")
        if (p in PLAYER_ONLY) return no("${PlatformId.label(p)} 只提供网页播放器，不能下载")
        if (e.kind == MediaKind.EMBED) return no("网页内容，没有文件直链")
        val url = e.remoteUrl?.trim()
        return when {
            url.isNullOrBlank() || !(url.startsWith("https://") || url.startsWith("http://")) -> no("没有可下载的文件地址")
            STREAM_HINTS.any { url.contains(it, ignoreCase = true) } -> no("流媒体，不支持下载")
            p !in DIRECT -> no("此来源未提供文件直链")
            else -> DownloadOffer(DownloadSource(e.id, url, e.title.ifBlank { url.substringAfterLast('/').substringBefore('?') }, e.kind,
                e.coverUrl ?: if (e.kind == MediaKind.IMAGE) url else null, e.platform, e.author), null)
        }
    }

    /** 朋友圈 video / image: its library entry when it has one, else the attachment's own uploaded URL. */
    fun check(a: Attachment, lib: MediaEntry?): DownloadOffer {
        if (lib != null) return check(lib)
        if (!a.localPath.isNullOrBlank()) return DownloadOffer(null, "已在本机")
        if (a.kind == AttachmentKind.EMBED) return DownloadOffer(null, "网页内容，没有文件直链")
        val url = a.remoteUrl ?: return DownloadOffer(null, "没有可下载的文件地址")
        val e = MediaEntry(id = "att:" + a.id, kind = if (a.kind == AttachmentKind.VIDEO) MediaKind.VIDEO else MediaKind.IMAGE,
            title = a.title, remoteUrl = url, coverUrl = a.coverUrl, platform = PlatformId.URL, source = "moment")
        return check(e)
    }
}
