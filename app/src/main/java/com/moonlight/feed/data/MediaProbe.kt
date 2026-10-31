package com.moonlight.feed.data

import java.io.File
import java.io.InputStream

object MediaProbe {
    enum class Kind { MP4, WEBM, MKV, JPEG, PNG, WEBP, GIF, HTML, UNKNOWN }

    data class Probe(
        val kind: Kind,
        val mime: String,
        val isVideo: Boolean,
        val isImage: Boolean,
        val isPlayableVideo: Boolean,
        val note: String = "",
    )

    fun sniff(file: File): Probe {
        if (!file.exists() || file.length() == 0L) {
            return Probe(Kind.UNKNOWN, "application/octet-stream", false, false, false, "空文件")
        }
        file.inputStream().use { return sniff(it, file.length()) }
    }

    fun sniff(input: InputStream, sizeHint: Long = -1): Probe {
        val buf = ByteArray(32)
        val n = input.read(buf)
        if (n <= 0) return Probe(Kind.UNKNOWN, "application/octet-stream", false, false, false, "无法读取")
        val kind = when {
            isHtml(buf, n) -> Kind.HTML
            isFtyp(buf, n) -> Kind.MP4
            isEbml(buf, n) -> Kind.WEBM // or mkv; both EBML
            isJpeg(buf, n) -> Kind.JPEG
            isPng(buf, n) -> Kind.PNG
            isGif(buf, n) -> Kind.GIF
            isWebp(buf, n) -> Kind.WEBP
            else -> Kind.UNKNOWN
        }
        val mime = when (kind) {
            Kind.MP4 -> "video/mp4"
            Kind.WEBM, Kind.MKV -> "video/webm"
            Kind.JPEG -> "image/jpeg"
            Kind.PNG -> "image/png"
            Kind.WEBP -> "image/webp"
            Kind.GIF -> "image/gif"
            Kind.HTML -> "text/html"
            Kind.UNKNOWN -> "application/octet-stream"
        }
        val isVideo = kind == Kind.MP4 || kind == Kind.WEBM || kind == Kind.MKV
        val isImage = kind == Kind.JPEG || kind == Kind.PNG || kind == Kind.WEBP || kind == Kind.GIF
        val note = when {
            kind == Kind.HTML -> "服务器返回了网页而非媒体"
            kind == Kind.UNKNOWN && sizeHint == 0L -> "0 字节"
            kind == Kind.UNKNOWN -> "无法识别文件头"
            else -> "ok"
        }
        return Probe(kind, mime, isVideo, isImage, isVideo && kind != Kind.HTML, note)
    }

    fun looksLikeVideoMime(mime: String?): Boolean {
        val m = mime?.lowercase() ?: return false
        return m.startsWith("video/") || m == "application/mp4" || m == "application/octet-stream"
    }

    fun looksLikeImageMime(mime: String?): Boolean {
        val m = mime?.lowercase() ?: return false
        return m.startsWith("image/")
    }

    private fun isFtyp(b: ByteArray, n: Int): Boolean {
        if (n < 8) return false
        // ....ftyp
        return b[4] == 'f'.code.toByte() && b[5] == 't'.code.toByte() &&
            b[6] == 'y'.code.toByte() && b[7] == 'p'.code.toByte()
    }

    private fun isEbml(b: ByteArray, n: Int): Boolean {
        // 1A 45 DF A3
        return n >= 4 && b[0] == 0x1A.toByte() && b[1] == 0x45.toByte() &&
            b[2] == 0xDF.toByte() && b[3] == 0xA3.toByte()
    }

    private fun isJpeg(b: ByteArray, n: Int) =
        n >= 3 && b[0] == 0xFF.toByte() && b[1] == 0xD8.toByte() && b[2] == 0xFF.toByte()

    private fun isPng(b: ByteArray, n: Int) =
        n >= 8 && b[0] == 0x89.toByte() && b[1] == 0x50.toByte() && b[2] == 0x4E.toByte() && b[3] == 0x47.toByte()

    private fun isGif(b: ByteArray, n: Int) =
        n >= 4 && b[0] == 'G'.code.toByte() && b[1] == 'I'.code.toByte() && b[2] == 'F'.code.toByte()

    private fun isWebp(b: ByteArray, n: Int) =
        n >= 12 && b[0] == 'R'.code.toByte() && b[1] == 'I'.code.toByte() &&
            b[2] == 'F'.code.toByte() && b[3] == 'F'.code.toByte() &&
            b[8] == 'W'.code.toByte() && b[9] == 'E'.code.toByte() &&
            b[10] == 'B'.code.toByte() && b[11] == 'P'.code.toByte()

    private fun isHtml(b: ByteArray, n: Int): Boolean {
        val s = b.copyOf(n).toString(Charsets.UTF_8).trimStart().lowercase()
        return s.startsWith("<!doctype html") || s.startsWith("<html") || s.startsWith("<?xml")
    }
}
