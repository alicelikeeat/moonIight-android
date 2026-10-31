package com.moonlight.feed.data

/** Icon / label treatment on a media-filled button, picked from the media's average luminance. */
data class RingInk(val lightMedia: Boolean) {
    companion object {
        /** Above this average luminance (0…1, Rec.709) the media counts as light → light scrim + dark ink. */
        const val LIGHT_THRESHOLD = 0.56f
        fun forLuminance(lum: Float) = RingInk(lum > LIGHT_THRESHOLD)
        fun luma(argb: Int): Float {
            val r = (argb shr 16 and 0xFF) / 255f; val g = (argb shr 8 and 0xFF) / 255f; val b = (argb and 0xFF) / 255f
            return 0.2126f * r + 0.7152f * g + 0.0722f * b
        }
        fun average(pixels: IntArray): Float = if (pixels.isEmpty()) 0f else pixels.fold(0f) { s, p -> s + luma(p) } / pixels.size
    }
}
