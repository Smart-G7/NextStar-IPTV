package com.custom.iptvtv.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.custom.iptvtv.AppState
import com.custom.iptvtv.Overlay
import com.custom.iptvtv.data.MediaItem
import com.custom.iptvtv.data.MediaType
import com.custom.iptvtv.net.AdBlocker
import kotlinx.coroutines.delay
import java.io.ByteArrayInputStream
import java.util.Collections

@Composable
fun BrowserHomeScreen(state: AppState) {
    var url by remember { mutableStateOf("") }
    val go = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(80); runCatching { go.requestFocus() } }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Text("Tarayıcı", style = MaterialTheme.typography.titleLarge, color = Pal.Text) }
        item {
            Text(
                "Reklam engelleyicili tarayıcı. Sayfada video akışı (m3u8/mp4) bulunduğu anda oynatıcı açılır. Sayfadayken kumandadaki Menü tuşu o sayfayı favorilere ekler.",
                color = Pal.Muted
            )
        }
        state.status?.let { msg -> item { Text(msg, color = Pal.Accent) } }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AppTextField(url, { url = it }, "Adres veya arama yaz (örn. example.com)", Modifier.weight(1f), KeyboardType.Uri, onDone = { state.openWeb(url) })
                ActionButton("Git", { state.openWeb(url) }, Modifier.focusRequester(go), Icons.Filled.Public, primary = true)
                ActionButton("Favori ekle", { state.normalizeUrl(url)?.let { state.addWebFav("", it) } }, icon = Icons.Filled.Star)
            }
        }

        item { SectionTitle("Favoriler", Modifier.padding(top = 10.dp)) }
        if (state.webFavs.isEmpty()) {
            item { Text("Henüz favori yok. Adres yazıp \"Favori ekle\" de ya da son açılanlardan yıldıza bas.", color = Pal.Muted) }
        } else {
            items(state.webFavs, key = { "f:" + it.url }) { fav ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FocusBox(onClick = { state.openWeb(fav.url) }, modifier = Modifier.weight(1f), scaleOnFocus = 1f) {
                        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(Icons.Filled.Star, null, tint = Pal.Accent, modifier = Modifier.size(22.dp))
                            Column(Modifier.weight(1f)) {
                                Text(fav.name, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Pal.Text)
                                Text(fav.url, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Pal.Muted, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    ActionButton("Sil", { state.removeWebFav(fav.url) }, icon = Icons.Filled.Delete)
                }
            }
        }

        if (state.history.isNotEmpty()) {
            item { SectionTitle("Son açılanlar", Modifier.padding(top = 10.dp)) }
            items(state.history, key = { "h:" + it }) { h ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FocusBox(onClick = { state.openWeb(h) }, modifier = Modifier.weight(1f), scaleOnFocus = 1f) {
                        Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(Icons.Filled.Public, null, tint = Pal.Muted, modifier = Modifier.size(22.dp))
                            Text(h, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Pal.Text)
                        }
                    }
                    ActionButton("Favori", { state.addWebFav("", h) }, icon = Icons.Filled.Star)
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebScreen(state: AppState, startUrl: String) {
    var webView by remember { mutableStateOf<WebView?>(null) }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val adBlock = remember { state.prefs.adBlock }
    var toast by remember { mutableStateOf<String?>(null) }
    val startTick = remember { state.webFavTick }

    // MainActivity Menü/Yer imi tuşunu yakalayıp sayaç artırır
    LaunchedEffect(state.webFavTick) {
        if (state.webFavTick > startTick) {
            val wv = webView
            val u = wv?.url
            if (wv != null && u != null && u != "about:blank") {
                val added = state.addWebFav(wv.title ?: "", u)
                toast = if (added) "★ Favorilere eklendi" else "Bu sayfa zaten favorilerde"
            }
        }
    }
    LaunchedEffect(toast) { if (toast != null) { delay(2000); toast = null } }

    BackHandler {
        val wv = webView
        if (wv != null && wv.canGoBack()) wv.goBack() else state.overlay = null
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { context ->
                WebView(context).apply {
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        allowFileAccess = false
                        allowContentAccess = false
                        mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                        javaScriptCanOpenWindowsAutomatically = false // pop-up engeli
                        setSupportMultipleWindows(false)
                        safeBrowsingEnabled = true
                    }
                    val ua = settings.userAgentString
                    val seen = Collections.synchronizedSet(HashSet<String>())
                    val pageUrl = java.util.concurrent.atomic.AtomicReference("")

                    webViewClient = object : WebViewClient() {
                        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                            pageUrl.set(url)
                            seen.clear()
                        }

                        // intent://, market:// gibi şemaları ve reklam alan adlarını engelle
                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                            val scheme = request.url.scheme?.lowercase()
                            return (scheme != "http" && scheme != "https") || (adBlock && AdBlocker.isBlocked(request.url.host))
                        }

                        // WebView'in arka plan thread'inde çalışır
                        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                            val uri = request.url
                            if (adBlock && AdBlocker.isBlocked(uri.host)) {
                                return WebResourceResponse("text/plain", "utf-8", 204, "No Content", emptyMap(), ByteArrayInputStream(ByteArray(0)))
                            }
                            if (isVideoPath(uri) && seen.add(uri.toString())) {
                                val headers = HashMap(request.requestHeaders)
                                headers["User-Agent"] = ua
                                pageUrl.get().takeIf { it.isNotEmpty() }?.let { headers["Referer"] = it }
                                CookieManager.getInstance().getCookie(uri.toString())?.let { headers["Cookie"] = it }
                                val item = MediaItem(
                                    id = MediaItem.stableId(uri.toString()),
                                    title = uri.host ?: "Web videosu",
                                    url = uri.toString(),
                                    logo = null,
                                    group = "Web",
                                    type = MediaType.MOVIE,
                                    headers = headers
                                )
                                // Kill-and-switch: Compose durumu ana thread'den güncellenir, WebView kompozisyondan çıkınca yok edilir
                                mainHandler.post { state.overlay = Overlay.Play(listOf(item), 0) }
                            }
                            return super.shouldInterceptRequest(view, request)
                        }

                        override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                            mainHandler.post { state.overlay = null } // uygulama çökmesin
                            return true
                        }
                    }
                    webView = this
                    loadUrl(startUrl)
                }
            },
            onRelease = { wv ->
                webView = null
                wv.stopLoading()
                wv.loadUrl("about:blank")
                (wv.parent as? ViewGroup)?.removeView(wv)
                wv.removeAllViews()
                wv.destroy()
            },
            modifier = Modifier.fillMaxSize()
        )
        toast?.let {
            Text(
                it,
                Modifier.align(Alignment.TopCenter).padding(top = 40.dp).clip(RoundedCornerShape(50))
                    .background(Color(0xDD000000)).padding(horizontal = 22.dp, vertical = 10.dp),
                color = Color.White
            )
        }
    }
}

private fun isVideoPath(uri: Uri): Boolean {
    val p = uri.path?.lowercase() ?: return false // sorgu dizgisi (?token=) artık sorun değil
    return p.endsWith(".m3u8") || p.endsWith(".mp4") || p.endsWith(".mpd")
}
