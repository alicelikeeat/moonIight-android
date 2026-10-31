package com.moonlight.feed.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("moon_settings")

/** 图片停留 options; 0 = 永久 (images stay until the user swipes). */
object ImageDwell {
    const val FOREVER = 0
    val OPTIONS = listOf(3, 5, 10, 30, FOREVER)
    fun label(v: Int) = if (v <= 0) "永久" else "$v 秒"
    fun autoAdvance(v: Int) = v > 0
}

class SettingsRepository(private val ctx: Context) {
    private object K {
        val muted = booleanPreferencesKey("muted")
        val loop = booleanPreferencesKey("loop")
        val shuffle = booleanPreferencesKey("shuffle")
        val imageSeconds = intPreferencesKey("image_seconds")
        val lastIndex = intPreferencesKey("last_index")
        val randomOn = booleanPreferencesKey("random_on")
        val keywords = stringSetPreferencesKey("keywords")
        val exclude = stringSetPreferencesKey("exclude")
        val pexelsKey = stringPreferencesKey("pexels_key")
        val pixabayKey = stringPreferencesKey("pixabay_key")
        val youtubeKey = stringPreferencesKey("youtube_key")
        val enabledPlatforms = stringSetPreferencesKey("enabled_platforms")
        val resume = booleanPreferencesKey("resume")
        val autoSaveGallery = booleanPreferencesKey("auto_save_gallery")
        val showProgressWhilePlaying = booleanPreferencesKey("show_progress_playing")
        val themeMode = stringPreferencesKey("theme_mode")
        val hidden = stringSetPreferencesKey("hidden_ids")
        val searchCount = intPreferencesKey("search_count")
        val revealOn = booleanPreferencesKey("reveal_on")
        val revealSpeed = stringPreferencesKey("reveal_speed")
        val feedScope = stringPreferencesKey("feed_scope")
        val resumeOnReturn = booleanPreferencesKey("resume_on_return")
        val momentsAutoplay = stringPreferencesKey("moments_autoplay")
        val loopVideos = booleanPreferencesKey("loop_videos")
        val homeBg = stringPreferencesKey("home_bg")
        val homeBgPath = stringPreferencesKey("home_bg_path")
        val homeBgDim = booleanPreferencesKey("home_bg_dim")
        val homeBgAllPages = booleanPreferencesKey("home_bg_all")
        val videoDisplay = stringPreferencesKey("video_display")
        val menuBackdrop = stringPreferencesKey("menu_backdrop")
        val menuSelect = stringPreferencesKey("menu_select")
        val ringAnim = stringPreferencesKey("ring_anim")
        val ringAnimSet = stringPreferencesKey("ring_anim_set")
        // 首页背景: photo and video kept independently (v3.2)
        val homeBgPhoto = stringPreferencesKey("home_bg_photo")
        val homeBgVideo = stringPreferencesKey("home_bg_video")
        val homeBgPhotoDim = booleanPreferencesKey("home_bg_photo_dim")
        val homeBgVideoDim = booleanPreferencesKey("home_bg_video_dim")
        val homeBgPhotoFocus = stringPreferencesKey("home_bg_photo_focus")
        val homeBgVideoFocus = stringPreferencesKey("home_bg_video_focus")
        /** 按键背景 (v4.3): skins + per-button assignment, JSON (see ButtonSkinPrefs). */
        val buttonSkins = stringPreferencesKey("button_skins")
        val v5 = stringPreferencesKey("v5_prefs")
        val chatServerUrl = stringPreferencesKey("chat_server_url")
        val recentSearches = stringPreferencesKey("recent_searches")
    }

    val defaultsKeywords = setOf(
        "自然", "城市", "夜景", "猫咪", "狗", "美食", "旅行", "星空",
        "海浪", "樱花", "雪景", "日落", "建筑", "抽象", "黑白",
        "nature", "space", "ocean", "city", "animals",
    )

