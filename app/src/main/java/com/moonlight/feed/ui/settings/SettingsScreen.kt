package com.moonlight.feed.ui.settings

import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.CenterFocusStrong
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.AspectRatio
import androidx.compose.material.icons.outlined.BlurOn
import androidx.compose.animation.togetherWith
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Language
import com.moonlight.feed.data.ImageDwell
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.AllInclusive
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import com.moonlight.feed.ui.design.GlassPill
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.automirrored.outlined.VolumeOff
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Casino
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.LinearScale
import androidx.compose.material.icons.outlined.PauseCircle
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.RemoveCircleOutline
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.material.icons.outlined.Tag
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.moonlight.feed.MoonApp
import com.moonlight.feed.data.GallerySaver
import com.moonlight.feed.data.PlatformId
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import com.moonlight.feed.data.RemoteApis
import com.moonlight.feed.data.SaveHelper
import com.moonlight.feed.data.SettingsRepository
import com.moonlight.feed.ui.SettingsDivider
import com.moonlight.feed.ui.SettingsGroup
import com.moonlight.feed.ui.SettingsRow
import com.moonlight.feed.ui.design.AnchorState
import com.moonlight.feed.ui.design.Haptics
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawWithContent
import com.moonlight.feed.ui.design.LocalOverlay
import com.moonlight.feed.ui.design.MenuAction
import com.moonlight.feed.ui.design.MenuSpec
import com.moonlight.feed.ui.design.MoonSwitch
import com.moonlight.feed.ui.design.MoonToaster
import com.moonlight.feed.ui.design.ScreenHeader
import com.moonlight.feed.ui.design.anchor
import com.moonlight.feed.ui.design.moonPress
import com.moonlight.feed.ui.theme.LocalMoonColors
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Wallpaper
import androidx.compose.material.icons.outlined.Movie
import com.moonlight.feed.ui.theme.ThemeMode
import kotlinx.coroutines.launch

class SettingsActions(
    val setReveal: (Boolean) -> Unit = {},
    val setRevealSpeed: (String) -> Unit = {},
    val setVideoDisplay: (String) -> Unit = {},
    val setMenuBackdrop: (String) -> Unit = {},
    val openSources: () -> Unit = {},
    val pickSearchCount: (Rect) -> Unit = {},
    val setTheme: (ThemeMode) -> Unit = {},
    val setMuted: (Boolean) -> Unit = {},
    val setLoop: (Boolean) -> Unit = {},
    val setShuffle: (Boolean) -> Unit = {},
    val pickImageSeconds: (Rect) -> Unit = {},
    val setResume: (Boolean) -> Unit = {},
    val setResumeOnReturn: (Boolean) -> Unit = {},
    val setShowProgress: (Boolean) -> Unit = {},
    val togglePlatform: (String, Boolean) -> Unit = { _, _ -> },
    val onPlatformLongPress: (String, Rect) -> Unit = { _, _ -> },
    val setRandom: (Boolean) -> Unit = {},
    val editKeywords: () -> Unit = {},
    val openFriends: () -> Unit = {},
    val exportSocial: () -> Unit = {},
    val importSocial: () -> Unit = {},
    val editServerUrl: () -> Unit = {},
    val editExclude: () -> Unit = {},
    val setMomentsAutoplay: (String) -> Unit = {},
    val setLoopVideos: (Boolean) -> Unit = {},
    /** THEME / BLACK apply directly; PHOTO / VIDEO open the picker. */
    val setHomeBg: (com.moonlight.feed.data.HomeBgKind) -> Unit = {},
    val setHomeBgDim: (Boolean) -> Unit = {},
    val setHomeBgAll: (Boolean) -> Unit = {},
    val pickHomeBg: (Boolean) -> Unit = {},
    val deleteHomeBg: (Boolean) -> Unit = {},
    val adjustHomeBg: (Boolean) -> Unit = {},
    val setMenuSelect: (String) -> Unit = {},
    val setRingAnim: (String) -> Unit = {},
    val setRingAnimSet: (Set<com.moonlight.feed.data.RingAnim>) -> Unit = {},
    val previewRingAnim: (com.moonlight.feed.data.RingAnim) -> Unit = {},
    /** 按键背景: opens the per-button list. */
    val openButtonSkins: () -> Unit = {},
    val setAppIcon: (com.moonlight.feed.data.AppIcon) -> Unit = {},
    val makePhotoIcon: (Rect) -> Unit = {},
    val editKeys: () -> Unit = {},
    val setAutoSave: (Boolean) -> Unit = {},
    val restoreHidden: () -> Unit = {},
    val showGestures: () -> Unit = {},
    /** v5.0 options (朋友圈自动播放, 定时长按, 录屏, 星轨速度, 删除). */
    val updateV5: V5Update = {},
    val setHoldPresent: (String) -> Unit = {},
    val setTabDock: (String) -> Unit = {},
    val setPortraitPlayback: (Boolean) -> Unit = {},
    val setVirtualFriends: (Boolean) -> Unit = {},
    val setVirtualInterval: (Int) -> Unit = {},
    val setVirtualProvider: (String) -> Unit = {},
    val setVirtualKey: (String) -> Unit = {},
)

private fun platformSubtitle(id: String, s: SettingsRepository.Snapshot) = when (id) {
    PlatformId.YOUTUBE -> "RSS 频道" + if (s.youtubeKey.isNotBlank()) " · API 搜索" else " · 可填 API Key"
    PlatformId.PEXELS, PlatformId.PIXABAY -> if ((id == PlatformId.PEXELS && s.pexelsKey.isBlank()) || (id == PlatformId.PIXABAY && s.pixabayKey.isBlank())) "需 API Key" else "已配置 Key"
    PlatformId.TWITCH -> "仅粘贴 Clip 链接"
    PlatformId.WEB -> "普通网页 WebView"
    PlatformId.DAILYMOTION -> "公开 API · 嵌入可能受限"
    else -> "随机发现与粘贴链接"
}

private fun platformTint(id: String) = when (id) {
    PlatformId.BILI -> Color(0xFF6FB7E8)
    PlatformId.YOUTUBE -> Color(0xFFFF6B6B)
    PlatformId.VIMEO -> Color(0xFF5BC6E8)
    PlatformId.DAILYMOTION -> Color(0xFF7A8CFF)
    PlatformId.ARCHIVE -> Color(0xFFBFB59A)
    PlatformId.NASA -> Color(0xFF8FA8FF)
    PlatformId.COMMONS -> Color(0xFF7FD1AE)
    PlatformId.PEERTUBE -> Color(0xFFF2A65A)
    PlatformId.PEXELS -> Color(0xFF55C59A)
    PlatformId.PIXABAY -> Color(0xFF5FCB8A)
    PlatformId.TWITCH -> Color(0xFFB18CFF)
    else -> Color(0xFFB8B8C0)
}

// =================================================================== stateful

@Composable
fun SettingsScreen() {
    var showSources by remember { mutableStateOf(false) }
    androidx.compose.animation.AnimatedContent(
        showSources,
        transitionSpec = {
            val dir = if (targetState) 1 else -1
            (androidx.compose.animation.slideInHorizontally(androidx.compose.animation.core.spring(0.86f, 380f)) { it * dir / 3 } + androidx.compose.animation.fadeIn()) togetherWith
                (androidx.compose.animation.slideOutHorizontally(androidx.compose.animation.core.spring(0.86f, 380f)) { -it * dir / 4 } + androidx.compose.animation.fadeOut())
        },
        label = "settings-nav",
    ) { sub ->
        if (sub) com.moonlight.feed.ui.sources.SourcesScreen(onBack = { showSources = false })
        else SettingsMain(onOpenSources = { showSources = true })
    }
}

