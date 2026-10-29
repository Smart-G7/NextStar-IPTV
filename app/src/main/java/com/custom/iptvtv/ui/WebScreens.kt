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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Public
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
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
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

    Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Tarayıcı", style = MaterialTheme.typography.titleLarge, color = Pal.Text)
        Text(
            "Reklam engelleyicili tarayıcı. Bir sayfada video akışı (m3u8/mp4) bulunduğu anda tarayıcı kapanır ve oynatıcı açılır; böylece WebView belleği anında boşalır.",
            color = Pal.Muted
        )
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AppTextField(url, { url = it }, "Adres veya arama yaz (örn. example.com)", Modifier.weight(1f), KeyboardType.Uri, onDone = { state.openWeb(url) })
            ActionButton("Git", { state.openWeb(url) }, Modifier.focusRequester(go), Icons.Filled.Public, primary = true)
        }
        if (state.history.isNotEmpty()) {
            SectionTitle("Son açılanlar")
            state.history.forEach { h ->
                FocusBox(onClick = { state.openWeb(h) }, modifier = Modifier.fillMaxWidth(), scaleOnFocus = 1f) {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Filled.Public, null, tint = Pal.Muted, modifier = Modifier.size(22.dp))
                        Text(h, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Pal.Text)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebScreen(state: AppState, startUrl: String) {
    var webView by remember { mutableStateOf<WebView?>(null) }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val adBlock = remember { state.prefs.adBlock }

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
                                val headers = HashMap<String, String>() // Range/Accept-Encoding gibi ham başlıklar ExoPlayer'ı bozar; yalnızca gerekenler
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
    }
}

private fun isVideoPath(uri: Uri): Boolean {
    val p = uri.path?.lowercase() ?: return false // sorgu dizgisi (?token=) artık sorun değil
    return p.endsWith(".m3u8") || p.endsWith(".mp4") || p.endsWith(".mpd")
}
