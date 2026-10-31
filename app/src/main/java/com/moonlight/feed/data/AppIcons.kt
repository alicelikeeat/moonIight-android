package com.moonlight.feed.data

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/** 应用图标 presets = launcher activity-aliases (manifest). Exactly one is enabled. */
enum class AppIcon(val label: String, val alias: String) {
    GIRL("月光少女", ".IconGirl"),
    CLASSIC("月亮（旧版）", ".IconClassic");

    companion object {
        val DEFAULT = GIRL
        fun of(name: String?) = entries.firstOrNull { it.name == name } ?: DEFAULT

        /** Enabled state per alias for [target]: only the target is on. Pure; unit-tested. */
        fun states(target: AppIcon): Map<AppIcon, Boolean> = entries.associateWith { it == target }
    }
}

object AppIconSwitcher {
    private fun cn(ctx: Context, i: AppIcon) = ComponentName(ctx.packageName, ctx.packageName + i.alias)

    fun current(ctx: Context): AppIcon {
        val pm = ctx.packageManager
        return AppIcon.entries.firstOrNull { i ->
            when (pm.getComponentEnabledSetting(cn(ctx, i))) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED -> false
                else -> i == AppIcon.DEFAULT   // manifest default
            }
        } ?: AppIcon.DEFAULT
    }

    /** Enables the target first, then disables the others, so there is never a moment without a launcher entry. */
    fun apply(ctx: Context, target: AppIcon) {
        val pm = ctx.packageManager
        val st = AppIcon.states(target)
        (st.entries.sortedByDescending { it.value }).forEach { (i, on) ->
            pm.setComponentEnabledSetting(
                cn(ctx, i),
                if (on) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP,
            )
        }
    }
}