@Composable
private fun SettingsMain(onOpenSources: () -> Unit) {
    val app = MoonApp.instance
    val s by app.settings.flow.collectAsState(initial = SettingsRepository.Snapshot())
    val userSources by app.sources.items.collectAsState()
    val overlay = LocalOverlay.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()

    fun edit(title: String, initial: String, hint: String, save: suspend (String) -> Unit) {
        overlay.showSheet(title) { dismiss -> EditSheet(initial, hint) { v -> dismiss(); scope.launch { save(v); MoonToaster.show("已保存", Icons.Outlined.Save) } } }
    }
    val editKeys = {
        edit("API Key", "youtube=${s.youtubeKey}\npexels=${s.pexelsKey}\npixabay=${s.pixabayKey}", "youtube= / pexels= / pixabay=") { d ->
            val m = d.lines().associate { val p = it.split("=", limit = 2); (p.getOrNull(0)?.trim()?.lowercase() ?: "") to (p.getOrNull(1)?.trim() ?: "") }
            app.settings.setYoutubeKey(m["youtube"] ?: ""); app.settings.setPexelsKey(m["pexels"] ?: ""); app.settings.setPixabayKey(m["pixabay"] ?: "")
        }
    }
    val editKeywords = {
        edit("关键词", s.keywords.joinToString("\n"), "每行一个") { d ->
            app.settings.setKeywords(d.lines().map { it.trim() }.filter { it.isNotBlank() }.toSet().ifEmpty { app.settings.defaultsKeywords })
        }
    }

    fun refreshPlatform(id: String) {
        scope.launch {
            MoonToaster.show("正在从 ${PlatformId.label(id)} 获取…", Icons.Outlined.Refresh)
            val found = runCatching {
                RemoteApis.multiRandomDiscovery(
                    keywords = s.keywords.toList(), exclude = s.exclude, enabled = setOf(id),
                    youtubeKey = s.youtubeKey, pexelsKey = s.pexelsKey, pixabayKey = s.pixabayKey,
                )
            }.getOrDefault(emptyList())
            found.forEach { app.library.upsert(it); SaveHelper.maybeSaveEntry(it) }
            MoonToaster.show(if (found.isEmpty()) "${PlatformId.label(id)} 暂无结果或不可达" else "已从 ${PlatformId.label(id)} 加入 ${found.size} 项", Icons.Outlined.Refresh)
        }
    }

    val social by app.social.state.collectAsState()
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val exportLauncher = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val ok = runCatching { ctx.contentResolver.openOutputStream(uri)?.use { it.write(app.social.exportJson().toByteArray()) } != null }.getOrDefault(false)
            MoonToaster.show(if (ok) "已导出备份" else "导出失败", Icons.Outlined.Save)
        }
    }
    val importLauncher = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val text = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { runCatching { ctx.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } }.getOrNull() }
            if (text == null) { MoonToaster.show("无法读取文件"); return@launch }
            val check = runCatching { com.moonlight.feed.data.social.SocialJson.decode(text) }
            check.exceptionOrNull()?.let { MoonToaster.show("导入失败：${it.message}"); return@launch }
            val inc = check.getOrThrow()
            overlay.confirm("导入备份？", "${inc.friends.size} 位好友 · ${inc.moments.size} 条动态 · ${inc.messages.size} 条消息", listOf(
                MenuAction("合并（保留现有）") { app.social.importJson(text, replace = false); MoonToaster.show("已合并导入") },
                MenuAction("替换现有数据", destructive = true) {
                    val snap = app.social.value
                    app.social.importJson(text, replace = true)
                    MoonToaster.show("已替换", action = "撤销") { app.social.restore(snap) }
                },
            ))
        }
    }
    var bgPickKind by remember { mutableStateOf(com.moonlight.feed.data.HomeBgKind.PHOTO) }
    val bgPicker = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val (path, video) = app.social.saveProfileMedia(uri) ?: run { MoonToaster.show("无法读取这个文件"); return@launch }
            app.settings.updateHomeBg { it.picked(video, path) }   // replaces only its own slot; the other pick is kept
            MoonToaster.show(if (video) "首页背景：视频（静音循环）" else "首页背景：照片", Icons.Outlined.CheckCircle)
        }
    }
    var appIcon by remember { mutableStateOf(com.moonlight.feed.data.AppIconSwitcher.current(ctx)) }
    fun frameIcon(path: String) {
        com.moonlight.feed.ui.design.FocusEditing.open(com.moonlight.feed.ui.design.FocusRequest(path, false, 0, "调整图标", com.moonlight.feed.data.MediaFocus(0.5f, 0.4f, 1f)) { f ->
            val ok = IconShortcut.pin(ctx, path, f)
            MoonToaster.show(if (ok) "已请求添加到桌面 · 请在桌面弹窗里确认" else "当前桌面应用不支持添加快捷方式图标")
        })
    }
    val iconPhotoPicker = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val f = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { IconShortcut.copyPhoto(ctx, uri) }
            if (f == null) MoonToaster.show("无法读取这个文件") else frameIcon(f.absolutePath)
        }
    }
    val iconVideoPicker = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        overlay.showSheet("选择一帧") { dismiss ->
            FramePicker(uri) { bmp ->
                dismiss()
                scope.launch {
                    val f = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { IconShortcut.saveFrame(ctx, bmp) }
                    if (f == null) MoonToaster.show("无法保存这一帧") else frameIcon(f.absolutePath)
                }
            }
        }
    }
    val openButtonSkins = rememberButtonSkinController()
    val actions = SettingsActions(
        openButtonSkins = openButtonSkins,
        setMomentsAutoplay = { v -> scope.launch { app.settings.setMomentsAutoplay(v) } },
        updateV5 = { f -> scope.launch { app.settings.updateV5(f) } },
        setLoopVideos = { v -> scope.launch { app.settings.setLoopVideos(v) } },
        setHomeBg = { k ->
            scope.launch {
                // a saved photo/video comes back instantly; only an empty slot opens the picker
                var needsPick = false
                app.settings.updateHomeBg { pr -> pr.select(k) { java.io.File(it).exists() }.also { needsPick = it.needsPick }.prefs }
                if (needsPick) { bgPickKind = k; bgPicker.launch(arrayOf(if (k == com.moonlight.feed.data.HomeBgKind.VIDEO) "video/*" else "image/*")) }
            }
        },
        pickHomeBg = { v ->
            bgPickKind = if (v) com.moonlight.feed.data.HomeBgKind.VIDEO else com.moonlight.feed.data.HomeBgKind.PHOTO
            bgPicker.launch(arrayOf(if (v) "video/*" else "image/*"))
        },
        deleteHomeBg = { v ->
            overlay.confirm(if (v) "删除保存的背景视频？" else "删除保存的背景照片？", "会删除 App 内的副本（相册里的原文件不受影响）。之后再选「${if (v) "视频" else "照片"}」需要重新选择。", listOf(
                MenuAction("删除", destructive = true) {
                    scope.launch {
                        var removed: String? = null
                        app.settings.updateHomeBg { pr -> pr.delete(v).also { removed = it.second }.first }
                        com.moonlight.feed.data.StoredCopies.delete(removed, java.io.File(ctx.filesDir, "avatars"))
                        MoonToaster.show("已删除", Icons.Outlined.CheckCircle)
                    }
                },
            ))
        },
        adjustHomeBg = { v ->
            val slot = s.homeBgPrefs().slot(v)
            if (slot != null) {
                val cfg = ctx.resources.configuration
                com.moonlight.feed.ui.design.FocusEditing.open(com.moonlight.feed.ui.design.FocusRequest(
                    slot.path, v, 0, "调整背景位置", slot.focus ?: com.moonlight.feed.data.MediaFocus.DEFAULT,
                    aspect = cfg.screenWidthDp.toFloat() / cfg.screenHeightDp.coerceAtLeast(1),
                ) { f -> scope.launch { app.settings.updateHomeBg { it.withFocus(v, f) } } })
            }
        },
        setMenuSelect = { v -> scope.launch { app.settings.setMenuSelect(v) } },
        setRingAnim = { v -> scope.launch { app.settings.setRingAnim(v) } },
        setRingAnimSet = { v -> scope.launch { app.settings.setRingAnimSet(v) } },
        previewRingAnim = { st -> overlay.showMenu(ringAnimPreview(st), fromLongPress = false) },
        setAppIcon = { i ->
            if (i != appIcon) {
                com.moonlight.feed.data.AppIconSwitcher.apply(ctx, i)
                appIcon = i
                MoonToaster.show("桌面图标已换成「${i.label}」· 部分桌面需要几秒才刷新", Icons.Outlined.CheckCircle)
            }
        },
        makePhotoIcon = { r ->
            overlay.showMenu(MenuSpec(anchor = r, title = "用照片做桌面图标", actions = listOf(listOf(
                MenuAction("照片", Icons.Outlined.Image) { iconPhotoPicker.launch(arrayOf("image/*")) },
                MenuAction("视频（选一帧）", Icons.Outlined.Movie, subtitle = "安卓不支持动态图标") { iconVideoPicker.launch(arrayOf("video/*")) },
            ))))
        },
        setHomeBgDim = { v -> scope.launch { app.settings.setHomeBgDim(v) } },
        setHomeBgAll = { v -> scope.launch { app.settings.setHomeBgAllPages(v) } },
        openFriends = { com.moonlight.feed.ui.AppBus.goTab.tryEmit(4); com.moonlight.feed.ui.AppBus.send(com.moonlight.feed.ui.AppEvent.SOCIAL_FRIENDS) },
        exportSocial = { exportLauncher.launch("月淡-朋友圈备份-" + java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.CHINA).format(java.util.Date()) + ".json") },
        importSocial = { importLauncher.launch(arrayOf("application/json", "text/*", "*/*")) },
        editServerUrl = { edit("聊天服务器地址", s.chatServerUrl, "例如 https://chat.example.com（仅保存，暂不连接）") { v -> app.settings.setChatServerUrl(v.lines().firstOrNull().orEmpty()) } },
        openSources = onOpenSources,
        setReveal = { v -> scope.launch { app.settings.setRevealOn(v) } },
        setRevealSpeed = { v -> scope.launch { app.settings.setRevealSpeed(v) } },
        setVideoDisplay = { v -> scope.launch { app.settings.setVideoDisplay(v) } },
        setMenuBackdrop = { v -> scope.launch { app.settings.setMenuBackdrop(v) } },
        pickSearchCount = { rect ->
            Haptics.open(view)
            overlay.showMenu(MenuSpec(anchor = rect, title = "每次搜索显示", list = true, actions = listOf(listOf(6, 10, 15, 20).map { n ->
                MenuAction("$n 条", checked = n == s.searchCount) { scope.launch { app.settings.setSearchCount(n) } }
            })))
        },
        setTheme = { m -> scope.launch { app.settings.setThemeMode(m.name) } },
        setMuted = { v -> scope.launch { app.settings.setMuted(v) } },
        setLoop = { v -> scope.launch { app.settings.setLoop(v) } },
        setShuffle = { v -> scope.launch { app.settings.setShuffle(v) } },
        pickImageSeconds = { rect ->
            Haptics.open(view)
            overlay.showMenu(
                MenuSpec(
                    anchor = rect, title = "图片停留", list = true,
                    actions = listOf(ImageDwell.OPTIONS.map { v ->
                        MenuAction(
                            if (v == 0) "永久" else ImageDwell.label(v), if (v == 0) Icons.Outlined.AllInclusive else Icons.Outlined.Timer,
                            checked = v == s.imageSeconds, subtitle = if (v == 0) "不自动切换，滑动才换下一张" else null,
                        ) { scope.launch { app.settings.setImageSeconds(v) } }
                    }),
                )
            )
        },
        setResume = { v -> scope.launch { app.settings.setResume(v) } },
        setResumeOnReturn = { v -> scope.launch { app.settings.setResumeOnReturn(v) } },
        setShowProgress = { v -> scope.launch { app.settings.setShowProgressWhilePlaying(v) } },
        togglePlatform = { id, on -> scope.launch { app.settings.togglePlatform(id, on) } },
        onPlatformLongPress = { id, rect ->
            Haptics.open(view)
            val on = id in s.enabledPlatforms
            val keyed = id == PlatformId.YOUTUBE || id == PlatformId.PEXELS || id == PlatformId.PIXABAY
            overlay.showMenu(
                MenuSpec(
                    anchor = rect,
                    title = PlatformId.label(id),
                    actions = listOf(
                        listOf(
                            MenuAction(if (on) "暂停" else "启用", if (on) Icons.Outlined.PauseCircle else Icons.Outlined.PlayCircle) {
                                scope.launch { app.settings.togglePlatform(id, !on) }
                            },
                            MenuAction("立即刷新", Icons.Outlined.Refresh, subtitle = "只从该平台随机加入") { refreshPlatform(id) },
                            if (keyed) MenuAction("编辑 Key", Icons.Outlined.Key) { editKeys() }
                            else MenuAction("编辑关键词", Icons.Outlined.Edit) { editKeywords() },
                        ),
                        listOf(
                            MenuAction("移除", Icons.Outlined.RemoveCircleOutline, destructive = true, subtitle = "停用并从首页隐藏其内容") {
                                overlay.confirm(
                                    "移除 ${PlatformId.label(id)}？", "停用该平台，并从首页隐藏已加入的内容（可在媒体库“已隐藏”中恢复）",
                                    listOf(MenuAction("移除", destructive = true) {
                                        scope.launch { app.settings.togglePlatform(id, false) }
                                        app.library.items.value.filter { it.resolvedPlatform() == id }.forEach { e -> app.library.update(e.id) { it.copy(hidden = true) } }
                                        MoonToaster.show("已移除 ${PlatformId.label(id)}", Icons.Outlined.RemoveCircleOutline)
                                    }),
                                )
                            },
                        ),
                    ),
                )
            )
        },
        setRandom = { v -> scope.launch { app.settings.setRandomOn(v) } },
        editKeywords = editKeywords,
        editExclude = { edit("排除词", s.exclude.joinToString("\n"), "每行一个；作者名也可") { d -> app.settings.setExclude(d.lines().map { it.trim() }.filter { it.isNotBlank() }.toSet()) } },
        editKeys = editKeys,
        setAutoSave = { v -> scope.launch { app.settings.setAutoSaveGallery(v) } },
        restoreHidden = {
            val hidden = app.library.items.value.filter { it.hidden }
            hidden.forEach { e -> app.library.update(e.id) { it.copy(hidden = false) } }
            MoonToaster.show("已恢复 ${hidden.size} 项", Icons.Outlined.Restore)
        },
        showGestures = { overlay.showSheet("手势") { _ -> GestureGuide() } },
        setHoldPresent = { v -> scope.launch { app.settings.setHoldPresent(v) } },
        setTabDock = { v -> scope.launch { app.settings.setTabDock(v) } },
        setPortraitPlayback = { v -> scope.launch { app.settings.setPortraitPlayback(v) } },
        setVirtualFriends = { v -> scope.launch { app.settings.setVirtualFriends(v) } },
        setVirtualInterval = { v -> scope.launch { app.settings.setVirtualInterval(v) } },
        setVirtualProvider = { v -> scope.launch { app.settings.setVirtualProvider(v) } },
        setVirtualKey = { v -> scope.launch { app.settings.setVirtualKey(v) } },
    )
    SettingsContent(s, actions, hiddenCount = app.library.items.collectAsState().value.count { it.hidden }, userSources = userSources, social = social, appIcon = appIcon)
}

