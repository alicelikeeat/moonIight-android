package com.moonlight.feed

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.FrameLayout
import androidx.viewpager2.widget.ViewPager2
import kotlin.math.abs

/**
 * Host for WebView (or other touch-hungry children).
 * Intercepts dominant vertical drags and drives ViewPager2 via fakeDrag,
 * so up/down swipe still changes pages while taps/horizontal stay with the child.
 */
class VerticalSwipeHost @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {
    var pager: ViewPager2? = null
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var startX = 0f
    private var startY = 0f
    private var lastY = 0f
    private var dragging = false

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        val p = pager ?: return false
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                startX = ev.x
                startY = ev.y
                lastY = ev.y
                dragging = false
                // Assume child wants touches until we decide it's a vertical page swipe
                parent?.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_MOVE -> {
                val dy = abs(ev.y - startY)
                val dx = abs(ev.x - startX)
                if (!dragging && dy > touchSlop && dy > 1.5f * dx) {
                    dragging = true
                    parent?.requestDisallowInterceptTouchEvent(false)
                    if (!p.isFakeDragging) p.beginFakeDrag()
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (dragging && p.isFakeDragging) p.endFakeDrag()
                dragging = false
            }
        }
        return dragging
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(ev: MotionEvent): Boolean {
        val p = pager ?: return super.onTouchEvent(ev)
        if (!dragging && ev.actionMasked != MotionEvent.ACTION_DOWN) {
            return super.onTouchEvent(ev)
        }
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                startX = ev.x; startY = ev.y; lastY = ev.y
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!dragging) {
                    val dy = abs(ev.y - startY)
                    val dx = abs(ev.x - startX)
                    if (dy > touchSlop && dy > 1.5f * dx) {
                        dragging = true
                        if (!p.isFakeDragging) p.beginFakeDrag()
                    }
                }
                if (dragging && p.isFakeDragging) {
                    val d = ev.y - lastY
                    lastY = ev.y
                    // ViewPager2 vertical: positive fakeDragBy moves finger-following
                    p.fakeDragBy(d)
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (p.isFakeDragging) p.endFakeDrag()
                dragging = false
                return true
            }
        }
        return super.onTouchEvent(ev)
    }
}
