package com.moonlight.feed.ui.player

import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.VerticalAlignTop
import androidx.compose.material.icons.automirrored.outlined.VolumeOff
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.moonlight.feed.ui.design.GlassIconButton
import com.moonlight.feed.ui.design.MenuAction
import com.moonlight.feed.ui.design.MenuSpec
import com.moonlight.feed.ui.design.MuteIcon
import com.moonlight.feed.ui.design.anchor
import com.moonlight.feed.ui.design.glass
import com.moonlight.feed.ui.design.moonPress
import com.moonlight.feed.ui.design.rememberAnchor
import com.moonlight.feed.ui.theme.LocalMoonColors

class PlayerActions(
    val home: () -> Unit = {},
    val toTop: () -> Unit = {},
    val random: () -> Unit = {},
    val browser: () -> Unit = {},
    val swap: () -> Unit = {},
    val mute: () -> Unit = {},
    val close: () -> Unit = {},
    val favorite: () -> Unit = {},
    val save: () -> Unit = {},
    val share: () -> Unit = {},
    val hide: () -> Unit = {},
    val download: () -> Unit = {},
)

/** Long-press on the media in the swipe player: ring around the finger. */
fun playerMediaMenu(
    origin: androidx.compose.ui.geometry.Offset, entry: com.moonlight.feed.data.MediaEntry?, muted: Boolean, embed: Boolean, a: PlayerActions,
    dl: com.moonlight.feed.data.download.DownloadTask? = null,
): MenuSpec {
    val offer = entry?.let { com.moonlight.feed.data.download.DownloadPolicy.check(it) } ?: com.moonlight.feed.data.download.DownloadOffer(null, "没有可下载的内容")
    return MenuSpec(context = com.moonlight.feed.data.HoldContext.PLAYER, 
    anchor = Rect(origin, 1f),
    origin = origin,
    title = entry?.title?.ifBlank { null },
    forceDark = true,
    // same object as the feed item → same ring: 收藏 · 声音 · 分享·保存 · 随机/换源 (≤ 4); the rest behind ···
    radial = listOf(
        MenuAction(if (entry?.favorite == true) "已收藏" else "收藏", if (entry?.favorite == true) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder, checked = entry?.favorite == true) { a.favorite() },
        MenuAction(if (muted) "打开声音" else "静音", if (muted) Icons.AutoMirrored.Outlined.VolumeOff else Icons.AutoMirrored.Outlined.VolumeUp, checked = muted) { a.mute() },
        MenuAction("分享·保存", Icons.Outlined.IosShare, children = listOfNotNull(
            MenuAction("分享", Icons.Outlined.Share) { a.share() },
            MenuAction("保存到相册", Icons.Outlined.Download) { a.save() },
            com.moonlight.feed.ui.download.DownloadActions.ringChild(offer, dl) { a.download() },
        )),
        if (embed) MenuAction("换源", Icons.Outlined.SwapHoriz) { a.swap() } else MenuAction("随机一条", Icons.Outlined.Shuffle) { a.random() },
    ),
    groupTitles = listOf("播放", "分享与保存", "整理"),
    actions = listOf(
        listOfNotNull(
            if (embed) MenuAction("随机一条", Icons.Outlined.Shuffle) { a.random() } else null,
            MenuAction("回到顶部", Icons.Outlined.VerticalAlignTop) { a.toTop() },
            MenuAction("回到首页", Icons.Outlined.Home, subtitle = "停在当前这一条") { a.home() },
        ),
        listOf(
            MenuAction("分享", Icons.Outlined.Share) { a.share() },
            MenuAction("保存到相册", Icons.Outlined.Download) { a.save() },
            com.moonlight.feed.ui.download.DownloadActions.sheetRow(offer, dl) { a.download() },
            MenuAction("浏览器打开", Icons.AutoMirrored.Outlined.OpenInNew) { a.browser() },
        ),
        listOf(
            MenuAction("从首页隐藏", Icons.Outlined.VisibilityOff) { a.hide() },
            MenuAction("关闭播放器", Icons.Outlined.Close) { a.close() },
        ),
    ),
)
}

fun playerHomeMenu(anchor: Rect, a: PlayerActions) = MenuSpec(
    anchor = anchor,
    forceDark = true,
    actions = listOf(
        listOf(
            MenuAction("回到首页", Icons.Outlined.Home, subtitle = "停在当前这一条") { a.home() },
            MenuAction("回到顶部", Icons.Outlined.VerticalAlignTop) { a.toTop() },
            MenuAction("随机一条", Icons.Outlined.Shuffle) { a.random() },
        ),
        listOf(MenuAction("浏览器打开", Icons.AutoMirrored.Outlined.OpenInNew) { a.browser() }),
        listOf(MenuAction("关闭", Icons.Outlined.Close, destructive = true) { a.close() }),
    ),
)

fun playerMoreMenu(anchor: Rect, muted: Boolean, a: PlayerActions) = MenuSpec(
    anchor = anchor,
    forceDark = true,
    actions = listOf(
        listOf(
            MenuAction(if (muted) "打开声音" else "静音", if (muted) Icons.AutoMirrored.Outlined.VolumeUp else Icons.AutoMirrored.Outlined.VolumeOff) { a.mute() },
            MenuAction("换源", Icons.Outlined.SwapHoriz, subtitle = "切换嵌入方式") { a.swap() },
            MenuAction("浏览器打开", Icons.AutoMirrored.Outlined.OpenInNew) { a.browser() },
        ),
        listOf(MenuAction("关闭", Icons.Outlined.Close, destructive = true) { a.close() }),
    ),
)

/** Glass top chrome for the immersive player: ⌂ · title pill · sound · more. Long-press ⌂ / ⋯ = menus. */
@Composable
fun PlayerTopBar(
    label: String,
    title: String,
    muted: Boolean,
    onHome: () -> Unit,
    onHomeLong: (Rect) -> Unit,
    onMute: () -> Unit,
    onMore: (Rect) -> Unit,
    onTitleClick: () -> Unit = {},
) {
    val c = LocalMoonColors.current
    val homeA = rememberAnchor()
    val moreA = rememberAnchor()
    Row(
        Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.displayCutout).padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlassIconButton(Icons.Outlined.Home, "回到首页", onHome, Modifier.anchor(homeA), onLongClick = { onHomeLong(homeA.rect) }, skinId = "player.home")
        Spacer(Modifier.width(8.dp))
        Column(
            Modifier.weight(1f)
                .moonPress(onTitleClick, { onMore(moreA.rect) }, pressedScale = 0.97f, highlightShape = RoundedCornerShape(22.dp), highlight = Color.White.copy(0.08f))
                .glass(RoundedCornerShape(22.dp), c)
                .padding(horizontal = 14.dp, vertical = 6.dp),
        ) {
            Text(label, color = c.textDim, style = MaterialTheme.typography.labelSmall, maxLines = 1)
            Text(title, color = c.text, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier.size(44.dp).moonPress(onMute, highlightShape = CircleShape).glass(CircleShape, c),
            contentAlignment = Alignment.Center,
        ) { MuteIcon(muted, tint = c.text) }
        Spacer(Modifier.width(8.dp))
        GlassIconButton(Icons.Outlined.MoreHoriz, "更多", { onMore(moreA.rect) }, Modifier.anchor(moreA), onLongClick = { onMore(moreA.rect) }, skinId = "player.menu")
    }
}
