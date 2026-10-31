package com.moonlight.feed.ui.design

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.SurfaceTexture
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.view.Surface
import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import com.moonlight.feed.data.ButtonSkinPrefs
import com.moonlight.feed.data.FocusCrop
import com.moonlight.feed.data.MediaFocus
import com.moonlight.feed.data.RingInk
import com.moonlight.feed.data.SkinDef
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

// =================================================================== 按键背景 runtime

/** Current 按键背景 settings (set from the settings flow; tests set it directly). */
object ButtonSkins {
    var prefs by mutableStateOf(ButtonSkinPrefs())
}

/** Decoded, downscaled pictures (photo, or a video's poster frame) + their average luminance. */
object SkinBitmaps {
    class Loaded(val bitmap: ImageBitmap, val lum: Float)
    private const val MAX_SIDE = 512
    private val cache = object : android.util.LruCache<String, Loaded>(32 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Loaded) = value.bitmap.width * value.bitmap.height * 4
    }
    fun cached(path: String): Loaded? = cache.get(path)

    /** null → missing / undecodable (the button then keeps its theme look). Call off the main thread. */
    fun load(path: String, video: Boolean): Loaded? {
        cache.get(path)?.let { return it }
        val raw: Bitmap = runCatching {
            if (!File(path).isFile) return null
            if (video) MediaMetadataRetriever().run { try { setDataSource(path); getFrameAtTime(0) } finally { runCatching { release() } } }
            else {
                val b = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(path, b)
                if (b.outWidth <= 0 || b.outHeight <= 0) null
                else BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = Integer.highestOneBit((maxOf(b.outWidth, b.outHeight) / MAX_SIDE).coerceAtLeast(1)) })
            }
        }.getOrNull() ?: return null
        val k = MAX_SIDE.toFloat() / maxOf(raw.width, raw.height)
        val bmp = if (k < 1f) Bitmap.createScaledBitmap(raw, (raw.width * k).toInt().coerceAtLeast(1), (raw.height * k).toInt().coerceAtLeast(1), true).also { if (it !== raw) raw.recycle() } else raw
        val tiny = Bitmap.createScaledBitmap(bmp, 24, 24, true)
        val px = IntArray(24 * 24); tiny.getPixels(px, 0, 24, 0, 0, 24, 24); if (tiny !== bmp) tiny.recycle()
        return Loaded(bmp.asImageBitmap(), RingInk.average(px)).also { cache.put(path, it) }
    }
    fun forget(path: String) { cache.remove(path) }
}

/** Demo / screenshot art (`art:N`) — drawn procedurally, a moonlit night (dark). */
private const val ART_LUM = 0.30f

/** Everything a button needs to draw its skin: the picture source, crop, scrim and the readable ink. */
class SkinPaint internal constructor(
    val def: SkinDef,
    private val poster: ImageBitmap?,
    private val live: VideoSkinPool.Entry?,
    val inkSpec: RingInk,
) {
    private val artSeed = if (def.path.startsWith("art:")) def.path.removePrefix("art:").toIntOrNull() ?: 0 else null
    val ink: Color get() = if (inkSpec.lightMedia) Color(0xFF15171C) else Color.White
    val scrim: Color get() = (if (inkSpec.lightMedia) Color.White else Color.Black).copy(alpha = 0.10f + 0.56f * def.scrim)
    val edge: Color get() = Color.White.copy(alpha = if (inkSpec.lightMedia) 0.80f else 0.45f)
    /** Label text that sits next to (not on) the button keeps the page colours; this is for text ON the button. */
    val dimInk: Color get() = ink.copy(alpha = 0.75f)

    /** Cover-crop of the picture into this button's own bounds around the focal point, then the scrim. */
    fun draw(scope: DrawScope) = with(scope) {
        val f = def.focus ?: MediaFocus.DEFAULT
        val w = size.width; val h = size.height
        if (w <= 0f || h <= 0f) return@with
        if (artSeed != null) {
            val p = FocusCrop.place(ART_SRC_W, ART_SRC_H, w, h, f)
            withTransform({ translate(p.left, p.top); scale(p.width / w, p.height / h, androidx.compose.ui.geometry.Offset.Zero) }) { drawProceduralArt(artSeed) }
        } else {
            val img = live?.frame ?: poster
            if (img != null) {
                val p = FocusCrop.place(img.width.toFloat(), img.height.toFloat(), w, h, f)
                drawImage(img, dstOffset = IntOffset(p.left.toInt(), p.top.toInt()), dstSize = IntSize(p.width.toInt().coerceAtLeast(1), p.height.toInt().coerceAtLeast(1)))
            }
        }
        drawRect(scrim)
    }
}

