package com.custom.iptvtv.ui

import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyEvent
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem as ExoMediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.mediacodec.MediaCodecUtil
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.custom.iptvtv.AppState
import com.custom.iptvtv.data.MediaItem
import com.custom.iptvtv.data.MediaType
import com.custom.iptvtv.net.Net
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * "Yazılımsal çözme": yazılım codec'lerini öne alır, donanım yedekte kalır.
 * (FFmpeg uzantısı Maven'da yayınlı olmadığı için bu yol kullanılır.)
 */
@OptIn(UnstableApi::class)
private val softwareFirstSelector = MediaCodecSelector { mimeType, secure, tunneling ->
    MediaCodecUtil.getDecoderInfos(mimeType, secure, tunneling).sortedBy { if (it.softwareOnly) 0 else 1 }
}

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(state: AppState, items: List<MediaItem>, startIndex: Int) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    var index by remember { mutableIntStateOf(startIndex.coerceIn(0, items.lastIndex)) }
    val item = items[index]
    val isLive = item.type == MediaType.LIVE
    val isRadio = item.type == MediaType.RADIO

    var buffering by remember { mutableStateOf(true) }
    var playing by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var overlayVisible by remember { mutableStateOf(true) }
    var overlayTick by remember { mutableIntStateOf(0) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var toast by remember { mutableStateOf<String?>(null) }
    var longHandled by remember { mutableStateOf(false) }
    var retries by remember { mutableIntStateOf(0) }
    var tracks by remember(item.id) { mutableStateOf(Tracks.EMPTY) }
    var trackMenu by remember { mutableStateOf(false) }
    var nowPlaying by remember(item.id) { mutableStateOf<String?>(null) }
    var station by remember(item.id) { mutableStateOf<String?>(null) }
    val fav = item.id in state.favorites
    val focus = remember { FocusRequester() }

    // Kanal başına yeni oynatıcı: farklı başlık (Referer/UA) gerektiren kaynaklar da doğru çalışır
    val player = remember(item.id) {
        val renderers = DefaultRenderersFactory(context)
            .setEnableDecoderFallback(true)
            .setMediaCodecSelector(if (state.prefs.hardwareDecoding) MediaCodecSelector.DEFAULT else softwareFirstSelector)
        val dataSourceFactory = OkHttpDataSource.Factory(Net.client(state.prefs.dns))
            .setDefaultRequestProperties(item.headers)
            .also { if (!item.headers.containsKey("User-Agent")) it.setUserAgent(Net.UA) }
        // 2GB RAM için tampon sınırı
        val loadControl = DefaultLoadControl.Builder()
            .setTargetBufferBytes(48 * 1024 * 1024)
            .setPrioritizeTimeOverSizeThresholds(false)
            .build()
        ExoPlayer.Builder(context, renderers)
            .setLoadControl(loadControl)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .setAudioAttributes(AudioAttributes.DEFAULT, true)
            .setHandleAudioBecomingNoisy(true)
            .build()
            .apply {
                setMediaItem(ExoMediaItem.fromUri(item.url))
                if (!isRadio) {
                    // Kayıtlı ses/altyazı dili tercihleri
                    val b = trackSelectionParameters.buildUpon()
                    val audioPref = state.prefs.audioLang
                    if (audioPref.isNotEmpty()) b.setPreferredAudioLanguage(audioPref)
                    val subPref = state.prefs.subLang
                    if (subPref == "off") b.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                    else if (subPref.isNotEmpty()) b.setPreferredTextLanguage(subPref)
                    trackSelectionParameters = b.build()
                }
                prepare()
                playWhenReady = true
            }
    }

    fun show() { overlayVisible = true; overlayTick++ }
    fun go(delta: Int) {
        if (items.size > 1) {
            index = (index + delta + items.size) % items.size
            error = null; retries = 0; show()
        }
    }
    fun toggleFav() {
        state.toggleFavorite(item.id)
        toast = if (item.id in state.favorites) "★ Favorilere eklendi" else "Favorilerden çıkarıldı"
    }
    fun seekBy(ms: Long) {
        val d = player.duration
        val target = player.currentPosition + ms
        player.seekTo(if (d > 0) target.coerceIn(0L, d) else target.coerceAtLeast(0L))
        show()
    }
    fun openTrackMenu() {
        if (isRadio) return
        if (tracks.groups.none { it.type == C.TRACK_TYPE_AUDIO || it.type == C.TRACK_TYPE_TEXT }) {
            toast = "Bu içerikte ses/altyazı seçeneği yok"
        } else trackMenu = true
    }
    fun selectAudio(g: Tracks.Group, i: Int) {
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setOverrideForType(TrackSelectionOverride(g.mediaTrackGroup, i)).build()
        g.getTrackFormat(i).language?.takeIf { it != "und" }?.let { state.prefs.audioLang = it } // sonraki videolar için hatırla
        trackMenu = false
    }
    fun selectText(g: Tracks.Group?, i: Int) {
        val b = player.trackSelectionParameters.buildUpon()
        if (g == null) {
            b.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            state.prefs.subLang = "off"
        } else {
            b.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            b.setOverrideForType(TrackSelectionOverride(g.mediaTrackGroup, i))
            g.getTrackFormat(i).language?.takeIf { it != "und" }?.let { state.prefs.subLang = it }
        }
        player.trackSelectionParameters = b.build()
        trackMenu = false
    }

    LaunchedEffect(item.id) { state.onPlayed(item); buffering = true; error = null; position = 0; duration = 0; show() }
    LaunchedEffect(trackMenu) { if (!trackMenu) { delay(50); runCatching { focus.requestFocus() } } }

    DisposableEffect(player, lifecycleOwner) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                buffering = playbackState == Player.STATE_BUFFERING
                if (playbackState == Player.STATE_READY) { error = null; retries = 0 }
                if (playbackState == Player.STATE_ENDED && !isLive && items.size > 1) go(1) // sıradaki bölüm/şarkı
            }
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                playing = isPlaying
                state.reportPlayback(item.title, if (isPlaying) "playing" else "paused")
            }
            override fun onTracksChanged(t: Tracks) { tracks = t }
            // Radyo: ICY (SHOUTcast/Icecast) akış bilgisi = IP radyosunun RDS karşılığı (sanatçı - şarkı, istasyon adı)
            override fun onMediaMetadataChanged(m: MediaMetadata) {
                if (!isRadio) return
                val song = listOf(m.artist?.toString()?.trim().orEmpty(), m.title?.toString()?.trim().orEmpty())
                    .filter { it.isNotEmpty() }.joinToString(" – ")
                nowPlaying = song.ifEmpty { null }
                station = m.station?.toString()?.trim()?.ifEmpty { null }
            }
            override fun onPlayerError(e: PlaybackException) {
                if (e.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
                    player.seekToDefaultPosition(); player.prepare()
                } else if (retries < 3) {
                    retries++
                    scope.launch { delay(1500L * retries); player.prepare() }
                } else {
                    error = "Yayın açılamadı (${e.errorCodeName}). OK ile tekrar dene."
                }
            }
        }
        val observer = LifecycleEventObserver { _, ev -> if (ev == Lifecycle.Event.ON_STOP) player.pause() } // HOME'da arka planda çalmasın
        player.addListener(listener)
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            player.removeListener(listener)
            player.release()
            state.reportPlayback(item.title, "idle")
        }
    }

    LaunchedEffect(player, overlayVisible, isLive) {
        while (overlayVisible && !isLive && !isRadio && isActive) {
            position = player.currentPosition
            duration = player.duration.takeIf { it > 0 } ?: 0L
            delay(500)
        }
    }
    LaunchedEffect(overlayTick, playing, overlayVisible) {
        if (overlayVisible && playing && !isRadio && !trackMenu) { delay(4000); overlayVisible = false }
    }
    LaunchedEffect(toast) { if (toast != null) { delay(1800); toast = null } }

    BackHandler { state.overlay = null }
    BackHandler(enabled = trackMenu) { trackMenu = false } // sonra kayıtlı olan önceliklidir

    Box(
        Modifier.fillMaxSize().background(Color.Black)
            .focusRequester(focus)
            .onKeyEvent { ev ->
                if (trackMenu) return@onKeyEvent false // menü açıkken yön tuşları menüde gezinsin
                val nk = ev.nativeKeyEvent
                when (ev.type) {
                    KeyEventType.KeyDown -> when (ev.key) {
                        Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                            if (nk.isLongPress) { toggleFav(); longHandled = true } else if (nk.repeatCount == 0) longHandled = false
                            true
                        }
                        Key.DirectionLeft -> { if (!isLive && !isRadio) seekBy(-10_000) else show(); true }
                        Key.DirectionRight -> { if (!isLive && !isRadio) seekBy(10_000) else show(); true }
                        Key.DirectionUp -> { if (isLive) go(-1) else show(); true }
                        Key.DirectionDown -> { if (isLive || isRadio) { if (isLive) go(1) else show() } else openTrackMenu(); true }
                        Key.ChannelUp, Key.MediaNext -> { go(1); true }
                        Key.ChannelDown, Key.MediaPrevious -> { go(-1); true }
                        Key.MediaFastForward -> { seekBy(30_000); true }
                        Key.MediaRewind -> { seekBy(-30_000); true }
                        Key.MediaPlayPause -> { if (player.isPlaying) player.pause() else player.play(); show(); true }
                        Key.MediaPlay -> { player.play(); show(); true }
                        Key.MediaPause -> { player.pause(); show(); true }
                        Key.Menu, Key.Captions, Key.Settings, Key.Info -> { openTrackMenu(); true }
                        else -> false
                    }
                    KeyEventType.KeyUp -> when (ev.key) {
                        Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                            if (!longHandled) {
                                if (error != null) { error = null; retries = 0; player.prepare() }
                                else { if (player.isPlaying) player.pause() else player.play(); show() }
                            }
                            longHandled = false
                            true
                        }
                        else -> false
                    }
                    else -> false
                }
            }
            .focusable()
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    isFocusable = false
                    isFocusableInTouchMode = false
                    keepScreenOn = true // izlerken TV ekran koruyucusu devreye girmesin
                    subtitleView?.setUserDefaultStyle()
                    subtitleView?.setUserDefaultTextSize()
                    this.player = player
                }
            },
            update = { it.player = player },
            onRelease = { it.player = null },
            modifier = Modifier.fillMaxSize()
        )

        if (isRadio) {
            RadioBackground(state, item)
            Column(
                Modifier.align(Alignment.Center).padding(horizontal = 64.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Logo(item.title, item.logo, Modifier.size(160.dp), padding = 16.dp)
                Text(item.title, style = MaterialTheme.typography.titleLarge, color = Color.White, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(station ?: item.group, color = Pal.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                nowPlaying?.let {
                    Text("♪  $it", style = MaterialTheme.typography.titleMedium, color = Pal.Accent, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }

        if (buffering && error == null) {
            CircularProgressIndicator(Modifier.align(Alignment.Center).size(56.dp), color = Pal.Accent, strokeWidth = 5.dp)
        }
        error?.let {
            Text(
                it, Modifier.align(Alignment.Center).clip(RoundedCornerShape(12.dp)).background(Color(0xCC000000)).padding(24.dp),
                color = Color.White, textAlign = TextAlign.Center
            )
        }

        AnimatedVisibility(visible = overlayVisible, enter = fadeIn(), exit = fadeOut()) {
            Box(Modifier.fillMaxSize()) {
                Column(
                    Modifier.align(Alignment.TopStart).fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Color(0xCC000000), Color.Transparent)))
                        .padding(horizontal = 48.dp, vertical = 32.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (isLive) Text("● CANLI", color = Pal.Danger, style = MaterialTheme.typography.labelLarge)
                        Text(item.title, style = MaterialTheme.typography.titleLarge, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                        if (fav) Icon(Icons.Filled.Star, null, tint = Pal.Accent, modifier = Modifier.size(26.dp))
                    }
                    Text("${item.group}${if (items.size > 1) " · ${index + 1}/${items.size}" else ""}", color = Color(0xFFB8C2D1))
                }
                Column(
                    Modifier.align(Alignment.BottomStart).fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xDD000000))))
                        .padding(horizontal = 48.dp, vertical = 28.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (!isLive && !isRadio) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            Icon(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(30.dp))
                            Text(fmt(position), color = Color.White)
                            val frac = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
                            Box(Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp)).background(Color(0x33FFFFFF))) {
                                Box(Modifier.fillMaxHeight().fillMaxWidth(frac).background(Pal.Accent))
                            }
                            Text(fmt(duration), color = Color.White)
                        }
                    }
                    Text(
                        when {
                            isLive -> "▲▼ kanal değiştir · Menü: ses/altyazı · OK duraklat · OK'a uzun bas: favori"
                            isRadio -> "OK duraklat · OK'a uzun bas: favori · Geri: listeye dön"
                            else -> "◀ ▶ ±10 sn · ▼ ses ve altyazı · OK duraklat · OK'a uzun bas: favori"
                        },
                        color = Color(0xFFB8C2D1), style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        toast?.let {
            Text(
                it, Modifier.align(Alignment.TopCenter).padding(top = 96.dp).clip(RoundedCornerShape(50)).background(Color(0xDD000000)).padding(horizontal = 22.dp, vertical = 10.dp),
                color = Color.White
            )
        }

        if (trackMenu) {
            TrackMenu(tracks, onAudio = { g, i -> selectAudio(g, i) }, onText = { g, i -> selectText(g, i) })
        }
    }
}

