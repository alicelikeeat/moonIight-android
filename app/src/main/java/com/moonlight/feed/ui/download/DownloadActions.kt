package com.moonlight.feed.ui.download

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.ErrorOutline
import com.moonlight.feed.data.MediaEntry
import com.moonlight.feed.data.download.DlState
import com.moonlight.feed.data.download.DownloadManager
import com.moonlight.feed.data.download.DownloadOffer
import com.moonlight.feed.data.download.DownloadPolicy
import com.moonlight.feed.data.download.DownloadSource
import com.moonlight.feed.data.download.DownloadTask
import com.moonlight.feed.data.download.Downloads
import com.moonlight.feed.ui.design.MenuAction
import com.moonlight.feed.ui.design.MoonToaster

/** The one 下载 action every ring / ··· sheet uses (feed item, player, 朋友圈 video). */
object DownloadActions {
    /** Ring child (secondary arc of 分享·保存): only when the item really is downloadable. */
    fun ringChild(offer: DownloadOffer, task: DownloadTask?, onClick: () -> Unit): MenuAction? =
        if (!offer.ok) null else MenuAction("下载", Icons.Outlined.CloudDownload, subtitle = status(task), short = shortStatus(task), onClick = onClick)

    /** ··· sheet row: always present; not downloadable → dimmed with the reason. */
    fun sheetRow(offer: DownloadOffer, task: DownloadTask?, onClick: () -> Unit): MenuAction =
        MenuAction("下载到「我的下载」", Icons.Outlined.CloudDownload, subtitle = status(task) ?: "可暂停、断点续传", disabledReason = offer.reason, onClick = onClick)

    fun status(t: DownloadTask?): String? = when (t?.state) {
        null -> null
        DlState.QUEUED -> "排队中"
        DlState.RUNNING -> t.progress?.let { "下载中 ${(it * 100).toInt()}%" } ?: "下载中"
        DlState.PAUSED -> "已暂停 · 点按继续"
        DlState.FAILED -> "失败 · 点按重试"
        DlState.DONE -> "已下载"
    }
    private fun shortStatus(t: DownloadTask?): String? = when (t?.state) {
        DlState.RUNNING -> t.progress?.let { "${(it * 100).toInt()}%" }
        DlState.DONE -> "已下载"
        else -> null
    }

    fun task(id: String): DownloadTask? = if (Downloads.ready) Downloads.manager.store.get(id) else null

    fun start(e: MediaEntry) {
        val o = DownloadPolicy.check(e)
        val src = o.source ?: return MoonToaster.show(o.reason ?: "不能下载")
        start(src)
    }

    fun start(src: DownloadSource) {
        if (!Downloads.ready) return
        val msg = when (Downloads.manager.request(src)) {
            DownloadManager.Request.STARTED -> "开始下载 · 在 库 › 我的下载 查看"
            DownloadManager.Request.RESUMED -> "继续下载"
            DownloadManager.Request.ALREADY_ACTIVE -> "正在下载 · 在 库 › 我的下载 查看"
            DownloadManager.Request.ALREADY_DONE -> "已下载 · 在 库 › 我的下载"
        }
        MoonToaster.show(msg, if (msg.startsWith("已下载")) Icons.Outlined.CheckCircle else Icons.Outlined.CloudDownload)
    }

    fun failed(reason: String) = MoonToaster.show(reason, Icons.Outlined.ErrorOutline)
}
