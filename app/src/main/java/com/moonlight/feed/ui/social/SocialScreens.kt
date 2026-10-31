package com.moonlight.feed.ui.social

import androidx.compose.material.icons.outlined.CenterFocusStrong
import androidx.compose.material.icons.outlined.PlayCircle
import com.moonlight.feed.ui.design.FocusFrames
import androidx.compose.runtime.snapshotFlow

import androidx.compose.material.icons.outlined.ThumbDown

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Undo
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Reply
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material.icons.outlined.ViewModule
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalContext
import com.moonlight.feed.BiliPlayerActivity
import com.moonlight.feed.CameraActivity
import com.moonlight.feed.MoonApp
import com.moonlight.feed.data.social.Attachment
import com.moonlight.feed.data.social.Comment
import com.moonlight.feed.data.social.Contact
import com.moonlight.feed.data.social.Conversation
import com.moonlight.feed.data.social.Message
import com.moonlight.feed.data.social.MessageStatus
import com.moonlight.feed.data.social.Moment
import com.moonlight.feed.data.social.attachmentOf
import com.moonlight.feed.ui.AppBus
import com.moonlight.feed.ui.AppEvent
import com.moonlight.feed.ui.design.LocalOverlay
import com.moonlight.feed.ui.design.MenuAction
import com.moonlight.feed.ui.design.MenuSpec
import com.moonlight.feed.ui.design.MoonOverlayState
import com.moonlight.feed.ui.design.MoonToaster
import kotlinx.coroutines.launch
import java.util.Calendar

sealed interface SRoute {
    data object Timeline : SRoute
    data object Friends : SRoute
    data object Chats : SRoute
    data class Profile(val id: String) : SRoute
    data class Composer(val draft: MomentDraft, val key: Long = System.nanoTime()) : SRoute
    data class Chat(val convId: String) : SRoute
}

/** Navigation stacks per tab (survive tab switches) + shared "acting as" identity. */
object SocialNav {
    val moments = mutableStateListOf<SRoute>(SRoute.Timeline)
    val chats = mutableStateListOf<SRoute>(SRoute.Chats)
    var actingId by mutableStateOf<String?>(null)
    /** Tab bar hides while composing / chatting / commenting. */
    var immersive by mutableStateOf(false)
    /** 朋友圈 tab pages: 0 = 动态, 1 = 消息 (swipe left / chat icon). */
    var page by mutableStateOf(0)
    /** Set by 我 › 编辑资料; consumed by the 朋友圈 host (opens the self editor sheet). */
    var pendingEditSelf by mutableStateOf(false)
    val atRoot: Boolean get() = moments.size == 1 && chats.size == 1
}