// =================================================================== stateless

@Composable
fun SettingsContent(s: SettingsRepository.Snapshot, a: SettingsActions, hiddenCount: Int = 0, userSources: List<com.moonlight.feed.data.UserSource> = emptyList(), social: com.moonlight.feed.data.social.SocialState? = null, appIcon: com.moonlight.feed.data.AppIcon = com.moonlight.feed.data.AppIcon.DEFAULT) {
    val mode = runCatching { ThemeMode.valueOf(s.themeMode) }.getOrDefault(ThemeMode.SYSTEM)
    val v5 = remember(s.v5, s.momentsAutoplay) { s.v5Prefs() }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenHeader("设置")

        SettingsGroup("外观") {
            Column(Modifier.padding(14.dp)) {
                Segmented(listOf("跟随系统", "深色", "浅色"), com.moonlight.feed.data.segmentIndex(THEME_SEGMENTS, mode)) {
                    a.setTheme(THEME_SEGMENTS[it])
                }
            }
            SettingsDivider()
            val bd = com.moonlight.feed.data.MenuBackdrop.of(s.menuBackdrop)
            SegmentRow(
                "菜单背景", when (bd) {
                    com.moonlight.feed.data.MenuBackdrop.CLEAR -> "不模糊不压暗，文字自带阴影"
                    com.moonlight.feed.data.MenuBackdrop.LIGHT -> "轻微模糊，背后内容仍清楚"
                    com.moonlight.feed.data.MenuBackdrop.BLUR -> "长按菜单打开时模糊背景"
                },
                Icons.Outlined.BlurOn, Color(0xFF9CC7FF),
                com.moonlight.feed.data.MenuBackdrop.entries.map { it.label }, bd.ordinal,
            ) { a.setMenuBackdrop(com.moonlight.feed.data.MenuBackdrop.entries[it].name) }
            SettingsDivider()
            val sel = com.moonlight.feed.data.MenuSelect.of(s.menuSelect)
            SegmentRow(
                "长按菜单选择方式", when (sel) {
                    com.moonlight.feed.data.MenuSelect.TAP -> "长按打开，松手后菜单保持打开，再点按选择"
                    com.moonlight.feed.data.MenuSelect.DRAG -> "按住滑到按钮（高亮 + 轻震），松手即选择"
                },
                Icons.Outlined.TouchApp, Color(0xFFF2A65A),
                com.moonlight.feed.data.MenuSelect.entries.map { it.label }, sel.ordinal,
            ) { a.setMenuSelect(com.moonlight.feed.data.MenuSelect.entries[it].name) }
            SettingsDivider()
            YuedanAppearanceRows(s, a)
            SettingsDivider()
            RingAnimRows(s, a)
            SettingsDivider()
            StarSpeedRows(v5, a.updateV5)
            SettingsDivider()
            DeleteAnimRows(v5, a.updateV5)
            SettingsDivider()
            ButtonSkinEntryRow(s, a)
            SettingsDivider()
            SettingsRow("手势说明", subtitle = if (com.moonlight.feed.data.MenuSelect.of(s.menuSelect) == com.moonlight.feed.data.MenuSelect.TAP) "点按 = 主要操作 · 双击 = 喜欢 · 长按 = 4 个常用 + ···（松开后再点选）" else "点按 = 主要操作 · 双击 = 喜欢 · 长按 = 4 个常用 + ···（按住滑动选择）", icon = Icons.Outlined.TouchApp, iconTint = Color(0xFFF2A65A), onClick = a.showGestures)
        }

        AppIconGroup(appIcon, a)

        HomeBackgroundGroup(s, a)

        SettingsGroup("播放") {
            YuedanPlaybackRow(s, a)
            SettingsDivider()
            val vd = com.moonlight.feed.data.VideoFit.of(s.videoDisplay)
            SegmentRow(
                "视频显示", when (vd) {
                    com.moonlight.feed.data.VideoDisplay.SMART -> "不裁内容；接近屏幕比例（裁切 <8%）时铺满"
                    com.moonlight.feed.data.VideoDisplay.FIT -> "始终完整显示，留黑边"
                    com.moonlight.feed.data.VideoDisplay.FILL -> "始终铺满屏幕，可能裁掉边缘"
                } + " · 双指捏合单独切换",
                Icons.Outlined.AspectRatio, Color(0xFF7FD1AE),
                com.moonlight.feed.data.VideoDisplay.entries.map { it.label }, vd.ordinal,
            ) { a.setVideoDisplay(com.moonlight.feed.data.VideoDisplay.entries[it].name) }
            SettingsDivider()
            Toggle("静音", "信息流默认静音", Icons.AutoMirrored.Outlined.VolumeOff, s.muted, a.setMuted, Color(0xFFFF8A7A))
            SettingsDivider()
            Toggle("循环播放", "视频播完重播", Icons.Outlined.Repeat, s.loop, a.setLoop, Color(0xFF7FD1AE))
            SettingsDivider()
            Toggle("随机顺序", "信息流打乱", Icons.Outlined.Shuffle, s.shuffle, a.setShuffle, Color(0xFFB18CFF))
            SettingsDivider()
            val dwellAnchor = remember { AnchorState() }
            Box(Modifier.anchor(dwellAnchor)) {
                SettingsRow(
                    "图片停留",
                    subtitle = if (ImageDwell.autoAdvance(s.imageSeconds)) "自动滑到下一项" else "不自动切换 · 滑动换下一张",
                    trailing = ImageDwell.label(s.imageSeconds), icon = Icons.Outlined.Image, iconTint = Color(0xFF8FA8FF),
                    onClick = { a.pickImageSeconds(dwellAnchor.rect) },
                )
            }
            SettingsDivider()
            Toggle("记住位置", "从上次的位置继续", Icons.Outlined.Bolt, s.resume, a.setResume, Color(0xFFF2C46A))
            Toggle("返回时继续播放", "离开 App 一律暂停；回来时只继续之前在播的", Icons.Outlined.Bolt, s.resumeOnReturn, a.setResumeOnReturn, Color(0xFF8FB8F2))
            SettingsDivider()
            val animOff = com.moonlight.feed.ui.design.systemAnimationsOff(androidx.compose.ui.platform.LocalContext.current)
            Toggle("模糊渐显动画", if (animOff) "系统已关闭动画，自动跳过" else "内容出现时由模糊变清晰", Icons.Outlined.BlurOn, s.revealOn, a.setReveal, Color(0xFFB9A7FF))
            androidx.compose.animation.AnimatedVisibility(s.revealOn) {
                Column(Modifier.padding(start = 56.dp, end = 16.dp, top = 2.dp, bottom = 12.dp)) {
                    val speeds = com.moonlight.feed.ui.design.RevealSpeed.entries
                    Segmented(speeds.map { it.label }, com.moonlight.feed.data.segmentIndex(speeds.map { it.name }, s.revealSpeed, fallback = speeds.indexOf(com.moonlight.feed.ui.design.RevealSpeed.STANDARD))) { i -> a.setRevealSpeed(speeds[i].name) }
                }
            }
            SettingsDivider()
            Toggle("播放进度线", "沉浸播放时底部细线", Icons.Outlined.LinearScale, s.showProgressWhilePlaying, a.setShowProgress, Color(0xFF6FB7E8))
        }

        YuedanVirtualGroup(s, a)

        SettingsGroup("网址源", footer = "手动添加任意公开网页；随机抓取同站图片与视频并插入信息流。遵守 robots.txt，不登录、不绕过验证。") {
            SettingsRow(
                "网址源",
                subtitle = if (userSources.isEmpty()) "添加网址 · 随机抓取" else "${userSources.size} 个 · ${userSources.count { it.enabled }} 个启用" +
                    (userSources.maxOfOrNull { it.lastFetchAt }?.takeIf { it > 0 }?.let { " · 上次 " + com.moonlight.feed.ui.sources.relTime(System.currentTimeMillis(), it) } ?: ""),
                trailing = if (userSources.isEmpty()) null else "${userSources.sumOf { it.totalCount }} 项",
                icon = Icons.Outlined.Language, iconTint = Color(0xFFE8C77A),
                onClick = a.openSources,
            )
        }

        SocialSettingsGroups(s, a, social)

        TimedHoldGroup(v5, a.updateV5)

        RecordGroup(v5, a.updateV5)

        SettingsGroup("内容源", footer = "长按平台：暂停 / 立即刷新 / 编辑 / 移除") {
            PlatformId.ALL_TOGGLEABLE.forEachIndexed { i, id ->
                if (i > 0) SettingsDivider()
                val anchor = remember(id) { AnchorState() }
                Box(Modifier.anchor(anchor)) {
                    SettingsRow(
                        PlatformId.label(id),
                        subtitle = platformSubtitle(id, s),
                        icon = Icons.Outlined.Public,
                        iconTint = platformTint(id),
                        onClick = { a.togglePlatform(id, id !in s.enabledPlatforms) },
                        onLongClick = { a.onPlatformLongPress(id, anchor.rect) },
                        trailingContent = { MoonSwitch(id in s.enabledPlatforms, { on -> a.togglePlatform(id, on) }) },
                    )
                }
            }
        }

        SettingsGroup("随机发现") {
            Toggle("随机模式", "多平台关键词发现", Icons.Outlined.Casino, s.randomOn, a.setRandom, Color(0xFFB18CFF))
            SettingsDivider()
            val countAnchor = remember { AnchorState() }
            Box(Modifier.anchor(countAnchor)) {
                SettingsRow("搜索显示条数", subtitle = "发现页搜索每批随机抽取", trailing = "${s.searchCount} 条", icon = Icons.Outlined.Search, iconTint = Color(0xFF6FB7E8), onClick = { a.pickSearchCount(countAnchor.rect) })
            }
            SettingsDivider()
            SettingsRow("关键词", subtitle = s.keywords.take(4).joinToString(" · ").ifBlank { "未设置" }, trailing = "${s.keywords.size}", icon = Icons.Outlined.Tag, onClick = a.editKeywords)
            SettingsDivider()
            SettingsRow("排除词", subtitle = s.exclude.take(3).joinToString(" · ").ifBlank { "无" }, trailing = "${s.exclude.size}", icon = Icons.Outlined.Block, onClick = a.editExclude)
            SettingsDivider()
            SettingsRow(
                "API Key", icon = Icons.Outlined.Key, onClick = a.editKeys,
                subtitle = listOf("YouTube" to s.youtubeKey, "Pexels" to s.pexelsKey, "Pixabay" to s.pixabayKey)
                    .joinToString("  ") { (n, k) -> if (k.isNotBlank()) "$n ✓" else "$n ·" },
            )
        }

        SettingsGroup("相册与内容") {
            Toggle("自动保存到相册", "可下载媒体写入相册；嵌入源仅封面", Icons.Outlined.PhotoLibrary, s.autoSaveGallery, a.setAutoSave, Color(0xFF7FD1AE))
            SettingsDivider()
            SettingsRow("保存位置", subtitle = GallerySaver.locationHint(), icon = Icons.Outlined.Save, chevron = false)
            SettingsDivider()
            SettingsRow("恢复已隐藏内容", subtitle = if (hiddenCount > 0) "$hiddenCount 项已隐藏" else "没有隐藏的内容", icon = Icons.Outlined.Visibility, onClick = if (hiddenCount > 0) a.restoreHidden else null)
        }

        SettingsGroup("关于", footer = "仅使用官方嵌入与公开 API / 直链；不破解 DRM、不扒流、不绕过签名、不抓取登录 Cookie。抖音 / TikTok / 快手 / 小红书 / 西瓜 / 优酷 / 爱奇艺 / 腾讯视频不支持。") {
            SettingsRow("月淡", subtitle = "v6.0 · 对齐 iOS 1.43 · 多源信息流 · 朋友圈", icon = Icons.Outlined.Info, chevron = false)
        }
        Spacer(Modifier.height(120.dp))
    }
}

