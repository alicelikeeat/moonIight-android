package com.moonlight.feed.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject

/** 朋友圈自动播放. */
enum class MomentsAutoplayMode(val label: String) {
    ALL("全部同时播放"), PICKED("只播指定的"), OFF("关闭");
    companion object {
        /** v5 value, or the v4 `moments_autoplay` string (ALWAYS / WIFI → 全部, OFF → 关闭). */
        fun of(raw: String?): MomentsAutoplayMode = entries.firstOrNull { it.name == raw } ?: if (raw == "OFF") OFF else ALL
    }
}

/** Where a 定时长按 was held — each context has its own assignable action. */
enum class HoldContext(val label: String) { FEED_ITEM("首页内容"), TAB("底部标签"), MOMENT_VIDEO("朋友圈视频"), PLAYER("播放页") }

enum class DeleteAnim(val label: String) { SHRED("碎纸机"), FADE("简单淡出"), NONE("关") }

/**
 * v5.0 settings in one JSON blob (DataStore key `v5_prefs`): 朋友圈自动播放, 定时长按, 全部按键, 录屏, 星轨速度, 删除动画, 最近删除.
 * Pure; unknown / missing fields fall back to the defaults below (tested).
 */
data class V5Prefs(
    val momentsMode: MomentsAutoplayMode = MomentsAutoplayMode.ALL,
    /** Network videos only on Wi‑Fi (local files always play) — kept from v4 「仅 Wi‑Fi」. */
    val autoplayWifiOnly: Boolean = false,
    /** 只播指定的: friends whose posts always autoplay. */
    val autoplayFriends: Set<String> = emptySet(),
    // 定时长按 ------------------------------------------------------------------------------------------
    val timedOn: Boolean = false,
    /** Threshold in seconds: 5 / 10 / custom 3…15. */
    val timedSeconds: Float = 5f,
    val timedShowTime: Boolean = true,
    val timedCountdown: Boolean = true,
    val timedAutoFire: Boolean = true,
    /** Context → [com.moonlight.feed.ui.design.MenuAction.key] that fires at the threshold. Empty = just open the ring. */
    val timedAssign: Map<HoldContext, String> = emptyMap(),
    /** Hold the screen centre 5 s → 全部按键. Own toggle, default ON. */
    val centerPanel: Boolean = true,
    // 录屏 --------------------------------------------------------------------------------------------
    val recordMic: Boolean = false,
    val recordAppAudio: Boolean = true,
    val recordToGallery: Boolean = false,
    // 动画 --------------------------------------------------------------------------------------------
    /** 星轨 speed multiplier 0.5…2 (慢 0.7 · 标准 1 · 快 1.4). */
    val starSpeed: Float = 1f,
    val deleteAnim: DeleteAnim = DeleteAnim.SHRED,
    /** 最近删除 keeps items this many days. */
    val trashDays: Int = 30,
) {
    val timedMs: Long get() = (timedSeconds.coerceIn(3f, 15f) * 1000).toLong()

    fun encode(): String = JSONObject().apply {
        put("momentsMode", momentsMode.name); put("wifiOnly", autoplayWifiOnly); put("friends", JSONArray(autoplayFriends.sorted()))
        put("timedOn", timedOn); put("timedSeconds", timedSeconds.toDouble()); put("showTime", timedShowTime); put("countdown", timedCountdown)
        put("autoFire", timedAutoFire); put("assign", JSONObject().apply { timedAssign.forEach { (k, v) -> put(k.name, v) } })
        put("centerPanel", centerPanel); put("recordMic", recordMic); put("recordAppAudio", recordAppAudio); put("recordToGallery", recordToGallery)
        put("starSpeed", starSpeed.toDouble()); put("deleteAnim", deleteAnim.name); put("trashDays", trashDays)
    }.toString()

    companion object {
        const val SLOW = 0.7f; const val STANDARD = 1f; const val FAST = 1.4f
        fun clampSpeed(v: Float) = v.coerceIn(0.5f, 2f)
        fun clampSeconds(v: Float) = v.coerceIn(3f, 15f)

        /** [legacyMoments] = v4 `moments_autoplay` (used only when the blob has no mode yet). */
        fun decode(raw: String?, legacyMoments: String? = null): V5Prefs {
            val d = V5Prefs(momentsMode = MomentsAutoplayMode.of(legacyMoments), autoplayWifiOnly = legacyMoments == "WIFI")
            if (raw.isNullOrBlank()) return d
            val o = runCatching { JSONObject(raw) }.getOrNull() ?: return d
            fun b(k: String, def: Boolean) = if (o.has(k)) o.optBoolean(k, def) else def
            fun f(k: String, def: Float) = if (o.has(k)) o.optDouble(k, def.toDouble()).toFloat().takeIf { !it.isNaN() } ?: def else def
            return V5Prefs(
                momentsMode = if (o.has("momentsMode")) MomentsAutoplayMode.of(o.optString("momentsMode")) else d.momentsMode,
                autoplayWifiOnly = b("wifiOnly", d.autoplayWifiOnly),
                autoplayFriends = o.optJSONArray("friends")?.let { a -> (0 until a.length()).map { a.optString(it) }.filter { it.isNotBlank() }.toSet() } ?: emptySet(),
                timedOn = b("timedOn", false), timedSeconds = clampSeconds(f("timedSeconds", 5f)),
                timedShowTime = b("showTime", true), timedCountdown = b("countdown", true), timedAutoFire = b("autoFire", true),
                timedAssign = o.optJSONObject("assign")?.let { a -> a.keys().asSequence().mapNotNull { k ->
                    val c = HoldContext.entries.firstOrNull { it.name == k } ?: return@mapNotNull null
                    val v = a.optString(k).takeIf { it.isNotBlank() && TimedActions.assignable(c, it) } ?: return@mapNotNull null
                    c to v
                }.toMap() } ?: emptyMap(),
                centerPanel = b("centerPanel", true), recordMic = b("recordMic", false), recordAppAudio = b("recordAppAudio", true),
                recordToGallery = b("recordToGallery", false), starSpeed = clampSpeed(f("starSpeed", 1f)),
                deleteAnim = DeleteAnim.entries.firstOrNull { it.name == o.optString("deleteAnim") } ?: DeleteAnim.SHRED,
                trashDays = o.optInt("trashDays", 30).coerceIn(1, 365),
            )
        }
    }
}

