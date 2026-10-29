package com.custom.iptvtv.data

import android.content.Context
import com.custom.iptvtv.net.Net
import okhttp3.Request
import org.json.JSONArray
import java.io.File

class Repository(context: Context, private val prefs: Prefs) {

    companion object {
        private const val MAX_BYTES = 150L * 1024 * 1024
        private const val STALE_MS = 6L * 3600 * 1000
        private const val RADIO_STALE_MS = 24L * 3600 * 1000
        private val GITHUB_BLOB = Regex("^https://github\\.com/([^/]+)/([^/]+)/blob/(.+)$")
        // Tam kelime eşleşmesi: "Contest", "Latest", "Industrial" yanlışlıkla elenmez
        private val BANNED = Regex("\\b(test|adult|xxx|vod test|trial)\\b", RegexOption.IGNORE_CASE)
        private val RADIO_HOSTS = listOf("de1.api.radio-browser.info", "nl1.api.radio-browser.info", "at1.api.radio-browser.info")
    }

    private val dir = File(context.filesDir, "lists").apply { mkdirs() }
    private val radioFile = File(context.filesDir, "radio_tr.json")

    fun cacheFile(src: Source) = File(dir, "${MediaItem.stableId(src.url)}.lst")
    fun isStale(src: Source): Boolean {
        val f = cacheFile(src)
        return !f.exists() || System.currentTimeMillis() - f.lastModified() > STALE_MS
    }

    fun clearAll() {
        dir.listFiles()?.forEach { it.delete() }
        radioFile.delete()
    }

    private fun normalize(url: String): String =
        GITHUB_BLOB.matchEntire(url)?.let {
            "https://raw.githubusercontent.com/${it.groupValues[1]}/${it.groupValues[2]}/${it.groupValues[3]}"
        } ?: url

    /** Listeyi diske akıtır (RAM'e almaz). Başarısız olursa eski önbellek korunur. */
    fun download(src: Source): Result<Unit> = runCatching {
        val req = Request.Builder().url(normalize(src.url)).header("User-Agent", Net.UA).build()
        Net.client(prefs.dns).newCall(req).execute().use { resp ->
            check(resp.isSuccessful) { "HTTP ${resp.code}" }
            val body = checkNotNull(resp.body) { "Boş yanıt" }
            val tmp = File(dir, "tmp_${System.nanoTime()}")
            try {
                tmp.outputStream().use { out ->
                    body.byteStream().use { input ->
                        val buf = ByteArray(64 * 1024)
                        var total = 0L
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            total += n
                            check(total <= MAX_BYTES) { "Liste çok büyük" }
                            out.write(buf, 0, n)
                        }
                    }
                }
                check(tmp.length() > 0) { "Boş liste" }
                val head = tmp.inputStream().use { s ->
                    val b = ByteArray(256)
                    val n = s.read(b)
                    if (n > 0) String(b, 0, n) else ""
                }
                check(!head.trimStart('\uFEFF', ' ', '\n', '\r', '\t').startsWith("<")) { "Liste yerine web sayfası döndü" }
                val dest = cacheFile(src)
                if (!tmp.renameTo(dest)) tmp.copyTo(dest, overwrite = true)
            } finally {
                tmp.delete()
            }
        }
    }

    /** Önbellekteki listelerden kataloğu kurar; gizli/çöp kanallar daha ayrıştırma sırasında elenir (RAM tasarrufu). */
    fun buildCatalog(sources: List<Source>): Catalog {
        val hidden = prefs.hidden
        val seen = HashSet<String>()
        val live = ArrayList<MediaItem>()
        val movies = ArrayList<MediaItem>()
        val series = ArrayList<MediaItem>()
        val radio = ArrayList<MediaItem>()
        for (s in sources) {
            val f = cacheFile(s)
            if (!f.exists()) continue
            f.bufferedReader(Charsets.UTF_8).use { r ->
                PlaylistParser.parse(r) { item ->
                    if (item.id in hidden || BANNED.containsMatchIn(item.group) || !seen.add(item.id)) return@parse
                    when (item.type) {
                        MediaType.LIVE -> live.add(item)
                        MediaType.MOVIE -> movies.add(item)
                        MediaType.SERIES -> series.add(item)
                        MediaType.RADIO -> radio.add(item)
                    }
                }
            }
        }
        live.trimToSize(); movies.trimToSize(); series.trimToSize(); radio.trimToSize()
        return Catalog(live, movies, series, radio)
    }

    // ---- Türkçe radyolar (radio-browser.info, açık veri tabanı) ----
    fun loadRadios(force: Boolean): List<MediaItem> {
        val fresh = radioFile.exists() && System.currentTimeMillis() - radioFile.lastModified() < RADIO_STALE_MS
        if (fresh && !force) return parseRadios(radioFile.readText())
        for (host in RADIO_HOSTS) {
            val ok = runCatching {
                val url = "https://$host/json/stations/bycountrycodeexact/TR?hidebroken=true&order=clickcount&reverse=true&limit=300"
                val req = Request.Builder().url(url).header("User-Agent", "NextStarIPTV/2.0").build()
                Net.client(prefs.dns).newCall(req).execute().use { r ->
                    check(r.isSuccessful)
                    val text = checkNotNull(r.body).string()
                    check(text.startsWith("["))
                    radioFile.writeText(text)
                }
            }.isSuccess
            if (ok) break
        }
        return if (radioFile.exists()) parseRadios(radioFile.readText()) else emptyList()
    }

    private fun parseRadios(text: String): List<MediaItem> = runCatching {
        val arr = JSONArray(text)
        val names = HashSet<String>()
        val out = ArrayList<MediaItem>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val name = o.optString("name").trim()
            val url = o.optString("url_resolved").ifEmpty { o.optString("url") }.trim()
            if (name.isEmpty() || !url.startsWith("http", true) || !names.add(name.lowercase())) continue
            val genre = o.optString("tags").split(',').firstOrNull { it.isNotBlank() }?.trim()
                ?.replaceFirstChar { it.uppercase() } ?: "Radyo"
            out.add(MediaItem(MediaItem.stableId(url), name, url, o.optString("favicon").ifEmpty { null }, genre, MediaType.RADIO))
        }
        out
    }.getOrDefault(emptyList())
}
