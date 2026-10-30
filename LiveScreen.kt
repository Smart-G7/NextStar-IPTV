package com.custom.iptvtv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.custom.iptvtv.AppState
import com.custom.iptvtv.data.MediaItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun LiveScreen(state: AppState) {
    val catalog = state.catalog
    val favorites = state.favorites
    val scope = rememberCoroutineScope()
    val listState = state.liveListState
    val selected = state.groupSel["live"] ?: GROUP_ALL
    val query = state.queries["live"] ?: ""
    val groupNames = remember(catalog) {
        listOf(GROUP_FAV, GROUP_ALL) + catalog.liveGroups.keys.sortedWith(String.CASE_INSENSITIVE_ORDER)
    }
    // Favori değişimi listeyi sıfırlamasın: yalnızca "Favoriler" grubundayken anahtar olur
    val favKey = if (selected == GROUP_FAV) favorites else null
    val channels = remember(catalog, selected, favKey, query) {
        val base = when (selected) {
            GROUP_ALL -> catalog.live
            GROUP_FAV -> catalog.live.filter { it.id in favorites }
            else -> catalog.liveGroups[selected].orEmpty()
        }
        if (query.isBlank()) base else base.filter { it.title.contains(query, ignoreCase = true) }
    }
    // Oynatıcıdan dönünce son izlenen kanala geri dön
    val restoreId = state.restoreFocusId
    val targetIndex = remember(channels, restoreId) {
        if (restoreId == null) 0 else channels.indexOfFirst { it.id == restoreId }.let { if (it < 0) 0 else it }
    }
    var focusedItem by remember(channels) { mutableStateOf(channels.getOrNull(targetIndex)) }
    val target = remember { FocusRequester() }
    val ready = catalog.live.isNotEmpty()
    LaunchedEffect(selected, ready) {
        if (ready) {
            if (targetIndex > 0) runCatching { listState.scrollToItem(maxOf(0, targetIndex - 3)) }
            delay(80)
            runCatching { target.requestFocus() }
            state.restoreFocusId = null
        }
    }

    Row(Modifier.fillMaxSize().padding(start = 8.dp, top = 24.dp, bottom = 16.dp, end = 24.dp)) {
        // 1) Gruplar
        LazyColumn(
            Modifier.width(230.dp).fillMaxHeight(),
            contentPadding = PaddingValues(end = 12.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(groupNames, key = { it }) { g ->
                FocusBox(
                    onClick = {
                        state.groupSel["live"] = g
                        state.restoreFocusId = null
                        scope.launch { runCatching { listState.scrollToItem(0) } }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    selected = g == selected,
                    scaleOnFocus = 1f
                ) {
                    Row(Modifier.padding(horizontal = 14.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(g, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, color = if (g == selected) Pal.Accent else Pal.Text)
                        catalog.liveGroups[g]?.let { Text("${it.size}", color = Pal.Muted, style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
        }

        // 2) Kanallar
        Column(Modifier.weight(1f).fillMaxHeight().padding(horizontal = 8.dp)) {
            Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Canlı TV", style = MaterialTheme.typography.titleLarge, color = Pal.Text)
                    Text("$selected · ${channels.size} kanal", color = Pal.Muted)
                }
                AppTextField(query, { state.queries["live"] = it }, "Kanal ara…", Modifier.width(260.dp))
            }
            if (channels.isEmpty()) {
                EmptyState(
                    Icons.Filled.LiveTv,
                    if (catalog.live.isEmpty()) "Henüz kanal yok" else "Sonuç bulunamadı",
                    if (catalog.live.isEmpty()) "Ayarlar'dan bir liste kaynağı ekle." else "Başka bir grup seç veya aramayı değiştir."
                )
            } else {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    state = listState,
                    contentPadding = PaddingValues(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    itemsIndexed(channels, key = { _, ch -> ch.id }) { index, ch ->
                        ChannelRow(
                            index = index,
                            item = ch,
                            fav = ch.id in favorites,
                            modifier = if (index == targetIndex) Modifier.focusRequester(target) else Modifier,
                            onFocused = { focusedItem = ch },
                            onClick = { state.play(channels, index) }
                        )
                    }
                }
            }
        }

        // 3) Önizleme + eylemler
        PreviewPanel(state, focusedItem, channels)
    }
}

@Composable
private fun ChannelRow(index: Int, item: MediaItem, fav: Boolean, modifier: Modifier, onFocused: () -> Unit, onClick: () -> Unit) {
    FocusBox(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(62.dp),
        scaleOnFocus = 1f,
        onFocused = onFocused
    ) {
        Row(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("${index + 1}", Modifier.width(44.dp), color = Pal.Muted, textAlign = TextAlign.End, maxLines = 1)
            Logo(item.title, item.logo, Modifier.size(42.dp))
            Text(item.title, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, color = Pal.Text)
            if (fav) Icon(Icons.Filled.Star, null, tint = Pal.Accent, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun PreviewPanel(state: AppState, item: MediaItem?, channels: List<MediaItem>) {
    Column(
        Modifier.width(320.dp).fillMaxHeight().padding(start = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (item == null) return@Column
        val fav = item.id in state.favorites
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Pal.Surface).padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Logo(item.title, item.logo, Modifier.size(150.dp), padding = 12.dp)
            Spacer(Modifier.height(14.dp))
            Text(item.title, style = MaterialTheme.typography.titleMedium, color = Pal.Text, maxLines = 3, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
            Text(item.group, color = Pal.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        ActionButton("Oynat", { state.play(channels, channels.indexOfFirst { it.id == item.id }) }, Modifier.fillMaxWidth(), Icons.Filled.PlayArrow, primary = true)
        ActionButton(if (fav) "Favoriden çıkar" else "Favorilere ekle", { state.toggleFavorite(item.id) }, Modifier.fillMaxWidth(), if (fav) Icons.Filled.Star else Icons.Filled.StarBorder)
        ActionButton("Bu kanalı gizle", { state.hide(item) }, Modifier.fillMaxWidth(), Icons.Filled.VisibilityOff)
    }
}
