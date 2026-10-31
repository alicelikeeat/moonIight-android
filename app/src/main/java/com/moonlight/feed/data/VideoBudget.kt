package com.moonlight.feed.data

/**
 * Caps how many decorative looping videos (video avatars, covers, profile backgrounds, page backgrounds) decode at once.
 * Holders beyond the cap show the first frame instead — the "fall back when perf is low" path. Pure; unit-tested.
 */
class VideoBudget(val max: Int) {
    private val holders = LinkedHashSet<Any>()
    @Synchronized fun acquire(key: Any): Boolean {
        if (key in holders) return true
        if (holders.size >= max) return false
        holders += key; return true
    }
    @Synchronized fun release(key: Any) { holders -= key }
    val active: Int @Synchronized get() = holders.size

    companion object {
        /** Shared app-wide budget for profile / background loops. */
        val Profile = VideoBudget(4)
    }
}
