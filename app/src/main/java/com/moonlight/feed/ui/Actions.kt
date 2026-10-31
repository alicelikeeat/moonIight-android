package com.moonlight.feed.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.moonlight.feed.data.MediaEntry
import com.moonlight.feed.data.MediaKind
import kotlinx.coroutines.flow.MutableSharedFlow
import java.io.File

/** Tab long-press quick actions are broadcast here; screens collect what they handle. */
enum class AppEvent { FEED_TOP, FEED_RANDOM, FEED_REFRESH, LIB_SELECT, LIB_FAVORITES, LIB_TRASH, DISCOVER_RANDOM, DISCOVER_PASTE, MOMENTS_COMPOSE, SOCIAL_FRIENDS, CHATS_NEW }

object AppBus {
    val events = MutableSharedFlow<AppEvent>(extraBufferCapacity = 8)
    fun send(e: AppEvent) { events.tryEmit(e) }

    /** Ask 首页 to show these ids first (in order) and land on the first one; consumed by FeedScreen. */
    val feedFocus = kotlinx.coroutines.flow.MutableStateFlow<List<String>?>(null)
    /** Ask MainActivity to switch tab (e.g. Discover → 首页 after 加入信息流). */
    val goTab = MutableSharedFlow<Int>(extraBufferCapacity = 2)
    fun focusFeed(ids: List<String>) { feedFocus.value = ids; goTab.tryEmit(0) }
    /** Open the 朋友圈 composer with these library entries attached (capture → 发朋友圈, feed 更多 → 发到朋友圈). */
    val compose = kotlinx.coroutines.flow.MutableStateFlow<ComposeRequest?>(null)
    fun composeMoment(entryIds: List<String>, posterId: String? = null) { compose.value = ComposeRequest(entryIds, posterId); goTab.tryEmit(TAB_MOMENTS) }
    /** Ask 首页 to switch its 筛选 (from the tab-bar ring); consumed by FeedScreen (toast + scroll to top). */
    val feedScopeRequest = kotlinx.coroutines.flow.MutableStateFlow<com.moonlight.feed.data.FeedScope?>(null)
    const val TAB_MOMENTS = 4
    const val TAB_CHATS = 5
}

fun MediaEntry.originalUrl(): String? =
    remoteUrl?.takeIf { it.startsWith("http") }
        ?: biliBvid?.let { "https://www.bilibili.com/video/$it" }
        ?: embedUrl

fun openOriginal(ctx: Context, e: MediaEntry): Boolean {
    val url = e.originalUrl() ?: return false
    return runCatching {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.isSuccess
}

/** System share sheet: local file via FileProvider, otherwise the public link. */
fun shareEntry(ctx: Context, e: MediaEntry): Boolean = runCatching {
    val local = e.localPath?.let { File(it) }?.takeIf { it.exists() && it.length() > 0 }
    val send = Intent(Intent.ACTION_SEND)
    if (local != null) {
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", local)
        send.type = if (e.kind == MediaKind.IMAGE) "image/*" else "video/*"
        send.putExtra(Intent.EXTRA_STREAM, uri)
        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    } else {
        val url = e.originalUrl() ?: return@runCatching false
        send.type = "text/plain"
        send.putExtra(Intent.EXTRA_TEXT, if (e.title.isNotBlank()) "${e.title}\n$url" else url)
    }
    send.putExtra(Intent.EXTRA_TITLE, e.title)
    ctx.startActivity(Intent.createChooser(send, "分享").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    true
}.getOrDefault(false)

data class ComposeRequest(val entryIds: List<String>, val posterId: String? = null, val id: Long = System.nanoTime())
