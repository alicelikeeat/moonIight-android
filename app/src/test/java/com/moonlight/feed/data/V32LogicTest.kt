package com.moonlight.feed.data

import com.moonlight.feed.data.social.DemoSocial
import com.moonlight.feed.data.social.SavedMedia
import com.moonlight.feed.data.social.SocialJson
import com.moonlight.feed.data.social.SocialState
import com.moonlight.feed.ui.settings.IconShortcut
import com.moonlight.feed.ui.social.ProfileMediaActions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class V32LogicTest {
    @get:Rule val tmp = TemporaryFolder()

    // ---------------------------------------------------------------- 首页背景: photo & video kept independently
    private val photo = "/data/x/avatars/p1.jpg"
    private val video = "/data/x/avatars/v1.mp4"

    @Test fun switchingAwayAndBackKeepsThePhoto() {
        var p = HomeBgPrefs().picked(false, photo)
        assertEquals(HomeBgKind.PHOTO, p.kind)
        for (k in listOf(HomeBgKind.THEME, HomeBgKind.BLACK)) {
            p = p.select(k).prefs
            assertEquals(photo, p.photo?.path)                 // never forgotten by switching
        }
        val back = p.select(HomeBgKind.PHOTO)
        assertFalse("saved photo returns instantly", back.needsPick)
        assertEquals(HomeBgKind.PHOTO, back.prefs.kind)
        assertEquals(photo, back.prefs.spec { true }.path)
    }
    @Test fun photoAndVideoAreSeparateSlots() {
        val p = HomeBgPrefs().picked(false, photo).withFocus(false, MediaFocus(0.5f, 0.2f, 1.4f)).withDim(false)
            .picked(true, video)
        assertEquals(HomeBgKind.VIDEO, p.kind)
        assertEquals(photo, p.photo?.path); assertEquals(video, p.video?.path)
        assertEquals(MediaFocus(0.5f, 0.2f, 1.4f), p.photo?.focus)
        assertFalse(p.photo!!.dim)                         // 模糊 remembered per slot
        val back = p.select(HomeBgKind.PHOTO).prefs
        assertEquals(HomeBgSpec(HomeBgKind.PHOTO, photo, false, false, MediaFocus(0.5f, 0.2f, 1.4f)), back.spec { true })
        // replacing the photo leaves the video alone and resets only the photo's position
        val p2 = back.picked(false, "/data/x/avatars/p2.jpg")
        assertEquals(video, p2.video?.path); assertNull(p2.photo?.focus)
    }
    @Test fun emptySlotOrMissingFileNeedsPick() {
        assertTrue(HomeBgPrefs().select(HomeBgKind.VIDEO).needsPick)
        val p = HomeBgPrefs().picked(false, photo).select(HomeBgKind.THEME).prefs
        assertTrue(p.select(HomeBgKind.PHOTO) { false }.needsPick)
        assertEquals(HomeBgKind.THEME, p.copy(kind = HomeBgKind.PHOTO).spec { false }.kind)   // gone → theme, slot kept
    }
    @Test fun deleteIsExplicitAndOnlyThen() {
        val p = HomeBgPrefs().picked(false, photo).picked(true, video).select(HomeBgKind.PHOTO).prefs
        val (afterVideo, f1) = p.delete(true)
        assertEquals(video, f1); assertNull(afterVideo.video); assertEquals(HomeBgKind.PHOTO, afterVideo.kind)
        assertTrue(afterVideo.select(HomeBgKind.VIDEO).needsPick)
        val (afterPhoto, f2) = afterVideo.delete(false)
        assertEquals(photo, f2); assertEquals(HomeBgKind.THEME, afterPhoto.kind)   // was active → 跟随主题
        // the same file in both slots is never removed while still used
        val shared = HomeBgPrefs(photo = BgSlot("/a/same"), video = BgSlot("/a/same"))
        assertNull(shared.delete(true).second)
    }
    @Test fun legacySinglePathMigratesByType() {
        val v = HomeBgPrefs.read("VIDEO", null, null, "/f/avatars/old.mp4", null, null, true, null, null, false)
        assertEquals("/f/avatars/old.mp4", v.video?.path); assertNull(v.photo)
        val ph = HomeBgPrefs.read("THEME", null, null, "/f/avatars/old.jpg", null, null, false, null, null, true)
        assertEquals("/f/avatars/old.jpg", ph.photo?.path); assertFalse(ph.photo!!.dim); assertTrue(ph.allPages)
        // explicit slots win over the legacy value
        val both = HomeBgPrefs.read("PHOTO", "/n/p.jpg", null, "/f/old.jpg", null, null, true, "0.5,0.1,1", null, false)
        assertEquals("/n/p.jpg", both.photo?.path); assertEquals(MediaFocus(0.5f, 0.1f, 1f), both.photo?.focus)
    }
    @Test fun storedCopyFilesSurviveSwitchingAndDeleteRemovesThem() {
        val root = tmp.newFolder("avatars")
        val pf = java.io.File(root, "p.jpg").apply { writeText("x") }
        val vf = java.io.File(root, "v.mp4").apply { writeText("y") }
        var p = HomeBgPrefs().picked(false, pf.path).picked(true, vf.path)
        repeat(3) { p = p.select(HomeBgKind.THEME).prefs.select(HomeBgKind.PHOTO).prefs.select(HomeBgKind.VIDEO).prefs }
        assertTrue(pf.exists() && vf.exists())              // nothing deletes inactive copies
        val (np, file) = p.delete(false)
        assertTrue(StoredCopies.delete(file, root))
        assertFalse(pf.exists()); assertTrue(vf.exists())
        assertEquals(vf.path, np.video?.path)
    }
    @Test fun storedCopiesNeverDeletesOutsideAppFolder() {
        val root = tmp.newFolder("avatars2")
        val outside = tmp.newFile("gallery.jpg")
        assertFalse(StoredCopies.delete(outside.path, root)); assertTrue(outside.exists())
        assertFalse(StoredCopies.delete(java.io.File(root, "../gallery.jpg").path, root)); assertTrue(outside.exists())
        assertFalse(StoredCopies.delete("art:3", root)); assertFalse(StoredCopies.delete(null, root))
    }

    // ---------------------------------------------------------------- avatar / cover: kept on 恢复默认 / replace
    private val me = DemoSocial.seed(SocialState.fresh(), 1_760_000_000_000L).self

    @Test fun resetKeepsAvatarAndRestoreBringsItBack() {
        val withPhoto = ProfileMediaActions.withFocus(ProfileMediaActions.withNewMedia(me, true, "/a/me.jpg", false), true, MediaFocus(0.4f, 0.2f, 1.3f))
        val letter = ProfileMediaActions.reset(withPhoto, true)            // 使用字母头像
        assertNull(letter.avatarPath)
        assertEquals(SavedMedia("/a/me.jpg", false, MediaFocus(0.4f, 0.2f, 1.3f)), letter.prevAvatar)
        val back = ProfileMediaActions.restore(letter, true)
        assertEquals("/a/me.jpg", back.avatarPath); assertEquals(MediaFocus(0.4f, 0.2f, 1.3f), back.avatarFocus)
    }
    @Test fun replacingCoverKeepsPreviousAndSwapWorksBothWays() {
        val a = ProfileMediaActions.withNewMedia(me, false, "/a/c1.mp4", true)
        val b = ProfileMediaActions.withNewMedia(a, false, "/a/c2.jpg", false)
        assertEquals("/a/c2.jpg", b.coverPath); assertEquals(SavedMedia("/a/c1.mp4", true, null), b.prevCover)
        val sw = ProfileMediaActions.restore(b, false)
        assertEquals("/a/c1.mp4", sw.coverPath); assertTrue(sw.coverVideo); assertEquals("/a/c2.jpg", sw.prevCover?.path)
    }
    @Test fun deletePrevOnlyRemovesUnusedFiles() {
        val b = ProfileMediaActions.reset(ProfileMediaActions.withNewMedia(me, false, "/a/c1.jpg", false), false)
        val (n, f) = ProfileMediaActions.deletePrev(b, false)
        assertNull(n.prevCover); assertNull(n.coverPath); assertEquals("/a/c1.jpg", f)
        assertTrue(ProfileMediaActions.usedBy(listOf(me.copy(avatarPath = "/a/c1.jpg")), "/a/c1.jpg"))
        assertFalse(ProfileMediaActions.usedBy(listOf(n), "/a/c1.jpg"))
    }
    @Test fun savedMediaSurvivesBackup() {
        val s0 = DemoSocial.seed(SocialState.fresh(), 1_760_000_000_000L)
        val s = s0.copy(contacts = s0.contacts.map {
            if (it.isSelf) it.copy(prevAvatar = SavedMedia("/a/old.mp4", true, MediaFocus(0.5f, 0.3f, 2f)), prevCover = SavedMedia("/a/c.jpg", false)) else it
        })
        assertEquals(s, SocialJson.decode(SocialJson.encode(s)))
    }

    // ---------------------------------------------------------------- 应用图标
    @Test fun exactlyOneLauncherAlias() {
        for (t in AppIcon.entries) {
            val st = AppIcon.states(t)
            assertEquals(1, st.values.count { it }); assertTrue(st[t]!!)
        }
        assertEquals(AppIcon.GIRL, AppIcon.DEFAULT); assertEquals(AppIcon.GIRL, AppIcon.of("bogus"))
        assertEquals(listOf(".IconGirl", ".IconClassic"), AppIcon.entries.map { it.alias })
    }
    @Test fun photoIconVisibleWindowMatchesEditorFrame() {
        val size = 432f
        val win = size * IconShortcut.VISIBLE
        val m = (size - win) / 2f
        val p = IconShortcut.layerPlacement(1000f, 1000f, size, MediaFocus(0.5f, 0.5f, 1f))
        assertEquals(m, p.left, 0.5f); assertEquals(m, p.top, 0.5f); assertEquals(win, p.width, 0.5f)
        // portrait photo, default-ish focus: covers the visible window completely
        val q = IconShortcut.layerPlacement(900f, 1600f, size, MediaFocus(0.5f, 0.4f, 1f))
        assertTrue(q.left <= m + 0.5f && q.top <= m + 0.5f && q.right >= m + win - 0.5f && q.bottom >= m + win - 0.5f)
    }
    @Test fun newSettingsDefaults() {
        val s = SettingsRepository.Snapshot()
        assertEquals("TAP", s.menuSelect)
        assertEquals(HomeBgKind.THEME, s.homeBgPrefs().kind); assertNull(s.homeBgPrefs().photo)
    }
}