@Composable
internal fun Toggle(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, checked: Boolean, onChange: (Boolean) -> Unit, tint: Color? = null) {
    SettingsRow(
        title, subtitle = subtitle, icon = icon, iconTint = tint,
        onClick = { onChange(!checked) },
        trailingContent = { MoonSwitch(checked, onChange) },
    )
}

val THEME_SEGMENTS = listOf(ThemeMode.SYSTEM, ThemeMode.DARK, ThemeMode.LIGHT)

/** Dim "即将推出" capsule for not-yet-available rows (disabled, no tap). */
@Composable
private fun SoonBadge() {
    val c = LocalMoonColors.current
    Text("即将推出", color = c.textDim, style = MaterialTheme.typography.labelSmall,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(c.hairline).padding(horizontal = 9.dp, vertical = 4.dp))
}

/** iOS segmented control with a spring-sliding thumb. Out-of-range [selected] is clamped (never hides a valid choice). */
@Composable
fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    val selected = selected.coerceIn(0, (options.size - 1).coerceAtLeast(0))
    val c = LocalMoonColors.current
    val view = LocalView.current
    // Large font scale: if the labels can't fit one row, fall back to wrapping pills (never clipped text).
    val measurer = androidx.compose.ui.text.rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelLarge
    val density = androidx.compose.ui.platform.LocalDensity.current
    val widest = remember(options, labelStyle, density.fontScale) { options.maxOfOrNull { measurer.measure(it, labelStyle).size.width } ?: 0 }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val need = with(density) { widest.toDp() } + 16.dp
        if (need * options.size > maxWidth - 4.dp) {
            SegmentedWrap(options, selected, onSelect)
            return@BoxWithConstraints
        }
    val segH = (36.dp * density.fontScale.coerceAtLeast(1f)).coerceAtMost(52.dp)
    BoxWithConstraints(
        Modifier.fillMaxWidth().height(segH).clip(RoundedCornerShape(11.dp))
            .background(if (c.dark) Color(0x1FFFFFFF) else Color(0x14000000)).padding(2.dp)
    ) {
        val w = maxWidth / options.size
        val x by animateDpAsState(w * selected, spring(0.72f, 450f), label = "seg")
        Box(Modifier.offset(x = x).width(w).fillMaxHeight().clip(RoundedCornerShape(9.dp)).background(if (c.dark) Color(0x40FFFFFF) else Color.White))
        Row(Modifier.fillMaxSize()) {
            options.forEachIndexed { i, o ->
                Box(
                    Modifier.weight(1f).fillMaxHeight().moonPress({ Haptics.tick(view); onSelect(i) }, pressedScale = 0.94f, highlightShape = null, hapticOnClick = false),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(o, color = if (i == selected) c.text else c.textDim, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center, maxLines = 1, softWrap = false)
                }
            }
        }
    }
    }
}

