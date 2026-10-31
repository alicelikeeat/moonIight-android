package com.moonlight.feed.data

import com.moonlight.feed.ui.design.RevealSpeed
import com.moonlight.feed.ui.settings.THEME_SEGMENTS
import com.moonlight.feed.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * v2.8 bug: 设置 › 模糊渐显 speed "快" could not be selected. The tap saved FAST, but the control's
 * selected index was `indexOfFirst{..}.coerceAtLeast(1)`, so FAST (index 0) always rendered as 标准.
 * These tests simulate tap → persist → re-render for every segmented control in settings.
 */
class SettingsSegmentsTest {
    private val speeds = RevealSpeed.entries
    private val names = speeds.map { it.name }
    private val standard = speeds.indexOf(RevealSpeed.STANDARD)

    /** What the settings screen does: onSelect(i) stores names[i]; the next frame shows segmentIndex(stored). */
    private fun tapAndRender(i: Int): Int { val stored = names[i]; return segmentIndex(names, stored, standard) }

    @Test fun fastIsSelectable() {
        assertEquals("快 must render as selected after tapping it", 0, tapAndRender(0))
        assertEquals("快", speeds[0].label)
    }

    @Test fun everyRevealSpeedRoundTrips() {
        speeds.indices.forEach { i -> assertEquals("option ${speeds[i].label}", i, tapAndRender(i)) }
    }

    @Test fun unknownOrMissingFallsBackToStandard() {
        assertEquals(standard, segmentIndex(names, "", standard))
        assertEquals(standard, segmentIndex(names, "TURBO", standard))
        assertEquals(1, standard)
    }

    @Test fun themeSegmentsRoundTrip() {
        THEME_SEGMENTS.forEachIndexed { i, m -> assertEquals(i, segmentIndex(THEME_SEGMENTS, m)) }
        assertEquals(listOf(ThemeMode.SYSTEM, ThemeMode.DARK, ThemeMode.LIGHT), THEME_SEGMENTS)
    }

    @Test fun sourceEditorDepthRoundTrips() {
        val depths = listOf(1, 2)
        depths.forEachIndexed { i, d -> assertEquals(i, segmentIndex(depths, d)) }
        assertEquals(0, segmentIndex(depths, 7)) // out-of-range stored value → first option, never a crash
    }

    @Test fun fallbackIsClamped() {
        assertEquals(2, segmentIndex(listOf("a", "b", "c"), "z", 9))
        assertEquals(0, segmentIndex(emptyList<String>(), "z", 3))
    }
}
