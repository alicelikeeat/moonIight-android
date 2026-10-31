package com.moonlight.feed.ui.social

import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Delete
import android.content.Context
import com.moonlight.feed.BiliPlayerActivity
import com.moonlight.feed.data.MediaFocus
import com.moonlight.feed.data.social.Contact
import com.moonlight.feed.data.social.SavedMedia
import com.moonlight.feed.ui.design.FocusEditing
import com.moonlight.feed.ui.design.FocusRequest
import com.moonlight.feed.ui.design.MoonToaster

/** Shared avatar / cover actions for 我, 朋友圈 header and profile pages. */
object ProfileMediaActions {
    fun path(c: Contact, avatar: Boolean) = if (avatar) c.avatarPath else c.coverPath
    fun video(c: Contact, avatar: Boolean) = if (avatar) c.avatarVideo else c.coverVideo
    fun focus(c: Contact, avatar: Boolean) = (if (avatar) c.avatarFocus else c.coverFocus) ?: MediaFocus.DEFAULT

    /** Long-press: straight to the full-screen player. Returns false when there's nothing custom to play. */
    fun play(ctx: Context, c: Contact, avatar: Boolean): Boolean {
        val p = path(c, avatar)?.takeIf { !it.startsWith("art:") && java.io.File(it).exists() } ?: return false
        BiliPlayerActivity.playLocal(ctx, p, video(c, avatar), c.name + if (avatar) " · 头像" else " · 封面")
        return true
    }

    /** 调整位置 → editor; [save] receives the new focus (apply it to the *current* contact). */
    fun adjust(c: Contact, avatar: Boolean, frameHeightDp: Int, save: (MediaFocus) -> Unit) {
        val p = path(c, avatar) ?: return
        FocusEditing.open(FocusRequest(p, video(c, avatar), frameHeightDp, if (avatar) "调整头像位置" else "调整封面位置", focus(c, avatar)) { f ->
            save(f)
            MoonToaster.show("已保存位置")
        })
    }

    fun current(c: Contact, avatar: Boolean): SavedMedia? = path(c, avatar)?.let { SavedMedia(it, video(c, avatar), if (avatar) c.avatarFocus else c.coverFocus) }
    fun prev(c: Contact, avatar: Boolean): SavedMedia? = if (avatar) c.prevAvatar else c.prevCover

    private fun set(c: Contact, avatar: Boolean, cur: SavedMedia?, prev: SavedMedia?): Contact =
        if (avatar) c.copy(avatarPath = cur?.path, avatarVideo = cur?.video ?: false, avatarFocus = cur?.focus, prevAvatar = prev)
        else c.copy(coverPath = cur?.path, coverVideo = cur?.video ?: false, coverFocus = cur?.focus, prevCover = prev)

    /** New media picked: store it with a fresh (default) focus; the one it replaces is kept as 上一个. */
    fun withNewMedia(c: Contact, avatar: Boolean, path: String, video: Boolean): Contact =
        set(c, avatar, SavedMedia(path, video, null), current(c, avatar) ?: prev(c, avatar))

    fun withFocus(c: Contact, avatar: Boolean, f: MediaFocus): Contact =
        if (avatar) c.copy(avatarFocus = f) else c.copy(coverFocus = f)

    /** 恢复默认 / 使用字母头像: back to the default look, but the photo/video is kept (用回 brings it back). */
    fun reset(c: Contact, avatar: Boolean): Contact = set(c, avatar, null, current(c, avatar) ?: prev(c, avatar))

    /** 用回上一个: swap current and saved. */
    fun restore(c: Contact, avatar: Boolean): Contact = prev(c, avatar)?.let { set(c, avatar, it, current(c, avatar)) } ?: c

    /** 删除已保存的: forget the saved copy. Returns the contact and the file to remove (null if still in use). */
    fun deletePrev(c: Contact, avatar: Boolean, inUse: (String) -> Boolean = { false }): Pair<Contact, String?> {
        val p = prev(c, avatar) ?: return c to null
        val n = set(c, avatar, current(c, avatar), null)
        return n to p.path.takeIf { it != path(n, avatar) && !inUse(it) }
    }

    /** True if any contact still shows or keeps [p]. */
    fun usedBy(contacts: List<Contact>, p: String) = contacts.any { c ->
        c.avatarPath == p || c.coverPath == p || c.prevAvatar?.path == p || c.prevCover?.path == p
    }
}

/**
 * Menu rows for the kept 上一个 photo/video: 用回 (instant) and 删除 (confirm → removes the stored copy).
 * [fresh] returns the current contact at click time; [save] persists it.
 */
fun savedMediaActions(
    ctx: Context, overlay: com.moonlight.feed.ui.design.MoonOverlayState, c: Contact, avatar: Boolean,
    fresh: () -> Contact?, all: () -> List<Contact>, save: (Contact) -> Unit,
): List<com.moonlight.feed.ui.design.MenuAction> {
    val p = ProfileMediaActions.prev(c, avatar) ?: return emptyList()
    val kind = if (p.video) "视频" else "照片"
    return listOf(
        com.moonlight.feed.ui.design.MenuAction("用回上一个", androidx.compose.material.icons.Icons.Outlined.History, subtitle = "已保存的$kind · 不用重新选择") {
            fresh()?.let { save(ProfileMediaActions.restore(it, avatar)) }
        },
        com.moonlight.feed.ui.design.MenuAction("删除已保存的$kind", androidx.compose.material.icons.Icons.Outlined.Delete, destructive = true) {
            overlay.confirm("删除已保存的$kind？", "会删除 App 内的副本（相册原文件不受影响）。之后需要重新选择。", listOf(
                com.moonlight.feed.ui.design.MenuAction("删除", destructive = true) {
                    val cur = fresh() ?: return@MenuAction
                    val (n, file) = ProfileMediaActions.deletePrev(cur, avatar)
                    save(n)
                    if (file != null && !ProfileMediaActions.usedBy(all().map { if (it.id == n.id) n else it }, file))
                        com.moonlight.feed.data.StoredCopies.delete(file, java.io.File(ctx.filesDir, "avatars"))
                    MoonToaster.show("已删除")
                },
            ))
        },
    )
}
