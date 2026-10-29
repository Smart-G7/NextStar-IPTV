package com.custom.iptvtv

import android.app.Application
import android.net.Uri
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import com.custom.iptvtv.data.Catalog
import com.custom.iptvtv.data.HomeAssistant
import com.custom.iptvtv.data.MediaItem
import com.custom.iptvtv.data.Prefs
import com.custom.iptvtv.data.Repository
import com.custom.iptvtv.data.Source
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class Tab(val label: String, val icon: ImageVector) {
    HOME("Ana Sayfa", Icons.Filled.Home),
    LIVE("Canlı TV", Icons.Filled.LiveTv),
    MOVIES("Filmler", Icons.Filled.Movie),
    SERIES("Diziler", Icons.Filled.VideoLibrary),
    RADIO("Radyo", Icons.Filled.Radio),
    WEB("Tarayıcı", Icons.Filled.Public),
    SETTINGS("Ayarlar", Icons.Filled.Settings)
}

sealed interface Overlay {
    class Play(val items: List<MediaItem>, val index: Int) : Overlay
    class Web(val url: String) : Overlay
}

/** Tek merkezi durum: Compose snapshot state; yapılandırma değişiminde ve ekranlar arası geçişte korunur. */
class AppState(app: Application) {
    val prefs = Prefs(app)
    private val repo = Repository(app, prefs)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    var tab by mutableStateOf(Tab.HOME)
    var overlay by mutableStateOf<Overlay?>(null)
    val groupSel = mutableStateMapOf<String, String>()

    var catalog by mutableStateOf(Catalog.EMPTY)
    var webRadios by mutableStateOf<List<MediaItem>>(emptyList())
    var loading by mutableStateOf(false)
    var status by mutableStateOf<String?>(null)

    var sources by mutableStateOf(prefs.sources)
    var favorites by mutableStateOf(prefs.favorites)
    var hiddenCount by mutableStateOf(prefs.hidden.size)
    var recents by mutableStateOf(prefs.recents)
    var history by mutableStateOf(prefs.history)

    private var haJob: Job? = null

    /** Açılışta önce önbellekten (anında), sonra arka planda tazele. */
    fun start() {
        scope.launch {
            catalog = withContext(Dispatchers.IO) { runCatching { repo.buildCatalog(sources) }.getOrNull() } ?: catalog
            refresh(force = false)
        }
    }

    fun refresh(force: Boolean = true) {
        if (loading) return
        scope.launch {
            loading = true
            status = null
            val list = sources
            val errors = withContext(Dispatchers.IO) {
                list.mapNotNull { s ->
                    if (force || repo.isStale(s)) {
                        repo.download(s).exceptionOrNull()?.let { e -> "${s.name}: ${e.message ?: e.javaClass.simpleName}" }
                    } else null
                }
            }
            catalog = withContext(Dispatchers.IO) { runCatching { repo.buildCatalog(list) }.getOrNull() } ?: catalog
            webRadios = withContext(Dispatchers.IO) { runCatching { repo.loadRadios(force) }.getOrNull() } ?: webRadios
            status = errors.firstOrNull()
            loading = false
        }
    }

    private fun rebuild() {
        scope.launch {
            catalog = withContext(Dispatchers.IO) { runCatching { repo.buildCatalog(sources) }.getOrNull() } ?: catalog
        }
    }

    // ---- kaynaklar ----
    fun addSource(name: String, url: String): Boolean {
        val u = url.trim()
        if (!u.startsWith("http://", true) && !u.startsWith("https://", true)) {
            status = "Geçerli bir http(s) adresi gir"; return false
        }
        if (sources.any { it.url == u }) { status = "Bu kaynak zaten ekli"; return false }
        val n = name.trim().ifEmpty { Uri.parse(u).host ?: "Liste" }
        sources = sources + Source(n, u)
        prefs.sources = sources
        refresh()
        return true
    }

    fun removeSource(s: Source) {
        sources = sources - s
        prefs.sources = sources
        repo.cacheFile(s).delete()
        rebuild()
    }

    fun clearCache() {
        repo.clearAll()
        catalog = Catalog.EMPTY
        refresh()
    }

    // ---- favori / gizle ----
    fun toggleFavorite(id: String) {
        val f = if (id in favorites) favorites - id else favorites + id
        favorites = f
        prefs.favorites = f
    }

    fun hide(item: MediaItem) {
        val h = prefs.hidden + item.id
        prefs.hidden = h
        hiddenCount = h.size
        rebuild()
    }

    fun unhideAll() {
        prefs.hidden = emptySet()
        hiddenCount = 0
        rebuild()
    }

    // ---- oynatma / tarayıcı ----
    fun play(items: List<MediaItem>, index: Int) {
        if (items.isEmpty()) return
        overlay = Overlay.Play(items, index.coerceIn(0, items.lastIndex))
    }

    fun onPlayed(item: MediaItem) {
        val list = (listOf(item) + recents.filter { it.id != item.id }).take(12)
        recents = list
        prefs.recents = list
    }

    fun openWeb(raw: String) {
        val url = normalizeUrl(raw) ?: return
        history = (listOf(url) + history.filter { it != url }).take(8)
        prefs.history = history
        overlay = Overlay.Web(url)
    }

    private fun normalizeUrl(raw: String): String? {
        val t = raw.trim()
        if (t.isEmpty()) return null
        if (t.startsWith("http://", true) || t.startsWith("https://", true)) return t
        if (t.contains(' ') || !t.contains('.')) return "https://duckduckgo.com/?q=" + Uri.encode(t)
        return "https://$t"
    }

    // ---- Home Assistant ----
    fun reportPlayback(title: String, haState: String) {
        if (!prefs.haEnabled || prefs.haUrl.isBlank() || prefs.haWebhook.isBlank()) return
        haJob?.cancel()
        haJob = scope.launch {
            delay(600) // kanal değiştirirken art arda istek gitmesin
            HomeAssistant.report(prefs.haUrl, prefs.haWebhook, title, haState)
        }
    }

    fun testHomeAssistant() {
        scope.launch {
            val ok = HomeAssistant.report(prefs.haUrl, prefs.haWebhook, "Test", "idle")
            status = if (ok) "Home Assistant: bağlantı başarılı" else "Home Assistant: bağlantı başarısız"
        }
    }
}
