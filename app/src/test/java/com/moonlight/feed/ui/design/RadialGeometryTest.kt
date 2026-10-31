package com.moonlight.feed.ui.design

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RadialGeometryTest {
    private val bounds = Rect(24f, 80f, 1056f, 2200f)
    private val r = 260f
    private val itemR = 110f

    private fun inside(res: RadialGeometry.Result, n: Int) = (0 until n).all { i ->
        val p = res.pos(i)
        p.x - itemR >= bounds.left - 1f && p.x + itemR <= bounds.right + 1f && p.y - itemR >= bounds.top - 1f && p.y + itemR <= bounds.bottom + 1f
    }

    @Test fun centredOriginOpensUpward() {
        val res = RadialGeometry.layout(bounds.center, 7, bounds, r, itemR)
        assertEquals(-90f, res.dir, 0.01f)
        assertTrue(inside(res, 7))
    }

    @Test fun cornersStayOnScreen() {
        listOf(Offset(40f, 2180f), Offset(1040f, 2180f), Offset(40f, 100f), Offset(1040f, 100f), Offset(1050f, 1100f)).forEach { o ->
            listOf(3, 6, 8).forEach { n -> assertTrue("origin=$o n=$n", inside(RadialGeometry.layout(o, n, bounds, r, itemR), n)) }
        }
    }

    @Test fun itemsEvenlySpaced() {
        val res = RadialGeometry.layout(Offset(540f, 1500f), 6, bounds, r, itemR)
        val step = RadialGeometry.stepDeg(6)
        res.angles.zipWithNext().forEach { (a, b) -> assertEquals(step, b - a, 0.01f) }
    }
}
