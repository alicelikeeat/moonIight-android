package com.moonlight.feed.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CenterFocusStrong
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moonlight.feed.MoonApp
import com.moonlight.feed.data.ButtonRegistry
import com.moonlight.feed.data.ButtonSkinPrefs
import com.moonlight.feed.data.SkinDef
import com.moonlight.feed.data.SkinKind
import com.moonlight.feed.ui.SettingsDivider
import com.moonlight.feed.ui.SettingsRow
import com.moonlight.feed.ui.design.ButtonSkins
import com.moonlight.feed.ui.design.GlassPill
import com.moonlight.feed.ui.design.LocalOverlay
import com.moonlight.feed.ui.design.MenuAction
import com.moonlight.feed.ui.design.MoonToaster
import com.moonlight.feed.ui.design.SkinBitmaps
import com.moonlight.feed.ui.design.glass
import com.moonlight.feed.ui.design.moonPress
import com.moonlight.feed.ui.design.rememberSkinPaint
import com.moonlight.feed.ui.design.skinFill
import com.moonlight.feed.ui.theme.LocalMoonColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

// =================================================================== 按键背景 (settings)

/** Row in 外观 that opens the per-button list. */
@Composable
fun ButtonSkinEntryRow(s: com.moonlight.feed.data.SettingsRepository.Snapshot, a: SettingsActions) {
    val p = s.buttonSkinPrefs()
    SettingsRow(
        "按键背景", subtitle = if (p.assign.isEmpty()) "所有按键跟随主题 · 可给任意按键单独设图片或视频" else "${p.assign.size} 个按键已自定义 · ${p.skins.size} 个背景",
        icon = Icons.Outlined.Palette, iconTint = Color(0xFFE7B45A), onClick = a.openButtonSkins,
    )
}

/** Short description of what a button uses now. */
fun skinLabel(p: ButtonSkinPrefs, buttonId: String): String {
    val d = p.skinFor(buttonId) ?: return "跟随主题"
    val shared = p.usersOf(d.id).size
    return d.kind.label + when { d.owner != null -> " · 专属"; shared > 1 -> " · 与 ${shared - 1} 个按键共用"; else -> "" }
}

/** Small live preview of a button with [def] (null → the theme look). */
@Composable
fun ButtonSkinPreview(def: SkinDef?, size: Dp = 30.dp, icon: ImageVector = Icons.Outlined.TouchApp) {
    val c = LocalMoonColors.current
    val paint = def?.let { rememberSkinPaint(it) }
    Box(
        Modifier.size(size).then(if (paint != null) Modifier.skinFill(paint, CircleShape) else Modifier.glass(CircleShape, c)),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = paint?.ink ?: c.text, modifier = Modifier.size(size * 0.5f)) }
}