    data class Snapshot(
        val muted: Boolean = false,
        val loop: Boolean = true,
        val shuffle: Boolean = false,
        val imageSeconds: Int = 5,
        val lastIndex: Int = 0,
        val randomOn: Boolean = false,
        val keywords: Set<String> = emptySet(),
        val exclude: Set<String> = emptySet(),
        val pexelsKey: String = "",
        val pixabayKey: String = "",
        val youtubeKey: String = "",
        val enabledPlatforms: Set<String> = PlatformId.DEFAULT_ENABLED,
        val resume: Boolean = true,
        val autoSaveGallery: Boolean = true,
        val showProgressWhilePlaying: Boolean = false,
        val themeMode: String = "SYSTEM",
        val hidden: Set<String> = emptySet(),
        val searchCount: Int = 10,
        val revealOn: Boolean = true,
        val revealSpeed: String = "STANDARD",
        val feedScope: String = "ALL",
        val resumeOnReturn: Boolean = true,
        /** 朋友圈 video autoplay: ALWAYS / WIFI / OFF. */
        val momentsAutoplay: String = "ALWAYS",
        /** Video avatars / covers / backgrounds animate (false → first frame). */
        val loopVideos: Boolean = true,
        /** 首页 background: THEME (match other pages) / BLACK / PHOTO / VIDEO. */
        val homeBg: String = "THEME",
        val homeBgPath: String? = null,
        val homeBgDim: Boolean = true,
        val homeBgAllPages: Boolean = false,
        /** 视频显示: SMART / FIT / FILL. */
        val videoDisplay: String = "SMART",
        /** 菜单背景: CLEAR / LIGHT / BLUR. */
        val menuBackdrop: String = "BLUR",
        /** 长按菜单选择方式: TAP (点击, default) / DRAG (拖动). */
        val menuSelect: String = "TAP",
        /** 长按动画: SPIN / BLUR / SHATTER / MOON / RANDOM. */
        val ringAnim: String = "SPIN",
        /** Styles 随机 picks from (CSV); empty = all. */
        val ringAnimSet: String = "",
        val homeBgPhoto: String? = null,
        val homeBgVideo: String? = null,
        val homeBgPhotoDim: Boolean? = null,
        val homeBgVideoDim: Boolean? = null,
        val homeBgPhotoFocus: String? = null,
        val homeBgVideoFocus: String? = null,
        val chatServerUrl: String = "",
        val recentSearches: List<String> = emptyList(),
        /** 按键背景 JSON. */
        val buttonSkins: String = "",
        /** v5.0 options blob. */
        val v5: String = "",
    ) {
        fun buttonSkinPrefs(): ButtonSkinPrefs = ButtonSkinPrefs.decode(buttonSkins)
        fun v5Prefs(): V5Prefs = V5Prefs.decode(v5, momentsAutoplay)

        /** 首页背景 with the photo and video slots kept apart (legacy single path migrated by type). */
        fun homeBgPrefs(): HomeBgPrefs = HomeBgPrefs.read(
            homeBg, homeBgPhoto, homeBgVideo, homeBgPath, homeBgPhotoDim, homeBgVideoDim, homeBgDim,
            homeBgPhotoFocus, homeBgVideoFocus, homeBgAllPages,
        )
    }

    val flow: Flow<Snapshot> = ctx.dataStore.data.map { p ->
        Snapshot(
            muted = p[K.muted] ?: false,
            loop = p[K.loop] ?: true,
            shuffle = p[K.shuffle] ?: false,
            imageSeconds = p[K.imageSeconds] ?: 5,
            lastIndex = p[K.lastIndex] ?: 0,
            randomOn = p[K.randomOn] ?: false,
            keywords = p[K.keywords] ?: defaultsKeywords,
            exclude = p[K.exclude] ?: emptySet(),
            pexelsKey = p[K.pexelsKey] ?: "",
            pixabayKey = p[K.pixabayKey] ?: "",
            youtubeKey = p[K.youtubeKey] ?: "",
            enabledPlatforms = p[K.enabledPlatforms] ?: PlatformId.DEFAULT_ENABLED,
            resume = p[K.resume] ?: true,
            autoSaveGallery = p[K.autoSaveGallery] ?: true,
            showProgressWhilePlaying = p[K.showProgressWhilePlaying] ?: false,
            themeMode = p[K.themeMode] ?: "SYSTEM",
            hidden = p[K.hidden] ?: emptySet(),
            searchCount = p[K.searchCount] ?: 10,
            revealOn = p[K.revealOn] ?: true,
            revealSpeed = p[K.revealSpeed] ?: "STANDARD",
            feedScope = p[K.feedScope] ?: "ALL",
            resumeOnReturn = p[K.resumeOnReturn] ?: true,
            momentsAutoplay = p[K.momentsAutoplay] ?: "ALWAYS",
            loopVideos = p[K.loopVideos] ?: true,
            homeBg = p[K.homeBg] ?: "THEME",
            homeBgPath = p[K.homeBgPath],
            homeBgDim = p[K.homeBgDim] ?: true,
            homeBgAllPages = p[K.homeBgAllPages] ?: false,
            videoDisplay = p[K.videoDisplay] ?: "SMART",
            menuBackdrop = p[K.menuBackdrop] ?: "BLUR",
            menuSelect = p[K.menuSelect] ?: "TAP",
            ringAnim = RingAnimPick.normalize(p[K.ringAnim]),
            ringAnimSet = p[K.ringAnimSet]?.takeIf { it.isNotBlank() }?.let { RingAnimPick.encodeSet(RingAnimPick.parseSet(it)) } ?: "",   // v4.0 names migrate
            homeBgPhoto = p[K.homeBgPhoto],
            homeBgVideo = p[K.homeBgVideo],
            homeBgPhotoDim = p[K.homeBgPhotoDim],
            homeBgVideoDim = p[K.homeBgVideoDim],
            homeBgPhotoFocus = p[K.homeBgPhotoFocus],
            homeBgVideoFocus = p[K.homeBgVideoFocus],
            chatServerUrl = p[K.chatServerUrl] ?: "",
            recentSearches = p[K.recentSearches]?.split('\n')?.filter { it.isNotBlank() } ?: emptyList(),
            buttonSkins = p[K.buttonSkins] ?: "",
            v5 = p[K.v5] ?: "",
        )
    }

