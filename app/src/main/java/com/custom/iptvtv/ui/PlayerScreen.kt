package com.custom.iptvtv.ui

import android.view.KeyEvent as AKeyEvent
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem as ExoItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import com.custom.iptvtv.AppState
import com.custom.iptvtv.data.DnsProvider
import com.custom.iptvtv.data.MediaItem
import com.custom.iptvtv.data.MediaType
import com.custom.iptvtv.net.Net
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request

/**
 * Tam ekran oynatıcı (Media3). Kumanda:
 *  OK = duraklat/devam, OK uzun = favori, ◀▶ = VOD ±10 sn, ▲▼ = canlıda önceki/sonraki kanal,
 *  CH+/CH−, ⏭/⏮ = kanal/bölüm değiştir, Geri = çık.
 */
@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(state: AppState, items: List<MediaItem>, startIndex: Int) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var index by remember { mutableIntStateOf(startIndex.coerceIn(0, items.lastIndex)) }
    val item = items[index.coerceIn(0, items.lastIndex)]
    val currentItem by rememberUpdatedState(item)
    val zapping = item.type == MediaType.LIVE || item.type == MediaType.RADIO

    var buffering by remember { mutableStateOf(true) }
    var playing by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var osdVisible by remember { mutableStateOf(true) }
    var osdTick by remember { mutableIntStateOf(0) }
    var pos by remember { mutableLongStateOf(0L) }
    var dur by remember { mutableLongStateOf(0L) }
    val retries = remember { intArrayOf(0) }
    val longPress = remember { booleanArrayOf(false) }
    val probed = remember { booleanArrayOf(false) }
    val focus = remember { FocusRequester() }

    val dsFactory = remember { OkHttpDataSource.Factory(Net.client(state.prefs.dns)) }
    val exo = remember {
        val renderers = DefaultRenderersFactory(context).apply {
            setEnableDecoderFallback(true)
            if (!state.prefs.hardwareDecoding) {
                // Yazılım çözücüler öne alınır (uyumsuz yayınlar için)
                setMediaCodecSelector { mime, secure, tunneling ->
                    MediaCodecSelector.DEFAULT.getDecoderInfos(mime, secure, tunneling)
                        .sortedBy { it.hardwareAccelerated }
                }
            }
        }
        ExoPlayer.Builder(context, renderers)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dsFactory))
            .build()
    }

    DisposableEffect(exo) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                buffering = playbackState == Player.STATE_BUFFERING
                if (playbackState == Player.STATE_READY) {
                    error = null
                    retries[0] = 0
                }
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                playing = playWhenReady
                state.reportPlayback(currentItem.title, if (playWhenReady) "playing" else "paused")
            }

            override fun onPlayerError(e: PlaybackException) {
                val container = e.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED ||
                    e.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED
                if (container && !probed[0]) {
                    // Biçim tanınmadı: içeriğin ilk baytlarına bakıp doğru biçimle bir kez daha dene
                    probed[0] = true
                    buffering = true
                    val target = currentItem
                    scope.launch {
                        when (val kind = withContext(Dispatchers.IO) { probe(target, state.prefs.dns) }) {
                            Probe.Hls -> reload(exo, target, MimeTypes.APPLICATION_M3U8)
                            Probe.Ts -> reload(exo, target, MimeTypes.VIDEO_MP2T)
                            is Probe.Bad -> { buffering = false; error = kind.message }
                            Probe.Unknown -> {
                                buffering = false
                                error = "Bu yayın biçimi desteklenmiyor" + extHint(target.url) + " (${e.errorCodeName})"
                            }
                        }
                    }
                    return
                }
                if (container) {
                    buffering = false
                    error = "Yayın açılamadı (${e.errorCodeName})"
                    return
                }
                if (retries[0] < 3) {
                    // Canlı yayınlar sık kopar: kısa beklemeyle kendiliğinden yeniden dene
                    retries[0]++
                    buffering = true
                    scope.launch { delay(1500); exo.prepare() }
                } else {
                    buffering = false
                    error = "Yayın açılamadı (${e.errorCodeName})"
                }
            }
        }
        exo.addListener(listener)
        onDispose {
            exo.removeListener(listener)
            exo.release()
            state.reportPlayback(currentItem.title, "idle")
        }
    }

    // Seçili öğe değişince (ilk açılış dahil) yeni yayını yükle
    LaunchedEffect(item.id) {
        retries[0] = 0
        probed[0] = false
        error = null
        buffering = true
        pos = 0L
        dur = 0L
        // Öğeye özel başlıklar (User-Agent, Referer, Cookie) bu yayından itibaren kullanılır
        dsFactory.setDefaultRequestProperties(mapOf("User-Agent" to Net.UA) + item.headers)
        exo.setMediaItem(buildExoItem(item))
        exo.prepare()
        exo.playWhenReady = true
        state.onPlayed(item)
        state.reportPlayback(item.title, "playing")
        osdTick++
    }

    // Bilgi çubuğu 4 sn görünür
    LaunchedEffect(osdTick) {
        osdVisible = true
        delay(4000)
        osdVisible = false
    }
    // VOD ilerleme çubuğunu görünürken güncelle
    LaunchedEffect(osdVisible, zapping) {
        while (osdVisible && !zapping) {
            pos = exo.currentPosition
            dur = exo.duration.coerceAtLeast(0L)
            delay(500)
        }
    }
    LaunchedEffect(Unit) { delay(50); runCatching { focus.requestFocus() } }

    BackHandler { state.overlay = null }

    fun seek(deltaMs: Long) {
        val d = exo.duration
        val t = exo.currentPosition + deltaMs
        exo.seekTo(if (d != C.TIME_UNSET && d > 0) t.coerceIn(0L, d) else t.coerceAtLeast(0L))
        osdTick++
    }

    fun zap(delta: Int) {
        val n = if (zapping) (index + delta).mod(items.size) else index + delta
        if (n in items.indices && n != index) index = n else osdTick++
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onPreviewKeyEvent { ev ->
                val key = ev.nativeKeyEvent
                val down = ev.type == KeyEventType.KeyDown
                val up = ev.type == KeyEventType.KeyUp
                when (key.keyCode) {
                    AKeyEvent.KEYCODE_DPAD_CENTER, AKeyEvent.KEYCODE_ENTER, AKeyEvent.KEYCODE_NUMPAD_ENTER -> {
                        if (down) {
                            if (key.repeatCount == 0) {
                                longPress[0] = false
                            } else if (!longPress[0]) {
                                longPress[0] = true // uzun basış: favori
                                state.toggleFavorite(currentItem.id)
                                osdTick++
                            }
                        } else if (up) {
                            if (!longPress[0]) {
                                exo.playWhenReady = !exo.playWhenReady
                                osdTick++
                            }
                            longPress[0] = false
                        }
                        true
                    }
                    AKeyEvent.KEYCODE_DPAD_LEFT -> { if (down) { if (zapping) osdTick++ else seek(-10_000L) }; true }
                    AKeyEvent.KEYCODE_DPAD_RIGHT -> { if (down) { if (zapping) osdTick++ else seek(10_000L) }; true }
                    AKeyEvent.KEYCODE_DPAD_UP -> { if (down) { if (zapping) zap(-1) else osdTick++ }; true }
                    AKeyEvent.KEYCODE_DPAD_DOWN -> { if (down) { if (zapping) zap(1) else osdTick++ }; true }
                    AKeyEvent.KEYCODE_CHANNEL_UP, AKeyEvent.KEYCODE_MEDIA_NEXT -> { if (down) zap(1); true }
                    AKeyEvent.KEYCODE_CHANNEL_DOWN, AKeyEvent.KEYCODE_MEDIA_PREVIOUS -> { if (down) zap(-1); true }
                    AKeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                        if (down) { exo.playWhenReady = !exo.playWhenReady; osdTick++ }
                        true
                    }
                    AKeyEvent.KEYCODE_MEDIA_PLAY -> { if (down) exo.playWhenReady = true; true }
                    AKeyEvent.KEYCODE_MEDIA_PAUSE -> { if (down) exo.playWhenReady = false; true }
                    else -> false
                }
            }
            .focusRequester(focus)
            .focusable()
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    isFocusable = false
                    keepScreenOn = true
                    player = exo
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        if (item.type == MediaType.RADIO) {
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                Logo(item.title, item.logo, Modifier.size(220.dp), padding = 16.dp)
                Spacer(Modifier.height(16.dp))
                Text(item.title, style = MaterialTheme.typography.titleLarge, color = Pal.Text, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }

        if (buffering && error == null) {
            CircularProgressIndicator(
                Modifier
                    .align(if (item.type == MediaType.RADIO) Alignment.TopEnd else Alignment.Center)
                    .padding(24.dp)
                    .size(44.dp),
                color = Pal.Accent
            )
        }

        error?.let { msg ->
            Text(
                msg,
                Modifier.align(Alignment.Center).padding(32.dp),
                color = Pal.Danger,
                style = MaterialTheme.typography.titleMedium
            )
        }

        if (osdVisible || error != null || !playing) {
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xE6000000))))
                    .padding(horizontal = 40.dp, vertical = 28.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (item.type != MediaType.RADIO) Logo(item.title, item.logo, Modifier.size(56.dp))
                    Column(Modifier.weight(1f)) {
                        Text(item.title, style = MaterialTheme.typography.titleMedium, color = Pal.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${item.group} · ${index + 1}/${items.size}", color = Pal.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (item.id in state.favorites) Icon(Icons.Filled.Star, null, tint = Pal.Accent, modifier = Modifier.size(28.dp))
                    Icon(if (playing) Icons.Filled.PlayArrow else Icons.Filled.Pause, null, tint = Pal.Text, modifier = Modifier.size(32.dp))
                }
                if (!zapping && dur > 0L) {
                    Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(Color(0x33FFFFFF))) {
                        Box(
                            Modifier
                                .fillMaxWidth((pos.toFloat() / dur.toFloat()).coerceIn(0f, 1f))
                                .fillMaxHeight()
                                .background(Pal.Accent)
                        )
                    }
                    Row(Modifier.fillMaxWidth()) {
                        Text(formatTime(pos), color = Pal.Muted)
                        Spacer(Modifier.weight(1f))
                        Text(formatTime(dur), color = Pal.Muted)
                    }
                }
            }
        }
    }
}

