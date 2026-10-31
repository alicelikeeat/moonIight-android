package com.moonlight.feed.data.record

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.os.Build
import android.view.Surface
import androidx.annotation.RequiresApi
import java.io.File

/** Where the virtual display renders, and how the MP4 gets written. */
interface RecordSink {
    val surface: Surface
    fun start()
    /** Finalise the file; throws when nothing usable was written. */
    fun stop()
}

/** Video (+ optional microphone) through MediaRecorder → MP4. */
class MediaRecorderSink(ctx: Context, file: File, w: Int, h: Int, mic: Boolean) : RecordSink {
    @Suppress("DEPRECATION")
    private val rec = (if (Build.VERSION.SDK_INT >= 31) MediaRecorder(ctx) else MediaRecorder()).apply {
        if (mic) setAudioSource(MediaRecorder.AudioSource.MIC)
        setVideoSource(MediaRecorder.VideoSource.SURFACE)
        setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        setVideoEncoder(MediaRecorder.VideoEncoder.H264)
        setVideoSize(w, h); setVideoFrameRate(30); setVideoEncodingBitRate((w * h * 6).coerceIn(2_000_000, 12_000_000))
        if (mic) { setAudioEncoder(MediaRecorder.AudioEncoder.AAC); setAudioEncodingBitRate(128_000); setAudioSamplingRate(44_100) }
        setOutputFile(file.absolutePath)
        prepare()
    }
    override val surface: Surface get() = rec.surface
    override fun start() = rec.start()
    override fun stop() { try { rec.stop() } finally { rec.release() } }
}

/**
 * Video through a MediaCodec input surface + in-app audio from AudioPlaybackCapture (and the microphone mixed in when
 * enabled) → AAC, both muxed into one MP4. Apps / content that opt out of capture simply contribute silence.
 */
@RequiresApi(29)
class CodecSink(file: File, w: Int, h: Int, projection: MediaProjection, private val mic: Boolean) : RecordSink {
    private val muxer = MediaMuxer(file.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
    private val video = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
    private val audio = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
    override val surface: Surface
    private val appRec: AudioRecord
    private val micRec: AudioRecord?
    private val lock = Object()
    private var vTrack = -1; private var aTrack = -1; private var muxing = false
    @Volatile private var running = false
    private var samples = 0
    private val threads = ArrayList<Thread>()

    init {
        val vf = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, w, h).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, (w * h * 6).coerceIn(2_000_000, 12_000_000))
            setInteger(MediaFormat.KEY_FRAME_RATE, 30); setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2)
        }
        video.configure(vf, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        surface = video.createInputSurface()
        val af = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, RATE, 2).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, 160_000); setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16_384)
        }
        audio.configure(af, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        val fmt = AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(RATE).setChannelMask(AudioFormat.CHANNEL_IN_STEREO).build()
        val buf = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_STEREO, AudioFormat.ENCODING_PCM_16BIT).coerceAtLeast(8192) * 2
        val cap = AudioPlaybackCaptureConfiguration.Builder(projection)
            .addMatchingUsage(AudioAttributes.USAGE_MEDIA).addMatchingUsage(AudioAttributes.USAGE_GAME).addMatchingUsage(AudioAttributes.USAGE_UNKNOWN).build()
        @SuppressLint("MissingPermission")
        val a = AudioRecord.Builder().setAudioFormat(fmt).setBufferSizeInBytes(buf).setAudioPlaybackCaptureConfig(cap).build()
        appRec = a
        @SuppressLint("MissingPermission")
        val m = if (mic) runCatching { AudioRecord(MediaRecorder.AudioSource.MIC, RATE, AudioFormat.CHANNEL_IN_STEREO, AudioFormat.ENCODING_PCM_16BIT, buf) }.getOrNull() else null
        micRec = m
    }

    override fun start() {
        running = true
        video.start(); audio.start(); appRec.startRecording(); micRec?.startRecording()
        threads += Thread({ drainLoop(video, true) }, "rec-video")
        threads += Thread({ audioLoop() }, "rec-audio")
        threads.forEach { it.start() }
    }

    private fun audioLoop() {
        val frame = ShortArray(2048); val micFrame = ShortArray(2048)
        while (running) {
            val n = appRec.read(frame, 0, frame.size)
            if (n <= 0) continue
            micRec?.let { r ->
                val k = r.read(micFrame, 0, n)
                for (i in 0 until minOf(n, k.coerceAtLeast(0))) frame[i] = (frame[i] + micFrame[i]).coerceIn(-32768, 32767).toShort()
            }
            queueAudio(frame, n, System.nanoTime() / 1000, false)
            drain(audio, false)
        }
        queueAudio(frame, 0, System.nanoTime() / 1000, true)
        drainLoop(audio, false)
    }

    private fun queueAudio(pcm: ShortArray, n: Int, ptsUs: Long, eos: Boolean) {
        val idx = audio.dequeueInputBuffer(10_000); if (idx < 0) return
        val b = audio.getInputBuffer(idx) ?: return
        b.clear()
        val count = minOf(n, b.remaining() / 2)
        for (i in 0 until count) b.putShort(pcm[i])
        audio.queueInputBuffer(idx, 0, count * 2, ptsUs, if (eos) MediaCodec.BUFFER_FLAG_END_OF_STREAM else 0)
    }

    /** Drains until end of stream (video: after signalEndOfInputStream). */
    private fun drainLoop(codec: MediaCodec, isVideo: Boolean) {
        while (true) { if (drain(codec, isVideo)) return; if (!running && isVideo && eosSent) continue }
    }
    @Volatile private var eosSent = false

    private val info = MediaCodec.BufferInfo()
    private val ainfo = MediaCodec.BufferInfo()
    /** One pass; true at end of stream. */
    private fun drain(codec: MediaCodec, isVideo: Boolean): Boolean {
        val bi = if (isVideo) info else ainfo
        while (true) {
            val idx = codec.dequeueOutputBuffer(bi, 10_000)
            when {
                idx == MediaCodec.INFO_TRY_AGAIN_LATER -> return false
                idx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> synchronized(lock) {
                    if (isVideo) vTrack = muxer.addTrack(codec.outputFormat) else aTrack = muxer.addTrack(codec.outputFormat)
                    if (vTrack >= 0 && aTrack >= 0 && !muxing) { muxer.start(); muxing = true; lock.notifyAll() }
                }
                idx >= 0 -> {
                    val out = codec.getOutputBuffer(idx)
                    val config = bi.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0
                    if (out != null && bi.size > 0 && !config) synchronized(lock) {
                        if (muxing) { muxer.writeSampleData(if (isVideo) vTrack else aTrack, out, bi); if (isVideo) samples++ }
                    }
                    codec.releaseOutputBuffer(idx, false)
                    if (bi.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return true
                }
            }
        }
    }

    override fun stop() {
        running = false
        runCatching { video.signalEndOfInputStream() }; eosSent = true
        threads.forEach { runCatching { it.join(3000) } }
        runCatching { appRec.stop() }; appRec.release(); micRec?.let { runCatching { it.stop() }; it.release() }
        runCatching { video.stop() }; video.release(); runCatching { audio.stop() }; audio.release()
        val ok = muxing && samples > 0
        runCatching { if (muxing) muxer.stop() }; runCatching { muxer.release() }
        surface.release()
        if (!ok) error("no frames")
    }

    companion object { const val RATE = 44_100 }
}