/** The skin [buttonId] uses, ready to draw — or null → theme (no skin, still decoding, missing or bad file). */
@Composable
fun rememberButtonSkin(buttonId: String?): SkinPaint? {
    if (buttonId == null) return null
    val def = ButtonSkins.prefs.skinFor(buttonId) ?: return null
    return rememberSkinPaint(def)
}

@Composable
fun rememberSkinPaint(def: SkinDef): SkinPaint? {
    if (def.path.startsWith("art:")) return remember(def) { SkinPaint(def, null, null, RingInk.forLuminance(ART_LUM)) }
    var loaded by remember(def.path) { mutableStateOf(SkinBitmaps.cached(def.path)) }
    var failed by remember(def.path) { mutableStateOf(false) }
    if (loaded == null && !failed) LaunchedEffect(def.path) {
        val r = withContext(Dispatchers.IO) { SkinBitmaps.load(def.path, def.video) }
        if (r == null) failed = true else loaded = r
    }
    val l = loaded ?: return null
    val live = if (def.video && !LocalInspectionMode.current && LocalLoopVideos.current) rememberVideoSkin(def.path) else null
    return remember(def, l, live) { SkinPaint(def, l.bitmap, live, RingInk.forLuminance(l.lum)) }
}

/** The single shared draw path for every skinnable button: picture clipped to [shape] + scrim + edge. */
fun Modifier.skinFill(p: SkinPaint, shape: Shape, edge: Boolean = true): Modifier =
    this.clip(shape).drawBehind { p.draw(this) }.then(if (edge) Modifier.border(0.75.dp, p.edge, shape) else Modifier)

/** Skin when the button has one, else its normal theme / MediaGlass look ([theme]). */
inline fun Modifier.skinOr(p: SkinPaint?, shape: Shape, edge: Boolean = true, theme: Modifier.() -> Modifier): Modifier =
    if (p != null) skinFill(p, shape, edge) else theme()

// =================================================================== video skins

/** Registers this (composed, started) button as a viewer of the video skin; pauses with the lifecycle. */
@Composable
private fun rememberVideoSkin(path: String): VideoSkinPool.Entry {
    val ctx = LocalContext.current.applicationContext
    val entry = remember(path) { VideoSkinPool.entry(path) }
    val owner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(path, owner) {
        var on = false
        fun set(v: Boolean) { if (v != on) { on = v; if (v) VideoSkinPool.attach(ctx, path) else VideoSkinPool.detach(ctx, path) } }
        val obs = LifecycleEventObserver { _, e ->
            when (e) { Lifecycle.Event.ON_START -> set(true); Lifecycle.Event.ON_STOP -> set(false); else -> Unit }
        }
        set(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs); set(false) }
    }
    return entry
}

/**
 * One muted, looping ExoPlayer per *video skin* (never per button), and at most
 * [ButtonSkinPrefs.MAX_PLAYING_VIDEOS] of them at once — the rest show their poster frame until a slot frees up.
 * Frames are snapshotted (GL, ≤ 20 fps, ≤ 224 px) into a shared bitmap every button using that skin draws.
 * Players are pooled; all are released when no skinned button is visible (offscreen / app hidden).
 */
object VideoSkinPool {
    class Entry internal constructor(val path: String) {
        internal var users = 0
        var frame by mutableStateOf<ImageBitmap?>(null); internal set
        internal var player: ExoPlayer? = null
        internal var grabber: GlFrameGrabber? = null
        internal var listener: Player.Listener? = null
        val playing get() = player != null
    }
    private const val IDLE_KEEP = 2
    private val entries = LinkedHashMap<String, Entry>()
    private val order = ArrayList<String>()
    private val idle = ArrayList<ExoPlayer>()
    val playingCount get() = entries.values.count { it.playing }

    fun entry(path: String) = entries.getOrPut(path) { Entry(path) }

