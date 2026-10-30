package com.custom.iptvtv.net

object AdBlocker {
    @Volatile
    var blockedHosts: Set<String> = setOf(
        "doubleclick.net", "googlesyndication.com", "googleadservices.com", "adservice.google.com",
        "popads.net", "popcash.net", "adsterra.com", "propellerads.com", "exoclick.com", "juicyads.com",
        "trafficjunky.com", "adnxs.com", "taboola.com", "outbrain.com", "revcontent.com",
        "onclickads.net", "clickadu.com", "hilltopads.net"
    )

    /** Alan adı sonekiyle eşleşir (a.b.doubleclick.net dahil). */
    fun isBlocked(host: String?): Boolean {
        var h = host?.lowercase() ?: return false
        while (true) {
            if (h in blockedHosts) return true
            val dot = h.indexOf('.')
            if (dot < 0) return false
            h = h.substring(dot + 1)
        }
    }
}
