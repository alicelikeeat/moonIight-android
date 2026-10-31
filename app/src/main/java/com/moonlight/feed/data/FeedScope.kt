package com.moonlight.feed.data

/** Home feed scope: everything · on-device files only · items that need a web/embed or remote player. */
enum class FeedScope(val label: String, val hint: String) {
    ALL("全部", "全部内容"),
    LOCAL("本地", "仅设备上的图片与视频"),
    ONLINE("在线", "需联网 / 网页播放器的内容");

    fun matches(e: MediaEntry): Boolean = when (this) {
        ALL -> true
        LOCAL -> e.isLocalFile()
        ONLINE -> !e.isLocalFile()
    }

    companion object {
        fun of(name: String?): FeedScope = entries.firstOrNull { it.name == name } ?: ALL
    }
}

/** A file stored on this device (imported, captured, downloaded) that the native feed plays directly. */
fun MediaEntry.isLocalFile(): Boolean =
    (kind == MediaKind.VIDEO || kind == MediaKind.IMAGE) && !localPath.isNullOrBlank()

/**
 * Index of [current] among [values] for a segmented control; [fallback] only when [current] is unknown.
 * (v2.8 bug: the reveal-speed control used `indexOf(..).coerceAtLeast(1)`, which also turned 快 = 0 into 1.)
 */
fun <T> segmentIndex(values: List<T>, current: T, fallback: Int = 0): Int =
    values.indexOf(current).let { if (it < 0) fallback.coerceIn(0, (values.size - 1).coerceAtLeast(0)) else it }
