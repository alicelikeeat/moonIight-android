package com.moonlight.feed.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedScopeTest {
    private val localVideo = MediaEntry(kind = MediaKind.VIDEO, title = "cap", localPath = "/data/media/cap.mp4")
    private val localImage = MediaEntry(kind = MediaKind.IMAGE, title = "imp", localPath = "/data/media/imp.jpg")
    private val downloaded = MediaEntry(kind = MediaKind.VIDEO, title = "dl", localPath = "/data/media/dl.mp4", remoteUrl = "https://x/y.mp4", platform = "pexels")
    private val embed = MediaEntry(kind = MediaKind.EMBED, title = "yt", embedUrl = "https://www.youtube.com/embed/x", platform = "youtube")
    private val bili = MediaEntry(kind = MediaKind.BILI, title = "bv", biliBvid = "BV1xx")
    private val stream = MediaEntry(kind = MediaKind.VIDEO, title = "remote", remoteUrl = "https://x/y.mp4")
    private val all = listOf(localVideo, localImage, downloaded, embed, bili, stream)

    @Test fun localIsOnDeviceFilesOnly() {
        assertEquals(listOf(localVideo, localImage, downloaded), all.filter { FeedScope.LOCAL.matches(it) })
        assertTrue(downloaded.isLocalFile())
        assertFalse(stream.isLocalFile())
        assertFalse(embed.copy(localPath = "/weird").isLocalFile()) // embeds always need the web player
    }

    @Test fun onlineIsEverythingElse() {
        assertEquals(listOf(embed, bili, stream), all.filter { FeedScope.ONLINE.matches(it) })
    }

    @Test fun allAndPartition() {
        assertEquals(all, all.filter { FeedScope.ALL.matches(it) })
        all.forEach { e -> assertTrue(FeedScope.LOCAL.matches(e) xor FeedScope.ONLINE.matches(e)) }
    }

    @Test fun persistedNames() {
        assertEquals(FeedScope.LOCAL, FeedScope.of("LOCAL"))
        assertEquals(FeedScope.ALL, FeedScope.of(null))
        assertEquals(FeedScope.ALL, FeedScope.of("garbage"))
        assertEquals(listOf("全部", "本地", "在线"), FeedScope.entries.map { it.label })
    }
}
