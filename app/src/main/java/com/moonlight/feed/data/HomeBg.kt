package com.moonlight.feed.data

/** 首页 background choice (also optionally used by every page). Pure; unit-tested. */
enum class HomeBgKind(val label: String) { THEME("跟随主题"), BLACK("纯黑"), PHOTO("照片"), VIDEO("视频") }

data class HomeBgSpec(val kind: HomeBgKind, val path: String?, val dim: Boolean, val allPages: Boolean, val focus: MediaFocus? = null) {
    /** Applies to a non-home page only when the user opted in and picked something other than the theme. */
    fun appliesToOtherPages(): Boolean = allPages && kind != HomeBgKind.THEME

    companion object {
        /** Missing / deleted media falls back to the theme background. */
        fun resolve(kind: String?, path: String?, dim: Boolean, allPages: Boolean, exists: (String) -> Boolean): HomeBgSpec {
            val k = HomeBgKind.entries.firstOrNull { it.name == kind } ?: HomeBgKind.THEME
            val needsMedia = k == HomeBgKind.PHOTO || k == HomeBgKind.VIDEO
            return if (needsMedia && (path == null || !exists(path))) HomeBgSpec(HomeBgKind.THEME, null, dim, allPages)
            else HomeBgSpec(k, if (needsMedia) path else null, dim, allPages)
        }
    }
}

/** One saved background (a copy inside app storage) with its own 模糊并变暗 and position/zoom. */
data class BgSlot(val path: String, val dim: Boolean = true, val focus: MediaFocus? = null)

/**
 * 首页背景 preferences. The picked photo and the picked video are kept independently; [kind] only says which is
 * active. Switching 照片 → 跟随主题 → 照片 restores the same photo instantly; only [delete] forgets (and removes) a copy.
 * Pure; unit-tested.
 */
data class HomeBgPrefs(
    val kind: HomeBgKind = HomeBgKind.THEME,
    val photo: BgSlot? = null,
    val video: BgSlot? = null,
    val allPages: Boolean = false,
    /** 模糊并变暗 used for 纯黑 / not-yet-picked slots. */
    val dim: Boolean = true,
) {
    fun slot(video: Boolean) = if (video) this.video else photo
    val active: BgSlot? get() = when (kind) { HomeBgKind.PHOTO -> photo; HomeBgKind.VIDEO -> video; else -> null }
    val activeDim: Boolean get() = active?.dim ?: dim

    /** Result of tapping a 首页背景 option: the new prefs, and whether the picker must open (nothing saved yet). */
    data class Select(val prefs: HomeBgPrefs, val needsPick: Boolean)

    fun select(k: HomeBgKind, exists: (String) -> Boolean = { true }): Select = when (k) {
        HomeBgKind.THEME, HomeBgKind.BLACK -> Select(copy(kind = k), false)
        HomeBgKind.PHOTO, HomeBgKind.VIDEO -> {
            val s = slot(k == HomeBgKind.VIDEO)
            if (s != null && exists(s.path)) Select(copy(kind = k), false) else Select(this, true)
        }
    }

    /** A new photo/video was picked: it replaces only its own slot (fresh default position), and becomes active. */
    fun picked(isVideo: Boolean, path: String): HomeBgPrefs {
        val keepDim = slot(isVideo)?.dim ?: dim
        val s = BgSlot(path, keepDim, null)
        return if (isVideo) copy(video = s, kind = HomeBgKind.VIDEO) else copy(photo = s, kind = HomeBgKind.PHOTO)
    }

    /** Explicit 删除: forgets the slot; if it was active the background falls back to 跟随主题. Returns the file to remove. */
    fun delete(isVideo: Boolean): Pair<HomeBgPrefs, String?> {
        val old = slot(isVideo)?.path
        val wasActive = kind == (if (isVideo) HomeBgKind.VIDEO else HomeBgKind.PHOTO)
        val p = (if (isVideo) copy(video = null) else copy(photo = null)).let { if (wasActive) it.copy(kind = HomeBgKind.THEME) else it }
        // never delete a file the other slot still uses
        val removable = old?.takeIf { it != p.photo?.path && it != p.video?.path }
        return p to removable
    }

    fun withDim(v: Boolean): HomeBgPrefs = when (kind) {
        HomeBgKind.PHOTO -> copy(photo = photo?.copy(dim = v), dim = if (photo == null) v else dim)
        HomeBgKind.VIDEO -> copy(video = video?.copy(dim = v), dim = if (video == null) v else dim)
        else -> copy(dim = v)
    }

    fun withFocus(isVideo: Boolean, f: MediaFocus?): HomeBgPrefs =
        if (isVideo) copy(video = video?.copy(focus = f)) else copy(photo = photo?.copy(focus = f))

    /** What the screens draw. Missing / deleted media falls back to the theme background. */
    fun spec(exists: (String) -> Boolean): HomeBgSpec {
        val a = active
        return if ((kind == HomeBgKind.PHOTO || kind == HomeBgKind.VIDEO) && (a == null || !exists(a.path))) HomeBgSpec(HomeBgKind.THEME, null, dim, allPages)
        else HomeBgSpec(kind, a?.path, a?.dim ?: dim, allPages, a?.focus)
    }

    companion object {
        private val VIDEO_EXT = setOf("mp4", "m4v", "mov", "webm", "3gp", "mkv", "ts")
        fun isVideoPath(p: String) = p.substringAfterLast('.', "").lowercase() in VIDEO_EXT

        /**
         * Builds prefs from stored values. v3.0/v3.1 kept a single [legacyPath] for both; it is migrated into the
         * photo or video slot by file type (only when that slot is empty), so nothing picked earlier is lost.
         */
        fun read(
            kind: String?, photoPath: String?, videoPath: String?, legacyPath: String?,
            photoDim: Boolean?, videoDim: Boolean?, dim: Boolean,
            photoFocus: String?, videoFocus: String?, allPages: Boolean,
        ): HomeBgPrefs {
            val legacyVideo = legacyPath?.let { isVideoPath(it) }
            val ph = photoPath ?: legacyPath?.takeIf { legacyVideo == false }
            val vi = videoPath ?: legacyPath?.takeIf { legacyVideo == true }
            return HomeBgPrefs(
                kind = HomeBgKind.entries.firstOrNull { it.name == kind } ?: HomeBgKind.THEME,
                photo = ph?.let { BgSlot(it, photoDim ?: dim, MediaFocus.decode(photoFocus)) },
                video = vi?.let { BgSlot(it, videoDim ?: dim, MediaFocus.decode(videoFocus)) },
                allPages = allPages, dim = dim,
            )
        }
    }
}

/** Removes a stored copy — only ever inside the app's own media folder [root]; never touches gallery originals. */
object StoredCopies {
    fun delete(path: String?, root: java.io.File): Boolean {
        if (path == null || path.startsWith("art:")) return false
        val f = java.io.File(path)
        val inside = runCatching { f.canonicalPath.startsWith(root.canonicalPath + java.io.File.separator) }.getOrDefault(false)
        return inside && f.isFile && f.delete()
    }
}
