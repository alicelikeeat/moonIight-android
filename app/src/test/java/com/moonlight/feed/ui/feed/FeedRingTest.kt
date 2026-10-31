package com.moonlight.feed.ui.feed

import com.moonlight.feed.data.FeedScope
import com.moonlight.feed.data.MediaEntry
import com.moonlight.feed.data.MediaKind
import com.moonlight.feed.ui.design.ring
import com.moonlight.feed.ui.design.sheetGroups
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** v4.0 per-item rings: the 4 most frequent actions by kind; everything else grouped behind ···. */
class FeedRingTest {
    private fun entry(kind: MediaKind) = MediaEntry(kind = kind, title = "t", localPath = if (kind == MediaKind.VIDEO || kind == MediaKind.IMAGE) "/f" else null,
        embedUrl = if (kind == MediaKind.EMBED) "https://e" else null)
    private fun spec(kind: MediaKind, h: FeedMenuHandlers = FeedMenuHandlers()) = feedMenuSpec(entry(kind), false, true, 1f, true, true, h, 5, FeedScope.ALL)

    @Test fun ringsPerKind() {
        assertEquals(listOf("收藏", "倍速", "静音", "分享·保存"), spec(MediaKind.VIDEO).ring().map { it.title })
        assertEquals(listOf("收藏", "停留时间", "设为背景", "分享·保存"), spec(MediaKind.IMAGE).ring().map { it.title })
        assertEquals(listOf("收藏", "在浏览器打开", "下一个同源", "分享"), spec(MediaKind.EMBED).ring().map { it.title })
    }

    @Test fun sheetIsGroupedWithDangerLast() {
        val g = spec(MediaKind.VIDEO).sheetGroups()
        assertEquals(listOf("播放", "分享与保存", "整理", "危险"), g.map { it.first })
        assertEquals(listOf("删除"), g.last().second.map { it.title })
        assertTrue(g.first().second.any { it.title == "暂停" })
    }

    @Test fun subChoicesAreShortEnoughForTheArc() {
        val speed = spec(MediaKind.VIDEO).ring().first { it.title == "倍速" }
        assertEquals(listOf("0.5×", "0.75×", "正常", "1.25×", "1.5×", "2×"), speed.children!!.map { it.short })
        val dwell = spec(MediaKind.IMAGE).ring().first { it.title == "停留时间" }
        assertTrue(dwell.children!!.size <= com.moonlight.feed.ui.design.SUB_ARC_MAX)
    }

    @Test fun shareSaveFansOut() {
        var saved = false; var shared = false; var posted = false
        val r = spec(MediaKind.VIDEO, FeedMenuHandlers(onSave = { saved = true }, onShare = { shared = true }, onPostMoment = { posted = true })).ring()
        r.first { it.title == "分享·保存" }.children!!.forEach { it.onClick() }
        assertTrue(saved && shared && posted)
    }
}
