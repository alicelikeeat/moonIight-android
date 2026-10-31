package com.moonlight.feed.data

import org.json.JSONArray
import org.json.JSONObject

/** One themable button: stable [id] (persisted), Chinese [name], settings [group]. */
data class SkinButton(val id: String, val name: String, val group: String)

/** Every button whose background can be a picture or a video (按键背景). Ids are stable — never rename. */
object ButtonRegistry {
    const val G_RING = "长按表盘"
    const val G_TAB = "底部标签栏"
    const val G_PLAYER = "播放器"
    const val G_PAGE = "页面按钮"
    const val G_DIALOG = "弹窗与面板"

    val all: List<SkinButton> = listOf(
        SkinButton("ring.button", "表盘按钮", G_RING),
        SkinButton("ring.more", "表盘中心 ···", G_RING),
        SkinButton("ring.title", "表盘标题", G_RING),
        SkinButton("ring.sub", "表盘二级选项", G_RING),
        SkinButton("tab.bar", "标签栏底板", G_TAB),
        SkinButton("tab.home", "首页", G_TAB),
        SkinButton("tab.discover", "发现", G_TAB),
        SkinButton("tab.capture", "拍摄 ●", G_TAB),
        SkinButton("tab.moments", "朋友圈", G_TAB),
        SkinButton("tab.me", "我", G_TAB),
        SkinButton("player.like", "喜欢", G_PLAYER),
        SkinButton("player.mute", "静音", G_PLAYER),
        SkinButton("player.more", "更多", G_PLAYER),
        SkinButton("player.play", "播放 / 暂停", G_PLAYER),
        SkinButton("player.counter", "计数 1/22", G_PLAYER),
        SkinButton("player.home", "全屏播放 · 回到首页", G_PLAYER),
        SkinButton("player.menu", "全屏播放 · 更多", G_PLAYER),
        SkinButton("moments.compose", "发动态", G_PAGE),
        SkinButton("moments.friends", "好友", G_PAGE),
        SkinButton("moments.chats", "消息", G_PAGE),
        SkinButton("chat.send", "聊天 · 发送", G_PAGE),
        SkinButton("chat.new", "新对话", G_PAGE),
        SkinButton("friends.new", "新建好友", G_PAGE),
        SkinButton("page.back", "返回（所有页面）", G_PAGE),
        SkinButton("page.more", "主页 · 更多", G_PAGE),
        SkinButton("discover.refresh", "发现 · 刷新", G_PAGE),
        SkinButton("discover.add", "发现 · 加入", G_PAGE),
        SkinButton("dialog.action", "弹窗操作按钮", G_DIALOG),
        SkinButton("dialog.cancel", "弹窗取消", G_DIALOG),
    )
    val ids: Set<String> = all.map { it.id }.toSet()
    val groups: List<String> = all.map { it.group }.distinct()
    fun byId(id: String) = all.firstOrNull { it.id == id }
    fun inGroup(g: String) = all.filter { it.group == g }
}

enum class SkinKind(val label: String) { PHOTO("图片"), VIDEO("视频") }

/**
 * A picked picture / video. [owner] set → private to that one button (created from its own editor or 各自选择);
 * null → a shared skin several buttons may use. [scrim] 0…1 = how strongly it is tinted under the icon.
 */
data class SkinDef(
    val id: String, val kind: SkinKind, val path: String,
    val focus: MediaFocus? = null, val scrim: Float = DEFAULT_SCRIM, val owner: String? = null,
) {
    val video get() = kind == SkinKind.VIDEO
    companion object { const val DEFAULT_SCRIM = 0.5f }
}

/**
 * 按键背景: the skins and which button uses which. Pure; unit-tested.
 * Nothing here deletes a file: removing a skin or resetting a button only forgets references; files no skin uses
 * are offered by [unusedFiles] for an explicit 清理未使用.
 */
data class ButtonSkinPrefs(val skins: List<SkinDef> = emptyList(), val assign: Map<String, String> = emptyMap()) {
    fun skin(id: String?) = skins.firstOrNull { it.id == id }
    /** The skin [buttonId] uses (null = theme). */
    fun skinFor(buttonId: String): SkinDef? = skin(assign[buttonId])
    fun usersOf(skinId: String) = assign.filterValues { it == skinId }.keys

    private fun nextId(): String { var k = skins.size + 1; while (skins.any { it.id == "s$k" }) k++; return "s$k" }

    /** Adds a skin from a freshly picked file. Returns the prefs and the new skin id. */
    fun addSkin(kind: SkinKind, path: String, owner: String? = null): Pair<ButtonSkinPrefs, String> {
        val id = nextId()
        return copy(skins = skins + SkinDef(id, kind, path, owner = owner)) to id
    }

