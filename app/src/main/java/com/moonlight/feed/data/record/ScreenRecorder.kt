package com.moonlight.feed.data.record

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 录屏 entry point. Consent → foreground service (type mediaProjection, with a notification) → MediaRecorder / MediaCodec
 * into an MP4 → added to 库 (and optionally the system gallery). Protected (DRM / FLAG_SECURE) content records black — no bypass.
 */
object ScreenRecorder {
    private val _state = MutableStateFlow<RecState>(RecState.Idle)
    val state: StateFlow<RecState> = _state

    private var appCtx: Context? = null
    private var consentLauncher: ActivityResultLauncher<Intent>? = null
    private var micLauncher: ActivityResultLauncher<String>? = null
    private var pendingOpts: RecordOptions? = null

    private val port = object : ProjectionPort {
        override fun requestConsent() {
            val ctx = appCtx ?: return machine.consent(false, null)
            val l = consentLauncher ?: return machine.consent(false, null)
            val mpm = ctx.getSystemService(MediaProjectionManager::class.java) ?: return machine.consent(false, null)
            runCatching { l.launch(mpm.createScreenCaptureIntent()) }.onFailure { machine.failed("无法打开录屏授权") }
        }
        override fun start(consent: Any, opts: RecordOptions) {
            val ctx = appCtx ?: return machine.failed("录屏不可用")
            val (code, data) = consent as Pair<*, *>
            val i = Intent(ctx, RecordService::class.java).setAction(RecordService.ACTION_START)
                .putExtra(RecordService.EXTRA_CODE, code as Int).putExtra(RecordService.EXTRA_DATA, data as Intent)
                .putExtra(RecordService.EXTRA_MIC, opts.mic).putExtra(RecordService.EXTRA_APP_AUDIO, opts.appAudio)
                .putExtra(RecordService.EXTRA_GALLERY, opts.toGallery)
            runCatching { ContextCompat.startForegroundService(ctx, i) }.onFailure { machine.failed("无法启动录屏服务") }
        }
        override fun stop() {
            val ctx = appCtx ?: return
            runCatching { ctx.startService(Intent(ctx, RecordService::class.java).setAction(RecordService.ACTION_STOP)) }
        }
    }
    val machine: RecordingMachine = RecordingMachine(port).also { m -> m.listener = { _state.value = it } }

    /** Call in each activity's onCreate (registers the consent + microphone launchers for it). */
    fun attach(activity: ComponentActivity) {
        appCtx = activity.applicationContext
        consentLauncher = activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
            machine.consent(r.resultCode == Activity.RESULT_OK && r.data != null, r.data?.let { r.resultCode to it })
        }
        micLauncher = activity.registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            val o = pendingOpts ?: return@registerForActivityResult
            pendingOpts = null
            // declined → record video only (graceful), say so
            val opts = if (granted) o else o.copy(mic = false, appAudio = false)
            if (!granted) com.moonlight.feed.ui.design.MoonToaster.show("未授予录音权限 · 只录画面")
            machine.request(opts)
        }
    }

    fun recording(): Boolean = _state.value.active

    /** 录屏 from a ring / sheet / 全部按键: toggles (stops when already recording). */
    fun toggle(ctx: Context, opts: RecordOptions) {
        if (recording()) { machine.stop(); return }
        if (consentLauncher == null) { com.moonlight.feed.ui.design.MoonToaster.show("录屏不可用"); return }
        val needAudio = opts.mic || (opts.appAudio && Build.VERSION.SDK_INT >= 29)
        val o = if (Build.VERSION.SDK_INT < 29) opts.copy(appAudio = false) else opts
        if (needAudio && ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            pendingOpts = o; micLauncher?.launch(Manifest.permission.RECORD_AUDIO) ?: machine.request(o.copy(mic = false, appAudio = false))
            return
        }
        machine.request(o)
    }

    fun stop() = machine.stop()

    // service → machine
    internal fun onStarted() = machine.started()
    internal fun onSaved(path: String) = machine.saved(path)
    internal fun onFailed(msg: String) = machine.failed(msg)
    internal fun onProjectionEnded() = machine.projectionEnded()
}
