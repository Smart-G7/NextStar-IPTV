package com.custom.iptvtv.data

import java.security.MessageDigest

enum class MediaType { LIVE, MOVIE, SERIES, RADIO }

data class MediaItem(
    val id: String,
    val title: String,
    val url: String,
    val logo: String?,
    val group: String,
    val type: MediaType,
    val headers: Map<String, String> = emptyMap()
) {
    companion object {
        /** Kimlik URL'den türetilir: aynı isimli kanallar çakışmaz. */
        fun stableId(url: String): String =
            MessageDigest.getInstance("SHA-1").digest(url.toByteArray())
                .take(8).joinToString("") { "%02x".format(it) }
    }
}

data class Source(val name: String, val url: String)

enum class DnsProvider(val title: String, val description: String, val dohUrl: String?, val bootstrap: List<String>) {
    CLOUDFLARE("Cloudflare", "1.1.1.1 · hızlı", "https://cloudflare-dns.com/dns-query", listOf("1.1.1.1", "1.0.0.1")),
    GOOGLE("Google", "8.8.8.8 · alternatif", "https://dns.google/dns-query", listOf("8.8.8.8", "8.8.4.4")),
    QUAD9("Quad9", "Zararlı site engelli", "https://dns.quad9.net/dns-query", listOf("9.9.9.9", "149.112.112.112")),
    ADGUARD("AdGuard", "Reklam ve takip engelli", "https://dns.adguard-dns.com/dns-query", listOf("94.140.14.14", "94.140.15.15")),
    SYSTEM("Sistem (kapalı)", "Cihazın ağ ayarındaki DNS", null, emptyList())
}

class Catalog(
    val live: List<MediaItem>,
    val movies: List<MediaItem>,
    val series: List<MediaItem>,
    val radio: List<MediaItem>
) {
    val liveGroups: Map<String, List<MediaItem>> by lazy { live.groupBy { it.group } }
    val movieGroups: Map<String, List<MediaItem>> by lazy { movies.groupBy { it.group } }
    val seriesGroups: Map<String, List<MediaItem>> by lazy { series.groupBy { it.group } }
    val isEmpty: Boolean get() = live.isEmpty() && movies.isEmpty() && series.isEmpty() && radio.isEmpty()

    companion object {
        val EMPTY = Catalog(emptyList(), emptyList(), emptyList(), emptyList())
    }
}
