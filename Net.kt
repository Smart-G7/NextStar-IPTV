package com.custom.iptvtv.net

import com.custom.iptvtv.data.DnsProvider
import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.dnsoverhttps.DnsOverHttps
import java.net.InetAddress
import java.util.concurrent.TimeUnit

object Net {
    const val UA = "Mozilla/5.0 (Linux; Android 12; Android TV) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36"

    @Volatile
    private var cached: Pair<DnsProvider, OkHttpClient>? = null

    private val bootstrap by lazy {
        OkHttpClient.Builder().connectTimeout(5, TimeUnit.SECONDS).readTimeout(5, TimeUnit.SECONDS).build()
    }

    /** Sağlayıcı değişmedikçe aynı istemci (ve bağlantı havuzu) kullanılır. */
    fun client(provider: DnsProvider): OkHttpClient {
        cached?.let { if (it.first == provider) return it.second }
        return synchronized(this) {
            cached?.takeIf { it.first == provider }?.second ?: build(provider).also { cached = provider to it }
        }
    }

    private fun build(provider: DnsProvider): OkHttpClient {
        val dns: Dns = provider.dohUrl?.let { FallbackDns(doh(it, provider.bootstrap)) } ?: Dns.SYSTEM
        return OkHttpClient.Builder()
            .dns(dns)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    private fun doh(url: String, bootstrapIps: List<String>): Dns =
        DnsOverHttps.Builder()
            .client(bootstrap)
            .url(url.toHttpUrl())
            .bootstrapDnsHosts(bootstrapIps.map { InetAddress.getByName(it) })
            .build()

    /** Önce DoH (ISS DNS zehirlemesi hata vermez, sahte IP döner), olmazsa sistem DNS'i (yerel ağ adları için de gerekli). */
    private class FallbackDns(private val doh: Dns) : Dns {
        override fun lookup(hostname: String): List<InetAddress> =
            try {
                doh.lookup(hostname).ifEmpty { Dns.SYSTEM.lookup(hostname) }
            } catch (e: Exception) {
                Dns.SYSTEM.lookup(hostname)
            }
    }
}
