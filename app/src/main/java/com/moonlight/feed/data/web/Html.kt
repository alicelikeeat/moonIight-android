package com.moonlight.feed.data.web

import java.net.URI

/** Minimal, dependency-free HTML tag scanner (enough for media/link extraction). */
internal object Html {
    data class Tag(val name: String, val attrs: Map<String, String>)

    private val tagRe = Regex("""<([a-zA-Z][a-zA-Z0-9:-]*)(\s[^<>]*?)?/?>""")
    private val attrRe = Regex("""([a-zA-Z_:][-a-zA-Z0-9_:.]*)\s*(?:=\s*(?:"([^"]*)"|'([^']*)'|([^\s"'=<>`]+)))?""")
    private val commentRe = Regex("""<!--.*?-->""", RegexOption.DOT_MATCHES_ALL)
    private val scriptRe = Regex("""<(script|style|template)\b[^>]*>.*?</\1\s*>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))

    fun strip(html: String): String = scriptRe.replace(commentRe.replace(html, " "), " ")

    fun tags(html: String, names: Set<String>? = null): Sequence<Tag> =
        tagRe.findAll(html).mapNotNull { m ->
            val name = m.groupValues[1].lowercase()
            if (names != null && name !in names) return@mapNotNull null
            val attrs = HashMap<String, String>()
            m.groupValues.getOrNull(2)?.let { raw ->
                attrRe.findAll(raw).forEach { a ->
                    val k = a.groupValues[1].lowercase()
                    val v = a.groupValues[2].ifEmpty { a.groupValues[3].ifEmpty { a.groupValues[4] } }
                    if (k !in attrs) attrs[k] = decode(v)
                }
            }
            Tag(name, attrs)
        }

    fun title(html: String): String? =
        Regex("""<title[^>]*>(.*?)</title>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
            .find(html)?.groupValues?.get(1)?.let { decode(it).replace(Regex("\\s+"), " ").trim() }?.ifBlank { null }

    fun decode(s: String): String = s
        .replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'").replace("&apos;", "'")
        .replace("&lt;", "<").replace("&gt;", ">").replace("&#x2F;", "/").replace("&#47;", "/")
        .replace("&nbsp;", " ")

    /** Base href support + relative resolution; returns null for non-http(s). */
    fun resolve(base: String, ref: String?): String? {
        val r = ref?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val low = r.lowercase()
        if (low.startsWith("data:") || low.startsWith("javascript:") || low.startsWith("mailto:") ||
            low.startsWith("tel:") || low.startsWith("blob:") || low.startsWith("#")) return null
        return try {
            val u = URI(base).resolve(URI(r.replace(" ", "%20")))
            if (u.scheme?.lowercase() in setOf("http", "https") && !u.host.isNullOrBlank()) u.toString() else null
        } catch (_: Throwable) { null }
    }

    fun baseHref(html: String, pageUrl: String): String =
        tags(html, setOf("base")).firstOrNull()?.attrs?.get("href")?.let { resolve(pageUrl, it) } ?: pageUrl
}

object UrlNorm {
    private val trackingParams = Regex("""^(utm_[a-z]+|fbclid|gclid|mc_eid|mc_cid|igshid|spm|ref_src|_ga)$""", RegexOption.IGNORE_CASE)

    /** Lower-case host, drop fragment / default port / tracking params / trailing slash. */
    fun normalize(url: String): String = try {
        val u = URI(url.trim())
        val host = u.host?.lowercase()?.removePrefix("www.") ?: return url.trim()
        val q = u.rawQuery?.split('&')?.filter { p -> p.isNotBlank() && !trackingParams.matches(p.substringBefore('=')) }
            ?.sorted()?.joinToString("&")?.ifBlank { null }
        val path = (u.rawPath ?: "").ifBlank { "/" }.let { if (it.length > 1) it.trimEnd('/') else it }
        val port = if (u.port == -1 || u.port == 80 || u.port == 443) "" else ":${u.port}"
        "${u.scheme?.lowercase() ?: "https"}://$host$port$path" + (q?.let { "?$it" } ?: "")
    } catch (_: Throwable) { url.trim() }

    /** Stable content key used for library dedupe. */
    fun key(url: String): String {
        val md = java.security.MessageDigest.getInstance("SHA-1")
        return md.digest(normalize(url).toByteArray()).joinToString("") { "%02x".format(it) }.take(20)
    }

    fun host(url: String): String? = try { URI(url).host?.lowercase()?.removePrefix("www.") } catch (_: Throwable) { null }

    /** Same site: identical host, or one is a subdomain of the other (www/m variants). */
    fun sameSite(a: String, b: String): Boolean {
        val ha = host(a) ?: return false
        val hb = host(b) ?: return false
        return ha == hb || ha.endsWith(".$hb") || hb.endsWith(".$ha")
    }
}
