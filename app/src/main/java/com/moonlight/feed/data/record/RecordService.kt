package com.moonlight.feed.data.record

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.moonlight.feed.MoonApp
import com.moonlight.feed.data.MediaEntry
import com.moonlight.feed.data.MediaKind
import com.moonlight.feed.data.PlatformId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

/** Foreground service (type mediaProjection) that owns the projection, the virtual display and the encoder. */
class RecordService : Service() {
    private var projection: MediaProjection? = null
    private var display: VirtualDisplay? = null
    private var sink: RecordSink? = null
    private var out: File? = null
    private var toGallery = false
    private val main = Handler(Looper.getMainLooper())
    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> start(intent)
            ACTION_STOP -> finish()
        }
        return START_NOT_STICKY
    }

    private fun notification(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26 && nm.getNotificationChannel(CHANNEL) == null)
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "录屏", NotificationManager.IMPORTANCE_LOW))
        val stop = PendingIntent.getService(this, 1, Intent(this, RecordService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL).setSmallIcon(android.R.drawable.presence_video_online)
            .setContentTitle("月淡 · 正在录屏").setContentText("点「停止」保存到 库").setOngoing(true)
            .addAction(0, "停止", stop).setContentIntent(stop).build()
    }

    private fun start(i: Intent) {
        // Android 14: startForeground(type mediaProjection) must happen before getMediaProjection
        runCatching {
            if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIF_ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
            else startForeground(NOTIF_ID, notification())
        }.onFailure { ScreenRecorder.onFailed("无法启动录屏通知"); stopSelf(); return }
        val code = i.getIntExtra(EXTRA_CODE, 0)
        @Suppress("DEPRECATION") val data: Intent? = i.getParcelableExtra(EXTRA_DATA)
        val mpm = getSystemService(MediaProjectionManager::class.java)
        val p = runCatching { data?.let { mpm.getMediaProjection(code, it) } }.getOrNull()
        if (p == null) { ScreenRecorder.onFailed("录屏授权无效"); stopSelf(); return }
        projection = p
        p.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() { main.post { ScreenRecorder.onProjectionEnded(); finish() } }
        }, main)
        toGallery = i.getBooleanExtra(EXTRA_GALLERY, false)
        val dm = resources.displayMetrics
        // even dimensions, capped at 1080p on the short side (encoders reject odd / huge sizes)
        val scale = minOf(1f, 1080f / minOf(dm.widthPixels, dm.heightPixels))
        val w = ((dm.widthPixels * scale).toInt() / 2) * 2; val h = ((dm.heightPixels * scale).toInt() / 2) * 2
        val dir = File(MoonApp.instance.library.mediaDir(), "").also { it.mkdirs() }
        val f = File(dir, "rec_${System.currentTimeMillis()}.mp4"); out = f
        val mic = i.getBooleanExtra(EXTRA_MIC, false); val appAudio = i.getBooleanExtra(EXTRA_APP_AUDIO, false)
        val s = runCatching {
            if (appAudio && Build.VERSION.SDK_INT >= 29) CodecSink(f, w, h, p, mic) else MediaRecorderSink(this, f, w, h, mic)
        }.getOrElse { ScreenRecorder.onFailed("编码器启动失败：${it.message}"); finish(); return }
        sink = s
        display = runCatching {
            p.createVirtualDisplay("moonlight-rec", w, h, dm.densityDpi, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, s.surface, null, null)
        }.getOrElse { ScreenRecorder.onFailed("无法创建录屏画面"); finish(); return }
        runCatching { s.start() }.onFailure { ScreenRecorder.onFailed("录屏启动失败"); finish(); return }
        ScreenRecorder.onStarted()
    }

    private var finishing = false
    private fun finish() {
        if (finishing) return
        finishing = true
        val s = sink; sink = null
        val ok = s?.let { runCatching { it.stop() }.isSuccess } ?: false
        runCatching { display?.release() }; display = null
        runCatching { projection?.stop() }; projection = null
        val f = out
        if (ok && f != null && f.exists() && f.length() > 0) {
            val entry = MediaEntry(id = "rec-" + f.nameWithoutExtension.removePrefix("rec_"), kind = MediaKind.VIDEO, title = "录屏 " +
                java.text.SimpleDateFormat("M月d日 HH:mm", java.util.Locale.CHINA).format(java.util.Date()),
                localPath = f.absolutePath, platform = PlatformId.LOCAL, author = "我", source = "local")
            MoonApp.instance.library.upsert(entry)
            ScreenRecorder.onSaved(f.absolutePath)
            if (toGallery) io.launch { com.moonlight.feed.data.GallerySaver.saveLocalFile(applicationContext, f, true) }
        } else if (s != null) { f?.delete(); ScreenRecorder.onFailed("录屏太短或未能保存") }
        if (Build.VERSION.SDK_INT >= 24) stopForeground(STOP_FOREGROUND_REMOVE) else @Suppress("DEPRECATION") stopForeground(true)
        stopSelf()
    }

    override fun onDestroy() { if (!finishing) finish(); super.onDestroy() }

    companion object {
        const val ACTION_START = "moonlight.rec.START"; const val ACTION_STOP = "moonlight.rec.STOP"
        const val EXTRA_CODE = "code"; const val EXTRA_DATA = "data"; const val EXTRA_MIC = "mic"
        const val EXTRA_APP_AUDIO = "app_audio"; const val EXTRA_GALLERY = "gallery"
        const val CHANNEL = "record"; const val NOTIF_ID = 5050
    }
}