    /** v5.0: read-modify-write of the options blob. */
    suspend fun updateV5(f: (V5Prefs) -> V5Prefs) = ctx.dataStore.edit { p ->
        p[K.v5] = f(V5Prefs.decode(p[K.v5], p[K.momentsAutoplay])).encode()
    }
    suspend fun setMuted(v: Boolean) = ctx.dataStore.edit { it[K.muted] = v }
    suspend fun setLoop(v: Boolean) = ctx.dataStore.edit { it[K.loop] = v }
    suspend fun setShuffle(v: Boolean) = ctx.dataStore.edit { it[K.shuffle] = v }
    /** 0 = 永久 (never auto-advance). */
    suspend fun setImageSeconds(v: Int) = ctx.dataStore.edit { it[K.imageSeconds] = if (v <= 0) 0 else v.coerceIn(2, 60) }
    suspend fun setLastIndex(v: Int) = ctx.dataStore.edit { it[K.lastIndex] = v.coerceAtLeast(0) }
    suspend fun setRandomOn(v: Boolean) = ctx.dataStore.edit { it[K.randomOn] = v }
    suspend fun setKeywords(v: Set<String>) = ctx.dataStore.edit { it[K.keywords] = v }
    suspend fun setExclude(v: Set<String>) = ctx.dataStore.edit { it[K.exclude] = v }
    suspend fun setPexelsKey(v: String) = ctx.dataStore.edit { it[K.pexelsKey] = v.trim() }
    suspend fun setPixabayKey(v: String) = ctx.dataStore.edit { it[K.pixabayKey] = v.trim() }
    suspend fun setYoutubeKey(v: String) = ctx.dataStore.edit { it[K.youtubeKey] = v.trim() }
    suspend fun setEnabledPlatforms(v: Set<String>) = ctx.dataStore.edit { it[K.enabledPlatforms] = v }
    suspend fun togglePlatform(id: String, on: Boolean) = ctx.dataStore.edit { prefs ->
        val cur = (prefs[K.enabledPlatforms] ?: PlatformId.DEFAULT_ENABLED).toMutableSet()
        if (on) cur += id else cur -= id
        prefs[K.enabledPlatforms] = cur
    }
    suspend fun setResume(v: Boolean) = ctx.dataStore.edit { it[K.resume] = v }
    suspend fun setAutoSaveGallery(v: Boolean) = ctx.dataStore.edit { it[K.autoSaveGallery] = v }
    suspend fun setShowProgressWhilePlaying(v: Boolean) = ctx.dataStore.edit { it[K.showProgressWhilePlaying] = v }
    suspend fun setThemeMode(v: String) = ctx.dataStore.edit { it[K.themeMode] = v }
    suspend fun hide(id: String) = ctx.dataStore.edit { it[K.hidden] = (it[K.hidden] ?: emptySet()) + id }
    suspend fun unhide(id: String) = ctx.dataStore.edit { it[K.hidden] = (it[K.hidden] ?: emptySet()) - id }
    suspend fun setRevealOn(v: Boolean) = ctx.dataStore.edit { it[K.revealOn] = v }
    suspend fun setRevealSpeed(v: String) = ctx.dataStore.edit { it[K.revealSpeed] = v }
    suspend fun setChatServerUrl(v: String) = ctx.dataStore.edit { it[K.chatServerUrl] = v.trim() }
    suspend fun setMomentsAutoplay(v: String) = ctx.dataStore.edit { it[K.momentsAutoplay] = v }
    suspend fun setLoopVideos(v: Boolean) = ctx.dataStore.edit { it[K.loopVideos] = v }
    suspend fun setMenuSelect(v: String) = ctx.dataStore.edit { it[K.menuSelect] = MenuSelect.of(v).name }
    suspend fun setRingAnim(v: String) = ctx.dataStore.edit { it[K.ringAnim] = RingAnimPick.normalize(v) }
    suspend fun setRingAnimSet(v: Set<RingAnim>) = ctx.dataStore.edit { it[K.ringAnimSet] = RingAnimPick.encodeSet(v) }

