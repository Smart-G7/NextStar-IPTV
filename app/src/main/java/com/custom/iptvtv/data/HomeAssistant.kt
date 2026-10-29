package com.custom.iptvtv.data

import android.util.Log
import com.custom.iptvtv.net.Net
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object HomeAssistant {
    private const val TAG = "HomeAssistant"

    /** state: "playing" | "paused" | "idle". Başlık JSONObject ile kodlanır (enjeksiyon yok). */
    suspend fun report(haUrl: String, webhookId: String, title: String, state: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val base = haUrl.trim().trimEnd('/').toHttpUrlOrNull() ?: return@withContext false
                val url = base.newBuilder().addPathSegments("api/webhook").addPathSegment(webhookId).build()
                val json = JSONObject().put("event", "media_update").put("title", title)
                    .put("state", state).put("device", "NextStar-4K-MiniLED").toString()
                val client = Net.client(DnsProvider.SYSTEM).newBuilder()
                    .callTimeout(6, TimeUnit.SECONDS).build()
                val req = Request.Builder().url(url).post(json.toRequestBody("application/json".toMediaType())).build()
                client.newCall(req).execute().use { it.isSuccessful }
            } catch (e: Exception) {
                Log.e(TAG, "HA hatası: ${e.javaClass.simpleName}") // webhook gizli: URL/mesaj loglanmaz
                false
            }
        }
}
