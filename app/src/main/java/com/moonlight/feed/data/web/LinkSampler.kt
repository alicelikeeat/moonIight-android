package com.moonlight.feed.data.web

import kotlin.random.Random

/** Same-site link extraction + random crawl planning (bounded depth / page budget). */
object LinkSampler {
    private val assetExt = Regex("""\.(css|js|json|xml|rss|atom|pdf|zip|rar|7z|gz|tar|exe|apk|dmg|iso|mp3|wav|flac|woff2?|ttf|otf|svg|ico|jpe?g|png|gif|webp|avif|mp4|webm|mov|m4v)(\?|$)""", RegexOption.IGNORE_CASE)
    private val skipPath = Regex("""/(login|logout|signin|signup|sign-in|sign-up|register|account|cart|checkout|password|oauth|auth|admin|wp-admin|wp-login|feed|search|share|print|cdn-cgi)(/|\?|$)""", RegexOption.IGNORE_CASE)

    fun extractLinks(html: String, base: String): List<String> {
        val seen = LinkedHashSet<String>()
        Html.tags(html, setOf("a")).forEach { t ->
            if (t.attrs["rel"]?.contains("nofollow", true) == true) return@forEach
            val u = Html.resolve(base, t.attrs["href"]) ?: return@forEach
            val noFrag = u.substringBefore('#')
            if (!UrlNorm.sameSite(noFrag, base)) return@forEach
            if (assetExt.containsMatchIn(noFrag.substringBefore('?'))) return@forEach
            if (skipPath.containsMatchIn(noFrag)) return@forEach
            if (UrlNorm.normalize(noFrag) == UrlNorm.normalize(base)) return@forEach
            seen += noFrag
        }
        return seen.toList()
    }

    /**
     * Picks up to [k] unvisited links at random (dedupe by normalized URL).
     */
    fun sample(links: List<String>, visited: Set<String>, k: Int, rnd: Random): List<String> =
        links.distinctBy { UrlNorm.normalize(it) }
            .filter { UrlNorm.normalize(it) !in visited }
            .shuffled(rnd)
            .take(k.coerceAtLeast(0))

    /** Random sample of [n] items keeping a mix (videos first chance), stable for a given seed. */
    fun <T> sampleItems(items: List<T>, n: Int, rnd: Random): List<T> = items.shuffled(rnd).take(n.coerceAtLeast(0))
}
