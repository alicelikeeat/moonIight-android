package com.moonlight.feed.ui.design

import androidx.media3.common.Player
import com.moonlight.feed.data.Playable

/** [Playable] view of a media3 player for [com.moonlight.feed.data.PlayToggle]. */
fun Player.playable(): Playable = object : Playable {
    private val p = this@playable
    override val playWhenReady get() = p.playWhenReady
    override val playbackState get() = p.playbackState
    override val suppressed get() = p.playbackSuppressionReason != Player.PLAYBACK_SUPPRESSION_REASON_NONE
    override val hasMedia get() = p.mediaItemCount > 0
    override fun play() = p.play()
    override fun pause() = p.pause()
    override fun prepare() = p.prepare()
    override fun seekToStart() = p.seekTo(0L)
}
