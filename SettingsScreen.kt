package com.custom.iptvtv.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.custom.iptvtv.AppState
import com.custom.iptvtv.data.DnsProvider
import kotlinx.coroutines.delay

private val AUDIO_LANGS = listOf(
    "" to "Otomatik", "tr" to "Türkçe", "en" to "English", "de" to "Deutsch", "ru" to "Русский", "ar" to "العربية"
)
private val SUB_LANGS = listOf("" to "Otomatik", "off" to "Kapalı") + AUDIO_LANGS.drop(1)
private val RADIO_BG = listOf("none" to "Koyu gradyan", "logo" to "Radyo logosu", "image" to "Görsel", "video" to "Video")

@Composable
fun SettingsScreen(state: AppState) {
    val prefs = state.prefs
    val context = LocalContext.current
    var hw by remember { mutableStateOf(prefs.hardwareDecoding) }
    var adBlock by remember { mutableStateOf(prefs.adBlock) }
    var dns by remember { mutableStateOf(prefs.dns) }
    var audioLang by remember { mutableStateOf(prefs.audioLang) }
    var subLang by remember { mutableStateOf(prefs.subLang) }
    var bgMode by remember { mutableStateOf(prefs.radioBgMode) }
    var bgUri by remember { mutableStateOf(prefs.radioBgUri) }
    var haOn by remember { mutableStateOf(prefs.haEnabled) }
    var haUrl by remember { mutableStateOf(prefs.haUrl) }
    var haHook by remember { mutableStateOf(prefs.haWebhook) }
    var newName by remember { mutableStateOf("") }
    var newUrl by remember { mutableStateOf("") }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(80); runCatching { first.requestFocus() } }

    // Yerel dosya seçici (bazı TV'lerde yoktur; hata yakalanır)
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            bgUri = uri.toString()
            prefs.radioBgUri = bgUri
        }
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 32.dp, end = 40.dp, top = 28.dp, bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { Text("Ayarlar", style = MaterialTheme.typography.titleLarge, color = Pal.Text) }
        state.status?.let { msg -> item { Text(msg, color = Pal.Accent) } }

        // ---- Kaynaklar ----
        item { SectionTitle("Liste kaynakları") }
        items(state.sources, key = { it.url }) { s ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(s.name, color = Pal.Text, maxLines = 1)
                    Text(maskUrl(s.url), color = Pal.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                }
                ActionButton("Sil", { state.removeSource(s) }, icon = Icons.Filled.Delete)
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AppTextField(newName, { newName = it }, "Ad (isteğe bağlı)", Modifier.width(220.dp))
                AppTextField(newUrl, { newUrl = it }, "M3U / JSON / GitHub raw adresi", Modifier.weight(1f), KeyboardType.Uri)
                ActionButton("Ekle", { if (state.addSource(newName, newUrl)) { newName = ""; newUrl = "" } }, icon = Icons.Filled.Add, primary = true)
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ActionButton(if (state.loading) "Güncelleniyor…" else "Listeleri şimdi güncelle", { state.refresh() }, Modifier.focusRequester(first), Icons.Filled.Refresh)
                ActionButton("Önbelleği temizle ve yeniden indir", { state.clearCache() }, icon = Icons.Filled.Delete)
            }
        }

        // ---- Oynatıcı ----
        item { SectionTitle("Oynatıcı") }
        item {
            ToggleRow("Donanımsal çözme", "Açık: TV'nin video çipi kullanılır (önerilen). Kapalı: yazılım codec'leri öne alınır; uyumsuz yayınlar için.", hw) {
                hw = it; prefs.hardwareDecoding = it
            }
        }

        // ---- Ses ve altyazı ----
        item { SectionTitle("Film ve dizi: ses ve altyazı dili") }
        item {
            Text("Oynatırken ▼ tuşu (veya Menü) ile ses/altyazı seçersen tercihin otomatik hatırlanır.", color = Pal.Muted)
            Text("Ses dili", color = Pal.Text, modifier = Modifier.padding(top = 10.dp, bottom = 6.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(AUDIO_LANGS, key = { it.first }) { (code, name) ->
                    Chip(name, code == audioLang) { audioLang = code; prefs.audioLang = code }
                }
            }
            Text("Altyazı", color = Pal.Text, modifier = Modifier.padding(top = 12.dp, bottom = 6.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(SUB_LANGS, key = { it.first }) { (code, name) ->
                    Chip(name, code == subLang) { subLang = code; prefs.subLang = code }
                }
            }
        }

        // ---- Radyo arka planı ----
        item { SectionTitle("Radyo çalarken arka plan") }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(RADIO_BG, key = { it.first }) { (code, name) ->
                    Chip(name, code == bgMode) { bgMode = code; prefs.radioBgMode = code }
                }
            }
        }
        if (bgMode == "image" || bgMode == "video") {
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AppTextField(
                        bgUri, { bgUri = it; prefs.radioBgUri = it },
                        if (bgMode == "image") "Görsel adresi (https://…) veya dosyadan seç" else "Video adresi (https://… mp4) veya dosyadan seç",
                        Modifier.weight(1f), KeyboardType.Uri
                    )
                    ActionButton("Dosyadan seç", {
                        runCatching { picker.launch(arrayOf(if (bgMode == "image") "image/*" else "video/*")) }
                            .onFailure { state.status = "Bu TV'de dosya seçici yok; adres kullan." }
                    })
                }
            }
        }

        // ---- DNS ----
        item { SectionTitle("Akıllı DNS (DoH)") }
        item {
            Text(dns.description, color = Pal.Muted)
            LazyRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(DnsProvider.values().toList(), key = { it.name }) { p ->
                    Chip(p.title, p == dns) { dns = p; prefs.dns = p }
                }
            }
        }

        // ---- Tarayıcı ----
        item { SectionTitle("Tarayıcı") }
        item {
            ToggleRow("Reklam ve pop-up engelleyici", "Bilinen reklam alan adlarını ve pop-up açılışlarını engeller. Bir sonraki sayfa açılışında geçerli olur.", adBlock) {
                adBlock = it; prefs.adBlock = it
            }
        }

        // ---- Home Assistant ----
        item { SectionTitle("Home Assistant") }
        item {
            ToggleRow("Oynatma durumunu bildir", "Ne izlendiğini webhook ile Home Assistant'a gönderir.", haOn) { haOn = it; prefs.haEnabled = it }
        }
        item { AppTextField(haUrl, { haUrl = it; prefs.haUrl = it }, "Adres (örn. http://192.168.1.10:8123)", Modifier.fillMaxWidth(), KeyboardType.Uri) }
        item { AppTextField(haHook, { haHook = it; prefs.haWebhook = it }, "Webhook ID", Modifier.fillMaxWidth()) }
        item { ActionButton("Bağlantıyı dene", { state.testHomeAssistant() }) }

        // ---- Bakım ----
        item { SectionTitle("Bakım") }
        item {
            ActionButton("Gizlenen kanalları geri getir (${state.hiddenCount})", { state.unhideAll() }, icon = Icons.Filled.VisibilityOff)
        }
        state.crashLog?.let { log ->
            item { SectionTitle("Son çökme kaydı") }
            item {
                Text(
                    log.lines().take(24).joinToString("\n"),
                    color = Pal.Muted, fontFamily = FontFamily.Monospace, fontSize = 12.sp
                )
            }
            item { ActionButton("Kaydı temizle", { state.clearCrash() }, icon = Icons.Filled.Delete) }
        }
    }
}

@Composable
private fun ToggleRow(title: String, desc: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    FocusBox(onClick = { onChange(!checked) }, modifier = Modifier.fillMaxWidth(), scaleOnFocus = 1f) {
        Row(Modifier.padding(horizontal = 18.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.weight(1f)) {
                Text(title, color = Pal.Text, style = MaterialTheme.typography.bodyLarge)
                Text(desc, color = Pal.Muted, style = MaterialTheme.typography.bodySmall)
            }
            Switch(checked = checked, onCheckedChange = null) // yalnızca görsel; tıklamayı FocusBox yönetir
        }
    }
}

/** Kullanıcı adı/şifre içeren adresleri ekranda gösterirken gizle. */
private fun maskUrl(url: String): String =
    url.replace(Regex("(?i)(password|pass|pwd|token|key)=[^&]+"), "$1=•••")
        .replace(Regex("(?i)(https?://)[^/@]+@"), "$1•••@")
