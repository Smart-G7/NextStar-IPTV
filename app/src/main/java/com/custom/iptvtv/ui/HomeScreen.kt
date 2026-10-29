package com.custom.iptvtv.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import com.custom.iptvtv.AppState
import com.custom.iptvtv.Tab
import com.custom.iptvtv.data.MediaItem
import kotlinx.coroutines.delay
import java.util.Calendar

@Composable
fun HomeScreen(state: AppState) {
    val catalog = state.catalog
    val favorites = state.favorites
    val hour = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
    val greeting = when {
        hour < 6 -> "İyi geceler"
        hour < 12 -> "Günaydın"
        hour < 18 -> "İyi günler"
        else -> "İyi akşamlar"
    }
    val favItems = remember(catalog, favorites) {
        sequenceOf(catalog.live, catalog.movies, catalog.series, catalog.radio)
            .flatten().filter { it.id in favorites }.take(40).toList()
    }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(80); runCatching { first.requestFocus() } }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 28.dp, bottom = 40.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 32.dp), verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Text(greeting, style = MaterialTheme.typography.headlineMedium, color = Pal.Text)
                    Text(
                        "${catalog.live.size} kanal · ${catalog.movies.size} film · ${catalog.series.size} dizi · ${catalog.radio.size + state.webRadios.size} radyo",
                        color = Pal.Muted
                    )
                }
                ActionButton(
                    if (state.loading) "Güncelleniyor…" else "Yenile",
                    onClick = { state.refresh() },
                    modifier = Modifier.focusRequester(first),
                    icon = Icons.Filled.Refresh
                )
            }
        }
        state.status?.let { msg ->
            item { Text(msg, color = Pal.Danger, modifier = Modifier.padding(horizontal = 32.dp, vertical = 10.dp)) }
        }
        if (state.sources.isEmpty()) {
            item {
                FocusBox(
                    onClick = { state.tab = Tab.SETTINGS },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 20.dp),
                    shape = RoundedCornerShape(16.dp),
                    scaleOnFocus = 1.01f
                ) {
                    Row(Modifier.padding(24.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Icon(Icons.Filled.Add, null, tint = Pal.Accent, modifier = Modifier.size(36.dp))
                        Column {
                            Text("Başlamak için bir liste ekle", style = MaterialTheme.typography.titleMedium, color = Pal.Text)
                            Text("Ayarlar → Liste kaynakları bölümüne M3U, JSON veya GitHub raw adresi gir. Türkçe radyolar listesiz de çalışır.", color = Pal.Muted)
                        }
                    }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 20.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                QuickTile(Tab.LIVE, catalog.live.size, state, Modifier.weight(1f))
                QuickTile(Tab.MOVIES, catalog.movies.size, state, Modifier.weight(1f))
                QuickTile(Tab.SERIES, catalog.series.size, state, Modifier.weight(1f))
                QuickTile(Tab.RADIO, catalog.radio.size + state.webRadios.size, state, Modifier.weight(1f))
            }
        }
        if (state.recents.isNotEmpty()) item { MediaRow("Devam et", state.recents, state) }
        if (favItems.isNotEmpty()) item { MediaRow("Favoriler", favItems, state) }
    }
}

@Composable
private fun QuickTile(tab: Tab, count: Int, state: AppState, modifier: Modifier) {
    FocusBox(onClick = { state.tab = tab }, modifier = modifier.height(92.dp), shape = RoundedCornerShape(16.dp), scaleOnFocus = 1.05f) {
        Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Icon(tab.icon, null, tint = Pal.Accent, modifier = Modifier.size(34.dp))
            Column {
                Text(tab.label, color = Pal.Text, style = MaterialTheme.typography.titleMedium)
                Text("$count içerik", color = Pal.Muted, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun MediaRow(title: String, items: List<MediaItem>, state: AppState) {
    Column(Modifier.padding(top = 8.dp)) {
        SectionTitle(title, Modifier.padding(horizontal = 32.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 32.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            itemsIndexed(items, key = { _, it -> it.id }) { i, it ->
                WideCard(it, onClick = { state.play(items, i) })
            }
        }
    }
}