// ---------------- Ses / Altyazı menüsü ----------------

private class TrackOpt(val label: String, val group: Tracks.Group?, val index: Int, val selected: Boolean)

@OptIn(UnstableApi::class)
private fun optionsFor(tracks: Tracks, type: Int): List<TrackOpt> {
    val out = ArrayList<TrackOpt>()
    for (g in tracks.groups) {
        if (g.type != type) continue
        for (i in 0 until g.length) {
            if (!g.isTrackSupported(i)) continue
            out.add(TrackOpt(trackLabel(g.getTrackFormat(i), type, out.size + 1), g, i, g.isTrackSelected(i)))
        }
    }
    return out
}

@OptIn(UnstableApi::class)
private fun trackLabel(f: Format, type: Int, n: Int): String {
    val lang = f.language?.takeIf { it.isNotBlank() && it != "und" }
        ?.let { Locale.forLanguageTag(it).getDisplayLanguage(Locale("tr")) }
        ?.replaceFirstChar { it.uppercase() }
    val parts = ArrayList<String>()
    parts.add(lang ?: f.label?.takeIf { it.isNotBlank() } ?: "Parça $n")
    if (type == C.TRACK_TYPE_AUDIO && f.channelCount > 0) {
        parts.add(when (f.channelCount) { 1 -> "Mono"; 2 -> "Stereo"; 6 -> "5.1"; 8 -> "7.1"; else -> "${f.channelCount} kanal" })
    }
    f.label?.takeIf { lang != null && it.isNotBlank() && !it.equals(lang, true) }?.let { parts.add(it) }
    return parts.joinToString(" · ")
}

