package app.lull.core

/** Display helpers for lists where every title starts the same way (lecture series, podcasts). */
object Titles {

    private val VIEWS = Regex("""^[\d.,]+[\s\u00A0]*[KMB]?[\s\u00A0]+(views?|plays?|listens?)$""", RegexOption.IGNORE_CASE)
    private const val MIN_PREFIX = 8
    private const val SEPARATORS = "(-–—:|•·,["
    private const val EDGE = " \t-–—:|•·,.(["

    /** True for "74 views", "1.2K plays" and the like, which are never an artist or show name. */
    fun isCount(text: String): Boolean = VIEWS.matches(text.trim())

    /**
     * When all titles share a long common start ("Shama'il of al-Muhammadiyyah (Part 1)",
     * "… (Part 2)"), returns only the part that tells them apart ("Part 1", "Part 2").
     * Otherwise returns the titles unchanged. Never returns a blank title.
     */
    fun distinct(titles: List<String>): List<String> {
        if (titles.size < 2) return titles
        var prefix = titles.reduce { a, b -> a.commonPrefixWith(b, ignoreCase = true) }
        // Cut back to the last separator, so "(Part 1)" keeps its "Part" and words stay whole.
        // A hyphen counts only with a space before it, so "al-Muhammadiyyah" isn't split.
        val boundary = prefix.indices.lastOrNull { i ->
            val c = prefix[i]
            c in SEPARATORS && (c != '-' || (i > 0 && prefix[i - 1].isWhitespace()))
        } ?: -1
        prefix = if (boundary >= 0) prefix.substring(0, boundary + 1) else ""
        if (prefix.trim().length < MIN_PREFIX) return titles

        val rest = titles.map { clean(it.substring(prefix.length)) }
        return if (rest.any { it.isBlank() }) titles else rest
    }

    private fun clean(s: String): String {
        var t = s.trim { it in EDGE }
        if (t.endsWith(")") && !t.contains("(")) t = t.dropLast(1).trimEnd()
        if (t.endsWith("]") && !t.contains("[")) t = t.dropLast(1).trimEnd()
        return t
    }
}
