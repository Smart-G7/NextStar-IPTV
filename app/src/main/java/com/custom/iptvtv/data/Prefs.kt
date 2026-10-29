package com.custom.iptvtv.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("iptv_prefs", Context.MODE_PRIVATE)

    var hardwareDecoding: Boolean
        get() = sp.getBoolean("hw", true)
        set(v) { sp.edit().putBoolean("hw", v).apply() }

    var adBlock: Boolean
        get() = sp.getBoolean("adblock", true)
        set(v) { sp.edit().putBoolean("adblock", v).apply() }

    var haEnabled: Boolean
        get() = sp.getBoolean("ha_on", false)
        set(v) { sp.edit().putBoolean("ha_on", v).apply() }

    var haUrl: String
        get() = sp.getString("ha_url", "") ?: ""
        set(v) { sp.edit().putString("ha_url", v.trim()).apply() }

    var haWebhook: String
        get() = sp.getString("ha_hook", "") ?: ""
        set(v) { sp.edit().putString("ha_hook", v.trim()).apply() }

    var dns: DnsProvider
        get() = runCatching { DnsProvider.valueOf(sp.getString("dns", "") ?: "") }.getOrDefault(DnsProvider.CLOUDFLARE)
        set(v) { sp.edit().putString("dns", v.name).apply() }

    var hidden: Set<String>
        get() = HashSet(sp.getStringSet("hidden", emptySet()) ?: emptySet())
        set(v) { sp.edit().putStringSet("hidden", HashSet(v)).apply() }

    var favorites: Set<String>
        get() = HashSet(sp.getStringSet("favs", emptySet()) ?: emptySet())
        set(v) { sp.edit().putStringSet("favs", HashSet(v)).apply() }

    var sources: List<Source>
        get() = readArray("sources") { Source(it.optString("name"), it.optString("url")) }
        set(v) = writeArray("sources", v) { JSONObject().put("name", it.name).put("url", it.url) }

    var recents: List<MediaItem>
        get() = readArray("recents") { o ->
            val headers = HashMap<String, String>()
            o.optJSONObject("headers")?.let { h -> h.keys().forEach { k -> headers[k] = h.optString(k) } }
            MediaItem(
                id = o.optString("id"),
                title = o.optString("title"),
                url = o.optString("url"),
                logo = o.optString("logo").ifEmpty { null },
                group = o.optString("group"),
                type = runCatching { MediaType.valueOf(o.optString("type")) }.getOrDefault(MediaType.LIVE),
                headers = headers
            )
        }
        set(v) = writeArray("recents", v) {
            JSONObject().put("id", it.id).put("title", it.title).put("url", it.url).put("logo", it.logo ?: "")
                .put("group", it.group).put("type", it.type.name).put("headers", JSONObject(it.headers))
        }

    var history: List<String>
        get() = runCatching {
            val a = JSONArray(sp.getString("history", "[]"))
            (0 until a.length()).map { a.optString(it) }.filter { it.isNotEmpty() }
        }.getOrDefault(emptyList())
        set(v) { sp.edit().putString("history", JSONArray(v).toString()).apply() }

    private fun <T> readArray(key: String, map: (JSONObject) -> T?): List<T> = runCatching {
        val arr = JSONArray(sp.getString(key, "[]"))
        (0 until arr.length()).mapNotNull { i -> arr.optJSONObject(i)?.let(map) }
    }.getOrDefault(emptyList())

    private fun <T> writeArray(key: String, list: List<T>, toJson: (T) -> JSONObject) {
        val arr = JSONArray()
        list.forEach { arr.put(toJson(it)) }
        sp.edit().putString(key, arr.toString()).apply()
    }
}