/** [Segmented] at large font scale: the same single choice as wrapping pills (2+ rows). */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun SegmentedWrap(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    val view = LocalView.current
    androidx.compose.foundation.layout.FlowRow(
        Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEachIndexed { i, o -> com.moonlight.feed.ui.design.GlassPill(o, { Haptics.tick(view); onSelect(i) }, accent = i == selected) }
    }
}

@Composable
private fun EditSheet(initial: String, hint: String, onSave: (String) -> Unit) {
    val c = LocalMoonColors.current
    var text by remember { mutableStateOf(initial) }
    Text(hint, color = c.textDim, style = MaterialTheme.typography.bodySmall)
    Spacer(Modifier.height(10.dp))
    Box(Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(18.dp)).background(c.hairline).padding(14.dp)) {
        BasicTextField(
            value = text, onValueChange = { text = it },
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = c.text),
            cursorBrush = SolidColor(c.accent), modifier = Modifier.fillMaxSize(),
        )
    }
    Spacer(Modifier.height(14.dp))
    Text(
        "保存", color = c.onAccent, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().moonPress({ onSave(text) }, highlightShape = RoundedCornerShape(18.dp))
            .clip(RoundedCornerShape(18.dp)).background(c.accent).padding(vertical = 15.dp),
    )
    Spacer(Modifier.height(8.dp))
}