/** 朋友圈 tab: 动态 ↔ 消息 as two horizontally swipeable pages. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun SocialTab() {
    val pager = androidx.compose.foundation.pager.rememberPagerState(initialPage = SocialNav.page) { 2 }
    LaunchedEffect(SocialNav.page) { if (pager.currentPage != SocialNav.page) pager.animateScrollToPage(SocialNav.page) }
    LaunchedEffect(pager) { snapshotFlow { pager.settledPage }.collect { SocialNav.page = it } }
    // back on 消息 (at its root) → 动态
    BackHandler(enabled = SocialNav.page == 1 && SocialNav.chats.size == 1) { SocialNav.page = 0 }
    androidx.compose.foundation.pager.HorizontalPager(
        state = pager, modifier = Modifier.fillMaxSize(), beyondViewportPageCount = 1,
        userScrollEnabled = SocialNav.atRoot && !SocialNav.immersive,
        key = { it },
    ) { p -> if (p == 0) MomentsTab() else ChatsTab() }
}

private enum class PickTarget { COMPOSER, CHAT, AVATAR }

@Composable
fun MomentsTab() = SocialHost(SocialNav.moments)

@Composable
fun ChatsTab() = SocialHost(SocialNav.chats)

@Composable
private fun SocialHost(stack: MutableList<SRoute>) {
    val app = MoonApp.instance
    val ctx = LocalContext.current
    val overlay = LocalOverlay.current
    val scope = rememberCoroutineScope()
    val s by app.social.state.collectAsState()
    val library by app.library.items.collectAsState()
    val acting = SocialNav.actingId?.takeIf { s.contact(it) != null } ?: s.selfId
    val top = stack.last()
    val settings by app.settings.flow.collectAsState(initial = com.moonlight.feed.data.SettingsRepository.Snapshot())
    val isMoments = stack === SocialNav.moments
    // v5.0: each stack has its own inline-window pool (profiles opened from 消息 play too); only the visible page's top route drives it
    val players = rememberMomentPlayers()
    val pageVisible = if (isMoments) SocialNav.page == 0 else SocialNav.page == 1

    // per-screen mutable bits
    var draft by remember { mutableStateOf<MomentDraft?>(null) }
    LaunchedEffect(top) { draft = (top as? SRoute.Composer)?.draft }
    var chatText by remember { mutableStateOf("") }
    var chatSpeaker by remember { mutableStateOf<String?>(null) }
    var stripOpen by remember { mutableStateOf(false) }
    var commentTarget by remember { mutableStateOf<Pair<String, String?>?>(null) } // momentId, replyTo userId
    var commentText by remember { mutableStateOf("") }
    var friendDraft by remember { mutableStateOf<Contact?>(null) }
    var pickTarget by remember { mutableStateOf(PickTarget.COMPOSER) }
    val chatConv = (top as? SRoute.Chat)?.let { r -> s.conversations.firstOrNull { it.id == r.convId } }
    LaunchedEffect(chatConv?.id) {
        chatText = ""; stripOpen = false
        chatSpeaker = chatConv?.let { c -> if (s.selfId in c.memberIds) s.selfId else c.memberIds.firstOrNull() }
    }
    // opening a chat marks it read (and keeps it read while new messages arrive on screen)
    val lastInChat = chatConv?.let { s.lastMessage(it.id)?.createdAt }
    LaunchedEffect(chatConv?.id, lastInChat) { chatConv?.let { app.social.markRead(it.id, maxOf(System.currentTimeMillis(), lastInChat ?: 0)) } }
    // both pager pages stay composed: only the visible one drives the tab bar and back
    val visible = if (stack === SocialNav.chats) SocialNav.page == 1 else SocialNav.page == 0
    if (visible) SocialNav.immersive = top is SRoute.Composer || top is SRoute.Chat || commentTarget != null

    fun push(r: SRoute) { commentTarget = null; stack.add(r) }
    fun pop() { if (stack.size > 1) stack.removeAt(stack.lastIndex) }
    BackHandler(enabled = visible && (stack.size > 1 || commentTarget != null)) { if (commentTarget != null) commentTarget = null else pop() }

    fun attach(list: List<Attachment>) {
        when (pickTarget) {
            PickTarget.COMPOSER -> draft = draft?.let { it.copy(attachments = (it.attachments + list).distinctBy { a -> a.libraryEntryId ?: a.id }.take(9)) }
            PickTarget.CHAT -> chatConv?.let { c -> app.social.send(c.id, chatSpeaker ?: s.selfId, "", list) }
            PickTarget.AVATAR -> Unit
        }
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        uris.forEach { u -> runCatching { ctx.contentResolver.takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION) } }
        scope.launch {
            val added = app.library.importUris(uris)
            if (added.isEmpty()) MoonToaster.show("没有可用的图片或视频", Icons.Outlined.PhotoLibrary) else attach(added.map(::attachmentOf))
        }
    }
    val avatarPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            app.social.saveProfileMedia(uri)?.let { (p, video) ->
                val d = friendDraft?.let { ProfileMediaActions.withNewMedia(it, true, p, video) } ?: return@let
                friendDraft = d
                ProfileMediaActions.adjust(d, true, FocusFrames.AVATAR) { f -> friendDraft = friendDraft?.copy(avatarFocus = f) }
            } ?: MoonToaster.show("无法读取这个文件")
        }
    }
    // 朋友圈 cover (self) / profile background (anyone): photo or video, copied into app storage
    var coverFor by remember { mutableStateOf<String?>(null) }
    val coverPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val id = coverFor ?: return@rememberLauncherForActivityResult
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val saved = app.social.saveProfileMedia(uri) ?: run { MoonToaster.show("无法读取这个文件"); return@launch }
            val c = app.social.value.contact(id)?.let { ProfileMediaActions.withNewMedia(it, false, saved.first, saved.second) } ?: return@launch
            app.social.upsertContact(c)
            MoonToaster.show(if (saved.second) "已设为视频背景（静音循环）" else "已更换背景", Icons.Outlined.CheckCircle)
            ProfileMediaActions.adjust(c, false, coverFrame(c)) { f -> app.social.value.contact(id)?.let { app.social.upsertContact(it.copy(coverFocus = f)) } }
        }
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val id = r.data?.getStringExtra(CameraActivity.EXTRA_RESULT_ID) ?: return@rememberLauncherForActivityResult
        app.library.items.value.firstOrNull { it.id == id }?.let { attach(listOf(attachmentOf(it))) }
    }

    fun openMedia(atts: List<Attachment>, index: Int) {
        val lib = app.library.items.value.associateBy { it.id }
        val ids = atts.mapNotNull { it.libraryEntryId }.filter { it in lib }
        val start = atts.getOrNull(index)?.libraryEntryId?.takeIf { it in lib }
        if (start == null) { MoonToaster.show(if (ids.isEmpty()) "示例图片，没有可播放的文件" else "这条媒体已从库中删除"); return }
        ctx.startActivity(Intent(ctx, BiliPlayerActivity::class.java).putExtra(BiliPlayerActivity.EXTRA_START_ID, start)
            .putStringArrayListExtra(BiliPlayerActivity.EXTRA_FEED_IDS, ArrayList(ids)))
    }

    fun compose(poster: String = s.selfId, atts: List<Attachment> = emptyList()) = push(SRoute.Composer(MomentDraft(posterId = poster, attachments = atts)))

    fun editFriend(c: Contact?) {
        friendDraft = c ?: Contact(name = "", avatarColor = (s.contacts.size) % AvatarPalette.size)
        overlay.showSheet(if (c == null) "新建好友" else if (c.isSelf) "我的资料" else "编辑好友") { dismiss ->
            val d = friendDraft ?: return@showSheet
            FriendEditor(
                d, onChange = { friendDraft = it },
                onPickPhoto = { avatarPicker.launch(arrayOf("image/*", "video/*")) },
                onDeleteSaved = {
                    overlay.confirm("删除已保存的头像？", "会删除 App 内的副本（相册原文件不受影响）。", listOf(MenuAction("删除", destructive = true) {
                        val cur = friendDraft ?: return@MenuAction
                        val (n, file) = ProfileMediaActions.deletePrev(cur, true)
                        friendDraft = n
                        // the stored copy is removed now only if nothing saved still uses it
                        val others = app.social.value.contacts.filter { it.id != n.id } + n
                        if (file != null && !ProfileMediaActions.usedBy(others, file)) com.moonlight.feed.data.StoredCopies.delete(file, java.io.File(ctx.filesDir, "avatars"))
                    }))
                },
                onAdjust = { friendDraft?.let { cur -> ProfileMediaActions.adjust(cur, true, FocusFrames.AVATAR) { f -> friendDraft = friendDraft?.copy(avatarFocus = f) } } },
                onSave = {
                    val saved = d.copy(name = d.name.trim(), bio = d.bio.trim(), autoReplies = d.autoReplies.map { it.trim() }.filter { it.isNotEmpty() })
                    app.social.upsertContact(saved); dismiss()
                    MoonToaster.show(if (c == null) "已添加 ${saved.name}" else "已保存", Icons.Outlined.CheckCircle)
                },
            )
        }
    }

    fun deleteFriend(c: Contact) {
        overlay.confirm("删除 ${c.name}？", "TA 的动态、评论、点赞和与 TA 的单聊会一起删除。", listOf(
            MenuAction("删除", Icons.Outlined.Delete, destructive = true, onClick = {
                val snap = app.social.value
                app.social.deleteContact(c.id)
                if ((stack.last() as? SRoute.Profile)?.id == c.id) pop()
                MoonToaster.show("已删除 ${c.name}", Icons.Outlined.Delete, action = "撤销") { app.social.restore(snap) }
            }),
        ))
    }

    fun chatWith(members: List<String>) = push(SRoute.Chat(app.social.conversationFor(members).id))

    fun coverMenu(c: Contact, r: Rect) = overlay.showMenu(MenuSpec(anchor = r, title = if (c.isSelf) "朋友圈封面" else "${c.name} 的主页背景", actions = listOf(buildList {
        add(MenuAction("更换", Icons.Outlined.PhotoLibrary, subtitle = "照片或视频 · 视频静音循环") { coverFor = c.id; coverPicker.launch(arrayOf("image/*", "video/*")) })
        if (c.coverPath != null) {
            add(MenuAction("调整位置", Icons.Outlined.CenterFocusStrong, subtitle = "拖动 · 双指缩放") {
                ProfileMediaActions.adjust(c, false, coverFrame(c)) { f -> app.social.value.contact(c.id)?.let { app.social.upsertContact(it.copy(coverFocus = f)) } }
            })
            add(MenuAction("播放", Icons.Outlined.PlayCircle, subtitle = "轻点也可直接打开") { ProfileMediaActions.play(ctx, c, false) })
            add(MenuAction("恢复默认", Icons.Outlined.Refresh, subtitle = "照片 / 视频会保留，可用回") { app.social.upsertContact(ProfileMediaActions.reset(c, false)) })
        }
        addAll(savedMediaActions(ctx, overlay, c, false, { app.social.value.contact(c.id) }, { app.social.value.contacts }) { app.social.upsertContact(it) })
    })))

    fun friendMenu(c: Contact, anchor: Rect) = overlay.showMenu(friendMenuSpec(c, s, anchor, FriendRingHandlers(
        chat = { chatWith(listOf(s.selfId, c.id)) },
        profile = { push(SRoute.Profile(c.id)) },
        compose = { compose(c.id) },
        edit = { editFriend(c) },
        chatBetween = { o -> chatWith(listOf(c.id, o)) },
        toggleAuto = { if (c.autoReplies.isEmpty()) editFriend(c.copy(autoReplyOn = true)) else app.social.upsertContact(c.copy(autoReplyOn = !c.autoReplyOn)) },
        delete = { deleteFriend(c) },
    )))

    val people = listOf(s.self) + s.friends
    // 朋友圈 video ring — v5.0: reached by 定时长按 on a video tile (plain long-press = full-screen player)
    fun videoRing(m: Moment, i: Int, r: Rect): MenuSpec {
        val a = m.attachments[i]
        val pb = players
        val libEntry = a.libraryEntryId?.let { id -> library.firstOrNull { it.id == id } }
        val offer = com.moonlight.feed.data.download.DownloadPolicy.check(a, libEntry)
        return momentVideoMenuSpec(
            download = com.moonlight.feed.ui.download.DownloadActions.ringChild(offer, offer.source?.let { com.moonlight.feed.ui.download.DownloadActions.task(it.id) }) {
                offer.source?.let { com.moonlight.feed.ui.download.DownloadActions.start(it) }
            },
            anchor = r, title = s.contact(m.authorId)?.name,
            playing = pb?.isPlaying(a.id) == true, muted = pb?.slots?.get(a.id)?.muted != false,
            canPlayInline = pb?.canPlay(a) == true, canOpen = true, canSave = libEntry != null,
            h = MomentVideoHandlers(
                toggle = { pb?.tap(a) },
                open = { pb?.handOff(); openMedia(m.attachments, i) },
                sound = { pb?.let { p -> if (p.slots[a.id] == null) p.tap(a); p.mute(a.id, p.slots[a.id]?.muted != false) } },
                save = {
                    libEntry?.let { e -> scope.launch {
                        MoonToaster.show("正在保存…", Icons.Outlined.Download)
                        val res = com.moonlight.feed.data.GallerySaver.saveEntry(app, app.http, e)
                        if (res.ok) MoonToaster.show("已保存到相册", Icons.Outlined.CheckCircle) else MoonToaster.show("保存失败：${res.error}", Icons.Outlined.ErrorOutline)
                    } }
                },
            ),
        )
    }

    val mh = MomentHandlers(
        onOpenMedia = { m, i -> openMedia(m.attachments, i) },
        onLike = { m ->
            app.social.toggleLike(m.id, acting)
            if (acting != s.selfId && acting !in m.likes) MoonToaster.show("以 ${s.contact(acting)?.name} 点赞", Icons.Outlined.Favorite)
        },
        onLikeLong = { m, r -> overlay.showMenu(MenuSpec(anchor = r, title = "谁点了赞", list = true,
            actions = listOf(people.map { p -> MenuAction(if (p.isSelf) "我" else p.name, if (p.id in m.likes) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder, checked = p.id in m.likes,
                subtitle = if (p.id in m.dislikes) "已踩 · 点赞会取消踩" else null) { app.social.toggleLike(m.id, p.id) } })))
        },
        onDislike = { m ->
            app.social.toggleDislike(m.id, acting)
            if (acting != s.selfId && acting !in m.dislikes) MoonToaster.show("以 ${s.contact(acting)?.name} 踩", Icons.Outlined.ThumbDown)
        },
        onDislikeLong = { m, r -> overlay.showMenu(MenuSpec(anchor = r, title = "谁踩了", list = true,
            actions = listOf(people.map { p -> MenuAction(if (p.isSelf) "我" else p.name, Icons.Outlined.ThumbDown, checked = p.id in m.dislikes,
                subtitle = if (p.id in m.likes) "已赞 · 踩会取消赞" else null) { app.social.toggleDislike(m.id, p.id) } })))
        },
        // v4.0: tap cover = play it full screen (nothing custom yet → its menu); long-press = the ring
        onCover = { c, r -> if (!ProfileMediaActions.play(ctx, c, false)) coverMenu(c, r) },
        onMediaLong = { c, avatar, r -> if (avatar) friendMenu(c, r) else coverMenu(c, r) },
        onChats = { SocialNav.page = 1 },
        onComment = { m, cm -> commentTarget = m.id to cm?.authorId; commentText = "" },
        onCommentLong = { m, cm, r -> overlay.showMenu(MenuSpec(anchor = r, title = cm.text.take(24), actions = listOf(listOf(
            MenuAction("回复", Icons.Outlined.Reply) { commentTarget = m.id to cm.authorId; commentText = "" },
            MenuAction("复制", Icons.Outlined.ContentCopy) { copy(ctx, cm.text) },
            MenuAction("删除", Icons.Outlined.Delete, destructive = true) { app.social.deleteComment(m.id, cm.id) },
        )))) },
        // v5.0: long-press a video = straight into the full-screen player. With 定时长按 on, the open waits for the finger-up
        // and holding to the threshold pops the video ring instead (its assigned option fires) — see TimedHold.
        onVideoLong = { m, i, r ->
            if (!overlay.deferHold(com.moonlight.feed.data.HoldContext.MOMENT_VIDEO, ring = { videoRing(m, i, r) }, onRelease = { openMedia(m.attachments, i) }))
                openMedia(m.attachments, i)
        },
        onMomentLong = { m, r -> overlay.showMenu(momentPostMenuSpec(m, s, acting, r, PostRingHandlers(
            like = { app.social.toggleLike(m.id, acting) },
            dislike = { app.social.toggleDislike(m.id, acting) },
            comment = { commentTarget = m.id to null; commentText = "" },
            forward = { conv -> app.social.send(conv, acting, m.text, m.attachments); MoonToaster.show("已转发", Icons.AutoMirrored.Outlined.Chat) },
            edit = { push(SRoute.Composer(MomentDraft(m.authorId, m.text, m.attachments, m.createdAt, editingId = m.id))) },
            copy = { copy(ctx, m.text) },
            setAuthor = { pid -> app.social.upsertMoment(m.copy(authorId = pid)) },
            toggleAutoplay = {
                app.social.upsertMoment(m.copy(autoplay = !m.autoplay))
                MoonToaster.show(if (m.autoplay) "已取消自动播放" else "已设为自动播放（只播指定的）", Icons.Outlined.CheckCircle)
            },
            // v5.0: 碎纸机 + 最近删除 (the ring's 删除 already sits in the ··· sheet's 危险 section)
            delete = { com.moonlight.feed.ui.library.TrashUi.trashMoment(m) },
        ))) },
        onAuthor = { id -> push(SRoute.Profile(id)) },
        onCompose = { compose() },
        onComposeLong = { r -> overlay.showMenu(MenuSpec(anchor = r, title = "发动态", actions = listOf(buildList {
            add(MenuAction("以我发布", Icons.Outlined.Person) { compose() })
            if (s.friends.isNotEmpty()) add(MenuAction("以好友发布", Icons.Outlined.Badge, children = s.friends.map { f -> MenuAction(f.name) { compose(f.id) } }))
            add(MenuAction("拍照发布", Icons.Outlined.CameraAlt) { camera.launch(CameraActivity.intent(ctx, false, "moment")) })
            add(MenuAction("录像发布", Icons.Outlined.Videocam) { camera.launch(CameraActivity.intent(ctx, true, "moment")) })
            add(MenuAction("从相册", Icons.Outlined.PhotoLibrary) { compose(); pickTarget = PickTarget.COMPOSER; gallery.launch(arrayOf("image/*", "video/*")) })
        }))) },
        onFriends = { push(SRoute.Friends) },
        onSeedDemo = { app.social.seedDemo(); MoonToaster.show("已载入示例好友（可随时删除）", Icons.Outlined.CheckCircle) },
        onNewFriend = { editFriend(null) },
        onBack = { pop() },
        onEditProfile = { editFriend(it) },
        onMessage = { chatWith(listOf(s.selfId, it.id)) },
        onPostAs = { compose(it.id) },
        onProfileLong = { c, r -> friendMenu(c, r) },
    )

    // external requests: capture → 发朋友圈, feed 更多 → 发到朋友圈, tab long-press actions
    val req by AppBus.compose.collectAsState()
    LaunchedEffect(req) {
        val r = req ?: return@LaunchedEffect
        if (stack !== SocialNav.moments) return@LaunchedEffect
        AppBus.compose.value = null
        val lib = app.library.items.value.associateBy { it.id }
        compose(r.posterId ?: s.selfId, r.entryIds.mapNotNull { lib[it] }.map(::attachmentOf))
    }
    LaunchedEffect(SocialNav.pendingEditSelf) { if (isMoments && SocialNav.pendingEditSelf) { SocialNav.pendingEditSelf = false; editFriend(s.self) } }
    LaunchedEffect(Unit) {
        AppBus.events.collect { e ->
            when (e) {
                AppEvent.MOMENTS_COMPOSE -> if (stack === SocialNav.moments) compose()
                AppEvent.SOCIAL_FRIENDS -> if (stack === SocialNav.moments || stack === SocialNav.chats) { if (stack.last() != SRoute.Friends) push(SRoute.Friends) }
                AppEvent.CHATS_NEW -> if (stack === SocialNav.chats) { SocialNav.page = 1; newChat(overlay, s) { chatWith(it) } }
                else -> Unit
            }
        }
    }

    AnimatedContent(
        targetState = top,
        transitionSpec = {
            val forward = stack.indexOf(targetState) >= stack.indexOf(initialState)
            (slideInHorizontally(spring(0.86f, 500f)) { if (forward) it / 4 else -it / 4 } + fadeIn(spring(stiffness = 600f))) togetherWith
                (slideOutHorizontally(spring(0.9f, 600f)) { if (forward) -it / 6 else it / 4 } + fadeOut(spring(stiffness = 900f)))
        },
        label = "social", modifier = Modifier.fillMaxSize(),
    ) { r ->
        Box(Modifier.fillMaxSize()) {
            when (r) {
                SRoute.Timeline -> MomentsContent(s, acting, mh, players = players, autoplayPaused = !pageVisible || commentTarget != null || r != top)
                SRoute.Friends -> FriendsContent(s, FriendsHandlers(
                    onOpen = { push(SRoute.Profile(it.id)) }, onLong = { c, a -> friendMenu(c, a) }, onNew = { editFriend(null) },
                    onBack = if (stack.size > 1) ({ pop() }) else null, onSeedDemo = mh.onSeedDemo,
                ))
                is SRoute.Profile -> s.contact(r.id)?.let { FriendProfileContent(it, s, acting, mh, players = players, active = pageVisible && r == top) } ?: LaunchedEffect(Unit) { pop() }
                is SRoute.Composer -> {
                    val d = draft ?: r.draft
                    ComposerContent(d, s, ComposerHandlers(
                        onCancel = { pop() },
                        onPublish = {
                            val now = System.currentTimeMillis()
                            if (d.editingId != null) {
                                s.moments.firstOrNull { it.id == d.editingId }?.let { old ->
                                    app.social.upsertMoment(old.copy(authorId = d.posterId, text = d.text.trim(), attachments = d.attachments, createdAt = d.time ?: old.createdAt, editedAt = now))
                                }
                                MoonToaster.show("已保存", Icons.Outlined.CheckCircle)
                            } else {
                                app.social.upsertMoment(Moment(authorId = d.posterId, text = d.text.trim(), attachments = d.attachments, createdAt = d.time ?: now))
                                val who = s.contact(d.posterId)
                                MoonToaster.show(if (who == null || who.isSelf) "已发布" else "已以 ${who.name} 的身份发布", Icons.Outlined.CheckCircle)
                            }
                            pop()
                            if (stack.last() != SRoute.Timeline && stack.last() !is SRoute.Profile) { stack.clear(); stack.add(SRoute.Timeline) }
                        },
                        onText = { t -> draft = d.copy(text = t) },
                        onPoster = { p -> draft = d.copy(posterId = p.id) },
                        onAddMedia = { a ->
                            pickTarget = PickTarget.COMPOSER
                            overlay.showMenu(mediaSourceMenu(a, library.isNotEmpty(),
                                fromLibrary = { libraryPicker(overlay) { picked -> draft = (draft ?: d).let { it.copy(attachments = (it.attachments + picked.map(::attachmentOf)).distinctBy { x -> x.libraryEntryId ?: x.id }.take(9)) } } },
                                fromGallery = { gallery.launch(arrayOf("image/*", "video/*")) },
                                photo = { camera.launch(CameraActivity.intent(ctx, false, "attach")) },
                                video = { camera.launch(CameraActivity.intent(ctx, true, "attach")) },
                            ))
                        },
                        onRemoveMedia = { a -> draft = d.copy(attachments = d.attachments - a) },
                        onOpenMedia = { i -> openMedia(d.attachments, i) },
                        onTime = { a -> overlay.showMenu(timeMenu(a, d.time) { t -> if (t == -1L) pickDateTime(ctx, d.time) { draft = (draft ?: d).copy(time = it) } else draft = d.copy(time = t) }) },
                    ))
                }
                SRoute.Chats -> ChatListContent(s, ChatListHandlers(
                    onOpen = { push(SRoute.Chat(it.id)) },
                    onLong = { c, a -> overlay.showMenu(MenuSpec(anchor = a, title = convTitle(c, s), actions = listOf(listOf(
                        MenuAction(if (c.pinned) "取消置顶" else "置顶", Icons.Outlined.PushPin, checked = c.pinned) { app.social.updateConversation(c.copy(pinned = !c.pinned)) },
                        MenuAction("清空记录", Icons.Outlined.DeleteSweep) {
                            val snap = app.social.value; app.social.clearConversation(c.id)
                            MoonToaster.show("已清空", Icons.Outlined.DeleteSweep, action = "撤销") { app.social.restore(snap) }
                        },
                        MenuAction("删除对话", Icons.Outlined.Delete, destructive = true) {
                            val snap = app.social.value; app.social.deleteConversation(c.id)
                            MoonToaster.show("已删除对话", Icons.Outlined.Delete, action = "撤销") { app.social.restore(snap) }
                        },
                    )))) },
                    onNew = { newChat(overlay, s) { chatWith(it) } },
                    onFriends = { push(SRoute.Friends) },
                    onSeedDemo = mh.onSeedDemo,
                    onBack = { SocialNav.page = 0 },
                ))
                is SRoute.Chat -> {
                    val conv = s.conversations.firstOrNull { it.id == r.convId }
                    if (conv == null) LaunchedEffect(Unit) { pop() } else {
                        val members = conv.memberIds.mapNotNull { s.contact(it) }
                        val speaker = chatSpeaker?.takeIf { it in conv.memberIds } ?: perspectiveOf(conv, s)
                        ChatContent(conv, s, speaker, chatText, stripOpen, ChatHandlers(
                            onBack = { pop() },
                            onTitle = { a -> overlay.showMenu(MenuSpec(anchor = a, title = convTitle(conv, s), list = true, actions = listOf(
                                members.map { p -> MenuAction(if (p.isSelf) "我" else p.name, Icons.Outlined.Person, subtitle = "查看主页") { push(SRoute.Profile(p.id)) } },
                                listOf(MenuAction("清空记录", Icons.Outlined.DeleteSweep, destructive = true) { app.social.clearConversation(conv.id) }),
                            ))) },
                            onMessageLong = { m, a -> overlay.showMenu(messageMenu(m, a, members, ctx,
                                retry = { app.social.deleteMessage(m.id); app.social.send(conv.id, m.senderId, m.text, m.attachments) },
                                reply = { val who = s.contact(m.senderId)?.let { if (it.isSelf) "我" else it.name } ?: ""; chatText = "回复 $who：「${m.text.take(20)}」 " })) },
                            onOpenMedia = { m, i -> openMedia(m.attachments, i) },
                            onAvatar = { push(SRoute.Profile(it)) },
                            onText = { chatText = it },
                            onSpeaker = { p -> chatSpeaker = p.id; stripOpen = false },
                            onToggleStrip = { stripOpen = !stripOpen },
                            onSpeakerLong = { overlay.showMenu(MenuSpec(anchor = Rect.Zero, title = "以谁的身份发言",
                                radial = members.take(8).map { p -> MenuAction(if (p.isSelf) "我" else p.name, Icons.Outlined.Person, checked = p.id == speaker) { chatSpeaker = p.id } },
                                actions = emptyList())) },
                            onSend = { if (chatText.isNotBlank()) { app.social.send(conv.id, speaker, chatText); chatText = "" } },
                            onAttach = {
                                pickTarget = PickTarget.CHAT
                                overlay.showMenu(mediaSourceMenu(Rect.Zero, library.isNotEmpty(),
                                    fromLibrary = { libraryPicker(overlay) { picked -> if (picked.isNotEmpty()) app.social.send(conv.id, speaker, "", picked.map(::attachmentOf)) } },
                                    fromGallery = { gallery.launch(arrayOf("image/*", "video/*")) },
                                    photo = { camera.launch(CameraActivity.intent(ctx, false, "attach")) },
                                    video = { camera.launch(CameraActivity.intent(ctx, true, "attach")) },
                                ))
                            },
                        ))
                    }
                }
            }
            // comment bar (timeline / profile)
            val ct = commentTarget
            if (ct != null && (r == SRoute.Timeline || r is SRoute.Profile)) {
                val target = s.moments.firstOrNull { it.id == ct.first }
                Box(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().imePadding()) {
                    SpeakInputBar(
                        speaker = s.contact(acting), people = people, text = commentText, onText = { commentText = it },
                        onSpeaker = { p -> SocialNav.actingId = p.id; stripOpen = false }, stripOpen = stripOpen, onToggleStrip = { stripOpen = !stripOpen },
                        placeholder = ct.second?.let { "回复 ${s.contact(it)?.name}…" } ?: "评论 ${target?.let { s.contact(it.authorId)?.name } ?: ""}…",
                        onSend = {
                            if (commentText.isNotBlank() && target != null) {
                                app.social.addComment(target.id, Comment(authorId = acting, text = commentText.trim(), replyToId = ct.second))
                                commentText = ""; commentTarget = null; stripOpen = false
                            }
                        },
                    )
                }
            }
        }
    }
}

private fun copy(ctx: Context, text: String) {
    (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("月光", text))
    MoonToaster.show("已复制", Icons.Outlined.ContentCopy)
}

private fun mediaSourceMenu(anchor: Rect, hasLibrary: Boolean, fromLibrary: () -> Unit, fromGallery: () -> Unit, photo: () -> Unit, video: () -> Unit) = MenuSpec(
    anchor = anchor, title = "添加图片或视频",
    actions = listOf(buildList {
        if (hasLibrary) add(MenuAction("从库选择", Icons.Outlined.ViewModule, onClick = fromLibrary))
        add(MenuAction("从相册", Icons.Outlined.PhotoLibrary, onClick = fromGallery))
        add(MenuAction("拍照", Icons.Outlined.CameraAlt, onClick = photo))
        add(MenuAction("录像", Icons.Outlined.Videocam, onClick = video))
    }),
)

private fun libraryPicker(overlay: MoonOverlayState, onPicked: (List<com.moonlight.feed.data.MediaEntry>) -> Unit) {
    val sel = mutableStateListOf<String>()
    overlay.showSheet("从库选择") { dismiss ->
        val items by MoonApp.instance.library.items.collectAsState()
        val visible = items.filter { !it.hidden }
        LibraryPicker(visible, sel.toSet(), onToggle = { e -> if (e.id in sel) sel.remove(e.id) else if (sel.size < 9) sel.add(e.id) }) {
            val byId = visible.associateBy { it.id }
            onPicked(sel.mapNotNull { byId[it] }); dismiss()
        }
    }
}

private fun timeMenu(anchor: Rect, current: Long?, pick: (Long?) -> Unit): MenuSpec {
    val now = System.currentTimeMillis()
    fun opt(label: String, t: Long?) = MenuAction(label, checked = current == t && t == null) { pick(t) }
    return MenuSpec(anchor = anchor, title = "发布时间", list = true, actions = listOf(listOf(
        opt("现在", null),
        MenuAction("10 分钟前") { pick(now - 10 * 60_000) },
        MenuAction("1 小时前") { pick(now - 3_600_000) },
        MenuAction("昨天此时") { pick(now - 86_400_000) },
        MenuAction("3 天前") { pick(now - 3 * 86_400_000L) },
    ), listOf(MenuAction("自定义…", subtitle = current?.let { draftTimeLabel(it) }) { pick(-1L) })))
}

private fun pickDateTime(ctx: Context, initial: Long?, onPicked: (Long) -> Unit) {
    val cal = Calendar.getInstance().apply { initial?.let { timeInMillis = it } }
    DatePickerDialog(ctx, { _, y, m, d ->
        TimePickerDialog(ctx, { _, hh, mm ->
            cal.set(y, m, d, hh, mm, 0); onPicked(cal.timeInMillis)
        }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true).show()
    }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
}

internal fun messageMenu(m: Message, anchor: Rect, members: List<Contact>, ctx: Context, retry: () -> Unit, reply: () -> Unit = {}): MenuSpec {
    val app by lazy { MoonApp.instance }   // only touched when an action runs (pure to build → testable)
    val speaker = MenuAction("改发言人", Icons.Outlined.SwapHoriz, children = members.map { p ->
        MenuAction(if (p.isSelf) "我" else p.name, checked = p.id == m.senderId, short = (if (p.isSelf) "我" else p.name).take(3)) { app.social.updateMessage(m.copy(senderId = p.id)) } })
    fun remove(label: String) { app.social.deleteMessage(m.id); MoonToaster.show(label, Icons.Outlined.Undo, action = "撤销") { app.social.updateMessage(m) } }
    return MenuSpec(
        anchor = anchor, title = m.text.take(24).ifBlank { null },
        radial = buildList {
            if (m.status == MessageStatus.FAILED) add(MenuAction("重新发送", Icons.Outlined.Refresh, onClick = retry))
            if (m.text.isNotBlank()) add(MenuAction("复制", Icons.Outlined.ContentCopy) { copy(ctx, m.text) })
            add(MenuAction("回复", Icons.Outlined.Reply, onClick = reply))
            add(speaker)
            add(MenuAction("撤回", Icons.Outlined.Undo, subtitle = "可撤销") { remove("已撤回") })
        },
        actions = listOf(listOf(MenuAction("删除", Icons.Outlined.Delete, destructive = true) { remove("已删除消息") })),
    )
}

private fun newChat(overlay: MoonOverlayState, s: com.moonlight.feed.data.social.SocialState, start: (List<String>) -> Unit) {
    if (s.friends.isEmpty()) { MoonToaster.show("先新建一位好友", Icons.Outlined.Person); return }
    val sel = mutableStateListOf(s.selfId)
    overlay.showSheet("新对话") { dismiss ->
        val st by MoonApp.instance.social.state.collectAsState()
        NewChatPicker(st, sel.toSet(), onToggle = { p -> if (p.id in sel) sel.remove(p.id) else sel.add(p.id) }) { start(sel.toList()); dismiss() }
    }
}


/** 转发到聊天: recent conversations fan out as sub-choices (≤ 6 on the arc, else a list). */
internal fun forwardToChat(s: com.moonlight.feed.data.social.SocialState, acting: String, send: (String) -> Unit): MenuAction {
    val convs = s.conversations.sortedByDescending { c -> s.messages.filter { it.conversationId == c.id }.maxOfOrNull { it.createdAt } ?: 0L }
    return MenuAction("转发到聊天", Icons.AutoMirrored.Outlined.Send, subtitle = if (convs.isEmpty()) "还没有对话" else null,
        children = convs.map { c -> val t = convTitle(c, s); MenuAction(t, short = t.take(3)) { send(c.id) } }.ifEmpty { null },
        onClick = { MoonToaster.show("还没有对话，先在「消息」里新建一个") })
}

/** Self cover is mainly seen as the 朋友圈 cover; others' as their profile background. */
private fun coverFrame(c: Contact) = if (c.isSelf) FocusFrames.MOMENTS_COVER else FocusFrames.PROFILE_COVER
