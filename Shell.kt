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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.custom.iptvtv.AppState
import com.custom.iptvtv.Overlay
import com.custom.iptvtv.Tab

@Composable
fun Root(state: AppState) {
    // Oynatıcı/tarayıcı açıkken kabuk kompozisyondan çıkar: boşuna RAM ve CPU harcanmaz
    when (val ov = state.overlay) {
        is Overlay.Play -> PlayerScreen(state, ov.items, ov.index)
        is Overlay.Web -> WebScreen(state, ov.url)
        null -> Shell(state)
    }
}

@Composable
private fun Shell(state: AppState) {
    BackHandler(enabled = state.tab != Tab.HOME) { state.tab = Tab.HOME }
    Row(
        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Pal.Bg, Pal.BgEnd)))
    ) {
        Sidebar(state)
        Box(Modifier.weight(1f).fillMaxHeight()) {
            when (state.tab) {
                Tab.HOME -> HomeScreen(state)
                Tab.LIVE -> LiveScreen(state)
                Tab.MOVIES -> PosterScreen(state, Tab.MOVIES)
                Tab.SERIES -> PosterScreen(state, Tab.SERIES)
                Tab.RADIO -> RadioScreen(state)
                Tab.WEB -> BrowserHomeScreen(state)
                Tab.SETTINGS -> SettingsScreen(state)
            }
        }
    }
}

@Composable
private fun Sidebar(state: AppState) {
    Column(
        Modifier.width(210.dp).fillMaxHeight().background(Color(0x66000000)).padding(horizontal = 14.dp, vertical = 26.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text("NextStar", style = MaterialTheme.typography.titleLarge, color = Pal.Accent, modifier = Modifier.padding(start = 10.dp))
        Text("IPTV", color = Pal.Muted, modifier = Modifier.padding(start = 10.dp, bottom = 20.dp))
        Tab.values().forEach { tab ->
            val active = state.tab == tab
            FocusBox(
                onClick = { state.tab = tab },
                modifier = Modifier.fillMaxWidth(),
                selected = active,
                scaleOnFocus = 1f
            ) {
                Row(
                    Modifier.padding(horizontal = 14.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(tab.icon, null, tint = if (active) Pal.Accent else Pal.Text, modifier = Modifier.size(24.dp))
                    Text(tab.label, color = if (active) Pal.Accent else Pal.Text, fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal)
                }
            }
        }
    }
}
