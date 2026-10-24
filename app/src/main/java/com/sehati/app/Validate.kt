package com.sehati.app

import android.net.Uri

/** رابط خرائط جوجل فقط، ويجب أن يكون https. */
fun isMapsLink(s: String): Boolean {
    val t = s.trim()
    if (!t.startsWith("https://")) return false
    val host = Uri.parse(t).host?.lowercase() ?: return false
    if (host == "maps.app.goo.gl") return true
    val googleHost = host == "google.com" || host.endsWith(".google.com") || host == "goo.gl"
    return googleHost && t.contains("/maps")
}

/** رقم هاتف: من 7 إلى 15 رقماً، ويقبل + والمسافات. */
fun isPhone(s: String): Boolean {
    val t = s.trim()
    if (!Regex("^\\+?[0-9 ]+$").matches(t)) return false
    return t.count { it.isDigit() } in 7..15
}