@Composable
private fun TrackMenu(tracks: Tracks, onAudio: (Tracks.Group, Int) -> Unit, onText: (Tracks.Group?, Int) -> Unit) {
    val audio = remember(tracks) { optionsFor(tracks, C.TRACK_TYPE_AUDIO) }
    val text = remember(tracks) { optionsFor(tracks, C.TRACK_TYPE_TEXT) }
    val textOff = remember(tracks) { text.none { it.selected } }
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(80); runCatching { firstFocus.requestFocus() } }

    Box(Modifier.fillMaxSize().background(Color(0xB0000000)), contentAlignment = Alignment.Center) {
        Row(
            Modifier.clip(RoundedCornerShape(20.dp)).background(Pal.Surface).padding(28.dp),
            horizontalArrangement = Arrangement.spacedBy(28.dp)
        ) {
            Column(Modifier.width(340.dp)) {
                SectionTitle("Ses", Modifier.padding(bottom = 12.dp))
                if (audio.isEmpty()) Text("Seçenek yok", color = Pal.Muted)
                LazyColumn(Modifier.heightIn(max = 380.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(audio.size) { n ->
                        val o = audio[n]
                        OptionRow(o.label, o.selected, if (n == 0 || o.selected) Modifier.focusRequester(firstFocus) else Modifier) {
                            o.group?.let { g -> onAudio(g, o.index) }
                        }
                    }
                }
            }
            Column(Modifier.width(340.dp)) {
                SectionTitle("Altyazı", Modifier.padding(bottom = 12.dp))
                LazyColumn(Modifier.heightIn(max = 380.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    item { OptionRow("Kapalı", textOff, Modifier) { onText(null, 0) } }
                    items(text.size) { n ->
                        val o = text[n]
                        OptionRow(o.label, o.selected, Modifier) { onText(o.group, o.index) }
                    }
                }
            }
        }
    }
}

@Composable
private fun OptionRow(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    FocusBox(onClick = onClick, modifier = modifier.fillMaxWidth(), selected = selected, scaleOnFocus = 1f) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(label, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, color = if (selected) Pal.Accent else Pal.Text)
            if (selected) Icon(Icons.Filled.Check, null, tint = Pal.Accent, modifier = Modifier.size(20.dp))
        }
    }
}

