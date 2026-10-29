package com.custom.iptvtv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import com.custom.iptvtv.AppState
import com.custom.iptvtv.Overlay
import com.custom.iptvtv.Tab

/** Uygulamanın kökü: oynatıcı/tarayıcı tam ekran açıkken ana kabuk kompozisyondan çıkar (bellek ve odak rahat). */
@Composable
fun Root(state: AppState) {
    val overlay = state.overlay
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Pal.Bg, Pal.BgEnd)))) {
        when (overlay) {
            is Overlay.Play -> PlayerScreen(state, overlay.items, overlay.index)
            is Overlay.Web -> WebScreen(state, overlay.url)
            null -> MainShell(state)
        }
    }
}

@Composable
private fun MainShell(state: AppState) {
    // Geri: Ana Sayfa dışındaysan Ana Sayfa'ya dön; Ana Sayfa'da sistem davranışı (uygulamadan çık)
    BackHandler(enabled = state.tab != Tab.HOME) { state.tab = Tab.HOME }

    Row(Modifier.fillMaxSize()) {
        NavRail(state)
        Box(Modifier.weight(1f).fillMaxHeight()) {
            when (state.tab) {
                Tab.HOME -> HomeScreen(state)
                Tab.LIVE -> LiveScreen(state)
                Tab.MOVIES -> BrowseScreen(
                    state, "movies", "Filmler", state.catalog.movies, Icons.Filled.Movie,
                    "Henüz film yok", "Ayarlar'dan film içeren bir liste kaynağı ekle."
                )
                Tab.SERIES -> BrowseScreen(
                    state, "series", "Diziler", state.catalog.series, Icons.Filled.VideoLibrary,
                    "Henüz dizi yok", "Ayarlar'dan dizi içeren bir liste kaynağı ekle."
                )
                Tab.RADIO -> {
                    val radios = remember(state.catalog, state.webRadios) {
                        (state.catalog.radio + state.webRadios).distinctBy { it.id }
                    }
                    BrowseScreen(
                        state, "radio", "Radyo", radios, Icons.Filled.Radio,
                        "Radyo listesi boş", "İnternet bağlantını kontrol edip Ana Sayfa'dan Yenile'ye bas."
                    )
                }
                Tab.WEB -> BrowserHomeScreen(state)
                Tab.SETTINGS -> SettingsScreen(state)
            }
        }
    }
}

@Composable
private fun NavRail(state: AppState) {
    Column(
        Modifier.width(200.dp).fillMaxHeight()
            .background(Pal.Surface.copy(alpha = 0.55f))
            .padding(horizontal = 12.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            "NextStar",
            style = MaterialTheme.typography.titleMedium,
            color = Pal.Accent,
            modifier = Modifier.padding(start = 10.dp, bottom = 14.dp)
        )
        Tab.values().forEach { t ->
            val sel = state.tab == t
            FocusBox(onClick = { state.tab = t }, modifier = Modifier.fillMaxWidth(), selected = sel, scaleOnFocus = 1f) {
                Row(
                    Modifier.padding(horizontal = 12.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(t.icon, null, tint = if (sel) Pal.Accent else Pal.Muted, modifier = Modifier.size(22.dp))
                    Text(t.label, color = if (sel) Pal.Accent else Pal.Text, maxLines = 1)
                }
            }
        }
    }
}
