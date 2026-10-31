package com.moonlight.feed.data.record

/** What to capture. [appAudio] = AudioPlaybackCapture (Android 10+, apps that allow it); [mic] = microphone. */
data class RecordOptions(val mic: Boolean = false, val appAudio: Boolean = true, val toGallery: Boolean = false)

sealed interface RecState {
    data object Idle : RecState
    /** Waiting for the system consent dialog. */
    data object Requesting : RecState
    /** Consent given; foreground service + projection + encoder starting. */
    data class Starting(val opts: RecordOptions) : RecState
    data class Recording(val startedAt: Long, val opts: RecordOptions) : RecState
    /** Finalising the MP4 (moov atom) and adding it to 库. */
    data object Stopping : RecState
    data class Saved(val path: String, val durationMs: Long) : RecState
    data class Denied(val reason: String) : RecState
    data class Failed(val message: String) : RecState
    val active: Boolean get() = this is Requesting || this is Starting || this is Recording || this is Stopping
}

/** The Android side (MediaProjection consent, foreground service, MediaRecorder) — faked in tests. */
interface ProjectionPort {
    fun requestConsent()
    fun start(consent: Any, opts: RecordOptions)
    fun stop()
}

/**
 * 录屏 state machine (pure). Never double-starts, handles a declined consent dialog, the system revoking the projection
 * (status-bar 停止 / screen lock on some devices) and encoder failures; every path ends in Saved / Denied / Failed / Idle.
 */
class RecordingMachine(private val port: ProjectionPort, private val clock: () -> Long = System::currentTimeMillis) {
    var state: RecState = RecState.Idle; private set
    private var opts = RecordOptions()
    private var startedAt = 0L
    var listener: ((RecState) -> Unit)? = null
    private fun set(s: RecState) { state = s; listener?.invoke(s) }

    fun request(o: RecordOptions): Boolean {
        if (state.active) return false
        opts = o; set(RecState.Requesting); port.requestConsent(); return true
    }

    /** Result of the consent dialog. [token] = the projection result data (null / !granted → denied). */
    fun consent(granted: Boolean, token: Any?) {
        if (state != RecState.Requesting) return
        if (!granted || token == null) { set(RecState.Denied("已取消录屏授权")); return }
        set(RecState.Starting(opts)); port.start(token, opts)
    }

    /** The encoder is running. */
    fun started() {
        val s = state as? RecState.Starting ?: return
        startedAt = clock(); set(RecState.Recording(startedAt, s.opts))
    }

    /** ⏹ on the pill / the notification. */
    fun stop() {
        when (state) {
            is RecState.Recording -> { set(RecState.Stopping); port.stop() }
            is RecState.Starting -> { port.stop(); set(RecState.Idle) }
            RecState.Requesting -> set(RecState.Idle)
            else -> Unit
        }
    }

    /** The system ended the projection by itself. */
    fun projectionEnded() { if (state is RecState.Recording) { set(RecState.Stopping); port.stop() } else if (state is RecState.Starting) { set(RecState.Failed("录屏被系统中止")) } }

    fun saved(path: String) {
        if (state != RecState.Stopping && state !is RecState.Recording) return
        set(RecState.Saved(path, (clock() - startedAt).coerceAtLeast(0L)))
    }

    fun failed(message: String) {
        val wasRunning = state is RecState.Recording || state is RecState.Starting
        if (!state.active) return
        set(RecState.Failed(message))
        if (wasRunning) port.stop()
    }

    /** Acknowledge a final state (toast shown) → Idle. */
    fun reset() { if (!state.active) set(RecState.Idle) }

    fun elapsedMs(now: Long = clock()): Long = (state as? RecState.Recording)?.let { (now - it.startedAt).coerceAtLeast(0L) } ?: 0L

    companion object {
        fun timer(ms: Long): String { val s = ms / 1000; return "%d:%02d".format(s / 60, s % 60) }
    }
}