    fun attach(ctx: android.content.Context, path: String) {
        val e = entry(path)
        if (e.users++ == 0) { order.remove(path); order.add(path) }
        rebalance(ctx)
    }

    fun detach(ctx: android.content.Context, path: String) {
        val e = entries[path] ?: return
        e.users = (e.users - 1).coerceAtLeast(0)
        if (e.users == 0) order.remove(path)
        rebalance(ctx)
    }

    private fun rebalance(ctx: android.content.Context) {
        val allowed = ButtonSkinPrefs.playingVideos(order, entries.mapValues { it.value.users }).toSet()
        for (e in entries.values.toList()) {
            if (e.path in allowed) start(ctx, e) else stop(e)
            if (e.users == 0) entries.remove(e.path)
        }
        while (idle.size > (if (entries.isEmpty()) 0 else IDLE_KEEP)) idle.removeAt(idle.lastIndex).release()
    }

    private fun start(ctx: android.content.Context, e: Entry) {
        if (e.player != null) return
        val p = idle.removeLastOrNull() ?: ExoPlayer.Builder(ctx).build().apply {
            trackSelectionParameters = trackSelectionParameters.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true).build()
            volume = 0f
            repeatMode = Player.REPEAT_MODE_ONE
        }
        val g = GlFrameGrabber { bmp -> if (e.player === p) e.frame = bmp.asImageBitmap() }
        val l = object : Player.Listener { override fun onVideoSizeChanged(v: VideoSize) { g.setVideoSize(v.width, v.height) } }
        p.addListener(l)
        e.player = p; e.grabber = g; e.listener = l
        g.start { s -> if (e.player === p) p.setVideoSurface(s) }
        p.setMediaItem(MediaItem.fromUri(Uri.fromFile(File(e.path))))
        p.prepare(); p.playWhenReady = true
    }

    private fun stop(e: Entry) {
        val p = e.player ?: return
        p.playWhenReady = false
        p.stop(); p.clearVideoSurface(); p.clearMediaItems()
        e.listener?.let { p.removeListener(it) }
        e.grabber?.release()
        e.player = null; e.grabber = null; e.listener = null
        e.frame = null      // back to the poster
        idle.add(p)
    }
}

/** Decodes into a SurfaceTexture and snapshots small RGBA frames (GL thread) for drawing into many buttons. */
internal class GlFrameGrabber(private val onFrame: (Bitmap) -> Unit) {
    private val thread = HandlerThread("skin-video").apply { start() }
    private val gl = Handler(thread.looper)
    private val main = Handler(Looper.getMainLooper())
    private var dpy: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var ctx: EGLContext = EGL14.EGL_NO_CONTEXT
    private var pb: EGLSurface = EGL14.EGL_NO_SURFACE
    private var st: SurfaceTexture? = null
    private var surface: Surface? = null
    private var oes = 0; private var prog = 0; private var fbo = 0; private var fboTex = 0
    private var w = 0; private var h = 0
    private var buf: ByteBuffer? = null
    private val bitmaps = arrayOfNulls<Bitmap>(3); private var bi = 0
    private var last = 0L
    private val m = FloatArray(16)
    private val quad: FloatBuffer = ByteBuffer.allocateDirect(8 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        .apply { put(floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f)); position(0) }
    @Volatile private var dead = false