@OptIn(UnstableApi::class)
private fun buildExoItem(item: MediaItem, mime: String? = null): ExoItem {
    val b = ExoItem.Builder().setUri(item.url)
    when {
        mime != null -> b.setMimeType(mime)
        // Uzantısız/sorgulu HLS adreslerini ExoPlayer'a açıkça bildir
        item.url.substringBefore('#').lowercase().contains(".m3u8") -> b.setMimeType(MimeTypes.APPLICATION_M3U8)
    }
    return b.build()
}

@OptIn(UnstableApi::class)
private fun reload(exo: ExoPlayer, item: MediaItem, mime: String) {
    exo.setMediaItem(buildExoItem(item, mime))
    exo.prepare()
    exo.playWhenReady = true
}

private sealed interface Probe {
    object Hls : Probe
    object Ts : Probe
    object Unknown : Probe
    class Bad(val message: String) : Probe
}

/** Adresin ilk baytlarını okuyup gerçek biçimi (HLS / MPEG-TS / hata sayfası) anlamaya çalışır. */
private fun probe(item: MediaItem, dns: DnsProvider): Probe = try {
    val rb = Request.Builder().url(item.url).header("User-Agent", Net.UA)
    item.headers.forEach { (k, v) -> rb.header(k, v) }
    Net.client(dns).newCall(rb.build()).execute().use { r ->
        if (!r.isSuccessful) {
            val hint = when (r.code) {
                401, 403, 458 -> " Kullanıcı adı/şifre, abonelik süresi veya aynı anda bağlantı sınırı (başka cihazda açık yayın) kontrol edilmeli."
                404 -> " Kanal sunucuda bulunamadı; listeyi yenile."
                else -> ""
            }
            Probe.Bad("Sunucu HTTP ${r.code} döndürdü.$hint")
        } else {
            val buf = ByteArray(1024)
            val n = r.body?.byteStream()?.use { it.read(buf) } ?: -1
            val head = if (n > 0) String(buf, 0, n, Charsets.ISO_8859_1).trimStart() else ""
            val type = r.header("Content-Type")?.lowercase() ?: ""
            when {
                n <= 0 -> Probe.Bad("Sunucu boş yanıt verdi (kanal kapalı veya bağlantı sınırı dolu olabilir).")
                head.startsWith("#EXTM3U") || type.contains("mpegurl") -> Probe.Hls
                buf[0] == 0x47.toByte() && (n <= 188 || buf[188] == 0x47.toByte()) -> Probe.Ts
                head.startsWith("<") || type.contains("html") ->
                    Probe.Bad("Sunucu video yerine bir web sayfası/hata mesajı döndürdü (HTTP ${r.code}).")
                else -> Probe.Unknown
            }
        }
    }
} catch (e: Exception) {
    Probe.Bad("Sunucuya ulaşılamadı: " + (e.message ?: e.javaClass.simpleName))
}

private fun extHint(url: String): String {
    val ext = url.substringBefore('?').substringBefore('#').substringAfterLast('/', "").substringAfterLast('.', "").lowercase()
    return if (ext.isNotEmpty() && ext.length <= 5) " (.$ext)" else ""
}

private fun formatTime(ms: Long): String {
    val s = ms / 1000
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%02d:%02d".format(m, sec)
}
