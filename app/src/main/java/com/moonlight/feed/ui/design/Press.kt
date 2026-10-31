package com.moonlight.feed.ui.design

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView

/** Haptic vocabulary: strong on menu open, light on selections. */
object Haptics {
    fun open(v: View) {
        v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    }
    fun context(v: View) {
        v.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
    }
    fun tick(v: View) {
        v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }
    fun light(v: View) {
        v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }
    fun confirm(v: View) {
        if (Build.VERSION.SDK_INT >= 30) v.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        else v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
    }
}

/** Spring used everywhere for "lively" motion. */
fun <T> moonSpring(damping: Float = 0.72f, stiffness: Float = Spring.StiffnessMediumLow) =
    spring<T>(dampingRatio = damping, stiffness = stiffness)

/**
 * Every pressable: spring press-scale (~0.92) with bounce back, subtle highlight instead of ripple,
 * light haptic on tap, strong haptic + optional long-press action.
 */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.moonPress(
    onClick: (() -> Unit)?,
    onLongClick: (() -> Unit)? = null,
    pressedScale: Float = 0.92f,
    highlightShape: Shape? = RectangleShape,
    highlight: Color = Color.White.copy(alpha = 0.08f),
    enabled: Boolean = true,
    hapticOnClick: Boolean = true,
    /** v4.0: double-tap = 喜欢 / 赞 where an object supports it. */
    onDoubleClick: (() -> Unit)? = null,
): Modifier = composed {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val view = LocalView.current
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = if (pressed) 900f else 420f),
        label = "press",
    )
    val hl by animateFloatAsState(if (pressed) 1f else 0f, spring(stiffness = 1200f), label = "hl")
    this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .combinedClickable(
            interactionSource = interaction,
            indication = null,
            enabled = enabled,
            onClick = {
                if (hapticOnClick) Haptics.light(view)
                onClick?.invoke()
            },
            onLongClick = onLongClick?.let { lc ->
                {
                    Haptics.open(view)
                    lc()
                }
            },
            onDoubleClick = onDoubleClick?.let { dc -> { Haptics.confirm(view); dc() } },
        )
        .drawWithContent {
            drawContent()
            if (hl > 0.01f && highlightShape != null) {
                drawOutline(highlightShape.createOutline(size, layoutDirection, this), highlight, alpha = hl)
            }
        }
}