    fun start(onSurface: (Surface) -> Unit) = gl.post {
        runCatching {
            dpy = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            val v = IntArray(2); EGL14.eglInitialize(dpy, v, 0, v, 1)
            val cfgs = arrayOfNulls<EGLConfig>(1); val n = IntArray(1)
            EGL14.eglChooseConfig(dpy, intArrayOf(EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8, EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT, EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT, EGL14.EGL_NONE), 0, cfgs, 0, 1, n, 0)
            ctx = EGL14.eglCreateContext(dpy, cfgs[0], EGL14.EGL_NO_CONTEXT, intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE), 0)
            pb = EGL14.eglCreatePbufferSurface(dpy, cfgs[0], intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE), 0)
            EGL14.eglMakeCurrent(dpy, pb, pb, ctx)
            val t = IntArray(1); GLES20.glGenTextures(1, t, 0); oes = t[0]
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oes)
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            prog = program(
                "attribute vec2 aPos; uniform mat4 uTex; varying vec2 vTex; void main(){ gl_Position = vec4(aPos.x, -aPos.y, 0.0, 1.0); vTex = (uTex * vec4((aPos + 1.0) * 0.5, 0.0, 1.0)).xy; }",
                "#extension GL_OES_EGL_image_external : require\nprecision mediump float; uniform samplerExternalOES uS; varying vec2 vTex; void main(){ gl_FragColor = texture2D(uS, vTex); }",
            )
            resize(224, 224)
            val s = SurfaceTexture(oes).apply { setOnFrameAvailableListener({ frame() }, gl) }
            st = s
            val sf = Surface(s); surface = sf
            main.post { if (!dead) onSurface(sf) }
        }
    }

    /** Snapshot size follows the video's aspect, longest side ≤ 224 px. */
    fun setVideoSize(vw: Int, vh: Int) { if (vw > 0 && vh > 0) gl.post { val k = 224f / maxOf(vw, vh); resize((vw * k).toInt().coerceAtLeast(8), (vh * k).toInt().coerceAtLeast(8)) } }

    private fun resize(nw: Int, nh: Int) {
        if (nw == w && nh == h) return
        w = nw; h = nh
        if (fbo != 0) { GLES20.glDeleteFramebuffers(1, intArrayOf(fbo), 0); GLES20.glDeleteTextures(1, intArrayOf(fboTex), 0) }
        val t = IntArray(1); GLES20.glGenTextures(1, t, 0); fboTex = t[0]
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, fboTex)
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, w, h, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null)
        val f = IntArray(1); GLES20.glGenFramebuffers(1, f, 0); fbo = f[0]
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fbo)
        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, fboTex, 0)
        buf = ByteBuffer.allocateDirect(w * h * 4).order(ByteOrder.nativeOrder())
        for (i in bitmaps.indices) bitmaps[i] = null
    }

    private fun frame() {
        val s = st ?: return
        runCatching {
            s.updateTexImage()
            val now = System.nanoTime()
            if (now - last < 50_000_000L) return      // ≤ 20 fps is plenty for a 56 dp button
            last = now
            s.getTransformMatrix(m)
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fbo)
            GLES20.glViewport(0, 0, w, h)
            GLES20.glUseProgram(prog)
            val aPos = GLES20.glGetAttribLocation(prog, "aPos")
            GLES20.glEnableVertexAttribArray(aPos)
            GLES20.glVertexAttribPointer(aPos, 2, GLES20.GL_FLOAT, false, 0, quad)
            GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(prog, "uTex"), 1, false, m, 0)
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oes)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
            val b = buf ?: return
            b.rewind()
            GLES20.glReadPixels(0, 0, w, h, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, b)
            val bmp = bitmaps[bi] ?: Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { bitmaps[bi] = it }
            bi = (bi + 1) % bitmaps.size
            b.rewind(); bmp.copyPixelsFromBuffer(b)
            main.post { if (!dead) onFrame(bmp) }
        }
    }

    fun release() {
        dead = true
        gl.post {
            runCatching {
                st?.setOnFrameAvailableListener(null); st?.release(); surface?.release()
                if (fbo != 0) GLES20.glDeleteFramebuffers(1, intArrayOf(fbo), 0)
                GLES20.glDeleteTextures(2, intArrayOf(oes, fboTex), 0)
                if (prog != 0) GLES20.glDeleteProgram(prog)
                EGL14.eglMakeCurrent(dpy, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
                EGL14.eglDestroySurface(dpy, pb); EGL14.eglDestroyContext(dpy, ctx); EGL14.eglTerminate(dpy)
            }
            thread.quitSafely()
        }
    }

    private fun program(vs: String, fs: String): Int {
        fun sh(type: Int, src: String) = GLES20.glCreateShader(type).also { GLES20.glShaderSource(it, src); GLES20.glCompileShader(it) }
        return GLES20.glCreateProgram().also { p ->
            GLES20.glAttachShader(p, sh(GLES20.GL_VERTEX_SHADER, vs)); GLES20.glAttachShader(p, sh(GLES20.GL_FRAGMENT_SHADER, fs)); GLES20.glLinkProgram(p)
        }
    }
}
