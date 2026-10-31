package com.moonlight.feed.data.web

/** Small robots.txt evaluator: groups for our agent or '*', Allow/Disallow longest-match, '*' and '$' wildcards, Crawl-delay. */
class RobotsTxt private constructor(private val rules: List<Pair<Boolean, Regex>>, private val lens: List<Int>, val crawlDelaySec: Double?) {
    fun isAllowed(pathAndQuery: String): Boolean {
        val p = pathAndQuery.ifBlank { "/" }
        var best = -1; var allow = true
        rules.forEachIndexed { i, (isAllow, re) ->
            if (re.containsMatchIn(p) && re.find(p)?.range?.first == 0) {
                val l = lens[i]
                if (l > best || (l == best && isAllow)) { best = l; allow = isAllow }
            }
        }
        return allow
    }

    companion object {
        val ALLOW_ALL = RobotsTxt(emptyList(), emptyList(), null)

        fun parse(text: String, agent: String = "moonlight"): RobotsTxt {
            data class Group(val agents: MutableList<String> = mutableListOf(), val rules: MutableList<Pair<Boolean, String>> = mutableListOf(), var delay: Double? = null)
            val groups = mutableListOf<Group>()
            var cur: Group? = null
            var lastWasAgent = false
            text.lineSequence().forEach { raw ->
                val line = raw.substringBefore('#').trim()
                if (line.isEmpty()) return@forEach
                val k = line.substringBefore(':').trim().lowercase()
                val v = line.substringAfter(':', "").trim()
                when (k) {
                    "user-agent" -> {
                        if (cur == null || !lastWasAgent) { cur = Group(); groups += cur!! }
                        cur!!.agents += v.lowercase(); lastWasAgent = true
                    }
                    "allow", "disallow" -> { cur?.let { if (v.isNotEmpty() || k == "allow") it.rules += (k == "allow") to v }; lastWasAgent = false }
                    "crawl-delay" -> { cur?.delay = v.toDoubleOrNull(); lastWasAgent = false }
                    else -> lastWasAgent = false
                }
            }
            val mine = groups.filter { g -> g.agents.any { it != "*" && agent.lowercase().contains(it) } }
            val chosen = mine.ifEmpty { groups.filter { "*" in it.agents } }
            val rs = chosen.flatMap { it.rules }.filter { it.second.isNotEmpty() }
            return RobotsTxt(
                rs.map { (a, pat) -> a to toRegex(pat) },
                rs.map { it.second.length },
                chosen.firstNotNullOfOrNull { it.delay },
            )
        }

        private fun toRegex(pattern: String): Regex {
            val sb = StringBuilder("^")
            pattern.forEachIndexed { i, ch ->
                when {
                    ch == '*' -> sb.append(".*")
                    ch == '$' && i == pattern.lastIndex -> sb.append("$")
                    else -> sb.append(Regex.escape(ch.toString()))
                }
            }
            return Regex(sb.toString())
        }
    }
}
