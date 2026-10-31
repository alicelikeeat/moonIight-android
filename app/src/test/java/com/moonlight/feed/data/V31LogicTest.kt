package com.moonlight.feed.data

import com.moonlight.feed.data.social.DemoSocial
import com.moonlight.feed.data.social.SocialJson
import com.moonlight.feed.data.social.SocialState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class V31LogicTest {
    private val eps = 0.01f

    // ---- 1a: focus / reposition
    @Test fun defaultFocusBiasesToUpperThird() {
        val f = MediaFocus.DEFAULT
        assertEquals(0.5f, f.x, 0f); assertEquals(0.3f, f.y, 0f); assertEquals(1f, f.scale, 0f)
        // portrait 1080x1920 photo in a 1080x648 cover: centre crop shows rows 636..1284, default shows 381..1029 (keeps heads)
        val p = FocusCrop.place(1080f, 1920f, 1080f, 648f, f)
        assertEquals(1080f, p.width, eps); assertEquals(1920f, p.height, eps)
        assertEquals(-(1920f - 648f) * 0.3f, p.top, eps)
        val centre = FocusCrop.place(1080f, 1920f, 1080f, 648f, MediaFocus(0.5f, 0.5f))
        assertTrue(p.top > centre.top)
    }
    @Test fun placementAlwaysCoversTheFrame() {
        for (f in listOf(MediaFocus(0f, 0f), MediaFocus(1f, 1f), MediaFocus(0.5f, 0.3f, 2.5f))) {
            val p = FocusCrop.place(1920f, 1080f, 400f, 400f, f)
            assertTrue(p.left <= 0.01f && p.top <= 0.01f && p.right >= 399.99f && p.bottom >= 399.99f)
        }
    }
    @Test fun dragMovesTheRightWayAndClamps() {
        val f = MediaFocus.DEFAULT
        val down = FocusCrop.pan(f, 0f, 200f, 1080f, 1920f, 1080f, 648f)   // finger down → see more of the top
        assertTrue(down.y < f.y)
        val far = FocusCrop.pan(f, 0f, 1e6f, 1080f, 1920f, 1080f, 648f)
        assertEquals(0f, far.y, 0f)
        // no horizontal overflow → x untouched
        assertEquals(f.x, FocusCrop.pan(f, 300f, 0f, 1080f, 1920f, 1080f, 648f).x, 0f)
    }
    @Test fun pinchZoomClamped() {
        assertEquals(2f, FocusCrop.zoom(MediaFocus.DEFAULT, 2f).scale, eps)
        assertEquals(MediaFocus.MAX_SCALE, FocusCrop.zoom(MediaFocus.DEFAULT, 99f).scale, eps)
        assertEquals(1f, FocusCrop.zoom(MediaFocus.DEFAULT, 0.1f).scale, eps)
        val z = FocusCrop.place(1000f, 1000f, 500f, 500f, MediaFocus(0.5f, 0.5f, 2f))
        assertEquals(1000f, z.width, eps); assertEquals(-250f, z.left, eps)
    }
    @Test fun focusEncodesLocaleSafeAndRejectsJunk() {
        val f = MediaFocus(0.25f, 0.125f, 1.5f)
        val old = java.util.Locale.getDefault()
        try { java.util.Locale.setDefault(java.util.Locale.GERMANY); assertEquals("0.2500,0.1250,1.5000", f.encode()) } finally { java.util.Locale.setDefault(old) }
        assertEquals(f, MediaFocus.decode(f.encode()))
        assertNull(MediaFocus.decode("x,y")); assertNull(MediaFocus.decode(null))
        assertEquals(MediaFocus(1f, 0f, 4f), MediaFocus.decode("3,-1,9"))
    }
    @Test fun focusSurvivesBackup() {
        val s0 = DemoSocial.seed(SocialState.fresh(), 1_700_000_000_000L)
        val f = s0.friends.first()
        val s = s0.copy(contacts = s0.contacts.map {
            if (it.id == f.id) it.copy(avatarPath = "/a.jpg", avatarFocus = MediaFocus(0.4f, 0.2f, 1.6f), coverPath = "/c.mp4", coverVideo = true, coverFocus = MediaFocus(0.5f, 0.1f, 1f)) else it
        })
        val back = SocialJson.decode(SocialJson.encode(s))
        assertEquals(s, back)
        assertNull(back.contacts.first { it.id != f.id }.coverFocus)  // untouched contacts keep null (= default)
    }

    // ---- 1b: 视频显示
    private val phone = floatArrayOf(1080f, 2340f)
    @Test fun smartFitsLandscape() {
        assertFalse(VideoFit.fill(VideoDisplay.SMART, 1920f, 1080f, phone[0], phone[1]))
        assertFalse(VideoFit.fill(VideoDisplay.SMART, 1080f, 1080f, phone[0], phone[1]))
    }
    @Test fun smartFillsOnlyNearScreenRatioPortrait() {
        // 9:19.5 video on a 9:19.5 screen → 0% crop → fill
        assertTrue(VideoFit.fill(VideoDisplay.SMART, 1080f, 2340f, phone[0], phone[1]))
        // 9:20 → ~2.5% crop → fill
        assertTrue(VideoFit.fill(VideoDisplay.SMART, 1080f, 2400f, phone[0], phone[1]))
        // 9:16 on 9:19.5 → ~18% crop → fit (would cut heads/edges)
        assertFalse(VideoFit.fill(VideoDisplay.SMART, 1080f, 1920f, phone[0], phone[1]))
        // 3:4 portrait → fit
        assertFalse(VideoFit.fill(VideoDisplay.SMART, 900f, 1200f, phone[0], phone[1]))
        assertEquals(0.18f, VideoFit.cropFraction(1080f, 1920f, 1080f, 2340f), 0.01f)
    }
    @Test fun smartBoundaryAtEightPercent() {
        val h8 = 2340f * 0.92f  // video slightly wider than screen: crop exactly ~8%
        assertTrue(VideoFit.cropFraction(1080f, h8, 1080f, 2340f) <= VideoFit.SMART_MAX_CROP + 1e-4f)
        assertFalse(VideoFit.fill(VideoDisplay.SMART, 1080f, 2340f * 0.88f, phone[0], phone[1]))
    }
    @Test fun unknownSizesNeverCrop() {
        assertFalse(VideoFit.fill(VideoDisplay.SMART, 0f, 0f, phone[0], phone[1]))
        assertFalse(VideoFit.fill(VideoDisplay.SMART, 1080f, 1920f, 0f, 0f))
    }
    @Test fun modesAndPerItemOverride() {
        assertFalse(VideoFit.fill(VideoDisplay.FIT, 1080f, 2340f, phone[0], phone[1]))
        assertTrue(VideoFit.fill(VideoDisplay.FILL, 1920f, 1080f, phone[0], phone[1]))
        assertTrue(VideoFit.fill(VideoDisplay.FIT, 1920f, 1080f, phone[0], phone[1], override = true))
        assertFalse(VideoFit.fill(VideoDisplay.FILL, 1920f, 1080f, phone[0], phone[1], override = false))
        assertEquals(VideoDisplay.SMART, VideoFit.of(null)); assertEquals(VideoDisplay.SMART, VideoFit.of("bogus"))
        assertEquals(listOf("智能", "完整显示", "铺满"), VideoDisplay.entries.map { it.label })
    }

    // ---- 3: 菜单背景
    @Test fun menuBackdropLevels() {
        assertEquals(listOf("透明", "轻度模糊", "模糊"), MenuBackdrop.entries.map { it.label })
        assertEquals(0f, MenuBackdrop.CLEAR.blurDp, 0f); assertEquals(0f, MenuBackdrop.CLEAR.dim, 0f)
        assertTrue(MenuBackdrop.CLEAR.labelShadow && MenuBackdrop.LIGHT.labelShadow && !MenuBackdrop.BLUR.labelShadow)
        assertTrue(MenuBackdrop.LIGHT.blurDp in 1f..MenuBackdrop.BLUR.blurDp - 1f)
        assertEquals(24f, MenuBackdrop.BLUR.blurDp, 0f); assertEquals(1f, MenuBackdrop.BLUR.dim, 0f)
        assertEquals(MenuBackdrop.BLUR, MenuBackdrop.of(null)); assertEquals(MenuBackdrop.CLEAR, MenuBackdrop.of("CLEAR"))
    }
    @Test fun settingsDefaultsUnchangedBehaviour() {
        val s = SettingsRepository.Snapshot()
        assertEquals("SMART", s.videoDisplay); assertEquals("BLUR", s.menuBackdrop)
    }
}