/**
 * What a 定时长按 may fire, per context. Keys match [com.moonlight.feed.ui.design.MenuAction.key] on the rings.
 * Destructive actions (删除 …) are never in these lists, so they can't be assigned or auto-fired.
 */
object TimedActions {
    /** [titles] = ring / sheet titles this option stands for in its context (toggles have two). [reversible] → 撤销. */
    data class Option(val key: String, val label: String, val reversible: Boolean, val titles: Set<String>)
    private fun o(key: String, label: String, reversible: Boolean, vararg titles: String) = Option(key, label, reversible, titles.toSet())
    val byContext: Map<HoldContext, List<Option>> = mapOf(
        HoldContext.FEED_ITEM to listOf(
            o("fav", "收藏 / 取消收藏", true, "收藏", "取消收藏"), o("mute", "静音", true, "静音"), o("play", "播放 / 暂停", true, "播放", "暂停"),
            o("loop", "循环播放", true, "循环播放"), o("share", "分享", false, "分享"), o("save", "保存到相册", false, "保存到相册"),
            o("post", "发到朋友圈", false, "发到朋友圈"), o("info", "视频信息", false, "视频信息", "信息")),
        HoldContext.TAB to listOf(
            o("random", "随机一条", false, "随机一条", "随机一下"), o("reshuffle", "重新洗牌", false, "重新洗牌"), o("top", "回到顶部", false, "回到顶部"),
            o("record", "录屏", false, "录屏"), o("compose", "发动态", false, "发动态"), o("settings", "设置", false, "设置")),
        HoldContext.MOMENT_VIDEO to listOf(
            o("mv_play", "播放 / 暂停", true, "播放", "暂停"), o("mv_mute", "静音 / 打开声音", true, "静音", "打开声音"),
            o("mv_open", "进入播放页面", false, "进入播放页面"), o("mv_save", "保存到相册", false, "保存到相册", "保存")),
        HoldContext.PLAYER to listOf(
            o("fav", "收藏", true, "收藏", "已收藏"), o("mute", "静音 / 打开声音", true, "静音", "打开声音"), o("share", "分享", false, "分享"),
            o("save", "保存到相册", false, "保存到相册"), o("random", "随机一条", false, "随机一条"), o("record", "录屏", false, "录屏")),
    )
    fun assignable(c: HoldContext, key: String) = byContext[c].orEmpty().any { it.key == key }
    fun option(c: HoldContext, key: String?) = key?.let { k -> byContext[c].orEmpty().firstOrNull { it.key == k } }
}

/** App-wide observable copy (set from the settings flow in MoonApp; tests set it directly). */
object V5 {
    var prefs by mutableStateOf(V5Prefs())
}
