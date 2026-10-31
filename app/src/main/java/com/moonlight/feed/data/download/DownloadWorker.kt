package com.moonlight.feed.data.download

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.moonlight.feed.MoonApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/** App-wide 主动下载 wiring (WorkManager + store + engine). Initialised in [MoonApp.onCreate], before any worker runs. */
object Downloads {
    const val MAX_ATTEMPTS = 6
    const val TAG = "moon-download"
    const val CHANNEL = "downloads"
    lateinit var manager: DownloadManager; private set
    lateinit var engine: DownloadEngine; private set
    val ready get() = ::manager.isInitialized

    fun init(app: MoonApp) {
        if (ready) return
        val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val store = DownloadStore(java.io.File(app.filesDir, "downloads.json"), io)
        val main = Handler(Looper.getMainLooper())
        manager = DownloadManager(store, WorkScheduler(app), AutoCache.downloadPartDir(app.filesDir).also { it.mkdirs() }) { id -> app.library.items.value.any { it.id == id } }
        engine = DownloadEngine(store, RangeFetch(app.http), manager, app.library.mediaDir(), finish = { e -> main.post { app.library.upsert(e) } })
        manager.restore()
    }

    fun uniqueName(id: String) = "dl:$id"
}

class WorkScheduler(private val ctx: Context) : DownloadScheduler {
    override fun enqueue(id: String, replace: Boolean) {
        val req = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setInputData(workDataOf(DownloadWorker.KEY_ID to id))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 20, TimeUnit.SECONDS)
            .addTag(Downloads.TAG)
            .build()
        WorkManager.getInstance(ctx).enqueueUniqueWork(Downloads.uniqueName(id), if (replace) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP, req)
    }
    override fun cancel(id: String) { WorkManager.getInstance(ctx).cancelUniqueWork(Downloads.uniqueName(id)) }
}

/** One CoroutineWorker per task, in the foreground with a progress notification. */
class DownloadWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    private val id = inputData.getString(KEY_ID).orEmpty()
    private val nid = 0x4D00 + (id.hashCode() and 0xFFF)

    override suspend fun doWork(): Result {
        if (id.isBlank()) return Result.failure()
        Downloads.init(applicationContext as MoonApp)
        val t = Downloads.manager.store.get(id) ?: return Result.success()
        runCatching { setForeground(info(t)) }   // Android 12+ may refuse from the background: keep going without it
        var lastNote = 0L
        val step = withContext(Dispatchers.IO) {
            Downloads.engine.execute(id, runAttemptCount, Downloads.MAX_ATTEMPTS, { isStopped }) { p ->
                val now = System.currentTimeMillis()
                if (now - lastNote > 1000) { lastNote = now; runCatching { setForegroundAsync(info(p)) } }
            }
        }
        if (step == DownloadEngine.Step.DONE || step == DownloadEngine.Step.FAILED) Downloads.manager.store.get(id)?.let { notifyEnd(it) }
        return when (step) {
            DownloadEngine.Step.RETRY -> Result.retry()
            DownloadEngine.Step.FAILED -> Result.failure()
            else -> Result.success()
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo = info(Downloads.manager.store.get(id) ?: DownloadTask(id, "", "下载", com.moonlight.feed.data.MediaKind.VIDEO))

    private fun channel(): String {
        val nm = applicationContext.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26 && nm.getNotificationChannel(Downloads.CHANNEL) == null)
            nm.createNotificationChannel(NotificationChannel(Downloads.CHANNEL, "下载", NotificationManager.IMPORTANCE_LOW))
        return Downloads.CHANNEL
    }

    private fun openApp(): PendingIntent? = applicationContext.packageManager.getLaunchIntentForPackage(applicationContext.packageName)?.let {
        PendingIntent.getActivity(applicationContext, 0, it.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun info(t: DownloadTask): ForegroundInfo {
        val p = t.progress
        val n = NotificationCompat.Builder(applicationContext, channel())
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(t.title.ifBlank { "下载" })
            .setContentText(if (p != null) "${(p * 100).toInt()}% · ${DownloadFormat.bytes(t.bytes)} / ${DownloadFormat.bytes(t.total)}" else "正在下载 · ${DownloadFormat.bytes(t.bytes)}")
            .setProgress(100, ((p ?: 0f) * 100).toInt(), p == null)
            .setOngoing(true).setOnlyAlertOnce(true).setSilent(true)
            .setContentIntent(openApp())
            .build()
        return if (Build.VERSION.SDK_INT >= 29) ForegroundInfo(nid, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC) else ForegroundInfo(nid, n)
    }

    private fun notifyEnd(t: DownloadTask) = runCatching {
        val ok = t.state == DlState.DONE
        val n = NotificationCompat.Builder(applicationContext, channel())
            .setSmallIcon(if (ok) android.R.drawable.stat_sys_download_done else android.R.drawable.stat_notify_error)
            .setContentTitle(if (ok) "已下载 · 在 库 › 我的下载" else "下载失败")
            .setContentText(if (ok) t.title else "${t.title} · ${t.error ?: ""}")
            .setAutoCancel(true).setContentIntent(openApp())
            .build()
        applicationContext.getSystemService(NotificationManager::class.java).notify(nid + 0x1000, n)
    }

    companion object { const val KEY_ID = "id" }
}

object DownloadFormat {
    fun bytes(b: Long): String = when {
        b < 0 -> "?"
        b < 1024 -> "$b B"
        b < 1024L * 1024 -> "%.0f KB".format(b / 1024.0)
        b < 1024L * 1024 * 1024 -> "%.1f MB".format(b / 1048576.0)
        else -> "%.2f GB".format(b / 1073741824.0)
    }
}
