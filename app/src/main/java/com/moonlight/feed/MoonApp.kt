package com.moonlight.feed

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.moonlight.feed.data.LibraryRepository
import com.moonlight.feed.data.SettingsRepository
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MoonApp : Application(), ImageLoaderFactory {
    lateinit var library: LibraryRepository
        private set
    lateinit var settings: SettingsRepository
        private set
    lateinit var http: OkHttpClient
        private set
    lateinit var sources: com.moonlight.feed.data.SourcesRepository
        private set
    lateinit var social: com.moonlight.feed.data.social.SocialRepository
        private set
    @Volatile var chatServerUrl: String = ""
    /** v5.0 最近删除. A purged 库 item's app-private file goes with it. */
    val trash: com.moonlight.feed.data.trash.TrashStore by lazy {
        com.moonlight.feed.data.trash.TrashStore(java.io.File(filesDir, "trash.json")) { t ->
            t.entry?.localPath?.let { p -> if (p.startsWith(filesDir.absolutePath) || p.startsWith(library.mediaDir().absolutePath)) runCatching { java.io.File(p).delete() } }
        }
    }
    private var purged = false

    override fun onCreate() {
        super.onCreate()
        instance = this
        http = OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
        library = LibraryRepository(this)
        settings = SettingsRepository(this)
        sources = com.moonlight.feed.data.SourcesRepository(this)
        social = com.moonlight.feed.data.social.SocialRepository(this) { com.moonlight.feed.data.social.ChatServerConfig(chatServerUrl) }
        // 主动下载: re-enqueues unfinished tasks (KEEP) after process death / reboot
        com.moonlight.feed.data.download.Downloads.init(this)
        kotlinx.coroutines.MainScope().launch { settings.flow.collect {
            chatServerUrl = it.chatServerUrl
            com.moonlight.feed.ui.design.MenuStyle.backdrop = com.moonlight.feed.data.MenuBackdrop.of(it.menuBackdrop)
            com.moonlight.feed.ui.design.MenuStyle.videoDisplay = com.moonlight.feed.data.VideoFit.of(it.videoDisplay)
            com.moonlight.feed.ui.design.MenuStyle.select = com.moonlight.feed.data.MenuSelect.of(it.menuSelect)
            com.moonlight.feed.ui.design.MenuStyle.anim = it.ringAnim
            com.moonlight.feed.ui.design.MenuStyle.animSet = com.moonlight.feed.data.RingAnimPick.parseSet(it.ringAnimSet)
            com.moonlight.feed.ui.design.MenuStyle.present = com.moonlight.feed.data.HoldPresent.of(it.holdPresent)
            com.moonlight.feed.ui.design.MenuStyle.tabDock = com.moonlight.feed.data.TabDock.of(it.tabDock)
            com.moonlight.feed.ui.design.ButtonSkins.prefs = it.buttonSkinPrefs()
            com.moonlight.feed.data.V5.prefs = it.v5Prefs()
            com.moonlight.feed.ui.design.MenuStyle.starSpeed = com.moonlight.feed.data.V5.prefs.starSpeed
            if (!purged) { purged = true; val days = com.moonlight.feed.data.V5.prefs.trashDays
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch { trash.purge(days) } }
        } }
        // Virtual-friend posts stay off until the user turns them on, then wait out the chosen interval.
        kotlinx.coroutines.MainScope().launch {
            var last = System.currentTimeMillis()
            while (true) {
                kotlinx.coroutines.delay(60_000)
                val snap = settings.flow.first()
                if (!snap.virtualFriends) continue
                val wait = com.moonlight.feed.data.VirtualCopy.intervalMinutes(snap.virtualInterval) * 60_000L
                if (System.currentTimeMillis() - last < wait) continue
                val posted = runCatching { com.moonlight.feed.data.VirtualFriends.tick(this@MoonApp, snap) }.getOrDefault(false)
                if (posted) last = System.currentTimeMillis()
            }
        }
    }

    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .okHttpClient(http)
            .crossfade(180)
            .components { add(coil.decode.VideoFrameDecoder.Factory()) }
            .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.25).build() }
            .diskCache {
                DiskCache.Builder()
                    .directory(com.moonlight.feed.data.download.AutoCache.imageDir(cacheDir))   // 自动缓存: best-effort, never a download
                    .maxSizeBytes(120L * 1024 * 1024)
                    .build()
            }
            .build()

    companion object {
        lateinit var instance: MoonApp
            private set
    }
}
