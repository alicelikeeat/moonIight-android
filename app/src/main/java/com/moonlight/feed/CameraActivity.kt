package com.moonlight.feed

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.moonlight.feed.data.MediaEntry
import com.moonlight.feed.data.MediaKind
import com.moonlight.feed.data.PlatformId
import com.moonlight.feed.data.SaveHelper
import com.moonlight.feed.ui.AppBus
import com.moonlight.feed.ui.camera.CamFlash
import com.moonlight.feed.ui.camera.CamMode
import com.moonlight.feed.ui.camera.CameraCallbacks
import com.moonlight.feed.ui.camera.CameraContent
import com.moonlight.feed.ui.camera.CameraPermissionExplainer
import com.moonlight.feed.ui.camera.CameraUiState
import com.moonlight.feed.ui.camera.PermState
import com.moonlight.feed.ui.design.MoonOverlayHost
import com.moonlight.feed.ui.design.MoonOverlayState
import com.moonlight.feed.ui.design.MoonToaster
import com.moonlight.feed.ui.design.ProceduralArt
import com.moonlight.feed.ui.theme.LocalMoonColors
import com.moonlight.feed.ui.theme.MediaGlass
import com.moonlight.feed.ui.theme.MoonTheme
import com.moonlight.feed.ui.theme.ThemeMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

/**
 * In-app CameraX camera (Preview + ImageCapture + VideoCapture/Recorder).
 * A capture goes straight into the 月光 library (first in 首页) and, if 自动保存到相册 is on, into Pictures/月光 · Movies/月光.
 * After a photo / when a recording stops, the camera closes and 首页 lands on the new item.
 */
class CameraActivity : ComponentActivity() {
    companion object {
        const val EXTRA_MODE = "mode"           // "photo" | "video"
        const val EXTRA_RESULT_ID = "capture_id"
        private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        /** What happens after a capture: "feed" (default: 首页 first item), "moment" (open 朋友圈 composer), "attach" (just return the id). */
        const val EXTRA_FOR = "for"
        fun intent(ctx: Context, video: Boolean, purpose: String = "feed") = Intent(ctx, CameraActivity::class.java)
            .putExtra(EXTRA_MODE, if (video) "video" else "photo").putExtra(EXTRA_FOR, purpose)
    }

    private var finishing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            hide(WindowInsetsCompat.Type.navigationBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        val startVideo = intent.getStringExtra(EXTRA_MODE) == "video"
        setContent {
            MoonTheme(ThemeMode.DARK) {
                val overlay = remember { MoonOverlayState() }
                androidx.compose.runtime.CompositionLocalProvider(LocalMoonColors provides MediaGlass) {
                    MoonOverlayHost(overlay, backdrop = false) { CameraRoot(startVideo) }
                }
            }
        }
    }

    private fun granted(p: String) = ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED

