package com.custom.iptvtv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.custom.iptvtv.AppState
import com.custom.iptvtv.Tab
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Filmler ve Diziler: grup çipleri + poster ızgarası. Oynatıcıdan dönünce aynı konuma gelir. */
@Composable
fun PosterScreen(state: AppState, tab: Tab) {
    val isMovie = tab == Tab.MOVIES
    val catalog = state.catalog
    val all = if (isMovie) catalog.movies else catalog.series
    val groups = if (isMovie) catalog.movieGroups else catalog.seriesGroups
    val gridState = if (isMovie) state.movieGridState else state.seriesGridState
    val favorites = state.favorites
    val scope = rememberCoroutineScope()
    val selected = state.groupSel[tab.name] ?: GROUP_ALL
    val query = state.queries[tab.name] ?: ""
    val chips = remember(catalog, tab) { listOf(GROUP_ALL, GROUP_FAV) + groups.keys.sortedWith(String.CASE_INSENSITIVE_ORDER) }
    val favKey = if (selected == GROUP_FAV) favorites else null
    val items = remember(catalog, tab, selected, favKey, query) {
        val base = when (selected) {
            GROUP_ALL -> all
            GROUP_FAV -> all.filter { it.id in favorites }
            else -> groups[selected].orEmpty()
        }
        if (query.isBlank()) base else base.filter { it.title.contains(query, ignoreCase = true) }
    }
    val restoreId = state.restoreFocusId
    val targetIndex = remember(items, restoreId) {
        if (restoreId == null) 0 else items.indexOfFirst { it.id == restoreId }.let { if (it < 0) 0 else it }
    }
    val target = remember { FocusRequester() }
    val ready = all.isNotEmpty()
    LaunchedEffect(tab, selected, ready) {
        if (ready) {
            if (targetIndex > 0) runCatching { gridState.scrollToItem(maxOf(0, targetIndex - 4)) }
            delay(80)
            runCatching { target.requestFocus() }
            state.restoreFocusId = null
        }
    }

    Column(Modifier.fillMaxSize().padding(start = 8.dp, top = 24.dp, end = 24.dp)) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(tab.label, style = MaterialTheme.typography.titleLarge, color = Pal.Text)
                Text("$selected · ${items.size} içerik", color = Pal.Muted)
            }
            AppTextField(query, { state.queries[tab.name] = it }, "Ara…", Modifier.width(260.dp))
        }
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(chips, key = { it }) { g ->
                Chip(g, g == selected) {
                    state.groupSel[tab.name] = g
                    state.restoreFocusId = null
                    scope.launch { runCatching { gridState.scrollToItem(0) } }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        if (items.isEmpty()) {
            EmptyState(
                if (isMovie) Icons.Filled.Movie else Icons.Filled.VideoLibrary,
                if (all.isEmpty()) "Henüz içerik yok" else "Sonuç bulunamadı",
                if (all.isEmpty()) "Listende bu türde içerik yok. Gruplar \"film\", \"dizi\", \"sezon\" gibi kelimelerle tanınır." else "Başka bir grup seç veya aramayı değiştir."
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(168.dp),
                state = gridState,
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                itemsIndexed(items, key = { _, it -> it.id }) { index, it ->
                    FocusBox(
                        onClick = { state.play(items, index) },
                        modifier = Modifier.fillMaxWidth().aspectRatio(0.68f).then(if (index == targetIndex) Modifier.focusRequester(target) else Modifier),
                        shape = RoundedCornerShape(12.dp),
                        scaleOnFocus = 1.06f
                    ) {
                        Logo(it.title, it.logo, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, padding = 0.dp)
                        Box(
                            Modifier.align(Alignment.BottomStart).fillMaxWidth()
                                .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xEE000000))))
                                .padding(start = 10.dp, end = 10.dp, top = 28.dp, bottom = 10.dp)
                        ) {
                            Text(it.title, maxLines = 2, overflow = TextOverflow.Ellipsis, color = Color.White, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
    }
}

/** Radyo: varsayılan kompakt liste, istenirse küçük kartlı ızgara. Çıkışta aynı satıra döner. */
@Composable
fun RadioScreen(state: AppState) {
    val favorites = state.favorites
    val grid = state.radioGrid
    val query = state.queries["radio"] ?: ""
    val list = remember(state.catalog, state.webRadios, query) {
        val merged = (state.catalog.radio + state.webRadios).distinctBy { it.id }
        if (query.isBlank()) merged else merged.filter { it.title.contains(query, ignoreCase = true) }
    }
    val restoreId = state.restoreFocusId
    val targetIndex = remember(list, restoreId) {
        if (restoreId == null) 0 else list.indexOfFirst { it.id == restoreId }.let { if (it < 0) 0 else it }
    }
    val target = remember { FocusRequester() }
    val ready = (state.catalog.radio.size + state.webRadios.size) > 0
    LaunchedEffect(grid, ready) {
        if (ready) {
            if (targetIndex > 0) {
                runCatching {
                    if (grid) state.radioGridState.scrollToItem(maxOf(0, targetIndex - 4))
                    else state.radioListState.scrollToItem(maxOf(0, targetIndex - 3))
                }
            }
            delay(80)
            runCatching { target.requestFocus() }
            state.restoreFocusId = null
        }
    }

    Column(Modifier.fillMaxSize().padding(start = 8.dp, top = 24.dp, end = 24.dp)) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Radyo", style = MaterialTheme.typography.titleLarge, color = Pal.Text)
                Text("${list.size} istasyon", color = Pal.Muted)
            }
            AppTextField(query, { state.queries["radio"] = it }, "Radyo ara…", Modifier.width(240.dp))
            Spacer(Modifier.width(12.dp))
            ActionButton(if (grid) "Liste" else "Izgara", { state.setRadioGrid(!grid) }, icon = if (grid) Icons.Filled.ViewList else Icons.Filled.GridView)
            Spacer(Modifier.width(12.dp))
            ActionButton("Yenile", { state.refresh() }, icon = Icons.Filled.Refresh)
        }
        if (list.isEmpty()) {
            EmptyState(Icons.Filled.Radio, "Radyo listesi boş", "İnternet bağlantısını kontrol edip Yenile'ye bas.")
        } else if (!grid) {
            LazyColumn(
                Modifier.fillMaxSize(),
                state = state.radioListState,
                contentPadding = PaddingValues(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                itemsIndexed(list, key = { _, it -> it.id }) { index, it ->
                    FocusBox(
                        onClick = { state.play(list, index) },
                        modifier = Modifier.fillMaxWidth().height(58.dp).then(if (index == targetIndex) Modifier.focusRequester(target) else Modifier),
                        scaleOnFocus = 1f
                    ) {
                        Row(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            Logo(it.title, it.logo, Modifier.size(38.dp))
                            Text(it.title, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, color = Pal.Text)
                            Text(it.group, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Pal.Muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(160.dp), textAlign = TextAlign.End)
                            if (it.id in favorites) Icon(Icons.Filled.Star, null, tint = Pal.Accent, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(150.dp),
                state = state.radioGridState,
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                itemsIndexed(list, key = { _, it -> it.id }) { index, it ->
                    FocusBox(
                        onClick = { state.play(list, index) },
                        modifier = Modifier.fillMaxWidth().height(112.dp).then(if (index == targetIndex) Modifier.focusRequester(target) else Modifier),
                        shape = RoundedCornerShape(12.dp),
                        scaleOnFocus = 1.05f
                    ) {
                        Column(Modifier.fillMaxSize().padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            Logo(it.title, it.logo, Modifier.size(52.dp))
                            Spacer(Modifier.height(8.dp))
                            Text(it.title, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Pal.Text, style = MaterialTheme.typography.bodyMedium)
                            if (it.id in favorites) Icon(Icons.Filled.Star, null, tint = Pal.Accent, modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }
        }
    }
}
