package com.moonlight.feed

import androidx.compose.material.icons.outlined.Close
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import androidx.compose.material.icons.outlined.CheckCircle
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.moonlight.feed.ui.design.playable
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import coil.load
import com.moonlight.feed.data.MediaEntry
import com.moonlight.feed.data.MediaKind
import com.moonlight.feed.data.PlatformId
import com.moonlight.feed.data.PlatformResolver
import java.io.File
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.compose.material.icons.outlined.AspectRatio
import com.moonlight.feed.ui.design.Haptics
import com.moonlight.feed.ui.design.LocalHazeState
import com.moonlight.feed.ui.design.MoonOverlayHost
import com.moonlight.feed.ui.design.MoonOverlayState
import com.moonlight.feed.ui.player.PlayerActions
import com.moonlight.feed.ui.player.PlayerTopBar
import com.moonlight.feed.ui.player.playerHomeMenu
import com.moonlight.feed.ui.player.playerMoreMenu
import com.moonlight.feed.ui.theme.LocalMoonColors
import com.moonlight.feed.ui.theme.MediaGlass
import com.moonlight.feed.ui.theme.MoonTheme
import com.moonlight.feed.ui.theme.ThemeMode

/**
 * Full-screen vertical pager across the whole library feed.
 * One ExoPlayer + one WebView reused on the settled page.
 * Immersive chrome auto-hides; vertical swipe works even over Bilibili WebView.
 */
class BiliPlayerActivity : ComponentActivity() {
    private lateinit var root: FrameLayout
    private lateinit var pager: ViewPager2
    private lateinit var topBar: FrameLayout
    private lateinit var overlayView: ComposeView
    private val overlayState = MoonOverlayState()
    private val chromeLabel = mutableStateOf("")
    private val chromeTitle = mutableStateOf("")
    private val chromeMuted = mutableStateOf(true)
    /** Status line facade: the glass title pill shows the label + transient messages. */
    private val status = object {
        var text: CharSequence = ""
            set(v) {
                field = v
                val parts = v.toString().split("\n", limit = 2)
                chromeLabel.value = parts[0]
                chromeTitle.value = parts.getOrNull(1) ?: (items.getOrNull(if (::pager.isInitialized) pager.currentItem else startIndex)?.title ?: "")
            }
    }
    private lateinit var progressBar: ProgressBar
    private lateinit var items: List<MediaEntry>
    private var startIndex = 0

    private var webView: WebView? = null
    private var webHost: VerticalSwipeHost? = null
    private var attachedWebPage = -1
    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null
    private var fullscreenContainer: FrameLayout? = null

    private var player: ExoPlayer? = null
    private var attachedPlayerPage = -1

    private val uiHandler = Handler(Looper.getMainLooper())
    private val hideBarRunnable = Runnable { hideTopBar() }
    private var barVisible = true
    private var muted = true // autoplay-friendly; unmute via chrome if needed
    // pause-on-leave state
    private val videoGate = com.moonlight.feed.data.PlaybackGate()
    private val embedGate = com.moonlight.feed.data.PlaybackGate()
    private var ytState = -1 // YouTube IFrame-API state reported by MoonBridge; -1 = no API page
    private var embedBlanked = false
    private var suppressEmbedAutoplay = false
    private var resumeOnReturn = true
    private var currentEmbedId: String? = null
    private val autoSkipRunnable = Runnable {
        val next = pager.currentItem + 1
        if (next < items.size) pager.setCurrentItem(next, true)
        else Toast.makeText(this, "已到末尾", Toast.LENGTH_SHORT).show()
    }
    private var errorOverlay: LinearLayout? = null

    private val embedModes = listOf(
        { bv: String -> "https://player.bilibili.com/player.html?bvid=$bv&page=1&high_quality=1&autoplay=1&danmaku=0&mute=1" },
        { bv: String -> "https://www.bilibili.com/blackboard/html5mobileplayer.html?bvid=$bv&autoplay=1" },
        { bv: String -> "https://m.bilibili.com/video/$bv" },
    )
    private var embedMode = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        com.moonlight.feed.data.record.ScreenRecorder.attach(this)
        // v5.0 全部按键 in the player: the open ring + 录屏 / 关闭播放器
        overlayState.globals = { listOf(recordAction(this), com.moonlight.feed.ui.design.MenuAction("关闭播放器", androidx.compose.material.icons.Icons.Outlined.Close) { setResult(RESULT_CANCELED); finish() }) }
        lifecycleScope.launch { com.moonlight.feed.data.record.ScreenRecorder.state.collect { st -> recResultToast(st) {} } }
        lifecycleScope.launch {
            MoonApp.instance.settings.flow.collect { s ->
                val cfg = com.moonlight.feed.ui.design.revealConfig(this@BiliPlayerActivity, s.revealOn, s.revealSpeed)
                revealMs = if (cfg.enabled) cfg.ms else 0
                resumeOnReturn = s.resumeOnReturn
            }
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
        )
        WindowCompat.setDecorFitsSystemWindows(window, false)
        hideSystemBars()

        val lib = MoonApp.instance.library
        val orderedIds = intent.getStringArrayListExtra(EXTRA_FEED_IDS)
        val localPath = intent.getStringExtra(EXTRA_LOCAL_PATH)
        items = if (localPath != null) {
            // a single profile cover / avatar file — played here, never added to the library
            if (File(localPath).exists()) listOf(
                MediaEntry(
                    id = "local:" + localPath,
                    kind = if (intent.getBooleanExtra(EXTRA_LOCAL_VIDEO, false)) MediaKind.VIDEO else MediaKind.IMAGE,
                    title = intent.getStringExtra(EXTRA_LOCAL_TITLE) ?: "",
                    localPath = localPath,
                    source = "profile",
                )
            ) else emptyList()
        } else if (!orderedIds.isNullOrEmpty()) {
            val map = lib.items.value.associateBy { it.id }
            orderedIds.mapNotNull { map[it] }.ifEmpty { lib.items.value.filter { !it.hidden } }
        } else {
            lib.items.value.filter { !it.hidden }
        }
        if (items.isEmpty()) {
            Toast.makeText(this, "没有可播放内容", Toast.LENGTH_SHORT).show()
            finish(); return
        }
        val startId = intent.getStringExtra(EXTRA_START_ID)
        startIndex = when {
            startId != null -> items.indexOfFirst { it.id == startId }.let { if (it >= 0) it else 0 }
            else -> intent.getIntExtra(EXTRA_START_INDEX, 0).coerceIn(0, items.lastIndex)
        }

