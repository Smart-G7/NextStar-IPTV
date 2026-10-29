package com.custom.iptvtv.data

import java.net.URLEncoder

/** Xtream Codes bilgilerinden standart M3U adresi üretir. */
object Xtream {
    fun buildUrl(host: String, user: String, pass: String, output: String = "ts"): String? {
        var h = host.trim().trimEnd('/')
        if (h.isEmpty() || user.isBlank() || pass.isBlank()) return null
        if (!h.startsWith("http://", true) && !h.startsWith("https://", true)) h = "http://$h"
        // Panel adresi /get.php veya /player_api.php ile yapıştırılırsa kök adrese indir
        h = h.substringBefore("/get.php").substringBefore("/player_api.php").trimEnd('/')
        val out = if (output.equals("m3u8", true)) "m3u8" else "ts"
        return "$h/get.php?username=${enc(user.trim())}&password=${enc(pass.trim())}&type=m3u_plus&output=$out"
    }

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")
}