    /** Points [buttons] at [skinId]; null = 跟随主题. Unknown buttons / skins are ignored. */
    fun assign(buttons: Collection<String>, skinId: String?): ButtonSkinPrefs {
        val ok = buttons.filter { it in ButtonRegistry.ids }
        if (skinId != null && skin(skinId) == null) return this
        val m = assign.toMutableMap()
        ok.forEach { if (skinId == null) m.remove(it) else m[it] = skinId }
        return copy(assign = m)
    }

    /** One button picks its own new file → a private skin for it alone. */
    fun pickPrivate(button: String, kind: SkinKind, path: String): ButtonSkinPrefs {
        val (p, id) = addSkin(kind, path, owner = button)
        return p.assign(listOf(button), id)
    }

    /** Several buttons, one new file: 共用同一个. */
    fun pickShared(buttons: Collection<String>, kind: SkinKind, path: String): ButtonSkinPrefs {
        val (p, id) = addSkin(kind, path)
        return p.assign(buttons, id)
    }

    /** Forgets a skin and every assignment to it (those buttons go back to the theme). Files are NOT deleted. */
    fun deleteSkin(skinId: String) = copy(skins = skins.filterNot { it.id == skinId }, assign = assign.filterValues { it != skinId })

    fun withFocus(skinId: String, f: MediaFocus?) = copy(skins = skins.map { if (it.id == skinId) it.copy(focus = f) else it })
    fun withScrim(skinId: String, v: Float) = copy(skins = skins.map { if (it.id == skinId) it.copy(scrim = v.coerceIn(0f, 1f)) else it })

    /** Files under the media folder that no skin references (and that [alsoUsed] — 首页背景, avatars… — doesn't either). */
    fun unusedFiles(files: Collection<String>, alsoUsed: Set<String> = emptySet()): List<String> {
        val used = skins.map { it.path }.toSet() + alsoUsed
        return files.filter { it !in used }
    }

    fun encode(): String = JSONObject().apply {
        put("v", 1)
        put("skins", JSONArray().apply {
            skins.forEach { s -> put(JSONObject().apply {
                put("id", s.id); put("kind", s.kind.name); put("path", s.path); put("scrim", s.scrim.toDouble())
                s.focus?.let { put("focus", it.encode()) }; s.owner?.let { put("owner", it) }
            }) }
        })
        put("assign", JSONObject().apply { assign.toSortedMap().forEach { (b, s) -> put(b, s) } })
    }.toString()

    companion object {
        /** Lenient: unknown / removed button ids, dangling skin ids and malformed entries are dropped. */
        fun decode(raw: String?, known: Set<String> = ButtonRegistry.ids): ButtonSkinPrefs {
            if (raw.isNullOrBlank()) return ButtonSkinPrefs()
            return runCatching {
                val o = JSONObject(raw)
                val arr = o.optJSONArray("skins") ?: JSONArray()
                val skins = (0 until arr.length()).mapNotNull { i ->
                    val s = arr.optJSONObject(i) ?: return@mapNotNull null
                    val kind = SkinKind.entries.firstOrNull { it.name == s.optString("kind") } ?: return@mapNotNull null
                    val path = s.optString("path").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                    SkinDef(
                        s.optString("id").takeIf { it.isNotBlank() } ?: return@mapNotNull null, kind, path,
                        MediaFocus.decode(s.optString("focus").takeIf { it.isNotBlank() }),
                        s.optDouble("scrim", SkinDef.DEFAULT_SCRIM.toDouble()).toFloat().coerceIn(0f, 1f),
                        s.optString("owner").takeIf { it.isNotBlank() && it in known },
                    )
                }.distinctBy { it.id }
                val ids = skins.map { it.id }.toSet()
                val a = o.optJSONObject("assign")
                val assign = buildMap {
                    a?.keys()?.forEach { b -> val s = a.optString(b); if (b in known && s in ids) put(b, s) }
                }
                ButtonSkinPrefs(skins, assign)
            }.getOrDefault(ButtonSkinPrefs())
        }

        /**
         * Video skins allowed to play right now: the first [cap] skins (in the order they became visible) that have
         * visible users. The rest show their poster frame until a slot frees up.
         */
        fun playingVideos(order: List<String>, users: Map<String, Int>, cap: Int = MAX_PLAYING_VIDEOS): List<String> =
            order.filter { (users[it] ?: 0) > 0 }.take(cap)

        const val MAX_PLAYING_VIDEOS = 4
    }
}