@Composable
fun GestureGuide() {
    val c = LocalMoonColors.current
    val rows = gestureGuide(com.moonlight.feed.ui.design.MenuStyle.select)
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(c.hairline)) {
        rows.forEachIndexed { i, (k, v) ->
            if (i > 0) Box(Modifier.fillMaxWidth().padding(start = 16.dp).height(0.6.dp).background(c.hairline))
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 11.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(k, color = c.accent, style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(76.dp))
                Text(v, color = c.text, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
    Spacer(Modifier.height(8.dp))
    Text("没有“长按 2 倍速”：倍速在圆环菜单 › 倍速 中。图片停留可选“永久”，图片不会自动切换。", color = c.textDim, style = MaterialTheme.typography.bodySmall)
    Spacer(Modifier.height(8.dp))
}

/** 朋友圈与消息 + 聊天服务器 (server connect is a placeholder: 即将推出). */
val AUTOPLAY_MODES = listOf("ALWAYS", "WIFI", "OFF")
val HOME_BG_KINDS = com.moonlight.feed.data.HomeBgKind.entries.toList()

/** 首页背景: 跟随主题 (default) · 纯黑 · 照片 · 视频, + 模糊变暗 + 应用到所有页面. */
@Composable
fun HomeBackgroundGroup(s: SettingsRepository.Snapshot, a: SettingsActions) {
    val c = LocalMoonColors.current
    val prefs = s.homeBgPrefs()
    val kind = prefs.kind
    val media = kind == com.moonlight.feed.data.HomeBgKind.PHOTO || kind == com.moonlight.feed.data.HomeBgKind.VIDEO
    SettingsGroup("首页背景", footer = "照片和视频各自保存（含位置与模糊设置），切换选项只改变使用哪一个；只有点垃圾桶才会删除 App 内的副本。") {
        Column(Modifier.padding(14.dp)) {
            Segmented(HOME_BG_KINDS.map { it.label }, com.moonlight.feed.data.segmentIndex(HOME_BG_KINDS, kind)) { a.setHomeBg(HOME_BG_KINDS[it]) }
            Spacer(Modifier.height(12.dp))
            // saved picks: tap to use (instantly), trash to delete the stored copy
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SavedBgTile("照片", prefs.photo, false, kind == com.moonlight.feed.data.HomeBgKind.PHOTO,
                    onUse = { a.setHomeBg(com.moonlight.feed.data.HomeBgKind.PHOTO) }, onDelete = { a.deleteHomeBg(false) }, modifier = Modifier.weight(1f))
                SavedBgTile("视频", prefs.video, true, kind == com.moonlight.feed.data.HomeBgKind.VIDEO,
                    onUse = { a.setHomeBg(com.moonlight.feed.data.HomeBgKind.VIDEO) }, onDelete = { a.deleteHomeBg(true) }, modifier = Modifier.weight(1f))
            }
        }
        androidx.compose.animation.AnimatedVisibility(media) {
            val isVideo = kind == com.moonlight.feed.data.HomeBgKind.VIDEO
            Column {
                SettingsDivider()
                SettingsRow(
                    if (isVideo) "更换视频" else "更换照片",
                    subtitle = "只替换这一个；另一种会保留",
                    icon = Icons.Outlined.Image, iconTint = Color(0xFF8FA8FF), onClick = { a.pickHomeBg(isVideo) },
                )
                SettingsDivider()
                SettingsRow("调整位置", subtitle = "拖动 · 双指缩放（按全屏比例）", icon = Icons.Outlined.CenterFocusStrong, iconTint = Color(0xFF7FD1AE), onClick = { a.adjustHomeBg(isVideo) })
                SettingsDivider()
                Toggle("模糊并变暗", "让内容和文字更清楚 · 照片和视频分别记住", Icons.Outlined.BlurOn, prefs.activeDim, a.setHomeBgDim, Color(0xFFB9A7FF))
            }
        }
        SettingsDivider()
        Toggle("应用到所有页面", if (kind == com.moonlight.feed.data.HomeBgKind.THEME) "选择纯黑、照片或视频后可用" else "发现、朋友圈、我、设置也用这个背景",
            Icons.Outlined.Wallpaper, prefs.allPages, a.setHomeBgAll, c.accent)
    }
}

/** A saved background: thumbnail (or 「未选择」), active ring, and a trash button. */
@Composable
private fun SavedBgTile(
    label: String, slot: com.moonlight.feed.data.BgSlot?, video: Boolean, active: Boolean,
    onUse: () -> Unit, onDelete: () -> Unit, modifier: Modifier = Modifier,
) {
    val c = LocalMoonColors.current
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier
            .height(92.dp)
            .clip(shape)
            .background(c.hairline)
            .then(if (active) Modifier.border(2.dp, c.accent, shape) else Modifier)
            .moonPress(onUse, pressedScale = 0.97f, highlightShape = shape),
    ) {
        if (slot != null) {
            com.moonlight.feed.ui.design.ProfileMedia(slot.path, video, Modifier.fillMaxSize(), slot.focus) { }
            Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Brush.verticalGradient(0.45f to Color.Transparent, 1f to Color.Black.copy(0.6f))))
            Box(
                Modifier.align(Alignment.TopEnd).padding(4.dp).size(36.dp).clip(CircleShape)
                    .moonPress(onDelete, highlightShape = CircleShape, highlight = Color.White.copy(0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(28.dp).clip(CircleShape).background(Color.Black.copy(0.45f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.Delete, "删除$label", tint = Color.White, modifier = Modifier.size(16.dp))
                }
            }
            Text(
                (if (active) "✓ " else "") + label + (if (active) " · 使用中" else " · 点按使用"),
                color = Color.White, style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.align(Alignment.BottomStart).padding(10.dp),
            )
        } else {
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(if (video) Icons.Outlined.Movie else Icons.Outlined.Image, null, tint = c.textDim, modifier = Modifier.size(22.dp))
                Spacer(Modifier.height(4.dp))
                Text("$label · 未选择", color = c.textDim, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
fun SocialSettingsGroups(s: SettingsRepository.Snapshot, a: SettingsActions, social: com.moonlight.feed.data.social.SocialState?) {
        SettingsGroup("朋友圈与消息", footer = "好友、动态与聊天只保存在本机。导出为 JSON 可随时恢复或迁移。") {
            // v5.0: several inline windows at once; tap = play/pause, long-press = full-screen player
            MomentsAutoplayRows(remember(s.v5, s.momentsAutoplay) { s.v5Prefs() }, social, a.updateV5)
            SettingsDivider()
            Toggle("动态头像与封面", "视频头像、封面、背景静音循环；关闭或省电时只显示首帧", Icons.Outlined.Movie, s.loopVideos, a.setLoopVideos, Color(0xFFE59AC8))
            SettingsDivider()
            SettingsRow(
                "好友", subtitle = social?.let { "${it.friends.size} 位虚拟好友 · ${it.moments.size} 条动态 · ${it.conversations.size} 个对话" } ?: "虚拟好友",
                icon = Icons.Outlined.People, iconTint = Color(0xFF8FD18B), onClick = a.openFriends,
            )
            SettingsDivider()
            SettingsRow("导出备份", subtitle = "JSON · 不含图片文件本身", icon = Icons.Outlined.Upload, iconTint = Color(0xFF6FB7E8), onClick = a.exportSocial)
            SettingsDivider()
            SettingsRow("导入备份", subtitle = "合并或替换", icon = Icons.Outlined.Download, iconTint = Color(0xFF6FB7E8), onClick = a.importSocial)
        }

        SettingsGroup("聊天服务器", footer = "现在所有对话都在本机离线进行。之后可连接自建服务器，让月淡成为真正的聊天工具——协议草案见项目 docs/CHAT_PROTOCOL.md。") {
            SettingsRow(
                "服务器地址", subtitle = s.chatServerUrl.ifBlank { "未设置" }, icon = Icons.Outlined.Dns, iconTint = Color(0xFFB59AF5), onClick = a.editServerUrl,
            )
            SettingsDivider()
            SettingsRow("连接", subtitle = "WebSocket 实时同步 · 已读回执", icon = Icons.Outlined.Link, iconTint = Color(0xFFB59AF5), chevron = false) { SoonBadge() }
            SettingsDivider()
            SettingsRow("账号", subtitle = "未登录 · 真实账号可与虚拟好友共存", icon = Icons.Outlined.AccountCircle, iconTint = Color(0xFFB59AF5), chevron = false) { SoonBadge() }
            SettingsDivider()
            SettingsRow("当前模式", trailing = "本机", subtitle = "消息状态：✓ 已存本机 · ✓✓ 对方已读（虚拟）", icon = Icons.Outlined.PhoneAndroid, iconTint = Color(0xFF8E9BB8), chevron = false)
        }
}


/** A labelled row with a segmented choice underneath (视频显示 / 菜单背景). */
@Composable
fun SegmentRow(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color, options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Column {
        SettingsRow(title, subtitle = subtitle, icon = icon, iconTint = tint, chevron = false)
        Column(Modifier.padding(start = 56.dp, end = 16.dp, bottom = 12.dp)) { Segmented(options, selected, onSelect) }
    }
}


/** 手势说明 rows for the chosen 长按菜单选择方式 (also checked by tests). */
fun gestureGuide(mode: com.moonlight.feed.data.MenuSelect): List<Pair<String, String>> = listOf<Any>(
        "原则" to "点按 = 这个东西最主要的一件事 · 双击 = 喜欢 / 赞 · 长按 = 最多 4 个常用操作 + ···（其余全部）",
        "点按" to "视频：播放 / 暂停 · 图片：显示 / 隐藏控件 · 缩略图 / 封面 / 朋友圈媒体：打开播放 · 头像：打开主页 · 标签：切换",
        "双击" to "首页：喜欢（爱心迸发）· 朋友圈：赞（不会取消赞）",
        "长按" to "在手指周围展开圆环：最多 4 个按钮，只放这个东西最常用的操作；删除永远不在圆环上",
        "···" to "圆环中心的 ··· 打开毛玻璃底部面板：所有其他操作按 播放 / 分享与保存 / 整理 / 危险 分组，下滑关闭",
        MENU_SELECT_ROWS,
        "子选项" to "倍速 · 停留时间 · 分享·保存 · 筛选 等在圆环外侧展开一小段弧，点一下选择；再点父按钮收起",
        "上下滑" to "切换上一条 / 下一条",
        "左滑" to "首页内容：详情卡（来源、大小、日期、标签）",
        "双指" to "图片：缩放 · 视频：张开 = 铺满，捏合 = 完整显示（只影响这一条）",
        "右边缘" to "返回（系统手势）",
        "长按标签" to "整个页面的选项：首页 = 筛选（全部 / 本地 / 在线）· 随机一条 · 重新洗牌 · 回到顶部",
        "计数" to "首页右上角 1/22：点按 = 跳转 / 拖动定位 · 长按 = 首页圆环",
        "头像 / 封面" to "点按 = 全屏播放（未设置时打开菜单）· 长按 = 更换 / 调整位置 / 播放 / 恢复默认",
        "朋友圈视频" to "点按 = 进入播放页面 · 长按 = 播放/暂停 · 进入播放页面 · 声音 · 保存",
        "朋友圈动态" to "长按 = 赞 · 踩 · 评论 · 转发到聊天；编辑 / 复制 / 改发布者 / 删除 在 ··· 里",
        "库" to "点按缩略图 = 打开 · 长按 = 收藏 · 发朋友圈 · 分享·保存 · 多选",
        "消息" to "长按消息 = 复制 · 回复 · 改发言人 · 撤回",
        "● 拍摄" to "点按打开相机；长按 = 拍照 · 录像 · 导入 · 发朋友圈",
        "底栏" to "手指从底栏开始：横向拖动切换页面；静止长按打开该按钮的圆环，不会顺手切页。内容画在底栏下面时，落在底栏上的手指仍归底栏",
        "滚动" to "手指一移动，长按就失败；快速滑动保持为滚动。列表还在拖动或减速时不开始长按。点按和长按互斥",
        "菜单样式" to "弹出菜单（默认）/ 跟手圆形玻璃 / 水滴融合（可选，默认关闭）/ 玻璃气泡分离。旋转、荷花、星轨、月光仍是打开动画",
        "发现" to "右上角切换单列或双列，横幅大小可调。下拉刷新。首页可随机添加 10 / 15 / 20 条",
        "动画" to "设置 › 外观 › 长按动画：旋转 / 荷花（花苞绽放）/ 星轨（按钮沿弧线从远处飞来）/ 月光（全屏暗下，只照亮按钮）/ 随机",
).flatMap { r -> if (r == MENU_SELECT_ROWS) menuSelectRows(mode) else listOf(@Suppress("UNCHECKED_CAST") (r as Pair<String, String>)) }

private val MENU_SELECT_ROWS = Any()

private fun menuSelectRows(mode: com.moonlight.feed.data.MenuSelect): List<Pair<String, String>> = when (mode) {
    com.moonlight.feed.data.MenuSelect.TAP -> listOf(
        "松开" to "长按只负责打开圆环；松开手指不会选中任何按钮（即使手指正停在按钮上），圆环保持打开",
        "再点按" to "点按圆环里的按钮执行；带子选项的按钮先在外侧展开一小段弧，再点一下选择",
        "收起" to "点空白处或按返回键关闭菜单",
        "选择方式" to "设置 › 外观 › 长按菜单选择方式：点击（当前）/ 拖动",
    )
    com.moonlight.feed.data.MenuSelect.DRAG -> listOf(
        "按住滑动" to "长按后不松手，滑到某个按钮（放大 + 轻震）后松开即执行；在空白处松开 = 不选，圆环保持打开",
        "子选项弧" to "滑到带子选项的按钮松开会在外侧展开一小段弧，再点一下选择",
        "松开再点" to "也可以先在空白处松手，再点按圆环里的按钮",
        "收起" to "点空白处或按返回键关闭菜单",
        "选择方式" to "设置 › 外观 › 长按菜单选择方式：点击 / 拖动（当前）",
    )
}

/** Default (点击) guide. */
val GESTURE_GUIDE: List<Pair<String, String>> get() = gestureGuide(com.moonlight.feed.data.MenuSelect.TAP)


/** 应用图标: preset launcher icons (activity-alias) + 用照片做桌面图标 (pinned shortcut). */
@Composable
fun AppIconGroup(current: com.moonlight.feed.data.AppIcon, a: SettingsActions) {
    val c = LocalMoonColors.current
    SettingsGroup("应用图标", footer = "预设图标会替换桌面上的月淡图标。「用照片做桌面图标」是另外添加一个主屏幕快捷方式，能否添加由桌面应用决定；视频只能取一帧（安卓不支持动态图标）。") {
        Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            com.moonlight.feed.data.AppIcon.entries.forEach { i ->
                val on = i == current
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(18.dp))
                        .then(if (on) Modifier.border(2.dp, c.accent, RoundedCornerShape(18.dp)) else Modifier)
                        .moonPress({ a.setAppIcon(i) }, pressedScale = 0.96f, highlightShape = RoundedCornerShape(18.dp))
                        .padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    androidx.compose.foundation.Image(
                        androidx.compose.ui.res.painterResource(if (i == com.moonlight.feed.data.AppIcon.GIRL) com.moonlight.feed.R.drawable.icon_preview_girl else com.moonlight.feed.R.drawable.icon_preview_classic),
                        i.label, Modifier.size(64.dp),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text((if (on) "✓ " else "") + i.label + if (i == com.moonlight.feed.data.AppIcon.DEFAULT) "（默认）" else "",
                        color = if (on) c.accent else c.text, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        SettingsDivider()
        val anchor = remember { AnchorState() }
        Box(Modifier.anchor(anchor)) {
            SettingsRow("用照片做桌面图标", subtitle = "照片或视频的一帧 · 调整位置后添加主屏幕快捷方式（取决于桌面应用）",
                icon = Icons.Outlined.AddPhotoAlternate, iconTint = Color(0xFFF2A0C0), onClick = { a.makePhotoIcon(anchor.rect) })
        }
    }
}


/** Demo ring for 长按动画 → 试一试 (forces the style; actions do nothing). */
fun ringAnimPreview(style: com.moonlight.feed.data.RingAnim, origin: androidx.compose.ui.geometry.Offset? = null) = com.moonlight.feed.ui.design.MenuSpec(
    anchor = Rect.Zero, origin = origin, title = "预览 · ${style.label}", anim = style,
    radial = listOf(
        com.moonlight.feed.ui.design.MenuAction("收藏", Icons.Outlined.FavoriteBorder),
        com.moonlight.feed.ui.design.MenuAction("倍速", Icons.Outlined.Speed),
        com.moonlight.feed.ui.design.MenuAction("声音", Icons.AutoMirrored.Outlined.VolumeUp),
        com.moonlight.feed.ui.design.MenuAction("分享·保存", Icons.Outlined.IosShare),
    ),
    actions = emptyList(),
)

/** 长按动画: style choice (or 随机) · the pool 随机 draws from · 试一试 chips that open a demo ring per style. */
@Composable
fun RingAnimRows(s: com.moonlight.feed.data.SettingsRepository.Snapshot, a: SettingsActions, previewState: androidx.compose.foundation.lazy.LazyListState = androidx.compose.foundation.lazy.rememberLazyListState()) {
    val c = LocalMoonColors.current
    val opts = com.moonlight.feed.data.RingAnim.CHOICES
    val random = s.ringAnim == com.moonlight.feed.data.RingAnim.RANDOM
    val sel = if (random) opts.size else opts.indexOf(com.moonlight.feed.data.RingAnim.of(s.ringAnim))
    val pool = com.moonlight.feed.data.RingAnimPick.parseSet(s.ringAnimSet)
    SegmentRow(
        "长按动画", if (random) "每次打开随机换一种 · 系统关闭动画时只淡入淡出" else com.moonlight.feed.data.RingAnim.of(s.ringAnim).hint,
        Icons.Outlined.AutoAwesome, Color(0xFFF6E7B0),
        RING_ANIM_LABELS, sel,
    ) { i -> a.setRingAnim(if (i == opts.size) com.moonlight.feed.data.RingAnim.RANDOM else opts[i].name) }
    if (random) {
        Text("随机范围", color = c.textDim, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(start = 56.dp, bottom = 8.dp))
        @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
        androidx.compose.foundation.layout.FlowRow(Modifier.padding(start = 56.dp, end = 16.dp, bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            opts.forEach { st ->
                val on = st in pool
                GlassPill(st.short, { a.setRingAnimSet(if (on) (pool - st).ifEmpty { pool } else pool + st) }, accent = on)
            }
        }
    }
    Text("试一试", color = c.textDim, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(start = 56.dp, bottom = 8.dp))
    // a horizontally scrolling, snapping row with soft edge fades — every chip (incl. 随机) is reachable at any width / font scale
    val chips = remember(pool) { ringAnimPreviewChips() }
    androidx.compose.foundation.lazy.LazyRow(
        state = previewState,
        flingBehavior = androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior(previewState, androidx.compose.foundation.gestures.snapping.SnapPosition.Start),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 56.dp, end = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp).edgeFade(previewState),
    ) {
        items(chips.size) { i ->
            val (label, st) = chips[i]
            GlassPill(label, { a.previewRingAnim(st ?: com.moonlight.feed.data.RingAnimPick.pick(com.moonlight.feed.data.RingAnim.RANDOM, pool, 1f)) }, icon = Icons.Outlined.PlayArrow)
        }
    }
}

/** Segment labels, in order: 旋转 · 荷花 · 星轨 · 月光 · 随机. */
val RING_ANIM_LABELS = com.moonlight.feed.data.RingAnim.CHOICES.map { it.short } + "随机"

/** 试一试 chips: one per style + 随机 (null → draw from the enabled pool). */
fun ringAnimPreviewChips(): List<Pair<String, com.moonlight.feed.data.RingAnim?>> =
    com.moonlight.feed.data.RingAnim.CHOICES.map { it.short to it } + ("随机" to null)

/** Fades whichever horizontal edge still has content beyond it. */
fun Modifier.edgeFade(state: androidx.compose.foundation.lazy.LazyListState, width: androidx.compose.ui.unit.Dp = 28.dp): Modifier = this
    .graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val w = width.toPx()
        if (state.canScrollBackward) drawRect(Brush.horizontalGradient(listOf(Color.Transparent, Color.Black), startX = 0f, endX = w), size = androidx.compose.ui.geometry.Size(w, size.height), blendMode = androidx.compose.ui.graphics.BlendMode.DstIn)
        if (state.canScrollForward) drawRect(Brush.horizontalGradient(listOf(Color.Black, Color.Transparent), startX = size.width - w, endX = size.width), topLeft = androidx.compose.ui.geometry.Offset(size.width - w, 0f), size = androidx.compose.ui.geometry.Size(w, size.height), blendMode = androidx.compose.ui.graphics.BlendMode.DstIn)
    }
