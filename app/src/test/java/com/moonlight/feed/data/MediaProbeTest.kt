package com.moonlight.feed.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

class MediaProbeTest {
    @Test fun ftypMp4() {
        val b = byteArrayOf(0,0,0,0x18, 'f'.code.toByte(), 't'.code.toByte(), 'y'.code.toByte(), 'p'.code.toByte()) + ByteArray(16)
        val p = MediaProbe.sniff(ByteArrayInputStream(b))
        assertEquals(MediaProbe.Kind.MP4, p.kind)
        assertTrue(p.isPlayableVideo)
    }

    @Test fun rejectHtml() {
        val b = "<!DOCTYPE html><html>".toByteArray()
        val p = MediaProbe.sniff(ByteArrayInputStream(b))
        assertEquals(MediaProbe.Kind.HTML, p.kind)
    }

    @Test fun jpeg() {
        val b = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte()) + ByteArray(8)
        assertEquals(MediaProbe.Kind.JPEG, MediaProbe.sniff(ByteArrayInputStream(b)).kind)
    }
}
