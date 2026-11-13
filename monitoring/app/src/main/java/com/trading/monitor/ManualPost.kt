package com.trading.monitor

/** A link to a post on X: the author's handle as written, the post id and its creation time. */
data class XLink(val handle: String, val postId: String, val timeMs: Long)

private const val SNOWFLAKE_EPOCH_MS = 1288834974657L

private val X_LINK = Regex(
    // With a scheme the link may follow any character; without one it must not continue a longer name (notx.com).
    "(?:https?://|(?<![A-Za-z0-9_.-]))(?:(?:www|mobile|m)\\.)?(?:x|twitter)\\.com/" +
        "([A-Za-z0-9_]{1,15})/status/(\\d+)(?:[/?#]\\S*)?",
    RegexOption.IGNORE_CASE,
)

private val MENTION = Regex("(?<![A-Za-z0-9_])@([A-Za-z0-9_]{1,15})")

private val EXTRA_NEWLINES = Regex("(?:\\r?\\n){3,}")

/** True for a link whose first path segment is the reserved "i" (x.com/i/status/...), which has no handle. */
private fun MatchResult.isReservedPath() = groupValues[1].equals("i", ignoreCase = true)

/** Finds the first x.com / twitter.com post link in [text]; null if none or its id carries no time. */
fun findXLink(text: String): XLink? {
    val m = X_LINK.findAll(text).firstOrNull { !it.isReservedPath() } ?: return null
    val id = m.groupValues[2]
    val time = timeFromPostId(id) ?: return null
    return XLink(m.groupValues[1], id, time)
}

/** Creation time in epoch ms of an X snowflake post id; null if [id] is not a positive number or predates snowflakes. */
fun timeFromPostId(id: String): Long? {
    if (id.isEmpty() || !id.all { it in '0'..'9' }) return null
    val n = id.toLongOrNull() ?: return null
    if (n <= 0) return null
    val ts = n shr 22
    if (ts == 0L) return null
    return ts + SNOWFLAKE_EPOCH_MS
}

/** Removes every x.com / twitter.com post link from [text], trims, and collapses 3+ newlines to two. */
fun cleanSharedText(text: String): String =
    X_LINK.replace(text, "").trim().replace(EXTRA_NEWLINES, "\n\n")

/** Picks the author handle from @mentions: the first one in [known] (ignoring case), else the first mention lowercased. */
fun guessHandle(text: String, known: List<String>): String? {
    val mentions = MENTION.findAll(text).map { it.groupValues[1] }.toList()
    for (mention in mentions) {
        val hit = known.firstOrNull { it.equals(mention, ignoreCase = true) }
        if (hit != null) return hit
    }
    return mentions.firstOrNull()?.lowercase()
}

/** Id for a post added by hand, built from the current time. */
fun manualPostId(nowMs: Long): String = "m-$nowMs"

/** True if [id] was produced by [manualPostId]. */
fun isManualId(id: String): Boolean = id.startsWith("m-")

/** Text of a post after more text is added to it: unchanged if [added] is blank or already contained, else joined by a blank line. */
fun mergePostText(existing: String, added: String): String {
    val e = existing.trim()
    val a = added.trim()
    return when {
        a.isEmpty() -> e
        e.isEmpty() -> a
        e.contains(a) -> e
        else -> e + "\n\n" + a
    }
}
