package com.umutk.pulse.extras.keyboard

/** Takes the trackers out of a copied link (a setting, on by default): the marketing and click ids that tell a site where the link came from. */
object KbLinks {
    private val TRACKERS = Regex("^(utm_[a-z_]+|fbclid|gclid|gclsrc|dclid|msclkid|yclid|mc_eid|mc_cid|igshid|igsh|si|ref_src|ref_url|_hsenc|_hsmi|mkt_tok|vero_id|oly_enc_id|oly_anon_id|wickedid|twclid|ttclid|li_fat_id|s_cid|spm|scm|share_id|feature)$", RegexOption.IGNORE_CASE)
    private val LINK = Regex("^https?://\\S+$")

    /** [text] with the trackers taken out of it when it is one link; anything else comes back as it is. */
    fun clean(text: String): String {
        if (!KeyboardPrefs.cleanLinks) return text
        val t = text.trim()
        if (!LINK.matches(t)) return text
        val q = t.indexOf('?'); if (q < 0) return text
        val hashAt = t.indexOf('#', q)
        val base = t.substring(0, q)
        val query = if (hashAt > 0) t.substring(q + 1, hashAt) else t.substring(q + 1)
        val hash = if (hashAt > 0) t.substring(hashAt) else ""
        // "si" and "feature" are trackers on YouTube and Spotify links only (elsewhere they can mean something)
        val strictHost = Regex("(youtube\\.com|youtu\\.be|spotify\\.com|instagram\\.com)", RegexOption.IGNORE_CASE).containsMatchIn(base)
        val kept = query.split('&').filter { p ->
            val k = p.substringBefore('=')
            !(TRACKERS.matches(k) && (strictHost || k.lowercase() !in setOf("si", "feature", "spm", "scm")))
        }
        if (kept.size == query.split('&').size) return text
        return base + (if (kept.isEmpty()) "" else "?" + kept.joinToString("&")) + hash
    }
}
