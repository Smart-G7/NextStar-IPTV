package com.custom.iptvtv.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader

/** Akış (stream) tabanlı ayrıştırıcı: dosyanın tamamı RAM'e alınmaz. Kabul kararını çağıran verir. */
object PlaylistParser {
    private const val MAX_ITEMS = 150_000
    private val LOGO_RE = Regex("tvg-logo=\"([^\"]*)\"", RegexOption.IGNORE_CASE)
    private val GROUP_RE = Regex("group-title=\"([^\"]*)\"", RegexOption.IGNORE_CASE)

    fun parse(reader: BufferedReader, onItem: (MediaItem) -> Unit) {
        // İlk anlamlı karakter '[' veya '{' ise JSON, değilse M3U (BOM ve boşluklar atlanır)
        reader.mark(1)
        var c = reader.read()
        while (c != -1 && (c.toChar().isWhitespace() || c == 0xFEFF)) { reader.mark(1); c = reader.read() }
        if (c == -1) return
        reader.reset()
        if (c == '['.code || c == '{'.code) parseJson(reader.readText(), onItem) else parseM3u(reader, onItem)
    }

    private fun parseM3u(reader: BufferedReader, onItem: (MediaItem) -> Unit) {
        // Aynı grup/logo dizgisini binlerce kanal paylaşır: RAM'de yüzlerce MB yerine birkaç MB
        val pool = HashMap<String, String>()
        fun pooled(v: String): String = pool.getOrPut(v) { v }
        var count = 0
        var title = ""
        var logo: String? = null
        var group = "Genel"
        val headers = HashMap<String, String>()

        for (raw in reader.lineSequence()) {
            val line = raw.trim()
            when {
                line.startsWith("#EXTINF:", true) -> {
                    title = extractTitle(line)
                    logo = LOGO_RE.find(line)?.groupValues?.get(1)?.trim()?.ifEmpty { null }?.let { pooled(it) }
                    group = pooled(GROUP_RE.find(line)?.groupValues?.get(1)?.trim()?.ifEmpty { null } ?: "Genel")
                }
                line.startsWith("#EXTGRP:", true) -> if (group == "Genel") group = line.substringAfter(':').trim().ifEmpty { "Genel" }
                line.startsWith("#EXTVLCOPT:", true) -> {
                    val kv = line.substringAfter(':')
                    when {
                        kv.startsWith("http-user-agent=", true) -> headers["User-Agent"] = kv.substringAfter('=')
                        kv.startsWith("http-referrer=", true) -> headers["Referer"] = kv.substringAfter('=')
                    }
                }
                line.isEmpty() || line.startsWith("#") -> Unit
                else -> {
                    if (line.startsWith("http://", true) || line.startsWith("https://", true)) {
                        onItem(
                            MediaItem(
                                id = MediaItem.stableId(line),
                                title = title.ifEmpty { "Adsız" },
                                url = line,
                                logo = logo,
                                group = group,
                                type = detectType(group, line),
                                headers = if (headers.isEmpty()) emptyMap() else HashMap(headers)
                            )
                        )
                        if (++count >= MAX_ITEMS) return
                    }
                    title = ""; logo = null; group = "Genel"; headers.clear() // bir kanalın bilgisi sonrakine sızmasın
                }
            }
        }
    }

    /** Tırnak dışındaki ilk virgülden sonrası (virgüllü kanal adları bozulmaz). */
    private fun extractTitle(line: String): String {
        var inQuotes = false
        for (i in line.indices) {
            val ch = line[i]
            if (ch == '"') inQuotes = !inQuotes
            else if (ch == ',' && !inQuotes) return line.substring(i + 1).trim()
        }
        return ""
    }

    private fun parseJson(text: String, onItem: (MediaItem) -> Unit) {
        val t = text.trim().removePrefix("\uFEFF")
        val arr = if (t.startsWith("[")) JSONArray(t)
        else JSONObject(t).let { it.optJSONArray("channels") ?: it.optJSONArray("items") ?: JSONArray() }
        for (i in 0 until minOf(arr.length(), MAX_ITEMS)) {
            val o = arr.optJSONObject(i) ?: continue
            val url = o.optString("url").ifEmpty { o.optString("stream") }.trim()
            if (!url.startsWith("http://", true) && !url.startsWith("https://", true)) continue
            val group = o.optString("group").ifEmpty { o.optString("category") }.ifEmpty { "Genel" }
            onItem(
                MediaItem(
                    id = MediaItem.stableId(url),
                    title = o.optString("name").ifEmpty { o.optString("title") }.ifEmpty { "Adsız" },
                    url = url,
                    logo = o.optString("logo").ifEmpty { null },
                    group = group,
                    type = detectType(group, url)
                )
            )
        }
    }

    private fun detectType(group: String, url: String): MediaType {
        val g = group.lowercase()
        val u = url.lowercase()
        return when {
            g.contains("radyo") || g.contains("radio") || u.contains("icecast") -> MediaType.RADIO
            g.contains("film") || g.contains("movie") || g.contains("sinema") || u.contains("/movie/") -> MediaType.MOVIE
            g.contains("dizi") || g.contains("series") || g.contains("sezon") || u.contains("/series/") -> MediaType.SERIES
            else -> MediaType.LIVE
        }
    }
}
