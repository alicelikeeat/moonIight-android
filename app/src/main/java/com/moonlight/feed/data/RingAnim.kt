package com.moonlight.feed.data

import kotlin.random.Random

/** 长按动画: how a ring opens and closes. [FADE] is the reduce-motion fallback (not user-selectable). */
enum class RingAnim(val label: String, val short: String, val hint: String) {
    SPIN("旋转展开", "旋转", "按钮沿弧线旋转弹出"),
    LOTUS("荷花", "荷花", "每个按钮从花苞中绽放，收起时合拢下沉"),
    STAR("星轨", "星轨", "按钮从远处沿弧线绕着手指飞来，越来越近，落入圆环"),
    MOON("月光", "月光", "全屏暗下，只有按钮被月光照亮（忽略菜单背景）"),
    FADE("淡入淡出", "淡入", "系统已关闭动画时使用");

    companion object {
        val CHOICES = listOf(SPIN, LOTUS, STAR, MOON)
        const val RANDOM = "RANDOM"
        /** Older names: v4.0 模糊 → (坠落 →) 星轨, 破碎 → 荷花, 月光表盘 → 月光; v4.1 坠落 → 星轨. */
        private val LEGACY = mapOf("BLUR" to STAR, "FALL" to STAR, "SHATTER" to LOTUS)
        fun of(v: String?): RingAnim = LEGACY[v?.trim()] ?: entries.firstOrNull { it.name == v?.trim() && it != FADE } ?: SPIN
        /** Like [of] but null for unknown names (used for the 随机 pool). */
        fun parse(v: String?): RingAnim? = LEGACY[v?.trim()] ?: entries.firstOrNull { it.name == v?.trim() && it != FADE }
    }
}

/** Pure choice of the style for one ring opening. */
object RingAnimPick {
    /** Stored value: a style name or [RingAnim.RANDOM]; v4.0 values migrate. */
    fun normalize(v: String?): String = if (v == RingAnim.RANDOM) RingAnim.RANDOM else RingAnim.of(v).name

    /** CSV of styles 随机 may pick from; empty / invalid → all four. v4.0 names migrate. */
    fun parseSet(csv: String?): Set<RingAnim> =
        csv.orEmpty().split(',').mapNotNull { RingAnim.parse(it) }.toSet().ifEmpty { RingAnim.CHOICES.toSet() }
    fun encodeSet(set: Set<RingAnim>): String = RingAnim.CHOICES.filter { it in set }.joinToString(",")

    /**
     * [animatorScale] = system 动画时长缩放 (0 = 关闭动画) → always [RingAnim.FADE].
     * 随机 picks uniformly among [enabled]; otherwise the chosen style.
     */
    fun pick(setting: String?, enabled: Set<RingAnim>, animatorScale: Float, rnd: Random = Random.Default): RingAnim {
        if (animatorScale <= 0f) return RingAnim.FADE
        if (setting != RingAnim.RANDOM) return RingAnim.of(setting)
        val pool = RingAnim.CHOICES.filter { it in enabled }.ifEmpty { RingAnim.CHOICES }
        return pool[rnd.nextInt(pool.size)]
    }
}
