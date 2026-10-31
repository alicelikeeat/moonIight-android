package com.moonlight.feed.ui.design

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.moonlight.feed.data.HomeBgKind
import com.moonlight.feed.data.HomeBgSpec
import com.moonlight.feed.data.VideoBudget
import java.io.File

val LocalHomeBg = compositionLocalOf { HomeBgSpec(HomeBgKind.THEME, null, true, false) }

private val BackgroundBudget = VideoBudget(1)

/**
 * The 首页 backdrop (visible behind letterboxed media, the empty state and during reveal):
 * 跟随主题 = the same gradient as every other page, 纯黑, a photo, or a muted looping video — optionally blurred + dimmed.
 */
@Composable
fun HomeBackdrop(spec: HomeBgSpec, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit = {}) {
    when (spec.kind) {
        HomeBgKind.THEME -> MoonBackground(modifier, content)
        HomeBgKind.BLACK -> Box(modifier.fillMaxSize().background(Color.Black), content = content)
        HomeBgKind.PHOTO, HomeBgKind.VIDEO -> Box(modifier.fillMaxSize().background(Color.Black)) {
            val m = Modifier.fillMaxSize().then(if (spec.dim && Build.VERSION.SDK_INT >= 31) Modifier.blur(28.dp) else Modifier)
            val p = spec.path!!
            // saved position/zoom (调整位置) applies to both; default = upper-third bias
            val f = spec.focus ?: com.moonlight.feed.data.MediaFocus.DEFAULT
            if (spec.kind == HomeBgKind.VIDEO) LoopVideo(p, m, BackgroundBudget, focus = f)
            else if (p.startsWith("art:")) FocusedArt(p.removePrefix("art:").toIntOrNull() ?: 0, f, m)
            else MoonImage(File(p), m, FocusContentScale(f.scale), alignment = focusAlignment(f))
            if (spec.dim) Box(Modifier.fillMaxSize().background(Color.Black.copy(0.42f)))
            content()
        }
    }
}