    /**
     * Read-modify-write of 首页背景. Writes both slots explicitly and retires the legacy single path, so switching
     * modes never forgets a pick. Returns the new prefs.
     */
    suspend fun updateHomeBg(f: (HomeBgPrefs) -> HomeBgPrefs): HomeBgPrefs {
        var out = HomeBgPrefs()
        ctx.dataStore.edit { p ->
            val cur = HomeBgPrefs.read(
                p[K.homeBg], p[K.homeBgPhoto], p[K.homeBgVideo], p[K.homeBgPath], p[K.homeBgPhotoDim], p[K.homeBgVideoDim],
                p[K.homeBgDim] ?: true, p[K.homeBgPhotoFocus], p[K.homeBgVideoFocus], p[K.homeBgAllPages] ?: false,
            )
            val n = f(cur); out = n
            p[K.homeBg] = n.kind.name
            p[K.homeBgDim] = n.dim
            p[K.homeBgAllPages] = n.allPages
            p.remove(K.homeBgPath)
            fun slot(s: BgSlot?, path: androidx.datastore.preferences.core.Preferences.Key<String>, dim: androidx.datastore.preferences.core.Preferences.Key<Boolean>, focus: androidx.datastore.preferences.core.Preferences.Key<String>) {
                if (s == null) { p.remove(path); p.remove(dim); p.remove(focus) }
                else { p[path] = s.path; p[dim] = s.dim; s.focus?.let { p[focus] = it.encode() } ?: p.remove(focus) }
            }
            slot(n.photo, K.homeBgPhoto, K.homeBgPhotoDim, K.homeBgPhotoFocus)
            slot(n.video, K.homeBgVideo, K.homeBgVideoDim, K.homeBgVideoFocus)
        }
        return out
    }

    /** Read-modify-write of 按键背景 (decoded leniently; unknown buttons dropped). */
    suspend fun updateButtonSkins(f: (ButtonSkinPrefs) -> ButtonSkinPrefs): ButtonSkinPrefs {
        var out = ButtonSkinPrefs()
        ctx.dataStore.edit { p -> out = f(ButtonSkinPrefs.decode(p[K.buttonSkins])); p[K.buttonSkins] = out.encode() }
        return out
    }

    /** Kept for callers that only switch to 跟随主题 / 纯黑 or record a fresh pick. */
    suspend fun setHomeBg(kind: String, path: String? = null) = updateHomeBg { pr ->
        val k = HomeBgKind.entries.firstOrNull { it.name == kind } ?: HomeBgKind.THEME
        if (path != null && (k == HomeBgKind.PHOTO || k == HomeBgKind.VIDEO)) pr.picked(k == HomeBgKind.VIDEO, path) else pr.select(k).prefs
    }
    suspend fun setVideoDisplay(v: String) = ctx.dataStore.edit { it[K.videoDisplay] = v }
    suspend fun setMenuBackdrop(v: String) = ctx.dataStore.edit { it[K.menuBackdrop] = v }
    suspend fun setHomeBgDim(v: Boolean) = updateHomeBg { it.withDim(v) }
    suspend fun setHomeBgAllPages(v: Boolean) = updateHomeBg { it.copy(allPages = v) }
    suspend fun setResumeOnReturn(v: Boolean) = ctx.dataStore.edit { it[K.resumeOnReturn] = v }
    suspend fun setFeedScope(v: String) = ctx.dataStore.edit { it[K.feedScope] = FeedScope.of(v).name }
    suspend fun setSearchCount(v: Int) = ctx.dataStore.edit { it[K.searchCount] = v.coerceIn(4, 30) }
    /** Most-recent-first, max 10, user-typed only (no presets). */
    suspend fun pushRecentSearch(q: String) = ctx.dataStore.edit {
        val t = q.trim().replace('\n', ' ')
        if (t.isBlank()) return@edit
        val cur = it[K.recentSearches]?.split('\n')?.filter { s -> s.isNotBlank() } ?: emptyList()
        it[K.recentSearches] = (listOf(t) + cur.filter { s -> !s.equals(t, true) }).take(10).joinToString("\n")
    }
    suspend fun removeRecentSearch(q: String) = ctx.dataStore.edit {
        val cur = it[K.recentSearches]?.split('\n')?.filter { s -> s.isNotBlank() } ?: emptyList()
        it[K.recentSearches] = cur.filter { s -> s != q }.joinToString("\n")
    }
    suspend fun clearRecentSearches() = ctx.dataStore.edit { it[K.recentSearches] = "" }
    suspend fun clearHidden() = ctx.dataStore.edit { it[K.hidden] = emptySet() }
}
