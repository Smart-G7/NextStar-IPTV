package com.custom.iptvtv.ui

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.custom.iptvtv.AppState
import com.custom.iptvtv.data.MediaItem
import kotlinx.coroutines.delay

/** Filmler / Diziler / Radyo için ortak ekran: grup çipleri + arama + poster ızgarası. */
@Composable
fun BrowseScreen(
    state: AppState,
    key: String,
    title: String,
    items: List<MediaItem>,
    icon: ImageVector,
    emptyTitle: String,
    emptyMessage: String
) {
    val favorites = state.favorites
    val selected = state.groupSel[key] ?: GROUP_ALL
    var query by remember { mutableStateOf("") }
    val groups = remember(items) { items.groupBy { it.group } }
    val groupNames = remember(groups) {
        (listOf(GROUP_FAV, GROUP_ALL) + groups.keys.sortedWith(String.CASE_INSENSITIVE_ORDER)).distinct()
    }
    val favKey = if (selected == GROUP_FAV) favorites else null
    val shown = remember(items, selected, favKey, query) {
        val base = when (selected) {
            GROUP_ALL -> items
            GROUP_FAV -> items.filter { it.id in favorites }
            else -> groups[selected].orEmpty()
        }
        if (query.isBlank()) base else base.filter { it.title.contains(query, ignoreCase = true) }
    }
    var focused by remember(shown) { mutableStateOf<MediaItem?>(null) }
    val firstItem = remember { FocusRequester() }
    LaunchedEffect(selected, items) { delay(80); runCatching { firstItem.requestFocus() } }

    Column(Modifier.fillMaxSize().padding(start = 8.dp, top = 24.dp, end = 24.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(start = 24.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleLarge, color = Pal.Text)
                Text("$selected · ${shown.size} içerik", color = Pal.Muted)
            }
            AppTextField(query, { query = it }, "Ara…", Modifier.width(260.dp))
        }
        Row(
            Modifier.fillMaxWidth().padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            LazyRow(
                Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(groupNames, key = { it }) { g ->
                    Chip(g, g == selected) { state.groupSel[key] = g }
                }
            }
            val f = focused
            if (f != null) {
                val isFav = f.id in favorites
                ActionButton(
                    if (isFav) "Favoriden çıkar" else "Favori",
                    { state.toggleFavorite(f.id) },
                    icon = if (isFav) Icons.Filled.Star else Icons.Filled.StarBorder
                )
                ActionButton("Gizle", { state.hide(f) }, icon = Icons.Filled.VisibilityOff)
            }
        }
        if (shown.isEmpty()) {
            EmptyState(
                icon,
                if (items.isEmpty()) emptyTitle else "Sonuç bulunamadı",
                if (items.isEmpty()) emptyMessage else "Başka bir grup seç veya aramayı değiştir."
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 170.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 24.dp, end = 8.dp, top = 8.dp, bottom = 32.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                itemsIndexed(shown, key = { _, m -> m.id }) { i, m ->
                    PosterCard(
                        item = m,
                        fav = m.id in favorites,
                        modifier = if (i == 0) Modifier.focusRequester(firstItem) else Modifier,
                        onFocused = { focused = m },
                        onClick = { state.play(shown, i) }
                    )
                }
            }
        }
    }
}

@Composable
private fun PosterCard(item: MediaItem, fav: Boolean, modifier: Modifier, onFocused: () -> Unit, onClick: () -> Unit) {
    FocusBox(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(196.dp),
        shape = RoundedCornerShape(14.dp),
        scaleOnFocus = 1.05f,
        onFocused = onFocused
    ) {
        Column {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                Logo(item.title, item.logo, Modifier.fillMaxSize(), padding = 10.dp)
                if (fav) {
                    Icon(
                        Icons.Filled.Star, null, tint = Pal.Accent,
                        modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(20.dp)
                    )
                }
            }
            Text(
                item.title,
                Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = Pal.Text,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