// ---------------- Radyo arka planı ----------------

@Composable
private fun RadioBackground(state: AppState, item: MediaItem) {
    val mode = remember { state.prefs.radioBgMode }
    val uri = remember { state.prefs.radioBgUri }
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF0B1B30), Color(0xFF05080F))))) {
        when {
            mode == "logo" && !item.logo.isNullOrBlank() ->
                AsyncImage(model = item.logo, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            mode == "image" && uri.isNotBlank() ->
                AsyncImage(model = uri, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            mode == "video" && uri.isNotBlank() -> BackgroundVideo(uri)
        }
        Box(Modifier.fillMaxSize().background(Color(0x99000000))) // yazılar okunsun diye karartma
    }
}

/** Sessiz, döngülü arka plan videosu. Ses odağı istemez; ana radyo akışına karışmaz. */
@OptIn(UnstableApi::class)
@Composable
private fun BackgroundVideo(uri: String) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val video = remember(uri) {
        ExoPlayer.Builder(context).build().apply {
            volume = 0f
            repeatMode = Player.REPEAT_MODE_ALL
            setMediaItem(ExoMediaItem.fromUri(uri))
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(video, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, ev ->
            if (ev == Lifecycle.Event.ON_STOP) video.pause() else if (ev == Lifecycle.Event.ON_START) video.play()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer); video.release() }
    }
    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                useController = false
                isFocusable = false
                isFocusableInTouchMode = false
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                this.player = video
            }
        },
        update = { it.player = video },
        onRelease = { it.player = null },
        modifier = Modifier.fillMaxSize()
    )
}

private fun fmt(ms: Long): String {
    val s = ms / 1000
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%02d:%02d".format(m, sec)
}