        root = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
        }

        val renderers = DefaultRenderersFactory(this)
            .setEnableDecoderFallback(true)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF)
        player = ExoPlayer.Builder(this, renderers).build().apply {
            repeatMode = Player.REPEAT_MODE_ONE
            volume = 0f
            playWhenReady = true
            setHandleAudioBecomingNoisy(true)
            setAudioAttributes(com.moonlight.feed.ui.feed.MEDIA_AUDIO, !muted) // focus only while audible; loss → pause
            addListener(object : Player.Listener {
                override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) { applyFit() }
            })
        }

        pager = ViewPager2(this).apply {
            orientation = ViewPager2.ORIENTATION_VERTICAL
            offscreenPageLimit = 1
            adapter = FeedAdapter()
            setCurrentItem(startIndex, false)
            registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                override fun onPageScrollStateChanged(state: Int) {
                    if (state == ViewPager2.SCROLL_STATE_DRAGGING) scheduleHideBar()
                }
                override fun onPageSelected(position: Int) {
                    bindSettledPage(position)
                    status.text = pageLabel(position)
                    scheduleHideBar()
                }
            })
        }

        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            visibility = View.GONE
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(3),
            ).apply { topMargin = dp(52) }
        }

        topBar = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
            elevation = dp(6).toFloat()
        }
        status.text = pageLabel(startIndex)
        val actions = PlayerActions(
            home = { goHome() },
            toTop = { pager.setCurrentItem(0, true) },
            random = { if (items.size > 1) pager.setCurrentItem(items.indices.random(), false) },
            browser = { openExternal(pager.currentItem) },
            swap = { cycleEmbedMode() },
            mute = { toggleMute() },
            close = { setResult(RESULT_CANCELED); finish() },
        )
        topBar.addView(ComposeView(this).apply {
            setContent {
                MoonTheme(ThemeMode.DARK) {
                    CompositionLocalProvider(LocalMoonColors provides MediaGlass, LocalHazeState provides null) {
                        PlayerTopBar(
                            label = chromeLabel.value,
                            title = chromeTitle.value,
                            muted = chromeMuted.value,
                            onHome = { showTopBar(); scheduleHideBar(); goHome() },
                            onHomeLong = { r -> openMenu(playerHomeMenu(r, actions)) },
                            onMute = { showTopBar(); scheduleHideBar(); toggleMute() },
                            onMore = { r -> openMenu(playerMoreMenu(r, muted, actions)) },
                        )
                    }
                }
            }
        })
        overlayView = ComposeView(this).apply {
            visibility = View.GONE
            layoutParams = FrameLayout.LayoutParams(MATCH, MATCH)
            elevation = dp(12).toFloat()
            setContent {
                MoonTheme(ThemeMode.DARK) {
                    CompositionLocalProvider(LocalMoonColors provides MediaGlass) {
                        MoonOverlayHost(overlayState, backdrop = false) { }
                        val p = overlayState.menuProgress.value
                        SideEffect { setBackdropBlur(p) }
                        LaunchedEffect(overlayState.menu, overlayState.shownMenu) {
                            if (overlayState.menu == null && overlayState.shownMenu == null) {
                                visibility = View.GONE
                                setBackdropBlur(0f)
                                scheduleHideBar()
                            }
                        }
                    }
                }
            }
        }

        root.addView(
            pager,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
        root.addView(progressBar)
        root.addView(topBar)
        root.addView(overlayView)
        setContentView(root)

        // Tap top zone to reveal chrome
        root.isClickable = true
        topBar.setOnTouchListener { _, ev ->
            if (ev.action == MotionEvent.ACTION_DOWN) {
                showTopBar(); scheduleHideBar()
            }
            false
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (customView != null) {
                    (webView?.webChromeClient as? WebChromeClient)?.onHideCustomView()
                } else {
                    goHome()
                }
            }
        })

        pager.post {
            bindSettledPage(startIndex)
            scheduleHideBar()
        }
    }

    private fun pageLabel(pos: Int): String {
        val e = items.getOrNull(pos) ?: return ""
        val kind = e.platformLabel()
        return "$kind · ${pos + 1}/${items.size}\n${e.title.take(36)}"
    }

    private fun goHome() {
        finishWithResult()
    }

    private fun toggleMute() {
        muted = !muted
        chromeMuted.value = muted
        player?.volume = if (muted) 0f else 1f
        player?.setAudioAttributes(com.moonlight.feed.ui.feed.MEDIA_AUDIO, !muted)
        val pos = pager.currentItem
        if (items.getOrNull(pos)?.isWebEmbed() == true) reloadEmbed(pos)
    }

    // ---------------- blur-to-sharp reveal (View side)
    private var revealMs = 520
    private val revealAnims = HashMap<View, android.animation.ValueAnimator>()
    private val revealInterp = android.view.animation.PathInterpolator(0.16f, 1f, 0.3f, 1f)

    private fun blurPx(k: Float) = 32f * resources.displayMetrics.density * k

    /** RenderEffect blur (12+) + 1.04→1 scale + dim→full; the media keeps playing underneath. */
    private fun revealView(v: View) {
        revealAnims.remove(v)?.cancel()
        if (revealMs <= 0) { clearReveal(v); return }
        val a = android.animation.ValueAnimator.ofFloat(0f, 1f).apply {
            duration = revealMs.toLong(); interpolator = revealInterp
            addUpdateListener { va ->
                val k = va.animatedValue as Float
                v.scaleX = 1.04f - 0.04f * k; v.scaleY = v.scaleX
                v.alpha = 0.4f + 0.6f * k
                if (android.os.Build.VERSION.SDK_INT >= 31) {
                    val r = blurPx(1f - k)
                    v.setRenderEffect(if (r < 0.5f) null else android.graphics.RenderEffect.createBlurEffect(r, r, android.graphics.Shader.TileMode.CLAMP))
                }
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) { clearReveal(v); revealAnims.remove(v) }
            })
        }
        revealAnims[v] = a
        a.start()
    }

    private fun clearReveal(v: View) {
        v.scaleX = 1f; v.scaleY = 1f; v.alpha = 1f
        if (android.os.Build.VERSION.SDK_INT >= 31) v.setRenderEffect(null)
    }

    /** Blurred cover over an embed / preparing video (tiny decode below 12 = naturally soft). */
    private fun showBlurredCover(holder: PageVH, e: MediaEntry) {
        val cv = holder.cover ?: return
        val model = e.coverUrl ?: e.localPath?.let { File(it) } ?: return
        revealAnims.remove(cv)?.cancel()
        cv.alpha = 1f; cv.visibility = View.VISIBLE
        if (revealMs <= 0) return
        if (android.os.Build.VERSION.SDK_INT >= 31) {
            cv.load(model) { crossfade(false) }
            val r = blurPx(1f)
            cv.setRenderEffect(android.graphics.RenderEffect.createBlurEffect(r, r, android.graphics.Shader.TileMode.CLAMP))
            cv.scaleX = 1.04f; cv.scaleY = 1.04f
        } else cv.load(model) { size(24, 24); crossfade(false) }
    }

    /** Embed ready → the blurred cover sharpens slightly while fading away. */
    private fun fadeOutCover(holder: PageVH?) {
        val cv = holder?.cover ?: return
        if (cv.visibility != View.VISIBLE) return
        if (revealMs <= 0) { cv.visibility = View.GONE; clearReveal(cv); return }
        revealAnims.remove(cv)?.cancel()
        val a = android.animation.ValueAnimator.ofFloat(0f, 1f).apply {
            duration = revealMs.toLong(); interpolator = revealInterp
            addUpdateListener { va ->
                val k = va.animatedValue as Float
                cv.alpha = 1f - k
                cv.scaleX = 1.04f - 0.04f * k; cv.scaleY = cv.scaleX
                if (android.os.Build.VERSION.SDK_INT >= 31) {
                    val r = blurPx(1f - k)
                    cv.setRenderEffect(if (r < 0.5f) null else android.graphics.RenderEffect.createBlurEffect(r, r, android.graphics.Shader.TileMode.CLAMP))
                }
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) { cv.visibility = View.GONE; clearReveal(cv); revealAnims.remove(cv) }
            })
        }
        revealAnims[cv] = a
        a.start()
    }

    // ---------------- radial long-press over the media (works above WebView / PlayerView)
    private var routingToRing = false
    private val ringDetector by lazy {
        android.view.GestureDetector(this, object : android.view.GestureDetector.SimpleOnGestureListener() {
            override fun onLongPress(e: MotionEvent) {
                if (overlayView.visibility == View.VISIBLE) return
                if (barVisible && e.y < topBar.bottom) return   // top chrome has its own long-presses
                openMediaRing(e)
            }
        })
    }

    private fun overlayPoint(ev: MotionEvent): androidx.compose.ui.geometry.Offset {
        val loc = IntArray(2); overlayView.getLocationInWindow(loc)
        val d = IntArray(2); window.decorView.getLocationInWindow(d)
        return androidx.compose.ui.geometry.Offset(ev.x + d[0] - loc[0], ev.y + d[1] - loc[1])
    }

    private fun openMediaRing(e: MotionEvent) {
        val p = overlayPoint(e)
        val pos = pager.currentItem
        val entry = items.getOrNull(pos)?.let { e -> MoonApp.instance.library.items.value.firstOrNull { it.id == e.id } ?: e }
        overlayState.pressStartedAt = e.downTime
        overlayState.pressOrigin = p; overlayState.finger = p; overlayState.fingerDown = true
        routingToRing = true
        // the pager / WebView must not see the rest of this gesture
        val cancel = MotionEvent.obtain(e).apply { action = MotionEvent.ACTION_CANCEL }
        super.dispatchTouchEvent(cancel); cancel.recycle()
        openMenu(withRecord(this, com.moonlight.feed.ui.player.playerMediaMenu(p, entry, muted, entry?.isWebEmbed() == true, mediaActions(),
            entry?.let { com.moonlight.feed.ui.download.DownloadActions.task(it.id) })))
    }

    private fun mediaActions() = PlayerActions(
        download = {
            items.getOrNull(pager.currentItem)?.let { cur ->
                com.moonlight.feed.ui.download.DownloadActions.start(MoonApp.instance.library.items.value.firstOrNull { it.id == cur.id } ?: cur)
            }
        },
        home = { goHome() },
        toTop = { pager.setCurrentItem(0, true) },
        random = { if (items.size > 1) pager.setCurrentItem(items.indices.random(), false) },
        browser = { openExternal(pager.currentItem) },
        swap = { cycleEmbedMode() },
        mute = { toggleMute() },
        close = { setResult(RESULT_CANCELED); finish() },
        favorite = {
            items.getOrNull(pager.currentItem)?.let { cur ->
                val e = MoonApp.instance.library.items.value.firstOrNull { it.id == cur.id } ?: cur
                MoonApp.instance.library.update(e.id) { it.copy(favorite = !it.favorite) }
                com.moonlight.feed.ui.design.MoonToaster.show(if (e.favorite) "已取消收藏" else "已收藏", androidx.compose.material.icons.Icons.Outlined.CheckCircle)
            }
        },
        save = {
            items.getOrNull(pager.currentItem)?.let { e ->
                lifecycleScope.launch {
                    val r = com.moonlight.feed.data.GallerySaver.saveEntry(MoonApp.instance, MoonApp.instance.http, e)
                    com.moonlight.feed.ui.design.MoonToaster.show(if (r.ok) "已保存到相册" else "保存失败：${r.error}", androidx.compose.material.icons.Icons.Outlined.CheckCircle)
                }
            }
        },
        share = { items.getOrNull(pager.currentItem)?.let { com.moonlight.feed.ui.shareEntry(this, it) } },
        hide = {
            items.getOrNull(pager.currentItem)?.let { e ->
                MoonApp.instance.library.update(e.id) { it.copy(hidden = true) }
                com.moonlight.feed.ui.design.MoonToaster.show("已隐藏", androidx.compose.material.icons.Icons.Outlined.CheckCircle)
                if (pager.currentItem < items.lastIndex) pager.setCurrentItem(pager.currentItem + 1, true)
            }
        },
    )

    // ---------------- 视频显示: 智能 / 完整显示 / 铺满 + per-item pinch override
    private val fillOverride = HashMap<String, Boolean>()
    private var pinchTotal = 1f
    private var pinchFired = false

    private fun currentFill(): Boolean? {
        val pos = attachedPlayerPage
        val e = items.getOrNull(pos) ?: return null
        val pv = findHolder(pos)?.playerView ?: return null
        val vs = player?.videoSize ?: return null
        val ds = com.moonlight.feed.ui.feed.displaySize(vs)
        return com.moonlight.feed.data.VideoFit.fill(
            com.moonlight.feed.ui.design.MenuStyle.videoDisplay,
            ds.width.toFloat(), ds.height.toFloat(), pv.width.toFloat(), pv.height.toFloat(), fillOverride[e.id],
        )
    }

    private fun applyFit() {
        val fill = currentFill() ?: return
        findHolder(attachedPlayerPage)?.playerView?.resizeMode =
            if (fill) AspectRatioFrameLayout.RESIZE_MODE_ZOOM else AspectRatioFrameLayout.RESIZE_MODE_FIT
    }

    private val pinchDetector by lazy {
        android.view.ScaleGestureDetector(this, object : android.view.ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScaleBegin(d: android.view.ScaleGestureDetector): Boolean { pinchTotal = 1f; pinchFired = false; return true }
            override fun onScale(d: android.view.ScaleGestureDetector): Boolean {
                pinchTotal *= d.scaleFactor
                if (!pinchFired && (pinchTotal > 1.15f || pinchTotal < 0.87f)) {
                    pinchFired = true
                    val e = items.getOrNull(attachedPlayerPage) ?: return true
                    val out = pinchTotal > 1f
                    if (currentFill() != out) {
                        fillOverride[e.id] = out
                        applyFit()
                        Haptics.tick(root)
                        com.moonlight.feed.ui.design.MoonToaster.show(if (out) "铺满" else "完整显示", androidx.compose.material.icons.Icons.Outlined.AspectRatio)
                    }
                }
                return true
            }
        })
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (routingToRing) {
            overlayState.finger = overlayPoint(ev)
            val up = ev.actionMasked == MotionEvent.ACTION_UP || ev.actionMasked == MotionEvent.ACTION_CANCEL
            overlayState.fingerDown = !up
            overlayState.onPointer(!up)   // the opening finger's up is swallowed; the ring stays open for a tap
            if (up) routingToRing = false
            return true
        }
        if (overlayView.visibility != View.VISIBLE) {
            ringDetector.onTouchEvent(ev)
            pinchDetector.onTouchEvent(ev)
            if (ev.pointerCount >= 2 && items.getOrNull(attachedPlayerPage)?.kind == MediaKind.VIDEO) {
                // two fingers on a video = 适应/铺满 pinch; keep the pager from scrolling meanwhile
                if (ev.actionMasked == MotionEvent.ACTION_POINTER_DOWN) {
                    val cancel = MotionEvent.obtain(ev).apply { action = MotionEvent.ACTION_CANCEL }
                    super.dispatchTouchEvent(cancel); cancel.recycle()
                }
                return true
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    /** Long-press glass menu over the player: pager is blurred with RenderEffect (12+), dimmed below 12. */
    private fun openMenu(spec: com.moonlight.feed.ui.design.MenuSpec) {
        uiHandler.removeCallbacks(hideBarRunnable)
        Haptics.open(root)
        overlayView.visibility = View.VISIBLE
        overlayState.showMenu(spec)
    }

    private var lastBlur = -1f
    private fun setBackdropBlur(p: Float) {
        val k = p.coerceIn(0f, 1f)
        if (kotlin.math.abs(k - lastBlur) < 0.01f) return
        lastBlur = k
        if (android.os.Build.VERSION.SDK_INT >= 31) {
            val r = 28f * resources.displayMetrics.density * k * (com.moonlight.feed.ui.design.MenuStyle.backdrop.blurDp / 24f)
            pager.setRenderEffect(if (r < 0.5f) null else android.graphics.RenderEffect.createBlurEffect(r, r, android.graphics.Shader.TileMode.CLAMP))
            topBar.setRenderEffect(if (r < 0.5f) null else android.graphics.RenderEffect.createBlurEffect(r, r, android.graphics.Shader.TileMode.CLAMP))
        }
    }

    private fun bindSettledPage(position: Int) {
        val e = items.getOrNull(position) ?: return
        if (attachedPlayerPage != position) {
            player?.pause()
            attachedPlayerPage = -1
        }
        when {
            e.isWebEmbed() -> bindEmbed(position, e)
            e.kind == MediaKind.VIDEO -> bindVideo(position, e)
            e.kind == MediaKind.IMAGE -> {
                detachWeb()
                player?.stop()
                player?.clearMediaItems()
                // images never auto-advance in the swipe player (图片停留 applies to 首页 only; 永久 = no timer anywhere)
                findHolder(position)?.cover?.let { cv ->
                    if (android.os.Build.VERSION.SDK_INT >= 31) revealView(cv)
                    else if (revealMs > 0) {
                        val model = e.localPath?.let { File(it) } ?: e.remoteUrl
                        cv.load(model) { size(24, 24) }
                        cv.load(model) { crossfade(revealMs) }
                    }
                }
            }
        }
    }

    private fun bindVideo(position: Int, e: MediaEntry) {
        detachWeb()
        val holder = findHolder(position)
        if (holder == null) {
            pager.post { if (pager.currentItem == position) bindSettledPage(position) }
            return
        }
        val pv = holder.playerView ?: return
        val pl = player ?: return
        val uri = when {
            e.localPath != null && File(e.localPath).exists() -> Uri.fromFile(File(e.localPath))
            e.remoteUrl != null -> Uri.parse(e.remoteUrl)
            else -> null
        }
        if (uri == null) {
            holder.overlayText?.text = "无法播放"
            return
        }
        pv.player = pl
        pv.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
        pl.volume = if (muted) 0f else 1f
        pv.post { applyFit() }
        pl.setMediaItem(MediaItem.fromUri(uri))
        pl.prepare()
        pl.playWhenReady = true
        pl.play()
        attachedPlayerPage = position
        holder.overlayText?.visibility = View.GONE
        // playback already started above; the reveal is purely visual
        if (android.os.Build.VERSION.SDK_INT >= 31 || revealMs <= 0) { holder.cover?.visibility = View.GONE; revealView(pv) }
        else { showBlurredCover(holder, e); fadeOutCover(holder) }
    }

    private fun bindEmbed(position: Int, e: MediaEntry) {
        player?.pause()
        uiHandler.removeCallbacks(autoSkipRunnable)
        hideNativeEmbedError()
        val holder = findHolder(position)
        if (holder == null) {
            pager.post { if (pager.currentItem == position) bindSettledPage(position) }
            return
        }
        ensureWebView()
        val host = webHost ?: return
        val wv = webView ?: return
        (host.parent as? ViewGroup)?.removeView(host)
        holder.webSlot?.addView(
            host,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
        host.pager = pager
        holder.cover?.visibility = View.VISIBLE
        if (attachedWebPage != position) {
            showBlurredCover(holder, e)
            progressBar.visibility = View.VISIBLE
            loadEmbedContent(wv, e)
            attachedWebPage = position
        }
        holder.overlayText?.visibility = View.GONE
    }

    private fun loadEmbedContent(wv: WebView, e: MediaEntry) {
        ytState = -1
        val platform = e.resolvedPlatform()
        currentEmbedId = PlatformResolver.extractYoutubeId(e.remoteUrl ?: e.embedUrl ?: "")
            ?: e.biliBvid
        when (platform) {
            PlatformId.YOUTUBE -> {
                val id = PlatformResolver.extractYoutubeId(e.remoteUrl ?: "")
                    ?: PlatformResolver.extractYoutubeId(e.embedUrl ?: "")
                    ?: currentEmbedId
                if (id.isNullOrBlank()) {
                    status.text = "无效 YouTube 链接"
                    return
                }
                currentEmbedId = id
                // Documented approach: local HTML + loadDataWithBaseURL(https://<package>)
                wv.loadDataWithBaseURL(
                    EmbedHost.BASE + "/",
                    EmbedHost.youtubeHtml(id, muted),
                    "text/html",
                    "UTF-8",
                    null,
                )
            }
            PlatformId.BILI -> {
                val bv = e.biliBvid
                if (!bv.isNullOrBlank()) {
                    val url = embedModes[embedMode % embedModes.size](bv)
                    wv.loadUrl(url, EmbedHost.refererHeaders())
                } else {
                    val url = e.embedUrl ?: e.remoteUrl ?: return
                    wv.loadUrl(url, EmbedHost.refererHeaders())
                }
            }
            PlatformId.TWITCH -> {
                val slug = PlatformResolver.extractTwitchClip(e.remoteUrl ?: e.embedUrl ?: "")
                    ?: return
                val src = EmbedHost.twitchEmbedUrl(slug, muted)
                wv.loadDataWithBaseURL(
                    EmbedHost.BASE + "/",
                    EmbedHost.iframeHtml(src, "Twitch"),
                    "text/html",
                    "UTF-8",
                    null,
                )
            }
            PlatformId.VIMEO, PlatformId.DAILYMOTION, PlatformId.PEERTUBE -> {
                val src = e.embedUrl ?: e.resolvedEmbedUrl(muted) ?: e.remoteUrl ?: return
                wv.loadDataWithBaseURL(
                    EmbedHost.BASE + "/",
                    EmbedHost.iframeHtml(src, platform),
                    "text/html",
                    "UTF-8",
                    null,
                )
            }
            else -> {
                val url = e.embedUrl ?: e.remoteUrl ?: return
                // Generic / web: Referer header when loading remote URL directly
                if (url.startsWith("http")) {
                    wv.loadUrl(url, EmbedHost.refererHeaders())
                } else {
                    wv.loadDataWithBaseURL(
                        EmbedHost.BASE + "/",
                        EmbedHost.iframeHtml(url, "web"),
                        "text/html",
                        "UTF-8",
                        null,
                    )
                }
            }
        }
    }

    private fun resolveEmbedUrl(e: MediaEntry): String? {
        val bv = e.biliBvid
        if (!bv.isNullOrBlank() && (e.platform == null || e.platform == "bili" || e.kind == MediaKind.BILI)) {
            return embedModes[embedMode % embedModes.size](bv)
        }
        if (e.resolvedPlatform() == PlatformId.YOUTUBE) {
            val id = PlatformResolver.extractYoutubeId(e.remoteUrl ?: e.embedUrl ?: "") ?: return null
            return EmbedHost.youtubeEmbedUrl(id, muted)
        }
        return e.resolvedEmbedUrl(mute = muted)
            ?: e.embedUrl
            ?: e.remoteUrl
    }

    private fun showNativeEmbedError(code: String, videoId: String?) {
        hideNativeEmbedError()
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xD907080B.toInt())
            gravity = Gravity.CENTER
            setPadding(dp(32), dp(24), dp(32), dp(24))
            layoutParams = FrameLayout.LayoutParams(MATCH, MATCH)
        }
        box.addView(TextView(this).apply {
            text = when (code) {
                "153" -> "视频播放器配置错误"
                "101", "150" -> "嵌入已被禁用"
                else -> "播放出错"
            }
            setTextColor(Color.WHITE)
            textSize = 18f
            gravity = Gravity.CENTER
        })
        box.addView(TextView(this).apply {
            text = "错误 $code"
            setTextColor(0xFFAAAAAA.toInt())
            textSize = 13f
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, dp(20))
        })
        fun errBtn(label: String, light: Boolean, action: () -> Unit) = Button(this).apply {
            text = label
            textSize = 14f
            isAllCaps = false
            stateListAnimator = null
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = dp(24).toFloat()
                setColor(if (light) 0xFFE8E3C9.toInt() else 0x33FFFFFF)
                setStroke(dp(1).coerceAtLeast(1), if (light) 0x00000000 else 0x40FFFFFF)
            }
            setTextColor(if (light) 0xFF15161A.toInt() else Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(dp(220), dp(48)).apply { topMargin = dp(10) }
            setOnClickListener { action() }
        }
        box.addView(errBtn("在 YouTube 中打开", true) {
            openYoutubeId(videoId)
        })
        box.addView(errBtn("下一条", false) {
            hideNativeEmbedError()
            autoSkipRunnable.run()
        })
        root.addView(box)
        errorOverlay = box
        showTopBar()
        if (code == "101" || code == "150") {
            uiHandler.postDelayed(autoSkipRunnable, 3500)
        }
    }

    private fun hideNativeEmbedError() {
        errorOverlay?.let { root.removeView(it) }
        errorOverlay = null
    }

    private fun openYoutubeId(id: String?) {
        val vid = id ?: currentEmbedId ?: return
        val tries = listOf(
            Uri.parse("vnd.youtube:$vid"),
            Uri.parse(EmbedHost.youtubeWatchUrl(vid)),
        )
        for (u in tries) {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, u)); return
            } catch (_: Exception) { }
        }
    }

    inner class MoonBridge {
        @JavascriptInterface
        fun onPlayerReady(id: String) {
            uiHandler.post {
                progressBar.visibility = View.GONE
                fadeOutCover(findHolder(pager.currentItem))
                scheduleHideBar()
            }
        }
        @JavascriptInterface
        fun onPlayerState(state: String) {
            uiHandler.post { ytState = state.toIntOrNull() ?: ytState; scheduleHideBar() }
        }
        @JavascriptInterface
        fun onPlayerError(code: String, id: String) {
            uiHandler.post {
                progressBar.visibility = View.GONE
                status.text = "YouTube 错误 $code"
                showNativeEmbedError(code, id)
            }
        }
        @JavascriptInterface
        fun openYoutube(id: String) {
            uiHandler.post { openYoutubeId(id) }
        }
        @JavascriptInterface
        fun skipNext() {
            uiHandler.post {
                hideNativeEmbedError()
                autoSkipRunnable.run()
            }
        }
    }

    private fun reloadEmbed(position: Int) {
        attachedWebPage = -1
        items.getOrNull(position)?.let { if (it.isWebEmbed()) bindEmbed(position, it) }
    }

    private fun cycleEmbedMode() {
        val e = items.getOrNull(pager.currentItem) ?: return
        if (!e.biliBvid.isNullOrBlank()) {
            embedMode = (embedMode + 1) % embedModes.size
            val names = listOf("官方嵌入", "H5 移动", "手机站")
            Toast.makeText(this, "切换到 ${names[embedMode]}", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "已刷新嵌入页", Toast.LENGTH_SHORT).show()
        }
        reloadEmbed(pager.currentItem)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun ensureWebView() {
        if (webView != null) return
        val host = VerticalSwipeHost(this)
        webHost = host
        val wv = WebView(this).apply {
            setBackgroundColor(Color.BLACK)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            settings.loadsImagesAutomatically = true
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = true
            settings.userAgentString =
                "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
            addJavascriptInterface(MoonBridge(), "MoonBridge")
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    val u = request.url ?: return false
                    val s = u.toString()
                    return when {
                        s.startsWith("http://") || s.startsWith("https://") -> {
                            val hostName = (u.host ?: "").lowercase()
                            val internal = listOf(
                                "bilibili", "bilivideo", "hdslb",
                                "youtube", "youtu.be", "googlevideo", "ytimg", "ggpht",
                                "vimeo", "vimeocdn",
                                "dailymotion", "dmcdn",
                                "twitch", "ttvnw", "jtvnw",
                                "peertube", "framasoft",
                                "com.moonlight.feed",
                            ).any { hostName.contains(it) }
                            if (!internal) {
                                runCatching { startActivity(Intent(Intent.ACTION_VIEW, u)) }
                                true
                            } else false
                        }
                        s.startsWith("bilibili://") || s.startsWith("bili://") -> {
                            try {
                                startActivity(Intent(Intent.ACTION_VIEW, u))
                            } catch (_: ActivityNotFoundException) {
                                status.text = "未安装哔哩 App · 可点浏览器"
                            }
                            true
                        }
                        s.startsWith("vnd.youtube") || s.startsWith("youtube://") -> {
                            runCatching { startActivity(Intent(Intent.ACTION_VIEW, u)) }
                            true
                        }
                        else -> true
                    }
                }
                override fun onPageFinished(view: WebView?, url: String?) {
                    progressBar.visibility = View.GONE
                    // non-YouTube embeds have no ready callback: give the player a beat to paint, then fade the cover
                    val pos = pager.currentItem
                    if (items.getOrNull(pos)?.platform != com.moonlight.feed.data.PlatformId.YOUTUBE) uiHandler.postDelayed({ if (pager.currentItem == pos) fadeOutCover(findHolder(pos)) }, 350)
                    // returning from background with "返回时继续播放" off / not playing before: reload but stay paused
                    if (suppressEmbedAutoplay) {
                        suppressEmbedAutoplay = false
                        view?.evaluateJavascript(EmbedHost.PAUSE_JS, null)
                        uiHandler.postDelayed({ webView?.evaluateJavascript(EmbedHost.PAUSE_JS, null) }, 900)
                    } else if (!muted) {
                        view?.evaluateJavascript(
                            "(function(){var v=document.querySelector('video');if(v){v.muted=false;v.volume=1;v.play&&v.play();}})();",
                            null,
                        )
                    } else {
                        view?.evaluateJavascript(
                            "(function(){var v=document.querySelector('video');if(v){v.muted=true;v.play&&v.play();}})();",
                            null,
                        )
                    }
                    findHolder(pager.currentItem)?.cover?.visibility = View.GONE
                    scheduleHideBar()
                }
                override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                    if (request.isForMainFrame) {
                        progressBar.visibility = View.GONE
                        status.text = "加载失败 · 换源或浏览器"
                    }
                }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                    if (customView != null) {
                        callback?.onCustomViewHidden(); return
                    }
                    customView = view
                    customViewCallback = callback
                    fullscreenContainer = FrameLayout(this@BiliPlayerActivity).apply {
                        setBackgroundColor(Color.BLACK)
                        addView(view, FrameLayout.LayoutParams(MATCH, MATCH))
                    }
                    root.addView(fullscreenContainer, FrameLayout.LayoutParams(MATCH, MATCH))
                    webView?.visibility = View.GONE
                }
                override fun onHideCustomView() {
                    fullscreenContainer?.let { root.removeView(it) }
                    fullscreenContainer = null
                    customView = null
                    customViewCallback?.onCustomViewHidden()
                    customViewCallback = null
                    webView?.visibility = View.VISIBLE
                }
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    progressBar.visibility = if (newProgress in 1..99) View.VISIBLE else View.GONE
                    progressBar.progress = newProgress
                }
            }
        }
        webView = wv
        host.addView(wv, FrameLayout.LayoutParams(MATCH, MATCH))
        host.pager = pager
    }

    private fun detachWeb() {
        webView?.evaluateJavascript(
            "(function(){var v=document.querySelector('video');if(v){v.pause();}})();",
            null,
        )
        webHost?.let { h -> (h.parent as? ViewGroup)?.removeView(h) }
        attachedWebPage = -1
        // keep webView instance for reuse
    }

    private fun openExternal(position: Int) {
        val e = items.getOrNull(position) ?: return
        val uri = when {
            !e.biliBvid.isNullOrBlank() -> Uri.parse("https://www.bilibili.com/video/${e.biliBvid}")
            !e.remoteUrl.isNullOrBlank() -> Uri.parse(e.remoteUrl)
            !e.embedUrl.isNullOrBlank() -> Uri.parse(e.embedUrl)
            else -> return
        }
        val tries = buildList {
            if (!e.biliBvid.isNullOrBlank()) add(Uri.parse("bilibili://video/${e.biliBvid}"))
            add(uri)
        }
        for (u in tries) {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, u)); return
            } catch (_: Exception) { }
        }
    }

    private fun findHolder(position: Int): PageVH? {
        val rv = pager.getChildAt(0) as? RecyclerView ?: return null
        return rv.findViewHolderForAdapterPosition(position) as? PageVH
    }

    private fun showTopBar() {
        if (!::topBar.isInitialized) return
        barVisible = true
        topBar.animate().cancel()
        topBar.visibility = View.VISIBLE
        topBar.animate().alpha(1f).setDuration(200).setInterpolator(DecelerateInterpolator()).start()
        hideSystemBars() // keep immersive; transient swipe still works
    }

    private fun hideTopBar() {
        if (!::topBar.isInitialized) return
        barVisible = false
        topBar.animate().cancel()
        topBar.animate().alpha(0f).setDuration(200).setInterpolator(AccelerateInterpolator())
            .withEndAction { topBar.visibility = View.GONE }.start()
    }

    private fun scheduleHideBar() {
        uiHandler.removeCallbacks(hideBarRunnable)
        uiHandler.removeCallbacks(autoSkipRunnable)
        hideNativeEmbedError()
        uiHandler.postDelayed(hideBarRunnable, 3000)
    }

    private fun hideSystemBars() {
        WindowInsetsControllerCompat(window, window.decorView).let {
            it.hide(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars())
            it.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    private fun finishWithResult() {
        setResult(RESULT_OK, Intent().putExtra(EXTRA_RESULT_INDEX, pager.currentItem)
            .putExtra(EXTRA_RESULT_ID, items.getOrNull(pager.currentItem)?.id))
        finish()
    }

    // Home / recents / other app / screen off / another activity on top → ON_PAUSE: stop everything audible.
    override fun onPause() {
        super.onPause()
        val pos = pager.currentItem
        val cur = items.getOrNull(pos)
        val p = player
        videoGate.suspend(
            cur?.kind == MediaKind.VIDEO && p != null && (p.isPlaying || (p.playWhenReady && p.playbackState == Player.STATE_BUFFERING)),
            cur?.id,
        )
        p?.pause()
        embedGate.suspend(EmbedHost.embedWasPlaying(cur?.isWebEmbed() == true && attachedWebPage == pos, ytState), cur?.id)
        uiHandler.removeCallbacks(autoSkipRunnable)
        webView?.evaluateJavascript(EmbedHost.PAUSE_JS, null)
        webView?.onPause()
    }

    override fun onStop() {
        if (EmbedHost.needsHardStop(embedGate.wasPlaying, ytState)) {
            webView?.stopLoading(); webView?.loadUrl("about:blank")
            attachedWebPage = -1; embedBlanked = true
        }
        webView?.pauseTimers() // process-wide; balanced in onStart
        super.onStop()
    }

    override fun onStart() {
        super.onStart()
        webView?.resumeTimers()
    }

    // Resume only what was playing before (and only if 设置 › 返回时继续播放 allows).
    override fun onResume() {
        super.onResume()
        webView?.onResume()
        val pos = pager.currentItem
        val cur = items.getOrNull(pos)
        if (videoGate.resume(resumeOnReturn, cur?.id) && cur?.kind == MediaKind.VIDEO) player?.let { com.moonlight.feed.data.PlayToggle.resume(it.playable()) }
        val embedGo = embedGate.resume(resumeOnReturn, cur?.id)
        if (embedBlanked) {
            embedBlanked = false
            if (cur?.isWebEmbed() == true) { suppressEmbedAutoplay = !embedGo; bindSettledPage(pos) }
        } else if (embedGo) webView?.evaluateJavascript(EmbedHost.RESUME_JS, null)
        hideSystemBars()
    }

    override fun onDestroy() {
        uiHandler.removeCallbacks(hideBarRunnable)
        uiHandler.removeCallbacks(autoSkipRunnable)
        hideNativeEmbedError()
        player?.release(); player = null
        webView?.apply {
            loadUrl("about:blank"); stopLoading(); destroy()
        }
        webView = null
        super.onDestroy()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private inner class FeedAdapter : RecyclerView.Adapter<PageVH>() {
        override fun getItemCount() = items.size
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageVH {
            val slot = FrameLayout(parent.context).apply {
                layoutParams = ViewGroup.LayoutParams(MATCH, MATCH)
                setBackgroundColor(Color.BLACK)
            }
            val cover = ImageView(parent.context).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                layoutParams = FrameLayout.LayoutParams(MATCH, MATCH)
            }
            val webSlot = FrameLayout(parent.context).apply {
                layoutParams = FrameLayout.LayoutParams(MATCH, MATCH)
            }
            val playerView = (LayoutInflater.from(parent.context)
                .inflate(R.layout.player_view_texture, slot, false) as PlayerView).apply {
                useController = false
                visibility = View.GONE
            }
            val overlay = TextView(parent.context).apply {
                setTextColor(Color.WHITE)
                textSize = 14f
                gravity = Gravity.CENTER
                visibility = View.GONE
                layoutParams = FrameLayout.LayoutParams(MATCH, MATCH)
            }
            slot.addView(cover)
            slot.addView(playerView)
            slot.addView(webSlot)
            slot.addView(overlay)
            // tap empty / cover toggles chrome
            slot.setOnClickListener {
                if (barVisible) hideTopBar() else {
                    showTopBar(); scheduleHideBar()
                }
            }
            // long-press → radial ring: handled in dispatchTouchEvent so it also works over WebView embeds
            return PageVH(slot, cover, playerView, webSlot, overlay)
        }

        override fun onBindViewHolder(holder: PageVH, position: Int) {
            val e = items[position]
            holder.cover?.visibility = View.VISIBLE
            holder.playerView?.visibility = View.GONE
            holder.overlayText?.visibility = View.GONE
            // Don't yank the live WebView off the settled Bili page during rebind
            holder.webSlot?.let { slot ->
                if (!(webHost != null && webHost!!.parent === slot)) {
                    slot.removeAllViews()
                }
            }
            when (e.kind) {
                MediaKind.IMAGE -> {
                    val model = e.localPath ?: e.remoteUrl
                    holder.cover?.load(model) { crossfade(true) }
                }
                MediaKind.VIDEO -> {
                    holder.playerView?.visibility = View.VISIBLE
                    // cover while preparing
                    val model = e.coverUrl ?: e.localPath
                    if (model != null) holder.cover?.load(model)
                    else holder.cover?.setImageDrawable(null)
                }
                MediaKind.BILI, MediaKind.EMBED -> {
                    if (!e.coverUrl.isNullOrBlank()) holder.cover?.load(e.coverUrl) { crossfade(true) }
                    else holder.cover?.setImageDrawable(null)
                }
            }
        }

        override fun onViewRecycled(holder: PageVH) {
            if (holder.playerView?.player != null) {
                holder.playerView?.player = null
            }
            holder.webSlot?.let { slot ->
                if (webHost != null && webHost!!.parent === slot) {
                    // moving off-screen — detach but keep instance
                    slot.removeView(webHost)
                } else {
                    slot.removeAllViews()
                }
            }
            super.onViewRecycled(holder)
        }
    }

    class PageVH(
        root: View,
        val cover: ImageView?,
        val playerView: PlayerView?,
        val webSlot: FrameLayout?,
        val overlayText: TextView?,
    ) : RecyclerView.ViewHolder(root)

    companion object {
        const val EXTRA_START_INDEX = "start_index"
        const val EXTRA_START_ID = "start_id"
        const val EXTRA_FEED_IDS = "feed_ids"
        /** Play one local file (profile cover / avatar) without touching the library. */
        const val EXTRA_LOCAL_PATH = "local_path"
        const val EXTRA_LOCAL_VIDEO = "local_video"
        const val EXTRA_LOCAL_TITLE = "local_title"

        fun playLocal(ctx: android.content.Context, path: String, video: Boolean, title: String = "") {
            ctx.startActivity(
                Intent(ctx, BiliPlayerActivity::class.java)
                    .putExtra(EXTRA_LOCAL_PATH, path).putExtra(EXTRA_LOCAL_VIDEO, video).putExtra(EXTRA_LOCAL_TITLE, title)
            )
        }
        const val EXTRA_RESULT_INDEX = "result_index"
        const val EXTRA_RESULT_ID = "result_id"
        private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
    }
}