@Composable
private fun CheckBox(state: Boolean?, onClick: () -> Unit) {
    // state: true = all, null = some, false = none
    val c = LocalMoonColors.current
    Box(
        Modifier.size(40.dp).moonPress(onClick, highlightShape = CircleShape), contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(22.dp).clip(RoundedCornerShape(7.dp))
                .background(if (state != false) c.accent else Color.Transparent)
                .border(1.5.dp, if (state != false) c.accent else c.textDim.copy(0.6f), RoundedCornerShape(7.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (state == true) Icon(Icons.Outlined.Check, "已选", tint = c.onAccent, modifier = Modifier.size(16.dp))
            if (state == null) Icon(Icons.Outlined.Remove, "部分已选", tint = c.onAccent, modifier = Modifier.size(16.dp))
        }
    }
}

/**
 * The list: grouped by area, a checkbox per button + per group, 全选 / 反选, a live preview and a reset per row.
 * Tapping a row opens that button's own editor.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ButtonSkinList(
    prefs: ButtonSkinPrefs,
    selection: Set<String>,
    onSelection: (Set<String>) -> Unit,
    onOpen: (String) -> Unit,
    onReset: (String) -> Unit,
    onBatch: () -> Unit,
    onCleanup: () -> Unit,
) {
    val c = LocalMoonColors.current
    val all = ButtonRegistry.ids
    Column(Modifier.fillMaxWidth()) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 10.dp)) {
            GlassPill("全选", { onSelection(all) }, accent = selection.size == all.size)
            GlassPill("反选", { onSelection(all - selection) })
            if (selection.isNotEmpty()) GlassPill("取消选择", { onSelection(emptySet()) })
            GlassPill("清理未使用", onCleanup, icon = Icons.Outlined.CleaningServices)
        }
        for (g in ButtonRegistry.groups) {
            val ids = ButtonRegistry.inGroup(g).map { it.id }
            val n = ids.count { it in selection }
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                CheckBox(if (n == 0) false else if (n == ids.size) true else null) {
                    onSelection(if (n == ids.size) selection - ids.toSet() else selection + ids)
                }
                Text(g, style = MaterialTheme.typography.labelLarge, color = c.textDim, fontWeight = FontWeight.SemiBold)
            }
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(c.text.copy(alpha = 0.05f))) {
                ButtonRegistry.inGroup(g).forEachIndexed { i, b ->
                    if (i > 0) SettingsDivider()
                    val on = b.id in selection
                    val def = prefs.skinFor(b.id)
                    Row(
                        Modifier.fillMaxWidth().moonPress({ onOpen(b.id) }, pressedScale = 0.98f, highlight = c.text.copy(0.06f)).padding(end = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CheckBox(on) { onSelection(if (on) selection - b.id else selection + b.id) }
                        ButtonSkinPreview(def)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                            Text(b.name, style = MaterialTheme.typography.bodyMedium, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(skinLabel(prefs, b.id), style = MaterialTheme.typography.bodySmall, color = c.textDim, maxLines = 1)
                        }
                        if (def != null) Box(Modifier.size(40.dp).moonPress({ onReset(b.id) }, highlightShape = CircleShape), contentAlignment = Alignment.Center) {
                            Icon(Icons.Outlined.Replay, "恢复主题：${b.name}", tint = c.textDim, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        if (selection.isNotEmpty()) GlassPill("为已选的 ${selection.size} 个按键设置背景", onBatch, icon = Icons.Outlined.Palette, accent = true)
    }
}

/** One button's own editor: 跟随主题 / 选图片 / 选视频 / an existing background, + 调整画面, 遮罩强度, 删除. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ButtonSkinEditor(
    buttonId: String,
    prefs: ButtonSkinPrefs,
    onTheme: () -> Unit,
    onPick: (video: Boolean) -> Unit,
    onUse: (skinId: String) -> Unit,
    onAdjust: () -> Unit,
    onScrim: (Float) -> Unit,
    onDelete: () -> Unit,
    onBack: () -> Unit,
) {
    val c = LocalMoonColors.current
    val b = ButtonRegistry.byId(buttonId) ?: return
    val def = prefs.skinFor(buttonId)
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ButtonSkinPreview(def, 64.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(b.name, style = MaterialTheme.typography.titleMedium, color = c.text)
                Text(b.group + " · " + skinLabel(prefs, buttonId), style = MaterialTheme.typography.bodySmall, color = c.textDim)
            }
        }
        Spacer(Modifier.height(14.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            GlassPill("跟随主题", onTheme, accent = def == null)
            GlassPill("选图片", { onPick(false) }, icon = Icons.Outlined.Image)
            GlassPill("选视频", { onPick(true) }, icon = Icons.Outlined.Movie)
        }
        val others = prefs.skins.filter { it.id != def?.id && (it.owner == null || it.owner == buttonId) }
        if (others.isNotEmpty()) {
            Text("已有背景", style = MaterialTheme.typography.labelMedium, color = c.textDim, modifier = Modifier.padding(top = 14.dp, bottom = 8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                others.forEach { s ->
                    Box(Modifier.moonPress({ onUse(s.id) }, highlightShape = CircleShape)) { ButtonSkinPreview(s, 40.dp, if (s.video) Icons.Outlined.Movie else Icons.Outlined.Image) }
                }
            }
        }
        if (def != null) {
            Spacer(Modifier.height(10.dp))
            SettingsRow("调整画面", subtitle = "拖动 · 双指缩放", icon = Icons.Outlined.CenterFocusStrong, iconTint = Color(0xFF7FD1AE), onClick = onAdjust)
            Text("遮罩强度 · ${(def.scrim * 100).toInt()}%（图标对比度自动）", style = MaterialTheme.typography.labelMedium, color = c.textDim, modifier = Modifier.padding(start = 14.dp, top = 4.dp))
            var v by remember(def.id) { mutableStateOf(def.scrim) }
            androidx.compose.material3.Slider(v, { v = it }, onValueChangeFinished = { onScrim(v) }, modifier = Modifier.padding(horizontal = 14.dp),
                colors = androidx.compose.material3.SliderDefaults.colors(thumbColor = c.accent, activeTrackColor = c.accent, inactiveTrackColor = c.hairline))
            SettingsRow(if (def.owner != null) "删除这个背景" else "删除这个背景（所有使用它的按键恢复主题）", icon = Icons.Outlined.Delete, destructive = true, onClick = onDelete,
                subtitle = "文件保留在 App 内，可在列表里「清理未使用」时删除")
        }
        Spacer(Modifier.height(8.dp))
        GlassPill("返回列表", onBack)
    }
}

/** Wires the list / editor / pickers / batch flow / cleanup to settings storage. Returns "open the list". */
@Composable
fun rememberButtonSkinController(): () -> Unit {
    val app = MoonApp.instance
    val overlay = LocalOverlay.current
    val scope = rememberCoroutineScope()
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val dir = remember { File(ctx.filesDir, "skins").apply { mkdirs() } }
    var selection by remember { mutableStateOf(setOf<String>()) }
    // what the next picked file is for
    class Pending(val targets: List<String>, val shared: Boolean, val video: Boolean)
    var pending by remember { mutableStateOf<Pending?>(null) }
    val holder = remember { arrayOfNulls<(Uri) -> Unit>(1) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) holder[0]?.invoke(uri) else pending = null }

    fun update(f: (ButtonSkinPrefs) -> ButtonSkinPrefs) = scope.launch { ButtonSkins.prefs = app.settings.updateButtonSkins(f) }
    lateinit var openList: () -> Unit
    lateinit var openEditor: (String) -> Unit
    fun launchPick(p: Pending) {
        pending = p
        if (p.targets.size > 1 && !p.shared) MoonToaster.show("为「${ButtonRegistry.byId(p.targets.first())?.name}」选择${if (p.video) "视频" else "图片"}")
        picker.launch(arrayOf(if (p.video) "video/*" else "image/*"))
    }
    holder[0] = { uri ->
        val p = pending
        if (p != null) scope.launch {
            val path = withContext(Dispatchers.IO) {
                runCatching {
                    val ext = if (p.video) "mp4" else "jpg"
                    val out = File(dir, "skin-${System.currentTimeMillis()}.$ext")
                    ctx.contentResolver.openInputStream(uri)?.use { i -> out.outputStream().use { i.copyTo(it) } }
                    out.takeIf { it.length() > 0 }?.absolutePath
                }.getOrNull()
            }
            val ok = path != null && withContext(Dispatchers.IO) { SkinBitmaps.load(path, p.video) } != null
            if (!ok) { path?.let { File(it).delete() }; MoonToaster.show("无法解码这个文件 · 按键仍跟随主题"); pending = null; return@launch }
            val kind = if (p.video) SkinKind.VIDEO else SkinKind.PHOTO
            when {
                p.shared -> update { it.pickShared(p.targets, kind, path!!) }
                else -> update { it.pickPrivate(p.targets.first(), kind, path!!) }
            }
            // 各自选择: step to the next selected button
            if (!p.shared && p.targets.size > 1) launchPick(Pending(p.targets.drop(1), false, p.video))
            else { pending = null; MoonToaster.show("已设置按键背景", Icons.Outlined.CheckCircle); if (p.targets.size == 1) openEditor(p.targets.first()) else openList() }
        }
    }
    fun cleanup() {
        val files = dir.listFiles()?.map { it.absolutePath }.orEmpty()
        val unused = ButtonSkins.prefs.unusedFiles(files)
        if (unused.isEmpty()) { MoonToaster.show("没有未使用的文件"); return }
        val mb = unused.sumOf { File(it).length() } / 1_048_576.0
        overlay.confirm("清理未使用？", "${unused.size} 个文件（%.1f MB）没有被任何按键或背景使用。".format(mb), listOf(
            MenuAction("删除这些文件", destructive = true) {
                unused.forEach { com.moonlight.feed.data.StoredCopies.delete(it, dir); SkinBitmaps.forget(it) }
                MoonToaster.show("已清理 ${unused.size} 个文件", Icons.Outlined.CheckCircle)
            },
        ))
    }
    fun batch() {
        val ids = selection.toList()
        val p = ButtonSkins.prefs
        fun newMedia(video: Boolean) {
            if (ids.size == 1) launchPick(Pending(ids, false, video))
            else overlay.confirm("${ids.size} 个按键", "新${if (video) "视频" else "图片"}怎么用？", listOf(
                MenuAction("共用同一个") { launchPick(Pending(ids, true, video)) },
                MenuAction("各自选择（依次为每个按键选）") { launchPick(Pending(ids, false, video)) },
            ))
        }
        overlay.confirm("为 ${ids.size} 个按键设置背景", null, buildList {
            add(MenuAction("跟随主题") { update { it.assign(ids, null) }; openList() })
            p.skins.filter { it.owner == null }.forEach { s -> add(MenuAction("背景 ${s.id} · ${s.kind.label}（${p.usersOf(s.id).size} 个在用）") { update { it.assign(ids, s.id) }; openList() }) }
            add(MenuAction("新图片…") { newMedia(false) })
            add(MenuAction("新视频…") { newMedia(true) })
        })
    }
    openList = {
        overlay.showSheet("按键背景") { _ ->
            Column(Modifier.heightIn(max = 620.dp).verticalScroll(rememberScrollState())) {
                ButtonSkinList(
                    ButtonSkins.prefs, selection, { selection = it }, { openEditor(it) },
                    { id -> update { it.assign(listOf(id), null) } }, { batch() }, { cleanup() },
                )
            }
        }
    }
    openEditor = { id ->
        overlay.showSheet(null) { _ ->
            val prefs = ButtonSkins.prefs
            val def = prefs.skinFor(id)
            ButtonSkinEditor(
                id, prefs,
                onTheme = { update { it.assign(listOf(id), null) } },
                onPick = { v -> launchPick(Pending(listOf(id), false, v)) },
                onUse = { sid -> update { it.assign(listOf(id), sid) } },
                onAdjust = {
                    def?.let { d -> com.moonlight.feed.ui.design.FocusEditing.open(com.moonlight.feed.ui.design.FocusRequest(d.path, d.video, 0, "调整画面", d.focus ?: com.moonlight.feed.data.MediaFocus.DEFAULT) { f ->
                        update { it.withFocus(d.id, f) } }) }
                },
                onScrim = { v -> def?.let { d -> update { it.withScrim(d.id, v) } } },
                onDelete = {
                    def?.let { d ->
                        val n = prefs.usersOf(d.id).size
                        overlay.confirm("删除这个背景？", if (n > 1) "$n 个按键在用，都会恢复主题。文件保留，可稍后「清理未使用」。" else "这个按键恢复主题。文件保留，可稍后「清理未使用」。", listOf(
                            MenuAction("删除背景", destructive = true) { update { it.deleteSkin(d.id) } },
                            MenuAction("只让这个按键恢复主题") { update { it.assign(listOf(id), null) } },
                        ))
                    }
                },
                onBack = { openList() },
            )
        }
    }
    return { openList() }
}