    @Composable
    private fun CameraRoot(startVideo: Boolean) {
        var cam by remember { mutableStateOf(granted(Manifest.permission.CAMERA)) }
        var mic by remember { mutableStateOf(granted(Manifest.permission.RECORD_AUDIO)) }
        val prefs = remember { getSharedPreferences("camera", MODE_PRIVATE) }
        var perm by remember {
            mutableStateOf(if (prefs.getBoolean("asked", false) && !shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) PermState.BLOCKED else PermState.ASK)
        }
        val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
            prefs.edit().putBoolean("asked", true).apply()
            cam = granted(Manifest.permission.CAMERA); mic = granted(Manifest.permission.RECORD_AUDIO)
            if (!cam) perm = if (shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) PermState.DENIED else PermState.BLOCKED
        }
        // Back from system settings: re-check.
        DisposableEffect(Unit) {
            val obs = androidx.lifecycle.LifecycleEventObserver { _, e ->
                if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) { cam = granted(Manifest.permission.CAMERA); mic = granted(Manifest.permission.RECORD_AUDIO) }
            }
            lifecycle.addObserver(obs); onDispose { lifecycle.removeObserver(obs) }
        }
        if (!cam) {
            CameraPermissionExplainer(
                perm, camera = false, mic = mic,
                onContinue = { launcher.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)) },
                onSettings = { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))) },
                onCancel = { finish() },
                backdrop = { ProceduralArt(3, Modifier.fillMaxSize()) },
            )
            return
        }
        LiveCamera(startVideo, mic) { if (!mic) launcher.launch(arrayOf(Manifest.permission.RECORD_AUDIO)) }
    }

    @SuppressLint("MissingPermission")
    @Composable
    private fun LiveCamera(startVideo: Boolean, mic: Boolean, askMic: () -> Unit) {
        val app = MoonApp.instance
        var ui by remember { mutableStateOf(CameraUiState(mode = if (startVideo) CamMode.VIDEO else CamMode.PHOTO, audio = mic)) }
        LaunchedEffect(mic) { ui = ui.copy(audio = mic) }
        var flashPulse by remember { mutableIntStateOf(0) }
        val previewView = remember {
            PreviewView(this).apply {
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                scaleType = PreviewView.ScaleType.FILL_CENTER
            }
        }
        var camera by remember { mutableStateOf<Camera?>(null) }
        var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
        var videoCapture by remember { mutableStateOf<VideoCapture<Recorder>?>(null) }
        var combined by remember { mutableStateOf(true) }
        var recording by remember { mutableStateOf<Recording?>(null) }
        val items by app.library.items.collectAsState()
        val last = items.firstOrNull { it.id.startsWith("cap-") && it.localPath != null && File(it.localPath!!).exists() }
        LaunchedEffect(last?.id) { ui = ui.copy(lastThumb = last?.localPath?.let { File(it) }) }

        // (Re)bind on lens change; in fallback (device can't do all three use cases) also on mode change.
        val bindKey = "${ui.front}|" + if (combined) "" else ui.mode.name
        LaunchedEffect(bindKey) {
            val provider = cameraProvider()
            val selector = if (ui.front) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
            if (!provider.hasCamera(selector)) { MoonToaster.show("没有可用的${if (ui.front) "前置" else "后置"}摄像头", Icons.Outlined.ErrorOutline); ui = ui.copy(front = !ui.front); return@LaunchedEffect }
            val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
            val ic = ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build()
            val recorder = Recorder.Builder()
                .setQualitySelector(QualitySelector.fromOrderedList(listOf(Quality.FHD, Quality.HD, Quality.SD), FallbackStrategy.lowerQualityOrHigherThan(Quality.SD)))
                .build()
            val vc = VideoCapture.withOutput(recorder)
            provider.unbindAll()
            val c = runCatching { provider.bindToLifecycle(this@CameraActivity, selector, preview, ic, vc) }.getOrNull()
            if (c != null) {
                combined = true; camera = c; imageCapture = ic; videoCapture = vc
            } else {
                combined = false
                provider.unbindAll()
                val photo = ui.mode == CamMode.PHOTO
                camera = runCatching {
                    if (photo) provider.bindToLifecycle(this@CameraActivity, selector, preview, ic) else provider.bindToLifecycle(this@CameraActivity, selector, preview, vc)
                }.getOrNull()
                imageCapture = if (photo) ic else null; videoCapture = if (photo) null else vc
                if (camera == null) MoonToaster.show("相机启动失败", Icons.Outlined.ErrorOutline)
            }
            ui = ui.copy(hasFlash = camera?.cameraInfo?.hasFlashUnit() == true, canHoldToRecord = combined, zoom = 1f)
        }
        DisposableEffect(Unit) { onDispose { recording?.stop() } }

        fun newFile(ext: String): File {
            val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            return File(app.library.mediaDir(), "cap_${stamp}.$ext")
        }

        fun deliver(file: File, video: Boolean) {
            if (finishing) return
            finishing = true
            val title = "拍摄 " + SimpleDateFormat("M月d日 HH:mm", Locale.CHINA).format(Date())
            val entry = MediaEntry(
                id = "cap-" + file.nameWithoutExtension.removePrefix("cap_") + "-" + (System.nanoTime() % 100000),
                kind = if (video) MediaKind.VIDEO else MediaKind.IMAGE,
                title = title,
                localPath = file.absolutePath,
                platform = PlatformId.LOCAL,
                author = "我",
                source = "local",
            )
            app.library.upsert(entry)                                 // index 0 of the library
            appScope.launch { runCatching { SaveHelper.maybeSaveEntry(entry) } } // Pictures/月光 · Movies/月光 when 自动保存到相册 is on
            when (intent.getStringExtra(EXTRA_FOR) ?: "feed") {
                "moment" -> { AppBus.composeMoment(listOf(entry.id)); MoonToaster.show("已加入库 · 写点什么吧", Icons.Outlined.CheckCircle) }
                "attach" -> Unit                                   // caller (composer / chat) attaches it
                else -> {
                    AppBus.focusFeed(listOf(entry.id))                // 首页: first item, scrolled to, autoplay
                    MoonToaster.show("已加入库", Icons.Outlined.CheckCircle, action = "发朋友圈") { AppBus.composeMoment(listOf(entry.id)) }
                }
            }
            setResult(Activity.RESULT_OK, Intent().putExtra(EXTRA_RESULT_ID, entry.id).putExtra(EXTRA_FOR, intent.getStringExtra(EXTRA_FOR) ?: "feed"))
            finish()
        }

        fun takePhoto() {
            val ic = imageCapture ?: run {
                // fallback binding in video mode: switch to photo first
                ui = ui.copy(mode = CamMode.PHOTO); return
            }
            ic.flashMode = when (ui.flash) { CamFlash.OFF -> ImageCapture.FLASH_MODE_OFF; CamFlash.AUTO -> ImageCapture.FLASH_MODE_AUTO; CamFlash.ON -> ImageCapture.FLASH_MODE_ON }
            val file = newFile("jpg")
            val meta = ImageCapture.Metadata().apply { isReversedHorizontal = ui.front }
            ui = ui.copy(busy = true)
            flashPulse++
            ic.takePicture(
                ImageCapture.OutputFileOptions.Builder(file).setMetadata(meta).build(),
                ContextCompat.getMainExecutor(this),
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(r: ImageCapture.OutputFileResults) { deliver(file, video = false) }
                    override fun onError(e: ImageCaptureException) {
                        file.delete(); ui = ui.copy(busy = false)
                        MoonToaster.show("拍照失败：${e.message ?: e.imageCaptureError}", Icons.Outlined.ErrorOutline)
                    }
                },
            )
        }

        fun startRecording() {
            val vc = videoCapture ?: run { ui = ui.copy(mode = CamMode.VIDEO); return }
            if (recording != null) return
            val file = newFile("mp4")
            if (ui.flash != CamFlash.OFF && ui.hasFlash) camera?.cameraControl?.enableTorch(true)
            val pending = vc.output.prepareRecording(this, FileOutputOptions.Builder(file).build())
                .let { if (granted(Manifest.permission.RECORD_AUDIO)) it.withAudioEnabled() else it }
            ui = ui.copy(recording = true, recordMs = 0)
            recording = pending.start(ContextCompat.getMainExecutor(this)) { ev ->
                when (ev) {
                    is VideoRecordEvent.Status -> ui = ui.copy(recordMs = TimeUnit.NANOSECONDS.toMillis(ev.recordingStats.recordedDurationNanos))
                    is VideoRecordEvent.Finalize -> {
                        recording = null
                        camera?.cameraControl?.enableTorch(false)
                        ui = ui.copy(recording = false)
                        val ok = file.exists() && file.length() > 0 &&
                            (!ev.hasError() || ev.error == VideoRecordEvent.Finalize.ERROR_SOURCE_INACTIVE || ev.error == VideoRecordEvent.Finalize.ERROR_DURATION_LIMIT_REACHED || ev.error == VideoRecordEvent.Finalize.ERROR_FILE_SIZE_LIMIT_REACHED)
                        if (ok && ev.recordingStats.recordedDurationNanos > 300_000_000L) deliver(file, video = true)
                        else {
                            file.delete()
                            MoonToaster.show(if (ev.hasError()) "录像失败（${ev.error}）" else "录像太短", Icons.Outlined.ErrorOutline)
                        }
                    }
                    else -> Unit
                }
            }
        }
        fun stopRecording() { recording?.stop() }

        BackHandler(ui.recording) { stopRecording() }

        CameraContent(
            ui, flashPulse = flashPulse,
            cb = CameraCallbacks(
                onClose = { finish() },
                onFlash = { ui = ui.copy(flash = when (ui.flash) { CamFlash.OFF -> CamFlash.AUTO; CamFlash.AUTO -> CamFlash.ON; CamFlash.ON -> CamFlash.OFF }) },
                onFlip = { ui = ui.copy(front = !ui.front) },
                onMode = { m -> ui = ui.copy(mode = m); if (m == CamMode.VIDEO && !granted(Manifest.permission.RECORD_AUDIO)) askMic() },
                onShutter = {
                    when {
                        ui.recording -> stopRecording()
                        ui.mode == CamMode.PHOTO -> takePhoto()
                        else -> startRecording()
                    }
                },
                onHoldStart = { startRecording() },
                onHoldEnd = { stopRecording() },
                onThumb = { last?.let { AppBus.focusFeed(listOf(it.id)); setResult(Activity.RESULT_OK, Intent().putExtra(EXTRA_RESULT_ID, it.id)); finish() } },
                onFocus = { p ->
                    val c = camera ?: return@CameraCallbacks
                    val point = previewView.meteringPointFactory.createPoint(p.x, p.y)
                    c.cameraControl.startFocusAndMetering(
                        FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE)
                            .setAutoCancelDuration(3, TimeUnit.SECONDS).build()
                    )
                },
                onPinch = { z ->
                    val c = camera ?: return@CameraCallbacks
                    val st = c.cameraInfo.zoomState.value ?: return@CameraCallbacks
                    val r = (st.zoomRatio * z).coerceIn(st.minZoomRatio, st.maxZoomRatio)
                    c.cameraControl.setZoomRatio(r); ui = ui.copy(zoom = r)
                },
                onZoomPreset = {
                    val c = camera ?: return@CameraCallbacks
                    val st = c.cameraInfo.zoomState.value ?: return@CameraCallbacks
                    val r = (if (ui.zoom < 1.9f) 2f else 1f).coerceIn(st.minZoomRatio, st.maxZoomRatio)
                    c.cameraControl.setZoomRatio(r); ui = ui.copy(zoom = r)
                },
            ),
        ) {
            AndroidView({ previewView }, Modifier.fillMaxSize())
        }
    }

    private suspend fun cameraProvider(): ProcessCameraProvider = suspendCancellableCoroutine { cont ->
        val f = ProcessCameraProvider.getInstance(this)
        f.addListener({ cont.resume(f.get()) }, ContextCompat.getMainExecutor(this))
    }
}
